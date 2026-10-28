package com.antaurora.apofirstlight.liquid.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.liquid.LiquidBasin;
import com.antaurora.apofirstlight.liquid.LiquidCell;
import com.antaurora.apofirstlight.liquid.LiquidColumn;
import com.antaurora.apofirstlight.liquid.LiquidEngine;
import com.antaurora.apofirstlight.liquid.LiquidLedger;
import com.antaurora.apofirstlight.liquid.LiquidUnits;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /afl dev water ...} (docs/gameplay/water_v1_design.md 10, 16.5): read and test the physical water in game.
 * <ul>
 *   <li>{@code here}: the column at the feet, its layers, liquid, levels, basin; the chunk's account; the engine;</li>
 *   <li>{@code fill <litres>} / {@code drain <litres>}: fill the pool looked at (8 m), else at the feet, its whole surface
 *   rising at once (LiquidEngine#fill) / take out of that layer;</li>
 *   <li>{@code pour <litres> [L/s]} / {@code pour stop}: pour from the hand, a stream landing where it falls (package F);</li>
 *   <li>{@code burst <mm> <bar> [seconds]} / {@code burst stop}: a burst pipe at the block face looked at (64 m), water
 *   out of a hole mm across at bar (Orifice), up from a top face, 20 degrees above level from a side (M4 of
 *   docs/gameplay/water_mesh_parcels_v1_design.md);</li>
 *   <li>{@code river} / {@code river scale <x>}: the river flow field at the feet (river plan R1: the river line, eta, slope,
 *   depth, velocity) / every river's speed times x (river_velocity_scale), laid again over the chunks within 8;</li>
 *   <li>{@code rain <mm/h> [radius]}: test rain on the columns around (package 1's stand-in; 0 stops it);</li>
 *   <li>{@code surface}: the natural rain's surface water on the column at the feet and its chunk's account (package 3a,
 *   LiquidRain); {@code rainstats [reset]}: how long settling every loaded chunk takes (the server stress test);
 *   {@code rainforce <mm/h>} / {@code rainforce off}: natural rain over every loaded chunk whatever the weather;</li>
 *   <li>{@code perf}: the water's cost a tick since the last {@code perf}, by part and by engine phase, and what was
 *   stepping (river plan docs/gameplay/water_river_v1_design.md 9, step 1);</li>
 *   <li>{@code ledger}: the chunk's account and the engine's own check (every chunk's liquid against its account);</li>
 *   <li>{@code clear [radius]}: take out all the liquid around (written in the account as taken);</li>
 *   <li>{@code pause} / {@code resume}: stop and go on stepping (the liquid stays as it is).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LiquidDevCommand {
    private LiquidDevCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("water")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("here").executes(c -> here(c.getSource())))
                .then(Commands.literal("fill").then(Commands.argument("litres", DoubleArgumentType.doubleArg(0.001, 1_000_000))
                        .executes(c -> pour(c.getSource(), DoubleArgumentType.getDouble(c, "litres")))))
                .then(Commands.literal("drain").then(Commands.argument("litres", DoubleArgumentType.doubleArg(0.001, 1_000_000))
                        .executes(c -> pour(c.getSource(), -DoubleArgumentType.getDouble(c, "litres")))))
                .then(Commands.literal("pour")
                        .then(Commands.literal("stop").executes(c -> pourStop(c.getSource())))
                        .then(Commands.argument("litres", DoubleArgumentType.doubleArg(0.01, 1000))
                                .executes(c -> pourFromHand(c.getSource(), DoubleArgumentType.getDouble(c, "litres"), 1.0))
                                .then(Commands.argument("litres_per_second", DoubleArgumentType.doubleArg(0.001, 20))
                                        .executes(c -> pourFromHand(c.getSource(), DoubleArgumentType.getDouble(c, "litres"),
                                                DoubleArgumentType.getDouble(c, "litres_per_second"))))))
                .then(Commands.literal("burst")
                        .then(Commands.literal("stop").executes(c -> burstStop(c.getSource())))
                        .then(Commands.argument("mm", DoubleArgumentType.doubleArg(1, 300))
                                .then(Commands.argument("bar", DoubleArgumentType.doubleArg(0.05, 20))
                                        .executes(c -> burst(c.getSource(), DoubleArgumentType.getDouble(c, "mm"), DoubleArgumentType.getDouble(c, "bar"), 10))
                                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1, 600))
                                                .executes(c -> burst(c.getSource(), DoubleArgumentType.getDouble(c, "mm"), DoubleArgumentType.getDouble(c, "bar"),
                                                        DoubleArgumentType.getDouble(c, "seconds")))))))
                .then(Commands.literal("river").executes(c -> river(c.getSource()))
                        .then(Commands.literal("scale").then(Commands.argument("x", DoubleArgumentType.doubleArg(0, 4))
                                .executes(c -> riverScale(c.getSource(), DoubleArgumentType.getDouble(c, "x"))))))
                .then(Commands.literal("rain").then(Commands.argument("mm_per_hour", DoubleArgumentType.doubleArg(0, 500))
                        .executes(c -> rain(c.getSource(), DoubleArgumentType.getDouble(c, "mm_per_hour"), 16))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(c -> rain(c.getSource(), DoubleArgumentType.getDouble(c, "mm_per_hour"), IntegerArgumentType.getInteger(c, "radius"))))))
                .then(Commands.literal("surface").executes(c -> surface(c.getSource())))
                .then(Commands.literal("rainstats").executes(c -> rainStats(c.getSource(), false))
                        .then(Commands.literal("reset").executes(c -> rainStats(c.getSource(), true))))
                .then(Commands.literal("rainforce")
                        .then(Commands.literal("off").executes(c -> rainForce(c.getSource(), Double.NaN)))
                        .then(Commands.argument("mm_per_hour", DoubleArgumentType.doubleArg(0, 500))
                                .executes(c -> rainForce(c.getSource(), DoubleArgumentType.getDouble(c, "mm_per_hour")))))
                .then(Commands.literal("ledger").executes(c -> ledger(c.getSource())))
                .then(Commands.literal("perf").executes(c -> perf(c.getSource())))
                .then(Commands.literal("clear").executes(c -> clear(c.getSource(), 16))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(c -> clear(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("pause").executes(c -> pause(c.getSource(), true)))
                .then(Commands.literal("resume").executes(c -> pause(c.getSource(), false))))));
    }

    private static int here(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        LiquidWorld.State st = LiquidWorld.state(level);
        LiquidEngine e = st.engine;
        Vec3 p = source.getPosition();
        int x = (int) Math.floor(p.x), z = (int) Math.floor(p.z);
        List<String> lines = new ArrayList<>();
        LiquidColumn col = e.column(x, z);
        if (col == null) lines.add(String.format(Locale.ROOT, "AFL water at (%d, %d): the chunk is not in the liquid yet (loading).", x, z));
        else {
            lines.add(String.format(Locale.ROOT, "AFL water column (%d, %d), %d layer(s); you stand at Y %.3f:", x, z, col.cells().size(), p.y));
            for (LiquidCell c : col.cells()) {
                String state = c.boundary() ? "held at a fixed level" : c.volume() == 0 ? "dry" : c.sleeping() ? "still" : "flowing";
                LiquidBasin b = c.basin();
                lines.add(String.format(Locale.ROOT, "  floor %.4f, %s; %s L, level %.4f (%.3f m deep), %s%s",
                        c.floor(), c.openTop() ? "open to the sky (kept to " + String.format(Locale.ROOT, "%.2f", c.ceiling()) + ")" : "ceiling " + String.format(Locale.ROOT, "%.4f", c.ceiling()),
                        litres(c.volume()), c.level(), Math.max(0, c.level() - c.floor()), state,
                        b == null ? "" : String.format(Locale.ROOT, "; basin of %d cells, %s L, spills at %.4f", b.cells().size(), litres(b.volume()), b.spill())));
            }
        }
        LiquidLedger l = e.ledger(x >> 4, z >> 4);
        if (l != null) lines.add(account(l));
        lines.add("Engine: " + e.stats() + String.format(Locale.ROOT, "; last tick %.3f ms (rain %.3f, pours %.3f, engine %.3f, last sync %.3f), %d chunks synced, surfaces %d sent / %d unchanged skipped%s",
                st.lastTickNanos / 1e6, st.lastRainNanos / 1e6, st.lastPourNanos / 1e6, st.lastEngineNanos / 1e6, st.lastSyncNanos / 1e6,
                st.syncedChunks, st.surfacesSent, st.surfacesSkipped, st.paused ? "; PAUSED" : ""));
        send(source, lines);
        return 1;
    }

    private static int surface(CommandSourceStack source) {
        Vec3 p = source.getPosition();
        String[] lines = LiquidRain.describe(source.getLevel(), (int) Math.floor(p.x), (int) Math.floor(p.z));
        if (lines == null) {
            source.sendFailure(Component.literal("AFL rain: no surface water here (not the overworld, or the chunk is still loading)."));
            return 0;
        }
        send(source, List.of(lines));
        return 1;
    }

    private static int rainStats(CommandSourceStack source, boolean reset) {
        if (reset) LiquidRain.resetStats(source.getLevel());
        LiquidWorld.State st = LiquidWorld.state(source.getLevel());
        String line = LiquidRain.stats(source.getLevel()) + String.format(Locale.ROOT, "; whole water tick last %.3f ms (rain %.3f, engine %.3f, last sync %.3f)",
                st.lastTickNanos / 1e6, st.lastRainNanos / 1e6, st.lastEngineNanos / 1e6, st.lastSyncNanos / 1e6);
        send(source, List.of(line));
        return 1;
    }

    private static int rainForce(CommandSourceStack source, double mm) {
        if (!LiquidRain.force(source.getLevel(), mm)) {
            source.sendFailure(Component.literal("AFL rain: only the overworld has natural rain."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(Double.isNaN(mm) ? "AFL rain: the weather's rain again."
                : String.format(Locale.ROOT, "AFL rain: %.1f mm/h over every loaded chunk (dev; the sky does not change). /afl dev water rainforce off ends it.", mm)), false);
        return 1;
    }

    private static String account(LiquidLedger l) {
        return String.format(Locale.ROOT, "Chunk account (L): rain %s, natural rain in %s, poured %s, taken %s, from water bodies %s, to them %s, in from chunks %s, out to them %s, limbo %s; net %s",
                litres(l.rain()), litres(l.fromSurface()), litres(l.added()), litres(l.removed()), litres(l.boundaryIn()), litres(l.boundaryOut()),
                litres(l.crossIn()), litres(l.crossOut()), litres(l.limbo()), litres(l.net()));
    }

    private static String litres(long ml) {
        return String.format(Locale.ROOT, "%.3f", ml / (double) LiquidUnits.ML_PER_LITRE);
    }

    /** Pours (litres > 0) or takes (litres < 0) at the layer looked at, else at the feet. */
    private static int pour(CommandSourceStack source, double litres) {
        ServerLevel level = source.getLevel();
        LiquidEngine e = LiquidWorld.engine(level);
        Vec3 feet = source.getPosition().add(0, 0.05, 0), looked = null;
        if (source.getEntity() instanceof ServerPlayer player) {
            HitResult hit = player.pick(8, 1, false);
            if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                // just off the face looked at, into the open
                looked = bh.getLocation().add(Vec3.atLowerCornerOf(bh.getDirection().getNormal()).scale(0.05));
            }
        }
        Vec3 at = looked != null ? looked : feet;
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        long ml = Math.round(Math.abs(litres) * LiquidUnits.ML_PER_LITRE);
        LiquidColumn col = e.column(x, z);
        LiquidCell c = col == null ? null : col.cellAt(at.y);
        if (c == null) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT, "AFL water: no open layer at (%.2f, %.2f, %.2f) (inside solid, or the chunk is not in the liquid yet).", at.x, at.y, at.z)));
            return 0;
        }
        if (litres > 0) {
            // the pool rises as a whole (LiquidEngine#fill), not a tower in one column
            int cells = e.fill(x, at.y, z, ml);
            if (cells == 0) {
                source.sendFailure(Component.literal("AFL water: that layer is held at a fixed level, nothing poured."));
                return 0;
            }
            if (cells < 0) {
                source.sendFailure(Component.literal(String.format(Locale.ROOT,
                        "AFL water: no pool there: %.3f L would spread over the open ground downhill (more than 4096 cells), nothing poured. Fill a pit, or pour with /afl dev water pour.", ml / 1000.0)));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "AFL water: filled %.3f L at (%d, %.2f, %d) over %d cells; the surface there is now at %.4f.",
                    ml / 1000.0, x, at.y, z, cells, c.level())), false);
        } else {
            long taken = e.remove(x, at.y, z, ml);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "AFL water: took %.3f L of %.3f asked at (%d, %.2f, %d); %s L left in the layer.",
                    taken / 1000.0, ml / 1000.0, x, at.y, z, litres(c.volume()))), false);
        }
        return 1;
    }

    private record Rain(double mm, int cx, int cz, int r) implements LiquidEngine.RainField {
        @Override
        public double mmPerHour(int x, int z) {
            int dx = x - cx, dz = z - cz;
            return dx * dx + dz * dz <= r * r ? mm : 0;
        }

        @Override public int minX() { return cx - r; }
        @Override public int minZ() { return cz - r; }
        @Override public int maxX() { return cx + r; }
        @Override public int maxZ() { return cz + r; }
    }

    /** Pours from the hand where the player looks (docs/gameplay/water_falling_v1_design.md 3.3). */
    private static int pourFromHand(CommandSourceStack source, double litres, double perSecond) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("AFL water: only a player can pour."));
            return 0;
        }
        LiquidPours.start(LiquidWorld.state(source.getLevel()), player, Math.round(litres * LiquidUnits.ML_PER_LITRE), perSecond);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "AFL water: pouring %.2f L at %.3f L/s from your hand (%.1f s; it goes where you look). /afl dev water pour stop stops it.",
                litres, perSecond, litres / perSecond)), false);
        return 1;
    }

    private static int pourStop(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        boolean was = LiquidPours.stop(LiquidWorld.state(source.getLevel()), player);
        source.sendSuccess(() -> Component.literal(was ? "AFL water: stopped pouring." : "AFL water: you are not pouring."), false);
        return was ? 1 : 0;
    }

    /** The river flow field at the feet (river plan R1). */
    private static int river(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        LiquidWorld.State st = LiquidWorld.state(level);
        if (!st.hydroAsked) {
            st.hydro = RiverFlow.hydro(level);
            st.hydroAsked = true;
        }
        if (st.hydro == null) {
            source.sendFailure(Component.literal("AFL river: this world's rivers are vanilla water (no river_flow gate: made before R1, or not a Terrain V2 world)."));
            return 0;
        }
        Vec3 at = source.getPosition();
        var p = new com.antaurora.apofirstlight.worldgen.terrain.v2.HydroQuery.Point();
        if (!st.hydro.river(at.x, at.z, p)) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT, "AFL river: no river at (%.1f, %.1f); %d river columns laid since the level loaded, speed x%.2f.",
                    at.x, at.z, st.riverColumns, RiverFlow.velocityScale)));
            return 0;
        }
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        LiquidCell c = st.engine.riverLayer(x, z, p.eta);
        String cell;
        if (c == null) cell = "no water layer under it (the bed stands up to the surface, or the chunk is not in the engine yet)";
        else {
            double[] v = new double[2];
            c.velocity(v);
            cell = String.format(Locale.ROOT, "%s, bed %.2f, depth %.2f m, velocity (%.2f, %.2f) %.2f m/s",
                    c.river() ? "river water" : c.boundary() ? "held, not a river" : "not laid", c.floor(), p.eta - c.floor(), v[0], v[1], Math.hypot(v[0], v[1]));
        }
        String msg = String.format(Locale.ROOT, "AFL river: line %d, %.0f m from its source, %.1f m %s of its centreline (half width %.1f), %.1f km2; level block %d, surface %.3f, slope %.5f; %s. %d columns laid, speed x%.2f.",
                p.line, p.s, Math.abs(p.offset), p.offset >= 0 ? "right" : "left", p.halfWidth, p.area / 1e6, p.level, p.eta, p.slope, cell,
                st.riverColumns, RiverFlow.velocityScale);
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int riverScale(CommandSourceStack source, double x) {
        ServerLevel level = source.getLevel();
        LiquidWorld.State st = LiquidWorld.state(level);
        RiverFlow.velocityScale = x;
        if (!st.hydroAsked) {
            st.hydro = RiverFlow.hydro(level);
            st.hydroAsked = true;
        }
        int n = 0;
        if (st.hydro != null) {
            int cx = (int) Math.floor(source.getPosition().x) >> 4, cz = (int) Math.floor(source.getPosition().z) >> 4;
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++)
                if (st.engine.loaded(cx + dx, cz + dz)) n += RiverFlow.apply(level, st.engine, st.hydro, cx + dx, cz + dz);
        }
        int laid = n;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "AFL river: speed x%.2f; %d river columns within 8 chunks laid again (the rest when they load).", x, laid)), false);
        return 1;
    }

    /** A burst pipe at the face looked at (M4). */
    private static int burst(CommandSourceStack source, double mm, double bar, double seconds) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("AFL water: only a player can pick the face."));
            return 0;
        }
        HitResult hit = player.pick(64, 1, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("AFL water: look at a block face within 64 m."));
            return 0;
        }
        Vec3 at = bh.getLocation().add(Vec3.atLowerCornerOf(bh.getDirection().getNormal()).scale(0.05));
        LiquidBursts.Burst b = LiquidBursts.start(LiquidWorld.state(source.getLevel()), at, bh.getDirection(), mm, bar, seconds);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "AFL water: burst at (%.2f, %.2f, %.2f) out of the %s face: %.0f mm hole at %.2f bar, %.1f m/s, %.2f L/s for %.1f s (%.1f L). /afl dev water burst stop stops it.",
                at.x, at.y, at.z, bh.getDirection().getName(), mm, bar, b.speed, b.litresPerSecond, seconds, b.left / 1000.0)), false);
        return 1;
    }

    private static int burstStop(CommandSourceStack source) {
        int n = LiquidBursts.stop(LiquidWorld.state(source.getLevel()));
        source.sendSuccess(() -> Component.literal(n > 0 ? "AFL water: stopped " + n + " burst(s)." : "AFL water: nothing is bursting."), false);
        return n;
    }

    private static int rain(CommandSourceStack source, double mm, int radius) {
        LiquidEngine e = LiquidWorld.engine(source.getLevel());
        Vec3 p = source.getPosition();
        if (mm <= 0) {
            e.setRain(null);
            source.sendSuccess(() -> Component.literal("AFL water: test rain stopped."), false);
            return 1;
        }
        e.setRain(new Rain(mm, (int) Math.floor(p.x), (int) Math.floor(p.z), radius));
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "AFL water: test rain %.1f mm/h on every open column within %d m (package 1's stand-in: the water runs off the slopes cell by cell; catchment and infiltration are package 3). 1 mm/h = 1 L/m2 a game hour; /afl dev water rain 0 stops it.", mm, radius)), false);
        return 1;
    }

    /** The averages since the last reading (or the world's start), then a fresh window. */
    private static int perf(CommandSourceStack source) {
        LiquidWorld.State st = LiquidWorld.state(source.getLevel());
        LiquidEngine e = st.engine;
        long n = Math.max(1, st.perfTicks);
        long[] spent = e.spentNanos();
        double[] phase = new double[4];
        for (int i = 0; i < 4; i++) phase[i] = (spent[i] - st.perfSpent[i]) / 1e6 / n;
        long[] p = st.perfNanos;
        double total = (p[0] + p[1] + p[2] + p[3] + p[4]) / 1e6 / n;
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "AFL water over the last %d ticks: %.3f ms a tick; chunks + ground changes %.3f, natural rain %.3f, pours %.3f, engine %.3f, sync %.3f (%.3f ms each of %d syncs)",
                st.perfTicks, total, p[0] / 1e6 / n, p[1] / 1e6 / n, p[2] / 1e6 / n, p[3] / 1e6 / n, p[4] / 1e6 / n,
                st.perfSyncs == 0 ? 0 : p[4] / 1e6 / st.perfSyncs, st.perfSyncs));
        int awake = e.awakeWetCells();
        double engineNs = (phase[0] + phase[1] + phase[2] + phase[3]) * 1e6;
        lines.add(String.format(Locale.ROOT, "  engine phases, ms a tick: rain + levels %.3f, wake %.3f, flow %.3f, quiet + sleep + soak %.3f; now %d flowing cells, %d substeps (%.0f ns a flowing cell a substep, budget 80-120)",
                phase[0], phase[1], phase[2], phase[3], awake, e.substeps(), awake == 0 ? 0 : engineNs / awake / Math.max(1, e.substeps())));
        lines.add("  " + e.stats());
        java.util.Arrays.fill(p, 0);
        st.perfTicks = st.perfSyncs = 0;
        st.perfSpent = spent;
        send(source, lines);
        return 1;
    }

    private static int ledger(CommandSourceStack source) {
        LiquidEngine e = LiquidWorld.engine(source.getLevel());
        Vec3 p = source.getPosition();
        List<String> lines = new ArrayList<>();
        LiquidLedger l = e.ledger((int) Math.floor(p.x) >> 4, (int) Math.floor(p.z) >> 4);
        lines.add(l == null ? "AFL water: this chunk is not in the liquid yet." : account(l));
        List<String> problems = e.verify();
        lines.add(problems.isEmpty()
                ? String.format(Locale.ROOT, "Check: every loaded chunk's liquid is its account's net to the millilitre; %s L in all.", litres(e.total()))
                : problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(5, problems.size()))));
        send(source, lines);
        return problems.isEmpty() ? 1 : 0;
    }

    private static int clear(CommandSourceStack source, int radius) {
        LiquidEngine e = LiquidWorld.engine(source.getLevel());
        Vec3 p = source.getPosition();
        int px = (int) Math.floor(p.x), pz = (int) Math.floor(p.z);
        long taken = 0;
        for (LiquidCell c : new ArrayList<>(e.cells())) {
            int dx = c.x() - px, dz = c.z() - pz;
            if (c.volume() > 0 && !c.boundary() && dx * dx + dz * dz <= radius * radius)
                taken += e.remove(c.x(), c.floor() + 1e-3, c.z(), c.volume());
        }
        long t = taken;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "AFL water: took out %s L within %d m.", litres(t), radius)), false);
        return 1;
    }

    private static int pause(CommandSourceStack source, boolean paused) {
        LiquidWorld.state(source.getLevel()).paused = paused;
        source.sendSuccess(() -> Component.literal(paused ? "AFL water: paused (nothing flows; saving and loading go on)." : "AFL water: flowing again."), false);
        return 1;
    }

    private static void send(CommandSourceStack source, List<String> lines) {
        Component all = Component.literal(String.join("\n", lines)).withStyle(ChatFormatting.GRAY);
        source.sendSuccess(() -> all, false);
    }
}
