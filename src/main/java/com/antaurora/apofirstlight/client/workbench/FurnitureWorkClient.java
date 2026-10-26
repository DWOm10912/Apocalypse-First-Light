package com.antaurora.apofirstlight.client.workbench;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.building.BuildingPackets;
import com.antaurora.apofirstlight.cabin.FurnitureWork;
import com.antaurora.apofirstlight.cabin.Workbench;
import com.antaurora.apofirstlight.cabin.WorkbenchBlockEntity;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting;
import com.antaurora.apofirstlight.client.RangedSoundInstance;
import com.antaurora.apofirstlight.client.WoodChipParticle;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.network.RangedSoundS2CPacket;
import com.antaurora.apofirstlight.registry.AflAttributes;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import com.antaurora.apofirstlight.survivor.SurvivorStats;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.antaurora.apofirstlight.cabin.FurnitureWork.BOARD;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.CAN_CLOSE;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.CAN_OPEN;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.CLOSING;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.CROSSCUT;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.FIT_KNOB;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.NAIL_GLANCE;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.NAIL_HOLD;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.NAIL_SEAT;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.NAIL_SET;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.NAIL_STRIKE;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.SCREW_HOLD;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.SCREW_SEAT;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.SCREW_SLIP;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.SCREW_START;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.SCREW_TURN;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.WIPE;
import static com.antaurora.apofirstlight.cabin.FurnitureWork.WIPE_OFF;
import static com.antaurora.apofirstlight.client.workbench.FurnitureBench.*;

/**
 * The furniture work on the client (docs/gameplay/furniture_bench_work_v1.md 4, 5): the plans the server sends (FurnitureWork
 * Plan, carried by BuildingPackets.WorkOf as the action) played one action after another, for the worker and whoever watches.
 * The own client plays the next plan as soon as it sends its word (BuildingPackets.Act): the server's copy then only brings
 * the marks, or replaces a plan that came out otherwise. Here too: the timing bar of the nails and screws (as the generator's
 * pull, client/PortableGeneratorPull), the actions' anchors (FurnitureBench's layout), their sounds and sawdust at their
 * events, and what the bench draws ({@link #scene}, client/FurnitureWorkRenderer).
 */
