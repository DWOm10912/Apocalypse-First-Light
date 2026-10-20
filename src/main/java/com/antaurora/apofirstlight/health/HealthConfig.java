package com.antaurora.apofirstlight.health;

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

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Survival and health V1a numbers (docs/gameplay/survival_health_v1a.md), from data/apocalypse_firstlight/health/
 * health_v1.json (/reload). Fields default to the same values; a file that does not parse or fails the checks is rejected
 * and the previous numbers stay. All of them are game balance parameters, not medical figures.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class HealthConfig {
    /** One damage rule: a damage type (and optionally only infected attackers) → health lost, by a factor or a curve. */
    public static final class Rule {
        public String type;
        /** Only when the attacker is an infected (InfectedEntityRules#isInfected). */
        public boolean infectedAttacker;
        /** Health lost = the vanilla-scale amount × this, unless a curve is given. */
        public double scale = Double.NaN;
        /** Piecewise linear [[vanilla amount, health lost], ...], ascending; past the last point it goes on with the last slope. */
        public double[][] curve;

        double apply(double amount) {
            if (curve == null || curve.length == 0) return amount * scale;
            if (amount <= curve[0][0]) return curve[0][1] * (curve[0][0] <= 0 ? 1 : amount / curve[0][0]);
            for (int i = 1; i < curve.length; i++) {
                if (amount <= curve[i][0]) {
                    double t = (amount - curve[i - 1][0]) / (curve[i][0] - curve[i - 1][0]);
                    return curve[i - 1][1] + t * (curve[i][1] - curve[i - 1][1]);
                }
            }
            int n = curve.length;
            if (n < 2) return amount * curve[0][1] / Math.max(1e-6, curve[0][0]);
            double slope = (curve[n - 1][1] - curve[n - 2][1]) / (curve[n - 1][0] - curve[n - 2][0]);
            return curve[n - 1][1] + (amount - curve[n - 1][0]) * slope;
        }
    }

    public static final class Damage {
        public double defaultScale = 5;
        public List<Rule> rules = List.of();
        /** Damage types that never take a player below 1 HP (vanilla Normal starvation stops at 1). */
        public List<String> neverBelowOne = List.of("minecraft:starve");
    }

    public static final class Regen {
        /** No natural regeneration for this long after the last damage (each new damage starts it again). */
        public double delaySeconds = 15;
        public double awakePerMinute = 3, restPerMinute = 8;
        /** Below this food level (7: above vanilla's no-sprint line of 6) there is no natural regeneration. */
        public int minFood = 7;
        /** Skipping the night: health per skipped real minute of game time, at most sleep_max per skip. */
        public double sleepPerMinute = 8, sleepMax = 30;
    }

    public double maxHealth = 100;
    /** An old save's health (20 HP world) × this when the player is first seen at 100 HP. */
    public double legacyScale = 5;
    public Damage damage = new Damage();
    public Regen regen = new Regen();

    private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static volatile HealthConfig current = new HealthConfig();

    public static HealthConfig get() {
        return current;
    }

    private void validate() {
        if (!(maxHealth >= 1) || !(legacyScale > 0) || damage == null || regen == null)
            throw new IllegalArgumentException("invalid health numbers");
        if (!(damage.defaultScale > 0)) throw new IllegalArgumentException("damage.default_scale must be > 0");
        if (damage.rules == null) damage.rules = List.of();
        if (damage.neverBelowOne == null) damage.neverBelowOne = List.of();
        for (Rule r : damage.rules) {
            if (r == null || r.type == null) throw new IllegalArgumentException("a damage rule without a type");
            if (r.curve == null && !(r.scale > 0)) throw new IllegalArgumentException("rule " + r.type + ": no scale or curve");
            if (r.curve != null) for (int i = 0; i < r.curve.length; i++) {
                if (r.curve[i].length != 2) throw new IllegalArgumentException("rule " + r.type + ": curve points are [amount, health]");
                if (i > 0 && !(r.curve[i][0] > r.curve[i - 1][0])) throw new IllegalArgumentException("rule " + r.type + ": curve not ascending");
            }
        }
        if (regen.delaySeconds < 0 || regen.awakePerMinute < 0 || regen.restPerMinute < 0 || regen.sleepPerMinute < 0 || regen.sleepMax < 0)
            throw new IllegalArgumentException("invalid regen numbers");
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "health") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                HealthConfig loaded = null;
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var config = GSON.fromJson(input.get(id), HealthConfig.class);
                        config.validate();
                        loaded = config;
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL HEALTH] Rejected {}: {}", id, ex.getMessage());
                    }
                }
                if (loaded != null) current = loaded;
            }
        });
    }
}
