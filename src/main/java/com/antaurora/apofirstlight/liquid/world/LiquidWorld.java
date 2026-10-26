package com.antaurora.apofirstlight.liquid.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.liquid.LiquidCell;
import com.antaurora.apofirstlight.liquid.LiquidColumn;
import com.antaurora.apofirstlight.liquid.LiquidEngine;
import com.antaurora.apofirstlight.liquid.ground.BlockGroundView;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Physical water V1 package 2 (docs/gameplay/water_v1_design.md 16): the liquid engine in the game. One LiquidEngine per
 * server level over the whole build height, its ground the blocks' collision shapes (BlockGroundView) until C's GroundView
 * merges.
 * <ul>
 *   <li>Chunks: a chunk's liquid is saved in its own NBT ({@value #TAG}, the engine's bytes, 5.10). Read when the chunk is
 *   read, handed to the engine at the end of the first tick the chunk is reachable; on unload the engine lets it go and its
 *   bytes are written by the save right after.</li>
 *   <li>Ground changes: every block change on the server (ServerLevel#onBlockStateChange, LiquidServerLevelMixin) next to a
 *   column the liquid has rebuilds the columns around it at the end of the tick, the liquid kept to the millilitre.</li>
 *   <li>Sync: chunks whose liquid moved go to the players tracking them at most every {@value #SYNC_EVERY} ticks
 *   (LiquidPackets.Surface: each wet cell's floor and level); a player starting to track a chunk gets it at once.</li>
 *   <li>Vanilla water flows as before (5.8 stage 0): this neither reads nor places water blocks.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LiquidWorld {
    /** The chunk NBT key of the liquid's bytes. */
    public static final String TAG = "apocalypse_firstlight:water";
    static final int SYNC_EVERY = 2, MARK_EVERY = 100;

    private LiquidWorld() {}

    /** One level's liquid. */
    static final class State {
        final ServerLevel level;
        final LiquidEngine engine;
        /** Chunks loaded in the game, not yet in the engine (waiting to be reachable). */
        final LinkedHashSet<Long> toLoad = new LinkedHashSet<>();
        /** Bytes read with a chunk, until the engine takes them (the chunk's read may be off the server thread). */
        final Map<Long, byte[]> read = new ConcurrentHashMap<>();
        /** Bytes of chunks the engine let go, for the save that follows the unload. */
        final Map<Long, byte[]> unloaded = new ConcurrentHashMap<>();
        /** Block changes next to the liquid this tick: chunk key -> {x0, z0, x1, z1}. */
        final Map<Long, int[]> changed = new TreeMap<>();
        boolean paused;
        long tickNanos, lastTickNanos;
        /** The last tick's parts (ns): rain settling, players pouring, the engine's step, the sync to clients. */
        long lastRainNanos, lastPourNanos, lastEngineNanos, lastSyncNanos;
        int syncedChunks;
        /** What each chunk's last Surface showed (LiquidPackets.Surface#digest): the same again is not sent. */
        final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap sent = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        /** Readout: Surface packets sent and skipped as unchanged since the last reset. */
        long surfacesSent, surfacesSkipped;
        /**
         * /afl dev water perf: ns summed since the last reading, by part (chunks loading and ground changes, natural rain,
         * pours, engine, sync), the ticks counted and the syncs among them, and the engine's own phases at that reading.
         */
        final long[] perfNanos = new long[5];
        long perfTicks, perfSyncs;
        long[] perfSpent = new long[4];
        /** Players pouring (LiquidPours), by entity id. */
        final Map<Integer, LiquidPours.Pour> pours = new java.util.LinkedHashMap<>();

        State(ServerLevel level) {
            this.level = level;
            engine = new LiquidEngine(new BlockGroundView(level), level.getMinBuildHeight(), level.getMaxBuildHeight());
            engine.setTick(level.getGameTime());
        }
    }

    /** Concurrent: a chunk's read (ChunkDataEvent.Load) may come off the server thread. */
    private static final Map<ResourceKey<Level>, State> STATES = new ConcurrentHashMap<>();

    static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level.dimension(), k -> new State(level));
    }

    /** The level's engine (dev commands, readouts). */
    public static LiquidEngine engine(ServerLevel level) {
        return state(level).engine;
    }

    /** The level's engine when it has one already (WaterQuery: asking about water makes none). */
    public static LiquidEngine engineIfPresent(ServerLevel level) {
        State st = STATES.get(level.dimension());
        return st == null ? null : st.engine;
    }

    // ---- chunks ---------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void read(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) return;
        if (!event.getData().contains(TAG, Tag.TAG_BYTE_ARRAY)) return;
        state(level).read.put(chunk.getPos().toLong(), event.getData().getByteArray(TAG));
    }

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) return;
        state(level).toLoad.add(chunk.getPos().toLong());
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) return;
        State st = STATES.get(level.dimension());
        if (st == null) return;
        long key = chunk.getPos().toLong();
        st.toLoad.remove(key);
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        st.sent.remove(LiquidColumn.chunkKey(cx, cz));
        if (!st.engine.loaded(cx, cz)) return;
        boolean keep = st.engine.worthSaving(cx, cz);
        byte[] data = st.engine.unloadChunk(cx, cz);
        if (keep) {
            st.unloaded.put(key, data);
            chunk.setUnsaved(true);   // the save comes right after this event (ChunkMap#scheduleUnload)
        }
    }

    @SubscribeEvent
    public static void save(ChunkDataEvent.Save event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) return;
        State st = STATES.get(level.dimension());
        if (st == null) return;
        long key = chunk.getPos().toLong();
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        byte[] data = st.unloaded.remove(key);
        if (data == null && st.engine.loaded(cx, cz)) data = st.engine.worthSaving(cx, cz) ? st.engine.saveChunk(cx, cz) : null;
        else if (data == null) data = st.read.get(key);   // read but never handed over: written back as it was
        if (data != null) event.getData().putByteArray(TAG, data);
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Watch event) {
        State st = STATES.get(event.getLevel().dimension());
        if (st == null || !st.engine.loaded(event.getPos().x, event.getPos().z)) return;
        List<LiquidCell> wet = LiquidPackets.Surface.shown(st.engine.wetCells(event.getPos().x, event.getPos().z));
        if (!wet.isEmpty()) AflNetwork.sendLiquid(event.getPlayer(), LiquidPackets.Surface.of(event.getPos().x, event.getPos().z, wet));
        var falls = st.engine.fallsIn(event.getPos().x, event.getPos().z);
        if (!falls.isEmpty()) AflNetwork.sendLiquid(event.getPlayer(), LiquidPackets.Falls.of(event.getPos().x, event.getPos().z, falls));
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level.dimension());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    // ---- ground changes ---------------------------------------------------------------------------------------------

    /** A block changed on the server (LiquidServerLevelMixin): the columns around it, if the liquid has any there. */
    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (before == after) return;
        State st = STATES.get(level.dimension());
        if (st == null) return;
        int x = pos.getX(), z = pos.getZ();
        boolean near = false;
        for (int dx = -1; dx <= 1 && !near; dx++) for (int dz = -1; dz <= 1 && !near; dz++) near = st.engine.hasColumn(x + dx, z + dz);
        if (!near) return;
        // an edge reads both its columns, and a smoothed slope's top follows its neighbours: the 3 x 3 around it
        int[] r = st.changed.computeIfAbsent(LiquidColumn.chunkKey(x >> 4, z >> 4), k -> new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
        r[0] = Math.min(r[0], x - 1);
        r[1] = Math.min(r[1], z - 1);
        r[2] = Math.max(r[2], x + 1);
        r[3] = Math.max(r[3], z + 1);
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        State st = STATES.get(level.dimension());
        if (st == null) return;
        long t0 = System.nanoTime();
        LiquidEngine e = st.engine;
        // chunks the game has loaded, once they are reachable
        if (!st.toLoad.isEmpty()) {
            List<Long> ready = new ArrayList<>();
            for (long key : st.toLoad) {
                int cx = net.minecraft.world.level.ChunkPos.getX(key), cz = net.minecraft.world.level.ChunkPos.getZ(key);
                if (level.getChunkSource().getChunkNow(cx, cz) != null) ready.add(key);
            }
            for (long key : ready) {
                st.toLoad.remove(key);
                int cx = net.minecraft.world.level.ChunkPos.getX(key), cz = net.minecraft.world.level.ChunkPos.getZ(key);
                if (!e.loaded(cx, cz)) e.loadChunk(cx, cz, st.read.remove(key));
            }
        }
        if (!st.changed.isEmpty()) {
            for (int[] r : st.changed.values()) e.groundChanged(r[0], r[1], r[2], r[3]);
            st.changed.clear();
        }
        // natural rain (package 3a) before the step, so rain onto water flows this tick
        long t1 = System.nanoTime();
        LiquidRain.tick(level, e);
        long t2 = System.nanoTime();
        LiquidPours.tick(st);
        long t3 = System.nanoTime();
        if (!st.paused) e.tick();
        long t4 = System.nanoTime();
        if (level.getGameTime() % SYNC_EVERY == 0) sync(st);
        long t5 = System.nanoTime();
        st.lastRainNanos = t2 - t1;
        st.lastPourNanos = t3 - t2;
        st.lastEngineNanos = t4 - t3;
        if (level.getGameTime() % SYNC_EVERY == 0) {
            st.lastSyncNanos = t5 - t4;
            st.perfSyncs++;
        }
        st.perfNanos[0] += t1 - t0;
        st.perfNanos[1] += t2 - t1;
        st.perfNanos[2] += t3 - t2;
        st.perfNanos[3] += t4 - t3;
        st.perfNanos[4] += t5 - t4;
        st.perfTicks++;
        if (level.getGameTime() % MARK_EVERY == 0) {
            // the autosave writes only chunks marked unsaved
            for (long key : e.wetChunks()) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(engineX(key), engineZ(key));
                if (chunk != null) chunk.setUnsaved(true);
            }
        }
        st.lastTickNanos = System.nanoTime() - t0;
        st.tickNanos += st.lastTickNanos;
    }

    private static void sync(State st) {
        st.syncedChunks = 0;
        for (long key : st.engine.takeChanged()) {
            int cx = engineX(key), cz = engineZ(key);
            LevelChunk chunk = st.level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            // films the client does not draw are left out, and what shows the same as last time is not sent again
            LiquidPackets.Surface p = LiquidPackets.Surface.of(cx, cz, LiquidPackets.Surface.shown(st.engine.wetCells(cx, cz)));
            int digest = p.digest();
            if (st.sent.containsKey(key) && st.sent.get(key) == digest) {
                st.surfacesSkipped++;
                continue;
            }
            st.sent.put(key, digest);
            AflNetwork.sendTrackingChunk(chunk, p);
            st.surfacesSent++;
            st.syncedChunks++;
        }
        // falls only when one starts, stops or its flow changes by a fifth (docs/gameplay/water_falling_v1_design.md 3.5)
        for (long key : st.engine.takeFallsChanged()) {
            int cx = engineX(key), cz = engineZ(key);
            LevelChunk chunk = st.level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            AflNetwork.sendTrackingChunk(chunk, LiquidPackets.Falls.of(cx, cz, st.engine.fallsIn(cx, cz)));
        }
    }

    /** The engine's chunk keys are x << 32 | z (LiquidColumn#chunkKey), not ChunkPos's layout. */
    static int engineX(long key) {
        return (int) (key >> 32);
    }

    static int engineZ(long key) {
        return (int) key;
    }
}
