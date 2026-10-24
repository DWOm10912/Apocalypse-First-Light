package com.antaurora.apofirstlight.client.workbench;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.building.BuildingPackets;
import com.antaurora.apofirstlight.cabin.FurnitureWork;
import com.antaurora.apofirstlight.cabin.Workbench;
import com.antaurora.apofirstlight.cabin.WorkbenchBlockEntity;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.client.RangedSoundInstance;
import com.antaurora.apofirstlight.client.WoodChipParticle;
import com.antaurora.apofirstlight.client.survivor.SurvivorSheetClient;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.network.RangedSoundS2CPacket;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Work at the workbench (docs/gameplay/afl_workbench_v1_design.md 17): a recipe made at the bench (WorkbenchRecipeView#work)
 * is not made on the page. One click on 制作 closes the page and the player works on the top until the items are made: a
 * white oak log lies on the top over the drawers (WorkbenchRenderer, {@link #workpiece}), the body steps to it and saws it
 * (the skeleton action {@code tool/hand_saw_set}, then the loop {@code tool/hand_saw_rip}, at {@link #at}'s anchors), the
 * kerf runs into the log as the item comes on, sawdust falls (client/WoodChipParticle); a panel over the hotbar shows the
 * item, how many and the progress. Moving, jumping, a left click, opening any screen, leaving the bench, no tool or no
 * materials stop it.
 * <p>
 * The wooden knob ({@link #KNOB}, 2026-10-22) is made otherwise: a bench hook at the bench's right end with a strip on it,
 * one 10 s loop per knob (tools/author-wooden-knob-actions.mjs): the end sawn off, whittled with the folding knife, looked
 * at, blown clean and set down. Its sounds and chips come at the action's events ({@link #knob}).
 * <p>
 * The own work is kept here and told to the server (BuildingPackets.Work), which tells the players who see this one
 * (BuildingPackets.WorkOf, {@link #remote}): they see the same action, log and sawdust. The anchors' numbers are those of
 * tools/author-hand-saw-actions.mjs WORK (which checks the reach and the clearances offline).
 * <p>
 * The furniture (2026-10-22, docs/gameplay/furniture_bench_work_v1.md) is played by FurnitureWorkClient: the server's plans
 * one action after another, the nails' and screws' timing bar (the use key), Shift or a left click to stop; here only the
 * session, its stop checks and the page's lines about a half-built piece ({@link #benchJob}, {@link #stepLine}).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WorkbenchWork {
    // ---- the work on the top (blocks, the bench's north frame: the master cell's corner, the player at z < 0 facing +z, +x
    // the player's left); tools/author-hand-saw-actions.mjs WORK
    /** The log: across the master cell's middle, half its width, from its front end (over the edge) to its far end. */
    public static final double LOG_X = 0.5, LOG_R = 0.09, TOP = 0.9, LOG_END = -0.30, LOG_FAR = 0.54;
    /** The kerf on the log's top: from just in at the front end to the bench's front edge as an item is made. */
    public static final double KERF_FROM = -0.27, KERF_TO = -0.02;
    private static final double STAND_LEFT = 0.36, STAND_BACK = 0.42, YAW = -15;
    private static final double HOLD_LEFT = 0.13, HOLD_BACK = 0.1, HOLD_UP = 0.15;
    /** tool/hand_saw_set's length (s): the strokes start after it. */
    private static final double SET = 0.7, STROKE = 0.8;
    /** Each loop action and the one played once before it. */
    private static final Map<String, String> STARTS = Map.of("tool/hand_saw_rip", "tool/hand_saw_set");
    /** How far from the stand point a work may start (the action's approach steps at most 2 blocks), and may drift. */
    private static final double START_REACH = 1.8, DRIFT = 0.25;
    private static final ResourceLocation LOG = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "white_oak_log");

    // ---- the wooden knob (blocks, the same frame; tools/author-wooden-knob-actions.mjs WORK)
    public static final String KNOB = WorkbenchCrafting.KNOB_WORK;
    /** One real cm of the knob's work mesh in the world (its cm, the props' 1.35, the model's 0.9375); the mesh's own scale. */
    public static final double KNOB_CM = 0.0196850394 * 1.35 * 0.9375, KNOB_MESH_SCALE = 1.35 * 0.9375;
    /** The hook board's top, the saw's plane, the strip on the hook (its half, its middle z against the back cleat, its top). */
    public static final double KNOB_HOOK_TOP = 0.92, KNOB_CUT_X = 0.10, KNOB_HALF = 1.6 * KNOB_CM, KNOB_Z = 0.21 - KNOB_HALF,
            KNOB_TOP = KNOB_HOOK_TOP + 2 * KNOB_HALF, KNOB_KERF_END = KNOB_HOOK_TOP - 0.004;
    private static final double KNOB_STAND_LEFT = 0.36, KNOB_STAND_BACK = 0.34, KNOB_HOLD_LEFT = 0.13, KNOB_HOLD_UP = 0.03;
    private static final double KNOB_AHEAD = 0.30, KNOB_UP = 1.05, KNOB_SET_X = 0.56, KNOB_SET_Z = 0.10;
    /** The loop (s), when the end falls off, and the saw's strokes (from, to). */
    public static final double KNOB_LOOP = 10, KNOB_CUT_THROUGH = 2.7, KNOB_SAW_FROM = 0.7;
    /** Model px a block (the action's event positions: model px of the body at the stand, from the carve anchor). */
    private static final double PX = 16 / 0.9375;

    /** Where the furniture's work starts from (the bench's frame; FurnitureBench's stances are within reach of it). */
    private static final double FURNITURE_X = 0.78, FURNITURE_Z = -0.13, FURNITURE_REACH = 1.4;

    /**
     * What a player's skeleton plays now (SkeletonActions): the action, when it started (s, game time), its anchors (world),
     * how fast its clock runs (the furniture's nails and screws by the carpentry level), whether it follows the last one
     * without a blend.
     */
    public record At(String action, double started, Map<String, Vec3> anchors, float speed, boolean chain) {
        public At(String action, double started, Map<String, Vec3> anchors) {
            this(action, started, anchors, 1F, false);
        }
    }

    static final class Session {
        BlockPos bench;
        WorkbenchRecipeView recipe;
        int quantity, done;
        long started, itemStarted;
        Vec3 at;
        /** A furniture piece (FurnitureWorkClient plays it), and the carpentry level it is worked at. */
        boolean furniture;
        int level;
    }

    @Nullable private static Session own;
    /** Other players at work (entity id → what and since when, game time). */
    private static final Map<Integer, Remote> REMOTE = new HashMap<>();

    private record Remote(WorkbenchCrafting.Working work, long started) {}

    private WorkbenchWork() {}

    // ------------------------------------------------------------------------------------------------ own work

    /**
     * The page's 制作 on a recipe made at the bench: close the page and start (or say where to stand). {@code discard}: the
     * other recipe's half-built piece on the bench is thrown away (the page asked twice).
     */
    public static void start(BlockPos bench, WorkbenchRecipeView recipe, int quantity, boolean discard) {
        var mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || quantity < 1) return;
        BlockState state = mc.level.getBlockState(bench);
        if (!(state.getBlock() instanceof Workbench)) return;
        Direction facing = state.getValue(Workbench.FACING);
        boolean furniture = recipe.furniture();
        Vec3 stand = furniture ? Workbench.local(bench, facing, FURNITURE_X, 0, FURNITURE_Z)
                : KNOB.equals(recipe.work()) ? Workbench.local(bench, facing, KNOB_CUT_X + KNOB_STAND_LEFT, 0, KNOB_Z - KNOB_STAND_BACK)
                : Workbench.local(bench, facing, LOG_X + STAND_LEFT, 0, KERF_FROM - STAND_BACK);
        Vec3 front = Workbench.local(bench, facing, 1, 0, 0.04);
        Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 p = player.position();
        if (p.subtract(front).dot(out) < 0 || Math.hypot(p.x - stand.x, p.z - stand.z) > (furniture ? FURNITURE_REACH : START_REACH)) {
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.workbench.stand_front"), true);
            return;
        }
        mc.setScreen(null);
        Session s = new Session();
        s.bench = bench.immutable();
        s.recipe = recipe;
        s.quantity = quantity;
        s.started = mc.level.getGameTime();
        s.itemStarted = s.started + (STARTS.containsKey(recipe.work()) ? Math.round(SET * 20) : 0);
        s.at = p;
        s.furniture = furniture;
        s.level = carpentry();
        own = s;
        AflNetwork.buildingToServer(new BuildingPackets.Work(s.bench, recipe.id(), true, discard));
        if (furniture) FurnitureWorkClient.started(player);
    }

    /** Working at a bench now (the building hammer's panel keeps out of the way). */
    public static boolean active() { return own != null; }

    @Nullable
    static Session ownSession() { return own; }

    private static void stop() {
        if (own == null) return;
        AflNetwork.buildingToServer(new BuildingPackets.Work(own.bench, own.recipe.id(), false, false));
        if (own.furniture && Minecraft.getInstance().player != null) FurnitureWorkClient.stopped(Minecraft.getInstance().player);
        own = null;
    }

    /** FurnitureWorkClient: the work is over (the can shut, or the bar left alone). */
    static void stopOwn() { stop(); }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            own = null;
            REMOTE.clear();
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            if (own == null) return;
            // the hands are busy: a left click stops the work, a right click does nothing (no block broken or used meanwhile)
            // (at the furniture the right click is the nail's blow or the screw's turn on the bar)
            boolean attack = false, use = false;
            while (mc.options.keyAttack.consumeClick()) attack = true;
            while (mc.options.keyUse.consumeClick()) use = true;
            if (attack) stop();
            else if (use && own.furniture) FurnitureWorkClient.press(own, mc.player);
            return;
        }
        REMOTE.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player));
        dust(mc.level);
        strokes(mc.level);
        knob(mc.level);
        FurnitureWorkClient.events(mc.level);
        Session s = own;
        if (s == null) return;
        Player player = mc.player;
        var input = mc.player.input;
        if (!player.isAlive() || player.isSpectator() || mc.screen != null || !WorkbenchClient.atBench(player, s.bench)
                || player.position().distanceTo(s.at) > DRIFT || s.furniture && mc.options.keyShift.isDown()
                || Math.abs(input.forwardImpulse) > 0.01F || Math.abs(input.leftImpulse) > 0.01F || input.jumping) {
            stop();
            return;
        }
        if (!WorkbenchRecipes.toolPresent(player, s.recipe)) {
            ItemStack missing = WorkbenchRecipes.missingTool(player, s.recipe);
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.workbench.no_tool",
                    missing == null ? Component.empty() : missing.getHoverName()), true);
            stop();
            return;
        }
        if (s.furniture) {
            FurnitureWorkClient.tick(s, player);
            return;
        }
        long now = mc.level.getGameTime();
        if (now - s.itemStarted < s.recipe.ticks()) return;
        AflNetwork.buildingToServer(new BuildingPackets.Craft(s.bench, s.recipe.id()));
        s.done++;
        s.itemStarted = now;
        // the next one: the server's word on the last may still be on its way, so one more than is carried now
        if (s.done >= s.quantity || WorkbenchRecipes.craftable(player, s.recipe) < 2) stop();
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        own = null;
        REMOTE.clear();
        HALVES.clear();
        KNOB_TICKS.clear();
        WorkbenchRecipes.clear();
        FurnitureWorkClient.clear();
    }

    // ------------------------------------------------------------------------------------------------ others' work

    /**
     * BuildingPackets.WorkOf: a player started (work) or stopped (null) working at a bench. At the furniture the own player
     * gets it too: each plan the server makes of a word (FurnitureWorkClient), and the end when the server stops the work.
     */
    public static void remote(int entity, @Nullable WorkbenchCrafting.Working work) {
        var mc = Minecraft.getInstance();
        boolean furniture = work != null && FurnitureWork.queue(work.action());
        if (mc.player != null && entity == mc.player.getId() && own != null && own.furniture) {
            if (furniture) FurnitureWorkClient.own(mc.player, work);
            else if (work == null) {
                FurnitureWorkClient.stopped(mc.player);
                own = null;
            }
            return;
        }
        FurnitureWorkClient.remote(entity, work);
        if (work == null || mc.level == null || furniture) REMOTE.remove(entity);
        else REMOTE.put(entity, new Remote(work, mc.level.getGameTime()));
    }

    // ------------------------------------------------------------------------------------------------ the furniture on the page

    /** The half-built piece on a bench (its block entity's job), or null. */
    @Nullable
    public static FurnitureWork.Job benchJob(BlockPos bench) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        BlockPos master = Workbench.master(mc.level.getBlockState(bench), bench);
        return mc.level.getBlockEntity(master) instanceof WorkbenchBlockEntity be ? be.job() : null;
    }

    /** A piece's name: its recipe's result when this client knows the recipe, else the item named as the recipe. */
    public static Component jobName(FurnitureWork.Job job) {
        var mc = Minecraft.getInstance();
        WorkbenchRecipeView view = mc.player == null ? null : WorkbenchRecipes.find(mc.player, job.recipe());
        if (view != null) return view.result().getHoverName();
        String path = job.recipe().getPath();
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(job.recipe().getNamespace(), path.substring(path.lastIndexOf('/') + 1)));
        return item == null || item == net.minecraft.world.item.Items.AIR ? Component.literal(path) : new ItemStack(item).getHoverName();
    }

    /** A piece's step as the panel and the page say it: its name, and which nail, screw or time of how many. */
    public static Component stepLine(FurnitureWork.Job job, int level) {
        FurnitureWork.Step step = job.current();
        if (step == null) return Component.empty();
        String name = step.step();
        Component label = Component.translatable("hud.apocalypse_firstlight.workbench.step." + name.substring(name.indexOf('/') + 1));
        FurnitureWork.Kind kind = FurnitureWork.kind(name);
        int n, all;
        if (kind == FurnitureWork.Kind.FIT) {
            int screws = FurnitureWork.screws(name);
            n = job.rep() * screws + job.sub() + 1;
            all = step.times() * screws;
        } else {
            n = job.rep() + 1;
            all = step.times();
        }
        if (kind == FurnitureWork.Kind.TIMED && all <= 1) return label;
        return Component.translatable("hud.apocalypse_firstlight.workbench.step_count", label, Math.min(n, all), all);
    }

    /** The own carpentry level (survivor_growth/carpentry.json). */
    public static int carpentry() {
        return Math.max(0, SurvivorSheetClient.growthLevel(FurnitureWork.GROWTH));
    }

    // ------------------------------------------------------------------------------------------------ looks

    /**
     * A player's work as the looks need it: the bench, its facing, the loop action, the start (game time), the item's progress,
     * the recipe (the log on the bench top is its log), the seconds since the start.
     */
    private record Job(BlockPos bench, Direction facing, String work, long started, float progress, ResourceLocation recipe, double seconds) {
        boolean knob() { return KNOB.equals(work); }

        /** The knob's second in its loop. */
        double cycle() { return seconds % KNOB_LOOP; }

        /** A point of the bench's north frame in the world. */
        Vec3 at(double x, double y, double z) { return Workbench.local(bench, facing, x, y, z); }

        /** A direction of the bench's north frame in the world. */
        Vec3 dir(double x, double y, double z) { return at(x, y, z).subtract(at(0, 0, 0)); }
    }

    @Nullable
    private static Job job(Player p, float partial) {
        Level level = p.level();
        BlockPos bench;
        String work;
        long started;
        float progress;
        ResourceLocation recipe;
        double now = level.getGameTime() + partial;
        Session s = own;
        if (p == Minecraft.getInstance().player) {
            if (s == null || s.recipe.work() == null || s.furniture) return null;
            bench = s.bench;
            work = s.recipe.work();
            recipe = s.recipe.id();
            started = s.started;
            progress = (float) Math.max(0, Math.min(1, (now - s.itemStarted) / Math.max(1, s.recipe.ticks())));
        } else {
            Remote r = REMOTE.get(p.getId());
            if (r == null) return null;
            // the action and time come with WorkOf: whoever watches may not know the recipe
            bench = r.work().bench();
            work = r.work().action();
            recipe = r.work().recipe();
            started = r.started();
            // the others' item clock is not sent: the recipe's time, round and round from the first stroke
            double since = now - started - (STARTS.containsKey(work) ? SET * 20 : 0);
            int ticks = Math.max(1, r.work().ticks());
            progress = since < 0 ? 0 : (float) (since % ticks / ticks);
        }
        BlockState state = level.getBlockState(bench);
        if (!(state.getBlock() instanceof Workbench)) return null;
        return new Job(bench, state.getValue(Workbench.FACING), work, started, progress, recipe, Math.max(0, (now - started) / 20.0));
    }

    /** SkeletonActions: what this player plays at a bench now, or null. */
    @Nullable
    public static At at(Player p, float partial) {
        At furniture = FurnitureWorkClient.at(p, partial);
        if (furniture != null) return furniture;
        Job j = job(p, partial);
        if (j == null) return null;
        double now = (p.level().getGameTime() + partial) / 20.0, t0 = j.started() / 20.0;
        String first = STARTS.get(j.work());
        boolean opening = first != null && now - t0 < SET;
        return new At(opening ? first : j.work(), opening || first == null ? t0 : t0 + SET, anchors(j));
    }

    private static Map<String, Vec3> anchors(Job j) {
        if (j.knob()) return knobAnchors(j);
        double kerf = kerfZ(j.progress());
        double yaw = Math.toRadians(YAW);
        double sx = LOG_X + STAND_LEFT, sz = kerf - STAND_BACK;
        return Map.of(
                "stand", Workbench.local(j.bench(), j.facing(), sx, 0, sz),
                // the body faces YAW to the left of the cut (+z; +x is the player's left)
                "face", Workbench.local(j.bench(), j.facing(), sx + 3 * Math.sin(yaw), 0, sz + 3 * Math.cos(yaw)),
                "kerf", Workbench.local(j.bench(), j.facing(), LOG_X, TOP + 2 * LOG_R, kerf),
                "hold", Workbench.local(j.bench(), j.facing(), LOG_X + HOLD_LEFT, TOP + HOLD_UP, Math.max(kerf - HOLD_BACK, LOG_END + 0.03)));
    }

    public static double kerfZ(float progress) {
        return KERF_FROM + (KERF_TO - KERF_FROM) * progress;
    }

    /** WorkbenchRenderer: the progress of the item being made on this bench's top (0..1; the log and its kerf), or -1. */
    public static float workpiece(BlockPos master, float partial) {
        Job j = jobAt(master, partial);
        return j == null || j.knob() ? -1 : j.progress();
    }

    /** WorkbenchRenderer: the wooden knob's second in its loop at this bench (the hook, the strip, its kerf), or -1. */
    public static double knobCycle(BlockPos master, float partial) {
        Job j = jobAt(master, partial);
        return j == null || !j.knob() ? -1 : j.cycle();
    }

    // ------------------------------------------------------------------------------------------------ the wooden knob

    /** The stand point (x, z): off the cut, as the rip's, closer (the strip is low). */
    private static double[] knobStand() {
        return new double[]{KNOB_CUT_X + KNOB_STAND_LEFT, KNOB_Z - KNOB_STAND_BACK};
    }

    /** The blank in the hands while whittling: ahead of the stand point along the body's facing, at chest height. */
    private static double[] knobCarve() {
        double[] st = knobStand();
        double yaw = Math.toRadians(YAW);
        return new double[]{st[0] + KNOB_AHEAD * Math.sin(yaw), KNOB_UP, st[1] + KNOB_AHEAD * Math.cos(yaw)};
    }

    /** The kerf's depth by the loop's second: down through the strip over the three strokes. */
    public static double knobKerfY(double t) {
        return KNOB_TOP - (KNOB_TOP - KNOB_KERF_END) * Math.max(0, Math.min(1, (t - KNOB_SAW_FROM) / (KNOB_CUT_THROUGH - KNOB_SAW_FROM)));
    }

    private static Map<String, Vec3> knobAnchors(Job j) {
        double[] st = knobStand(), carve = knobCarve();
        double yaw = Math.toRadians(YAW);
        return Map.of(
                "stand", j.at(st[0], 0, st[1]),
                "face", j.at(st[0] + 3 * Math.sin(yaw), 0, st[1] + 3 * Math.cos(yaw)),
                "kerf", j.at(KNOB_CUT_X, knobKerfY(j.cycle()), KNOB_Z),
                "hold", j.at(KNOB_CUT_X + KNOB_HOLD_LEFT, KNOB_TOP + KNOB_HOLD_UP, KNOB_Z),
                "offcut", j.at(KNOB_CUT_X - 2.1 * KNOB_CM, TOP + KNOB_HALF, KNOB_Z),
                "carve", j.at(carve[0], carve[1], carve[2]),
                "set", j.at(KNOB_SET_X, TOP + 1.5 * KNOB_CM, KNOB_SET_Z));
    }

    /** An event's model px (x left, y down, z back, of the body at the stand facing YAW) as a direction of the bench's frame. */
    private static double[] knobDir(double[] m) {
        double yaw = Math.toRadians(YAW), ys = Math.sin(yaw), yc = Math.cos(yaw);
        double left = m[0] / PX, ahead = -m[2] / PX;
        return new double[]{left * yc + ahead * ys, -m[1] / PX, -left * ys + ahead * yc};
    }

    /** An event's model px from the carve anchor as a point of the bench's frame. */
    private static double[] knobPoint(double[] m) {
        double[] c = knobCarve(), d = knobDir(m);
        return new double[]{c[0] + d[0], c[1] + d[1], c[2] + d[2]};
    }

    /** One of the action's events: its ticks into the loop (until: the end of a stroke or cut), and its numbers. */
    private record KnobEvent(String type, int at, int until, @Nullable double[] pos, @Nullable double[] from, @Nullable double[] to,
                             @Nullable double[] dir, @Nullable double[] out, String phase) {}

    @Nullable private static List<KnobEvent> knobEvents;

    private static List<KnobEvent> knobEvents() {
        if (knobEvents != null) return knobEvents;
        List<KnobEvent> list = new ArrayList<>();
        PlayerAction action = PlayerActions.get(KNOB);
        if (action != null && action.events != null) for (JsonElement e : action.events) {
            JsonObject o = e.getAsJsonObject();
            int at = (int) Math.round(o.get("at").getAsDouble() * 20);
            int until = o.has("until") ? (int) Math.round(o.get("until").getAsDouble() * 20) : at;
            list.add(new KnobEvent(o.get("type").getAsString(), at, until, vec(o, "pos"), vec(o, "from"), vec(o, "to"), vec(o, "dir"),
                    vec(o, "out"), o.has("phase") ? o.get("phase").getAsString() : ""));
        }
        return knobEvents = list;
    }

    @Nullable
    private static double[] vec(JsonObject o, String key) {
        if (!o.has(key)) return null;
        var a = o.getAsJsonArray(key);
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    /** Each knob worker's last loop tick played (entity id → ticks since the start). */
    private static final Map<Integer, Long> KNOB_TICKS = new HashMap<>();

    /**
     * The knob's sounds and wood in step with the action each viewer sees, once a tick: the saw's push and pull (as far as
     * the rip's, WorkbenchCrafting.SAW_RADIUS) with sawdust out of the kerf, a few crumbs as the end falls off, each knife cut
     * (close by, WorkbenchCrafting.WHITTLE_RADIUS) with a little dust along it and its shaving or chips flicked off at its
     * end on a parabola, the end and the knob set down on the top, the dust blown off the finished knob.
     */
    private static void knob(Level level) {
        var mc = Minecraft.getInstance();
        if (!(level instanceof ClientLevel client)) return;
        List<Player> working = new ArrayList<>();
        if (own != null && mc.player != null) working.add(mc.player);
        for (int id : REMOTE.keySet()) if (level.getEntity(id) instanceof Player p) working.add(p);
        KNOB_TICKS.keySet().removeIf(id -> working.stream().noneMatch(p -> p.getId() == id));
        RandomSource random = level.random;
        for (Player p : working) {
            Job j = job(p, 0);
            if (j == null || !j.knob()) continue;
            long ticks = level.getGameTime() - j.started();
            Long last = KNOB_TICKS.put(p.getId(), ticks);
            if (last != null && last == ticks) continue;
            int t = (int) (ticks % Math.round(KNOB_LOOP * 20));
            double second = t / 20.0;
            BlockState wood = logState(j.recipe());
            // the saw: dust out of the bottom of the kerf, more on the push
            if (second >= KNOB_SAW_FROM && second < KNOB_CUT_THROUGH && wood != null) {
                boolean push = false;
                for (KnobEvent e : knobEvents()) if (e.type().equals("saw_push") && t >= e.at() && t < e.until()) push = true;
                int n = push ? 2 : random.nextFloat() < 0.35F ? 1 : 0;
                double y = knobKerfY(second);
                for (int i = 0; i < n; i++) {
                    Vec3 at = j.at(KNOB_CUT_X + (random.nextDouble() - 0.5) * 0.006, y - 0.003, KNOB_Z + (random.nextDouble() - 0.5) * 1.8 * KNOB_HALF);
                    WoodChipParticle.spawn(client, WoodChipParticle.Kind.DUST, at,
                            j.dir((random.nextDouble() - 0.5) * 0.08, -0.06 - 0.06 * random.nextDouble(), (random.nextDouble() - 0.5) * 0.2), wood, 1);
                }
            }
            for (KnobEvent e : knobEvents()) {
                if (e.type().equals("whittle") && t > e.at() && t < e.until() && e.from() != null && e.to() != null && wood != null
                        && random.nextFloat() < 0.5F) {
                    // a speck now and then off the edge as it cuts
                    double u = (t - e.at()) / (double) Math.max(1, e.until() - e.at());
                    double[] m = {e.from()[0] + (e.to()[0] - e.from()[0]) * u, e.from()[1] + (e.to()[1] - e.from()[1]) * u,
                            e.from()[2] + (e.to()[2] - e.from()[2]) * u};
                    double[] at = knobPoint(m);
                    Vec3 v = cutVelocity(j, e, 0.2, 0.15, random);
                    WoodChipParticle.spawn(client, WoodChipParticle.Kind.DUST, j.at(at[0], at[1], at[2]), v, wood, 1);
                }
                if (e.type().equals("whittle") && t == e.until() && e.to() != null && wood != null) shavings(client, j, e, wood, random);
                if (t != e.at()) continue;
                switch (e.type()) {
                    case "saw_push", "saw_pull" -> {
                        Vec3 at = j.at(KNOB_CUT_X, knobKerfY(second), KNOB_Z);
                        sound(level, e.type().equals("saw_push") ? AflSounds.HAND_SAW_PUSH.get() : AflSounds.HAND_SAW_PULL.get(), at,
                                WorkbenchCrafting.SAW_RADIUS, 1F, 1F);
                    }
                    case "cut_through" -> {
                        if (wood == null) break;
                        for (int i = 0; i < 8; i++) {
                            Vec3 at = j.at(KNOB_CUT_X + (random.nextDouble() - 0.5) * 0.01, KNOB_KERF_END, KNOB_Z + (random.nextDouble() - 0.5) * 2 * KNOB_HALF);
                            WoodChipParticle.spawn(client, i < 2 ? WoodChipParticle.Kind.CHIP : WoodChipParticle.Kind.DUST, at,
                                    j.dir((random.nextDouble() - 0.5) * 0.15, -0.05, (random.nextDouble() - 0.5) * 0.15), wood, 0.6F);
                        }
                    }
                    case "whittle" -> {
                        if (e.from() == null) break;
                        double[] at = knobPoint(e.from());
                        sound(level, AflSounds.FOLDING_KNIFE_WHITTLE.get(), j.at(at[0], at[1], at[2]), WorkbenchCrafting.WHITTLE_RADIUS,
                                0.8F, 0.94F + 0.12F * random.nextFloat());
                    }
                    case "drop", "set" -> {
                        if (e.pos() == null) break;
                        double[] at = knobPoint(e.pos());
                        boolean drop = e.type().equals("drop");
                        sound(level, AflSounds.WORKBENCH_TOOL_DOWN.get(), j.at(at[0], at[1], at[2]), WorkbenchCrafting.WHITTLE_RADIUS,
                                drop ? 0.3F : 0.45F, drop ? 1.7F : 1.35F);
                    }
                    case "blow" -> {
                        if (e.pos() == null || wood == null) break;
                        // the breath across the knob: its dust and a crumb or two away from the face, slowing fast
                        double[] at = knobPoint(e.pos());
                        double yaw = Math.toRadians(YAW);
                        for (int i = 0; i < 14; i++) {
                            double speed = 0.5 + 0.7 * random.nextDouble();
                            Vec3 v = j.dir(Math.sin(yaw) * speed + (random.nextDouble() - 0.5) * 0.35, -0.08 + (random.nextDouble() - 0.5) * 0.25,
                                    Math.cos(yaw) * speed + (random.nextDouble() - 0.5) * 0.35);
                            WoodChipParticle.spawn(client, i < 2 ? WoodChipParticle.Kind.CHIP : WoodChipParticle.Kind.DUST,
                                    j.at(at[0] + (random.nextDouble() - 0.5) * 0.03, at[1] + (random.nextDouble() - 0.5) * 0.03, at[2] + (random.nextDouble() - 0.5) * 0.03),
                                    v, wood, i < 2 ? 0.5F : 1F);
                        }
                    }
                    default -> { }
                }
            }
        }
    }

    /**
     * What a cut flicks off at its end, from the edge on a parabola: along the cut and out of the wood, a little up. The corners
     * come off as long slivers with a flake, the rounding as curled shavings, the neck as small chips, the tenon as short curls.
     */
    private static void shavings(ClientLevel level, Job j, KnobEvent e, BlockState wood, RandomSource random) {
        double[] at = knobPoint(e.to());
        Vec3 from = j.at(at[0], at[1], at[2]);
        switch (e.phase()) {
            case "corners" -> {
                WoodChipParticle.spawn(level, WoodChipParticle.Kind.SHAVING, from, cutVelocity(j, e, 1.1, 0.6, random), wood, 1.1F);
                WoodChipParticle.spawn(level, WoodChipParticle.Kind.CHIP, from, cutVelocity(j, e, 0.8, 0.5, random), wood, 1F);
            }
            case "neck" -> {
                for (int i = 0; i < 2; i++)
                    WoodChipParticle.spawn(level, WoodChipParticle.Kind.CHIP, from, cutVelocity(j, e, 0.7, 0.5, random), wood, 0.7F);
            }
            case "tenon" -> WoodChipParticle.spawn(level, WoodChipParticle.Kind.SHAVING, from, cutVelocity(j, e, 0.9, 0.5, random), wood, 0.6F);
            default -> WoodChipParticle.spawn(level, WoodChipParticle.Kind.SHAVING, from, cutVelocity(j, e, 1.0, 0.55, random), wood, 0.85F);
        }
        WoodChipParticle.spawn(level, WoodChipParticle.Kind.DUST, from, cutVelocity(j, e, 0.5, 0.3, random), wood, 1);
    }

    /** Off a cut (blocks a second): along it ({@code along}), out of the wood ({@code out}), a little up, a little scatter. */
    private static Vec3 cutVelocity(Job j, KnobEvent e, double along, double out, RandomSource random) {
        double[] d = e.dir() == null ? new double[3] : knobDir(e.dir()), n = e.out() == null ? new double[3] : knobDir(e.out());
        // knobDir turns model px into blocks: the event's unit vectors back to unit length
        double dl = Math.max(1e-6, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])), nl = Math.max(1e-6, Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]));
        double k = 0.8 + 0.4 * random.nextDouble();
        return j.dir(k * (along * d[0] / dl + out * n[0] / nl) + (random.nextDouble() - 0.5) * 0.25,
                k * (along * d[1] / dl + out * n[1] / nl) + 0.35 + 0.2 * random.nextDouble(),
                k * (along * d[2] / dl + out * n[2] / nl) + (random.nextDouble() - 0.5) * 0.25);
    }

    private static void sound(Level level, net.minecraft.sounds.SoundEvent sound, Vec3 at, double radius, float volume, float pitch) {
        RangedSoundInstance.play(new RangedSoundS2CPacket(sound.getLocation(), SoundSource.PLAYERS, at.x, at.y, at.z, (float) radius,
                volume, pitch, level.random.nextLong(), false));
    }

    /** WorkbenchRenderer: the log being sawn on this bench's top (#logState of its recipe), or null. */
    @Nullable
    public static BlockState workLog(BlockPos master, float partial) {
        Job j = jobAt(master, partial);
        return j == null ? null : logState(j.recipe());
    }

    @Nullable
    private static Job jobAt(BlockPos master, float partial) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (own != null && own.bench.equals(master) && mc.player != null) {
            Job j = job(mc.player, partial);
            if (j != null) return j;
        }
        for (var e : REMOTE.entrySet()) {
            if (!e.getValue().work().bench().equals(master)) continue;
            if (mc.level.getEntity(e.getKey()) instanceof Player p) {
                Job j = job(p, partial);
                if (j != null) return j;
            }
        }
        return null;
    }

    /**
     * Sawdust on the push of each stroke (client/WoodChipParticle): out of the kerf under the log's front end, falling, a
     * coarser bit now and then (a rip saw tears long fibres), now and then off its top.
     */
    private static void dust(Level level) {
        var mc = Minecraft.getInstance();
        if (!(level instanceof ClientLevel client)) return;
        RandomSource random = level.random;
        java.util.List<Player> working = new java.util.ArrayList<>();
        if (own != null && mc.player != null) working.add(mc.player);
        for (int id : REMOTE.keySet()) if (level.getEntity(id) instanceof Player p) working.add(p);
        for (Player p : working) {
            Job j = job(p, 0);
            if (j == null || j.knob()) continue;
            double since = (level.getGameTime() - j.started()) / 20.0 - (STARTS.containsKey(j.work()) ? SET : 0);
            if (since < 0 || since % STROKE >= STROKE / 2) continue;
            BlockState log = logState(j.recipe());
            if (log == null) continue;
            double kerf = kerfZ(j.progress());
            int n = random.nextFloat() < 0.5F ? 2 : 1;
            for (int i = 0; i < n; i++) {
                Vec3 under = j.at(LOG_X + (random.nextDouble() - 0.5) * 0.02, TOP - 0.01, LOG_END + random.nextDouble() * (Math.min(kerf, 0.0) - LOG_END));
                boolean coarse = random.nextFloat() < 0.06F;
                WoodChipParticle.spawn(client, coarse ? WoodChipParticle.Kind.CHIP : WoodChipParticle.Kind.DUST, under,
                        j.dir((random.nextDouble() - 0.5) * 0.06, -0.05 - 0.08 * random.nextDouble(), 0.05 + 0.1 * random.nextDouble()), log, coarse ? 0.5F : 1);
            }
            if (random.nextFloat() < 0.2F) {
                Vec3 top = j.at(LOG_X + (random.nextDouble() - 0.5) * 0.02, TOP + 2 * LOG_R + 0.005, kerf);
                WoodChipParticle.spawn(client, WoodChipParticle.Kind.DUST, top,
                        j.dir((random.nextDouble() - 0.5) * 0.25, 0.1 + 0.1 * random.nextDouble(), -0.05 * random.nextDouble()), log, 1);
            }
        }
    }

    /** Each working player's last half stroke sounded (entity id → half strokes since the first push). */
    private static final Map<Integer, Long> HALVES = new HashMap<>();

    /**
     * The saw heard in step with the action each viewer sees: the push at each stroke's start, the pull at its half, from
     * the kerf, fading out at WorkbenchCrafting.SAW_RADIUS (client/RangedSoundInstance, as noise/RangedSound plays a
     * server's sound; the server sends the infected's noise itself).
     */
    private static void strokes(Level level) {
        var mc = Minecraft.getInstance();
        java.util.List<Player> working = new java.util.ArrayList<>();
        if (own != null && mc.player != null) working.add(mc.player);
        for (int id : REMOTE.keySet()) if (level.getEntity(id) instanceof Player p) working.add(p);
        HALVES.keySet().removeIf(id -> working.stream().noneMatch(p -> p.getId() == id));
        for (Player p : working) {
            Job j = job(p, 0);
            if (j == null || j.knob()) continue;
            double since = (level.getGameTime() - j.started()) / 20.0 - (STARTS.containsKey(j.work()) ? SET : 0);
            if (since < 0) continue;
            long half = (long) Math.floor(since / (STROKE / 2));
            Long last = HALVES.put(p.getId(), half);
            if (last != null && last == half) continue;
            Vec3 at = Workbench.local(j.bench(), j.facing(), LOG_X, TOP + 2 * LOG_R, kerfZ(j.progress()));
            var sound = (half % 2 == 0 ? AflSounds.HAND_SAW_PUSH : AflSounds.HAND_SAW_PULL).get();
            RangedSoundInstance.play(new RangedSoundS2CPacket(sound.getLocation(), SoundSource.PLAYERS, at.x, at.y, at.z,
                    (float) WorkbenchCrafting.SAW_RADIUS, 1F, 1F, level.random.nextLong(), false));
        }
    }

    /**
     * The log a recipe saws, standing (its end grain's colour for the dust): the recipe's first log material when this
     * client knows the recipe (a species' boards saw that species' log); else, or for a recipe without a log, the white oak
     * log; null where none is registered.
     */
    @Nullable
    public static BlockState logState(@Nullable ResourceLocation recipe) {
        var mc = Minecraft.getInstance();
        WorkbenchRecipeView view = mc.player == null ? null : WorkbenchRecipes.find(mc.player, recipe);
        if (view != null) for (var m : view.materials())
            if (m.shown().getItem() instanceof net.minecraft.world.item.BlockItem b && b.getBlock() instanceof RotatedPillarBlock)
                return b.getBlock().defaultBlockState();
        var block = ForgeRegistries.BLOCKS.getValue(LOG);
        if (!(block instanceof RotatedPillarBlock)) return null;
        return block.defaultBlockState();
    }

    // ------------------------------------------------------------------------------------------------ panel

    /** Over the hotbar: the item, which of how many, the progress, and the key that stops. */
    private static final IGuiOverlay OVERLAY = (gui, g, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        Session s = own;
        if (s == null || mc.options.hideGui || mc.player == null || mc.screen != null || mc.level == null) return;
        if (s.furniture) {
            FurnitureWorkClient.panel(g, s, partialTick, screenWidth, screenHeight);
            return;
        }
        var font = mc.font;
        long nanos = System.nanoTime();
        Component title = Component.translatable("hud.apocalypse_firstlight.workbench.work", s.recipe.result().getHoverName(),
                Math.min(s.done + 1, s.quantity), s.quantity);
        Component stopText = Component.translatable("hud.apocalypse_firstlight.workbench.stop");
        AflKeyHint cap = AflKeyHint.MOUSE_LEFT;
        float keyW = cap.width(font) + 3 + font.width(stopText);
        float w = Math.max(14 + font.width(title), keyW) + 12, h = 6 + 12 + 6 + 14 + 3;
        float x = screenWidth / 2F - w / 2, y = screenHeight - 77 - h;
        AflUiDraw.panel(g, x, y, w, h, 1);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 6, y + 4, 0);
        pose.scale(0.75F, 0.75F, 1);
        g.renderItem(s.recipe.result(), 0, 0);
        pose.popPose();
        AflUiDraw.text(g, font, title, x + 20, y + 6, AflUiStyle.TEXT, 1);
        double now = mc.level.getGameTime() + partialTick;
        float progress = (float) Math.max(0, Math.min(1, (now - s.itemStarted) / Math.max(1, s.recipe.ticks())));
        float by = y + 19, bw = w - 12;
        AflUiShapes.fill(g, x + 6, by, bw, 2, 1, AflUiStyle.LINE, 0.18F);
        if (progress > 0) AflUiShapes.fill(g, x + 6, by, bw * progress, 2, 1, AflUiStyle.ACCENT, 1);
        float ky = by + 6;
        cap.draw(g, font, x + 6, ky, 1, nanos);
        AflUiDraw.text(g, font, stopText, x + 6 + cap.width(font) + 3, ky + 2, AflUiStyle.TEXT, 1);
    };

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        // not "overlays": Forge names the generated handler __Registration_overlays_RegisterGuiOverlaysEvent per package, and
        // WorkbenchClient.Registration#overlays in this package already has it (it would run twice, this one never)
        @SubscribeEvent
        public static void workOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "workbench_work", OVERLAY);
        }
    }
}
