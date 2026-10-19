package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.mixin.client.CreateWorldScreenAccessor;
import com.antaurora.apofirstlight.survivor.SurvivorMode;
import com.antaurora.apofirstlight.survivor.SurvivorScenes;
import com.antaurora.apofirstlight.survivor.Survivors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;

/**
 * The AFL New World page (docs/ui/new_world_survivor_setup_v1.md, scheme A; plan doc section 9): one centred card in
 * place of the vanilla Create World screen. AFL has one kind of world, so only the name, the seed (empty = random), the
 * survivor mode (Normal / Hard, fixed once created) and the start scene; commands can be allowed, and "Vanilla
 * settings" opens the vanilla screen itself (world type, game rules, data packs; the scene is then the wilderness). The
 * vanilla screen stays underneath: this page edits its WorldCreationUiState and lets it create the world, so the
 * federal default, recreate and every vanilla check keep working. The mode and scene go to the new world through
 * Survivors' create-screen hand-over, as the vanilla screen's mode button does.
 */
public final class AflNewWorldScreen extends Screen {
    static final PanoramaRenderer PANORAMA = new PanoramaRenderer(TitleScreen.CUBE_MAP);
    /** A vanilla screen the player asked to see as it is ("Vanilla settings"): not wrapped again. */
    private static WeakReference<CreateWorldScreen> passThrough = new WeakReference<>(null);

    private static final float CARD_X = 140, CARD_Y = 20, CARD_W = 200, CARD_H = 242, PAD = 11;
    private static final float FIELD_Y = 62, FIELD_H = 15, MODE_Y = 91, MODE_H = 26, LIST_Y = 142, ROW_H = 20;
    private static final float FOOT_Y = 239, BUTTON_H = 16;

    private final CreateWorldScreen vanilla;
    private final SetupCanvas canvas = new SetupCanvas();
    private EditBox nameField, seedField;
    @Nullable
    private EditBox focused;
    private SurvivorMode mode = SurvivorMode.NORMAL;
    private String scene = SurvivorScenes.DEFAULT;
    private boolean cheats;
    /** 0..1: how far the Hard look has faded in (the red cast over the page, the card's beat). */
    private float hardBlend;
    private long lastFrame;

    /** The Hard colours: dried-blood fill, a red line, the warm-white title tinted towards red. */
    private static final int HARD_FILL = 0x2A0C0A, HARD_LINE = 0xC2483B, HARD_TEXT = 0xF0D2CC, HARD_DIM = 0xC08A82;
    private static final long BEAT_MS = 1600;

    public AflNewWorldScreen(CreateWorldScreen vanilla) {
        super(Component.translatable("selectWorld.create"));
        this.vanilla = vanilla;
    }

    /** Whether this vanilla screen should open as it is (the player chose "Vanilla settings" for it). */
    static boolean passesThrough(CreateWorldScreen screen) {
        return passThrough.get() == screen;
    }

    @Override
    protected void init() {
        // the vanilla screen needs its own init for popScreen and onCreate (it keeps its state across inits)
        vanilla.init(minecraft, width, height);
        canvas.layout(minecraft, width, height);
        var state = vanilla.getUiState();
        cheats = state.isAllowCheats();
        float x = CARD_X + PAD, innerW = CARD_W - 2 * PAD, nameW = innerW * 0.57F;
        boolean seedFocused = focused != null && focused == seedField;   // the old boxes, before they are replaced
        String name = nameField != null ? nameField.getValue() : state.getName();
        String seed = seedField != null ? seedField.getValue() : state.getSeed();
        nameField = field(x + 5, nameW - 10, name, 32);
        nameField.setResponder(state::setName);
        seedField = field(x + nameW + 6 + 5, innerW - nameW - 6 - 10, seed, 64);
        seedField.setHint(SetupCanvas.tr("newworld.seed_hint").withStyle(s -> s.withColor(AflUiStyle.TEXT_DISABLED)));
        seedField.setResponder(state::setSeed);
        focused = seedFocused ? seedField : nameField;
        nameField.setFocused(focused == nameField);
        seedField.setFocused(focused == seedField);
    }

