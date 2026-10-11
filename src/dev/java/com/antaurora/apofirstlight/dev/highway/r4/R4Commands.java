package com.antaurora.apofirstlight.dev.highway.r4;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /afl dev highway_r4 (docs/worldgen/highway_r4_sample_v1.md, F and N): authorize -> find -> plan (survey, design,
 * preview) -> check (repeat survey, must match) -> build (journal saved first, then batched writes) -> status / verify /
 * sample -> remove (restores only blocks still holding the written state). Development worlds only (non-production, OP2,
 * creative, overworld, a world folder named AFL_R4_*). Never forces a chunk, never writes outside the checked journal.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class R4Commands {
    static final String PREFIX = "AFL_R4_";
    static final int BATCH = 4096;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int PLAN_TTL = 20 * 600, CHECK_TTL = 20 * 120;
    private record Pending(UUID player, R4Recipe request, R4Works.Prepared prepared, long at, long checkedAt) {}
    private static final Map<ServerLevel, Pending> PENDING = new HashMap<>();
    private static final Map<ServerLevel, String> JOBS = new HashMap<>();

    private R4Commands() {}

    private static boolean allowed(CommandSourceStack s) {
        return !FMLEnvironment.production && s.hasPermission(2) && s.getEntity() instanceof ServerPlayer p && p.isCreative()
                && s.getLevel().dimension().equals(Level.OVERWORLD);
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        var c = Commands.literal("highway_r4").requires(R4Commands::allowed);
        c.then(Commands.literal("authorize").executes(R4Commands::authorize));
        c.then(Commands.literal("find").executes(x -> find(x, 1500))
                .then(Commands.argument("radius", IntegerArgumentType.integer(256, 4000)).executes(x -> find(x, IntegerArgumentType.getInteger(x, "radius")))));
        c.then(Commands.literal("plan").then(Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("z", DoubleArgumentType.doubleArg())
                .then(Commands.argument("heading", DoubleArgumentType.doubleArg(-360, 360)).then(Commands.argument("length", IntegerArgumentType.integer(128, 512))
                        .executes(x -> plan(x, 8))
                        .then(Commands.argument("bend", DoubleArgumentType.doubleArg(-15, 15)).executes(x -> plan(x, DoubleArgumentType.getDouble(x, "bend")))))))));
        for (String a : new String[]{"check", "build", "status", "verify", "hide", "cancel", "remove"})
            c.then(Commands.literal(a).executes(x -> simple(x, a)));
        c.then(Commands.literal("sample").then(Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("z", DoubleArgumentType.doubleArg())
                .executes(x -> sample(x, DoubleArgumentType.getDouble(x, "x"), DoubleArgumentType.getDouble(x, "z"))))));
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c)));
    }

    private static String worldName(ServerLevel level) {
        var p = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        return p == null ? "" : p.toString();
    }

    private static void requireAuthorized(ServerLevel level, R4Data d) {
        if (!d.authorized) throw new IllegalArgumentException("NOT_AUTHORIZED; run /afl dev highway_r4 authorize in a test world named " + PREFIX + "*");
        if (!d.worldName.equals(worldName(level)) || d.seed != level.getSeed())
            throw new IllegalArgumentException("WORLD_IDENTITY_MISMATCH (authorized " + d.worldName + "); a copied or renamed world must be authorized again");
    }

    private static int authorize(CommandContext<CommandSourceStack> x) {
        return run(x, () -> {
            ServerLevel level = x.getSource().getLevel();
            String name = worldName(level);
            if (!name.startsWith(PREFIX)) throw new IllegalArgumentException("WORLD_NOT_A_TEST_WORLD: the world folder is '" + name + "'; create a creative test world whose name starts with " + PREFIX);
            R4Data d = R4Data.get(level);
            d.authorized = true;
            d.worldName = name;
            d.seed = level.getSeed();
            d.setDirty();
            level.getDataStorage().save();
            reply(x, "AUTHORIZED " + name + " (seed " + d.seed + ") for R4 test construction. Natural blocks inside a checked plan may be replaced here; nowhere else.");
        });
    }

    private static int find(CommandContext<CommandSourceStack> x, int radius) {
        return run(x, () -> {
            var p = x.getSource().getPlayerOrException();
            long begin = System.nanoTime();
            var found = R4Site.find(x.getSource().getLevel(), p.getX(), p.getZ(), radius, 384);
            var list = found.picked();
            double ms = (System.nanoTime() - begin) / 1e6;
            if (list.isEmpty()) {
                reply(x, String.format(Locale.ROOT, "No suitable site within %d m (%d segments checked on the plan in %.0f ms; %d suitable but protected). Rejected: %s. Try another area.",
                        radius, found.all().evaluated(), ms, found.protectedSkipped(), found.all().rejections()));
                return;
            }
            reply(x, String.format(Locale.ROOT, "%d candidates from the Terrain V2 plan (%d suitable of %d checked, %.0f ms; best first; the plan is a guide, plan surveys real blocks):",
                    list.size(), found.all().candidates().size(), found.all().evaluated(), ms));
            int i = 1;
            for (var c : list) {
                reply(x, String.format(Locale.ROOT, "%d) %s%n   stand at the centre: /tp @s %.0f %.0f %.0f   then: /afl dev highway_r4 plan %.0f %.0f %.0f %.0f %.0f",
                        i++, c.describe(), c.midX(), Math.ceil(c.midY()) + 30, c.midZ(), c.x0() - 0.5, c.z0() - 0.5, c.heading(), c.length(), c.bend()));
            }
        });
    }

    private static int plan(CommandContext<CommandSourceStack> x, double bend) {
        return run(x, () -> {
            ServerLevel level = x.getSource().getLevel();
            ServerPlayer player = x.getSource().getPlayerOrException();
            R4Data d = R4Data.get(level);
            requireAuthorized(level, d);
            if (d.active()) throw new IllegalArgumentException("SEGMENT_EXISTS (phase " + d.phase + "); remove it first, never overwrite it");
            if (JOBS.containsKey(level)) throw new IllegalArgumentException("JOB_RUNNING");
            double len = IntegerArgumentType.getInteger(x, "length");
            len = Math.floor(len / R4Profile.DS) * R4Profile.DS;
            var request = new R4Recipe(R4Recipe.SCHEMA, DoubleArgumentType.getDouble(x, "x") + 0.5, DoubleArgumentType.getDouble(x, "z") + 0.5,
                    DoubleArgumentType.getDouble(x, "heading"), len, bend, R4Recipe.defaultLeg(len), null);
            long begin = System.nanoTime();
            var prepared = R4Works.prepare(level, request, level.getGameTime());
            double ms = (System.nanoTime() - begin) / 1e6;
            PENDING.put(level, new Pending(player.getUUID(), request, prepared, level.getGameTime(), -1));
            if (prepared.design() != null) {
                R4Network.send(player, new R4Network.Snapshot(level.dimension().location(), true, true, true, UUID.randomUUID(), prepared.recipe().version(), prepared.recipe().json()));
                var prof = prepared.design().profile;
                reply(x, String.format(Locale.ROOT, "PLAN %s in %.0f ms: profile max grade %.2f %%, min vertical curve K %.0f; %s; trees cleared %d blocks; journal %d writes",
                        prepared.recipe().describe(), ms, prof.maxGrade() * 100, prof.minK(), prepared.plan().stats(), prepared.treeBlocks(), prepared.pos().length));
                reply(x, "PREVIEW shown to you only: mesh, no blocks, no collision (tinted).");
            }
            if (!prepared.ok()) {
                fail(x, "NOT BUILDABLE here (" + prepared.problems().size() + " problem(s)); nothing was written:");
                for (int i = 0; i < Math.min(16, prepared.problems().size()); i++) fail(x, "  " + prepared.problems().get(i));
            } else reply(x, "Next: /afl dev highway_r4 check (repeats the survey; must match), then build within 2 minutes.");
        });
    }

    private static int simple(CommandContext<CommandSourceStack> x, String action) {
        return run(x, () -> {
            ServerLevel level = x.getSource().getLevel();
            ServerPlayer player = x.getSource().getPlayerOrException();
            R4Data d = R4Data.get(level);
            switch (action) {
                case "check" -> check(x, level, player, d);
                case "build" -> build(x, level, player, d);
                case "status" -> {
                    sync(player, d, true);
                    var pend = PENDING.get(level);
                    reply(x, "phase=" + d.phase + " journal=" + d.pos.length + " progress=" + d.progress + " conflicts=" + d.conflicts + " preserved=" + d.preserved
                            + (JOBS.containsKey(level) ? " job=" + JOBS.get(level) : "") + (d.active() ? " version=" + d.version.substring(0, 12) : "")
                            + (pend != null ? " pending plan: " + pend.request().describe() + (pend.prepared().ok() ? "" : " (not buildable)") : ""));
                    if (!d.stats.isEmpty()) reply(x, d.stats);
                }
                case "verify" -> verify(x, level, d);
                case "hide" -> { PENDING.remove(level); sync(player, d, false); reply(x, "Preview cleared; a built segment, if any, stays shown."); }
                case "cancel" -> { PENDING.remove(level); sync(player, d, false); reply(x, "Pending plan dropped; nothing was written."); }
                case "remove" -> remove(x, level, d);
                default -> throw new IllegalStateException(action);
            }
        });
    }

    private static Pending pending(ServerLevel level, ServerPlayer player) {
        var p = PENDING.get(level);
        if (p == null || !p.player().equals(player.getUUID())) throw new IllegalArgumentException("NO_PLAN; run plan first");
        if (level.getGameTime() - p.at() > PLAN_TTL) { PENDING.remove(level); throw new IllegalArgumentException("PLAN_EXPIRED; plan again"); }
        if (!p.prepared().ok()) throw new IllegalArgumentException("PLAN_NOT_BUILDABLE; see the problems listed by plan");
        return p;
    }

    /**
     * Repeat the whole preflight and compare it with the pending one (R4Journal): the works (recipe version, positions,
     * written states) must be identical; original states may differ only by natural changes, which are absorbed (the
     * fresh states replace the older ones). Anything else drops the pending plan: its preview no longer matches the world,
     * so the only way on is a new plan (a repeated check would compare against the same stale plan forever).
     */
    private static R4Works.Prepared reverify(CommandContext<CommandSourceStack> x, ServerLevel level, Pending p, String phase) {
        long begin = System.nanoTime();
        var fresh = R4Works.prepare(level, p.request(), level.getGameTime());
        double ms = (System.nanoTime() - begin) / 1e6;
        if (!fresh.ok()) {
            LOGGER.info("[AFL R4] {}: preflight failed after {} ticks: {}", phase, level.getGameTime() - p.at(), fresh.problems());
            throw new IllegalArgumentException(phase.toUpperCase(Locale.ROOT) + "_FAILED: " + fresh.problems().get(0) + (fresh.problems().size() > 1 ? " (+" + (fresh.problems().size() - 1) + " more, see the log)" : ""));
        }
        var diff = R4Works.diff(p.prepared(), fresh, 10);
        long sincePlan = level.getGameTime() - p.at(), sinceCheck = p.checkedAt() < 0 ? -1 : level.getGameTime() - p.checkedAt();
        if (!diff.examples().isEmpty() || !diff.acceptable())
            LOGGER.info("[AFL R4] {} vs pending ({} ticks after plan, {} after check, preflight {} ms): {}; works {} -> {}, originals {} -> {}; first differences: {}",
                    phase, sincePlan, sinceCheck, String.format(Locale.ROOT, "%.0f", ms), diff.summary(), p.prepared().worksPrint(), fresh.worksPrint(),
                    p.prepared().originalPrint(), fresh.originalPrint(), diff.examples());
        if (!diff.acceptable()) {
            PENDING.remove(level);
            String first = diff.examples().isEmpty() ? "" : "; first: " + diff.examples().get(0);
            throw new IllegalArgumentException("WORLD_CHANGED_SINCE_" + (phase.equals("build") ? "CHECK" : "PLAN") + " (" + diff.summary() + first
                    + "). The pending plan was dropped: run plan again (details in the log)");
        }
        if (diff.naturalChanges() > 0)
            reply(x, diff.naturalChanges() + " natural change(s) since " + (phase.equals("build") ? "check" : "plan") + " absorbed (grass, plants, leaves or snow; the journal records the current states)"
                    + (diff.examples().isEmpty() ? "" : ", e.g. " + diff.examples().get(0)));
        return fresh;
    }

    private static void check(CommandContext<CommandSourceStack> x, ServerLevel level, ServerPlayer player, R4Data d) {
        requireAuthorized(level, d);
        var p = pending(level, player);
        long begin = System.nanoTime();
        var again = reverify(x, level, p, "check");
        PENDING.put(level, new Pending(p.player(), p.request(), again, p.at(), level.getGameTime()));
        var st = again.plan().stats();
        reply(x, String.format(Locale.ROOT, "CHECK_OK in %.0f ms: same works (%d writes: %d removals incl. %d tree blocks, %d placements; works %s); max cut %.2f m, max fill %.2f m. build within 2 minutes; stay above the road.",
                (System.nanoTime() - begin) / 1e6, again.pos().length, st.removed() + again.treeBlocks(), again.treeBlocks(), st.placed(), again.worksPrint().substring(0, 8), st.maxCut(), st.maxFill()));
    }

    private static void build(CommandContext<CommandSourceStack> x, ServerLevel level, ServerPlayer player, R4Data d) {
        requireAuthorized(level, d);
        if (d.active()) throw new IllegalArgumentException("SEGMENT_EXISTS; remove it first");
        var p = pending(level, player);
        if (p.checkedAt() < 0 || level.getGameTime() - p.checkedAt() > CHECK_TTL) throw new IllegalArgumentException("REQUIRES_A_CHECK_IN_THE_LAST_2_MINUTES");
        // the journal is measured now, in the tick the build starts: the originals are the states being replaced
        var prep = reverify(x, level, p, "build");
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < prep.pos().length; i++) {
            pos.set(BlockPos.getX(prep.pos()[i]), BlockPos.getY(prep.pos()[i]), BlockPos.getZ(prep.pos()[i]));
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) throw new IllegalArgumentException("UNLOADED_CHUNK at " + pos.toShortString() + "; nothing written");
            BlockState now = level.getBlockState(pos);
            if (!now.equals(prep.before()[i])) {   // same tick as the preflight: cannot happen unless the preflight itself is wrong
                PENDING.remove(level);
                LOGGER.error("[AFL R4] build: state at {} is {} but the preflight of this tick recorded {}", pos.toShortString(), now, prep.before()[i]);
                throw new IllegalArgumentException("INTERNAL_STATE_MISMATCH at " + pos.toShortString() + " (" + prep.before()[i] + " recorded, " + now + " found); nothing written; plan again");
            }
        }
        LOGGER.info("[AFL R4] build: journal {} writes, works {}, originals {}", prep.pos().length, prep.worksPrint(), prep.originalPrint());
        d.begin(prep.recipe(), prep.pos(), prep.before(), prep.after(), "plan: " + prep.plan().stats() + "; trees " + prep.treeBlocks());
        level.getDataStorage().save();   // the journal is on disk before the first block changes
        JOBS.put(level, "BUILD");
        PENDING.remove(level);
        broadcast(level, d);   // the preview becomes the segment for everyone
        reply(x, "BUILDING " + prep.pos().length + " writes, " + BATCH + " per tick (about " + (prep.pos().length / BATCH / 20 + 1) + " s). Stay nearby so the chunks stay loaded.");
    }

    private static void verify(CommandContext<CommandSourceStack> x, ServerLevel level, R4Data d) {
        if (!d.active()) { reply(x, "EMPTY"); return; }
        int present = 0, original = 0, changed = 0, unloaded = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < d.pos.length; i++) {
            pos.set(BlockPos.getX(d.pos[i]), BlockPos.getY(d.pos[i]), BlockPos.getZ(d.pos[i]));
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) { unloaded++; continue; }
            BlockState s = level.getBlockState(pos);
            if (s.equals(d.palette.get(d.after[i]))) present++; else if (s.equals(d.palette.get(d.before[i]))) original++; else changed++;
        }
        reply(x, "phase=" + d.phase + " journal=" + d.pos.length + " present(as built)=" + present + " original(not yet written)=" + original
                + " changed(by someone else)=" + changed + " unloaded=" + unloaded + " (no repair)");
    }

    private static void remove(CommandContext<CommandSourceStack> x, ServerLevel level, R4Data d) {
        if (!d.active()) { PENDING.remove(level); reply(x, "EMPTY; nothing to remove"); return; }
        if (JOBS.containsKey(level)) throw new IllegalArgumentException("JOB_RUNNING " + JOBS.get(level));
        d.phase = "REMOVING";
        d.progress = d.pos.length;   // counts down over the whole journal: a crash mid-build may have written past the saved progress
        d.preserved = 0;
        d.setDirty();
        level.getDataStorage().save();
        JOBS.put(level, "REMOVE");
        broadcast(level, d);
        reply(x, "REMOVING: restoring every journal position that still holds the written state, newest first; anything changed since is kept.");
    }

    private static int sample(CommandContext<CommandSourceStack> x, double qx, double qz) {
        return run(x, () -> {
            ServerLevel level = x.getSource().getLevel();
            R4Data d = R4Data.get(level);
            R4Design design;
            if (d.active()) design = R4Recipe.decode(d.recipe).design();
            else {
                var p = PENDING.get(level);
                if (p == null || p.prepared().design() == null) throw new IllegalArgumentException("NO_SEGMENT_OR_PLAN");
                design = p.prepared().design();
            }
            var s = design.surface(qx, qz);
            if (s.isEmpty()) { reply(x, "OUTSIDE_ROAD"); return; }
            var v = s.get();
            int bx = (int) Math.floor(qx), bz = (int) Math.floor(qz), by = (int) Math.floor(v.y() - 0.01);
            BlockState at = level.getBlockState(new BlockPos(bx, by, bz));
            reply(x, String.format(Locale.ROOT, "%s station %.2f m lateral %+.2f m: height %.4f, normal (%.4f, %.4f, %.4f), tangent (%.4f, %.4f), zone %s; block at %d %d %d: %s",
                    v.paved() ? "PAVEMENT" : "MEDIAN TURF", v.station(), v.lateral(), v.y(), v.nx(), v.ny(), v.nz(), v.tx(), v.tz(), v.zone(), bx, by, bz,
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(at.getBlock())));
        });
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FMLEnvironment.production || !(event.level instanceof ServerLevel level)) return;
        String job = JOBS.get(level);
        if (job == null) return;
        R4Data d;
        try { d = R4Data.get(level); } catch (Exception e) { JOBS.remove(level); return; }
        var pos = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        if (job.equals("BUILD")) {
            int n = 0;
            while (d.progress < d.pos.length && n < BATCH) {
                int i = d.progress;
                pos.set(BlockPos.getX(d.pos[i]), BlockPos.getY(d.pos[i]), BlockPos.getZ(d.pos[i]));
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;   // wait until the player is back
                BlockState cur = level.getBlockState(pos), was = d.palette.get(d.before[i]), will = d.palette.get(d.after[i]);
                switch (R4Journal.build(cur, was, will, R4Works.STATES)) {
                    case WRITE -> level.setBlock(pos, will, flags);
                    case WRITE_NATURAL -> {   // e.g. grass spread onto the dirt since the preflight: record what is really replaced
                        d.before[i] = d.index(cur);
                        d.natural++;
                        level.setBlock(pos, will, flags);
                    }
                    case CONFLICT -> {
                        if (d.conflicts++ < 10) LOGGER.info("[AFL R4] build conflict at {}: journal {}, found {} (left as found)", pos.toShortString(), was, cur);
                    }
                    case ALREADY_DONE -> {}
                }
                d.progress++;
                n++;
            }
            d.setDirty();
            if (d.progress >= d.pos.length) {
                d.phase = "COMPLETE";
                level.getDataStorage().save();
                JOBS.remove(level);
                broadcast(level, d);
                LOGGER.info("[AFL R4] built: {} writes, natural changes absorbed {}, conflicts {}", d.pos.length, d.natural, d.conflicts);
                for (var p : level.players()) p.sendSystemMessage(Component.literal("Highway R4: BUILT " + d.pos.length + " writes, natural changes absorbed " + d.natural
                        + ", conflicts " + d.conflicts + " (left as found). /afl dev highway_r4 verify"));
            }
        } else {
            int n = 0;
            while (d.progress > 0 && n < BATCH) {
                int i = d.progress - 1;
                pos.set(BlockPos.getX(d.pos[i]), BlockPos.getY(d.pos[i]), BlockPos.getZ(d.pos[i]));
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
                BlockState cur = level.getBlockState(pos), was = d.palette.get(d.before[i]), will = d.palette.get(d.after[i]);
                switch (R4Journal.remove(cur, was, will)) {
                    case RESTORE -> level.setBlock(pos, was, flags);
                    case PRESERVE -> d.preserved++;
                    case ORIGINAL -> {}
                }
                d.progress--;
                n++;
            }
            d.setDirty();
            if (d.progress <= 0) {
                int kept = d.preserved;
                d.clear();
                level.getDataStorage().save();
                JOBS.remove(level);
                broadcast(level, d);
                for (var p : level.players()) p.sendSystemMessage(Component.literal("Highway R4: REMOVED; positions changed by someone else and kept: " + kept));
            }
        }
    }

    /** Resume an interrupted job after a restart (the journal says where it was). */
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (FMLEnvironment.production || !(e.getEntity() instanceof ServerPlayer p)) return;
        try {
            R4Data d = R4Data.get(p.serverLevel());
            sync(p, d, false);
            if (d.phase.equals("BUILDING") || d.phase.equals("REMOVING"))
                p.sendSystemMessage(Component.literal("Highway R4: an interrupted " + d.phase + " job is in the journal; run /afl dev highway_r4 remove to undo it safely."));
        } catch (Exception ex) {
            p.sendSystemMessage(Component.literal("Highway R4: " + ex.getMessage()));
        }
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!FMLEnvironment.production && e.getEntity() instanceof ServerPlayer p) try { sync(p, R4Data.get(p.serverLevel()), false); } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) { PENDING.clear(); JOBS.clear(); }

    /** The built segment (or nothing) to one player. */
    private static void sync(ServerPlayer p, R4Data d, boolean report) {
        boolean shown = d != null && (d.phase.equals("COMPLETE") || d.phase.equals("BUILDING"));
        R4Network.send(p, new R4Network.Snapshot(p.serverLevel().dimension().location(), shown, false, report,
                shown ? d.instance : new UUID(0, 0), shown ? d.version : "", shown ? d.recipe : ""));
    }

    private static void broadcast(ServerLevel level, R4Data d) { for (var p : level.players()) sync(p, d, false); }

    private interface Body { void run() throws Exception; }

    private static int run(CommandContext<CommandSourceStack> x, Body body) {
        try {
            body.run();
            return 1;
        } catch (Exception e) {
            fail(x, e.getMessage() == null ? e.toString() : e.getMessage());
            return 0;
        }
    }

    private static void reply(CommandContext<CommandSourceStack> x, String text) { x.getSource().sendSuccess(() -> Component.literal(text), false); }

    private static void fail(CommandContext<CommandSourceStack> x, String text) { x.getSource().sendFailure(Component.literal("Highway R4: " + text)); }
}
