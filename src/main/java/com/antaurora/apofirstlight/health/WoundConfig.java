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
 * Wound numbers (survival and health V1a, docs/gameplay/survival_health_v1a.md 5), from data/apocalypse_firstlight/wounds/
 * wounds_v1.json (/reload); a file that does not parse or fails the checks is rejected and the previous numbers stay. Times
 * in game days of wound age, which only runs while the player is online. Game balance parameters, not medical figures.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WoundConfig {
    public static final class Scratch {
        /** One roll at this age, unless the wound is cleaned and bandaged (then none). */
        public double checkDays = 1, infectChance = 0.25, cleanedFactor = 0.2, bandagedFactor = 0.5;
        /** The scratch is gone at this age (untreated / cleaned / bandaged / both). */
        public double healDays = 1.5, healDaysCleaned = 1.25, healDaysBandaged = 1.25, healDaysBoth = 0.75;
    }

    /** Lightning burns (Wounds.Kind BURN, DEEP_BURN): their own slots, one infection roll unless bandaged by then. */
    public static final class Burn {
        public int maxBurns = 4;
        public double checkDays = 0.5, infectChance = 0.1, deepInfectChance = 0.15;
        /** Gone after this many days (rolled between the two), deep: a metal burn. */
        public double[] healDays = {1, 3}, deepHealDays = {3, 5};
    }

    /** Local infection and suppuration share the shape: rolls while not cleaned, a recovery once cleaned, the penalties. */
    public static final class Stage {
        public double checkDays = 1;
        /** Infection: to suppuration (bandaged: the smaller one), or back to nothing. */
        public double worsenChance, worsenChanceBandaged, resolveChance;
        /** Suppuration: back to an (untreated) infection. */
        public double easeChance;
        /** Cleaned: the recovery's length (days), shorter when bandaged too; the penalties fade out over it. */
        public double recoverDaysCleaned = 1, recoverDaysBoth = 0.5;
        public double staminaRegen = 1, healthRegen = 1, visual;
    }

    public int dayTicks = 24000;
    /** Chance that an infected's melee hit that took health leaves a scratch. */
    public double scratchChance = 0.35;
    public int maxWounds = 3, maxSeverity = 3;
    /** Each scratch on the same wound past the first: the infection roll × (1 + this). */
    public double severityStep = 0.25;
    public Scratch scratch = new Scratch();
    public Stage infection = new Stage(), suppuration = new Stage();
    public Burn burn = new Burn();

    private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static volatile WoundConfig current = new WoundConfig();

    public static WoundConfig get() {
        return current;
    }

    long ticks(double days) {
        return Math.max(1, Math.round(days * dayTicks));
    }

    private void validate() {
        if (dayTicks < 20 || scratchChance < 0 || scratchChance > 1 || maxWounds < 1 || maxSeverity < 1 || severityStep < 0
                || scratch == null || infection == null || suppuration == null)
            throw new IllegalArgumentException("invalid wound numbers");
        for (Stage s : new Stage[]{infection, suppuration})
            if (!(s.checkDays > 0) || !(s.recoverDaysCleaned > 0) || !(s.recoverDaysBoth > 0) || s.worsenChance + s.resolveChance > 1)
                throw new IllegalArgumentException("invalid wound stage numbers");
        if (!(scratch.checkDays > 0) || !(scratch.healDays > 0) || !(scratch.healDaysBoth > 0))
            throw new IllegalArgumentException("invalid scratch numbers");
        if (burn == null || burn.maxBurns < 1 || !(burn.checkDays > 0) || burn.infectChance < 0 || burn.infectChance > 1
                || burn.deepInfectChance < 0 || burn.deepInfectChance > 1 || !range(burn.healDays) || !range(burn.deepHealDays))
            throw new IllegalArgumentException("invalid burn numbers");
    }

    private static boolean range(double[] r) {
        return r != null && r.length == 2 && r[0] > 0 && r[1] >= r[0];
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "wounds") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                WoundConfig loaded = null;
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var config = GSON.fromJson(input.get(id), WoundConfig.class);
                        config.validate();
                        loaded = config;
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL WOUNDS] Rejected {}: {}", id, ex.getMessage());
                    }
                }
                if (loaded != null) current = loaded;
            }
        });
    }
}
