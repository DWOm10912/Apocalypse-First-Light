package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.survivor.SurvivorCatalog;
import com.antaurora.apofirstlight.survivor.SurvivorNames;
import com.antaurora.apofirstlight.survivor.SurvivorPackets;
import com.antaurora.apofirstlight.survivor.SurvivorScenes;
import com.antaurora.apofirstlight.survivor.SurvivorSetup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The survivor setup screen (docs/gameplay/survivor_background_skills_v1_plan.md 9, scheme A2): background on the left,
 * body levels and traits in the middle, the survivor, its name and what it starts with on the right. Two ways in:
 * <ul>
 * <li>before the world: the AFL New World page opens it after "Next" (the catalog the data packs just loaded); Start
 * creates the world with the setup handed over (SurvivorSetupFlow confirms it at the host's first login); Esc goes back
 * to the New World page;</li>
 * <li>in the world: a first survivor still choosing (multiplayer, a dev reset, a world made another way) gets it from
 * the server with the catalog files; Start sends the choice; Esc leaves to the title screen.</li>
 * </ul>
 * Everything here is a preview: the screen checks with the same rules as the server (SurvivorSetup.check), and the
 * server checks again.
 */
public final class AflSurvivorSetupScreen extends Screen {
    private static final float TOP = 38, BOTTOM = 255;
    private static final float LEFT_X = 15, LEFT_W = 110;
    private static final float MID_X = 130, MID_W = 205;
    private static final float RIGHT_X = 340, RIGHT_W = 125;
    private static final float BODY_Y = TOP, BODY_H = 62;
    private static final float TRAIT_Y = BODY_Y + BODY_H + 4, TRAIT_HEAD_H = 24;
    private static final float CHIP_H = 12, CHIP_GAP = 3, GROUP_H = 11;
    private static final float PREVIEW_H = 70, NAME_Y = TOP + PREVIEW_H + 4, NAME_H = 36, START_Y = NAME_Y + NAME_H + 4;
    private static final float BUTTON_Y = 238, BUTTON_H = 16;
    private static final int PIPS = 7;

    private final SurvivorCatalog.Snapshot catalog;
    /** The New World page this was opened from (before the world), or null in the world. */
    @Nullable
    private final AflNewWorldScreen newWorld;
    private final SurvivorDoll doll = new SurvivorDoll();
    /** The trait whose conflict was just clicked, and when (the hint flashes red). */
    @Nullable
    private String conflictClicked;
    private long conflictAt;
    private final CompoundTag info;
    private final SetupCanvas canvas = new SetupCanvas();

    private String background;
    private final Map<String, Integer> body = new LinkedHashMap<>();
    private final Set<String> traits = new LinkedHashSet<>();
    private boolean customName;
    private EditBox nameField;
    private boolean nameFocused;
    private float scroll;
    /** The start summary's scroll and how far it can go (set while drawing). */
    private float startScroll, startMax;
    @Nullable
    private String hovered, shown;
    private boolean waiting;
    private List<String> refusal = List.of();
    private final Random random = new Random();

    /** In the world: the server's prompt. */
    public AflSurvivorSetupScreen(SurvivorPackets.SetupPrompt prompt) {
        this(SurvivorCatalog.parse(prompt.catalog()), prompt.info(), null);
    }

    /** Before the world: the catalog the Create World screen's data packs loaded, the scene picked on the page. */
    static AflSurvivorSetupScreen beforeWorld(AflNewWorldScreen newWorld, String scene) {
        CompoundTag info = new CompoundTag();
        info.put("Bases", com.antaurora.apofirstlight.survivor.SurvivorSetupFlow.bases());
        info.putString("Scene", scene);
        info.putString("Account", net.minecraft.client.Minecraft.getInstance().getUser().getName());
        return new AflSurvivorSetupScreen(SurvivorCatalog.get(), info, newWorld);
    }

    private AflSurvivorSetupScreen(SurvivorCatalog.Snapshot catalog, CompoundTag info, @Nullable AflNewWorldScreen newWorld) {
        super(SetupCanvas.tr("setup.title"));
        this.catalog = catalog;
        this.info = info;
        this.newWorld = newWorld;
        String recommended = SurvivorScenes.byId(info.getString("Scene")).recommended();
        background = recommended != null && catalog.backgrounds().containsKey(recommended) ? recommended
                : catalog.backgrounds().isEmpty() ? SurvivorSetup.ORDINARY : catalog.backgrounds().keySet().iterator().next();
        resetBody();
    }

    private void resetBody() {
        body.clear();
        for (SurvivorCatalog.BodyDef def : catalog.body().values()) body.put(def.id(), def.defaultLevel());
    }

    /** Server: the confirm was refused; show why and let the player try again. */
    public void refused(List<String> problems) {
        waiting = false;
        refusal = problems;
    }

    @Override
    protected void init() {
        canvas.layout(minecraft, width, height);
        String value = nameField != null ? nameField.getValue() : "";
        float x = RIGHT_X + 8;
        nameField = new EditBox(font, Math.round(x + 4), Math.round(NAME_Y + 22 + 3), Math.round(RIGHT_W - 16 - 8), 9, Component.empty());
        nameField.setBordered(false);
        nameField.setMaxLength(SurvivorNames.NICKNAME_MAX);
        nameField.setTextColor(AflUiStyle.argb(AflUiStyle.TEXT, 1));
        nameField.setFilter(s -> s.codePoints().allMatch(SurvivorNames::nicknameChar));
        nameField.setValue(value);
        nameField.setFocused(nameFocused);
    }

    @Override
    public void tick() {
        nameField.tick();
    }

    // ---------------------------------------------------------------- rules (preview; the server decides)

    private SurvivorSetup.Choice choice() {
        return new SurvivorSetup.Choice(background, Map.copyOf(body), List.copyOf(traits));
    }

    private SurvivorSetup.Check check() {
        return SurvivorSetup.check(catalog, choice());
    }

    private List<String> fixedTraits() {
        SurvivorCatalog.BackgroundDef def = catalog.backgrounds().get(background);
        return def == null ? List.of() : def.traits();
    }

    private List<String> allTraits() {
        List<String> all = new ArrayList<>(fixedTraits());
        for (String id : traits) if (!all.contains(id)) all.add(id);
        return all;
    }

    private boolean excluded(String id) {
        for (String other : allTraits()) {
            if (other.equals(id)) continue;
            SurvivorCatalog.TraitDef a = catalog.traits().get(other), b = catalog.traits().get(id);
            if (a != null && a.exclusive().contains(id) || b != null && b.exclusive().contains(other)) return true;
        }
        return false;
    }

    private int budget() {
        SurvivorCatalog.BackgroundDef def = catalog.backgrounds().get(background);
        return def == null ? 0 : def.budget();
    }

    /** The body level the survivor starts with (the chosen one plus trait bonuses), as SurvivorSetup.confirm. */
    private int startLevel(SurvivorCatalog.BodyDef def) {
        int level = body.getOrDefault(def.id(), def.defaultLevel());
        for (String id : allTraits()) {
            SurvivorCatalog.TraitDef trait = catalog.traits().get(id);
            if (trait != null) level += trait.bodyBonus().getOrDefault(def.id(), 0);
        }
        return Mth.clamp(level, 1, def.max());
    }

    private int growthLevel(SurvivorCatalog.GrowthDef def) {
        int level = def.defaultLevel(), cap = def.max();
        for (String id : allTraits()) {
            SurvivorCatalog.TraitDef trait = catalog.traits().get(id);
            if (trait == null) continue;
            level = Math.max(level, trait.growthStart().getOrDefault(def.id(), level));
            cap = Math.min(cap, trait.growthCap().getOrDefault(def.id(), cap));
        }
        return Math.max(0, Math.min(cap, level));
    }

    @Nullable
    private String name() {
        if (info.contains("Name")) return info.getString("Name");
        if (!customName) return info.getString("Account");
        return SurvivorNames.nickname(nameField.getValue());
    }

    private boolean ready() {
        return !waiting && name() != null && check().ok();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (newWorld != null) AflNewWorldScreen.PANORAMA.render(partialTick, 1);
        else renderBackground(g);
        g.fillGradient(0, 0, width, height, AflUiStyle.argb(0x0A0B0C, 0.62F), AflUiStyle.argb(0x0A0B0C, 0.86F));
        float mx = canvas.vx(mouseX), my = canvas.vy(mouseY);
        hovered = null;
        SurvivorSetup.Check check = check();
        canvas.push(g);
        header(g, check);
        backgrounds(g, mx, my);
        bodyPanel(g, mx, my);
        traitPanel(g, mx, my);
        preview(g, mouseX, mouseY);
        namePanel(g, mx, my, partialTick);
        startPanel(g);
        footer(g, mx, my, check);
        canvas.pop(g);
    }

    private void header(GuiGraphics g, SurvivorSetup.Check check) {
        SetupCanvas.keyHint(g, font, "Esc", SetupCanvas.tr(newWorld != null ? "newworld.back" : "setup.leave"), 15, 12, 0.75F);
        SetupCanvas.centred(g, font, SetupCanvas.tr("setup.title"), 240, 9, AflUiStyle.TEXT, 1, 1.3F);
        SetupCanvas.centred(g, font, SetupCanvas.tr("setup.subtitle"), 240, 23, AflUiStyle.TEXT_DIM, 1, 0.62F);
        int left = check.pointsLeft();
        Component points = Component.literal(left + " / " + budget());
        SetupCanvas.right(g, font, points, 465, 10, left < 0 ? RED : AflUiStyle.ACCENT, 1, 1.1F);
        float pw = SetupCanvas.width(font, points, 1.1F);
        SetupCanvas.right(g, font, SetupCanvas.tr("setup.points"), 465 - pw - 4, 12, AflUiStyle.TEXT_DIM, 1, 0.65F);
        SetupCanvas.right(g, font, SetupCanvas.tr("setup.negative", check.negativePoints(), SurvivorCatalog.NEGATIVE_POINT_CAP), 465, 23,
                check.negativePoints() > SurvivorCatalog.NEGATIVE_POINT_CAP ? RED : AflUiStyle.TEXT_DIM, 1, 0.55F);
    }

    // ---- left: backgrounds

    private void backgrounds(GuiGraphics g, float mx, float my) {
        AflUiDraw.panel(g, LEFT_X, TOP, LEFT_W, BOTTOM - TOP, 1);
        float x = LEFT_X + 7, w = LEFT_W - 14;
        SetupCanvas.text(g, font, SetupCanvas.tr("setup.background"), x, TOP + 6, AflUiStyle.TEXT_DIM, 1, 0.7F);
        smallButton(g, LEFT_X + LEFT_W - 7 - 26, TOP + 4, 26, SetupCanvas.tr("newworld.random"), mx, my);
        float y = TOP + 18;
        for (SurvivorCatalog.BackgroundDef def : catalog.backgrounds().values()) {
            boolean on = def.id().equals(background);
            float h = backgroundHeight(def, w);
            boolean hover = SetupCanvas.in(mx, my, x, y, w, h);
            AflUiShapes.box(g, x, y, w, h, AflUiStyle.RADIUS_TILE, on || hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK,
                    on ? 0.85F : 0.4F, on ? AflUiStyle.ACCENT : AflUiStyle.LINE, on ? 0.7F : 0.16F, 0, 0);
            SetupCanvas.text(g, font, label("background", def.id()), x + 5, y + 4, AflUiStyle.TEXT, 1, 0.78F);
            SetupCanvas.right(g, font, SetupCanvas.tr("setup.budget", def.budget()), x + w - 5, y + 4.6F, AflUiStyle.TEXT_DIM, 1, 0.6F);
            if (on) {
                float ty = y + 15;
                for (FormattedCharSequence line : wrap(desc("background", def.id()), w - 10, 0.58F)) {
                    textSeq(g, line, x + 5, ty, AflUiStyle.TEXT_DIM, 0.58F);
                    ty += 7;
                }
                if (!def.traits().isEmpty()) {
                    ty += 1;
                    SetupCanvas.text(g, font, SetupCanvas.tr("setup.comes_with"), x + 5, ty, AflUiStyle.TEXT_DIM, 1, 0.55F);
                    ty += 7;
                    for (String id : def.traits()) {
                        boolean hv = SetupCanvas.in(mx, my, x, ty - 1, w, 7);
                        if (hv) hovered = id;
                        AflUiShapes.fill(g, x + 6, ty + 1.8F, 2.4F, 2.4F, 1.2F, dot(id), 1);
                        SetupCanvas.text(g, font, label("trait", id), x + 11, ty, hv ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1, 0.58F);
                        ty += 7;
                    }
                }
            }
            y += h + 3;
        }
        float ny = BOTTOM - 6;
        List<FormattedCharSequence> note = wrap(SetupCanvas.tr("setup.background_note"), w, 0.52F);
        ny -= note.size() * 6.5F;
        for (FormattedCharSequence line : note) {
            textSeq(g, line, x, ny, AflUiStyle.TEXT_DISABLED, 0.52F);
            ny += 6.5F;
        }
    }

    private float backgroundHeight(SurvivorCatalog.BackgroundDef def, float w) {
        if (!def.id().equals(background)) return 14;
        float h = 15 + wrap(desc("background", def.id()), w - 10, 0.58F).size() * 7 + 3;
        if (!def.traits().isEmpty()) h += 8 + def.traits().size() * 7;
        return h;
    }

    // ---- middle: body

    private void bodyPanel(GuiGraphics g, float mx, float my) {
        AflUiDraw.panel(g, MID_X, BODY_Y, MID_W, BODY_H, 1);
        float x = MID_X + 8;
        SetupCanvas.text(g, font, SetupCanvas.tr("setup.body"), x, BODY_Y + 6, AflUiStyle.TEXT_DIM, 1, 0.7F);
        SetupCanvas.right(g, font, SetupCanvas.tr("setup.body_rule"), MID_X + MID_W - 8, BODY_Y + 6.6F, AflUiStyle.TEXT_DISABLED, 1, 0.55F);
        float y = BODY_Y + 18;
        for (SurvivorCatalog.BodyDef def : catalog.body().values()) {
            int chosen = body.getOrDefault(def.id(), def.defaultLevel()), start = startLevel(def);
            SetupCanvas.text(g, font, label("body", def.id()), x, y + 1, AflUiStyle.TEXT, 1, 0.8F);
            float px = x + 34;
            stepButton(g, px, y, false, chosen > def.startMin(), mx, my);
            for (int i = 1; i <= PIPS; i++) {
                float cx = px + 11 + (i - 1) * 8;
                boolean filled = i <= start, bonus = i > chosen && i <= start, reachable = i >= def.startMin() && i <= def.startMax();
                int rgb = bonus ? AflUiStyle.ACCENT : AflUiStyle.TEXT;
                if (filled) AflUiShapes.fill(g, cx, y + 1.5F, 6, 5, 1.2F, rgb, 0.9F);
                else AflUiShapes.box(g, cx, y + 1.5F, 6, 5, 1.2F, AflUiStyle.BACK, 0.4F, AflUiStyle.LINE, reachable ? 0.45F : 0.15F, 0, 0);
            }
            stepButton(g, px + 11 + PIPS * 8 + 2, y, true, chosen < def.startMax(), mx, my);
            float lx = px + 11 + PIPS * 8 + 14;
            SetupCanvas.text(g, font, SetupCanvas.tr("setup.level", start), lx, y + 1.6F, AflUiStyle.TEXT_DIM, 1, 0.62F);
            List<Component> parts = new ArrayList<>();
            for (SurvivorCatalog.Curve curve : def.curves()) {
                double v = curve.values()[Mth.clamp(start, 1, curve.values().length) - 1];
                if (curve.multiply() ? Math.abs(v - 1) > 1e-9 : Math.abs(v) > 1e-9) parts.add(effect(curve.attribute(), curve.multiply(), v));
            }
            Component line = parts.isEmpty() ? SetupCanvas.tr("setup.no_change") : join(parts);
            SetupCanvas.text(g, font, line, x, y + 10, AflUiStyle.TEXT_DIM, 1, 0.55F);
            y += 21;
        }
    }

    private void smallButton(GuiGraphics g, float x, float y, float w, Component label, float mx, float my) {
        boolean hover = SetupCanvas.in(mx, my, x, y, w, 10);
        AflUiDraw.button(g, x, y, w, 10, hover, false, true, 1);
        SetupCanvas.centred(g, font, label, x + w / 2, y + 2.4F, AflUiStyle.TEXT, 1, 0.6F);
    }

    private void stepButton(GuiGraphics g, float x, float y, boolean plus, boolean enabled, float mx, float my) {
        boolean hover = enabled && SetupCanvas.in(mx, my, x, y, 8, 8);
        AflUiDraw.button(g, x, y, 8, 8, hover, false, enabled, 1);
        SetupCanvas.centred(g, font, Component.literal(plus ? "+" : "−"), x + 4, y + 1.2F, enabled ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1, 0.7F);
    }

    // ---- middle: traits

    private record Chip(String id, float x, float y, float w) {
    }

    private record Group(String id, float y) {
    }

    private float chipW() {
        return (MID_W - 16 - 2 * CHIP_GAP) / 3;
    }

    /** Groups and chips in content coordinates (y from the top of the scroll area); returns the content height. */
    private float layoutTraits(List<Group> groups, List<Chip> chips) {
        float y = 0, cw = chipW();
        List<String> fixed = fixedTraits();
        for (String group : SurvivorCatalog.GROUPS) {
            List<String> ids = new ArrayList<>();
            for (SurvivorCatalog.TraitDef def : catalog.traits().values()) {
                if (def.group().equals(group) && (!def.backgroundOnly() || fixed.contains(def.id()))) ids.add(def.id());
            }
            if (ids.isEmpty()) continue;
            groups.add(new Group(group, y));
            y += GROUP_H;
            for (int i = 0; i < ids.size(); i++) {
                int col = i % 3;
                if (i > 0 && col == 0) y += CHIP_H + CHIP_GAP;
                chips.add(new Chip(ids.get(i), MID_X + 8 + col * (cw + CHIP_GAP), y, cw));
            }
            y += CHIP_H + 5;
        }
        return y;
    }

    private float listTop() {
        return TRAIT_Y + TRAIT_HEAD_H + 4;
    }

    private float listBottom() {
        return BOTTOM - 4;
    }

    private void traitPanel(GuiGraphics g, float mx, float my) {
        AflUiDraw.panel(g, MID_X, TRAIT_Y, MID_W, BOTTOM - TRAIT_Y, 1);
        List<Group> groups = new ArrayList<>();
        List<Chip> chips = new ArrayList<>();
        float content = layoutTraits(groups, chips), top = listTop(), bottom = listBottom();
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - (bottom - top)));
        boolean inList = SetupCanvas.in(mx, my, MID_X, top, MID_W, bottom - top);
        List<String> fixed = fixedTraits();
        canvas.scissor(g, MID_X, top, MID_W, bottom - top);
        for (Group group : groups) {
            float y = top + group.y() - scroll;
            SetupCanvas.text(g, font, SetupCanvas.tr("trait_group." + group.id()), MID_X + 8, y + 2, AflUiStyle.TEXT_DIM, 1, 0.6F);
        }
        for (Chip chip : chips) {
            float y = top + chip.y() - scroll;
            if (y + CHIP_H < top || y > bottom) continue;
            SurvivorCatalog.TraitDef def = catalog.traits().get(chip.id());
            boolean locked = fixed.contains(chip.id()), on = locked || traits.contains(chip.id());
            boolean blocked = !on && excluded(chip.id());
            boolean hover = inList && SetupCanvas.in(mx, my, chip.x(), y, chip.w(), CHIP_H);
            if (hover) hovered = chip.id();
            float alpha = blocked ? 0.55F : 1;
            int line = on ? AflUiStyle.ACCENT : blocked ? RED : AflUiStyle.LINE;
            float lineAlpha = on ? (locked ? 0.4F : 0.75F) : blocked ? (hover ? 0.95F : 0.6F) : 0.16F;
            AflUiShapes.box(g, chip.x(), y, chip.w(), CHIP_H, AflUiStyle.RADIUS_TILE, on || hover && !blocked ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK,
                    on ? 0.9F : 0.45F, line, lineAlpha, 0, 0);
            AflUiShapes.fill(g, chip.x() + 4, y + 4.8F, 2.4F, 2.4F, 1.2F, dot(chip.id()), alpha);
            SetupCanvas.text(g, font, label("trait", chip.id()), chip.x() + 9, y + 3.2F, on ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, alpha, 0.6F);
            Component tag = locked ? SetupCanvas.tr("setup.locked") : cost(def);
            SetupCanvas.right(g, font, tag, chip.x() + chip.w() - 3.5F, y + 3.8F, locked ? AflUiStyle.ACCENT : AflUiStyle.TEXT_DISABLED,
                    alpha, 0.5F);
        }
        g.disableScissor();
        if (content > bottom - top) {
            float track = bottom - top, thumb = Math.max(12, track * track / content);
            float ty = top + (track - thumb) * (scroll / (content - track));
            AflUiShapes.fill(g, MID_X + MID_W - 4, ty, 1.6F, thumb, 0.8F, AflUiStyle.LINE, 0.35F);
        }
        traitSummary(g);
    }

    /** The hovered trait (or the last one clicked): what it does, at the top of the trait panel. */
    private void traitSummary(GuiGraphics g) {
        float x = MID_X + 8, y = TRAIT_Y + 6, w = MID_W - 16;
        String id = hovered != null ? hovered : shown;
        SurvivorCatalog.TraitDef def = id == null ? null : catalog.traits().get(id);
        AflUiShapes.fill(g, MID_X + 6, TRAIT_Y + TRAIT_HEAD_H + 1, MID_W - 12, 0.4F, 0, AflUiStyle.LINE, 0.16F);
        if (def == null) {
            SetupCanvas.text(g, font, SetupCanvas.tr("setup.traits"), x, y, AflUiStyle.TEXT_DIM, 1, 0.7F);
            SetupCanvas.text(g, font, SetupCanvas.tr("setup.trait_hint"), x, y + 10, AflUiStyle.TEXT_DISABLED, 1, 0.55F);
            return;
        }
        Component name = label("trait", id);
        SetupCanvas.text(g, font, name, x, y, AflUiStyle.TEXT, 1, 0.75F);
        Component tag = fixedTraits().contains(id) ? SetupCanvas.tr("setup.locked_long") : cost(def);
        SetupCanvas.text(g, font, tag, x + SetupCanvas.width(font, name, 0.75F) + 5, y + 0.8F,
                def.negative() ? RED : AflUiStyle.ACCENT, 1, 0.58F);
        float ty = y + 9;
        List<String> conflicts = conflicts(id);
        if (!conflicts.isEmpty() && !traits.contains(id) && !fixedTraits().contains(id)) {
            // a trait that clashes with one already chosen: say which, instead of what it does
            List<Component> names = new ArrayList<>();
            for (String other : conflicts) {
                names.add(fixedTraits().contains(other) ? SetupCanvas.tr("setup.conflict_background", label("trait", other)) : label("trait", other));
            }
            boolean flash = id.equals(conflictClicked) && net.minecraft.Util.getMillis() - conflictAt < 600;
            SetupCanvas.text(g, font, SetupCanvas.tr("setup.conflict", join(names, "、")), x, ty, RED, flash ? 1 : 0.85F, 0.6F);
            return;
        }
        for (FormattedCharSequence line : wrap(describe(def), w, 0.55F)) {
            if (ty > TRAIT_Y + TRAIT_HEAD_H - 5) break;
            textSeq(g, line, x, ty, AflUiStyle.TEXT_DIM, 0.55F);
            ty += 6.5F;
        }
    }

    /** Points as they flow: a trait that costs points shows -N, one that gives points +N. */
    private Component cost(@Nullable SurvivorCatalog.TraitDef def) {
        if (def == null) return Component.empty();
        return Component.literal(def.negative() ? "+" + -def.cost() : "−" + def.cost());
    }

    /** The chosen (or background) traits this one cannot go with. */
    private List<String> conflicts(String id) {
        List<String> out = new ArrayList<>();
        SurvivorCatalog.TraitDef def = catalog.traits().get(id);
        for (String other : allTraits()) {
            if (other.equals(id)) continue;
            SurvivorCatalog.TraitDef o = catalog.traits().get(other);
            if (def != null && def.exclusive().contains(other) || o != null && o.exclusive().contains(id)) out.add(other);
        }
        return out;
    }

    /**
     * What a trait does: its attribute effects and body bonuses; growth only as "has gun experience" / "slow to get
     * good with guns" (the levels are for finding out in play). Exclusions are not listed: a clashing trait turns red.
     */
    private Component describe(SurvivorCatalog.TraitDef def) {
        List<Component> parts = new ArrayList<>();
        for (SurvivorCatalog.Effect e : def.effects()) parts.add(effect(e.attribute(), e.multiply(), e.value()));
        def.bodyBonus().forEach((body, n) -> parts.add(SetupCanvas.tr("setup.effect.body_bonus", label("body", body), signed(n))));
        if (!def.growthStart().isEmpty()) parts.add(SetupCanvas.tr("setup.effect.gun_experience"));
        if (!def.growthCap().isEmpty()) parts.add(SetupCanvas.tr("setup.effect.gun_capped"));
        return parts.isEmpty() ? SetupCanvas.tr("setup.no_change") : join(parts);
    }

    // ---- right: survivor, name, start

    private void preview(GuiGraphics g, int mouseX, int mouseY) {
        AflUiDraw.panel(g, RIGHT_X, TOP, RIGHT_W, PREVIEW_H, 1);
        float cx = RIGHT_X + RIGHT_W / 2, feet = TOP + PREVIEW_H - 6, size = 28;
        canvas.scissor(g, RIGHT_X + 1, TOP + 1, RIGHT_W - 2, PREVIEW_H - 2);
        // the turn follows the mouse in GUI units, like the inventory's doll
        float sx = canvas.originX + cx * canvas.scale, sy = canvas.originY + (feet - size * 1.6F) * canvas.scale;
        SurvivorDoll.Skin skin = minecraft.player != null ? SurvivorDoll.Skin.of(minecraft.player) : SurvivorDoll.Skin.account(minecraft);
        doll.render(g, skin, cx, feet, size, (sx - mouseX) / canvas.scale, (sy - mouseY) / canvas.scale);
        g.disableScissor();
    }

    private void namePanel(GuiGraphics g, float mx, float my, float partialTick) {
        AflUiDraw.panel(g, RIGHT_X, NAME_Y, RIGHT_W, NAME_H, 1);
        float x = RIGHT_X + 8, w = RIGHT_W - 16;
        SetupCanvas.text(g, font, SetupCanvas.tr("setup.name"), x, NAME_Y + 6, AflUiStyle.TEXT_DIM, 1, 0.7F);
        if (info.contains("Name")) {
            SetupCanvas.text(g, font, Component.literal(info.getString("Name")), x, NAME_Y + 19, AflUiStyle.TEXT, 1, 0.85F);
            SetupCanvas.right(g, font, SetupCanvas.tr("setup.name_fixed"), x + w, NAME_Y + 6.6F, AflUiStyle.TEXT_DISABLED, 1, 0.55F);
            return;
        }
        float half = 36;
        segment(g, x + w - 2 * half, NAME_Y + 4, half, SetupCanvas.tr("setup.name_account"), !customName, mx, my);
        segment(g, x + w - half, NAME_Y + 4, half, SetupCanvas.tr("setup.name_custom"), customName, mx, my);
        float fy = NAME_Y + 19;
        if (!customName) {
            SetupCanvas.text(g, font, Component.literal(info.getString("Account")), x + 4, fy + 4, AflUiStyle.TEXT, 1, 0.85F);
            return;
        }
        boolean bad = !nameField.getValue().isBlank() && SurvivorNames.nickname(nameField.getValue()) == null;
        AflUiShapes.box(g, x, fy, w, 13, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.7F,
                bad ? RED : nameFocused ? AflUiStyle.ACCENT : AflUiStyle.LINE, nameFocused || bad ? 0.9F : AflUiStyle.LINE_ALPHA, 0, 0);
        nameField.setY(Math.round(fy + 3));
        nameField.render(g, (int) mx, (int) my, partialTick);
        if (nameField.getValue().isEmpty() && !nameFocused) {
            SetupCanvas.text(g, font, SetupCanvas.tr("setup.name_hint"), x + 4, fy + 3.5F, AflUiStyle.TEXT_DISABLED, 1, 0.65F);
        }
    }

    private void segment(GuiGraphics g, float x, float y, float w, Component label, boolean on, float mx, float my) {
        boolean hover = SetupCanvas.in(mx, my, x, y, w, 10);
        AflUiShapes.box(g, x, y, w, 10, 2, on || hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK, on ? 0.9F : 0.4F,
                on ? AflUiStyle.ACCENT : AflUiStyle.LINE, on ? 0.6F : 0.2F, 0, 0);
        SetupCanvas.centred(g, font, label, x + w / 2, y + 2.6F, on ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1, 0.58F);
    }

    private float startTop() {
        return START_Y + 16;
    }

    private float startBottom() {
        return BUTTON_Y - 4 - 3;
    }

    /**
     * What the survivor starts with: the scene, the base readouts with every effect applied, gun levels, other changes.
     * Scrolls with the wheel (more will be listed later: kit, scene details).
     */
    private void startPanel(GuiGraphics g) {
        AflUiDraw.panel(g, RIGHT_X, START_Y, RIGHT_W, BUTTON_Y - 4 - START_Y, 1);
        float x = RIGHT_X + 8, w = RIGHT_W - 16;
        SetupCanvas.text(g, font, SetupCanvas.tr("setup.start"), x, START_Y + 6, AflUiStyle.TEXT_DIM, 1, 0.7F);
        float top = startTop(), bottom = startBottom();
        canvas.scissor(g, RIGHT_X + 1, top, RIGHT_W - 2, bottom - top);
        float y = top + 1 - startScroll;
        SurvivorScenes.Scene scene = SurvivorScenes.byId(info.getString("Scene"));
        y = row(g, x, w, y, SetupCanvas.tr("setup.scene"), SetupCanvas.tr("scene." + scene.name()));

        Map<String, double[]> totals = totals();   // attribute id -> {sum of additions, product of factors}
        CompoundTag bases = info.getCompound("Bases");
        y = baseRow(g, x, w, y, "apocalypse_firstlight:carry_capacity", totals, bases, "setup.unit.kg");
        y = baseRow(g, x, w, y, "apocalypse_firstlight:max_stamina", totals, bases, null);
        double cold = value("apocalypse_firstlight:cold_threshold", totals, bases), heat = value("apocalypse_firstlight:heat_threshold", totals, bases);
        y = row(g, x, w, y, SetupCanvas.tr("setup.comfort"), SetupCanvas.tr("setup.unit.range_c", number(cold), number(heat)));
        y = baseRow(g, x, w, y, "minecraft:generic.max_health", totals, bases, null);

        List<Component> others = new ArrayList<>();
        for (Map.Entry<String, double[]> e : totals.entrySet()) {
            if (bases.contains(e.getKey())) continue;
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(net.minecraft.resources.ResourceLocation.tryParse(e.getKey()));
            if (attribute == null) continue;
            double[] t = e.getValue();
            if (Math.abs(t[1] - 1) > 1e-9) others.add(effect(attribute, true, t[1]));
            if (Math.abs(t[0]) > 1e-9) others.add(effect(attribute, false, t[0]));
        }
        // guns: one row when all three start the same ("Guns  novice"), else a line listing each
        List<Component> guns = new ArrayList<>();
        java.util.Set<String> tiers = new java.util.HashSet<>();
        for (SurvivorCatalog.GrowthDef def : catalog.growth().values()) {
            int level = growthLevel(def);
            String tier = level >= 4 ? "trained" : level >= 2 ? "basic" : "novice";
            tiers.add(tier);
            guns.add(SetupCanvas.tr("setup.gun", label("growth", def.id()), SetupCanvas.tr("setup.tier." + tier)));
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (tiers.size() == 1) y = row(g, x, w, y, SetupCanvas.tr("setup.guns"), SetupCanvas.tr("setup.tier." + tiers.iterator().next()));
        else if (!guns.isEmpty()) lines.addAll(wrap(SetupCanvas.tr("setup.guns_line", join(guns)), w, 0.52F));
        if (!others.isEmpty()) lines.addAll(wrap(join(others), w, 0.52F));
        y += 1;
        for (FormattedCharSequence line : lines) {
            textSeq(g, line, x, y, AflUiStyle.TEXT_DIM, 0.52F);
            y += 6.3F;
        }
        g.disableScissor();
        float content = y + startScroll - top + 2, view = bottom - top;
        startMax = Math.max(0, content - view);
        startScroll = Mth.clamp(startScroll, 0, startMax);
        if (startMax > 0) {
            float thumb = Math.max(10, view * view / content), ty = top + (view - thumb) * (startScroll / startMax);
            AflUiShapes.fill(g, RIGHT_X + RIGHT_W - 4, ty, 1.6F, thumb, 0.8F, AflUiStyle.LINE, 0.35F);
        }
    }

    private float row(GuiGraphics g, float x, float w, float y, Component key, Component value) {
        SetupCanvas.text(g, font, key, x, y, AflUiStyle.TEXT_DIM, 1, 0.58F);
        SetupCanvas.right(g, font, value, x + w, y, AflUiStyle.TEXT, 1, 0.58F);
        return y + 7.5F;
    }

    private float baseRow(GuiGraphics g, float x, float w, float y, String id, Map<String, double[]> totals, CompoundTag bases,
                          @Nullable String unit) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(net.minecraft.resources.ResourceLocation.tryParse(id));
        if (attribute == null) return y;
        String n = number(value(id, totals, bases));
        return row(g, x, w, y, Component.translatable(attribute.getDescriptionId()), unit == null ? Component.literal(n) : SetupCanvas.tr(unit, n));
    }

    private static double value(String id, Map<String, double[]> totals, CompoundTag bases) {
        double[] t = totals.getOrDefault(id, new double[]{0, 1});
        return (bases.getDouble(id) + t[0]) * t[1];
    }

    private Map<String, double[]> totals() {
        Map<String, double[]> totals = new LinkedHashMap<>();
        for (SurvivorCatalog.BodyDef def : catalog.body().values()) {
            int level = startLevel(def);
            for (SurvivorCatalog.Curve curve : def.curves()) {
                add(totals, curve.attribute(), curve.multiply(), curve.values()[Mth.clamp(level, 1, curve.values().length) - 1]);
            }
        }
        for (String id : allTraits()) {
            SurvivorCatalog.TraitDef def = catalog.traits().get(id);
            if (def != null) for (SurvivorCatalog.Effect e : def.effects()) add(totals, e.attribute(), e.multiply(), e.value());
        }
        return totals;
    }

    private static void add(Map<String, double[]> totals, Attribute attribute, boolean multiply, double value) {
        var key = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        if (key == null) return;
        double[] t = totals.computeIfAbsent(key.toString(), k -> new double[]{0, 1});
        if (multiply) t[1] *= value;
        else t[0] += value;
    }

    private void footer(GuiGraphics g, float mx, float my, SurvivorSetup.Check check) {
        float randomW = 34, startW = RIGHT_W - randomW - 4;
        boolean hoverRandom = !waiting && SetupCanvas.in(mx, my, RIGHT_X, BUTTON_Y, randomW, BUTTON_H);
        AflUiDraw.button(g, RIGHT_X, BUTTON_Y, randomW, BUTTON_H, hoverRandom, false, !waiting, 1);
        SetupCanvas.centred(g, font, SetupCanvas.tr("setup.random_all"), RIGHT_X + randomW / 2, BUTTON_Y + 4.5F, AflUiStyle.TEXT, 1, 0.75F);
        float sx = RIGHT_X + randomW + 4;
        boolean ok = ready(), hover = ok && SetupCanvas.in(mx, my, sx, BUTTON_Y, startW, BUTTON_H);
        if (ok) {
            AflUiShapes.box(g, sx, BUTTON_Y, startW, BUTTON_H, AflUiStyle.RADIUS_TILE, AflUiStyle.KEY_BACK, hover ? 1 : 0.9F, AflUiStyle.LINE, 0.5F, 0, 0);
        } else {
            AflUiDraw.button(g, sx, BUTTON_Y, startW, BUTTON_H, false, false, false, 1);
        }
        Component label = SetupCanvas.tr(waiting ? "setup.waiting" : newWorld != null ? "setup.begin_world" : "setup.begin");
        SetupCanvas.centred(g, font, label, sx + startW / 2, BUTTON_Y + 4.5F, ok ? AflUiStyle.KEY_TEXT : AflUiStyle.TEXT_DISABLED, 1, 0.8F);

        Component note;
        int rgb = RED;
        if (!refusal.isEmpty()) note = problem(refusal.get(0));
        else if (customName && !info.contains("Name") && name() == null) {
            note = SetupCanvas.tr(nameField.getValue().isBlank() ? "setup.name_needed" : "name.refused.invalid");
        } else if (check.pointsLeft() < 0) note = SetupCanvas.tr("setup.over_budget", -check.pointsLeft());
        else if (check.negativePoints() > SurvivorCatalog.NEGATIVE_POINT_CAP) note = SetupCanvas.tr("setup.negative_cap", SurvivorCatalog.NEGATIVE_POINT_CAP);
        else if (!check.ok()) note = Component.literal(check.problems().get(0));
        else {
            note = SetupCanvas.tr("setup.final");
            rgb = AflUiStyle.TEXT_DISABLED;
        }
        SetupCanvas.right(g, font, note, RIGHT_X + RIGHT_W, BUTTON_Y + BUTTON_H + 3.5F, rgb, 1, 0.52F);
    }

    private Component problem(String key) {
        if (key.startsWith("name.")) return SetupCanvas.tr("name.refused." + key.substring(5));
        return SetupCanvas.tr("setup.refused", key);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || waiting) return true;
        float mx = canvas.vx(mouseX), my = canvas.vy(mouseY);
        boolean wasFocused = nameFocused;
        nameFocused = false;
        if (clickBackgrounds(mx, my) || clickBody(mx, my) || clickTraits(mx, my) || clickName(mx, my) || clickFooter(mx, my)) {
            nameField.setFocused(nameFocused);
            return true;
        }
        nameField.setFocused(false);
        if (wasFocused) nameFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickBackgrounds(float mx, float my) {
        float x = LEFT_X + 7, w = LEFT_W - 14;
        if (SetupCanvas.in(mx, my, LEFT_X + LEFT_W - 7 - 26, TOP + 4, 26, 10)) {
            List<String> ids = new ArrayList<>(catalog.backgrounds().keySet());
            if (!ids.isEmpty()) setBackground(ids.get(random.nextInt(ids.size())));
            click();
            return true;
        }
        float y = TOP + 18;
        for (SurvivorCatalog.BackgroundDef def : catalog.backgrounds().values()) {
            float h = backgroundHeight(def, w);
            if (SetupCanvas.in(mx, my, x, y, w, h)) {
                if (!def.id().equals(background)) {
                    setBackground(def.id());
                    click();
                }
                return true;
            }
            y += h + 3;
        }
        return false;
    }

    /** A new background: its own traits leave the picks, and picks it now excludes are dropped. */
    private void setBackground(String id) {
        background = id;
        refusal = List.of();
        traits.removeAll(fixedTraits());
        traits.removeIf(t -> {
            SurvivorCatalog.TraitDef def = catalog.traits().get(t);
            return def == null || def.backgroundOnly() || excluded(t);
        });
    }

    private boolean clickBody(float mx, float my) {
        float px = MID_X + 8 + 34, y = BODY_Y + 18;
        for (SurvivorCatalog.BodyDef def : catalog.body().values()) {
            int level = body.getOrDefault(def.id(), def.defaultLevel());
            if (SetupCanvas.in(mx, my, px, y, 8, 8) && level > def.startMin()) {
                body.put(def.id(), level - 1);
                refusal = List.of();
                click();
                return true;
            }
            if (SetupCanvas.in(mx, my, px + 11 + PIPS * 8 + 2, y, 8, 8) && level < def.startMax()) {
                body.put(def.id(), level + 1);
                refusal = List.of();
                click();
                return true;
            }
            y += 21;
        }
        return false;
    }

    private boolean clickTraits(float mx, float my) {
        float top = listTop(), bottom = listBottom();
        if (!SetupCanvas.in(mx, my, MID_X, top, MID_W, bottom - top)) return false;
        List<Group> groups = new ArrayList<>();
        List<Chip> chips = new ArrayList<>();
        layoutTraits(groups, chips);
        for (Chip chip : chips) {
            if (!SetupCanvas.in(mx, my, chip.x(), top + chip.y() - scroll, chip.w(), CHIP_H)) continue;
            shown = chip.id();
            if (fixedTraits().contains(chip.id())) return true;
            if (traits.remove(chip.id())) {
                refusal = List.of();
                click();
            } else if (!excluded(chip.id())) {
                traits.add(chip.id());
                refusal = List.of();
                click();
            } else {
                conflictClicked = chip.id();
                conflictAt = net.minecraft.Util.getMillis();
            }
            return true;
        }
        return true;
    }

    private boolean clickName(float mx, float my) {
        if (info.contains("Name")) return false;
        float x = RIGHT_X + 8, w = RIGHT_W - 16, half = 36;
        if (SetupCanvas.in(mx, my, x + w - 2 * half, NAME_Y + 4, half, 10)) {
            customName = false;
            refusal = List.of();
            click();
            return true;
        }
        if (SetupCanvas.in(mx, my, x + w - half, NAME_Y + 4, half, 10)) {
            customName = true;
            nameFocused = true;
            refusal = List.of();
            click();
            return true;
        }
        if (customName && SetupCanvas.in(mx, my, x, NAME_Y + 19, w, 13)) {
            nameFocused = true;
            nameField.setFocused(true);
            nameField.mouseClicked(mx, my, 0);
            return true;
        }
        return false;
    }

    private boolean clickFooter(float mx, float my) {
        float randomW = 34, startW = RIGHT_W - randomW - 4;
        if (SetupCanvas.in(mx, my, RIGHT_X, BUTTON_Y, randomW, BUTTON_H)) {
            randomize();
            click();
            return true;
        }
        if (SetupCanvas.in(mx, my, RIGHT_X + randomW + 4, BUTTON_Y, startW, BUTTON_H)) {
            if (ready()) {
                click();
                confirm();
            }
            return true;
        }
        return false;
    }

    /**
     * A random survivor that passes the rules: a random background, up to two negative traits, then body steps and
     * traits in random order while points last.
     */
    private void randomize() {
        List<String> ids = new ArrayList<>(catalog.backgrounds().keySet());
        if (!ids.isEmpty()) setBackground(ids.get(random.nextInt(ids.size())));
        traits.clear();
        resetBody();
        List<String> negatives = new ArrayList<>(), positives = new ArrayList<>();
        for (SurvivorCatalog.TraitDef def : catalog.traits().values()) {
            if (def.backgroundOnly() || fixedTraits().contains(def.id())) continue;
            (def.negative() ? negatives : positives).add(def.id());
        }
        Collections.shuffle(negatives, random);
        int wanted = random.nextInt(3);
        for (String id : negatives) {
            if (wanted == 0) break;
            if (tryAdd(id)) wanted--;
        }
        List<Runnable> steps = new ArrayList<>();
        for (SurvivorCatalog.BodyDef def : catalog.body().values()) {
            for (int i = 0; i < def.startMax() - def.defaultLevel(); i++) steps.add(() -> tryStep(def));
        }
        Collections.shuffle(positives, random);
        for (String id : positives.subList(0, Math.min(4, positives.size()))) steps.add(() -> tryAdd(id));
        Collections.shuffle(steps, random);
        for (Runnable step : steps) step.run();
        refusal = List.of();
    }

    private boolean tryAdd(String id) {
        if (excluded(id) || !traits.add(id)) return false;
        SurvivorSetup.Check check = check();
        if (check.ok() && check.pointsLeft() >= 0) return true;
        traits.remove(id);
        return false;
    }

    private void tryStep(SurvivorCatalog.BodyDef def) {
        int level = body.getOrDefault(def.id(), def.defaultLevel());
        if (level >= def.startMax()) return;
        body.put(def.id(), level + 1);
        if (!check().ok()) body.put(def.id(), level);
    }

    private void confirm() {
        String name = name();
        if (name == null || !check().ok()) return;
        waiting = true;
        refusal = List.of();
        if (newWorld != null) {
            newWorld.create(com.antaurora.apofirstlight.survivor.SurvivorSetupFlow.encode(choice(), name));
            return;
        }
        AflNetwork.survivorToServer(new SurvivorPackets.SetupChoice(background, Map.copyOf(body), List.copyOf(traits), name));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float mx = canvas.vx(mouseX), my = canvas.vy(mouseY);
        if (SetupCanvas.in(mx, my, RIGHT_X, START_Y, RIGHT_W, BUTTON_Y - 4 - START_Y)) {
            startScroll = Mth.clamp(startScroll - (float) delta * 10, 0, startMax);
        } else {
            scroll -= (float) delta * 14;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (newWorld != null) minecraft.setScreen(newWorld);
            else leave();
            return true;
        }
        if (nameFocused && customName) {
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
                nameFocused = false;
                nameField.setFocused(false);
                return true;
            }
            if (nameField.keyPressed(key, scanCode, modifiers)) {
                refusal = List.of();
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (nameFocused && customName && nameField.charTyped(c, modifiers)) {
            refusal = List.of();
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    /** As the death screen leaves: disconnect, save, title screen. The setup waits for the next join. */
    private void leave() {
        if (minecraft == null) return;
        if (minecraft.level != null) minecraft.level.disconnect();
        minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        minecraft.setScreen(new TitleScreen());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** Single player pauses while choosing (hunger, thirst and cold would still run on the held survivor). */
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---------------------------------------------------------------- text helpers

    private static final int RED = 0xE06A5A;

    static Component label(String kind, String id) {
        return Component.translatable(SurvivorCatalog.key(kind, id));
    }

    private static Component desc(String kind, String id) {
        return Component.translatable(SurvivorCatalog.key(kind, id) + ".desc");
    }

    /** Small coloured mark in place of trait icons (follow-up): warm for traits that cost points, red for ones that give. */
    private int dot(String id) {
        SurvivorCatalog.TraitDef def = catalog.traits().get(id);
        if (def == null) return AflUiStyle.TEXT_DIM;
        return def.negative() ? 0xD0705E : def.cost() == 0 ? AflUiStyle.TEXT_DIM : 0x8FB98A;
    }

    /** One attribute change, as "Comfortable load +3 kg", "Thirst rate +20%". */
    static Component effect(Attribute attribute, boolean multiply, double value) {
        MutableComponent name = Component.translatable(attribute.getDescriptionId());
        return name.append(" " + amount(attribute, multiply, value));
    }

    /** The change alone: "+20%" for a multiplier, "+3 kg" / "−1 °C" / "+10" for an offset. */
    static String amount(Attribute attribute, boolean multiply, double value) {
        if (multiply) return signed((value - 1) * 100) + "%";
        var key = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        String path = key == null ? "" : key.getPath();
        String unit = path.equals("carry_capacity") ? " kg" : path.endsWith("_threshold") ? " °C" : "";
        return signed(value) + unit;
    }

    static String signed(double v) {
        String n = number(Math.abs(v));
        return (v < 0 ? "−" : "+") + n;
    }

    static String number(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.rint(r) ? Long.toString((long) r) : String.format(Locale.ROOT, "%.1f", r);
    }

    static Component join(List<Component> parts) {
        return join(parts, " · ");
    }

    static Component join(List<Component> parts, String sep) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(sep);
            out.append(parts.get(i));
        }
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
