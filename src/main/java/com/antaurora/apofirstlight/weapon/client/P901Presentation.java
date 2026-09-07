package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.GeoBone;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.HashMap;

/** P9-only visual equip sampling, driven by vanilla's existing equip clock.
 * Poses come from the same exported bbmodel clips, not a second gameplay action. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class P901Presentation {
    private record Track(NavigableMap<Double, float[]> position, NavigableMap<Double, float[]> rotation) {}
    private record Clip(double length, Map<String, Track> bones) {}
    private static Map<String, Clip> clips = Map.of();
    private static Clip active;
    private static double seconds;
    private P901Presentation() {}

    @SubscribeEvent
    public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            var next = new HashMap<String, Clip>();
            var location = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "animations/p9_01.equip.json");
            try (var reader = manager.openAsReader(location)) {
                var animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
                for (String name : new String[]{"draw", "put_away"}) {
                    var json = animations.getAsJsonObject(name);
                    var bones = new HashMap<String, Track>();
                    for (var entry : json.getAsJsonObject("bones").entrySet()) {
                        var bone = entry.getValue().getAsJsonObject();
                        bones.put(entry.getKey(), new Track(readTrack(bone, "position"), readTrack(bone, "rotation")));
                    }
                    next.put(name, new Clip(json.get("animation_length").getAsDouble(), Map.copyOf(bones)));
                }
                clips = Map.copyOf(next);
            } catch (Exception error) {
                clips = Map.of();
                LogUtils.getLogger().error("Cannot load P9 equip animation {}", location, error);
            }
        });
    }

    private static NavigableMap<Double, float[]> readTrack(JsonObject bone, String channel) {
        var result = new TreeMap<Double, float[]>();
        if (!bone.has(channel)) return result;
        for (var entry : bone.getAsJsonObject(channel).entrySet()) {
            var vector = entry.getValue().getAsJsonObject().getAsJsonArray("vector");
            result.put(Double.parseDouble(entry.getKey()), new float[]{vector.get(0).getAsFloat(),
                    vector.get(1).getAsFloat(), vector.get(2).getAsFloat()});
        }
        return result;
    }

    public static void begin(float equipProgress, boolean outgoing) {
        active = equipProgress > .0001F ? clips.get(outgoing ? "put_away" : "draw") : null;
        if (active != null) seconds = active.length * Math.max(0, Math.min(1, outgoing ? equipProgress : 1 - equipProgress));
    }

    public static void end() { active = null; }

    private static float[] sample(NavigableMap<Double, float[]> keys) {
        if (keys.isEmpty()) return new float[3];
        var lo = keys.floorEntry(seconds);
        var hi = keys.ceilingEntry(seconds);
        if (lo == null) return keys.firstEntry().getValue();
        if (hi == null || hi.getKey().equals(lo.getKey())) return lo.getValue();
        float t = (float)((seconds - lo.getKey()) / (hi.getKey() - lo.getKey()));
        var value = new float[3];
        for (int i = 0; i < 3; i++) value[i] = lo.getValue()[i] + (hi.getValue()[i] - lo.getValue()[i]) * t;
        return value;
    }

    /** These two animated carriers have zero rest rotation. Restore after traversal
     * so first-person equip cannot leak into GUI, world rendering or Gecko state. */
    public static float[] apply(GeoBone bone) {
        if (active == null) return null;
        var track = active.bones.get(bone.getName());
        if (track == null) return null;
        var before = new float[]{bone.getPosX(), bone.getPosY(), bone.getPosZ(), bone.getRotX(), bone.getRotY(), bone.getRotZ()};
        var p = sample(track.position);
        var r = sample(track.rotation);
        bone.setPosX(p[0]); bone.setPosY(p[1]); bone.setPosZ(p[2]);
        // Gecko's Bedrock loader negates the X/Y rotation channels and converts degrees.
        bone.setRotX((float)Math.toRadians(-r[0]));
        bone.setRotY((float)Math.toRadians(-r[1]));
        bone.setRotZ((float)Math.toRadians(r[2]));
        return before;
    }

    public static void restore(GeoBone bone, float[] before) {
        if (before == null) return;
        bone.setPosX(before[0]); bone.setPosY(before[1]); bone.setPosZ(before[2]);
        bone.setRotX(before[3]); bone.setRotY(before[4]); bone.setRotZ(before[5]);
    }
}
