package com.antaurora.apofirstlight.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * One key cap (docs/ui/afl_overlay_ui_style_v1.md 3.5): a key mapping, shown by its current binding (rebinding in the
 * controls screen changes the cap at once), or a fixed mouse action. A mapping bound to the left, right or middle mouse
 * button and the fixed mouse actions draw a small mouse with that button marked; any other binding draws its name.
 * Pressing the key (polled from GLFW, so it also works while a screen is open) turns the cap's mark amber and sinks it
 * one GUI pixel, until 80 ms after release.
 */
public final class AflKeyHint {
    public static final int LEFT = 0, RIGHT = 1, MIDDLE = 2, SCROLL = 3;
    public static final AflKeyHint MOUSE_LEFT = new AflKeyHint(null, LEFT), MOUSE_RIGHT = new AflKeyHint(null, RIGHT),
            MOUSE_MIDDLE = new AflKeyHint(null, MIDDLE), MOUSE_SCROLL = new AflKeyHint(null, SCROLL);
    public static final float HEIGHT = 11, MOUSE_WIDTH = 11;
    private static final long RELEASE_NANOS = 80_000_000L;
    private static long scrolled;
    private static final java.util.Map<KeyMapping, AflKeyHint> BY_MAPPING = new java.util.IdentityHashMap<>();

    @Nullable private final KeyMapping mapping;
    private final int mouse;
    private long lastDown;

    private AflKeyHint(@Nullable KeyMapping mapping, int mouse) {
        this.mapping = mapping;
        this.mouse = mouse;
    }

    /** One cap per mapping, kept so its release timing survives rows rebuilt every frame. */
    public static AflKeyHint of(KeyMapping mapping) {
        return BY_MAPPING.computeIfAbsent(mapping, m -> new AflKeyHint(m, -1));
    }

    /** The scroll cap lights up briefly; screens call this from mouseScrolled. */
    public static void scrolled() {
        scrolled = System.nanoTime();
    }

    /** The mouse button this cap draws (0 left, 1 right, 2 middle, 3 scroll), or -1 for a named key. */
    private int mouseButton() {
        if (mapping == null) return mouse;
        InputConstants.Key key = mapping.getKey();
        return key.getType() == InputConstants.Type.MOUSE && key.getValue() >= 0 && key.getValue() <= 2 ? key.getValue() : -1;
    }

    private Component name() {
        return mapping == null ? Component.empty() : mapping.getTranslatedKeyMessage();
    }

    private boolean down(long now) {
        boolean down;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (mapping == null) {
            down = mouse == SCROLL ? now - scrolled < 150_000_000L : GLFW.glfwGetMouseButton(window, mouse) == GLFW.GLFW_PRESS;
        } else {
            InputConstants.Key key = mapping.getKey();
            if (key == InputConstants.UNKNOWN) down = false;
            else if (key.getType() == InputConstants.Type.MOUSE) down = GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            else down = key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
        }
        if (down) lastDown = now;
        return down || now - lastDown < RELEASE_NANOS;
    }

    public float width(Font font) {
        if (mouseButton() >= 0) return MOUSE_WIDTH;
        String text = clipped(font);
        return Math.max(HEIGHT, font.width(text) + 6);
    }

    private String clipped(Font font) {
        String text = name().getString();
        if (font.width(text) <= 54) return text;
        return font.plainSubstrByWidth(text, 54 - font.width("…")) + "…";
    }

    /** Draws the cap with its top-left at (x, y), GUI px; alpha fades it. */
    public void draw(GuiGraphics g, Font font, float x, float y, float alpha, long now) {
        if (alpha <= 0.02F) return;
        boolean pressed = down(now);
        float w = width(font), sink = pressed ? AflUiTween.snap(1) : 0;
        y += sink;
        int button = mouseButton();
        AflUiShapes.fill(g, x, y, w, HEIGHT, AflUiStyle.RADIUS_KEY, AflUiStyle.KEY_BACK, AflUiStyle.KEY_BACK_ALPHA * alpha);
        if (button >= 0) {
            mouse(g, x + (w - 5) / 2, y + 1.5F, button, pressed, alpha);
            return;
        }
        if (pressed) AflUiShapes.fill(g, x + 1.5F, y + HEIGHT - 1.5F, w - 3, 1, 0.5F, AflUiStyle.ACCENT, alpha);
        String text = clipped(font);
        g.pose().pushPose();
        g.pose().translate(x + (w - font.width(text)) / 2F, y + 2, 0);
        g.drawString(font, text, 0, 0, AflUiStyle.argb(AflUiStyle.KEY_TEXT, alpha), false);
        g.pose().popPose();
    }

    /** A 5 x 8 GUI px mouse: outline, the button split, the used button filled (amber while pressed). */
    private static void mouse(GuiGraphics g, float x, float y, int button, boolean pressed, float alpha) {
        int dark = AflUiStyle.KEY_TEXT, mark = pressed ? AflUiStyle.ACCENT : dark;
        AflUiShapes.box(g, x, y, 5, 8, 2.5F, 0, 0, dark, 0.85F * alpha, 0, 0);
        float line = 1 / AflUiTween.scale();
        AflUiShapes.fill(g, x + 0.5F, y + 3.5F, 4, line, 0, dark, 0.85F * alpha);         // across, under the buttons
        if (button == LEFT || button == RIGHT) {
            AflUiShapes.fill(g, x + 2.5F - line / 2, y + 0.5F, line, 3, 0, dark, 0.85F * alpha);   // between the buttons
            AflUiShapes.fill(g, button == LEFT ? x + 1 : x + 2.5F + line, y + 1, 1.5F - line, 2.5F, 0.5F, mark, 0.95F * alpha);
        } else {
            float h = button == SCROLL ? 2.5F : 1.6F;
            AflUiShapes.fill(g, x + 2.5F - 0.6F, y + 1, 1.2F, h, 0.6F, mark, 0.95F * alpha);
        }
    }
}
