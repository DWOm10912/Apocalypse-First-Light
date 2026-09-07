package com.antaurora.apofirstlight.weapon;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Packaged animation timelines are authoritative for configured weapon actions. */
public final class NativeGunAnimations {
    public record Cue(int tick, ResourceLocation sound) {}
    private static final Map<String, JsonObject> CACHE = new HashMap<>();
    private static synchronized JsonObject clip(String asset, String clip) {
        JsonObject animations = CACHE.computeIfAbsent(asset, key -> {
            String path = "/assets/apocalypse_firstlight/animations/" + key + ".animation.json";
            try (var in = NativeGunAnimations.class.getResourceAsStream(path)) {
                if (in == null) throw new IllegalStateException("Missing " + path);
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animations");
            } catch (Exception e) { throw new IllegalStateException("Invalid " + path, e); }
        });
        if (!animations.has(clip)) throw new IllegalArgumentException("Missing clip " + asset + "/" + clip);
        return animations.getAsJsonObject(clip);
    }
    public static int ticks(String asset, String clip) {
        return (int)Math.ceil(clip(asset, clip).get("animation_length").getAsDouble() * 20);
    }
    public static List<Cue> cues(String asset, String clip) {
        List<Cue> cues = new ArrayList<>();
        var a = clip(asset, clip);
        if (a.has("sound_effects")) a.getAsJsonObject("sound_effects").entrySet().forEach(e ->
                cues.add(new Cue((int)Math.ceil(Double.parseDouble(e.getKey()) * 20),
                        new ResourceLocation(e.getValue().getAsJsonObject().get("effect").getAsString()))));
        return cues;
    }
}
