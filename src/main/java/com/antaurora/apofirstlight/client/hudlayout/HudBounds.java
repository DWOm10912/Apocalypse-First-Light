package com.antaurora.apofirstlight.client.hudlayout;

/** GUI-scaled screen coordinates, not framebuffer pixels. */
public record HudBounds(float x, float y, float width, float height) {
    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }
    public HudBounds union(HudBounds b) {
        float left = Math.min(x, b.x), top = Math.min(y, b.y);
        return new HudBounds(left, top, Math.max(x + width, b.x + b.width) - left,
                Math.max(y + height, b.y + b.height) - top);
    }
}
