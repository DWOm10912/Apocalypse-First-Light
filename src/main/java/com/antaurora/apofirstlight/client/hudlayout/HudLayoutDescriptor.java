package com.antaurora.apofirstlight.client.hudlayout;

import com.google.gson.JsonObject;
import java.util.List;

/** No HUD-ID branches in the editor. Every schema is owned by its adapter. */
public interface HudLayoutDescriptor {
    String id();
    String fileName();
    JsonObject defaults();
    List<HudEditableElement> elements(JsonObject json, int width, int height);
    HudBounds occupied(JsonObject json, int width, int height);
    void apply(JsonObject json);
}
