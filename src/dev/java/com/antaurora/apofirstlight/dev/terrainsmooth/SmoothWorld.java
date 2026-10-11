package com.antaurora.apofirstlight.dev.terrainsmooth;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * The world side of the terrain smoothing prototype (P1): one configuration per logical side (the client's and the
 * server's, set by the same command), the block classification for SmoothRules, the per-position geometry cache that
 * collision and selection queries use, and the box shapes.
 * <p>
 * Client and server compute from their own level with the same rules and the same configuration, so they agree wherever
 * their blocks agree (the client's blocks are the server's, synced). Nothing reads a chunk that is not loaded (such a
 * column counts as unknown and pins its corners), so a collision query never loads or generates a chunk.
 */
public final class SmoothWorld {
    static final Block[] SURFACE = {Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.SAND, Blocks.GRAVEL, Blocks.STONE};

    /** One side's settings and caches. */
    public static final class Config {
        public final String side;
        volatile int mode = SmoothRules.MODE_OFF, minX, minZ, size;
        volatile boolean shapes = true;
        volatile ResourceKey<Level> dimension = Level.OVERWORLD;
        private final Long2LongOpenHashMap cache = new Long2LongOpenHashMap();
        final LongAdder queries = new LongAdder(), hits = new LongAdder(), computeNanos = new LongAdder(), clears = new LongAdder();

        Config(String side) {
            this.side = side;
            cache.defaultReturnValue(Long.MIN_VALUE);
        }

        boolean active() { return mode != SmoothRules.MODE_OFF; }

        boolean contains(int x, int z) { return SmoothRules.inArea(minX, minZ, size, x, z); }

        /** Within the area or the ring of columns whose blocks feed the area's shapes. */
        boolean near(int x, int z) { return x >= minX - 2 && x < minX + size + 2 && z >= minZ - 2 && z < minZ + size + 2; }

        void set(int mode, int centreX, int centreZ, int size, boolean shapes, ResourceKey<Level> dimension) {
            this.minX = centreX - size / 2;
            this.minZ = centreZ - size / 2;
            this.size = size;
            this.shapes = shapes;
            this.dimension = dimension;
            this.mode = mode;
            clear();
            anyActive = CLIENT.active() || SERVER.active();
        }

        void clear() {
            synchronized (cache) { cache.clear(); cache.trim(1024); }
            clears.increment();
        }

        int cached() { synchronized (cache) { return cache.size(); } }

        /** Geometry of the block at pos for collision and selection, cached until a block near the area changes. */
        long cachedGeometry(Level level, BlockPos pos) {
            // read the (often mutable) position once: the key and the computed slope always belong to the same block
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            long key = BlockPos.asLong(x, y, z);
            queries.increment();
            synchronized (cache) {
                long v = cache.get(key);
                if (v != Long.MIN_VALUE) { hits.increment(); return v; }
            }
            long begin = System.nanoTime();
            long g = geometry(level, this, x, y, z);
            computeNanos.add(System.nanoTime() - begin);
            synchronized (cache) {
                if (cache.size() > 262_144) cache.clear();
                cache.put(key, g);
            }
            return g;
        }

        public String summary() {
            long q = queries.sum(), h = hits.sum(), misses = q - h;
            return String.format(java.util.Locale.ROOT, "%s: %d shape queries in the area, %d cached (%.1f %%), %d computed %.2f ms (%.2f us each), cache %d positions, cleared %d times",
                    side, q, h, q == 0 ? 0 : 100.0 * h / q, misses, computeNanos.sum() / 1e6, misses == 0 ? 0 : computeNanos.sum() / 1e3 / misses, cached(), clears.sum());
        }
    }

    public static final Config CLIENT = new Config("client"), SERVER = new Config("server");
    /** either side active: read by the per-state hasLargeCollisionShape hook */
    static volatile boolean anyActive;
    /** set by the mixins when they first run: tells status whether the dev mixin config was loaded */
    static volatile boolean hooksSeen;

    private SmoothWorld() {}

    public static boolean isAnyActive() { return anyActive; }

    public static boolean hooksSeen() { return hooksSeen; }

    public static void markHooksSeen() { hooksSeen = true; }

    /** A chunk loading in on either side: positions next to it may have been computed as unknown. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
    public static final class Events {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void chunkLoaded(net.minecraftforge.event.level.ChunkEvent.Load event) {
            if (!anyActive || !(event.getLevel() instanceof Level level)) return;
            Config c = level.isClientSide() ? CLIENT : SERVER;
            int x = event.getChunk().getPos().getMinBlockX(), z = event.getChunk().getPos().getMinBlockZ();
            if (c.active() && x + 15 >= c.minX - 2 && x < c.minX + c.size + 2 && z + 15 >= c.minZ - 2 && z < c.minZ + c.size + 2) c.clear();
        }
    }

    static boolean surface(Block b) {
        for (Block s : SURFACE) if (s == b) return true;
        return false;
    }

    static int cell(BlockState s) {
        if (s.isAir()) return SmoothRules.AIR;
        if (!s.getFluidState().isEmpty()) return SmoothRules.FLUID;
        Block b = s.getBlock();
        if (b instanceof BushBlock || b instanceof SnowLayerBlock) return SmoothRules.COVER;
        if (s.is(BlockTags.LEAVES)) return SmoothRules.LEAF;
        return surface(b) ? SmoothRules.NATURAL : SmoothRules.OTHER;
    }

    /** Cell lookup over a level (loaded chunks only) or a render region. */
    private static final class Probe implements SmoothRules.Cells {
        BlockGetter getter;
        LevelReader reader;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        @Override
        public int cell(int x, int y, int z) {
            if (reader != null && !reader.hasChunk(x >> 4, z >> 4)) return SmoothRules.UNKNOWN;
            return SmoothWorld.cell(getter.getBlockState(pos.set(x, y, z)));
        }
    }

    private static final ThreadLocal<Probe> PROBE = ThreadLocal.withInitial(Probe::new);

    /** Geometry key of block (x, y, z) read from getter (a level or a render region); -1 = vanilla. */
    static long geometry(BlockGetter getter, Config c, int x, int y, int z) {
        if (!c.active() || !c.contains(x, z)) return -1;
        Probe p = PROBE.get();
        p.getter = getter;
        p.reader = getter instanceof Level level ? level : null;
        try {
            return SmoothRules.geometry(p, c.mode, c.minX, c.minZ, c.size, x, y, z);
        } finally {
            p.getter = null;
            p.reader = null;
        }
    }

    // ---------------------------------------------------------------- shapes (collision and selection share them)

    static final int SELECT_GRID = SmoothShapes.SELECT_GRID;

    /** Hook for BlockStateBase#getCollisionShape (collision true) / #getShape: the slope boxes, or null for vanilla. */
    public static VoxelShape hook(BlockState state, BlockGetter getter, BlockPos pos, boolean collision) {
        long g = sloped(state, getter, pos);
        return g < 0 ? null : SmoothShapes.shape(g, collision);
    }

    /** Geometry of a smoothed top at pos when its shapes follow the slope here, else -1 (vanilla). */
    private static long sloped(BlockState state, BlockGetter getter, BlockPos pos) {
        if (!anyActive || !surface(state.getBlock())) return -1;
        // the collision sweep passes the level; its suffocation test passes the chunk the block is in
        Level level = getter instanceof Level l ? l : getter instanceof net.minecraft.world.level.chunk.LevelChunk chunk ? chunk.getLevel() : null;
        if (level == null) return -1;
        Config c = level.isClientSide() ? CLIENT : SERVER;
        if (!c.active() || !c.shapes || level.dimension() != c.dimension || !c.contains(pos.getX(), pos.getZ())) return -1;
        return c.cachedGeometry(level, pos);
    }

    /**
     * Hook for BlockStateBase#isSuffocating: a smoothed top is not a full block any more, so it must not suffocate. The
     * vanilla predicate (blocks motion and a full collision cube) reads the per-state cache, which still says full cube.
     * Without this the local player's push out of blocks (LocalPlayer#moveTowardsClosestSpace, every tick, at the four
     * corners: is any suffocating block's collision inside the full-width column from the feet up?) shoves the player off
     * every box that rises above the feet: no walking uphill, shaking on uneven ground (user test 2026-10-11).
     */
    public static boolean notSuffocating(BlockState state, BlockGetter getter, BlockPos pos) {
        return sloped(state, getter, pos) >= 0;
    }

    /** How far to lift an entity box out of the smoothed tops under it on this side (SmoothShapes#lift), 0 if not inside. */
    static double lift(Level level, AABB box) {
        if (!anyActive) return 0;
        List<VoxelShape> tops = new ArrayList<>(4);
        var pos = new BlockPos.MutableBlockPos();
        int feet = Mth.floor(box.minY);
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++)
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++)
                for (int y = feet - 2; y <= feet + 1; y++) {   // tops reach up to 2 above their floor
                    long g = sloped(level.getBlockState(pos.set(x, y, z)), level, pos);
                    if (g >= 0) tops.add(SmoothShapes.shape(g, true).move(x, y, z));
                }
        return tops.isEmpty() ? 0 : SmoothShapes.lift(box, tops);
    }

    /** Hook for BlockStateBase#hasLargeCollisionShape: mode B tops rise above their cell (by up to 0.75). */
    public static boolean large(BlockState state) {
        return anyActive && surface(state.getBlock());
    }

    /** Hook for Level#onBlockStateChange (client) and ServerLevel#onBlockStateChange (server, which overrides it): a block near the area changed on this side. */
    public static void changed(Level level, BlockPos pos) {
        Config c = level.isClientSide() ? CLIENT : SERVER;
        if (!c.active() || !c.near(pos.getX(), pos.getZ())) return;
        c.clear();
        if (level.isClientSide()) SmoothPrototype.blockChanged(pos);
    }
}
