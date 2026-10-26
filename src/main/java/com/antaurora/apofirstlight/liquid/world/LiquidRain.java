package com.antaurora.apofirstlight.liquid.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.liquid.LiquidEngine;
import com.antaurora.apofirstlight.liquid.LiquidSoak;
import com.antaurora.apofirstlight.liquid.LiquidUnits;
import com.antaurora.apofirstlight.liquid.SurfaceWater;
import com.antaurora.apofirstlight.liquid.ground.BlockGroundView;
import com.antaurora.apofirstlight.liquid.ground.GroundMaterialV0;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.weather.AflWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Natural rain into the world (docs/gameplay/water_rain_v1_design.md 4.1, package 3a): every loaded chunk of the overworld
 * has a SurfaceWater, settled every {@value #PASS} ticks (a fortieth of the chunks each tick) with the regional weather's
 * rain over that chunk (AflWeather, mm a game hour). A chunk with nothing on it (no water, leaves dry, ground pale, soil
 * recovered) and no rain is passed over: nothing would change (first stress test 2026-10-25: 1,480 chunks in rain,
 * 53 ns a column, 1.0 ms a tick at 20 ticks).
 * <ul>
 *   <li>Each column: the top non-leaf block that blocks movement (heightmap MOTION_BLOCKING_NO_LEAVES) is what the rain
 *   lands on, a roof included, so the floor under it stays dry; leaves above it (MOTION_BLOCKING higher) are a canopy;
 *   water standing open there (the engine's) takes the rain in; a vanilla water or lava block is a water body.</li>
 *   <li>Once a settle: each chunk's settle covers exactly the ticks since its last one. A chunk starts counting when it
 *   loads (the rain while it was away is package 3e's, on C's AflTime) and stops when it unloads, so no rain is settled
 *   twice (requirement B).</li>
 *   <li>Saved in the chunk's NBT ({@value #TAG}), apart from the engine's liquid.</li>
 *   <li>The client: per column how dark the ground is and how fast it pales (LiquidPackets.Wet), sent only when a column
 *   starts or stops holding water, so a steady rain or a slow drying sends nothing; the client fades it itself.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LiquidRain {
    public static final String TAG = "apocalypse_firstlight:surface_water";
    /** Ticks between two settles of a chunk. */
    static final int PASS = 40;
    /** A settle covers at most this many ticks (a stalled server does not pour a storm at once). */
    static final int MAX_TICKS = 400;
    /** What the client is told a column is: dry (not sent), dark while water stands, paling. */
    static final byte DRY = 0, HOLDING = 1, PALING = 2;

    private LiquidRain() {}

    /** One loaded chunk's surface water and what was last sent of it. */
    static final class Chunk {
        final int cx, cz;
        final SurfaceWater water;
        long last;
        int index;
        /** The top block each column's material was read from, and that material. */
        final BlockState[] top = new BlockState[SurfaceWater.COLUMNS];
        final GroundMaterialV0.Kind[] kind = new GroundMaterialV0.Kind[SurfaceWater.COLUMNS];
        /** What each column is now (DRY / HOLDING / PALING), what the client was last told, and the ground's height then. */
        final byte[] state = new byte[SurfaceWater.COLUMNS];
        final byte[] sent = new byte[SurfaceWater.COLUMNS];
        final short[] sentY = new short[SurfaceWater.COLUMNS];
        final short[] y = new short[SurfaceWater.COLUMNS];
        boolean dirty, settled;

        Chunk(int cx, int cz, SurfaceWater water) {
            this.cx = cx;
            this.cz = cz;
            this.water = water;
            // from a save, until the first settle reads the ground
            for (int i = 0; i < SurfaceWater.COLUMNS; i++) state[i] = water.surface(i) > 0 ? HOLDING : water.dark(i) > 0 ? PALING : DRY;
        }
    }

    static final class State {
        final ServerLevel level;
        final BlockGroundView ground;
        final LinkedHashSet<Long> toLoad = new LinkedHashSet<>();
        final Map<Long, byte[]> read = new ConcurrentHashMap<>();
        final Map<Long, byte[]> unloaded = new ConcurrentHashMap<>();
        final Map<Long, Chunk> chunks = new java.util.HashMap<>();
        final List<Chunk> order = new ArrayList<>();
        int cursor;
        /** Dev: rain forced over every chunk (mm a game hour), NaN: the weather's. */
        double forced = Double.NaN;
        /** Stress readout: settles, columns and nanoseconds so far, the last tick's nanoseconds, packets sent. */
        long settles, columns, nanos, lastNanos, packets, packetColumns;

        State(ServerLevel level) {
            this.level = level;
            ground = new BlockGroundView(level);
        }
    }

    private static final Map<ResourceKey<Level>, State> STATES = new ConcurrentHashMap<>();

    /** Only where the regional weather rains: the overworld. */
    static State state(ServerLevel level) {
        if (!Level.OVERWORLD.equals(level.dimension())) return null;
        return STATES.computeIfAbsent(level.dimension(), k -> new State(level));
    }

    static long key(int cx, int cz) {
        return ChunkPos.asLong(cx, cz);
    }

    // ---- chunks -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void read(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) return;
        if (!event.getData().contains(TAG, Tag.TAG_BYTE_ARRAY)) return;
        State st = state(level);
        if (st != null) st.read.put(chunk.getPos().toLong(), event.getData().getByteArray(TAG));
    }

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) return;
        State st = state(level);
        if (st != null) st.toLoad.add(chunk.getPos().toLong());
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) return;
        State st = STATES.get(level.dimension());
        if (st == null) return;
        long key = chunk.getPos().toLong();
        st.toLoad.remove(key);
        Chunk c = st.chunks.remove(key);
        if (c == null) {
            // read but never handed over: written back as it was
            byte[] data = st.read.remove(key);
            if (data != null) st.unloaded.put(key, data);
            return;
        }
        Chunk lastOne = st.order.remove(st.order.size() - 1);
        if (lastOne != c) {
            st.order.set(c.index, lastOne);
            lastOne.index = c.index;
        }
        if (c.water.worthSaving()) {
            st.unloaded.put(key, c.water.save());
            chunk.setUnsaved(true);
        }
    }

    @SubscribeEvent
    public static void save(ChunkDataEvent.Save event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) return;
        State st = STATES.get(level.dimension());
        if (st == null) return;
        long key = chunk.getPos().toLong();
        byte[] data = st.unloaded.remove(key);
        if (data == null) {
            Chunk c = st.chunks.get(key);
            if (c != null) data = c.water.worthSaving() ? c.water.save() : null;
            else data = st.read.get(key);
        }
        if (data != null) event.getData().putByteArray(TAG, data);
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Watch event) {
        State st = STATES.get(event.getLevel().dimension());
        if (st == null) return;
        Chunk c = st.chunks.get(key(event.getPos().x, event.getPos().z));
        if (c != null) AflNetwork.sendLiquid(event.getPlayer(), packet(st, c));
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level.dimension());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    /** Before the engine's step (LiquidWorld#tick): rain onto water flows the same tick. */
    static void tick(ServerLevel level, LiquidEngine engine) {
        State st = STATES.get(level.dimension());
        if (st == null) return;
        long t0 = System.nanoTime();
        long now = level.getGameTime();
        if (!st.toLoad.isEmpty()) {
            List<Long> ready = new ArrayList<>();
            for (long key : st.toLoad) if (level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key)) != null) ready.add(key);
            for (long key : ready) {
                st.toLoad.remove(key);
                if (st.chunks.containsKey(key)) continue;
                Chunk c = new Chunk(ChunkPos.getX(key), ChunkPos.getZ(key), SurfaceWater.load(st.read.remove(key)));
                // counted from now: the rain while it was away is not settled here (3e)
                c.last = now - level.random.nextInt(PASS);
                c.index = st.order.size();
                st.order.add(c);
                st.chunks.put(key, c);
            }
        }
        int n = st.order.size();
        if (n == 0) return;
        int per = (n + PASS - 1) / PASS;
        for (int k = 0; k < per; k++) {
            if (st.cursor >= st.order.size()) st.cursor = 0;
            Chunk c = st.order.get(st.cursor++);
            if (now - c.last < PASS) continue;
            settle(st, engine, c, now);
        }
        st.lastNanos = System.nanoTime() - t0;
        st.nanos += st.lastNanos;
    }

    private static void settle(State st, LiquidEngine engine, Chunk c, long now) {
        LevelChunk chunk = st.level.getChunkSource().getChunkNow(c.cx, c.cz);
        if (chunk == null) return;
        int ticks = (int) Math.min(MAX_TICKS, now - c.last);
        c.last = now;
        double mm = rainOver(st, c);
        if (mm <= 0 && c.water.idle()) return;
        int minY = st.level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        SurfaceWater w = c.water;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lx + 16 * lz;
                int x = (c.cx << 4) + lx, z = (c.cz << 4) + lz;
                int yTop = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
                int yAny = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
                double canopyMm = 0;
                if (yAny > yTop) {
                    BlockState leaf = chunk.getBlockState(pos.set(x, yAny, z));
                    if (leaf.is(BlockTags.LEAVES)) canopyMm = leaf.is(Blocks.SPRUCE_LEAVES) ? 2 : 1;
                }
                int sink;
                GroundMaterialV0.Kind kind = GroundMaterialV0.Kind.NONE;
                if (yTop < minY) sink = SurfaceWater.NOTHING;
                else if (engine.surfaceWet(x, z)) sink = SurfaceWater.WATER;
                else {
                    BlockState top = chunk.getBlockState(pos.set(x, yTop, z));
                    if (!top.getFluidState().isEmpty()) sink = SurfaceWater.BODY;
                    else {
                        sink = SurfaceWater.GROUND;
                        if (top != c.top[i] || c.kind[i] == null) {
                            c.top[i] = top;
                            c.kind[i] = st.ground.materialAt(x, yTop, z).kind();
                        }
                        kind = c.kind[i];
                    }
                }
                w.settle(i, mm, ticks, canopyMm, sink, kind);
                if (w.handed > 0 && !engine.fromSurface(x, z, w.handed)) w.keep(i);
                c.y[i] = (short) yTop;
                // dark while rain reaches it or water stands; the client pales it only once neither does
                byte state = sink != SurfaceWater.GROUND ? DRY : w.surface(i) > 0 || w.reached ? HOLDING : w.dark(i) > 0 ? PALING : DRY;
                c.state[i] = state;
                if (state != c.sent[i] || state != DRY && c.y[i] != c.sentY[i]) c.dirty = true;
            }
        }
        // the first settle knows the ground: how fast each column pales
        if (!c.settled) {
            c.settled = true;
            for (byte b : c.state) c.dirty |= b != DRY;
        }
        st.settles++;
        st.columns += SurfaceWater.COLUMNS;
        if (c.dirty) {
            c.dirty = false;
            Object p = packet(st, c);
            AflNetwork.sendTrackingChunk(chunk, p);
            st.packets++;
        }
    }

    /** Rain over the chunk now, mm a game hour: the weather at its middle; none where it would snow or never falls. */
    private static double rainOver(State st, Chunk c) {
        if (!Double.isNaN(st.forced)) return st.forced;
        int x = (c.cx << 4) + 8, z = (c.cz << 4) + 8;
        float[] v = AflWeather.at(st.level, x, z);
        if (v == null || v[AflWeather.RAIN] <= 0) return 0;
        BlockPos at = new BlockPos(x, st.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        Biome.Precipitation p = st.level.getBiome(at).value().getPrecipitationAt(at);
        return p == Biome.Precipitation.RAIN ? v[AflWeather.RAIN] : 0;
    }

    /** The chunk's dark ground for the client: every column not dry, its ground's height and how fast it pales. */
    private static LiquidPackets.Wet packet(State st, Chunk c) {
        int n = 0;
        byte[] state = c.state;
        for (int i = 0; i < SurfaceWater.COLUMNS; i++) if (state[i] != DRY) n++;
        byte[] column = new byte[n], dark = new byte[n];
        short[] y = new short[n];
        int[] fade = new int[n];
        int k = 0;
        for (int i = 0; i < SurfaceWater.COLUMNS; i++) {
            c.sent[i] = state[i];
            c.sentY[i] = c.y[i];
            if (state[i] == DRY) continue;
            column[k] = (byte) i;
            y[k] = c.y[i];
            dark[k] = (byte) Math.round(c.water.dark(i) * 255);
            GroundMaterialV0.Kind kind = c.kind[i] == null ? GroundMaterialV0.Kind.NONE : c.kind[i];
            fade[k] = state[i] == HOLDING ? 0 : Math.max(1, (int) Math.round(LiquidSoak.darkHours(kind) * LiquidUnits.TICKS_PER_GAME_HOUR));
            k++;
        }
        st.packetColumns += n;
        return new LiquidPackets.Wet(c.cx, c.cz, column, y, dark, fade);
    }

    // ---- dev --------------------------------------------------------------------------------------------------------

    /** The column's surface water for /afl dev water surface, or null where it is not tracked. */
    static String[] describe(ServerLevel level, int x, int z) {
        State st = STATES.get(level.dimension());
        Chunk c = st == null ? null : st.chunks.get(key(x >> 4, z >> 4));
        if (c == null) return null;
        int i = (x & 15) + 16 * (z & 15);
        SurfaceWater w = c.water;
        GroundMaterialV0.Kind kind = c.kind[i] == null ? GroundMaterialV0.Kind.NONE : c.kind[i];
        double fc = LiquidSoak.infiltrationMmH(kind), f0 = LiquidSoak.initialInfiltrationMmH(kind);
        double f = Math.max(fc, f0 - LiquidSoak.HORTON_K * w.soaked(i));
        return new String[]{
                String.format(java.util.Locale.ROOT, "Surface water (%d, %d), ground Y %d %s: rain now %.2f mm/h%s", x, z, c.y[i],
                        kind.name().toLowerCase(java.util.Locale.ROOT), rainOver(st, c), Double.isNaN(st.forced) ? "" : " (forced)"),
                String.format(java.util.Locale.ROOT, "  on leaves %.3f L, on the ground %.3f L (storage %.1f mm, runoff waiting for 3b %.3f L)",
                        w.canopy(i) / 1000.0, w.surface(i) / 1000.0, LiquidSoak.storageMm(kind), SurfaceWater.runoff(w.surface(i), kind) / 1000.0),
                String.format(java.util.Locale.ROOT, "  soaks in at %.1f mm/h now (dry %.1f, soaked %.1f; F %.2f mm), dark %.2f",
                        f, f0, fc, w.soaked(i), w.dark(i)),
                String.format(java.util.Locale.ROOT, "Chunk surface account (L): rain %.3f = leaves dried %.3f + soaked in %.3f + dried %.3f + into water %.3f + into water bodies %.3f + held %.3f; gap %d mL",
                        w.rain() / 1000.0, w.canopyEvaporation() / 1000.0, w.infiltration() / 1000.0, w.evaporation() / 1000.0,
                        w.toWater() / 1000.0, w.toBodies() / 1000.0, w.held() / 1000.0, w.verify())};
    }

    /** The stress readout for /afl dev water rainstats. */
    static String stats(ServerLevel level) {
        State st = STATES.get(level.dimension());
        if (st == null) return "AFL rain: no surface water in this dimension.";
        long gap = 0, held = 0, rain = 0;
        int wet = 0;
        for (Chunk c : st.order) {
            gap += c.water.verify();
            held += c.water.held();
            rain += c.water.rain();
            for (int i = 0; i < SurfaceWater.COLUMNS; i++) if (c.water.surface(i) > 0) wet++;
        }
        double perTick = st.settles == 0 ? 0 : st.nanos / 1e6 / Math.max(1, st.settles) * st.order.size() / PASS;
        return String.format(java.util.Locale.ROOT,
                "AFL rain: %d chunks (%d columns) settled every %d ticks; %d settles so far, %.0f ns a column, about %.3f ms a tick, last tick %.3f ms; %d packets (%d columns) sent; %d columns hold water; rain %.1f L, held %.1f L, gap %d mL%s",
                st.order.size(), st.order.size() * SurfaceWater.COLUMNS, PASS, st.settles,
                st.columns == 0 ? 0 : st.nanos / (double) st.columns, perTick, st.lastNanos / 1e6, st.packets, st.packetColumns,
                wet, rain / 1000.0, held / 1000.0, gap, Double.isNaN(st.forced) ? "" : String.format(java.util.Locale.ROOT, "; rain forced to %.1f mm/h", st.forced));
    }

    static void resetStats(ServerLevel level) {
        State st = STATES.get(level.dimension());
        if (st != null) st.settles = st.columns = st.nanos = st.packets = st.packetColumns = 0;
    }

    /** Dev: force rain everywhere (mm a game hour), NaN back to the weather's; false outside the overworld. */
    static boolean force(ServerLevel level, double mm) {
        State st = state(level);
        if (st == null) return false;
        st.forced = mm;
        return true;
    }
}
