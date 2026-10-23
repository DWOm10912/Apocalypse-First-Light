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

import java.util.Map;
import java.util.TreeSet;

/**
 * Lightning injury V1 numbers (docs/gameplay/lightning_injury_v1_plan.md 3), {@code data/apocalypse_firstlight/lightning/
 * lightning_injury_v1.json}; the defaults here are the same. Balance numbers, not medical statistics. Times in seconds unless
 * named days (WoundConfig#dayTicks). The client reads its numbers from the same object (the defaults, or the integrated
 * server's).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class LightningInjuryConfig {
    private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static LightningInjuryConfig current = new LightningInjuryConfig();

    /** What one way of being struck does: chances 0..1, [least, most] ranges. */
    public static final class Hit {
        public double death;
        /** Health lost by a survivor (AFL HP); for ground current [at its inner edge, at its outer edge]. */
        public double[] health;
        public double unconsciousChance;
        public double[] unconscious = {0, 0};
        public double paralysisChance;
        public double[] paralysis = {0, 0};
        public double eardrum;
        /** Burning (s), 0 none (V1: none; people struck do not go up in flames, their spot's fire is put out, LightningInjury#CLEAR_FIRE_TICKS). */
        public double fire;
        /** The ears pushed up to severe tinnitus (TinnitusConfig#stages[2]). */
        public boolean severeTinnitus;
        /** Wet (in water, rain or a bubble column; plan 10.3 A): the death chance instead of death, -1 the same. */
        public double wetDeath = -1;
        /** Lightning burns (plan 10.3 B): this many where the current went in and out (ground current: within ground_burn_range). */
        public int burns;
        /**
         * Gear: metal gear on the body burns deeper there and costs metal_health more (C), the held item is thrown
         * (drop_range, D), each battery or charged tool carried is drained at drain_chance (E).
         */
        public boolean gear;
        /** Lichtenberg figure (skin_wound_marks_v1_plan.md 4.3): a fern-like red mark from where the current came in, lichtenberg_days long. */
        public boolean lichtenberg;

        Hit() {
        }

        Hit(double death, double[] health, double unconsciousChance, double[] unconscious, double paralysisChance, double[] paralysis,
            double eardrum, double fire, boolean severeTinnitus) {
            this.death = death;
            this.health = health;
            this.unconsciousChance = unconsciousChance;
            this.unconscious = unconscious;
            this.paralysisChance = paralysisChance;
            this.paralysis = paralysis;
            this.eardrum = eardrum;
            this.fire = fire;
            this.severeTinnitus = severeTinnitus;
        }

        Hit real(double wetDeath, int burns, boolean gear) {
            this.wetDeath = wetDeath;
            this.burns = burns;
            this.gear = gear;
            this.lichtenberg = gear;
            return this;
        }

        void validate(String name) {
            if (!chance(death) || !chance(unconsciousChance) || !chance(paralysisChance) || !chance(eardrum) || !(fire >= 0)
                    || !range(health, 0) || !range(unconscious, 0) || !range(paralysis, 0) || !(wetDeath == -1 || chance(wetDeath))
                    || burns < 0 || burns > 4)
                throw new IllegalArgumentException(name + ": chances 0..1 (wet_death -1: death), ranges [least, most] >= 0, burns 0..4");
        }
    }

    /**
     * Lightning V2 (docs/gameplay/lightning_v2_plan.md): where strikes land and what they do to trees. Rolling sphere
     * (world/StrikePoint): distance [least, likeliest, most] m drawn per strike; a lightning rod's tip counts rod_reach m
     * higher. A struck tree falls with fell, its crown catches fire with fire_wet where it rains, fire_dry in a dry storm,
     * burning fire_seconds [least, most] at most (rain puts it out sooner). enabled false: vanilla's target.
     */
    public static final class Strike {
        public boolean enabled = true;
        public double[] distance = {20, 45, 80};
        public double rodReach = 6;
        public double fell = 0.15, fireWet = 0.05, fireDry = 0.3;
        public double[] fireSeconds = {60, 150};

        void validate() {
            if (distance == null || distance.length != 3 || !(distance[0] >= 5) || !(distance[1] >= distance[0]) || !(distance[2] >= distance[1])
                    || distance[2] > 200 || !(rodReach >= 0) || rodReach > 30 || !chance(fell) || !chance(fireWet) || !chance(fireDry)
                    || !range(fireSeconds, 1))
                throw new IllegalArgumentException("strike: distance [least <= likeliest <= most] 5..200, rod_reach 0..30, chances 0..1, fire_seconds [least, most] >= 1");
        }
    }

    public Strike strike = new Strike();

    public Hit direct = new Hit(0.5, new double[]{40, 60}, 1, new double[]{20, 40}, 1, new double[]{120, 240}, 0.7, 0, true).real(0.35, 2, true);
    public Hit side = new Hit(0.05, new double[]{20, 35}, 0.5, new double[]{10, 25}, 0.6, new double[]{60, 120}, 0.3, 0, true).real(0.03, 1, true);
    /** Ground current: health [inner edge, outer edge], no death, no fainting, no eardrum. */
    public Hit ground = new Hit(0, new double[]{15, 5}, 0, new double[]{0, 0}, 0.2, new double[]{20, 40}, 0, 0, false).real(-1, 1, false);

    /** Wet: this many lightning burns more (the current running over the wet skin), when there are any. */
    public int wetBurns = 1;
    /** Ground current leaves its burn (a foot or leg) only within this many blocks across. */
    public double groundBurnRange = 4;
    /** Each metal burn costs this much health more (AFL HP). */
    public double metalHealth = 5;
    /** The held item lands this many blocks away, [least, most]. */
    public double[] dropRange = {1, 2};
    /** Each battery or charged tool carried is drained with this chance (no lasting damage). */
    public double drainChance = 0.5;
    /** The Lichtenberg figure lasts this many days (online, asleep × sleep), fading all the way. */
    public double lichtenbergDays = 1;

    /** Side flash: within side_range blocks across of the strike, the feet at most side_height above its top (the top may be a tree's height above them). */
    public double sideRange = 2, sideHeight = 4;
    /** Ground current: side_range .. ground_range blocks across (water_range when the strike or the player is in water), on the ground or in water, the feet at most ground_height above the strike's top. */
    public double groundRange = 8, waterRange = 12, groundHeight = 3;
    /** Ground current within this (blocks across) also leaves the aftermath. */
    public double groundAftermath = 4;
    /** Direct: the struck one, or with no aim known (a command, a trident) the nearest within this many blocks across. */
    public double directRange = 1;

    /** Fainted: a hurt of at least this (HP) wakes. */
    public double wakeDamage = 5;
    /** Paralysed legs: crawling × this (on vanilla's crawl). Then weak legs for [least, most] s: moving × weak_move, no sprint or jump, steps cost × weak_stamina. */
    public double crawl = 0.6;
    public double[] weakLegs = {180, 300};
    public double weakMove = 0.6, weakStamina = 1.5;

    /** A burst eardrum: days long (online), the world × eardrum_hearing, back to whole over the last eardrum_heal share. */
    public double eardrumDays = 2, eardrumHearing = 0.7, eardrumHeal = 0.5;
    /** The aftermath: days long (online), fading over its last aftermath_fade share. */
    public double aftermathDays = 1, aftermathFade = 1 / 3.0;
    public double aftermathStaminaRegen = 0.6, aftermathHealthRegen = 0.5, aftermathSway = 1.4;
    /** Asleep, the eardrum and the aftermath heal this many times faster. */
    public double sleep = 2;

    /** Other living things: side flash and ground current take this share of their maximum health; animals' ground current reaches × animal_ground. */
    public double mobSide = 0.6, mobGround = 0.2, animalGround = 1.5;

    // ---- the client ----
    /** After-images of strikes within this many blocks in view, [nearest, farthest] seconds. */
    public double afterimageRange = 48;
    public double[] afterimageSeconds = {6, 2};
    /** Waking up: the view blurred for this long (s). */
    public double wakeBlur = 15;
    /** The aftermath's headache: a blur spell every [least, most] s. */
    public double[] headacheGap = {60, 150};

    public static LightningInjuryConfig get() {
        return current;
    }

    private static boolean chance(double v) {
        return v >= 0 && v <= 1;
    }

    private static boolean range(double[] r, double min) {
        return r != null && r.length == 2 && r[0] >= min && r[1] >= min;
    }

    void validate() {
        if (direct == null || side == null || ground == null) throw new IllegalArgumentException("direct, side and ground");
        direct.validate("direct");
        side.validate("side");
        ground.validate("ground");
        if (strike == null) throw new IllegalArgumentException("strike");
        strike.validate();
        if (!(sideRange > 0) || !(sideHeight > 0) || !(groundRange >= sideRange) || !(waterRange >= groundRange)
                || !(groundHeight > 0) || !(groundAftermath >= 0) || !(directRange > 0) || !(wakeDamage > 0) || !chance(crawl) || !range(weakLegs, 0)
                || !chance(weakMove) || !(weakStamina >= 1) || !(eardrumDays > 0) || !chance(eardrumHearing) || !chance(eardrumHeal)
                || !(aftermathDays > 0) || !chance(aftermathFade) || !(aftermathStaminaRegen >= 0) || !(aftermathHealthRegen >= 0)
                || !(aftermathSway >= 1) || !(sleep > 0) || !chance(mobSide) || !chance(mobGround) || !(animalGround >= 1)
                || !(afterimageRange >= 0) || !range(afterimageSeconds, 0) || !(wakeBlur >= 0) || !range(headacheGap, 1)
                || wetBurns < 0 || wetBurns > 2 || !(groundBurnRange >= 0) || !(metalHealth >= 0) || !range(dropRange, 0) || !chance(drainChance) || !(lichtenbergDays > 0))
            throw new IllegalArgumentException("invalid lightning injury numbers");
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "lightning") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                LightningInjuryConfig loaded = null;
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var config = GSON.fromJson(input.get(id), LightningInjuryConfig.class);
                        config.validate();
                        loaded = config;
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL LIGHTNING] Rejected {}: {}", id, ex.getMessage());
                    }
                }
                if (loaded != null) current = loaded;
            }
        });
    }
}