    private EditBox field(float x, float w, String value, int max) {
        EditBox box = new EditBox(font, Math.round(x), Math.round(FIELD_Y + (FIELD_H - 8) / 2), Math.round(w), 9, Component.empty());
        box.setBordered(false);
        box.setMaxLength(max);
        box.setTextColor(AflUiStyle.argb(AflUiStyle.TEXT, 1));
        box.setValue(value);
        return box;
    }

    @Override
    public void tick() {
        nameField.tick();
        seedField.tick();
    }

    private boolean ready() {
        return !nameField.getValue().isBlank();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        PANORAMA.render(partialTick, 1);
        g.fillGradient(0, 0, width, height, AflUiStyle.argb(0x0A0B0C, 0.62F), AflUiStyle.argb(0x0A0B0C, 0.86F));
        long now = net.minecraft.Util.getMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        boolean hard = mode == SurvivorMode.HARD || vanilla.getUiState().isHardcore();
        hardBlend = net.minecraft.util.Mth.clamp(hardBlend + (hard ? dt : -dt) / 0.35F, 0, 1);
        if (hardBlend > 0) {
            // Hard: the page darkens and takes a red cast from below, with the card's heartbeat
            float e = hardBlend * hardBlend * (3 - 2 * hardBlend), beat = beat(now);
            g.fillGradient(0, 0, width, height, AflUiStyle.argb(0x1A0404, 0.30F * e), AflUiStyle.argb(0x420707, (0.42F + 0.08F * beat) * e));
        }
        float mx = canvas.vx(mouseX), my = canvas.vy(mouseY);
        canvas.push(g);
        SetupCanvas.keyHint(g, font, "Esc", SetupCanvas.tr("newworld.back"), 15, 12, 0.75F);
        SetupCanvas.centred(g, font, Component.literal("A P O C A L Y P S E   ·   F I R S T   L I G H T"), 240, 13,
                AflUiStyle.TEXT_DIM, 0.85F, 0.62F);
        AflUiDraw.panel(g, CARD_X, CARD_Y, CARD_W, CARD_H, 1);
        float x = CARD_X + PAD, innerW = CARD_W - 2 * PAD, right = x + innerW;
        SetupCanvas.text(g, font, SetupCanvas.tr("newworld.title"), x, 29, AflUiStyle.TEXT, 1, 1.35F);
        SetupCanvas.text(g, font, region(), x, 44, AflUiStyle.TEXT_DIM, 1, 0.68F);

        // name and seed
        float nameW = innerW * 0.57F, seedX = x + nameW + 6, seedW = innerW - nameW - 6;
        SetupCanvas.text(g, font, SetupCanvas.tr("newworld.name"), x, 54, AflUiStyle.TEXT_DIM, 1, 0.7F);
        SetupCanvas.text(g, font, SetupCanvas.tr("newworld.seed"), seedX, 54, AflUiStyle.TEXT_DIM, 1, 0.7F);
        fieldBox(g, x, nameW, focused == nameField);
        fieldBox(g, seedX, seedW, focused == seedField);
        nameField.render(g, (int) mx, (int) my, partialTick);
        seedField.render(g, (int) mx, (int) my, partialTick);

        // survivor mode
        SetupCanvas.text(g, font, SetupCanvas.tr("mode.title"), x, MODE_Y - 8, AflUiStyle.TEXT_DIM, 1, 0.7F);
        boolean hardcore = vanilla.getUiState().isHardcore();
        if (hardcore) mode = SurvivorMode.HARD;
        float half = (innerW - 4) / 2;
        modeCard(g, x, half, SurvivorMode.NORMAL, mx, my, !hardcore);
        modeCard(g, x + half + 4, half, SurvivorMode.HARD, mx, my, true);
        boolean hardRule = hardcore || mode == SurvivorMode.HARD;
        SetupCanvas.text(g, font, SetupCanvas.tr(hardcore ? "newworld.mode_hardcore" : hardRule ? "newworld.mode_rule_hard" : "newworld.mode_rule"),
                x, MODE_Y + MODE_H + 3, hardRule ? HARD_DIM : AflUiStyle.TEXT_DIM, hardRule ? 1 : 0.8F, 0.6F);

        // start scene
        SetupCanvas.text(g, font, SetupCanvas.tr("newworld.scene"), x, LIST_Y - 9, AflUiStyle.TEXT_DIM, 1, 0.7F);
        smallButton(g, right - 28, LIST_Y - 13, 28, SetupCanvas.tr("newworld.random"), mx, my);
        AflUiShapes.box(g, x, LIST_Y, innerW, ROW_H * SurvivorScenes.SHOWN.size(), AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.35F,
                AflUiStyle.LINE, 0.16F, 0, 0);
        for (int i = 0; i < SurvivorScenes.SHOWN.size(); i++) sceneRow(g, SurvivorScenes.SHOWN.get(i), x, LIST_Y + i * ROW_H, innerW, mx, my);
        SurvivorScenes.Scene picked = SurvivorScenes.byId(scene);
        if (!picked.connected()) {
            SetupCanvas.text(g, font, SetupCanvas.tr("newworld.scene_pending"), x, LIST_Y + ROW_H * SurvivorScenes.SHOWN.size() + 3, AflUiStyle.ACCENT, 0.85F, 0.6F);
        }

        // footer
        Component cheatsText = SetupCanvas.tr(cheats ? "newworld.cheats_on" : "newworld.cheats_off");
        boolean cheatsHover = SetupCanvas.in(mx, my, x, FOOT_Y + 4, SetupCanvas.width(font, cheatsText, 0.65F), 8);
        SetupCanvas.text(g, font, cheatsText, x, FOOT_Y + 5, cheatsHover ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1, 0.65F);
        Component vanillaText = SetupCanvas.tr("newworld.vanilla");
        float vx = x, vy = FOOT_Y + 12;
        boolean vanillaHover = SetupCanvas.in(mx, my, vx, vy - 1, SetupCanvas.width(font, vanillaText, 0.6F), 7);
        SetupCanvas.text(g, font, vanillaText, vx, vy, vanillaHover ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1, 0.6F);
        float createW = 46, cancelW = 32;
        button(g, right - createW, FOOT_Y, createW, SetupCanvas.tr(setupFirst() ? "newworld.next" : "newworld.create"), mx, my, ready(), true);
        button(g, right - createW - 4 - cancelW, FOOT_Y, cancelW, SetupCanvas.tr("newworld.cancel"), mx, my, true, false);
        canvas.pop(g);
    }

