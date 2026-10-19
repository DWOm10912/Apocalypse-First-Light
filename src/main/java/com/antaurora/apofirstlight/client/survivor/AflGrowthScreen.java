package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.registry.AflAttributes;
import com.antaurora.apofirstlight.survivor.SurvivorGrowth;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The growth page (docs/gameplay/survivor_background_skills_v1_plan.md sections 7, 9 and 16, P3), concept A / A2 of
 * design/ui/growth_v1 (user 2026-10-18: a skill table after Abiotic Factor's; the A2 layout once there are many items).
 * <ul>
 * <li>Left: how fast this survivor learns, the traits that touch growth, the group index (click to jump; the group in view
 * is marked), "only trained ones", the rules.</li>
 * <li>Right, scrolling: the body stats, then each growth group ("firearms"...), a header per group that stays at the top
 * while its rows scroll under it. Each item is one row: icon, name, a pill per level (reached; the one being trained fills
 * as it goes; ones this survivor cannot reach carry a lock), its level (guns: level and tier). Levels are of two kinds
 * (user 2026-10-18, after Abiotic Factor): a plain pill adds a little to a number, a node pill carries the mark of what is
 * learned there (the item's Perks; a perk at level 0, what the survivor does before learning anything, is drawn before the
 * first pill). Up to {@link #ROOMY_ITEMS} items each row also says how to train it; with more, the rows are lower and that
 * line moves into the row's hover text.</li>
 * <li>Hovering a pill says what that level gives; hovering the name gives the level, then the experience (current / needed
 * for the next level), then how to train it. Numbers only in hover text.</li>
 * </ul>
 * Opened with its own key (K by default) or from the character page; Esc or the key goes back to where it came from. Reads
 * the sheet's View part (SurvivorSetupFlow#view); view only.
 */
public final class AflGrowthScreen extends Screen {
    private static final float TOP = 38, BOTTOM = 255;
    private static final float LEFT_X = 15, LEFT_W = 118, RIGHT_X = 139, RIGHT_W = 326;
    private static final float SECTION_H = 15, SECTION_GAP = 5;
    private static final int ROOMY_ITEMS = 8;
    private static final float CHIP_H = 11, CHIP_GAP = 2.5F, GROUP_H = 12;
    private static final int OVER_CAP = 0xD0705E;
    /** "Only trained ones", kept while the game runs. */
    private static boolean onlyTrained;

    private final SetupCanvas canvas = new SetupCanvas();
    private final boolean fromCharacter;
    private float scroll, scrollMax;
    /** This frame's layout: the sections and where each starts in the scrolled content; the left column's hit boxes. */
    private List<Section> sections = List.of();
    private float[] offsets = new float[0];
    private float groupsY, filterY;

    /** Row metrics: roomy (with the how-to line) or compact (A2). */
    private record Rows(float h, float iconW, float iconH, float nameX, float nameSize, float pillsX, float pillW, float pillH, float gap,
                        float labelSize, boolean statusLine) {
        static final Rows ROOMY = new Rows(24, 26, 11, 30, 0.75F, 74, 17, 8, 2.5F, 0.62F, true);
        static final Rows COMPACT = new Rows(15, 20, 8.5F, 24, 0.66F, 66, 13.5F, 6, 2.3F, 0.58F, false);
    }

    private record Section(String key, Component heading, List<CompoundTag> items, boolean body) {
    }

    public AflGrowthScreen(boolean fromCharacter) {
        super(SetupCanvas.tr("growth_page.title"));
        this.fromCharacter = fromCharacter;
    }

    @Override
    protected void init() {
        canvas.layout(minecraft, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (fromCharacter) minecraft.setScreen(new AflCharacterScreen());
        else super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SurvivorGrowthClient.GROWTH.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (SetupCanvas.in(canvas.vx(mouseX), canvas.vy(mouseY), RIGHT_X, TOP, RIGHT_W, BOTTOM - TOP)) {
            scroll = Mth.clamp(scroll - (float) delta * 14, 0, scrollMax);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float mx = canvas.vx(mouseX), my = canvas.vy(mouseY), x = LEFT_X + 5, w = LEFT_W - 10;
        for (int i = 0; i < sections.size(); i++) {
            if (SetupCanvas.in(mx, my, x, groupsY + i * GROUP_H, w, GROUP_H - 1)) {
                scroll = Mth.clamp(offsets[i], 0, scrollMax);
                click();
                return true;
            }
        }
        if (SetupCanvas.in(mx, my, x, filterY, w, 10)) {
            onlyTrained = !onlyTrained;
            scroll = 0;
            click();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fillGradient(0, 0, width, height, AflUiStyle.argb(0x0A0B0C, 0.55F), AflUiStyle.argb(0x0A0B0C, 0.8F));
        float mx = canvas.vx(mouseX), my = canvas.vy(mouseY);
        CompoundTag sheet = SurvivorSheetClient.get();
        CompoundTag view = sheet.getCompound("View");
        canvas.push(g);
        SetupCanvas.keyHint(g, font, "Esc", SetupCanvas.tr("newworld.back"), 15, 12, 0.75F);
        SetupCanvas.centred(g, font, title, 240, 9, AflUiStyle.TEXT, 1, 1.3F);
        if (!sheet.contains("First", Tag.TAG_STRING) || !view.contains("Growth", Tag.TAG_LIST)) {
            sections = List.of();
            SetupCanvas.centred(g, font, SetupCanvas.tr("character.none"), 240, 130, AflUiStyle.TEXT_DIM, 1, 0.9F);
            canvas.pop(g);
            return;
        }
        String last = sheet.getString("Last");
        SetupCanvas.centred(g, font, Component.literal(last.isEmpty() ? sheet.getString("First") : sheet.getString("First") + " " + last),
                240, 23, AflUiStyle.TEXT_DIM, 1, 0.62F);
        Rows rows = layout(view);
        int current = currentSection();
        List<Component> tip = left(g, view, current, mx, my);
        List<Component> rowTip = right(g, rows, current, mx, my);
        if (rowTip != null) tip = rowTip;
        if (tip != null) tooltip(g, tip, mx, my);
        canvas.pop(g);
    }

    /** The sections (body, then each growth group in data order), filtered; their offsets; the row metrics. */
    private Rows layout(CompoundTag view) {
        List<Section> list = new ArrayList<>();
        list.add(new Section("body", SetupCanvas.tr("setup.body"), filter(tags(view.getList("Body", Tag.TAG_COMPOUND)), true), true));
        Map<String, List<CompoundTag>> groups = new LinkedHashMap<>();
        for (CompoundTag item : tags(view.getList("Growth", Tag.TAG_COMPOUND))) {
            String group = item.getString("Group");
            groups.computeIfAbsent(group.isEmpty() ? "general" : group, k -> new ArrayList<>()).add(item);
        }
        groups.forEach((group, items) -> list.add(new Section(group, SetupCanvas.tr("growth_page.group." + group), filter(items, false), false)));
        list.removeIf(s -> s.items().isEmpty());
        int count = 0;
        for (Section s : list) count += s.items().size();
        Rows rows = count > ROOMY_ITEMS ? Rows.COMPACT : Rows.ROOMY;
        offsets = new float[list.size()];
        float y = 0;
        for (int i = 0; i < list.size(); i++) {
            offsets[i] = y;
            y += SECTION_H + list.get(i).items().size() * rows.h() + SECTION_GAP;
        }
        float visible = BOTTOM - TOP - 8;
        scrollMax = Math.max(0, y - SECTION_GAP - visible);
        scroll = Mth.clamp(scroll, 0, scrollMax);
        sections = list;
        return rows;
    }

    /** "Only trained ones": items with experience or above where this survivor started from (the item's default). */
    private static List<CompoundTag> filter(List<CompoundTag> items, boolean body) {
        if (!onlyTrained) return items;
        List<CompoundTag> out = new ArrayList<>();
        for (CompoundTag item : items) {
            if (item.getInt("Xp") > 0 || item.getInt("Lv") > item.getInt("Default")) out.add(item);
        }
        return out;
    }

    /** The section whose rows are at the top of the view. */
    private int currentSection() {
        int current = 0;
        for (int i = 0; i < offsets.length; i++) if (offsets[i] <= scroll + 0.5F) current = i;
        return current;
    }

    // ---- left: learning, growth traits, the group index, the filter, the rules

    @Nullable
    private List<Component> left(GuiGraphics g, CompoundTag view, int current, float mx, float my) {
        AflUiDraw.panel(g, LEFT_X, TOP, LEFT_W, BOTTOM - TOP, 1);
        float x = LEFT_X + 7, w = LEFT_W - 14, y = TOP + 6;
        SetupCanvas.text(g, font, SetupCanvas.tr("growth_page.learning"), x, y, AflUiStyle.TEXT_DIM, 1, 0.7F);
        y += 12;
        Player player = minecraft.player;
        double learn = player == null ? 1 : attribute(player, AflAttributes.GROWTH_XP.get());
        double body = learn * (player == null ? 1 : attribute(player, AflAttributes.BODY_GROWTH_XP.get()));
        y = row(g, x, w, y, SetupCanvas.tr("growth_page.speed.learn"), pace(learn));
        y = row(g, x, w, y, SetupCanvas.tr("growth_page.speed.body"), pace(body));
        List<Component> tip = null;
        List<CompoundTag> traits = new ArrayList<>();
        for (Tag t : view.getList("Traits", Tag.TAG_COMPOUND)) if (aboutGrowth((CompoundTag) t)) traits.add((CompoundTag) t);
        if (!traits.isEmpty()) {
            y += 2;
            for (CompoundTag trait : traits) {
                boolean hover = SetupCanvas.in(mx, my, x, y, w, CHIP_H);
                AflUiShapes.box(g, x, y, w, CHIP_H, AflUiStyle.RADIUS_TILE, hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK, 0.85F,
                        AflUiStyle.LINE, 0.16F, 0, 0);
                int cost = trait.getInt("Cost");
                AflUiShapes.fill(g, x + 4, y + 4.3F, 2.4F, 2.4F, 1.2F, cost < 0 ? OVER_CAP : cost == 0 ? AflUiStyle.TEXT_DIM : 0x8FB98A, 1);
                SetupCanvas.text(g, font, AflSurvivorSetupScreen.label("trait", trait.getString("Id")), x + 9, y + 2.8F, AflUiStyle.TEXT, 1, 0.58F);
                if (hover) tip = List.of(AflSurvivorSetupScreen.label("trait", trait.getString("Id")), AflCharacterScreen.describe(trait));
                y += CHIP_H + CHIP_GAP;
            }
        }
        y += 3;
        AflUiShapes.fill(g, LEFT_X + 6, y, LEFT_W - 12, 0.4F, 0, AflUiStyle.LINE, 0.16F);
        y += 5;
        Component groupsHead = SetupCanvas.tr("growth_page.groups");
        SetupCanvas.text(g, font, groupsHead, x, y, AflUiStyle.TEXT_DIM, 1, 0.7F);
        SetupCanvas.text(g, font, SetupCanvas.tr("growth_page.groups_hint"), x + SetupCanvas.width(font, groupsHead, 0.7F) + 4, y + 1.2F,
                AflUiStyle.TEXT_DISABLED, 1, 0.5F);
        y += 11;
        groupsY = y;
        float gx = LEFT_X + 5, gw = LEFT_W - 10;
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            boolean on = i == current, hover = SetupCanvas.in(mx, my, gx, y, gw, GROUP_H - 1);
            if (on) AflUiShapes.box(g, gx, y, gw, GROUP_H - 1, 2.2F, AflUiStyle.ACCENT, 0.12F, AflUiStyle.ACCENT, 0.5F, 0, 0);
            else if (hover) AflUiShapes.fill(g, gx, y, gw, GROUP_H - 1, 2.2F, AflUiStyle.BACK_HOVER, 0.8F);
            SetupCanvas.text(g, font, s.heading(), gx + 4, y + 2.6F, AflUiStyle.TEXT, 1, 0.62F);
            SetupCanvas.right(g, font, Component.literal(Integer.toString(s.items().size())), gx + gw - 4, y + 2.8F, AflUiStyle.TEXT_DISABLED, 1, 0.55F);
            y += GROUP_H;
        }
        y += 3;
        AflUiShapes.fill(g, LEFT_X + 6, y, LEFT_W - 12, 0.4F, 0, AflUiStyle.LINE, 0.16F);
        y += 5;
        filterY = y;
        AflUiShapes.box(g, x, y + 1, 7, 7, 1.8F, onlyTrained ? AflUiStyle.TEXT : AflUiStyle.BACK, onlyTrained ? 0.92F : 0.5F, AflUiStyle.LINE, 0.4F, 0, 0);
        if (onlyTrained) {
            var pose = g.pose();
            pose.pushPose();
            pose.translate(x + 3.5F, y + 4.5F, 0);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-45));
            AflUiShapes.fill(g, -2.2F, -1.6F, 0.9F, 2.2F, 0, 0x16181A, 1);
            AflUiShapes.fill(g, -2.2F, 0.6F, 4.4F, 0.9F, 0, 0x16181A, 1);
            pose.popPose();
        }
        SetupCanvas.text(g, font, SetupCanvas.tr("growth_page.only_trained"), x + 11, y + 2, AflUiStyle.TEXT, 1, 0.6F);
        float fy = BOTTOM - 6;
        List<FormattedCharSequence> rules = new ArrayList<>(wrap(SetupCanvas.tr("growth_page.footer"), w, 0.55F));
        if (player != null && (player.isCreative() || player.isSpectator())) rules.addAll(wrap(SetupCanvas.tr("growth_page.creative"), w, 0.55F));
        fy -= rules.size() * 7;
        for (FormattedCharSequence line : rules) {
            textSeq(g, line, x, fy, AflUiStyle.TEXT_DISABLED, 0.55F);
            fy += 7;
        }
        return tip;
    }

    /** A trait that changes learning speed or a gun class's start / top. */
    private static boolean aboutGrowth(CompoundTag trait) {
        if (!trait.getCompound("GrowthStart").isEmpty() || !trait.getCompound("GrowthCap").isEmpty()) return true;
        for (Tag e : trait.getList("Effects", Tag.TAG_COMPOUND)) {
            String a = ((CompoundTag) e).getString("A");
            if (a.equals(AflAttributes.GROWTH_XP.getId().toString()) || a.equals(AflAttributes.BODY_GROWTH_XP.getId().toString())) return true;
        }
        return false;
    }

    private static Component pace(double speed) {
        return SetupCanvas.tr("growth_page.pace." + (speed >= 1.1 ? "fast" : speed <= 0.9 ? "slow" : "normal"));
    }

    private static double attribute(Player player, Attribute attribute) {
        var instance = player.getAttribute(attribute);
        double v = instance == null ? attribute.getDefaultValue() : instance.getValue();
        return Double.isFinite(v) ? v : attribute.getDefaultValue();
    }

    // ---- right: the sections

    @Nullable
    private List<Component> right(GuiGraphics g, Rows rows, int current, float mx, float my) {
        AflUiDraw.panel(g, RIGHT_X, TOP, RIGHT_W, BOTTOM - TOP, 1);
        float top = TOP + 4, bottom = BOTTOM - 4;
        float x = RIGHT_X + 9, right = RIGHT_X + RIGHT_W - 12;
        if (sections.isEmpty()) {
            SetupCanvas.text(g, font, SetupCanvas.tr("growth_page.none_trained"), x, top + 4, AflUiStyle.TEXT_DISABLED, 1, 0.6F);
            return null;
        }
        boolean inView = SetupCanvas.in(mx, my, RIGHT_X, top, RIGHT_W, bottom - top);
        List<Component> tip = null;
        canvas.scissor(g, RIGHT_X + 1, top, RIGHT_W - 2, bottom - top);
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            float y = top + 2 + offsets[i] - scroll;
            heading(g, s, x, right, y);
            y += SECTION_H;
            for (CompoundTag item : s.items()) {
                if (y + rows.h() > top && y < bottom) {
                    List<Component> t = item(g, rows, x, right, y, item, s.body(), inView && my > top + SECTION_H, mx, my);
                    if (t != null) tip = t;
                }
                y += rows.h();
            }
        }
        // the group in view keeps its header at the top while its rows scroll under it
        if (scroll > offsets[current] + 0.5F) {
            AflUiShapes.fill(g, RIGHT_X + 1, top, RIGHT_W - 2, SECTION_H, 0, 0x161718, 0.97F);
            heading(g, sections.get(current), x, right, top);
        }
        g.disableScissor();
        if (scrollMax > 0) {
            float track = bottom - top, content = scrollMax + track, thumb = Math.max(12, track * track / content);
            float ty = top + (track - thumb) * (scroll / scrollMax);
            AflUiShapes.fill(g, RIGHT_X + RIGHT_W - 4.5F, ty, 1.6F, thumb, 0.8F, AflUiStyle.LINE, 0.35F);
        }
        return tip;
    }

    private void heading(GuiGraphics g, Section s, float x, float right, float y) {
        SetupCanvas.text(g, font, s.heading(), x, y + 3, AflUiStyle.TEXT_DIM, 1, 0.7F);
        SetupCanvas.text(g, font, SetupCanvas.tr("growth_page.count", s.items().size()), x + SetupCanvas.width(font, s.heading(), 0.7F) + 4,
                y + 4.2F, AflUiStyle.TEXT_DISABLED, 1, 0.5F);
        AflUiShapes.fill(g, x, y + 12, right - x, 0.5F, 0, AflUiStyle.ACCENT, 0.3F);
    }

    /** One item's row; returns the hover text under the mouse (a pill, or the name for the item's standing). */
    @Nullable
    private List<Component> item(GuiGraphics g, Rows r, float x, float right, float y, CompoundTag item, boolean body, boolean hoverable,
                                 float mx, float my) {
        String id = item.getString("Id");
        boolean gun = !body && "firearms".equals(item.getString("Group"));
        int level = item.getInt("Lv"), max = Math.max(1, item.getInt("Max")), cap = item.getInt("Cap");
        int xp = item.getInt("Xp"), next = item.getInt("Next");
        // the whole way to this survivor's top, under the level (user 2026-10-18); compact rows keep it in the name's hover
        Component whole = SetupCanvas.tr("growth_page.xp", item.getInt("XpTotal"), item.getInt("XpFull"));
        CompoundTag perks = item.getCompound("Perks");
        Component name = AflSurvivorSetupScreen.label(body ? "body" : "growth", id);
        float midY = r.statusLine() ? y + 5.5F : y + r.h() / 2 - 0.5F;
        GrowthIcons.item(g, id, body, x, midY - r.iconH() / 2, r.iconW(), r.iconH(), AflUiStyle.TEXT, 0.9F);
        SetupCanvas.text(g, font, name, x + r.nameX(), midY - r.nameSize() * 3.5F, AflUiStyle.TEXT, 1, r.nameSize());
        Component standing = gun ? SetupCanvas.tr("growth_page.level_tier", level, SetupCanvas.tr("growth_page.tier." + SurvivorGrowth.tier(level)))
                : SetupCanvas.tr("setup.level", level);
        SetupCanvas.right(g, font, standing, right, midY - r.labelSize() * 3.5F, AflUiStyle.TEXT, 1, r.labelSize());
        List<Component> tip = null;
        // a perk at level 0 (what the survivor does before learning anything): a mark before the first pill
        if (perks.contains("0")) {
            float mark = r.pillH() * 1.15F, mx0 = x + r.pillsX() - r.gap() - mark - 1, my0 = midY - mark / 2;
            GrowthIcons.glyph(g, GrowthIcons.perk(perks.getString("0")), mx0, my0, mark, AflUiStyle.TEXT_DIM, 0.85F);
            if (hoverable && SetupCanvas.in(mx, my, mx0 - 1, my0 - 1, mark + 2, mark + 2)) {
                tip = List.of(SetupCanvas.tr("growth_page.tip_start", name), SetupCanvas.tr("perk." + perks.getString("0")),
                        SetupCanvas.tr("perk." + perks.getString("0") + ".desc"));
            }
        }
        for (int i = 1; i <= max; i++) {
            float px = x + r.pillsX() + (i - 1) * (r.pillW() + r.gap()), py = midY - r.pillH() / 2;
            float radius = r.pillH() / 4;
            boolean reached = i <= level, training = !reached && i == level + 1 && i <= cap && next > 0, locked = i > cap;
            if (reached) {
                AflUiShapes.fill(g, px, py, r.pillW(), r.pillH(), radius, AflUiStyle.TEXT, 0.88F);
            } else if (locked) {
                AflUiShapes.box(g, px, py, r.pillW(), r.pillH(), radius, OVER_CAP, 0.1F, OVER_CAP, 0.35F, 0, 0);
            } else if (training) {
                int colour = AflUiStyle.ACCENT;
                AflUiShapes.box(g, px, py, r.pillW(), r.pillH(), radius, AflUiStyle.BACK, 0.5F, colour, 0.8F, 0, 0);
                float ratio = Math.min(1, xp / (float) next);
                if (ratio > 0) AflUiShapes.fill(g, px + 0.9F, py + 0.9F, Math.max(1.4F, (r.pillW() - 1.8F) * ratio), r.pillH() - 1.8F, radius * 0.7F, colour, 0.75F);
            } else {
                AflUiShapes.box(g, px, py, r.pillW(), r.pillH(), radius, AflUiStyle.BACK, 0.45F, AflUiStyle.LINE, 0.3F, 0, 0);
            }
            float mark = r.pillH() * 0.85F;
            String perk = perks.getString(Integer.toString(i));
            if (locked) {
                GrowthIcons.glyph(g, GrowthIcons.LOCK, px + r.pillW() / 2 - mark / 2, py + (r.pillH() - mark) / 2, mark, OVER_CAP, 0.7F);
            } else if (!perk.isEmpty()) {
                float big = r.pillH() * 1.05F;
                GrowthIcons.glyph(g, GrowthIcons.perk(perk), px + r.pillW() / 2 - big / 2, py + (r.pillH() - big) / 2, big,
                        reached ? AflUiStyle.BACK : AflUiStyle.TEXT, reached ? 0.9F : 0.8F);
            }
            if (hoverable && SetupCanvas.in(mx, my, px - r.gap() / 2, py - 1, r.pillW() + r.gap(), r.pillH() + 2)) {
                tip = pillTip(item, name, i, gun, perk);
            }
        }
        Component status = status(id, level, max, next);
        int colour = next <= 0 && level < max ? OVER_CAP : AflUiStyle.TEXT_DIM;
        if (r.statusLine()) {
            SetupCanvas.text(g, font, status, x + r.pillsX(), y + 12.5F, colour, 1, 0.55F);
            SetupCanvas.right(g, font, whole, right, y + 12.5F, AflUiStyle.TEXT_DIM, 1, 0.55F);
        }
        if (tip == null && hoverable && SetupCanvas.in(mx, my, x, y, r.pillsX() - 2, r.h())) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.empty().append(name).append(" ").append(standing));
            lines.add(whole);
            lines.add(status);
            tip = lines;
        }
        return tip;
    }

    /** How to train it, or that it is at the top / this survivor's top. */
    private static Component status(String id, int level, int max, int next) {
        if (next <= 0) return SetupCanvas.tr(level >= max ? "growth_page.top" : "growth_page.capped");
        return SetupCanvas.tr("growth_page.how." + id.substring(id.indexOf(':') + 1));
    }

    /**
     * What a level gives: what is learned there (a node), the readouts at it (items with curves: body stats, cold / heat
     * tolerance), for a gun class the aiming sway and recoil recovery at it.
     */
    private List<Component> pillTip(CompoundTag item, Component name, int level, boolean gun, String perk) {
        List<Component> lines = new ArrayList<>();
        lines.add(SetupCanvas.tr("growth_page.tip_level", name, level));
        if (!perk.isEmpty()) {
            lines.add(SetupCanvas.tr("growth_page.perk", SetupCanvas.tr("perk." + perk)));
            lines.add(SetupCanvas.tr("perk." + perk + ".desc"));
        }
        if (item.contains("Curves", Tag.TAG_LIST)) {
            List<Component> parts = new ArrayList<>();
            for (Tag t : item.getList("Curves", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(c.getString("A")));
                ListTag values = c.getList("V", Tag.TAG_DOUBLE);
                if (attribute == null || values.isEmpty()) continue;
                double v = values.getDouble(Mth.clamp(level, 1, values.size()) - 1);
                boolean multiply = c.getBoolean("M");
                if (multiply ? Math.abs(v - 1) > 1e-9 : Math.abs(v) > 1e-9) parts.add(AflSurvivorSetupScreen.effect(attribute, multiply, v));
            }
            lines.addAll(parts);
        }
        if (gun) {
            lines.add(SetupCanvas.tr("growth_page.gun_ads", AflSurvivorSetupScreen.number(SurvivorGrowth.adsSway(level))));
            lines.add(SetupCanvas.tr("growth_page.gun_recoil", AflSurvivorSetupScreen.number(SurvivorGrowth.recoilRecovery(level))));
        }
        return lines;
    }

    // ---- helpers

    private void tooltip(GuiGraphics g, List<Component> lines, float mx, float my) {
        float size = 0.62F, lineH = 8, pad = 4;
        float w = 0;
        for (Component line : lines) w = Math.max(w, SetupCanvas.width(font, line, size));
        float h = lines.size() * lineH - 1.5F;
        float x = mx + 8, y = my + 8;
        if (x + w + pad > SetupCanvas.W - 4) x = mx - 8 - w;
        if (y + h + pad > SetupCanvas.H - 4) y = SetupCanvas.H - 4 - h - pad;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        AflUiShapes.box(g, x - pad, y - pad, w + 2 * pad, h + 2 * pad, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.95F,
                AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
        for (int i = 0; i < lines.size(); i++) {
            SetupCanvas.text(g, font, lines.get(i), x, y + i * lineH, i == 0 ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1, size);
        }
        pose.popPose();
    }

    private float row(GuiGraphics g, float x, float w, float y, Component key, Component value) {
        SetupCanvas.text(g, font, key, x, y, AflUiStyle.TEXT_DIM, 1, 0.6F);
        SetupCanvas.right(g, font, value, x + w, y, AflUiStyle.TEXT, 1, 0.6F);
        return y + 9;
    }

    private static List<CompoundTag> tags(ListTag tags) {
        List<CompoundTag> out = new ArrayList<>();
        for (Tag t : tags) out.add((CompoundTag) t);
        return out;
    }

    private List<FormattedCharSequence> wrap(Component text, float width, float size) {
        return font.split(text, Math.max(1, (int) (width / size)));
    }

    private void textSeq(GuiGraphics g, FormattedCharSequence line, float x, float y, int rgb, float size) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size, size, 1);
        g.drawString(font, line, 0, 0, AflUiStyle.argb(rgb, 1), false);
        g.pose().popPose();
    }
}
