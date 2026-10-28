package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.client.StatusBadgeHud;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.health.BodyPart;
import com.antaurora.apofirstlight.health.ClientHealth;
import com.antaurora.apofirstlight.health.Fractures;
import com.antaurora.apofirstlight.health.HealthConfig;
import com.antaurora.apofirstlight.health.Wounds;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The body map in the inventory's gear column (docs/ui/body_silhouette_v1_plan.md, user 2026-10-20: B, the player's own
 * skin, from the front): in place of the 3D player model, the flat Minecraft figure of seven parts (head 8 x 8, chest and
 * belly 8 x 6, arms and legs 4 x 12, slim arms 3 wide), one skin pixel {@link #U} canvas units, a dark seam between parts.
 * Plain white since 2026-10-27 (no skin, so what is worn never shows; the user: "这样就不用管衣服的状态了"); a part with an
 * injury is washed with the colour of its most pressing one (amber: treated and healing; brown: wants treating; red: wants
 * treating soon) and carries that one's HUD badge. "R / L" under the feet (the screen's left is the body's right), health under them. Hovering a part shows its
 * name, every injury on it with its state, and for each what to use: best first; the best one on the player (pockets, rig,
 * backpack, hands) is outlined in amber, better ones not on the player are dimmed above it, worse ones are left out; with
 * none at all, a line on what to do without. No crafting suggestions: nothing gets a recipe before the recipe rework (user
 * 2026-10-20). The text wraps at {@link #TIP_W} GUI px or two fifths of the screen (Chinese and English differ in length).
 * Client only, from ClientHealth and the player's items.
 */
final class BodySilhouette {
    /** Canvas units per skin pixel; the figure's top left (the right arm's outer edge, the head's top). */
    static final float U = 4, X0 = 48, Y0 = 29.5F, SEAM = 0.45F;
    /** The hover tooltip's widest text (GUI px). */
    private static final int TIP_W = 220, ICON = 18, LINE = 10, PAD = 4;
    private static final int AMBER = 0xD9A441, BROWN = 0xA86A3A, RED = 0xB8443C;
    /** The figure's own colour: white, a touch warm, not quite opaque so it sits on the dark panel. */
    private static final int FIGURE = 0xE8E6E1;
    private static final float FIGURE_ALPHA = 0.9F;

    record Part(BodyPart part, float x, float y, float w, float h) {}

    private BodySilhouette() {
    }

    /** The seven parts' boxes (canvas units), front view: the body's right arm and leg on the screen's left. */
    static List<Part> layout(boolean slim) {
        float arm = (slim ? 3 : 4) * U;
        float top = Y0, shoulders = Y0 + 8 * U, waist = shoulders + 6 * U, hips = shoulders + 12 * U;
        float body = X0 + 4 * U;
        return List.of(
                new Part(BodyPart.HEAD, body, top, 8 * U, 8 * U),
                new Part(BodyPart.CHEST, body, shoulders, 8 * U, 6 * U),
                new Part(BodyPart.BELLY, body, waist, 8 * U, 6 * U),
                new Part(BodyPart.RIGHT_ARM, body - arm, shoulders, arm, 12 * U),
                new Part(BodyPart.LEFT_ARM, body + 8 * U, shoulders, arm, 12 * U),
                new Part(BodyPart.RIGHT_LEG, body, hips, 4 * U, 12 * U),
                new Part(BodyPart.LEFT_LEG, body + 4 * U, hips, 4 * U, 12 * U));
    }

    /** Front-face UVs on the 64 x 64 skin: base u, v, width, height, then the outer layer's u, v. */
    private static int[] uv(BodyPart part, boolean slim) {
        int a = slim ? 3 : 4;
        return switch (part) {
            case HEAD -> new int[]{8, 8, 8, 8, 40, 8};
            case CHEST -> new int[]{20, 20, 8, 6, 20, 36};
            case BELLY -> new int[]{20, 26, 8, 6, 20, 42};
            case RIGHT_ARM -> new int[]{44, 20, a, 12, 44, 36};
            case LEFT_ARM -> new int[]{36, 52, a, 12, 52, 52};
            case RIGHT_LEG -> new int[]{4, 20, 4, 12, 4, 36};
            default -> new int[]{20, 52, 4, 12, 4, 52};
        };
    }

    // ---------------------------------------------------------------- the injuries

    /** One injury on a part: its badge, how pressing (3 red, 2 brown, 1 amber), what helps, what to do without. */
    private record Injury(StatusBadgeHud.Status status, int urgency, List<Option> options, Component without) {}

    /** One way to treat it: the item shown, what to do, why, whether the player has it on it. */
    private record Option(ItemStack icon, Component action, Component why, boolean have) {}

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.apocalypse_firstlight.inventory.body." + key, args);
    }

    /** Every injury on that part, most pressing first. */
    private static List<Injury> injuries(Player player, BodyPart part, Carried carried) {
        List<Injury> out = new ArrayList<>();
        for (Wounds.View w : ClientHealth.wounds()) if (w.part() == part) out.add(wound(w, carried));
        for (Fractures.View f : ClientHealth.fractures()) if (f.part() == part) out.add(fracture(player, f, carried));
        out.sort(Comparator.comparingInt(i -> -i.urgency()));
        return out;
    }

    private static Injury wound(Wounds.View w, Carried c) {
        var status = StatusBadgeHud.wound(w);
        // the slung gun's burn on the back: no hand of the player's own reaches it, it heals by itself (user 2026-10-21)
        if (!w.selfReach())
            return new Injury(status, w.stage() == Wounds.Stage.SCRATCH ? 1 : 2, List.of(), t("out_of_reach.back"));
        if (w.freshBurn()) {
            // a burn: a bandage over it keeps it from becoming infected; it heals by itself either way
            boolean deep = w.kind() == Wounds.Kind.DEEP_BURN;
            if (!w.bandaged())
                return new Injury(status, 2, List.of(c.option(AflItems.BANDAGE.get(), "bandage.wrap", "bandage.burn.why")),
                        t(deep ? "without.deep_burn" : "without.burn"));
            return new Injury(status, 1, List.of(), t("healing.burn"));
        }
        if (w.stage() == Wounds.Stage.SCRATCH) {
            if (!w.cleaned() && !w.bandaged())
                return new Injury(status, 2, List.of(c.option(AflItems.MEDICAL_ALCOHOL.get(), "alcohol.clean", "alcohol.clean.why"),
                        c.option(AflItems.BANDAGE.get(), "bandage.wrap", "bandage.wrap.why")), t("without.scratch"));
            if (!w.bandaged()) return new Injury(status, 1, List.of(c.option(AflItems.BANDAGE.get(), "bandage.wrap", "bandage.after_clean.why")), t("without.optional"));
            if (!w.cleaned()) return new Injury(status, 1, List.of(c.option(AflItems.MEDICAL_ALCOHOL.get(), "alcohol.clean", "alcohol.after_bandage.why")), t("without.optional"));
            return new Injury(status, 1, List.of(), t("healing.wound"));
        }
        // an infection or suppuration only gets better once cleaned
        boolean festering = w.stage() == Wounds.Stage.SUPPURATION;
        if (!w.cleaned())
            return new Injury(status, 3, List.of(c.option(AflItems.MEDICAL_ALCOHOL.get(), "alcohol.clean", "alcohol.infection.why")),
                    t(festering ? "without.suppuration" : "without.infection"));
        if (!w.bandaged()) return new Injury(status, 1, List.of(c.option(AflItems.BANDAGE.get(), "bandage.wrap", "bandage.infection.why")), t("healing.infection"));
        return new Injury(status, 1, List.of(), t("healing.infection"));
    }

    private static Injury fracture(Player player, Fractures.View f, Carried c) {
        var status = StatusBadgeHud.fracture(f, player);
        if (f.splinted()) {
            var config = HealthConfig.get().fracture;
            double days = (1 - f.progress()) * (f.part().leg() ? config.legHealDays : config.armHealDays);
            return new Injury(status, 1, List.of(), t("healing.fracture", String.format(Locale.ROOT, "%.1f", Math.max(0.1, days))));
        }
        return new Injury(status, 3, List.of(c.option(AflItems.SPLINT.get(), "splint.use", "splint.use.why")), t("without.fracture"));
    }

    /** What the player has on it: hands and hotbar, pockets, rig, backpack (not the ground, not a chest). */
    private static final class Carried {
        private final List<ItemStack> stacks = new ArrayList<>();

        Carried(Player player) {
            stacks.addAll(player.getInventory().items);
            stacks.addAll(PlayerGrids.stacks(player));
        }

        int count(Item item) {
            int n = 0;
            for (ItemStack s : stacks) if (s.is(item)) n += s.getCount();
            return n;
        }

        Option option(Item item, String action, String why) {
            return new Option(new ItemStack(item), t(action), t(why), count(item) > 0);
        }
    }

    // ---------------------------------------------------------------- drawing (canvas units)

    /**
     * The figure, its washes, R / L and health; {@code hovered}: the part under the mouse (outlined). Returns the badges to
     * draw over it once the canvas is popped ({@link #badges}).
     */
    static List<Object[]> draw(GuiGraphics g, Font font, AbstractClientPlayer player, @Nullable BodyPart hovered) {
        boolean slim = "slim".equals(player.getModelName());
        ResourceLocation wounds = com.antaurora.apofirstlight.client.WoundSkin.texture(player, slim);
        Carried carried = new Carried(player);
        List<Object[]> badges = new ArrayList<>();
        RenderSystem.enableBlend();
        for (Part p : layout(slim)) {
            int[] uv = uv(p.part(), slim);
            float x = p.x() + SEAM, y = p.y() + SEAM, w = p.w() - 2 * SEAM, h = p.h() - 2 * SEAM;
            // plain white (user 2026-10-27: "把E这个界面的人物模型改成白色的贴图，这样就不用管衣服的状态了"): no skin, no
            // clothes, the same figure whatever is worn
            AflUiShapes.fill(g, x, y, w, h, 0.6F, FIGURE, FIGURE_ALPHA);
            List<Injury> injuries = injuries(player, p.part(), carried);
            if (!injuries.isEmpty()) {
                Injury top = injuries.get(0);
                AflUiShapes.fill(g, x, y, w, h, 0.6F, top.urgency() >= 3 ? RED : top.urgency() == 2 ? BROWN : AMBER, 0.42F);
                badges.add(new Object[]{p, top.status()});
            }
            // the marks on the skin's front (client/WoundSkin) in their own colours over the white
            if (wounds != null) blit(g, wounds, x, y, w, h, uv[0], uv[1], uv[2], uv[3]);
            if (p.part() == hovered)
                AflUiShapes.box(g, x, y, w, h, 0.6F, AflUiStyle.BACK_HOVER, 0F, AflUiStyle.LINE, AflUiStyle.LINE_HOVER_ALPHA, 0, 0);
        }
        // R / L under the feet, each fitted to its leg's width
        float feet = Y0 + 32 * U + 2;
        for (Part p : layout(slim)) {
            if (!p.part().leg()) continue;
            Component side = t(p.part().right() ? "right" : "left");
            float size = Math.min(5.2F, 5.2F * (p.w() - 1) / Math.max(1, GridDrawing.width(font, side, 5.2F)));
            GridDrawing.text(g, font, side, p.x() + (p.w() - GridDrawing.width(font, side, size)) / 2, feet, size, AflUiStyle.TEXT_DIM, 1);
        }
        Component health = t("health", Math.round(player.getHealth()), Math.round(player.getMaxHealth()));
        float size = Math.min(6F, 6F * 120 / Math.max(1, GridDrawing.width(font, health, 6F)));
        GridDrawing.text(g, font, health, 80 - GridDrawing.width(font, health, size) / 2, feet + 6.8F, size, AflUiStyle.TEXT, 1);
        return badges;
    }

    /** One skin face, u, v, uw x vh texels, stretched over the box. */
    private static void blit(GuiGraphics g, ResourceLocation skin, float x, float y, float w, float h, int u, int v, int uw, int vh) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(w / uw, h / vh, 1);
        g.blit(skin, 0, 0, uw, vh, u, v, uw, vh, 64, 64);
        pose.popPose();
    }

    /** The parts' badges at GUI coordinates (the canvas at origin, scale): on each injured part, its most pressing injury's. */
    static void badges(GuiGraphics g, List<Object[]> badges, float originX, float originY, float scale) {
        for (Object[] b : badges) {
            Part p = (Part) b[0];
            float cx = p.x() + p.w() / 2, cy = p.y() + Math.min(p.h() / 2, p.part().limb() ? 5.5F * U : p.h() / 2);
            StatusBadgeHud.drawBadge(g, "body_" + p.part().key(), (StatusBadgeHud.Status) b[1], originX + cx * scale, originY + cy * scale,
                    3.6 * scale, 1);
        }
    }

    // ---------------------------------------------------------------- the hover tooltip (GUI px, vanilla text size)

    /** Some lines, optionally beside an item icon with a word on the right ("on you" / "missing"), optionally outlined. */
    private record Line(FormattedCharSequence text, int rgb) {}

    private record Block(List<Line> lines, @Nullable ItemStack icon, @Nullable Component right, int rightRgb, boolean outline,
                         float alpha, int gap) {
        int indent() { return icon == null ? 0 : ICON + 3; }

        int height() { return icon == null ? lines.size() * LINE : Math.max(ICON, lines.size() * LINE); }
    }

    static void tooltip(GuiGraphics g, Font font, Player player, BodyPart part, int mouseX, int mouseY, int screenW, int screenH) {
        int width = Math.min(TIP_W, Math.max(120, screenW * 2 / 5));
        List<Block> blocks = new ArrayList<>();
        blocks.add(text(font, width, 0, line(font, t("part." + part.key()), AflUiStyle.TEXT, width)));
        List<Injury> injuries = injuries(player, part, new Carried(player));
        if (injuries.isEmpty()) blocks.add(text(font, width, 0, line(font, t("no_injury"), AflUiStyle.TEXT_DIM, width)));
        for (Injury injury : injuries) {
            List<Line> head = new ArrayList<>(line(font, injury.status().name(), injury.urgency() >= 3 ? 0xE8867C : AflUiStyle.TEXT, width));
            head.addAll(line(font, injury.status().effect(), AflUiStyle.TEXT_DIM, width));
            blocks.add(text(font, width, 5, head));
            advice(blocks, font, width, injury);
        }
        draw(g, font, blocks, mouseX, mouseY, screenW, screenH);
    }

    /**
     * The injury's options, best first: up to the best one the player has (outlined), the better ones it lacks dimmed above
     * it, the worse ones left out; none on the player: all of them dimmed, then what to do without.
     */
    private static void advice(List<Block> blocks, Font font, int width, Injury injury) {
        List<Option> options = injury.options();
        int best = -1;
        for (int i = 0; i < options.size() && best < 0; i++) if (options.get(i).have()) best = i;
        int last = best < 0 ? options.size() - 1 : best;
        for (int i = 0; i <= last; i++) {
            Option o = options.get(i);
            Component right = t(o.have() ? "have" : "missing");
            int avail = width - ICON - 3 - font.width(right) - 6;
            List<Line> lines = new ArrayList<>(line(font, o.action(), AflUiStyle.TEXT, avail));
            lines.addAll(line(font, o.why(), AflUiStyle.TEXT_DIM, avail));
            blocks.add(new Block(lines, o.icon(), right, o.have() ? AflUiStyle.ACCENT : AflUiStyle.TEXT_DISABLED,
                    i == best, i == best ? 1 : 0.5F, 3));
        }
        if (best < 0) {
            Component without = options.isEmpty() ? injury.without() : t("without", injury.without());
            blocks.add(text(font, width, options.isEmpty() ? 1 : 3, line(font, without, AflUiStyle.TEXT_DIM, width)));
        }
    }

    private static List<Line> line(Font font, Component text, int rgb, int width) {
        List<Line> out = new ArrayList<>();
        for (FormattedCharSequence s : font.split(text, Math.max(40, width))) out.add(new Line(s, rgb));
        return out;
    }

    private static Block text(Font font, int width, int gap, List<Line> lines) {
        return new Block(lines, null, null, 0, false, 1, gap);
    }

    private static void draw(GuiGraphics g, Font font, List<Block> blocks, int mouseX, int mouseY, int screenW, int screenH) {
        int w = 0, h = 0;
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            int widest = 0;
            for (Line l : b.lines()) widest = Math.max(widest, font.width(l.text()));
            w = Math.max(w, b.indent() + widest + (b.right() == null ? 0 : 6 + font.width(b.right())));
            h += (i == 0 ? 0 : b.gap()) + b.height();
        }
        h -= 2;
        int pad = PAD, x = mouseX + 12, y = mouseY - 12;
        if (x + w + pad > screenW) x = Math.max(pad, mouseX - 16 - w);
        if (y + h + pad > screenH) y = screenH - h - pad;
        y = Math.max(pad, y);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        AflUiShapes.box(g, x - pad, y - pad, w + 2 * pad, h + 2 * pad, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.94F,
                AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
        int at = y;
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (i > 0) at += b.gap();
            int bh = b.height(), textH = b.lines().size() * LINE;
            if (b.outline())
                AflUiShapes.box(g, x - 2, at - 1.5F, w + 4, bh + 1, 2, AflUiStyle.ACCENT, 0.08F, AflUiStyle.ACCENT, 0.7F, 0, 0);
            if (b.icon() != null) {
                pose.pushPose();
                pose.translate(x + 1, at + (bh - 16) / 2F - 1, 0);
                g.renderItem(b.icon(), 0, 0);
                pose.popPose();
                if (b.alpha() < 1)   // dim the icon of an option the player lacks under a film of the tooltip's back
                    AflUiShapes.fill(g, x + 1, at + (bh - 16) / 2F - 1, 16, 16, 0, AflUiStyle.BACK, 0.55F);
            }
            int ty = at + (bh - textH) / 2;
            for (Line l : b.lines()) {
                g.drawString(font, l.text(), x + b.indent(), ty, AflUiStyle.argb(l.rgb(), b.alpha()), false);
                ty += LINE;
            }
            if (b.right() != null)
                g.drawString(font, b.right(), x + w - font.width(b.right()), at + (bh - 8) / 2, AflUiStyle.argb(b.rightRgb(), 1), false);
            at += bh;
        }
        pose.popPose();
    }
}
