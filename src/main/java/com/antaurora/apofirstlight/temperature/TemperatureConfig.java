package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/**
 * Temperature V1 numbers (docs/项目内容/01 - 设计/生存/体温.md), from
 * data/apocalypse_firstlight/temperature/temperature_v1.json (/reload). Fields default to the same values; a file that
 * does not parse or fails the checks is rejected and the previous numbers stay. Temperatures in °C, times in seconds.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class TemperatureConfig {
    /** Daily mean, half the day/night swing, and water temperature of a biome or dimension. */
    public static final class Climate { public double mean = 12, amp = 6, water = 11; }
    /** Unlisted biomes, from vanilla's temperature t and downfall d: mean = base + per_t·t, amp = base + per_dryness·(1 − d). */
    public static final class Fallback { public double meanBase = 1, meanPerTemperature = 14, ampBase = 3, ampPerDryness = 8, waterBelowMean = 1; }
    public static final class Weather { public double rain = -3, thunder = -4; }
    public static final class Altitude { public double startY = 80, perBlock = -0.05; }
    /** Underground weight u = clamp((sea_level − y) / depth_full); rock temperature = base + per_block·max(0, −y). */
    public static final class Cave { public double seaLevel = 63, depthFull = 32, base = 11, perBlockBelowZero = 0.125; }
    public static final class Scan { public int horizontal = 6, below = 2, above = 3; }
    /**
     * strength °C at distance 0, falling to 0 at radius: linearly, or with "falloff": "square" as (1 − d/radius)², steep
     * like radiant heat (lava, fire: very hot up close, gone a few blocks away); lit: only while the block's LIT is true.
     */
    public static final class Source {
        public String block, falloff = "linear";
        public double strength, radius;
        public boolean lit;
        transient boolean square;
    }
    public static final class Wetness {
        public double rainPerSecond = 1 / 60.0, drySeconds = 180, dryHeatPer = 5, dryMaxFactor = 4;
        /** In water: wet rises at soak_per_second × submersion per second, up to submersion × wet_per_submersion. */
        public double soakPerSecond = 1, wetPerSubmersion = 2;
        /** chill × wet, plus immersion_chill × submersion. */
        public double chill = 6, immersionChill = 12, insulationLoss = 0.5;
        /** Exertion heat × this when fully submerged (blended by depth): the water carries it away. */
        public double immersedExertion = 0;
    }
    public static final class Exertion { public double perStamina = 0.6, cap = 8, tauSeconds = 30; }
    /** One stage: entered below / above its threshold; the multipliers are the whole effect at that stage. */
    public static final class Stage {
        public double below = Double.NaN, above = Double.NaN;
        public double staminaRegen = 1, staminaCost = 1, thirst = 1, speed = 1, exhaustionPerSecond = 0;
        public boolean noNaturalRegen, damage;
    }
    public static final class Sway { public double start = 36, full = 34, extra = 0.6; }
    public static final class Hud {
        public double coldStart = 36.5, coldFull = 34, heatStart = 37.5, heatFull = 40;
        public double dangerColdStart = 35, dangerColdFull = 34, dangerHeatStart = 39, dangerHeatFull = 40;
        /** Trend arrows: none while |target − core| is below the dead band, full at trend_full °C. */
        public double trendDeadband = 0.1, trendFull = 1.5;
    }

    public double normal = 37, comfortLow = 14, comfortHigh = 24, coldSlope = 0.15, heatSlope = 0.15, coreMin = 33, coreMax = 41;
    public double tauWorsen = 300, tauWorsenImmersed = 100, tauRecover = 120, fireResistanceTargetCap = 37.5;
    /** The target may lie beyond the core range (lava, freezing water): the core then moves faster, and stops at core_min / max. */
    public double targetMin = 25, targetMax = 55;
    public Map<String, Climate> biomes = Map.of();
    public Fallback fallback = new Fallback();
    public Map<String, Climate> dimensions = Map.of();
    public double biomeBlend = 8;
    public Weather weather = new Weather();
    public Altitude altitude = new Altitude();
    public Cave cave = new Cave();
    /** Per side: the strongest source + source_stacking × the others, then capped; × (outdoor factor .. 1) by the cover. */
    public double outdoorSourceFactor = 0.6, sourceStacking = 0.5, sourceCapHeat = 80, sourceCapCold = -10;
    public Scan scan = new Scan();
    public List<Source> sources = List.of();
    public Wetness wetness = new Wetness();
    public Exertion exertion = new Exertion();
    public double hysteresis = 0.1;
    public List<Stage> coldStages = List.of();
    public List<Stage> heatStages = List.of();
    public double damageIntervalSeconds = 15;
    public Sway sway = new Sway();
    public Hud hud = new Hud();

    /** Built from "sources" on load: block → its entry. */
    transient Map<Block, Source> sourceMap = Map.of();
    static final Stage NONE = new Stage();

    private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static volatile TemperatureConfig current = new TemperatureConfig();
    public static TemperatureConfig get() { return current; }

    private void validate() {
        if (biomes == null) biomes = Map.of();
        if (dimensions == null) dimensions = Map.of();
        if (sources == null) sources = List.of();
        if (coldStages == null) coldStages = List.of();
        if (heatStages == null) heatStages = List.of();
        if (!(comfortLow < comfortHigh) || coldSlope < 0 || heatSlope < 0 || !(coreMin < normal && normal < coreMax)
                || !(tauWorsen > 0) || !(tauWorsenImmersed > 0) || !(tauRecover > 0) || !(targetMin <= coreMin) || !(targetMax >= coreMax)
                || sourceStacking < 0 || sourceStacking > 1 || biomeBlend < 0 || fallback == null
                || weather == null || altitude == null || cave == null || !(cave.depthFull > 0) || scan == null
                || scan.horizontal < 0 || scan.below < 0 || scan.above < 0 || wetness == null || !(wetness.drySeconds > 0)
                || exertion == null || !(exertion.tauSeconds > 0) || hysteresis < 0 || !(damageIntervalSeconds > 0)
                || sway == null || !(sway.start > sway.full) || hud == null || !(hud.coldStart > hud.coldFull)
                || !(hud.heatFull > hud.heatStart) || !(hud.dangerColdStart > hud.dangerColdFull) || !(hud.dangerHeatFull > hud.dangerHeatStart)
                || hud.trendDeadband < 0 || !(hud.trendFull > hud.trendDeadband))
            throw new IllegalArgumentException("invalid temperature numbers");
        for (var c : biomes.values()) if (c == null) throw new IllegalArgumentException("empty biome entry");
        for (var c : dimensions.values()) if (c == null) throw new IllegalArgumentException("empty dimension entry");
        double last = Double.MAX_VALUE;
        for (var s : coldStages) {
            if (!(s.below < last) || s.speed <= 0) throw new IllegalArgumentException("cold stages must fall in 'below'");
            last = s.below;
        }
        last = -Double.MAX_VALUE;
        for (var s : heatStages) {
            if (!(s.above > last) || s.speed <= 0) throw new IllegalArgumentException("heat stages must rise in 'above'");
            last = s.above;
        }
        Map<Block, Source> map = new IdentityHashMap<>();
        for (var s : sources) {
            if (s == null || s.block == null || !(s.radius > 0) || !("linear".equals(s.falloff) || "square".equals(s.falloff)))
                throw new IllegalArgumentException("bad source entry " + (s == null ? null : s.block));
            s.square = "square".equals(s.falloff);
            var id = new ResourceLocation(s.block);
            if (!ForgeRegistries.BLOCKS.containsKey(id)) {
                ApocalypseFirstLight.LOGGER.warn("[AFL TEMPERATURE] Unknown source block {}, skipped", id);
                continue;
            }
            map.put(ForgeRegistries.BLOCKS.getValue(id), s);
        }
        sourceMap = map;
    }

    @SubscribeEvent public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "temperature") {
            @Override protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                TemperatureConfig loaded = null;
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var config = GSON.fromJson(input.get(id), TemperatureConfig.class);
                        config.validate();
                        loaded = config; // the last file in id order wins
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL TEMPERATURE] Rejected {}: {}", id, ex.getMessage());
                    }
                }
                if (loaded != null) current = loaded;
            }
        });
    }
}
