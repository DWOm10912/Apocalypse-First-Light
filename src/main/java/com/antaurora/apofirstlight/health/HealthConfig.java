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

    /**
     * Fractures (docs/gameplay/fracture_splint_v1_plan.md). Pairs are [not splinted, splinted]; every penalty fades out
     * linearly over the last part of the healing (from fade_from).
     */
    public static final class Fracture {
        /**
         * A fall's health lost d (after scaling) breaks a leg with chance clamp((d - [0]) / [1], 0, [2]); an arm the same
         * way with arm_fall, but only when the player falls over, the arm it lands on (FallLanding).
         */
        public double[] legFall = {10, 40, 0.85}, armFall = {25, 60, 0.5};
        /** Game days to heal splinted; not splinted × unsplinted; resting (asleep, a passenger) × rest. */
        public double legHealDays = 5, armHealDays = 4, unsplinted = 0.25, rest = 1.5;
        public double fadeFrom = 0.7;
        /** Broken again: the healing done goes back by this fraction, and the splint comes off. */
        public double rebreakSetback = 0.5;
        /**
         * A broken leg stops sprinting until it has healed this far, [not splinted, splinted] (splinted 0.3: about 1.5 of
         * the 5 days, user 2026-10-20).
         */
        public double[] sprintFrom = {0.85, 0.3};
        public FallOver fallOver = new FallOver();
        public Leg leg = new Leg();
        /** The arm that holds the gun and the tool, and the other one that steadies the gun. */
        public Arm mainArm = Arm.main(), supportArm = Arm.support();
    }

    /**
     * Falling over on a landing that hurt (docs/gameplay/fall_landing_v1_plan.md 3, user 2026-10-20): chance
     * clamp((d - [0]) / [1], 0, [2]) of the health lost d; × broken_leg with a leg already broken, + load above the light
     * load, + speed at a horizontal speed of speed_from blocks a tick or more, + edge when a foot corner stands on nothing,
     * × sneak while sneaking; capped at 1. A leg broken by this landing always falls over.
     */
    public static final class FallOver {
        public double[] chance = {10, 50, 0.8};
        public double brokenLeg = 1.5, load = 0.15, speedFrom = 0.25, speed = 0.15, edge = 0.15, sneak = 0.5;
    }

    /**
     * Injury knockdown V1 (docs/gameplay/injury_knockdown_v1_plan.md 3, health/Knockdown): a hit from somewhere that does
     * not kill knocks the player down with chance clamp((d - [0]) / [1], 0, [2]) of the health lost d; + load above the
     * light load, × broken_leg with a leg broken, + sprint while sprinting, × sneak while sneaking; capped at 1. The types in
     * always knock down whenever d reaches always_min. The held item drops with drop. Within front_cone degrees of the facing
     * the hit comes from the front (down on the back), within it of the back from behind (onto the front), else the side.
     */
    public static final class Knockdown {
        public double[] chance = {25, 30, 1};
        public double load = 0.15, brokenLeg = 1.5, sprint = 0.15, sneak = 0.5;
        public List<String> always = List.of("minecraft:explosion", "minecraft:player_explosion", "apocalypse_firstlight:falling_tree",
                "apocalypse_firstlight:vehicle");
        public double alwaysMin = 8, drop = 0.3, frontCone = 50;
    }

    /**
     * Collapse (plan 4): a hit that leaves at most at health (and does not kill, nor is one of ignore) drops the player onto
     * the front, crawling × crawl, no jumping or sprinting; it gets up after min_seconds once health is back to up_at, or
     * after max_seconds.
     */
    public static final class Collapse {
        public double at = 15, upAt = 20, minSeconds = 12, maxSeconds = 45, crawl = 0.6;
        public List<String> ignore = List.of("minecraft:starve", "apocalypse_firstlight:dehydration", "apocalypse_firstlight:hypothermia",
                "apocalypse_firstlight:heatstroke", "minecraft:lightning_bolt");
    }

    /**
     * Vehicle impact (plan 5, vehicle/VehicleImpact): a body the truck closes on at min_speed m/s or more (once a cooldown
     * ticks) is hurt by damage [[closing m/s, health lost of 100], ...] (passed / 5: the vanilla scale), thrown along the hit
     * at throw × closing × mass / (mass + its mass) and up at lift × closing (at most lift_max m/s), its mass its box's
     * volume × density kg; a player breaks a leg with chance clamp((d - [0]) / [1], 0, [2]) of leg. The box reaches out
     * inflate + inflate_per_speed × speed blocks.
     */
    public static final class Vehicle {
        public double minSpeed = 1.5, inflate = 0.15, inflatePerSpeed = 0.03;
        public int cooldown = 10;
        public double[][] damage = {{1.5, 0}, {3, 4}, {5.6, 15}, {8.3, 36}, {11.1, 62}, {13.9, 90}, {16.7, 120}};
        public double throwFactor = 1.2, lift = 0.15, liftMax = 3, mass = 1450, density = 108;
        public double[] leg = {10, 50, 0.7};

        /** The health lost (of 100) at this closing speed (m/s): the curve, its last slope past the end. */
        public double damageAt(double speed) {
            if (speed <= damage[0][0]) return damage[0][1];
            for (int i = 1; i < damage.length; i++)
                if (speed <= damage[i][0])
                    return damage[i - 1][1] + (speed - damage[i - 1][0]) / (damage[i][0] - damage[i - 1][0]) * (damage[i][1] - damage[i - 1][1]);
            int n = damage.length;
            return damage[n - 1][1] + (speed - damage[n - 1][0]) * (damage[n - 1][1] - damage[n - 2][1]) / (damage[n - 1][0] - damage[n - 2][0]);
        }
    }

    public static final class Leg {
        public double[] move = {0.6, 0.8}, jump = {0.85, 0.9}, stamina = {1.3, 1.15}, cap = {85, 95};
        /** Jump height never below this × vanilla (1.25 blocks × 0.82: still up one block, never trapped in a pit). */
        public double jumpFloor = 0.82;
        /** Landing from landing_from blocks or more on a broken leg: landing_extra vanilla points more (× 5 by the fall rule). */
        public double landingFrom = 2, landingExtra = 1;
    }

    public static final class Arm {
        public double[] sway, recoilRecovery, dig, melee, climb, cap;

        static Arm main() {
            Arm a = new Arm();
            a.sway = new double[]{1.5, 1.2};
            a.recoilRecovery = new double[]{0.6, 0.8};
            a.dig = new double[]{0.5, 0.8};
            a.melee = new double[]{0.6, 0.85};
            a.climb = new double[]{0.5, 0.8};
            a.cap = new double[]{90, 95};
            return a;
        }

        static Arm support() {
            Arm a = new Arm();
            a.sway = new double[]{2.0, 1.4};
            a.recoilRecovery = new double[]{0.8, 0.9};
            a.dig = new double[]{0.8, 0.9};
            a.melee = new double[]{0.85, 0.95};
            a.climb = new double[]{0.5, 0.8};
            a.cap = new double[]{90, 95};
            return a;
        }
    }

    public double maxHealth = 100;
    /** An old save's health (20 HP world) × this when the player is first seen at 100 HP. */
    public double legacyScale = 5;
    public Damage damage = new Damage();
    public Regen regen = new Regen();
    public Fracture fracture = new Fracture();
    public Knockdown knockdown = new Knockdown();
    public Collapse collapse = new Collapse();
    public Vehicle vehicle = new Vehicle();

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
        if (fracture == null) fracture = new Fracture();
        var f = fracture;
        if (f.leg == null || f.mainArm == null || f.supportArm == null || !pair(f.legFall, 3) || !pair(f.armFall, 3)
                || !pair(f.sprintFrom, 2) || !(f.sprintFrom[0] >= 0 && f.sprintFrom[0] <= 1) || !(f.sprintFrom[1] >= 0 && f.sprintFrom[1] <= 1)
                || !(f.legFall[1] > 0) || !(f.armFall[1] > 0) || !(f.legHealDays > 0) || !(f.armHealDays > 0) || !(f.unsplinted > 0)
                || !(f.rest > 0) || !(f.fadeFrom >= 0 && f.fadeFrom < 1) || f.rebreakSetback < 0 || f.rebreakSetback > 1)
            throw new IllegalArgumentException("invalid fracture numbers");
        if (f.fallOver == null) f.fallOver = new FallOver();
        var o = f.fallOver;
        if (!pair(o.chance, 3) || !(o.chance[1] > 0) || !(o.brokenLeg >= 0) || !(o.load >= 0) || !(o.speedFrom >= 0) || !(o.speed >= 0)
                || !(o.edge >= 0) || !(o.sneak >= 0))
            throw new IllegalArgumentException("invalid fall_over numbers");
        for (double[] p : new double[][]{f.leg.move, f.leg.jump, f.leg.stamina, f.leg.cap}) if (!pair(p, 2)) throw new IllegalArgumentException("invalid fracture leg numbers");
        for (Arm a : new Arm[]{f.mainArm, f.supportArm})
            for (double[] p : new double[][]{a.sway, a.recoilRecovery, a.dig, a.melee, a.climb, a.cap})
                if (!pair(p, 2)) throw new IllegalArgumentException("invalid fracture arm numbers");
        if (knockdown == null) knockdown = new Knockdown();
        var k = knockdown;
        if (k.always == null) k.always = List.of();
        if (!pair(k.chance, 3) || !(k.chance[1] > 0) || !(k.load >= 0) || !(k.brokenLeg >= 0) || !(k.sprint >= 0) || !(k.sneak >= 0)
                || !(k.alwaysMin >= 0) || !(k.drop >= 0 && k.drop <= 1) || !(k.frontCone > 0 && k.frontCone < 90))
            throw new IllegalArgumentException("invalid knockdown numbers");
        if (collapse == null) collapse = new Collapse();
        var c = collapse;
        if (c.ignore == null) c.ignore = List.of();
        if (!(c.at >= 0) || !(c.upAt >= c.at) || !(c.minSeconds >= 0) || !(c.maxSeconds >= c.minSeconds) || !(c.crawl > 0 && c.crawl <= 1))
            throw new IllegalArgumentException("invalid collapse numbers");
        if (vehicle == null) vehicle = new Vehicle();
        var v = vehicle;
        if (!(v.minSpeed > 0) || !(v.inflate >= 0) || !(v.inflatePerSpeed >= 0) || v.cooldown < 1 || v.damage == null || v.damage.length < 2
                || !(v.throwFactor >= 0) || !(v.lift >= 0) || !(v.liftMax >= 0) || !(v.mass > 0) || !(v.density > 0) || !pair(v.leg, 3) || !(v.leg[1] > 0))
            throw new IllegalArgumentException("invalid vehicle numbers");
        for (int i = 0; i < v.damage.length; i++)
            if (!pair(v.damage[i], 2) || (i > 0 && !(v.damage[i][0] > v.damage[i - 1][0])))
                throw new IllegalArgumentException("vehicle damage points are ascending [speed, health]");
    }

    private static boolean pair(double[] values, int n) {
        if (values == null || values.length != n) return false;
        for (double v : values) if (!(v >= 0) || Double.isInfinite(v)) return false;
        return true;
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
