package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.registry.AflAttributes;
import com.antaurora.apofirstlight.survivor.SurvivorCatalog;
import com.antaurora.apofirstlight.survivor.SurvivorScenes;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The inventory's character page (docs/gameplay/survivor_background_skills_v1_plan.md section 9, P2c): who the survivor
 * is (background, start scene), the body levels, the traits, and every survivor attribute that is not an ordinary
 * person's right now. View only; growth is shown elsewhere later. Reads the sheet the server sends (SurvivorSheetClient,
 * its View part carries what a multiplayer client has no catalog for) and the player's live attribute values. Opened
 * from the inventory's title row; Esc or the inventory key goes back to the inventory.
 */
public final class AflCharacterScreen extends Screen {
    private static final float TOP = 38, BOTTOM = 255;
    private static final float LEFT_X = 15, LEFT_W = 125, PREVIEW_H = 96, BACK_Y = TOP + PREVIEW_H + 4;
    private static final float MID_X = 145, MID_W = 190;
    private static final float RIGHT_X = 340, RIGHT_W = 125;
    private static final float BODY_ROW = 14, TRAIT_HEAD_H = 26, CHIP_H = 12, CHIP_GAP = 3;
    private static final float ROW = 8, TEXT = 0.58F;
    /** Shown as their own rows with the base added (the setup page's start readouts). */
    private static final Set<String> BASE_ROWS = Set.of("carry_capacity", "max_stamina", "cold_threshold", "heat_threshold");
    private static final int RED = 0xE06A5A;

    private final SetupCanvas canvas = new SetupCanvas();
    private final SurvivorDoll doll = new SurvivorDoll();
    private float traitScroll, traitMax, attrScroll, attrMax;

    public AflCharacterScreen() {
        super(SetupCanvas.tr("character.title"));
    }

