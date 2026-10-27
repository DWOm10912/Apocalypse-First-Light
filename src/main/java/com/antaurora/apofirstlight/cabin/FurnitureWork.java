package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A wooden furniture piece made at the workbench (docs/gameplay/furniture_bench_work_v1.md, user 2026-10-22), both sides:
 * the recipe's work is a list of steps ({@link Step}: crosscut the boards, nail, brush the stain on, fit the hardware), each
 * played as player actions (player_actions/furniture/*.json, tools/author-furniture-actions.mjs). Nails and screws are a
 * timing ring each (doc 12.1, client/workbench/FurnitureWorkClient): the action sets the nail or starts the screw, then
 * every press is one blow or quarter turn, two in the zone's middle third; a miss only costs time (no bent nails, no
 * stripped screws). How many blows a nail takes, how many turns a screw, how fast and how wide the ring's zone come from
 * the carpentry growth ({@link #GROWTH}).
 * <p>
 * The piece's state is a {@link Job}, kept by the bench (WorkbenchBlockEntity, saved and sent to the clients): a piece left
 * half done stays on the bench and anyone who knows the recipe goes on from it. The server and the client both read the
 * order of play from here ({@link #plan}, {@link #advance}): the client plays it, the server checks that each step came no
 * sooner than its actions take ({@link Plan#ticks}) and keeps the count.
 */
public final class FurnitureWork {
    /** WIPE: the stain brushed on (doc 12.2; the step's id kept from the rag's wipe, so pieces left half done load). */
    public static final String CROSSCUT = "furniture/crosscut", NAIL = "furniture/nail", WIPE = "furniture/wipe",
            FIT_HINGE = "furniture/fit_hinge", FIT_KNOB = "furniture/fit_knob", FIT_ESCUTCHEON = "furniture/fit_escutcheon";
    /** The rag's wipe-off, dropped with the brush (doc 12.5 3): taken out of a piece saved with it. */
    private static final String WIPE_OFF = "furniture/wipe_off";
    /** Played once a batch: before the first wipe (pried open and stirred), after the last piece (pressed shut). */
    public static final String CAN_OPEN = "furniture/can_open", CAN_CLOSE = "furniture/can_close";
    public static final String NAIL_SET = "furniture/nail_set", NAIL_HOLD = "furniture/nail_hold", NAIL_STRIKE = "furniture/nail_strike",
            NAIL_GLANCE = "furniture/nail_glance", NAIL_SEAT = "furniture/nail_seat";
    public static final String SCREW_START = "furniture/screw_start", SCREW_HOLD = "furniture/screw_hold", SCREW_TURN = "furniture/screw_turn",
            SCREW_SLIP = "furniture/screw_slip", SCREW_SEAT = "furniture/screw_seat";
    /** The pieces on the bench (doc 3.6); before the first step that names one, the board being sawn. */
    public static final Set<String> PIECES = Set.of("panel", "frame", "box", "rack");
    public static final String BOARD = "board";
    /** The carpentry growth (survivor_growth/carpentry.json, doc 3.7). */
    public static final String GROWTH = "afl:carpentry";

    public enum Kind { TIMED, NAIL, FIT }

    /** The steps a recipe may name, and the screws one piece of hardware takes (doc 3.5: a hinge leaf 6, a knob 1, an escutcheon 2). */
    private static final Map<String, Kind> KINDS = Map.of(CROSSCUT, Kind.TIMED, NAIL, Kind.NAIL, WIPE, Kind.TIMED,
            FIT_HINGE, Kind.FIT, FIT_KNOB, Kind.FIT, FIT_ESCUTCHEON, Kind.FIT);
    private static final Map<String, Integer> SCREWS = Map.of(FIT_HINGE, 6, FIT_KNOB, 1, FIT_ESCUTCHEON, 2);
    /** The nail's and screw's actions, timed by the carpentry level ({@link #scale}); the others keep their length. */
    private static final Set<String> SCALED = Set.of(NAIL_SET, NAIL_STRIKE, NAIL_GLANCE, NAIL_SEAT, SCREW_START, SCREW_TURN, SCREW_SLIP, SCREW_SEAT);

    private FurnitureWork() {}

    /** One step of a recipe's work: its action, how many times (nails: visible nails; fits: pieces of hardware), the piece from it on. */
    public record Step(String step, int times, @Nullable String piece) {}

    @Nullable
    public static Kind kind(String step) {
        return KINDS.get(step);
    }

    /** The screws one piece of hardware of a fit step takes (0: not a fit). */
    public static int screws(String step) {
        return SCREWS.getOrDefault(step, 0);
    }

    // ---------------------------------------------------------------- carpentry (doc 3.7)

    /** Blows to drive a nail home, quarter turns to seat a screw: novice 4, trained 3, practised 2. */
    public static int blows(int level) {
        return 4 - com.antaurora.apofirstlight.survivor.SurvivorGrowth.tier(Math.max(0, level));
    }

    /** The nail's and screw's actions' length factor: x1.2 at level 0, x1 at 5, x0.8 at 10. */
    public static double scale(int level) {
        return 1.2 - 0.04 * Math.max(0, Math.min(10, level));
    }

    /**
     * The ring's zone (a share of the turn): 14 % + 1.2 % a level, times the QTE window attribute, at most 0.36 (a novice
     * about 50 deg, level 10 about 94, at most 130); its middle third is the perfect zone (two blows).
     */
    public static float zone(int level, double window) {
        return (float) Math.min(0.36, (0.14 + 0.012 * Math.max(0, Math.min(10, level))) * window);
    }

    /** An action's ticks at a level (the nail's and screw's scaled; a missing file 20). */
    public static int ticks(String action, int level) {
        PlayerAction a = PlayerActions.get(action);
        double s = a == null || Float.isNaN(a.length) ? 1 : a.length;
        return (int) Math.max(1, Math.round(s * 20 * (SCALED.contains(action) ? scale(level) : 1)));
    }

    /** A piece's ticks at a level, every press in the zone after 0.4 s on the ring (doc 4; the can opened once a piece not counted). */
    public static int estimate(List<Step> steps, int level) {
        int b = blows(level), wait = 8, n = 0;
        for (Step s : steps) switch (kind(s.step())) {
            case TIMED -> n += s.times() * ticks(s.step(), level);
            case NAIL -> n += s.times() * (ticks(NAIL_SET, level) + (b - 1) * ticks(NAIL_STRIKE, level) + ticks(NAIL_SEAT, level) + b * wait);
            case FIT -> n += s.times() * (ticks(s.step(), level) + screws(s.step())
                    * (ticks(SCREW_START, level) + (b - 1) * ticks(SCREW_TURN, level) + ticks(SCREW_SEAT, level) + b * wait));
        }
        return n;
    }

    // ---------------------------------------------------------------- the piece

    /**
     * The piece on a bench: the recipe and its steps (kept here, so a client that does not know the recipe still draws it),
     * the wood (its planks block, the board first carried when it was begun), the knob fitted (iron or wooden), and how far:
     * the step, its time (rep), the screw in the piece of hardware (sub), good blows on this nail or screw (hits), whether
     * this nail is set or this screw started (set: a press came), the ticks worked on it.
     */
    public record Job(ResourceLocation recipe, List<Step> steps, @Nullable ResourceLocation wood, @Nullable ResourceLocation knob,
                      int step, int rep, int sub, int hits, boolean set, int worked) {
        public static Job begin(ResourceLocation recipe, List<Step> steps, @Nullable ResourceLocation wood, @Nullable ResourceLocation knob) {
            return new Job(recipe, List.copyOf(steps), wood, knob, 0, 0, 0, 0, false, 0);
        }

        public boolean complete() { return step >= steps.size(); }

        @Nullable
        public Step current() { return complete() ? null : steps.get(step); }

        /** Going on after a stop: this nail or screw from its start (the ones done stay done). */
        public Job resume() { return new Job(recipe, steps, wood, knob, step, rep, sub, 0, false, worked); }

        Job worked(int more) { return new Job(recipe, steps, wood, knob, step, rep, sub, hits, set, worked + Math.max(0, more)); }

        /** The piece on the bench now: the last one named up to this step, or the board. */
        public String piece() {
            String p = BOARD;
            for (int i = 0; i < Math.min(step + 1, steps.size()); i++) if (steps.get(i).piece() != null) p = steps.get(i).piece();
            return p;
        }

        /** How many of a step's times are done: all before this step, this step's rep, none after. */
        public int doneOf(int index) {
            return index < step ? steps.get(index).times() : index == step ? rep : 0;
        }

        /** Times done and in all of the steps playing {@code action}. */
        public int[] count(String action) {
            int done = 0, all = 0;
            for (int i = 0; i < steps.size(); i++) if (steps.get(i).step().equals(action)) {
                done += doneOf(i);
                all += steps.get(i).times();
            }
            return new int[]{done, all};
        }

        /** The first step playing {@code action}, or -1. */
        public int first(String action) {
            for (int i = 0; i < steps.size(); i++) if (steps.get(i).step().equals(action)) return i;
            return -1;
        }

        /** The experience for the finished piece: a nail driven 1, a screw seated 1, the piece 3 (doc 3.7). */
        public int xp() {
            int n = 3;
            for (Step s : steps) {
                if (kind(s.step()) == Kind.NAIL) n += s.times();
                if (kind(s.step()) == Kind.FIT) n += s.times() * screws(s.step());
            }
            return n;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Recipe", recipe.toString());
            ListTag list = new ListTag();
            for (Step s : steps) {
                CompoundTag st = new CompoundTag();
                st.putString("Step", s.step());
                st.putInt("Times", s.times());
                if (s.piece() != null) st.putString("Piece", s.piece());
                list.add(st);
            }
            t.put("Steps", list);
            if (wood != null) t.putString("Wood", wood.toString());
            if (knob != null) t.putString("Knob", knob.toString());
            t.putInt("At", step);
            t.putInt("Rep", rep);
            t.putInt("Sub", sub);
            t.putInt("Hits", hits);
            t.putBoolean("Set", set);
            t.putInt("Worked", worked);
            return t;
        }

        @Nullable
        public static Job load(CompoundTag t) {
            ResourceLocation recipe = ResourceLocation.tryParse(t.getString("Recipe"));
            if (recipe == null) return null;
            List<Step> steps = new ArrayList<>();
            ListTag list = t.getList("Steps", Tag.TAG_COMPOUND);
            int at = t.getInt("At"), rep = t.getInt("Rep");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag st = list.getCompound(i);
                if (WIPE_OFF.equals(st.getString("Step"))) {
                    // saved before the brush: the wipe-off is gone, a piece at it goes on with the next step
                    if (at > steps.size()) at--;
                    else if (at == steps.size()) rep = 0;
                    continue;
                }
                if (kind(st.getString("Step")) == null) return null;
                steps.add(new Step(st.getString("Step"), Math.max(1, st.getInt("Times")), st.contains("Piece") ? st.getString("Piece") : null));
            }
            if (steps.isEmpty()) return null;
            return new Job(recipe, List.copyOf(steps), t.contains("Wood") ? ResourceLocation.tryParse(t.getString("Wood")) : null,
                    t.contains("Knob") ? ResourceLocation.tryParse(t.getString("Knob")) : null,
                    at, rep, t.getInt("Sub"), t.getInt("Hits"), t.getBoolean("Set"), t.getInt("Worked"));
        }
    }

    // ---------------------------------------------------------------- the order of play

    /**
     * One action played: its ticks (0: the hold, looped until a press). The client plays them all straight on from the one
     * before (client/workbench/FurnitureWorkClient#at, doc 12.9).
     */
    public record Play(String action, int ticks) {}

    /**
     * What ends a plan: a timed step's end (DONE), a press on the ring (PRESS), the piece done (END: the can closed and the
     * work stops; one piece a start, it stays on the bench, doc 12.3).
     */
    public enum Wait { DONE, PRESS, END }

    /** The plan of the can shut at a batch's end (Plan#qte): played, then the work stops. */
    public static final String CLOSING = "closing";

    /**
     * Where the piece stands, as the client draws it: the step, rep, sub, hits, set of a {@link Job}, and whether the can
     * stands open. A plan carries the one before its press ({@code from}: drawn while the press's own action plays) and the
     * one after ({@code to}), so the bench's block update and the plan may come in either order.
     */
    public record Mark(int step, int rep, int sub, int hits, boolean set, boolean can) {
        public static Mark of(Job job, boolean can) {
            return new Mark(job.step(), job.rep(), job.sub(), job.hits(), job.set(), can);
        }

        /** The job at this mark (its recipe, steps, wood and knob). */
        public Job on(Job job) {
            return new Job(job.recipe(), job.steps(), job.wood(), job.knob(), step, rep, sub, hits, set, job.worked());
        }

        String encode() {
            return step + "." + rep + "." + sub + "." + hits + "." + (set ? 1 : 0) + "." + (can ? 1 : 0);
        }

        @Nullable
        static Mark decode(String text) {
            String[] p = text.split("[.]");
            if (p.length != 6) return null;
            try {
                return new Mark(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                        "1".equals(p[4]), "1".equals(p[5]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * The actions to play from now until the next word from the player, and what that word is; {@code qte}: nail or screw
     * (or {@link #CLOSING}); {@code good}: the action a good press plays (the client plays it at once, before the server's word);
     * {@code from}, {@code to}: the piece before the press and after (null: not known, the bench's own job is drawn).
     */
    public record Plan(List<Play> plays, Wait waits, @Nullable String qte, @Nullable String good, @Nullable Mark from, @Nullable Mark to) {
        public Plan(List<Play> plays, Wait waits, @Nullable String qte, @Nullable String good) {
            this(plays, waits, qte, good, null, null);
        }

        Plan marked(@Nullable Mark from, @Nullable Mark to) {
            return new Plan(plays, waits, qte, good, from, to);
        }

        /** The least ticks before the next word (the hold not counted). */
        public int ticks() {
            int n = 0;
            for (Play p : plays) n += p.ticks();
            return n;
        }

        /**
         * The plan as WorkOf sends it (the own client and the players watching): action@ticks, '|' between; then '#' and
         * the wait, the qte, the good press's action, the marks before and after.
         */
        public String encode() {
            StringBuilder b = new StringBuilder();
            for (Play p : plays) {
                if (!b.isEmpty()) b.append('|');
                b.append(p.action()).append('@').append(p.ticks());
            }
            b.append('#').append(waits.name()).append('#').append(qte == null ? "" : qte).append('#').append(good == null ? "" : good);
            b.append('#').append(from == null ? "" : from.encode()).append('#').append(to == null ? "" : to.encode());
            return b.toString();
        }
    }

    /** WorkOf's plan back (Plan#encode); a broken one waits for the end. */
    public static Plan decode(String text) {
        String[] parts = text.split("#", -1);
        Wait wait = Wait.END;
        if (parts.length > 1) try { wait = Wait.valueOf(parts[1]); } catch (IllegalArgumentException ignored) { }
        String qte = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
        String good = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
        Mark from = parts.length > 4 ? Mark.decode(parts[4]) : null, to = parts.length > 5 ? Mark.decode(parts[5]) : null;
        return new Plan(plays(parts[0]), wait, qte, good, from, to);
    }

    private static List<Play> plays(String queue) {
        List<Play> out = new ArrayList<>();
        for (String part : queue.split("\\|")) {
            if (part.isEmpty()) continue;
            int at = part.lastIndexOf('@');
            int ticks = 0;
            if (at > 0) try { ticks = Integer.parseInt(part.substring(at + 1)); } catch (NumberFormatException ignored) { }
            out.add(new Play(at > 0 ? part.substring(0, at) : part, ticks));
        }
        return out;
    }

    /** Whether a WorkOf action is a furniture plan (Plan#encode), not one looped action. */
    public static boolean queue(String action) {
        return action.contains("#");
    }

    /**
     * What is played from the job's state: {@code reaction} first (the blow or turn just pressed, or null), then the next
     * step's start: a timed step's action (the can pried open and stirred before the first wipe of a batch:
     * {@code canOpen} false), a nail set, the hardware held on and the screw started; then the wait.
     * {@code before}: the job before the word that led here (null: a session's first plan).
     */
    public static Plan plan(Job job, boolean canOpen, int level, @Nullable Job before, @Nullable String reaction) {
        Step s = job.current();
        // the can stands open by the plan's end when the first wipe of a batch pries it open
        boolean can = canOpen || s != null && WIPE.equals(s.step());
        return play(job, canOpen, level, reaction).marked(before == null ? null : Mark.of(before, canOpen), Mark.of(job, can));
    }

    private static Plan play(Job job, boolean canOpen, int level, @Nullable String reaction) {
        List<Play> plays = new ArrayList<>();
        if (reaction != null) plays.add(new Play(reaction, ticks(reaction, level)));
        Step s = job.current();
        if (s == null) return new Plan(List.copyOf(plays), Wait.END, null, null);
        switch (kind(s.step())) {
            case TIMED -> {
                if (WIPE.equals(s.step()) && !canOpen) plays.add(new Play(CAN_OPEN, ticks(CAN_OPEN, level)));
                plays.add(new Play(s.step(), ticks(s.step(), level)));
                return new Plan(List.copyOf(plays), Wait.DONE, null, null);
            }
            case NAIL -> {
                if (!job.set()) plays.add(new Play(NAIL_SET, ticks(NAIL_SET, level)));
                plays.add(new Play(NAIL_HOLD, 0));
                return new Plan(List.copyOf(plays), Wait.PRESS, "nail", reaction(job, true, false, level));
            }
            case FIT -> {
                if (!job.set()) {
                    if (job.sub() == 0) plays.add(new Play(s.step(), ticks(s.step(), level)));
                    plays.add(new Play(SCREW_START, ticks(SCREW_START, level)));
                }
                plays.add(new Play(SCREW_HOLD, 0));
                return new Plan(List.copyOf(plays), Wait.PRESS, "screw", reaction(job, true, false, level));
            }
        }
        return new Plan(List.copyOf(plays), Wait.END, null, null);
    }

    /** The batch's end: the can pressed shut when it stands open, then the work stops. */
    public static Plan closing(boolean canOpen, int level) {
        return new Plan(canOpen ? List.of(new Play(CAN_CLOSE, ticks(CAN_CLOSE, level))) : List.of(), Wait.END, CLOSING, null);
    }

    /** A miss's action: the hammer glances off the nail, the bit slips out of the slot. */
    public static String miss(@Nullable String qte) {
        return "nail".equals(qte) ? NAIL_GLANCE : SCREW_SLIP;
    }

    /** Good blows a press counts: a miss none, the zone one, its middle third ({@code perfect}) two. */
    public static int counts(boolean good, boolean perfect) {
        return !good ? 0 : perfect ? 2 : 1;
    }

    /** The action a press plays: a good blow or turn (the last one seats it), or the glance or slip of a miss. */
    public static String reaction(Job job, boolean good, boolean perfect, int level) {
        Step s = job.current();
        boolean nail = s != null && kind(s.step()) == Kind.NAIL;
        if (!good) return nail ? NAIL_GLANCE : SCREW_SLIP;
        boolean last = job.hits() + counts(true, perfect) >= blows(level);
        return nail ? (last ? NAIL_SEAT : NAIL_STRIKE) : (last ? SCREW_SEAT : SCREW_TURN);
    }

    /** The job after a word: a timed step's end ({@code press} false), or a press ({@code good}: in the zone, {@code perfect}: its middle). */
    public static Job advance(Job job, boolean press, boolean good, boolean perfect, int level) {
        Step s = job.current();
        if (s == null) return job;
        int step = job.step(), rep = job.rep(), sub = job.sub(), hits = job.hits();
        boolean set = job.set();
        if (!press) rep++;
        else {
            set = true;
            if (good && (hits += counts(true, perfect)) >= blows(level)) {
                hits = 0;
                set = false;
                if (kind(s.step()) != Kind.FIT || ++sub >= screws(s.step())) {
                    sub = 0;
                    rep++;
                }
            }
        }
        if (rep >= s.times()) {
            step++;
            rep = 0;
            sub = 0;
            hits = 0;
            set = false;
        }
        return new Job(job.recipe(), job.steps(), job.wood(), job.knob(), step, rep, sub, hits, set, job.worked());
    }

    /** How far the piece is (0..1), each step weighed by its novice estimate. */
    public static float progress(Job job, int level) {
        double all = 0, done = 0;
        for (int i = 0; i < job.steps().size(); i++) {
            Step st = job.steps().get(i);
            double e = estimate(List.of(st), level);
            all += e;
            if (i < job.step()) done += e;
            else if (i == job.step()) {
                int screws = screws(st.step());
                double part = job.rep() + (screws > 0 ? job.sub() / (double) screws : 0);
                done += e * part / Math.max(1, st.times());
            }
        }
        return all <= 0 ? 0 : (float) Math.min(1, done / all);
    }

    /** Ticks a job's piece is worked between two words, counted as at most twice the plan plus 2 s (a wait on the ring). */
    public static Job worked(Job job, long gap, Plan plan) {
        return job.worked((int) Math.min(gap, plan.ticks() * 2L + 40));
    }
}
