package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.infected.InfectedEntityRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Wounds V1a (docs/gameplay/survival_health_v1a.md 5): the small stand-in for the later body state's injury list
 * (docs/gameplay/survival_health_v2_design.md 6). Up to {@code max_wounds} wounds, each a scratch that may turn into a local
 * infection and then suppuration; medical alcohol cleans one, a bandage covers one. No part of the body yet, no bleeding,
 * no health lost over time: an infection lowers stamina regeneration, suppuration also natural health regeneration, and
 * both show on the screen (health/WoundScreenEffect: blur spells, a fever's haze and chills). Only the worst wound counts, so several never stack.
 * <p>
 * Time is each wound's own age in ticks, which only runs while its player is online (ticked once a second by PlayerHealth),
 * so a player who logs back in after a week finds the wounds as they left them, not several stages on.
 * <p>
 * Saved in the player's persistent data ({@code afl_wounds}: {@code v}, {@code list}); a death clears them, returning from the
 * End keeps them. Server side; the client sees {@link View}s (HealthPackets).
 */
public final class Wounds {
    public enum Stage { SCRATCH, INFECTION, SUPPURATION }
    public enum Treatment { ALCOHOL, BANDAGE }
    /** What became of a wound in one step. */
    enum Change { NONE, WORSE, EASED, HEALED }

    /** What the client knows of a wound: its stage, whether it is cleaned / bandaged, its recovery (0..1, NaN: none). */
    public record View(Stage stage, boolean cleaned, boolean bandaged, float recovery) {}

    static final String KEY = "afl_wounds";
    private static final int VERSION = 1;

    static final class Wound {
        Stage stage = Stage.SCRATCH;
        int severity = 1;
        boolean cleaned, bandaged, rolled;
        long age, cleanedAt = -1, nextCheck;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("stage", stage.name().toLowerCase(Locale.ROOT));
            t.putInt("severity", severity);
            t.putBoolean("cleaned", cleaned);
            t.putBoolean("bandaged", bandaged);
            t.putBoolean("rolled", rolled);
            t.putLong("age", age);
            t.putLong("cleaned_at", cleanedAt);
            t.putLong("next_check", nextCheck);
            return t;
        }

        static Wound load(CompoundTag t) {
            Wound w = new Wound();
            try {
                w.stage = Stage.valueOf(t.getString("stage").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                w.stage = Stage.SCRATCH;
            }
            w.severity = Math.max(1, t.getInt("severity"));
            w.cleaned = t.getBoolean("cleaned");
            w.bandaged = t.getBoolean("bandaged");
            w.rolled = t.getBoolean("rolled");
            w.age = Math.max(0, t.getLong("age"));
            w.cleanedAt = t.contains("cleaned_at") ? t.getLong("cleaned_at") : -1;
            w.nextCheck = t.getLong("next_check");
            return w;
        }
    }

    static final class State {
        final List<Wound> wounds = new ArrayList<>();
        boolean dirty;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private Wounds() {
    }

    static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> load(player));
    }

    private static State load(ServerPlayer player) {
        State s = new State();
        CompoundTag root = player.getPersistentData().getCompound(KEY);
        for (Tag t : root.getList("list", Tag.TAG_COMPOUND)) s.wounds.add(Wound.load((CompoundTag) t));
        return s;
    }

    static void save(ServerPlayer player, State s) {
        if (s.wounds.isEmpty()) {
            player.getPersistentData().remove(KEY);
            return;
        }
        CompoundTag root = new CompoundTag();
        root.putInt("v", VERSION);
        ListTag list = new ListTag();
        for (Wound w : s.wounds) list.add(w.save());
        root.put("list", list);
        player.getPersistentData().put(KEY, root);
    }

    /** Logout: forget the in-memory copy (the persistent data has it). */
    static void forget(ServerPlayer player) {
        State s = STATES.remove(player.getUUID());
        if (s != null) save(player, s);
    }

    /** Death: no wounds on the next life. */
    static void clear(ServerPlayer player) {
        STATES.remove(player.getUUID());
        player.getPersistentData().remove(KEY);
    }

    // ---- sources ----

    /**
     * After a player's damage was scaled (PlayerHealth): an infected's melee hit that took health may leave a scratch. One
     * call per damage event, so one hit rolls once.
     */
    static void onDamage(ServerPlayer player, DamageSource source, float healthLost) {
        if (!(healthLost > 0) || !source.is(DamageTypes.MOB_ATTACK)) return;
        if (!(source.getEntity() instanceof LivingEntity attacker) || !InfectedEntityRules.isInfected(attacker)) return;
        if (player.getRandom().nextDouble() < WoundConfig.get().scratchChance) scratch(player);
    }

    /**
     * A new scratch (the API later infected kinds and other sources call). A free slot gets a new wound; with all slots taken,
     * the youngest scratch is scratched again: one more severity (to the cap), its cleaning and bandage gone, its clock back
     * to 0. With no scratch left to refresh (all infected) nothing changes. Returns whether anything changed.
     */
    public static boolean scratch(ServerPlayer player) {
        WoundConfig c = WoundConfig.get();
        State s = state(player);
        if (s.wounds.size() < c.maxWounds) {
            Wound w = new Wound();
            s.wounds.add(w);
        } else {
            Wound youngest = null;
            for (Wound w : s.wounds) if (w.stage == Stage.SCRATCH && (youngest == null || w.age < youngest.age)) youngest = w;
            if (youngest == null) return false;
            youngest.severity = Math.min(c.maxSeverity, youngest.severity + 1);
            youngest.cleaned = youngest.bandaged = youngest.rolled = false;
            youngest.age = 0;
        }
        s.dirty = true;
        message(player, "scratched");
        return true;
    }

    /** Dev (/afl health wound): adds a wound already at this stage (in a free slot, else replacing the oldest). */
    static void set(ServerPlayer player, Stage stage) {
        WoundConfig c = WoundConfig.get();
        State s = state(player);
        if (s.wounds.size() >= c.maxWounds) s.wounds.remove(0);
        Wound w = new Wound();
        to(w, stage, c);
        s.wounds.add(w);
        s.dirty = true;
    }

    /** Dev: every wound this many days older, stepped a second at a time (each check rolls as it would online). */
    static void advance(ServerPlayer player, double days) {
        WoundConfig c = WoundConfig.get();
        long steps = Math.max(1, Math.round(days * c.dayTicks / 20.0));
        for (long i = 0; i < steps && !state(player).wounds.isEmpty(); i++) step(player, 20);
    }

    static void clearDev(ServerPlayer player) {
        State s = state(player);
        s.wounds.clear();
        s.dirty = true;
    }

    // ---- time ----

    /** One step of {@code ticks} for every wound (PlayerHealth, once a second). */
    static void step(ServerPlayer player, int ticks) {
        WoundConfig c = WoundConfig.get();
        State s = state(player);
        if (s.wounds.isEmpty()) return;
        RandomSource random = player.getRandom();
        Stage worsened = null;
        boolean healed = false;
        for (var it = s.wounds.iterator(); it.hasNext(); ) {
            Wound w = it.next();
            w.age += ticks;
            Change change = process(w, c, random);
            if (change == Change.NONE) continue;
            s.dirty = true;
            if (change == Change.HEALED) { it.remove(); healed = true; }
            if (change == Change.WORSE && (worsened == null || w.stage.ordinal() > worsened.ordinal())) worsened = w.stage;
        }
        if (worsened != null) message(player, worsened == Stage.SUPPURATION ? "suppuration" : "infection");
        else if (healed && s.wounds.isEmpty()) message(player, "healed");
    }

    static Change process(Wound w, WoundConfig c, RandomSource random) {
        switch (w.stage) {
            case SCRATCH -> {
                boolean both = w.cleaned && w.bandaged;
                if (!both && !w.rolled && w.age >= c.ticks(c.scratch.checkDays)) {
                    w.rolled = true;
                    double p = c.scratch.infectChance * (1 + c.severityStep * (w.severity - 1));
                    if (w.cleaned) p *= c.scratch.cleanedFactor;
                    if (w.bandaged) p *= c.scratch.bandagedFactor;
                    if (random.nextDouble() < p) {
                        w.cleaned = w.bandaged = false;   // a fresh infection under an old dressing: clean and cover again
                        to(w, Stage.INFECTION, c);
                        return Change.WORSE;
                    }
                }
                double days = both ? c.scratch.healDaysBoth : w.cleaned ? c.scratch.healDaysCleaned
                        : w.bandaged ? c.scratch.healDaysBandaged : c.scratch.healDays;
                return w.age >= c.ticks(days) ? Change.HEALED : Change.NONE;
            }
            case INFECTION, SUPPURATION -> {
                WoundConfig.Stage st = stage(w.stage, c);
                if (w.cleaned) {
                    if (w.age - Math.max(0, w.cleanedAt) < recoveryTicks(w, c)) return Change.NONE;
                    if (w.stage == Stage.INFECTION) return Change.HEALED;
                    to(w, Stage.INFECTION, c);   // suppuration cleared: an infection, still cleaned (and bandaged), recovering
                    return Change.EASED;
                }
                if (w.age < w.nextCheck) return Change.NONE;
                w.nextCheck += c.ticks(st.checkDays);
                double r = random.nextDouble();
                if (w.stage == Stage.INFECTION) {
                    double worsen = w.bandaged ? st.worsenChanceBandaged : st.worsenChance;
                    if (r < worsen) {
                        w.bandaged = false;   // the dressing is soiled
                        to(w, Stage.SUPPURATION, c);
                        return Change.WORSE;
                    }
                    return r < worsen + st.resolveChance ? Change.HEALED : Change.NONE;
                }
                if (r < st.easeChance) {
                    to(w, Stage.INFECTION, c);
                    return Change.EASED;
                }
                return Change.NONE;
            }
        }
        return Change.NONE;
    }

    private static void to(Wound w, Stage stage, WoundConfig c) {
        w.stage = stage;
        w.age = 0;
        w.rolled = false;
        w.cleanedAt = w.cleaned ? 0 : -1;
        w.nextCheck = stage == Stage.SCRATCH ? 0 : c.ticks(stage(stage, c).checkDays);
    }

    private static WoundConfig.Stage stage(Stage stage, WoundConfig c) {
        return stage == Stage.SUPPURATION ? c.suppuration : c.infection;
    }

    private static long recoveryTicks(Wound w, WoundConfig c) {
        WoundConfig.Stage st = stage(w.stage, c);
        return c.ticks(w.bandaged ? st.recoverDaysBoth : st.recoverDaysCleaned);
    }

    /** A cleaned infection's / suppuration's recovery (0..1), NaN for anything else. */
    private static double recovery(Wound w, WoundConfig c) {
        if (w.stage == Stage.SCRATCH || !w.cleaned) return Double.NaN;
        return Math.min(1, (w.age - Math.max(0, w.cleanedAt)) / (double) recoveryTicks(w, c));
    }

    // ---- what the wounds do (the worst one counts) ----

    /** {stamina regeneration, natural health regeneration, screen effect} of one wound, faded over a recovery. */
    private static double[] penalty(Wound w, WoundConfig c) {
        if (w.stage == Stage.SCRATCH) return new double[]{1, 1, 0};
        WoundConfig.Stage st = stage(w.stage, c);
        double[] from = {st.staminaRegen, st.healthRegen, st.visual};
        double t = recovery(w, c);
        if (Double.isNaN(t)) return from;
        double[] to = w.stage == Stage.SUPPURATION
                ? new double[]{c.infection.staminaRegen, c.infection.healthRegen, c.infection.visual} : new double[]{1, 1, 0};
        return new double[]{from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t, from[2] + (to[2] - from[2]) * t};
    }

    private static double[] worst(ServerPlayer player) {
        WoundConfig c = WoundConfig.get();
        double[] out = {1, 1, 0};
        for (Wound w : state(player).wounds) {
            double[] p = penalty(w, c);
            out[0] = Math.min(out[0], p[0]);
            out[1] = Math.min(out[1], p[1]);
            out[2] = Math.max(out[2], p[2]);
        }
        return out;
    }

    /** PlayerStamina: stamina regeneration × this. */
    public static double staminaRegenMultiplier(ServerPlayer player) {
        return worst(player)[0];
    }

    /** PlayerHealth: natural health regeneration × this. */
    public static double healthRegenMultiplier(ServerPlayer player) {
        return worst(player)[1];
    }

    /** The screen effect's strength (0..1) before the client's own setting. */
    static double visual(ServerPlayer player) {
        return worst(player)[2];
    }

    static List<View> views(ServerPlayer player) {
        WoundConfig c = WoundConfig.get();
        List<View> out = new ArrayList<>();
        for (Wound w : state(player).wounds) out.add(new View(w.stage, w.cleaned, w.bandaged, (float) recovery(w, c)));
        return out;
    }

    // ---- treatment (shared with the client's item check through View) ----

    /**
     * Which wound a treatment goes to, or -1: alcohol to the worst wound not yet cleaned; a bandage to the worst wound not yet
     * covered, a cleaned one first, never an uncleaned suppuration (a bandage alone does not help it; {@link #needsCleaning}).
     * The same rule on the server's wounds and on the client's views, so the client starts using the item only when the
     * server will accept it.
     */
    public static int target(List<View> wounds, Treatment treatment) {
        int best = -1;
        for (int i = 0; i < wounds.size(); i++) {
            View w = wounds.get(i);
            if (treatment == Treatment.ALCOHOL ? w.cleaned() : w.bandaged()) continue;
            if (treatment == Treatment.BANDAGE && w.stage() == Stage.SUPPURATION && !w.cleaned()) continue;
            if (best < 0) { best = i; continue; }
            View b = wounds.get(best);
            int byStage = Integer.compare(w.stage().ordinal(), b.stage().ordinal());
            if (byStage > 0 || byStage == 0 && treatment == Treatment.BANDAGE && w.cleaned() && !b.cleaned()) best = i;
        }
        return best;
    }

    /** A bandage found nothing to cover but an uncleaned suppuration: the hint says to clean it first. */
    public static boolean needsCleaning(List<View> wounds) {
        for (View w : wounds) if (w.stage() == Stage.SUPPURATION && !w.cleaned()) return true;
        return false;
    }

    /** Applies one treatment on the server; false (nothing used up) when no wound takes it. */
    static boolean treat(ServerPlayer player, Treatment treatment) {
        State s = state(player);
        int i = target(views(player), treatment);
        if (i < 0) return false;
        Wound w = s.wounds.get(i);
        if (treatment == Treatment.ALCOHOL) {
            w.cleaned = true;
            w.cleanedAt = w.age;
        } else {
            w.bandaged = true;
        }
        s.dirty = true;
        message(player, treatment == Treatment.ALCOHOL ? "cleaned" : "bandaged");
        return true;
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wound." + key), true);
    }
}