    private void fieldBox(GuiGraphics g, float x, float w, boolean focus) {
        AflUiShapes.box(g, x, FIELD_Y, w, FIELD_H, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.7F,
                focus ? AflUiStyle.ACCENT : AflUiStyle.LINE, focus ? 0.9F : AflUiStyle.LINE_ALPHA, 0, 0);
    }

    /** A heartbeat (lub-dub) every {@link #BEAT_MS}: 0..1. */
    private static float beat(long now) {
        float t = (now % BEAT_MS) / (float) BEAT_MS;
        float lub = Math.max(0, 1 - Math.abs(t - 0.06F) / 0.07F), dub = 0.6F * Math.max(0, 1 - Math.abs(t - 0.24F) / 0.07F);
        float b = Math.max(lub, dub);
        return b * b * (3 - 2 * b);
    }

    private void modeCard(GuiGraphics g, float x, float w, SurvivorMode m, float mx, float my, boolean enabled) {
        if (m == SurvivorMode.HARD) {
            hardCard(g, x, w, mx, my);
            return;
        }
        boolean on = mode == m, hover = enabled && SetupCanvas.in(mx, my, x, MODE_Y, w, MODE_H);
        AflUiShapes.box(g, x, MODE_Y, w, MODE_H, AflUiStyle.RADIUS_TILE, on || hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK,
                on ? 0.8F : 0.45F, AflUiStyle.LINE, on ? 0.5F : 0.2F, 0, 0);
        if (on) AflUiShapes.fill(g, x + 6, MODE_Y + MODE_H - 1.6F, w - 12, 1.1F, 0.5F, AflUiStyle.ACCENT, 1);
        radio(g, x + 6, MODE_Y + 5, on, enabled);
        int text = enabled ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED;
        SetupCanvas.text(g, font, SetupCanvas.tr("mode." + m.id()), x + 15, MODE_Y + 4.5F, text, 1, 0.85F);
        SetupCanvas.text(g, font, SetupCanvas.tr("newworld.mode." + m.id()), x + 15, MODE_Y + 15, AflUiStyle.TEXT_DIM, enabled ? 1 : 0.6F, 0.58F);
    }

