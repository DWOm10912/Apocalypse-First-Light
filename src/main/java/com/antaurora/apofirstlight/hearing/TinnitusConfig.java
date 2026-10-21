package com.antaurora.apofirstlight.hearing;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.TreeSet;

/**
 * Tinnitus V2 numbers (docs/gameplay/tinnitus_v2_plan.md 3), {@code data/apocalypse_firstlight/hearing/tinnitus_v2.json}; the
 * defaults here are the same. The client reads the episode and hearing numbers from the same object (the defaults, or the
 * integrated server's).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class TinnitusConfig {
    private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static TinnitusConfig current = new TinnitusConfig();

    /** A shot's dose is 10^((ear_db − reference_db) / 10) at the shooter's ear; a suppressor takes suppressor_db off. */
    public double referenceDb = 170, suppressorDb = 25;
    /** Other players' shots count within this many blocks, over the distance squared (the shooter's own at 1). */
    public double range = 32;
    /** No sky over the head: the doses × this (the echo). */
    public double indoor = 2;
    /** A blast's dose: blast × (radius / blast_radius)² / max(1, distance)²; below blast_min it is left out. */
    public double blast = 500, blastRadius = 4, blastMin = 0.05;
    /** A lightning strike counts as a blast of this radius. */
    public double lightningRadius = 4;
    /** The load where each stage starts (light, tinnitus, severe), and its cap. */
    public double[] stages = {10, 30, 60};
    public double maxLoad = 200;
    /** The load halves in this many ticks, asleep sleep times faster. */
    public double halfLifeTicks = 6600, sleep = 3;
    /** One dose of jolt_from or more rings at once (the jolt); its strength is dose / jolt_full, at least jolt_least. */
    public double joltFrom = 3, joltFull = 40, joltLeast = 0.25;

    // ---- the client ----
    /** The world's sounds × this, per stage (light, tinnitus, severe). */
    public double[] hearing = {1, 0.85, 0.7};
    /** Seconds between episodes and how long one rings, [least, most] per stage; its volume per stage. */
    public double[][] interval = {{90, 180}, {40, 90}, {15, 40}};
    public double[][] length = {{3, 5}, {5, 8}, {6, 10}};
    public double[] volume = {0.25, 0.4, 0.55};
    /** A jolt: the world's sounds drop to jolt_hearing and come back over [weakest, strongest] seconds; its ring lasts as long. */
    public double joltHearing = 0.3;
    public double[] joltRecover = {6, 10};

    public static TinnitusConfig get() {
        return current;
    }

    /** The stage of this load: 0 none, 1 light, 2 tinnitus, 3 severe. */
    public int stage(double load) {
        int s = 0;
        for (double from : stages) if (load >= from) s++;
        return s;
    }

    void validate() {
        if (!(referenceDb > 0) || !(suppressorDb >= 0) || !(range > 0) || !(indoor >= 1) || !(blast >= 0) || !(blastRadius > 0)
                || !(blastMin >= 0) || !(lightningRadius >= 0) || !(halfLifeTicks > 0) || !(sleep > 0) || !(joltFrom > 0)
                || !(joltFull > 0) || !(joltLeast >= 0 && joltLeast <= 1) || !(joltHearing >= 0 && joltHearing <= 1))
            throw new IllegalArgumentException("invalid tinnitus numbers");
        if (stages == null || stages.length != 3 || !(stages[0] > 0) || !(stages[1] > stages[0]) || !(stages[2] > stages[1])
                || !(maxLoad >= stages[2]))
            throw new IllegalArgumentException("stages: three ascending loads, max_load at least the last");
        if (!perStage(hearing) || !perStage(volume)) throw new IllegalArgumentException("hearing / volume: three values 0..1");
        for (double[][] ranges : new double[][][]{interval, length}) {
            if (ranges == null || ranges.length != 3) throw new IllegalArgumentException("interval / length: three [least, most]");
            for (double[] r : ranges)
                if (r == null || r.length != 2 || !(r[0] > 0) || !(r[1] >= r[0])) throw new IllegalArgumentException("interval / length: [least, most] > 0");
        }
        if (joltRecover == null || joltRecover.length != 2 || !(joltRecover[0] > 0) || !(joltRecover[1] >= joltRecover[0]))
            throw new IllegalArgumentException("jolt_recover: [weakest, strongest] seconds");
    }

    private static boolean perStage(double[] v) {
        if (v == null || v.length != 3) return false;
        for (double x : v) if (!(x >= 0 && x <= 1)) return false;
        return true;
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "hearing") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                TinnitusConfig loaded = null;
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var config = GSON.fromJson(input.get(id), TinnitusConfig.class);
                        config.validate();
                        loaded = config;
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL HEARING] Rejected {}: {}", id, ex.getMessage());
                    }
                }
                if (loaded != null) current = loaded;
            }
        });
    }
}