public final class FurnitureWorkClient {
    /** The bar's sweep there and back (ticks, the generator's pull's), and the work's stop after this long on the bar with no press. */
    private static final int PERIOD = 28, IDLE = 600;
    /** The wooden knob's fit (its own hold), played for FIT_KNOB when the piece takes wooden knobs. */
    public static final String FIT_KNOB_WOODEN = "furniture/fit_knob_wooden";
    /** A press's actions: a plan's first play after a press, drawn on the piece as it was before the press (Plan#from). */
    private static final Set<String> REACTIONS = Set.of(NAIL_STRIKE, NAIL_GLANCE, NAIL_SEAT, SCREW_TURN, SCREW_SLIP, SCREW_SEAT);
    private static final Set<String> SCREWING = Set.of(SCREW_START, SCREW_HOLD, SCREW_TURN, SCREW_SLIP, SCREW_SEAT);
    private static final ResourceLocation WHITE_OAK_PLANKS = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "white_oak_planks");

    /** A plan played at a bench from {@code start} (game ticks); the server's copy of a plan already played only brings its marks. */
    private static final class Run {
        FurnitureWork.Plan plan;
        final double start;
        final BlockPos bench;

        Run(FurnitureWork.Plan plan, double start, BlockPos bench) {
            this.plan = plan;
            this.start = start;
            this.bench = bench.immutable();
        }
    }

    private static final Map<Integer, Run> RUNS = new HashMap<>();
    /** The last piece seen on each bench: drawn on while its block update and the plan cross (and after the piece is taken). */
    private static final Map<BlockPos, FurnitureWork.Job> SEEN = new HashMap<>();
    /** Each worker's run and its last tick whose events were played. */
    private static final Map<Integer, Fired> FIRED = new HashMap<>();

    private record Fired(Run run, long tick) {}

    /** The own words whose plan has not come back yet: only the last one's is taken. */
    private static int pending;
    /** The bar: the zone's middle, the last press (game time) and whether it was in the zone. */
    private static float zone = 0.5F;
    private static double pressedAt = -100;
    private static boolean pressedGood;

    private FurnitureWorkClient() {}

    private static double time(float partial) {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    static void clear() {
        RUNS.clear();
        SEEN.clear();
        FIRED.clear();
        pending = 0;
    }

    // ------------------------------------------------------------------------------------------------ the plans

    /** The own work began (BuildingPackets.Work sent): the server's first plan is awaited. */
    static void started(Player player) {
        RUNS.remove(player.getId());
        pending = 1;
        zone = nextZone(0.2F);
    }

    /** The own work stopped, here or by the server. */
    static void stopped(Player player) {
        RUNS.remove(player.getId());
        pending = 0;
    }

    /** WorkOf for the own player at furniture work: the plan of the last word sent (an earlier word's is played over already). */
    static void own(Player player, WorkbenchCrafting.Working work) {
        if (!FurnitureWork.queue(work.action())) return;
        if (pending > 0) pending--;
        if (pending > 0) return;
        FurnitureWork.Plan plan = FurnitureWork.decode(work.action());
        Run r = RUNS.get(player.getId());
        if (r != null && r.bench.equals(work.bench()) && same(r.plan, plan)) r.plan = plan;
        else RUNS.put(player.getId(), new Run(plan, time(0), work.bench()));
    }

    /** WorkOf for another player: a plan from now, or the end of it. */
    static void remote(int entity, @Nullable WorkbenchCrafting.Working work) {
        if (work == null || !FurnitureWork.queue(work.action())) RUNS.remove(entity);
        else RUNS.put(entity, new Run(FurnitureWork.decode(work.action()), time(0), work.bench()));
    }

    private static boolean same(FurnitureWork.Plan a, FurnitureWork.Plan b) {
        return a.waits() == b.waits() && a.plays().equals(b.plays());
    }

    /**
     * The own work's tick (after WorkbenchWork's stop checks): a timed step played out (the word, and the next step at
     * once), a piece done (on with the next, or the can shut and the end), a closing played out, the bar left alone too long.
     */
    static void tick(WorkbenchWork.Session s, Player player) {
        Run r = RUNS.get(player.getId());
        if (r == null) return;
        FurnitureWork.Plan p = r.plan;
        double now = time(0), el = now - r.start;
        if (el < p.ticks()) return;
        int lv = s.level;
        boolean can = p.to() != null && p.to().can();
        FurnitureWork.Job cur = current(r, s);
        switch (p.waits()) {
            case DONE -> {
                word(s, false, false);
                if (cur != null) play(player, FurnitureWork.plan(FurnitureWork.advance(cur, false, false, lv), can, lv, cur, null), r.bench, now);
            }
            case PRESS -> {
                if (el - p.ticks() > IDLE) WorkbenchWork.stopOwn();
            }
            case END -> {
                if (CLOSING.equals(p.qte())) {
                    WorkbenchWork.stopOwn();
                    return;
                }
                s.done++;
                boolean more = cur != null && s.done < s.quantity && WorkbenchRecipes.craftable(player, s.recipe) >= 1;
                word(s, !more, false);
                play(player, more ? FurnitureWork.plan(FurnitureWork.Job.begin(s.recipe.id(), s.recipe.steps(), cur.wood(), cur.knob()), can, lv, null, null)
                        : FurnitureWork.closing(can, lv), r.bench, now);
            }
        }
    }

    /** The use key on the bar: a blow or a quarter turn, in the zone or not (a miss only costs time). */
    static void press(WorkbenchWork.Session s, Player player) {
        Run r = RUNS.get(player.getId());
        if (r == null) return;
        FurnitureWork.Plan p = r.plan;
        double now = time(0);
        if (p.waits() != FurnitureWork.Wait.PRESS || now - r.start < p.ticks()) return;
        FurnitureWork.Job cur = current(r, s);
        if (cur == null) return;
        float w = zoneWidth(player, s.level);
        boolean good = Math.abs(marker(r, 0) - zone) <= w / 2;
        pressedAt = now;
        pressedGood = good;
        zone = nextZone(w);
        word(s, true, good);
        boolean can = p.to() != null && p.to().can();
        play(player, FurnitureWork.plan(FurnitureWork.advance(cur, true, good, s.level), can, s.level, cur,
                FurnitureWork.reaction(cur, good, s.level)), r.bench, now);
    }

    private static void word(WorkbenchWork.Session s, boolean press, boolean good) {
        AflNetwork.buildingToServer(new BuildingPackets.Act(s.bench, s.recipe.id(), press, good));
        pending++;
    }

    private static void play(Player player, FurnitureWork.Plan plan, BlockPos bench, double now) {
        RUNS.put(player.getId(), new Run(plan, now, bench));
    }

    /** The own piece as the plan leaves it. */
    @Nullable
    private static FurnitureWork.Job current(Run r, WorkbenchWork.Session s) {
        FurnitureWork.Job base = base(r.bench, s);
        if (base == null) return null;
        return r.plan.to() == null ? base : r.plan.to().on(base);
    }

    /**
     * The piece a bench's marks are read on (its recipe, steps, wood, knob): the bench's own job, else the last one seen
     * there, else (the own work, nothing there yet) the recipe's begun.
     */
    @Nullable
    private static FurnitureWork.Job base(BlockPos bench, @Nullable WorkbenchWork.Session s) {
        FurnitureWork.Job job = WorkbenchWork.benchJob(bench);
        if (job != null) SEEN.put(bench.immutable(), job);
        else job = SEEN.get(bench);
        if (s != null && s.furniture && s.bench.equals(bench) && (job == null || !job.recipe().equals(s.recipe.id())))
            job = FurnitureWork.Job.begin(s.recipe.id(), s.recipe.steps(), null, null);
        return job;
    }

    // ------------------------------------------------------------------------------------------------ the play's moment

    /** A plan's play at a moment: which, the action played (the wooden knob's fit for its own), its second, its speed, its clock's start (ticks). */
    private record Frame(int index, FurnitureWork.Play play, String action, double seconds, double speed, double started) {}

    @Nullable
    private static Frame frame(Run r, double now, @Nullable FurnitureWork.Job base) {
        List<FurnitureWork.Play> plays = r.plan.plays();
        double el = Math.max(0, now - r.start), acc = 0;
        for (int i = 0; i < plays.size(); i++) {
            FurnitureWork.Play pl = plays.get(i);
            String action = shownAction(pl.action(), base);
            // the hold loops until the press; the last play past its end stays on its last moment until the next plan
            if (pl.ticks() <= 0) return new Frame(i, pl, action, (el - acc) / 20, 1, r.start + acc);
            if (el < acc + pl.ticks() || i == plays.size() - 1) {
                double speed = length(action) * 20 / pl.ticks(), local = Math.min(el - acc, pl.ticks() - 0.2);
                return new Frame(i, pl, action, local / 20 * speed, speed, now - local);
            }
            acc += pl.ticks();
        }
        return null;
    }

    private static String shownAction(String action, @Nullable FurnitureWork.Job job) {
        return FIT_KNOB.equals(action) && wooden(job) ? FIT_KNOB_WOODEN : action;
    }

    static boolean wooden(@Nullable FurnitureWork.Job job) {
        return job != null && job.knob() != null && job.knob().getPath().equals("wooden_knob");
    }

    private static double length(String action) {
        PlayerAction a = PlayerActions.get(action);
        return a == null || Float.isNaN(a.length) ? 1 : a.length;
    }

    @Nullable
    private static FurnitureWork.Mark mark(Run r, Frame f) {
        return f.index() == 0 && r.plan.from() != null && REACTIONS.contains(f.play().action()) ? r.plan.from() : r.plan.to();
    }

    /** The piece as drawn at this play: before the press while the press's own action plays, after it from then on. */
    @Nullable
    private static FurnitureWork.Job shown(Run r, Frame f, BlockPos bench, @Nullable FurnitureWork.Job base) {
        // the can shut at the batch's end: the piece is taken already (or left done on the bench)
        if (CLOSING.equals(r.plan.qte())) return WorkbenchWork.benchJob(bench);
        if (base == null) return null;
        FurnitureWork.Mark m = mark(r, f);
        return m == null ? base : m.on(base);
    }

    /** What a bench draws: the piece (null: none), the action played at it and its second (null: no one works), the can out, the worker's level. */
    public record Scene(@Nullable FurnitureWork.Job job, @Nullable String action, double seconds, boolean can, int level) {}

    @Nullable
    public static Scene scene(BlockPos master, float partial) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (var e : RUNS.entrySet()) {
            Run r = e.getValue();
            if (!r.bench.equals(master) || !(mc.level.getEntity(e.getKey()) instanceof Player p)) continue;
            WorkbenchWork.Session s = p == mc.player ? WorkbenchWork.ownSession() : null;
            FurnitureWork.Job base = base(master, s);
            Frame f = frame(r, time(partial), base);
            if (f == null) continue;
            FurnitureWork.Mark m = mark(r, f);
            boolean can = f.action().equals(CAN_OPEN) || f.action().equals(CAN_CLOSE) || m != null && m.can() && !CLOSING.equals(r.plan.qte());
            return new Scene(shown(r, f, master, base), f.action(), f.seconds(), can, s != null ? s.level : WorkbenchWork.carpentry());
        }
        // no one at it: the piece as left (this nail or screw from its start, as the next who goes on finds it)
        FurnitureWork.Job job = WorkbenchWork.benchJob(master);
        return job == null ? null : new Scene(job.resume(), null, 0, false, WorkbenchWork.carpentry());
    }

    /** SkeletonActions (through WorkbenchWork#at): what this player plays at the furniture now, or null. */
    @Nullable
    static WorkbenchWork.At at(Player p, float partial) {
        Run r = RUNS.get(p.getId());
        var mc = Minecraft.getInstance();
        if (r == null || mc.level == null) return null;
        BlockState state = mc.level.getBlockState(r.bench);
        if (!(state.getBlock() instanceof Workbench)) return null;
        WorkbenchWork.Session s = p == mc.player ? WorkbenchWork.ownSession() : null;
        FurnitureWork.Job base = base(r.bench, s);
        double now = time(partial);
        Frame f = frame(r, now, base);
        if (f == null) return null;
        int level = s != null ? s.level : WorkbenchWork.carpentry();
        Map<String, Vec3> anchors = anchors(r.bench, state.getValue(Workbench.FACING), f.action(), f.seconds(), shown(r, f, r.bench, base), level);
        return new WorkbenchWork.At(f.action(), f.started() / 20.0, anchors, (float) f.speed(), f.play().chain());
    }

    // ------------------------------------------------------------------------------------------------ the piece's numbers

    /** How far the nail is in (0..1) at an action's second: set 0.15, each good blow an even share of the rest at its hit (0.2 s), the seat home. */
    public static double nailDepth(@Nullable String action, double t, int hits, int level) {
        int b = FurnitureWork.blows(level);
        double d0 = 0.15 + 0.85 * Math.min(hits, b - 1) / b, k = clamp((t - 0.2) / 0.05);
        if (NAIL_STRIKE.equals(action)) return d0 + (0.15 + 0.85 * Math.min(hits + 1, b - 1) / b - d0) * k;
        if (NAIL_SEAT.equals(action)) return d0 + (1 - d0) * k;
        return d0;
    }

    /** How far the screw is in (0..1) and how far turned (quarter turns) at an action's second: started 0.35, each good turn eased in over 0.05-0.3 s. */
    public static double[] screwDepth(@Nullable String action, double t, int hits, int level) {
        int b = FurnitureWork.blows(level);
        double d0 = 0.35 + 0.65 * Math.min(hits, b - 1) / b, k = clamp((t - 0.05) / 0.25);
        k = k * k * (3 - 2 * k);
        if (SCREW_TURN.equals(action)) return new double[]{d0 + (0.35 + 0.65 * Math.min(hits + 1, b - 1) / b - d0) * k, hits + k};
        if (SCREW_SEAT.equals(action)) return new double[]{d0 + (1 - d0) * k, hits + k};
        return new double[]{d0, hits};
    }

    /** Whether the nail being driven shows on the piece (set from 0.95 s of its setting, the fingers' till then). */
    public static boolean nailShown(@Nullable String action, double t) {
        return NAIL_SET.equals(action) ? t >= 0.95 : NAIL_HOLD.equals(action) || action != null && REACTIONS.contains(action) && action.startsWith("furniture/nail");
    }

    /** Whether the hardware being fitted shows on the piece (from 0.85 s of its fit, the hand's till then), and its screw (from 0.55 s of its start). */
    public static boolean hardwareShown(@Nullable String action, double t, FurnitureWork.Job job) {
        if (action == null) return job.sub() > 0 || job.set();
        if (action.startsWith("furniture/fit_")) return t >= 0.85;
        return SCREWING.contains(action);
    }

    public static boolean screwShown(@Nullable String action, double t) {
        return SCREW_START.equals(action) ? t >= 0.55 : action != null && SCREWING.contains(action);
    }

    /** The work's point on the piece (x, y, z): the nail's slot, the hardware's place; null for the other steps. */
    @Nullable
    private static double[] workPoint(@Nullable FurnitureWork.Job job) {
        if (job == null || job.complete()) return null;
        FurnitureWork.Kind kind = FurnitureWork.kind(job.current().step());
        Piece p = piece(pieceAt(job, job.step()));
        if (p == null) return null;
        if (kind == FurnitureWork.Kind.NAIL) return p.nail(nailsBefore(job, job.step()) + job.rep());
        if (kind == FurnitureWork.Kind.FIT) {
            Hardware h = hardware(job, job.step(), job.rep());
            return h == null ? null : h.base();
        }
        return null;
    }

    /** The piece's top, front and back (z), the board's top before there is a piece. */
    private static double[] extent(@Nullable FurnitureWork.Job job) {
        Piece p = job == null ? null : piece(pieceAt(job, Math.min(job.step(), job.steps().size() - 1)));
        return p == null ? new double[]{0.9, 0.06, 0.46} : new double[]{p.top(), p.z0(), p.z1()};
    }

    /**
     * The actions' anchors (tools/author-furniture-actions.mjs, the same numbers): the crosscut's stance, kerf, holding hand
     * and carry; the assembly's stance at the work (the nail's slot, the hardware's place; the can and the wipes their own),
     * the left hand's press, the nail's and screw's heads as far as they are in, the hardware's place, the box, the can and
     * its things, the wipes' strokes across the piece.
     */
    private static Map<String, Vec3> anchors(BlockPos bench, Direction facing, String action, double t, @Nullable FurnitureWork.Job job, int level) {
        Map<String, Vec3> m = new HashMap<>();
        if (action.equals(CROSSCUT)) {
            double yaw = Math.toRadians(CUT_YAW);
            put(m, "stand", bench, facing, CUT_STAND_X, 0, CUT_STAND_Z);
            put(m, "face", bench, facing, CUT_STAND_X + 3 * Math.sin(yaw), 0, CUT_STAND_Z + 3 * Math.cos(yaw));
            put(m, "kerf", bench, facing, CUT_X, CUT_TOP - 0.01, kerfZ(t));
            put(m, "hold", bench, facing, CUT_HOLD_X + CUT_PIECE * (1 - slide(t)), CUT_HOLD_Y, (CUT_Z0 + CUT_Z1) / 2);
            double[] c = carried(t, job == null ? 0 : job.count(CROSSCUT)[0]);
            put(m, "carry", bench, facing, c[0], c[1] + 0.02, c[2]);
            return m;
        }
        double[] ext = extent(job);
        double top = ext[0], z0 = ext[1], z1 = ext[2];
        boolean can = action.equals(CAN_OPEN) || action.equals(CAN_CLOSE), wipe = action.equals(WIPE) || action.equals(WIPE_OFF);
        double[] at = can || wipe ? null : workPoint(job);
        double x = at == null ? 1.02 : at[0], z = at == null ? 0.25 : at[2];
        double sz = can ? STAND_Z : standZ(z), sx = can ? CAN_STAND : wipe ? WIPE_STAND : standX(x);
        put(m, "stand", bench, facing, sx, 0, sz);
        put(m, "face", bench, facing, sx, 0, sz + 3);
        if (wipe) put(m, "press", bench, facing, WIPE_B + 0.22, top + 0.03, (z0 + z1) / 2);
        else put(m, "press", bench, facing, Math.min(x + PRESS_LEFT, 1.62), top + 0.03, Math.max(0.09, Math.min(z - PRESS_BACK, sz + PRESS_REACH)));
        double nd = job == null ? 0.15 : nailDepth(action, t, job.hits(), level);
        put(m, "head", bench, facing, x, top + NAIL_LEN * (1 - nd), z);
        Hardware hw = job == null || job.complete() || FurnitureWork.kind(job.current().step()) != FurnitureWork.Kind.FIT ? null
                : hardware(job, job.step(), job.rep());
        double[] screw = hw == null ? new double[]{x, top, z} : hw.screws()[Math.min(job.sub(), hw.screws().length - 1)];
        double sd = job == null ? 0.35 : screwDepth(action, t, job.hits(), level)[0];
        put(m, "shead", bench, facing, screw[0], screw[1] + SCREW_LEN * (1 - sd), screw[2]);
        if (hw != null) put(m, "hw", bench, facing, hw.base()[0], hw.base()[1], hw.base()[2]);
        else put(m, "hw", bench, facing, x - 0.038, top + 0.001, z);
        put(m, "box", bench, facing, BOX[0], BOX[1] + 0.13, BOX[2]);
        double[] mouth = mouth();
        put(m, "can", bench, facing, mouth[0], mouth[1], mouth[2]);
        put(m, "cantop", bench, facing, mouth[0], mouth[1] + 0.025, mouth[2]);
        put(m, "canhold", bench, facing, CAN[0] + 0.075, CAN[1] + 0.07, CAN[2]);
        put(m, "lid", bench, facing, LID[0], LID[1], LID[2]);
        put(m, "stick_bench", bench, facing, STICK[0], STICK[1], STICK[2]);
        put(m, "stick_rest", bench, facing, CAN[0] - 0.02, CAN[1] + 0.02, CAN[2]);
        put(m, "rag_rest", bench, facing, RAG[0], RAG[1], RAG[2]);
        // the strokes along the grain over the piece's depth: three a wipe on, two a wipe off
        for (int i = 0; i < 3; i++) {
            double bz = z0 + (z1 - z0) * (i + 0.5) / 3;
            put(m, "w" + i + "a", bench, facing, WIPE_A, top + WIPE_UP, bz);
            put(m, "w" + i + "b", bench, facing, WIPE_B, top + WIPE_UP, bz);
        }
        for (int i = 0; i < 2; i++) {
            double bz = z0 + (z1 - z0) * (i + 0.5) / 2;
            put(m, "o" + i + "a", bench, facing, WIPE_A, top + WIPE_UP, bz);
            put(m, "o" + i + "b", bench, facing, WIPE_B, top + WIPE_UP, bz);
        }
        return m;
    }

    private static void put(Map<String, Vec3> m, String name, BlockPos bench, Direction facing, double x, double y, double z) {
        m.put(name, Workbench.local(bench, facing, x, y, z));
    }

    /** The planks of the piece's wood (its boards' species), white oak when not known; null where none is registered. */
    @Nullable
    public static BlockState planks(@Nullable FurnitureWork.Job job) {
        ResourceLocation wood = job == null ? null : job.wood();
        Block b = Blocks.AIR;
        if (wood != null && wood.getPath().endsWith("_board"))
            b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(wood.getNamespace(), wood.getPath().replace("_board", "_planks")));
        if (b == null || b == Blocks.AIR) b = ForgeRegistries.BLOCKS.getValue(WHITE_OAK_PLANKS);
        return b == null || b == Blocks.AIR ? null : b.defaultBlockState();
    }

    // ------------------------------------------------------------------------------------------------ sounds and sawdust

    /**
     * Each worker's actions' events once a tick, as each viewer plays them: the sounds (at the work: the kerf, the can, the
     * piece, the nail or the hardware; their radius from the event, as the infected's noise the server sends), the sawdust
     * out of the kerf on each push and a burst as the saw goes through, the box opened at a step's first nail or hardware.
     */
    static void events(ClientLevel level) {
        var mc = Minecraft.getInstance();
        FIRED.keySet().removeIf(id -> !RUNS.containsKey(id));
        for (var e : RUNS.entrySet()) {
            if (!(level.getEntity(e.getKey()) instanceof Player p)) continue;
            Run r = e.getValue();
            BlockState state = level.getBlockState(r.bench);
            if (!(state.getBlock() instanceof Workbench)) continue;
            Direction facing = state.getValue(Workbench.FACING);
            long el = level.getGameTime() - Math.round(r.start);
            Fired f = FIRED.get(e.getKey());
            long last = f != null && f.run() == r ? f.tick() : el - 1;
            FIRED.put(e.getKey(), new Fired(r, el));
            if (el <= last) continue;
            WorkbenchWork.Session s = p == mc.player ? WorkbenchWork.ownSession() : null;
            int lv = s != null ? s.level : WorkbenchWork.carpentry();
            FurnitureWork.Job base = base(r.bench, s);
            List<FurnitureWork.Play> plays = r.plan.plays();
            long acc = 0;
            for (int i = 0; i < plays.size() && acc <= el; i++) {
                FurnitureWork.Play pl = plays.get(i);
                if (pl.ticks() <= 0) break;
                String action = shownAction(pl.action(), base);
                FurnitureWork.Mark mk = i == 0 && r.plan.from() != null && REACTIONS.contains(pl.action()) ? r.plan.from() : r.plan.to();
                FurnitureWork.Job job = base == null ? null : mk == null ? base : mk.on(base);
                double len = length(action), second = (el - acc) / (double) pl.ticks() * len;
                if (acc > last && acc <= el && job != null && job.rep() == 0 && job.sub() == 0
                        && (NAIL_SET.equals(action) || action.startsWith("furniture/fit_")))
                    sound(level, r.bench, facing, "furniture_box_open", new double[]{BOX[0], BOX[1] + 0.12, BOX[2]}, 2, 0.6F, 1F);
                PlayerAction a = PlayerActions.get(action);
                if (a != null && a.events != null) for (JsonElement je : a.events) {
                    JsonObject o = je.getAsJsonObject();
                    double atS = o.get("at").getAsDouble();
                    long at = acc + Math.round(atS / len * pl.ticks());
                    String type = o.has("type") ? o.get("type").getAsString() : "";
                    if (type.equals("dust") && o.has("until") && el >= at && el < acc + Math.round(o.get("until").getAsDouble() / len * pl.ticks()))
                        sawdust(level, r.bench, facing, job, second, false);
                    if (at <= last || at > el) continue;
                    if (type.equals("cut_through")) sawdust(level, r.bench, facing, job, atS, true);
                    if (o.has("sound")) sound(level, r.bench, facing, o.get("sound").getAsString(), soundAt(action, atS, job),
                            o.has("radius") ? o.get("radius").getAsDouble() : 4, o.has("volume") ? o.get("volume").getAsFloat() : 1F,
                            o.has("pitch") ? o.get("pitch").getAsFloat() : 1F);
                }
                acc += pl.ticks();
            }
        }
    }

    /** Where an action is heard: the kerf (the pile once the off-cut is carried), the can's mouth, the piece's middle, the nail or the hardware. */
    private static double[] soundAt(String action, double t, @Nullable FurnitureWork.Job job) {
        double[] ext = extent(job);
        if (action.equals(CROSSCUT)) return t >= CARRY_FROM ? new double[]{PILE_X, 0.95, PILE_Z} : new double[]{CUT_X, CUT_TOP, kerfZ(t)};
        if (action.equals(CAN_OPEN) || action.equals(CAN_CLOSE)) return mouth();
        if (action.equals(WIPE) || action.equals(WIPE_OFF)) return new double[]{(WIPE_A + WIPE_B) / 2, ext[0], (ext[1] + ext[2]) / 2};
        double[] at = workPoint(job);
        return at == null ? new double[]{1.2, ext[0], (ext[1] + ext[2]) / 2} : at;
    }

    private static void sound(ClientLevel level, BlockPos bench, Direction facing, String sound, double[] at, double radius, float volume, float pitch) {
        Vec3 w = Workbench.local(bench, facing, at[0], at[1], at[2]);
        RangedSoundInstance.play(new RangedSoundS2CPacket(new ResourceLocation(ApocalypseFirstLight.MOD_ID, sound), SoundSource.PLAYERS,
                w.x, w.y, w.z, (float) radius, volume, pitch, level.random.nextLong(), false));
    }

    /** Sawdust out of the bottom of the kerf as the saw pushes (falling past the bench's end), a burst of it and a chip or two as it goes through. */
    private static void sawdust(ClientLevel level, BlockPos bench, Direction facing, @Nullable FurnitureWork.Job job, double t, boolean through) {
        BlockState wood = planks(job);
        if (wood == null) return;
        RandomSource random = level.random;
        double kz = kerfZ(t);
        Vec3 o = Workbench.local(bench, facing, 0, 0, 0);
        int n = through ? 8 : random.nextFloat() < 0.5F ? 2 : 1;
        for (int i = 0; i < n; i++) {
            double z = through ? CUT_Z0 + random.nextDouble() * (CUT_Z1 - CUT_Z0) : CUT_Z0 + random.nextDouble() * Math.max(0.01, kz - CUT_Z0);
            Vec3 at = Workbench.local(bench, facing, CUT_X + (random.nextDouble() - 0.5) * 0.01, 0.9 - 0.003, z);
            Vec3 v = Workbench.local(bench, facing, (random.nextDouble() - 0.5) * (through ? 0.15 : 0.06), -0.05 - 0.08 * random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.1).subtract(o);
            WoodChipParticle.spawn(level, through && i < 2 ? WoodChipParticle.Kind.CHIP : WoodChipParticle.Kind.DUST, at, v, wood, through ? 0.6F : 1F);
        }
    }

    // ------------------------------------------------------------------------------------------------ the bar and the panel

    private static float zoneWidth(Player player, int level) {
        return FurnitureWork.zone(level, SurvivorStats.multiplier(player, AflAttributes.QTE_WINDOW, 0.1));
    }

    private static float nextZone(float width) {
        float lo = width / 2 + 0.08F, hi = 1 - width / 2 - 0.08F;
        return lo + (hi - lo) * (float) Math.random();
    }

    /** The marker on the bar (0..1), sweeping back and forth from the bar's start (the plan's actions played). */
    private static float marker(Run r, float partial) {
        double phase = ((time(partial) - r.start - r.plan.ticks()) % PERIOD + PERIOD) % PERIOD / PERIOD;
        return (float) (phase < 0.5 ? phase * 2 : 2 - phase * 2);
    }

    /**
     * Over the hotbar: the piece, which of how many, the step and how far it is, the key that stops; under the crosshair,
     * while the nail or screw waits, the timing bar (WorkbenchWork's overlay draws this for a furniture session).
     */
    static void panel(GuiGraphics g, WorkbenchWork.Session s, float partial, int screenWidth, int screenHeight) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Run r = RUNS.get(mc.player.getId());
        FurnitureWork.Job job = base(s.bench, s);
        Frame f = r == null ? null : frame(r, time(partial), job);
        if (r != null && f != null && !CLOSING.equals(r.plan.qte())) job = shown(r, f, s.bench, job);
        var font = mc.font;
        long nanos = System.nanoTime();
        Component title = Component.translatable("hud.apocalypse_firstlight.workbench.work", s.recipe.result().getHoverName(),
                Math.min(s.done + 1, s.quantity), s.quantity);
        Component step = job == null || job.complete() ? Component.empty() : WorkbenchWork.stepLine(job, s.level);
        Component stopText = Component.translatable("hud.apocalypse_firstlight.workbench.stop");
        AflKeyHint cap = AflKeyHint.MOUSE_LEFT;
        float keyW = cap.width(font) + 3 + font.width(stopText);
        float w = Math.max(Math.max(14 + font.width(title), keyW), font.width(step)) + 12, h = 6 + 12 + 11 + 6 + 14 + 3;
        float x = screenWidth / 2F - w / 2, y = screenHeight - 77 - h;
        AflUiDraw.panel(g, x, y, w, h, 1);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 6, y + 4, 0);
        pose.scale(0.75F, 0.75F, 1);
        g.renderItem(s.recipe.result(), 0, 0);
        pose.popPose();
        AflUiDraw.text(g, font, title, x + 20, y + 6, AflUiStyle.TEXT, 1);
        AflUiDraw.text(g, font, step, x + 6, y + 18, AflUiStyle.TEXT_DIM, 1);
        float progress = job == null ? 1 : FurnitureWork.progress(job, s.level);
        float by = y + 30, bw = w - 12;
        AflUiShapes.fill(g, x + 6, by, bw, 2, 1, AflUiStyle.LINE, 0.18F);
        if (progress > 0) AflUiShapes.fill(g, x + 6, by, bw * progress, 2, 1, AflUiStyle.ACCENT, 1);
        float ky = by + 6;
        cap.draw(g, font, x + 6, ky, 1, nanos);
        AflUiDraw.text(g, font, stopText, x + 6 + cap.width(font) + 3, ky + 2, AflUiStyle.TEXT, 1);
        if (r != null && r.plan.waits() == FurnitureWork.Wait.PRESS) {
            double since = time(partial) - r.start - r.plan.ticks();
            if (since >= 0) bar(g, s, r, partial, screenWidth, screenHeight, (float) Math.min(1, since / 4));
        }
    }

    /** The timing bar (as the generator's pull's): the zone, the sweeping marker (green or red a moment after a press), the keys. */
    private static void bar(GuiGraphics g, WorkbenchWork.Session s, Run r, float partial, int screenWidth, int screenHeight, float a) {
        var mc = Minecraft.getInstance();
        float W = 150, H = 7, cx = screenWidth / 2F, y = screenHeight / 2F + 34;
        AflUiDraw.panel(g, cx - W / 2 - 6, y - 6, W + 12, H + 26, a);
        AflUiShapes.fill(g, cx - W / 2, y, W, H, 2.5F, 0x2A2C2F, 0.9F * a);
        float zw = zoneWidth(mc.player, s.level);
        AflUiShapes.fill(g, cx - W / 2 + (zone - zw / 2) * W, y, zw * W, H, 2.5F, 0x7FB061, 0.85F * a);
        float m = marker(r, partial);
        int markRgb = time(partial) - pressedAt < 10 ? (pressedGood ? 0xB6F08E : 0xE07A5F) : 0xF2EEE6;
        AflUiShapes.fill(g, cx - W / 2 + m * W - 1.5F, y - 3, 3, H + 6, 1.5F, markRgb, a);
        String qte = "nail".equals(r.plan.qte()) ? "nail" : "screw";
        Component keys = Component.translatable("hud.apocalypse_firstlight.workbench.qte." + qte,
                mc.options.keyUse.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage());
        AflUiDraw.centred(g, mc.font, keys, cx, y + H + 6, AflUiStyle.TEXT_DIM, a);
    }

    /** Whether the board is still on the bench (the crosscut's steps), for the renderer's pile. */
    public static boolean board(FurnitureWork.Job job) {
        return pieceAt(job, Math.min(job.step(), job.steps().size() - 1)).equals(BOARD);
    }
}