    /**
     * The Hard card: unselected it is a plain card whose line warms to red under the mouse; selected it turns dried
     * blood with a red line and bar that beat like a heart, a red dot, and the title tinted red.
     */
    private void hardCard(GuiGraphics g, float x, float w, float mx, float my) {
        boolean on = mode == SurvivorMode.HARD, hover = SetupCanvas.in(mx, my, x, MODE_Y, w, MODE_H);
        float e = hardBlend * hardBlend * (3 - 2 * hardBlend), beat = beat(net.minecraft.Util.getMillis()) * e;
        AflUiShapes.box(g, x, MODE_Y, w, MODE_H, AflUiStyle.RADIUS_TILE, on || hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK,
                on ? 0.8F : 0.45F, hover && !on ? HARD_LINE : AflUiStyle.LINE, hover && !on ? 0.45F : on ? 0.5F * (1 - e) : 0.2F, 0, 0);
        if (e > 0) {
            AflUiShapes.box(g, x, MODE_Y, w, MODE_H, AflUiStyle.RADIUS_TILE, HARD_FILL, 0.9F * e, HARD_LINE, (0.55F + 0.4F * beat) * e,
                    0, 0);
            // a red glow rising from the bottom edge, stronger on the beat
            AflUiShapes.fill(g, x + 2, MODE_Y + MODE_H * 0.45F, w - 4, MODE_H * 0.55F - 2, AflUiStyle.RADIUS_TILE, HARD_LINE,
                    (0.06F + 0.10F * beat) * e);
            AflUiShapes.fill(g, x + 6, MODE_Y + MODE_H - 1.6F, w - 12, 1.1F, 0.5F, HARD_LINE, (0.75F + 0.25F * beat) * e);
        }
        AflUiShapes.box(g, x + 6, MODE_Y + 5, 6, 6, 3, AflUiStyle.BACK, 0.6F, on ? HARD_LINE : AflUiStyle.LINE, on ? 0.9F : 0.6F, 0, 0);
        if (on) AflUiShapes.fill(g, x + 7.6F, MODE_Y + 6.6F, 2.8F, 2.8F, 1.4F, HARD_LINE, 1);
        SetupCanvas.text(g, font, SetupCanvas.tr("mode.hard"), x + 15, MODE_Y + 4.5F, on ? HARD_TEXT : AflUiStyle.TEXT, 1, 0.85F);
        SetupCanvas.text(g, font, SetupCanvas.tr("newworld.mode.hard"), x + 15, MODE_Y + 15, on ? HARD_DIM : AflUiStyle.TEXT_DIM, 1, 0.58F);
    }

