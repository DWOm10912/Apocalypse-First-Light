package com.antaurora.apofirstlight.client.hudlayout;

import java.util.List;

/** Adapter supplies effective values (including loader fallbacks) and screen-to-layout units. */
public record HudEditableElement(String id, HudBounds bounds, String xPath, String yPath,
                                 float x, float y, float unitsPerPixel, boolean integral,
                                 float offsetLimit, List<Scale> scales) {
    public record Scale(String path, float value, float min, float max) {}
    public boolean draggable() { return xPath != null && yPath != null; }
    public boolean scalable() { return !scales.isEmpty(); }
}