    @Override
    protected void init() {
        canvas.layout(minecraft, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Back to the inventory (the vanilla screen; the grid inventory takes its place as usual). */
    @Override
    public void onClose() {
        if (minecraft.player != null) minecraft.setScreen(new InventoryScreen(minecraft.player));
        else super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float mx = canvas.vx(mouseX), my = canvas.vy(mouseY);
        if (SetupCanvas.in(mx, my, RIGHT_X, TOP, RIGHT_W, BOTTOM - TOP)) attrScroll = Mth.clamp(attrScroll - (float) delta * 12, 0, attrMax);
        else if (SetupCanvas.in(mx, my, MID_X, TOP, MID_W, BOTTOM - TOP)) traitScroll = Mth.clamp(traitScroll - (float) delta * 12, 0, traitMax);
        return true;
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
        if (!sheet.contains("First", Tag.TAG_STRING)) {
            SetupCanvas.centred(g, font, SetupCanvas.tr("character.none"), 240, 130, AflUiStyle.TEXT_DIM, 1, 0.9F);
            canvas.pop(g);
            return;
        }
        String last = sheet.getString("Last");
        Component name = Component.literal(last.isEmpty() ? sheet.getString("First") : sheet.getString("First") + " " + last);
        SetupCanvas.centred(g, font, name, 240, 9, AflUiStyle.TEXT, 1, 1.3F);
        SetupCanvas.centred(g, font, SetupCanvas.tr("character.life", Math.max(1, sheet.getInt("Life"))), 240, 23, AflUiStyle.TEXT_DIM, 1, 0.62F);
        preview(g, mouseX, mouseY);
        background(g, sheet);
        float traitY = body(g, sheet, view);
        traits(g, view, traitY, mx, my);
        attributes(g, view);
        canvas.pop(g);
    }

    private void preview(GuiGraphics g, int mouseX, int mouseY) {
        AflUiDraw.panel(g, LEFT_X, TOP, LEFT_W, PREVIEW_H, 1);
        if (minecraft.player == null) return;
        float cx = LEFT_X + LEFT_W / 2, feet = TOP + PREVIEW_H - 7, size = 40;
        canvas.scissor(g, LEFT_X + 1, TOP + 1, LEFT_W - 2, PREVIEW_H - 2);
        float sx = canvas.originX + cx * canvas.scale, sy = canvas.originY + (feet - size * 1.6F) * canvas.scale;
        doll.render(g, SurvivorDoll.Skin.of(minecraft.player), cx, feet, size, (sx - mouseX) / canvas.scale, (sy - mouseY) / canvas.scale);
        g.disableScissor();
    }

    /** Background name and what it was, then the start scene. */
    private void background(GuiGraphics g, CompoundTag sheet) {
        AflUiDraw.panel(g, LEFT_X, BACK_Y, LEFT_W, BOTTOM - BACK_Y, 1);
        float x = LEFT_X + 7, w = LEFT_W - 14, y = BACK_Y + 6;
        SetupCanvas.text(g, font, SetupCanvas.tr("setup.background"), x, y, AflUiStyle.TEXT_DIM, 1, 0.7F);
        y += 11;
        String background = sheet.getString("Background");
        if (!background.isEmpty()) {
            SetupCanvas.text(g, font, AflSurvivorSetupScreen.label("background", background), x, y, AflUiStyle.TEXT, 1, 0.85F);
            y += 11;
            for (FormattedCharSequence line : wrap(Component.translatable(SurvivorCatalog.key("background", background) + ".desc"), w, TEXT)) {
                textSeq(g, line, x, y, AflUiStyle.TEXT_DIM, TEXT);
                y += 7;
            }
        } else {
            SetupCanvas.text(g, font, AflSurvivorSetupScreen.label("background", "ordinary"), x, y, AflUiStyle.TEXT, 1, 0.85F);
            y += 11;
        }
        if (sheet.contains("Scene", Tag.TAG_STRING)) {
            y += 4;
            AflUiShapes.fill(g, LEFT_X + 6, y - 3, LEFT_W - 12, 0.4F, 0, AflUiStyle.LINE, 0.16F);
            row(g, x, w, y, SetupCanvas.tr("setup.scene"), SetupCanvas.tr("scene." + SurvivorScenes.byId(sheet.getString("Scene")).name()));
        }
    }

    /** One row per body item: pips up to its top level, the level. Returns where the trait panel starts. */
    private float body(GuiGraphics g, CompoundTag sheet, CompoundTag view) {
        List<CompoundTag> items = new ArrayList<>();
        for (Tag t : view.getList("Body", Tag.TAG_COMPOUND)) items.add((CompoundTag) t);
        float h = 18 + items.size() * BODY_ROW + 2;
        AflUiDraw.panel(g, MID_X, TOP, MID_W, h, 1);
        float x = MID_X + 8, y = TOP + 18;
        SetupCanvas.text(g, font, SetupCanvas.tr("setup.body"), x, TOP + 6, AflUiStyle.TEXT_DIM, 1, 0.7F);
        CompoundTag levels = sheet.getCompound("Body");
        for (CompoundTag item : items) {
            String id = item.getString("Id");
            int max = Math.max(1, item.getInt("Max"));
            int level = levels.contains(id, Tag.TAG_COMPOUND) ? levels.getCompound(id).getInt("Lv") : item.getInt("Default");
            SetupCanvas.text(g, font, AflSurvivorSetupScreen.label("body", id), x, y + 1, AflUiStyle.TEXT, 1, 0.8F);
            float px = x + 40;
            for (int i = 1; i <= max; i++) {
                float cx = px + (i - 1) * 9;
                if (i <= level) AflUiShapes.fill(g, cx, y + 1.5F, 7, 5, 1.2F, AflUiStyle.TEXT, 0.9F);
                else AflUiShapes.box(g, cx, y + 1.5F, 7, 5, 1.2F, AflUiStyle.BACK, 0.4F, AflUiStyle.LINE, 0.3F, 0, 0);
            }
            SetupCanvas.text(g, font, SetupCanvas.tr("setup.level", level), px + max * 9 + 4, y + 1.6F, AflUiStyle.TEXT_DIM, 1, 0.62F);
            y += BODY_ROW;
        }
        return TOP + h + 4;
    }

    /** The traits as chips (two columns, background ones first and marked); the hovered one says what it does on top. */
    private void traits(GuiGraphics g, CompoundTag view, float panelY, float mx, float my) {
        AflUiDraw.panel(g, MID_X, panelY, MID_W, BOTTOM - panelY, 1);
        List<CompoundTag> traits = new ArrayList<>();
        for (Tag t : view.getList("Traits", Tag.TAG_COMPOUND)) traits.add((CompoundTag) t);
        float top = panelY + TRAIT_HEAD_H + 4, bottom = BOTTOM - 5;
        float chipW = (MID_W - 16 - CHIP_GAP) / 2;
        int rows = (traits.size() + 1) / 2;
        float content = rows * (CHIP_H + CHIP_GAP);
        traitMax = Math.max(0, content - (bottom - top));
        traitScroll = Mth.clamp(traitScroll, 0, traitMax);
        boolean inList = SetupCanvas.in(mx, my, MID_X, top, MID_W, bottom - top);
        CompoundTag shown = null;
        canvas.scissor(g, MID_X, top, MID_W, bottom - top);
        for (int i = 0; i < traits.size(); i++) {
            CompoundTag t = traits.get(i);
            String id = t.getString("Id");
            boolean fixed = t.getBoolean("Fixed");
            float x = MID_X + 8 + (i % 2) * (chipW + CHIP_GAP), y = top + (i / 2) * (CHIP_H + CHIP_GAP) - traitScroll;
            boolean hover = inList && SetupCanvas.in(mx, my, x, y, chipW, CHIP_H);
            if (hover) shown = t;
            AflUiShapes.box(g, x, y, chipW, CHIP_H, AflUiStyle.RADIUS_TILE, hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK, 0.85F,
                    fixed ? AflUiStyle.ACCENT : AflUiStyle.LINE, fixed ? 0.4F : 0.16F, 0, 0);
            AflUiShapes.fill(g, x + 4, y + 4.8F, 2.4F, 2.4F, 1.2F, dot(t), 1);
            SetupCanvas.text(g, font, AflSurvivorSetupScreen.label("trait", id), x + 9, y + 3.2F, AflUiStyle.TEXT, 1, 0.6F);
            if (fixed) SetupCanvas.right(g, font, SetupCanvas.tr("setup.locked"), x + chipW - 3.5F, y + 3.8F, AflUiStyle.ACCENT, 1, 0.5F);
        }
        g.disableScissor();
        if (traitMax > 0) {
            float track = bottom - top, thumb = Math.max(12, track * track / content);
            float ty = top + (track - thumb) * (traitScroll / traitMax);
            AflUiShapes.fill(g, MID_X + MID_W - 4, ty, 1.6F, thumb, 0.8F, AflUiStyle.LINE, 0.35F);
        }
        traitSummary(g, panelY, traits.isEmpty(), shown);
    }

    private void traitSummary(GuiGraphics g, float panelY, boolean none, @Nullable CompoundTag t) {
        float x = MID_X + 8, y = panelY + 6, w = MID_W - 16;
        AflUiShapes.fill(g, MID_X + 6, panelY + TRAIT_HEAD_H + 1, MID_W - 12, 0.4F, 0, AflUiStyle.LINE, 0.16F);
        if (t == null) {
            SetupCanvas.text(g, font, SetupCanvas.tr("setup.traits"), x, y, AflUiStyle.TEXT_DIM, 1, 0.7F);
            SetupCanvas.text(g, font, SetupCanvas.tr(none ? "character.no_traits" : "character.trait_hint"), x, y + 10, AflUiStyle.TEXT_DISABLED, 1, 0.55F);
            return;
        }
        String id = t.getString("Id");
        Component name = AflSurvivorSetupScreen.label("trait", id);
        SetupCanvas.text(g, font, name, x, y, AflUiStyle.TEXT, 1, 0.75F);
        if (t.getBoolean("Fixed")) {
            SetupCanvas.text(g, font, SetupCanvas.tr("setup.locked_long"), x + SetupCanvas.width(font, name, 0.75F) + 5, y + 0.8F,
                    t.getInt("Cost") < 0 ? RED : AflUiStyle.ACCENT, 1, 0.58F);
        }
        float ty = y + 9;
        for (FormattedCharSequence line : wrap(describe(t), w, 0.55F)) {
            if (ty > panelY + TRAIT_HEAD_H - 5) break;
            textSeq(g, line, x, ty, AflUiStyle.TEXT_DIM, 0.55F);
            ty += 6.5F;
        }
    }

    /** What a trait does, worded as on the setup page (effects, body bonus, the gun flags). */
    private static Component describe(CompoundTag t) {
        List<Component> parts = new ArrayList<>();
        for (Tag e : t.getList("Effects", Tag.TAG_COMPOUND)) {
            CompoundTag ef = (CompoundTag) e;
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(ef.getString("A")));
            if (attribute != null) parts.add(AflSurvivorSetupScreen.effect(attribute, ef.getBoolean("M"), ef.getDouble("V")));
        }
        CompoundTag bonus = t.getCompound("BodyBonus");
        for (String body : bonus.getAllKeys()) {
            parts.add(SetupCanvas.tr("setup.effect.body_bonus", AflSurvivorSetupScreen.label("body", body), AflSurvivorSetupScreen.signed(bonus.getInt(body))));
        }
        if (t.getBoolean("GunStart")) parts.add(SetupCanvas.tr("setup.effect.gun_experience"));
        if (t.getBoolean("GunCap")) parts.add(SetupCanvas.tr("setup.effect.gun_capped"));
        return parts.isEmpty() ? SetupCanvas.tr("setup.no_change") : AflSurvivorSetupScreen.join(parts);
    }

    /**
     * The numbers as they are now (live attribute values: body, traits and anything else that changes them): comfortable
     * load, stamina, comfort band and health with their base, then every other survivor attribute that differs from an
     * ordinary person's. Creative ignores them (SurvivorStats); the page still shows them, with a note.
     */
    private void attributes(GuiGraphics g, CompoundTag view) {
        AflUiDraw.panel(g, RIGHT_X, TOP, RIGHT_W, BOTTOM - TOP, 1);
        Player player = minecraft.player;
        if (player == null) return;
        float x = RIGHT_X + 8, w = RIGHT_W - 16;
        SetupCanvas.text(g, font, SetupCanvas.tr("character.attributes"), x, TOP + 6, AflUiStyle.TEXT_DIM, 1, 0.7F);
        boolean creative = player.isCreative() || player.isSpectator();
        float top = TOP + 18, bottom = BOTTOM - (creative ? 13 : 5);
        canvas.scissor(g, RIGHT_X + 1, top, RIGHT_W - 2, bottom - top);
        float y = top + 1 - attrScroll;
        CompoundTag bases = view.getCompound("Bases");
        y = row(g, x, w, y, name(AflAttributes.CARRY_CAPACITY),
                SetupCanvas.tr("setup.unit.kg", AflSurvivorSetupScreen.number(bases.getDouble("apocalypse_firstlight:carry_capacity") + value(player, AflAttributes.CARRY_CAPACITY))));
        y = row(g, x, w, y, name(AflAttributes.MAX_STAMINA),
                Component.literal(AflSurvivorSetupScreen.number(bases.getDouble("apocalypse_firstlight:max_stamina") + value(player, AflAttributes.MAX_STAMINA))));
        double cold = bases.getDouble("apocalypse_firstlight:cold_threshold") + value(player, AflAttributes.COLD_THRESHOLD);
        double heat = bases.getDouble("apocalypse_firstlight:heat_threshold") + value(player, AflAttributes.HEAT_THRESHOLD);
        y = row(g, x, w, y, SetupCanvas.tr("setup.comfort"),
                SetupCanvas.tr("setup.unit.range_c", AflSurvivorSetupScreen.number(cold), AflSurvivorSetupScreen.number(heat)));
        y = row(g, x, w, y, Component.translatable("attribute.name.generic.max_health"), Component.literal(AflSurvivorSetupScreen.number(player.getMaxHealth())));
        y += 2;
        AflUiShapes.fill(g, RIGHT_X + 6, y - 1, RIGHT_W - 12, 0.4F, 0, AflUiStyle.LINE, 0.16F);
        y += 2;
        int others = 0;
        for (RegistryObject<Attribute> object : AflAttributes.all()) {
            if (BASE_ROWS.contains(object.getId().getPath())) continue;
            Attribute attribute = object.get();
            double v = value(player, object), base = attribute.getDefaultValue();
            if (Math.abs(v - base) < 1e-9) continue;
            // multipliers default to 1, offsets to 0 (AflAttributes)
            y = row(g, x, w, y, name(object), Component.literal(AflSurvivorSetupScreen.amount(attribute, base == 1, v)));
            others++;
        }
        if (others == 0) {
            SetupCanvas.text(g, font, SetupCanvas.tr("character.ordinary_numbers"), x, y, AflUiStyle.TEXT_DISABLED, 1, 0.55F);
            y += ROW;
        }
        g.disableScissor();
        float content = y + attrScroll - top + 2, visible = bottom - top;
        attrMax = Math.max(0, content - visible);
        attrScroll = Mth.clamp(attrScroll, 0, attrMax);
        if (attrMax > 0) {
            float thumb = Math.max(10, visible * visible / content), ty = top + (visible - thumb) * (attrScroll / attrMax);
            AflUiShapes.fill(g, RIGHT_X + RIGHT_W - 4, ty, 1.6F, thumb, 0.8F, AflUiStyle.LINE, 0.35F);
        }
        if (creative) SetupCanvas.text(g, font, SetupCanvas.tr("character.creative"), x, BOTTOM - 10, AflUiStyle.TEXT_DISABLED, 1, 0.52F);
    }

    /** The player's own value of an attribute (not SurvivorStats: the page shows the survivor's numbers in Creative too). */
    private static double value(Player player, RegistryObject<Attribute> object) {
        var instance = player.getAttribute(object.get());
        double v = instance == null ? object.get().getDefaultValue() : instance.getValue();
        return Double.isFinite(v) ? v : object.get().getDefaultValue();
    }

    private static Component name(RegistryObject<Attribute> object) {
        return Component.translatable(object.get().getDescriptionId());
    }

    private float row(GuiGraphics g, float x, float w, float y, Component key, Component value) {
        SetupCanvas.text(g, font, key, x, y, AflUiStyle.TEXT_DIM, 1, TEXT);
        SetupCanvas.right(g, font, value, x + w, y, AflUiStyle.TEXT, 1, TEXT);
        return y + ROW;
    }

    /** The setup page's trait mark: green for traits that cost points, red for ones that give, grey for free ones. */
    private static int dot(CompoundTag t) {
        int cost = t.getInt("Cost");
        return cost < 0 ? 0xD0705E : cost == 0 ? AflUiStyle.TEXT_DIM : 0x8FB98A;
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