    private void sceneRow(GuiGraphics g, SurvivorScenes.Scene s, float x, float y, float w, float mx, float my) {
        boolean on = scene.equals(s.id()), hover = SetupCanvas.in(mx, my, x, y, w, ROW_H);
        if (on || hover) AflUiShapes.fill(g, x + 0.5F, y + 0.5F, w - 1, ROW_H - 1, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK_HOVER, on ? 0.75F : 0.45F);
        if (y > LIST_Y) AflUiShapes.fill(g, x + 6, y, w - 12, 0.4F, 0, AflUiStyle.LINE, 0.12F);
        radio(g, x + 6, y + 7, on, true);
        Component name = SetupCanvas.tr("scene." + s.name());
        SetupCanvas.text(g, font, name, x + 16, y + 3.5F, AflUiStyle.TEXT, 1, 0.82F);
        if (s.maxPlayers() > 0) {
            float tx = x + 16 + SetupCanvas.width(font, name, 0.82F) + 4;
            Component tag = SetupCanvas.tr("newworld.max_players", s.maxPlayers());
            float tw = SetupCanvas.width(font, tag, 0.55F) + 5;
            AflUiShapes.box(g, tx, y + 3, tw, 8, 1.5F, AflUiStyle.BACK, 0.4F, AflUiStyle.LINE, 0.3F, 0, 0);
            SetupCanvas.text(g, font, tag, tx + 2.5F, y + 4.6F, AflUiStyle.TEXT_DIM, 1, 0.55F);
        }
        SetupCanvas.text(g, font, SetupCanvas.tr("scene." + s.name() + ".desc"), x + 16, y + 12.5F, AflUiStyle.TEXT_DIM, 1, 0.58F);
        if (s.recommended() != null) {
            String bg = s.recommended().substring(s.recommended().indexOf(':') + 1);
            SetupCanvas.right(g, font, SetupCanvas.tr("newworld.recommended", SetupCanvas.tr("background." + bg)), x + w - 6, y + 7.5F,
                    AflUiStyle.TEXT_DIM, 0.75F, 0.55F);
        }
    }

