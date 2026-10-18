package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.survivor.SurvivorCatalog.BackgroundDef;
import com.antaurora.apofirstlight.survivor.SurvivorCatalog.BodyDef;
import com.antaurora.apofirstlight.survivor.SurvivorCatalog.GrowthDef;
import com.antaurora.apofirstlight.survivor.SurvivorCatalog.Snapshot;
import com.antaurora.apofirstlight.survivor.SurvivorCatalog.TraitDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Survivor setup rules (docs/gameplay/survivor_background_skills_v1_plan.md 4–6, 9): one point pool per survivor, the
 * background's budget. Body stats start at their default; each level up costs a point, each level down gives one. A
 * positive trait costs its points, a negative one gives them (at most {@link SurvivorCatalog#NEGATIVE_POINT_CAP}).
 * Background traits are locked and free. Points left over are lost. The server checks every choice; a confirmed setup is
 * written as final values (background, all traits, body levels with trait bonuses, growth start levels).
 */
public final class SurvivorSetup {
    public static final String CHOOSING = "choosing";
    public static final String DONE = "done";
    /** The plain background: no traits, the biggest budget; also what a V0 survivor counts as. */
    public static final String ORDINARY = "afl:ordinary";
    /** Successors spend their budget on body levels within this range (section 6). */
    static final int SUCCESSOR_MIN = 2;
    static final int SUCCESSOR_MAX = 5;

    private SurvivorSetup() {
    }

    /** A player's picks: a background, chosen body levels (missing = default) and chosen traits (not the background's). */
    public record Choice(String background, Map<String, Integer> body, List<String> traits) {
    }

    /**
     * The outcome of {@link #check}: points left (may be negative), points the negative traits give, and what is wrong
     * (empty = the choice can be confirmed). Problems are short English lines for the command; the screen shows its own.
     */
    public record Check(int pointsLeft, int negativePoints, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    public static Check check(Snapshot catalog, Choice choice) {
        List<String> problems = new ArrayList<>();
        BackgroundDef background = catalog.backgrounds().get(choice.background());
        if (background == null) problems.add("unknown background " + choice.background());
        int spent = 0;
        for (String id : choice.body().keySet()) {
            if (!catalog.body().containsKey(id)) problems.add("unknown body stat " + id);
        }
        for (BodyDef def : catalog.body().values()) {
            int level = choice.body().getOrDefault(def.id(), def.defaultLevel());
            if (level < def.startMin() || level > def.startMax()) {
                problems.add(def.id() + " " + level + " outside " + def.startMin() + "-" + def.startMax());
            }
            spent += level - def.defaultLevel();
        }
        List<String> fixed = background == null ? List.of() : background.traits();
        Set<String> all = new LinkedHashSet<>(fixed);
        int negative = 0;
        for (String id : choice.traits()) {
            TraitDef def = catalog.traits().get(id);
            if (def == null) problems.add("unknown trait " + id);
            else if (def.backgroundOnly()) problems.add(id + " only comes with a background");
            else if (fixed.contains(id)) problems.add(id + " already comes with the background");
            else if (!all.add(id)) problems.add(id + " twice");
            else {
                spent += def.cost();
                if (def.negative()) negative -= def.cost();
            }
        }
        List<String> sorted = new ArrayList<>(all);
        for (int i = 0; i < sorted.size(); i++) {
            TraitDef def = catalog.traits().get(sorted.get(i));
            if (def == null) continue;
            for (int j = i + 1; j < sorted.size(); j++) {
                if (def.exclusive().contains(sorted.get(j))) problems.add(sorted.get(i) + " excludes " + sorted.get(j));
            }
        }
        if (negative > SurvivorCatalog.NEGATIVE_POINT_CAP) {
            problems.add("negative traits give " + negative + " points, at most " + SurvivorCatalog.NEGATIVE_POINT_CAP);
        }
        int left = (background == null ? 0 : background.budget()) - spent;
        if (left < 0) problems.add(-left + " points over the budget");
        return new Check(left, negative, List.copyOf(problems));
    }

    /**
     * Writes a checked choice into the record as final values and marks the setup done. Body levels add the traits' body
     * bonuses (big frame: strength can start at 6); growth items start at their default, raised by trait start levels
     * and capped by trait caps.
     */
    static void confirm(SurvivorRecord record, Snapshot catalog, Choice choice) {
        BackgroundDef background = catalog.backgrounds().get(choice.background());
        List<String> all = new ArrayList<>(new LinkedHashSet<>(background == null ? List.of() : background.traits()));
        for (String id : choice.traits()) if (!all.contains(id)) all.add(id);
        record.background = choice.background();
        record.traits.clear();
        record.traits.addAll(all);
        record.body.clear();
        for (BodyDef def : catalog.body().values()) {
            int level = choice.body().getOrDefault(def.id(), def.defaultLevel());
            for (String id : all) {
                TraitDef trait = catalog.traits().get(id);
                if (trait != null) level += trait.bodyBonus().getOrDefault(def.id(), 0);
            }
            record.body.put(def.id(), new SurvivorRecord.Progress(Math.max(1, Math.min(def.max(), level)), 0));
        }
        record.growth.clear();
        for (GrowthDef def : catalog.growth().values()) {
            int level = def.defaultLevel();
            int cap = def.max();
            for (String id : all) {
                TraitDef trait = catalog.traits().get(id);
                if (trait == null) continue;
                level = Math.max(level, trait.growthStart().getOrDefault(def.id(), level));
                cap = Math.min(cap, trait.growthCap().getOrDefault(def.id(), cap));
            }
            record.growth.put(def.id(), new SurvivorRecord.Progress(Math.max(0, Math.min(cap, level)), 0));
        }
        record.setup = DONE;
    }

    /** Back to the first survivor's choosing state: no background, no traits, default levels (dev command). */
    static void reset(SurvivorRecord record) {
        record.background = null;
        record.traits.clear();
        record.body.clear();
        record.growth.clear();
        record.setup = CHOOSING;
    }

    /**
     * A successor's setup (section 6), reproducible from its roll seed: a background from the successor pool, its locked
     * traits, the budget spent at random on body levels ({@value #SUCCESSOR_MIN}–{@value #SUCCESSOR_MAX}); no random
     * traits (V1). With no background data the successor is an ordinary person with default levels.
     */
    static void rollSuccessor(SurvivorRecord record, Snapshot catalog, long seed) {
        List<BackgroundDef> pool = new ArrayList<>();
        for (BackgroundDef def : catalog.backgrounds().values()) if (def.successorPool()) pool.add(def);
        if (pool.isEmpty()) {
            reset(record);
            record.background = ORDINARY;
            record.setup = DONE;
            return;
        }
        Random random = new Random(SurvivorNames.stream(seed, 501));
        BackgroundDef background = pool.get(random.nextInt(pool.size()));
        List<BodyDef> order = new ArrayList<>(catalog.body().values());
        Collections.shuffle(order, random);
        Map<String, Integer> body = new LinkedHashMap<>();
        int left = background.budget();
        for (BodyDef def : order) {
            int lo = Math.max(SUCCESSOR_MIN, def.startMin());
            int hi = Math.max(lo, Math.min(Math.min(SUCCESSOR_MAX, def.startMax()), def.defaultLevel() + left));
            int level = lo + random.nextInt(hi - lo + 1);
            body.put(def.id(), level);
            left -= level - def.defaultLevel();
        }
        Choice choice = new Choice(background.id(), body, List.of());
        if (!check(catalog, choice).ok()) choice = new Choice(background.id(), Map.of(), List.of());
        confirm(record, catalog, choice);
    }

    /** A body stat's level for this survivor: the stored one, else the stat's default. */
    public static int bodyLevel(SurvivorRecord record, BodyDef def) {
        SurvivorRecord.Progress p = record.body.get(def.id());
        return p == null ? def.defaultLevel() : Math.max(1, Math.min(def.max(), p.level));
    }
}
