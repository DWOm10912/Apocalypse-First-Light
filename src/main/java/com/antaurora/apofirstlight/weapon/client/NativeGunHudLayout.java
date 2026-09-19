package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.client.config.NativeGunHudConfig;

/** Small HUD-only geometry helpers; all dimensions are GUI units, never physical pixels. */
public final class NativeGunHudLayout {
    private NativeGunHudLayout() {}
    public record Frame(float x, float y, float scale, float width, float height) {}
    public record Box(float x, float y, float width, float height) {}
    public record Row(float center, float width) {}

    public static Frame frame(NativeGunHudConfig.Global g, int screenWidth, int screenHeight) {
        float scale = Math.min(g.scale(), Math.min(Math.max(1, screenWidth - 8) / g.width(),
                Math.max(1, screenHeight - 8) / g.height()));
        float w = g.width() * scale, h = g.height() * scale;
        return new Frame(clamp(screenWidth - g.rightMargin() - w + g.offsetX(), 4, screenWidth - 4 - w),
                clamp(screenHeight - g.bottomMargin() - h + g.offsetY(), 4, screenHeight - 4 - h), scale, w, h);
    }

    public static Box divider(NativeGunHudConfig c) {
        var d = c.divider();
        return new Box(d.offsetX(), d.offsetY(), d.width(), d.height());
    }

    public static Box silhouette(NativeGunHudConfig c, float imageWidth, float imageHeight) {
        var s = c.silhouette();
        float scale = Math.min(s.scale(), Math.min(s.width() / Math.max(1,imageWidth), s.height() / Math.max(1,imageHeight)));
        return new Box(s.offsetX() + (s.width() - imageWidth * scale) / 2,
                s.offsetY() + (s.height() - imageHeight * scale) / 2,
                imageWidth * scale, imageHeight * scale);
    }

    /** Editor box is independent of the contained icon's intrinsic aspect ratio. */
    public static Box silhouetteRegion(NativeGunHudConfig c) {
        var s = c.silhouette();
        return new Box(s.offsetX(), s.offsetY(), s.width(), s.height());
    }

    public static Box textRegion(float left, float top, float width, float height) {
        return new Box(left, top, width, height);
    }

    /** Font size is bounded by configured scale, box height and available row width; never grows to fill space. */
    public static float textScale(float configured, float rowWidth, float boxHeight, float textWidth, float lineHeight) {
        float upper=Math.min(configured,boxHeight/lineHeight);
        return Math.max(Math.min(.55F,upper),Math.min(upper,rowWidth/Math.max(1,textWidth)));
    }

    public static Row row(float left, float width) {
        return new Row(left + width / 2, width);
    }

    public static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
