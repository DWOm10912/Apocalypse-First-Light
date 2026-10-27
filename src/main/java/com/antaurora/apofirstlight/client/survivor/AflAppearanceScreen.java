package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.skeleton.BlockMeshBody;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.survivor.SurvivorAppearance;
import com.antaurora.apofirstlight.survivor.SurvivorPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Character creation V1, plan A (docs/ui/character_creation_v1.md, user 2026-10-26 "用方案A，开始做吧"): the appearance page,
 * its own step before the survivor setup page. Left: sex (female body V1, 2026-10-27: on the old body, the default, it
 * picks the body and the starting skin, SurvivorSkins#starter, and nothing else is offered); on the B5 body (dev switch)
 * also the five skin tones, the hair style, the hair colour (one for now). Middle: the body large (BodyPreview), dragged
 * to turn, scrolled to zoom. Right: the starting clothes, one row per slot, a garment or none. "Next" opens the setup page with the look;
 * that page's Esc comes back here. The background is a plain dark studio backdrop, never the world or the panorama
 * (user: "背景先用其他的，不要用游戏内的画面，就算这个时候进了游戏也别用"). Two ways in, as the setup page: before the world
 * (Esc back to the New World page) and in the world (the server's prompt; Esc leaves to the title screen).
 */
public final class AflAppearanceScreen extends Screen {
    private static final float TOP = 44, BOTTOM = 255;
    private static final float LEFT_X = 15, LEFT_W = 118;
    private static final float MID_X = 139, MID_W = 202;
    private static final float RIGHT_X = 347, RIGHT_W = 118;
    private static final float BUTTON_Y = 238, BUTTON_H = 16;
    private static final float CHIP_H = 13;
    /** A clothes row: its label, the garment's tile and "none" beside it. */
    private static final float ROW_H = 8 + CHIP_H + 7, WEAR_W = 66;
    /** The swatches: the body textures' skin colour per tone (b5_export TONES). */
    private static final int[] TONE_RGB = {0xE8BEA0, 0xC28C6A, 0xA06C4C, 0x764C34, 0x523424};
    private static final int HAIR_RGB = 0x3A291F;
    private static final SurvivorAppearance.Garment[] ROWS = SurvivorAppearance.Garment.values();
    private static final String[] ROW_LABEL = {"slot.base", "slot.coat", "slot.legs", "slot.feet"};

    @Nullable
    private final AflNewWorldScreen newWorld;
    @Nullable
    private final String scene;
    @Nullable
    private final SurvivorPackets.SetupPrompt prompt;
    private final SetupCanvas canvas = new SetupCanvas();
    private final BodyPreview preview = new BodyPreview();
    private final Random random = new Random();
    /** The setup page once opened, kept so going back and forth keeps its choices. */
    @Nullable
    private AflSurvivorSetupScreen setup;

    private boolean female;
    private int tone = SurvivorAppearance.DEFAULT_TONE;
    private String hair = SurvivorAppearance.DEFAULT_HAIR;
    private int outfit = SurvivorAppearance.FULL_OUTFIT;
    private float yaw = -24, pitch = 4, zoom = 1;
    private boolean dragging;
    private double dragX, dragY;

    /** In the world: the server's prompt (the setup page gets it on "Next"). */
    public AflAppearanceScreen(SurvivorPackets.SetupPrompt prompt) {
        this(null, null, prompt);
    }

    /** Before the world: the New World page and the scene picked there. */
    static AflAppearanceScreen beforeWorld(AflNewWorldScreen newWorld, String scene) {
        return new AflAppearanceScreen(newWorld, scene, null);
    }

    private AflAppearanceScreen(@Nullable AflNewWorldScreen newWorld, @Nullable String scene, @Nullable SurvivorPackets.SetupPrompt prompt) {
        super(SetupCanvas.tr("appearance.title"));
        this.newWorld = newWorld;
        this.scene = scene;
        this.prompt = prompt;
    }

    SurvivorAppearance.Look look() {
        return new SurvivorAppearance.Look(tone, hair, outfit, female);
    }

    @Override
    protected void init() {
        canvas.layout(minecraft, width, height);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        backdrop(g);
        float mx = canvas.vx(mouseX), my = canvas.vy(mouseY);
        canvas.push(g);
        header(g);
        left(g, mx, my);
        middle(g);
        right(g, mx, my);
        footer(g, mx, my);
        canvas.pop(g);
    }

    /** A dark studio backdrop over the whole window: a vertical fall-off, a lighter band behind the body, a floor shadow. */
    private void backdrop(GuiGraphics g) {
        g.fillGradient(0, 0, width, height, 0xFF1C1F23, 0xFF0B0C0E);
        float cx = canvas.originX + (MID_X + MID_W / 2) * canvas.scale;
        int band = Math.round(MID_W * 0.42F * canvas.scale);
        int top = Math.round(canvas.originY + TOP * canvas.scale), bottom = Math.round(canvas.originY + (BOTTOM - 26) * canvas.scale);
        for (int i = 0; i < 6; i++) {
            int w = band * (6 - i) / 6;
            g.fillGradient(Math.round(cx - w), top, Math.round(cx + w), bottom, AflUiStyle.argb(0x8A8F96, 0.012F), AflUiStyle.argb(0x8A8F96, 0.028F));
        }
    }

    private void header(GuiGraphics g) {
        SetupCanvas.keyHint(g, font, "Esc", SetupCanvas.tr(newWorld != null ? "newworld.back" : "setup.leave"), 15, 12, 0.75F);
        SetupCanvas.centred(g, font, SetupCanvas.tr("appearance.title"), 240, 9, AflUiStyle.TEXT, 1, 1.3F);
        SetupCanvas.centred(g, font, SetupCanvas.tr("appearance.subtitle"), 240, 23, AflUiStyle.TEXT_DIM, 1, 0.62F);
        // the steps: this page, then the survivor page
        Component one = SetupCanvas.tr("appearance.step_look"), two = SetupCanvas.tr("appearance.step_setup");
        float w1 = SetupCanvas.width(font, one, 0.6F), w2 = SetupCanvas.width(font, two, 0.6F), gap = 14;
        float x = 240 - (w1 + gap + w2) / 2;
        SetupCanvas.text(g, font, one, x, 33, AflUiStyle.ACCENT, 1, 0.6F);
        AflUiShapes.fill(g, x + w1 + 3, 35.2F, gap - 6, 0.6F, 0, AflUiStyle.LINE, 0.4F);
        SetupCanvas.text(g, font, two, x + w1 + gap, 33, AflUiStyle.TEXT_DISABLED, 1, 0.6F);
    }

    // ---- left: sex, tone, hair

    private void left(GuiGraphics g, float mx, float my) {
        AflUiDraw.panel(g, LEFT_X, TOP, LEFT_W, BOTTOM - TOP, 1);
        float x = LEFT_X + 7, w = LEFT_W - 14, y = TOP + 6;
        SetupCanvas.text(g, font, SetupCanvas.tr("appearance.sex"), x, y, AflUiStyle.TEXT_DIM, 1, 0.7F);
        float half = (w - 4) / 2;
        chip(g, x, y + 10, half, SetupCanvas.tr("appearance.sex.male"), !female, true, mx, my);
        chip(g, x + half + 4, y + 10, half, SetupCanvas.tr("appearance.sex.female"), female, true, mx, my);
        // the old body (the default): one starting skin per sex, so the B5 body's tone and hair are not offered
        if (!BlockMeshBody.active()) return;

        y += 34;
        SetupCanvas.text(g, font, SetupCanvas.tr("appearance.tone"), x, y, AflUiStyle.TEXT_DIM, 1, 0.7F);
        float sw = (w - 4 * 4) / 5;
        for (int i = 0; i < 5; i++) {
            float sx = x + i * (sw + 4), sy = y + 10;
            boolean on = tone == i + 1, hover = SetupCanvas.in(mx, my, sx, sy, sw, sw);
            AflUiShapes.box(g, sx, sy, sw, sw, AflUiStyle.RADIUS_TILE, TONE_RGB[i], 1, on ? AflUiStyle.ACCENT : AflUiStyle.LINE,
                    on ? 0.95F : hover ? 0.55F : 0.18F, 0, 0);
        }

        y += 10 + sw + 12;
        SetupCanvas.text(g, font, SetupCanvas.tr("appearance.hair"), x, y, AflUiStyle.TEXT_DIM, 1, 0.7F);
        float cy = y + 10;
        for (String style : SurvivorAppearance.HAIR) {
            chip(g, x, cy, w, SetupCanvas.tr("appearance.hair." + style), style.equals(hair), true, mx, my);
            cy += CHIP_H + 3;
        }

        y = cy + 9;
        SetupCanvas.text(g, font, SetupCanvas.tr("appearance.hair_colour"), x, y, AflUiStyle.TEXT_DIM, 1, 0.7F);
        SetupCanvas.right(g, font, SetupCanvas.tr("appearance.later"), x + w, y + 0.6F, AflUiStyle.TEXT_DISABLED, 1, 0.52F);
        AflUiShapes.box(g, x, y + 10, 12, 12, AflUiStyle.RADIUS_TILE, HAIR_RGB, 1, AflUiStyle.ACCENT, 0.95F, 0, 0);
        SetupCanvas.text(g, font, SetupCanvas.tr("appearance.hair_colour.dark_brown"), x + 17, y + 13.5F, AflUiStyle.TEXT, 1, 0.65F);
    }

    // ---- middle: the body

    private void middle(GuiGraphics g) {
        AflUiShapes.box(g, MID_X, TOP, MID_W, BOTTOM - TOP, AflUiStyle.RADIUS_PANEL, 0, 0, AflUiStyle.LINE, 0.12F, 0, 0);
        float cx = MID_X + MID_W / 2, feet = BOTTOM - 26;
        // the floor: a soft shadow under the feet
        for (int i = 0; i < 4; i++) {
            float rw = 30 + i * 9, rh = 4 + i * 1.6F;
            AflUiShapes.fill(g, cx - rw, feet - rh / 2, rw * 2, rh, rh / 2, 0x000000, 0.13F);
        }
        canvas.scissor(g, MID_X + 1, TOP + 1, MID_W - 2, BOTTOM - TOP - 2);
        float size = 92 * zoom;
        // zoomed in, the body rises so the head stays in view
        float lift = (zoom - 1) * 70;
        preview.render(g, look(), cx, feet + lift, size, yaw, pitch);
        g.disableScissor();
        SetupCanvas.centred(g, font, SetupCanvas.tr("appearance.drag"), cx, BOTTOM - 11, AflUiStyle.TEXT_DISABLED, 1, 0.55F);
    }

    // ---- right: the starting clothes

    private void right(GuiGraphics g, float mx, float my) {
        AflUiDraw.panel(g, RIGHT_X, TOP, RIGHT_W, BUTTON_Y - 6 - TOP, 1);
        float x = RIGHT_X + 7, w = RIGHT_W - 14, y = TOP + 6;
        SetupCanvas.text(g, font, SetupCanvas.tr("appearance.outfit"), x, y, AflUiStyle.TEXT_DIM, 1, 0.7F);
        y += 13;
        for (int i = 0; i < ROWS.length; i++) {
            SurvivorAppearance.Garment garment = ROWS[i];
            boolean on = (outfit & garment.bit()) != 0;
            SetupCanvas.text(g, font, SetupCanvas.tr("appearance." + ROW_LABEL[i]), x, y, AflUiStyle.TEXT_DIM, 1, 0.58F);
            chip(g, x, y + 8, WEAR_W, Component.translatable(garment.item().getDescriptionId()), on, true, mx, my);
            chip(g, x + WEAR_W + 3, y + 8, w - WEAR_W - 3, SetupCanvas.tr("appearance.none"), !on, true, mx, my);
            y += ROW_H;
        }
        float ty = y + 2;
        for (var line : font.split(SetupCanvas.tr("appearance.outfit_note"), Math.max(1, (int) (w / 0.55F)))) {
            g.pose().pushPose();
            g.pose().translate(x, ty, 0);
            g.pose().scale(0.55F, 0.55F, 1);
            g.drawString(font, line, 0, 0, AflUiStyle.argb(AflUiStyle.TEXT_DISABLED, 1), false);
            g.pose().popPose();
            ty += 6.5F;
        }
    }

    private void footer(GuiGraphics g, float mx, float my) {
        float randomW = 34, nextW = RIGHT_W - randomW - 4;
        boolean hoverRandom = SetupCanvas.in(mx, my, RIGHT_X, BUTTON_Y, randomW, BUTTON_H);
        AflUiDraw.button(g, RIGHT_X, BUTTON_Y, randomW, BUTTON_H, hoverRandom, false, true, 1);
        SetupCanvas.centred(g, font, SetupCanvas.tr("appearance.random"), RIGHT_X + randomW / 2, BUTTON_Y + 4.5F, AflUiStyle.TEXT, 1, 0.75F);
        float nx = RIGHT_X + randomW + 4;
        boolean hover = SetupCanvas.in(mx, my, nx, BUTTON_Y, nextW, BUTTON_H);
        AflUiShapes.box(g, nx, BUTTON_Y, nextW, BUTTON_H, AflUiStyle.RADIUS_TILE, AflUiStyle.KEY_BACK, hover ? 1 : 0.9F, AflUiStyle.LINE, 0.5F, 0, 0);
        SetupCanvas.centred(g, font, SetupCanvas.tr("appearance.next"), nx + nextW / 2, BUTTON_Y + 4.5F, AflUiStyle.KEY_TEXT, 1, 0.8F);
        SetupCanvas.right(g, font, SetupCanvas.tr("appearance.final"), RIGHT_X + RIGHT_W, BUTTON_Y + BUTTON_H + 3.5F, AflUiStyle.TEXT_DISABLED, 1, 0.52F);
    }

    /** A choice tile: accent-lined when chosen; a disabled one is dimmed and takes no clicks. */
    private void chip(GuiGraphics g, float x, float y, float w, Component label, boolean on, boolean enabled, float mx, float my) {
        boolean hover = enabled && SetupCanvas.in(mx, my, x, y, w, CHIP_H);
        AflUiShapes.box(g, x, y, w, CHIP_H, AflUiStyle.RADIUS_TILE, on || hover ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK,
                enabled ? on ? 0.85F : 0.4F : 0.2F, on ? AflUiStyle.ACCENT : AflUiStyle.LINE, on ? 0.7F : enabled ? 0.16F : 0.08F, 0, 0);
        SetupCanvas.text(g, font, label, x + 5, y + 3.4F, enabled ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1, 0.68F);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        float mx = canvas.vx(mouseX), my = canvas.vy(mouseY);
        if (SetupCanvas.in(mx, my, MID_X, TOP, MID_W, BOTTOM - TOP)) {
            dragging = true;
            dragX = mouseX;
            dragY = mouseY;
            return true;
        }
        // left: sex, tone swatches and hair styles (the same layout as #left)
        float x = LEFT_X + 7, w = LEFT_W - 14, y = TOP + 6 + 34;
        float half = (w - 4) / 2;
        for (int i = 0; i < 2; i++) {
            if (SetupCanvas.in(mx, my, x + i * (half + 4), TOP + 6 + 10, half, CHIP_H)) {
                female = i == 1;
                click();
                return true;
            }
        }
        float sw = (w - 4 * 4) / 5;
        for (int i = 0; i < 5 && BlockMeshBody.active(); i++) {
            if (SetupCanvas.in(mx, my, x + i * (sw + 4), y + 10, sw, sw)) {
                tone = i + 1;
                click();
                return true;
            }
        }
        float cy = y + 10 + sw + 12 + 10;
        for (String style : BlockMeshBody.active() ? SurvivorAppearance.HAIR : java.util.List.<String>of()) {
            if (SetupCanvas.in(mx, my, x, cy, w, CHIP_H)) {
                hair = style;
                click();
                return true;
            }
            cy += CHIP_H + 3;
        }
        // right: one garment or none per row
        float rx = RIGHT_X + 7, rw = RIGHT_W - 14, ry = TOP + 6 + 13;
        for (SurvivorAppearance.Garment garment : ROWS) {
            if (SetupCanvas.in(mx, my, rx, ry + 8, WEAR_W, CHIP_H)) {
                outfit |= garment.bit();
                click();
                return true;
            }
            if (SetupCanvas.in(mx, my, rx + WEAR_W + 3, ry + 8, rw - WEAR_W - 3, CHIP_H)) {
                outfit &= ~garment.bit();
                click();
                return true;
            }
            ry += ROW_H;
        }
        float randomW = 34;
        if (SetupCanvas.in(mx, my, RIGHT_X, BUTTON_Y, randomW, BUTTON_H)) {
            randomize();
            click();
            return true;
        }
        if (SetupCanvas.in(mx, my, RIGHT_X + randomW + 4, BUTTON_Y, RIGHT_W - randomW - 4, BUTTON_H)) {
            click();
            next();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!dragging) return false;
        yaw += (float) (mouseX - dragX) / canvas.scale * 1.2F;
        pitch = Mth.clamp(pitch + (float) (mouseY - dragY) / canvas.scale * 0.4F, -25, 30);
        dragX = mouseX;
        dragY = mouseY;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        zoom = Mth.clamp(zoom + (float) delta * 0.12F, 1, 2.2F);
        return true;
    }

    private void randomize() {
        female = random.nextBoolean();
        tone = 1 + random.nextInt(SurvivorAppearance.TONES);
        hair = SurvivorAppearance.HAIR.get(random.nextInt(SurvivorAppearance.HAIR.size()));
        outfit = random.nextInt(SurvivorAppearance.FULL_OUTFIT + 1);
    }

    /** The setup page with this look (made once, kept for going back and forth). */
    private void next() {
        if (setup == null) {
            setup = newWorld != null ? AflSurvivorSetupScreen.beforeWorld(newWorld, scene, this) : new AflSurvivorSetupScreen(prompt, this);
        }
        minecraft.setScreen(setup);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (newWorld != null) minecraft.setScreen(newWorld);
            else leave();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            next();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    /** As the setup page leaves: disconnect, save, title screen. The setup waits for the next join. */
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

    /** Single player pauses while choosing, as on the setup page. */
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
