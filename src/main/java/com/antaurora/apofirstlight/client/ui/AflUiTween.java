package com.antaurora.apofirstlight.client.ui;

import net.minecraft.client.Minecraft;

/**
 * A value easing towards its target over real time (System.nanoTime), not frames; retargeting starts from wherever the
 * value is now. Also the screen-pixel snapping every animated offset goes through (motion is smooth at GUI scale 3-4,
 * text and icons never land between screen pixels).
 */
public final class AflUiTween {
    public enum Ease {
        LINEAR, OUT, IN_OUT;
        public float apply(float t) {
            return switch (this) {
                case LINEAR -> t;
                case OUT -> 1 - (1 - t) * (1 - t) * (1 - t);
                case IN_OUT -> t * t * (3 - 2 * t);
            };
        }
    }

    private float from, to;
    private long start;
    private float millis;
    private Ease ease;

    public AflUiTween(float value) {
        from = to = value;
        ease = Ease.OUT;
    }

    public float target() {
        return to;
    }

    public float value(long now) {
        if (millis <= 0) return to;
        float t = (now - start) / 1_000_000f / millis;
        if (t >= 1) return to;
        if (t <= 0) return from;
        return from + (to - from) * ease.apply(t);
    }

    /** Ease to {@code target} over {@code millis} from the current value (no restart if it is already the target). */
    public AflUiTween to(float target, float millis, Ease ease, long now) {
        if (target == to) return this;
        from = value(now);
        to = target;
        start = now;
        this.millis = millis;
        this.ease = ease;
        return this;
    }

    /** Jump to {@code value}, no motion. */
    public AflUiTween snapTo(float value) {
        from = to = value;
        millis = 0;
        return this;
    }

    public boolean moving(long now) {
        return millis > 0 && (now - start) / 1_000_000f < millis;
    }

    public static float scale() {
        return (float) Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** A GUI-pixel length rounded to whole screen pixels. */
    public static float snap(float gui) {
        float s = scale();
        return Math.round(gui * s) / s;
    }

    public static float clamp01(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
