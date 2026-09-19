package com.antaurora.apofirstlight.client.hudlayout;

import java.util.List;

/** Adapter supplies effective values (including loader fallbacks) and screen-to-layout units. */
public record HudEditableElement(String id, HudBounds bounds, String xPath, String yPath,
                                 float x, float y, float unitsPerPixel, boolean integral,
                                 float offsetLimit, List<Scale> scales, Resize resize) {
    public record Scale(String path, float value, float min, float max) {}
    /** Dimension paths are optional so older HUD adapters remain move/scale-only. */
    public record Resize(String widthPath, String heightPath, float width, float height,
                         float minWidth, float maxWidth, float minHeight, float maxHeight,
                         boolean centeredX, boolean centeredY, boolean bottomRightAnchor) {}
    public HudEditableElement(String id, HudBounds bounds, String xPath, String yPath,
                              float x, float y, float unitsPerPixel, boolean integral,
                              float offsetLimit, List<Scale> scales) {
        this(id,bounds,xPath,yPath,x,y,unitsPerPixel,integral,offsetLimit,scales,null);
    }
    public boolean draggable() { return xPath != null && yPath != null; }
    public boolean scalable() { return !scales.isEmpty(); }
    public boolean resizable() { return resize != null; }
}