    private void radio(GuiGraphics g, float x, float y, boolean on, boolean enabled) {
        AflUiShapes.box(g, x, y, 6, 6, 3, AflUiStyle.BACK, 0.6F, AflUiStyle.LINE, enabled ? 0.6F : 0.25F, 0, 0);
        if (on) AflUiShapes.fill(g, x + 1.6F, y + 1.6F, 2.8F, 2.8F, 1.4F, enabled ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
    }

    private void smallButton(GuiGraphics g, float x, float y, float w, Component label, float mx, float my) {
        boolean hover = SetupCanvas.in(mx, my, x, y, w, 10);
        AflUiDraw.button(g, x, y, w, 10, hover, false, true, 1);
        SetupCanvas.centred(g, font, label, x + w / 2, y + 2.4F, AflUiStyle.TEXT, 1, 0.6F);
    }

    private void button(GuiGraphics g, float x, float y, float w, Component label, float mx, float my, boolean enabled, boolean primary) {
        boolean hover = enabled && SetupCanvas.in(mx, my, x, y, w, BUTTON_H);
        if (primary && enabled) {
            AflUiShapes.box(g, x, y, w, BUTTON_H, AflUiStyle.RADIUS_TILE, AflUiStyle.KEY_BACK, hover ? 1 : 0.9F, AflUiStyle.LINE, 0.5F, 0, 0);
            SetupCanvas.centred(g, font, label, x + w / 2, y + 4.5F, AflUiStyle.KEY_TEXT, 1, 0.8F);
        } else {
            AflUiDraw.button(g, x, y, w, BUTTON_H, hover, false, enabled, 1);
            SetupCanvas.centred(g, font, label, x + w / 2, y + 4.5F, enabled ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1, 0.8F);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float mx = canvas.vx(mouseX), my = canvas.vy(mouseY);
        float x = CARD_X + PAD, innerW = CARD_W - 2 * PAD, right = x + innerW, nameW = innerW * 0.57F;
        if (SetupCanvas.in(mx, my, x, FIELD_Y, nameW, FIELD_H)) return focus(nameField, mx, my);
        if (SetupCanvas.in(mx, my, x + nameW + 6, FIELD_Y, innerW - nameW - 6, FIELD_H)) return focus(seedField, mx, my);
        float half = (innerW - 4) / 2;
        if (!vanilla.getUiState().isHardcore() && SetupCanvas.in(mx, my, x, MODE_Y, half, MODE_H)) return pick(SurvivorMode.NORMAL);
        if (SetupCanvas.in(mx, my, x + half + 4, MODE_Y, half, MODE_H)) return pick(SurvivorMode.HARD);
        if (SetupCanvas.in(mx, my, right - 28, LIST_Y - 13, 28, 10)) {
            scene = SurvivorScenes.SHOWN.get((int) (Math.random() * SurvivorScenes.SHOWN.size())).id();
            click();
            return true;
        }
        for (int i = 0; i < SurvivorScenes.SHOWN.size(); i++) {
            if (SetupCanvas.in(mx, my, x, LIST_Y + i * ROW_H, innerW, ROW_H)) {
                scene = SurvivorScenes.SHOWN.get(i).id();
                click();
                return true;
            }
        }
        if (SetupCanvas.in(mx, my, x, FOOT_Y + 4, SetupCanvas.width(font, SetupCanvas.tr(cheats ? "newworld.cheats_on" : "newworld.cheats_off"), 0.65F), 8)) {
            cheats = !cheats;
            vanilla.getUiState().setAllowCheats(cheats);
            cheats = vanilla.getUiState().isAllowCheats();
            click();
            return true;
        }
        if (SetupCanvas.in(mx, my, x, FOOT_Y + 11, SetupCanvas.width(font, SetupCanvas.tr("newworld.vanilla"), 0.6F), 7)) {
            click();
            passThrough = new WeakReference<>(vanilla);
            Survivors.setCreateScreenScene(null);
            SurvivorCreateWorldClient.preset(vanilla, mode);
            minecraft.setScreen(vanilla);
            return true;
        }
        float createW = 46, cancelW = 32;
        if (SetupCanvas.in(mx, my, right - createW, FOOT_Y, createW, BUTTON_H) && ready()) {
            click();
            next();
            return true;
        }
        if (SetupCanvas.in(mx, my, right - createW - 4 - cancelW, FOOT_Y, cancelW, BUTTON_H)) {
            click();
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean focus(EditBox box, float mx, float my) {
        focused = box;
        nameField.setFocused(box == nameField);
        seedField.setFocused(box == seedField);
        box.mouseClicked(mx, my, 0);
        return true;
    }

    private boolean pick(SurvivorMode m) {
        boolean toHard = m == SurvivorMode.HARD && mode != SurvivorMode.HARD;
        mode = m;
        click();
        // one low heartbeat when Hard is picked
        if (toHard) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.0F));
        return true;
    }

    private void click() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB) {
            focus(focused == nameField ? seedField : nameField, -1, -1);
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && ready()) {
            next();
            return true;
        }
        if (focused != null && focused.keyPressed(key, scanCode, modifiers)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return focused != null && focused.charTyped(c, modifiers) || super.charTyped(c, modifiers);
    }

    /**
     * The world line: AFL's own world shows the region the survivors start in (the Columbian Federation); another
     * world type (picked in the vanilla settings) shows its own name.
     */
    private Component region() {
        var preset = vanilla.getUiState().getWorldType().preset();
        boolean federal = preset != null && preset.unwrapKey().map(k -> k.location().getNamespace().equals("apocalypse_firstlight")).orElse(false);
        return federal ? SetupCanvas.tr("newworld.region", SetupCanvas.tr("newworld.region.federation"))
                : SetupCanvas.tr("newworld.world_type", vanilla.getUiState().getWorldType().describePreset());
    }

    /** The survivor is set up before the world exists, when the data packs gave a catalog (else in the world, as before). */
    private boolean setupFirst() {
        return !com.antaurora.apofirstlight.survivor.SurvivorCatalog.get().backgrounds().isEmpty();
    }

    private void next() {
        vanilla.getUiState().setName(nameField.getValue());
        vanilla.getUiState().setSeed(seedField.getValue());
        if (setupFirst()) minecraft.setScreen(AflSurvivorSetupScreen.beforeWorld(this, scene));
        else create(null);
    }

    /** Creates the world (the vanilla screen does it), with the mode, scene and the survivor setup (null = choose in it). */
    void create(@Nullable net.minecraft.nbt.CompoundTag setup) {
        Survivors.setCreateScreenChoice(vanilla.getUiState().isHardcore() ? SurvivorMode.HARD : mode);
        Survivors.setCreateScreenScene(scene);
        Survivors.setCreateScreenSetup(setup);
        ((CreateWorldScreenAccessor) vanilla).apocalypse$create();
    }

    @Override
    public void onClose() {
        Survivors.setCreateScreenChoice(null);
        Survivors.setCreateScreenScene(null);
        Survivors.setCreateScreenSetup(null);
        vanilla.popScreen();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
