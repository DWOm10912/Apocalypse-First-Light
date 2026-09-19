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
        float x = clamp(d.offsetX(), 1, c.global().width() - d.width() - 8);
        float y = clamp(d.offsetY(), 1, c.global().height() - 2);
        return new Box(x, y, d.width(), Math.min(d.height(), c.global().height() - 1 - y));
    }

    public static Box silhouette(NativeGunHudConfig c, float imageWidth, float imageHeight) {
        var s = c.silhouette();
        float right = Math.max(1, divider(c).x() - 3);
        float cx = clamp(s.offsetX(), .5F, right - .5F);
        float cy = clamp(s.offsetY(), .5F, c.global().height() - .5F);
        float availableWidth = 2 * Math.min(cx, right - cx);
        float availableHeight = 2 * Math.min(cy, c.global().height() - cy);
        float scale = Math.min(s.scale(), Math.min(availableWidth / imageWidth, availableHeight / imageHeight));
        return new Box(cx - imageWidth * scale / 2, cy - imageHeight * scale / 2,
                imageWidth * scale, imageHeight * scale);
    }

    public static Row row(NativeGunHudConfig c, float center, float maxWidth) {
        var d = divider(c);
        float left = d.x() + d.width() + 4, right = c.global().width() - 1;
        center = clamp(center, left + .5F, right - .5F);
        return new Row(center, Math.min(maxWidth, 2 * Math.min(center - left, right - center)));
    }

    public static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
