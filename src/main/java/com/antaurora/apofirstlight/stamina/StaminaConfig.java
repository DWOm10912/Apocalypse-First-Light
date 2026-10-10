package com.antaurora.apofirstlight.stamina;

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
import java.util.*;

/**
 * Stamina V1 numbers (docs/项目内容/01 - 设计/生存/耐力.md), from data/apocalypse_firstlight/stamina/stamina_v1.json
 * (/reload). Fields default to the same values, so a missing key keeps its default; a file that does not parse or
 * fails the checks is rejected and the previous numbers stay. Rates are per second, amounts per action.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class StaminaConfig {
    public static final class Costs {
        public double walk = 1.5, sneak = 1.0, sprint = 8, jump = 5, sprintJump = 7, swim = 7, water = 2.5, climb = 3, mining = 2;
        /** Rolling on an office chair, per second; the load's movement multiplier applies at this share of its excess over 1. */
        public double officeChair = 0.5, officeChairLoad = 2.0 / 3.0;
        public double meleeEmptyHand = 3, melee = 5, shot = 0.6, reload = 1;
        /** One pull of a recoil starter (the portable diesel generator, 2026-10-09). */
        public double recoilPull = 6;
        /** item id → cost per swing */
        public Map<String, Double> meleeItems = Map.of("apocalypse_firstlight:crowbar", 8.0);
        /** ammo item id → cost per shot */
        public Map<String, Double> shotsByAmmo = Map.of();
        /** native gun definition id → cost per reload */
        public Map<String, Double> reloadsByGun = Map.of();
    }
    /** At this load ratio (load / comfort), movement costs × move, other actions × action, regeneration × regen. */
    public static final class WeightPoint { public double loadRatio, move = 1, action = 1, regen = 1; }
    public static final class Winded { public double meleeDamage = 0.6, digSpeed = 0.8; }
    /** Fatigue = 0 at start stamina and above, 1 at 0; sway × (1 + swayExtra × fatigue), spread × (1 + spreadExtra × fatigue). */
    public static final class Fatigue { public double start = 50, swayExtra = 1.5, spreadExtra = 0.4; }
    public static final class Breath { public double lightBelow = 20; }

    public double max = 100, regen = 15, regenInWater = 0.5, regenLowFood = 0.5;
    public int lowFoodLevel = 6;
    public double delaySeconds = 1.0, exhaustedDelaySeconds = 2.0, windedResume = 30;
    public Costs costs = new Costs();
    public List<WeightPoint> weight = List.of();
    public Winded winded = new Winded();
    public Fatigue fatigue = new Fatigue();
    public Breath breath = new Breath();

    private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static volatile StaminaConfig current = new StaminaConfig();
    public static StaminaConfig get() { return current; }

    /** Weight multipliers at a load ratio: linear between points, the end points' values outside. */
    public double[] weightMultipliers(double ratio) {
        if (weight.isEmpty()) return new double[]{1, 1, 1};
        WeightPoint previous = null;
        for (var point : weight) {
            if (ratio <= point.loadRatio) {
                if (previous == null) return new double[]{point.move, point.action, point.regen};
                double t = (ratio - previous.loadRatio) / (point.loadRatio - previous.loadRatio);
                return new double[]{lerp(previous.move, point.move, t), lerp(previous.action, point.action, t), lerp(previous.regen, point.regen, t)};
            }
            previous = point;
        }
        return new double[]{previous.move, previous.action, previous.regen};
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private void validate() {
        if (!(max > 0) || regen < 0 || delaySeconds < 0 || exhaustedDelaySeconds < 0 || windedResume < 0 || windedResume > max
                || costs == null || winded == null || fatigue == null || breath == null || weight == null)
            throw new IllegalArgumentException("invalid stamina numbers");
        double last = -Double.MAX_VALUE;
        for (var point : weight) {
            if (point.loadRatio <= last || point.move <= 0 || point.action <= 0 || point.regen < 0)
                throw new IllegalArgumentException("weight points must rise in load_ratio with positive multipliers");
            last = point.loadRatio;
        }
        if (costs.meleeItems == null) costs.meleeItems = Map.of();
        if (costs.shotsByAmmo == null) costs.shotsByAmmo = Map.of();
        if (costs.reloadsByGun == null) costs.reloadsByGun = Map.of();
    }

    @SubscribeEvent public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "stamina") {
            @Override protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                StaminaConfig loaded = null;
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var config = GSON.fromJson(input.get(id), StaminaConfig.class);
                        config.validate();
                        loaded = config; // the last file in id order wins
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL STAMINA] Rejected {}: {}", id, ex.getMessage());
                    }
                }
                if (loaded != null) current = loaded;
            }
        });
    }
}
