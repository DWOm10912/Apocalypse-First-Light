package com.antaurora.apofirstlight.terrainsmooth;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.block.RoadStructureBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

import static com.antaurora.apofirstlight.terrainsmooth.SmoothRules.*;

/**
 * Terrain Smooth V1 (docs/worldgen/terrain_smooth_v1.md), the common half: natural surface slopes in mode B on the
 * natural ground blocks (GROUND; podzol, mud and clay since 2026-10-12; snow block, red sand and the seven badlands
 * terracottas since 2026-10-14, for the federal snow peaks, canyons and steppe) of the overworld, for collision, selection and suffocation on both sides (the client half draws
 * them, TerrainSmoothClient). Nothing is written to the world: the slopes are computed from the blocks.
 * <ul>
 *   <li>State: one Side per logical side. The server reads TerrainSmoothConfig when it starts and sends its state to every
 *   client (TerrainSmoothStatePacket); a client only follows. Each change bumps the side's epoch, which makes every chunk's
 *   data stale.</li>
 *   <li>Data: per loaded chunk (SmoothChunkData on the LevelChunk) the natural surface of each column, from the
 *   WORLD_SURFACE heightmap (kept by client and server alike) down through sky, plants, leaves and trunks. The client
 *   makes it when a chunk loads (its chunk builders need it), the server when first asked; both keep it current on every
 *   block change (Level / ServerLevel#onBlockStateChange) and drop it with the chunk.</li>
 *   <li>Shapes: the mixins ask #shape / #notSuffocating / #risesIntoBox. Anything that is not a natural surface top
 *   (all buried blocks, cave floors, blocks under roofs) returns after one array read; geometry is cached per chunk on
 *   the side's main thread and dropped for the 3 x 3 columns around every change.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class TerrainSmooth {
    private static final Logger LOG = LogUtils.getLogger();
    /** the approved look: gentle two-block slopes */
    public static final int MODE = SmoothRules.MODE_B;
    public static final Block[] GROUND = {Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.SAND, Blocks.GRAVEL, Blocks.STONE,
            Blocks.PODZOL, Blocks.MUD, Blocks.CLAY,
            Blocks.SNOW_BLOCK, Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
            Blocks.BROWN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA};

    /** One logical side's session state and counters. */
    public static final class Side {
        public final String name;
        private volatile boolean enabled, shapes = true;
        private volatile int epoch;
        public final LongAdder queries = new LongAdder(), hits = new LongAdder(), computed = new LongAdder(), computeNanos = new LongAdder(),
                chunks = new LongAdder(), chunkNanos = new LongAdder(), changes = new LongAdder();

        Side(String name) { this.name = name; }

        /** natural surfaces are smoothed (drawn on the client) */
        public boolean enabled() { return enabled; }

        /** collision and selection follow the slopes (a development switch; on by default) */
        public boolean shapes() { return shapes; }

        public boolean collides() { return enabled && shapes; }

        public int epoch() { return epoch; }

        /** Main thread of the side only. Switching on or off makes every chunk's data stale (it was not kept up while off). */
        public void set(boolean enabled, boolean shapes) {
            if (enabled != this.enabled) epoch++;
            this.enabled = enabled;
            this.shapes = shapes;
            anyCollides = CLIENT.collides() || SERVER.collides();
        }

        /** Drop every chunk's data (made again from the blocks when needed). */
        public void invalidate() { epoch++; }

        public void resetCounters() {
            for (LongAdder a : new LongAdder[]{queries, hits, computed, computeNanos, chunks, chunkNanos, changes}) a.reset();
        }

        public String summary() {
            long q = queries.sum(), h = hits.sum(), c = computed.sum(), n = chunks.sum();
            return String.format(Locale.ROOT, "%s: %s%s; %d slope queries at natural surface tops, %d cached (%.1f %%), %d computed (%.2f us each); %d chunks' surfaces made (%.1f us each); %d block changes",
                    name, enabled ? "on" : "off", enabled && !shapes ? " (visual only)" : "", q, h, q == 0 ? 0 : 100.0 * h / q, c,
                    c == 0 ? 0 : computeNanos.sum() / 1e3 / c, n, n == 0 ? 0 : chunkNanos.sum() / 1e3 / n, changes.sum());
        }
    }

    public static final Side CLIENT = new Side("client"), SERVER = new Side("server");
    /** either side collides: read first by every shape hook */
    static volatile boolean anyCollides;
    /** the client's main thread (set by TerrainSmoothClient): owner of the client's geometry caches */
    public static volatile Thread clientThread;

    /** The client half's reaction to a client block change (re-mesh), set by TerrainSmoothClient. */
    public interface ClientHooks {
        void blockChanged(int x, int y, int z, int oldSurface, int newSurface);
    }

    public static volatile ClientHooks clientHooks;

    private TerrainSmooth() {}

    public static Side side(Level level) { return level.isClientSide() ? CLIENT : SERVER; }

    /** The overworld only. */
    public static boolean applies(Level level) { return level.dimension() == Level.OVERWORLD; }

    /** GROUND (also the tag minecraft:snow_layer_can_survive_on, so snow stays on a sloped top: its face is no longer full). */
    public static boolean ground(Block b) {
        return b == Blocks.GRASS_BLOCK || b == Blocks.DIRT || b == Blocks.COARSE_DIRT || b == Blocks.SAND || b == Blocks.GRAVEL || b == Blocks.STONE
                || b == Blocks.PODZOL || b == Blocks.MUD || b == Blocks.CLAY
                || b == Blocks.SNOW_BLOCK || b == Blocks.RED_SAND || b == Blocks.TERRACOTTA || b == Blocks.WHITE_TERRACOTTA || b == Blocks.ORANGE_TERRACOTTA
                || b == Blocks.YELLOW_TERRACOTTA || b == Blocks.BROWN_TERRACOTTA || b == Blocks.RED_TERRACOTTA || b == Blocks.LIGHT_GRAY_TERRACOTTA;
    }

    /** A smoothed top's collision: the slope boxes; mud's sit SmoothShapes.MUD_SINK lower, as vanilla mud's (feet sink in a little). */
    static VoxelShape collision(Block block, long g) {
        return block == Blocks.MUD ? SmoothShapes.sunk(g) : SmoothShapes.shape(g, true);
    }

    /** SmoothRules cell code of a block state. */
    public static int cell(BlockState s) {
        if (s.isAir()) return AIR;
        if (!s.getFluidState().isEmpty()) return FLUID;
        Block b = s.getBlock();
        // a bridge's invisible collision (road_structure, under the client's mesh) covers the ground like a plant: the ground
        // under it is its column's surface and smooths with its neighbours, so where a skewed wall leaves part of a cell
        // uncovered that ground meets the ground in front without a block step (docs/worldgen/federal_highways_c3.md 10.11)
        if (b instanceof BushBlock || b instanceof SnowLayerBlock || b instanceof RoadStructureBlock) return COVER;
        if (ground(b)) return NATURAL;
        if (s.is(BlockTags.LEAVES)) return LEAF;
        if (s.is(BlockTags.LOGS) || b instanceof VineBlock || b instanceof HugeMushroomBlock || b == Blocks.COCOA || b == Blocks.BEE_NEST) return TREE;
        return OTHER;
    }

    // ------------------------------------------------------------------------------------------------- chunk data

    /** On the side's main thread: the owner of caches and the only writer of chunk data. */
    static boolean owner(Level level) {
        if (level.isClientSide()) return Thread.currentThread() == clientThread;
        MinecraftServer server = level.getServer();
        return server != null && server.isSameThread();
    }

    /** The chunk's current data, made here when missing or stale on the side's main thread; else null (never on other threads). */
    public static SmoothChunkData data(Level level, LevelChunk chunk) {
        Side side = side(level);
        SmoothChunkData d = ((SmoothChunkHolder) chunk).afl$smoothData();
        if (d != null && d.epoch == side.epoch()) return d;
        if (!owner(level)) return null;
        d = make(chunk, side);
        ((SmoothChunkHolder) chunk).afl$setSmoothData(d);
        return d;
    }

    /** The chunk's current data without making it (chunk-builder threads). */
    public static SmoothChunkData peek(LevelChunk chunk, Side side) {
        SmoothChunkData d = ((SmoothChunkHolder) chunk).afl$smoothData();
        return d != null && d.epoch == side.epoch() ? d : null;
    }

    static SmoothChunkData make(LevelChunk chunk, Side side) {
        long begin = System.nanoTime();
        SmoothChunkData d = new SmoothChunkData(side.epoch());
        var pos = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) d.surface[z << 4 | x] = columnSurface(chunk, pos, x0 + x, z0 + z);
        side.chunks.increment();
        side.chunkNanos.add(System.nanoTime() - begin);
        return d;
    }

    /** SmoothRules#surface of one column of a chunk, from its WORLD_SURFACE heightmap (the highest non-air block) down. */
    static int columnSurface(LevelChunk chunk, BlockPos.MutableBlockPos pos, int x, int z) {
        int bottom = chunk.getMinBuildHeight();
        for (int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15); y >= bottom; y--) {
            int k = cell(chunk.getBlockState(pos.set(x, y, z)));
            if (skyward(k)) continue;
            return k == NATURAL ? y : NO_SURFACE;
        }
        return NO_SURFACE;
    }

    // ------------------------------------------------------------------------------------------------- geometry

    /** Level cells over loaded chunks only (never loading one); surfaces made on the main thread, read-only elsewhere. */
    private static final class LevelCells implements SmoothRules.Cells {
        Level level;
        boolean owner;
        private int lastX, lastZ;
        private LevelChunk last;
        private boolean any;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        LevelChunk chunk(int cx, int cz) {
            if (any && cx == lastX && cz == lastZ) return last;
            any = true;
            lastX = cx;
            lastZ = cz;
            return last = level.getChunkSource().getChunkNow(cx, cz);
        }

        @Override
        public int cell(int x, int y, int z) {
            LevelChunk c = chunk(x >> 4, z >> 4);
            return c == null ? UNKNOWN : TerrainSmooth.cell(c.getBlockState(pos.set(x, y, z)));
        }

        @Override
        public float turf(int x, int y, int z, int k) {
            BlockState s = chunk(x >> 4, z >> 4) == null ? null : chunk(x >> 4, z >> 4).getBlockState(pos.set(x, y, z));
            return s != null && s.getBlock() instanceof GradedTurfBlock ? y + GradedTurfBlock.corners(s)[k] : Float.NaN;
        }

        @Override
        public int surface(int x, int z) {
            LevelChunk c = chunk(x >> 4, z >> 4);
            if (c == null) return NO_SURFACE;
            SmoothChunkData d = owner ? data(level, c) : peek(c, side(level));
            return d == null ? NO_SURFACE : d.surface(x, z);
        }

        void reset() {
            level = null;
            last = null;
            any = false;
        }
    }

    private static final ThreadLocal<LevelCells> CELLS = ThreadLocal.withInitial(LevelCells::new);

    /** Geometry key of block (x, y, z) of chunk on its side (SmoothRules#geometry; cached on the main thread), -1 = cube. */
    public static long geometry(Level level, LevelChunk chunk, int x, int y, int z) {
        Side side = side(level);
        boolean owner = owner(level);
        SmoothChunkData d = owner ? data(level, chunk) : peek(chunk, side);
        if (d == null || d.surface(x, z) != y) return -1;   // not its column's natural surface: nearly every query ends here
        side.queries.increment();
        int key = SmoothChunkData.key(x, y, z);
        if (owner) {
            long v = d.cached(key);
            if (v != Long.MIN_VALUE) {
                side.hits.increment();
                return v;
            }
        }
        long begin = System.nanoTime();
        LevelCells c = CELLS.get();
        c.level = level;
        c.owner = owner;
        long g;
        try {
            g = SmoothRules.geometry(c, MODE, x, y, z);
        } finally {
            c.reset();
        }
        side.computed.increment();
        side.computeNanos.add(System.nanoTime() - begin);
        if (owner) d.store(key, g);
        return g;
    }

    /** Fresh geometry, ignoring the cache (diagnostics: compare with #geometry). */
    public static long fresh(Level level, LevelChunk chunk, int x, int y, int z) {
        SmoothChunkData d = owner(level) ? data(level, chunk) : peek(chunk, side(level));
        if (d == null || d.surface(x, z) != y) return -1;
        LevelCells c = CELLS.get();
        c.level = level;
        c.owner = owner(level);
        try {
            return SmoothRules.geometry(c, MODE, x, y, z);
        } finally {
            c.reset();
        }
    }

    /** Geometry for a shape query on a level, or on the chunk the block is in (the suffocation test passes that); -1 = cube. */
    static long sloped(BlockGetter getter, BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        Level level;
        LevelChunk chunk = null;
        if (getter instanceof LevelChunk c) {
            level = c.getLevel();
            if (c.getPos().x == x >> 4 && c.getPos().z == z >> 4) chunk = c;
        } else if (getter instanceof Level l) {
            level = l;
        } else {
            return -1;   // render, pathfinding and worldgen regions: vanilla
        }
        if (!side(level).collides() || !applies(level)) return -1;
        if (chunk == null) chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        return chunk == null ? -1 : geometry(level, chunk, x, y, z);
    }

    /** Hook (BlockStateBase#getCollisionShape, collision true / #getShape): the slope boxes, or null for the vanilla shape. */
    public static VoxelShape shape(Block block, BlockGetter getter, BlockPos pos, boolean collision) {
        if (!anyCollides || !ground(block)) return null;
        long g = sloped(getter, pos);
        return g < 0 ? null : collision ? collision(block, g) : SmoothShapes.shape(g, false);
    }

    /**
     * Hooks (MoveControl#tick, PathNavigation#getGroundY; V1.1, 2026-10-11): the height a smoothed top is stood on at
     * (fx, fz) within its cell, the collision column there (VoxelShape#max(Y, z, x)), above the cell's bottom; NaN for a
     * vanilla block. Vanilla takes the shape's highest point, a slope's highest corner, so a mob walking on a slope is
     * always "below" its block and its next node always looks a step up: it jumped at every slope.
     */
    public static double standingTop(Block block, BlockGetter getter, BlockPos pos, double fx, double fz) {
        if (!anyCollides || !ground(block)) return Double.NaN;
        long g = sloped(getter, pos);
        if (g < 0) return Double.NaN;
        double top = collision(block, g).max(Direction.Axis.Y, Mth.clamp(fz, 0.0D, 0.9999D), Mth.clamp(fx, 0.0D, 0.9999D));
        return Double.isInfinite(top) ? Double.NaN : top;
    }

    /**
     * Hook (BlockStateBase#isSuffocating): a smoothed top is not a full block any more, so it must not suffocate (the
     * local player's push out of blocks would shove the player off every slope box; P1, 2026-10-11). Every other block,
     * buried ground included, keeps the vanilla answer.
     */
    public static boolean notSuffocating(Block block, BlockGetter getter, BlockPos pos) {
        return anyCollides && ground(block) && sloped(getter, pos) >= 0;
    }

    /**
     * Hook (BlockCollisions, in place of the per-state "large collision shape" flag for the ground blocks): a smoothed top
     * rises up to 0.75 above its own cell, so the layer just under a box must be asked about it. Only that layer, so the
     * rest of the collision sweep stays exactly vanilla; whether the block really is a sloped top is up to the shape.
     */
    public static boolean risesIntoBox(BlockState state, BlockPos pos, AABB box) {
        return anyCollides && ground(state.getBlock()) && pos.getY() == Mth.floor(box.minY - 1.0E-7D) - 1;
    }

    /** How far to lift an entity box out of the smoothed tops under it (SmoothShapes#lift), 0 if not inside. */
    public static double lift(Level level, AABB box) {
        if (!side(level).collides() || !applies(level)) return 0;
        List<VoxelShape> tops = new ArrayList<>(4);
        var pos = new BlockPos.MutableBlockPos();
        int feet = Mth.floor(box.minY);
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++)
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) continue;
                for (int y = feet - 2; y <= feet + 1; y++) {   // tops reach up to 2 above their floor
                    Block block = chunk.getBlockState(pos.set(x, y, z)).getBlock();
                    if (!ground(block)) continue;
                    long g = geometry(level, chunk, x, y, z);
                    if (g >= 0) tops.add(collision(block, g).move(x, y, z));
                }
            }
        return tops.isEmpty() ? 0 : SmoothShapes.lift(box, tops);
    }

    // ------------------------------------------------------------------------------------------------- lifecycle

    /**
     * Hook (Level#onBlockStateChange on the client, ServerLevel#onBlockStateChange on the server, which overrides it
     * without calling it): keep the column's natural surface current and drop the cached slopes that read the column.
     */
    public static void changed(Level level, BlockPos pos) {
        Side side = side(level);
        if (!side.enabled() || !applies(level)) return;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        side.changes.increment();
        int before = NO_SURFACE, after = NO_SURFACE;
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk != null) {
            SmoothChunkData d = peek(chunk, side);
            if (d != null) {
                int i = SmoothChunkData.index(x, z);
                before = d.surface[i];
                after = d.surface[i] = columnSurface(chunk, new BlockPos.MutableBlockPos(), x, z);
            }
        }
        for (int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++) for (int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++) clearGeometry(level, side, cx, cz);
        ClientHooks hooks = clientHooks;
        if (hooks != null && level.isClientSide()) hooks.blockChanged(x, y, z, before, after);
    }

    private static void clearGeometry(Level level, Side side, int cx, int cz) {
        LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
        if (c == null) return;
        SmoothChunkData d = peek(c, side);
        if (d != null) d.clearGeometry();
    }

    /** Slopes along a chunk's border read its neighbours: their cached geometry is stale when it comes or goes. */
    private static void clearAround(Level level, Side side, ChunkPos p) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) clearGeometry(level, side, p.x + dx, p.z + dz);
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(event.getLevel() instanceof Level level)) return;
        Side side = side(level);
        if (!side.enabled() || !applies(level)) return;
        // the client's chunk builders read surfaces off-thread, so the client makes them now (also for a chunk object the
        // client refills from a new packet); the server on the first query
        ((SmoothChunkHolder) chunk).afl$setSmoothData(level.isClientSide() ? make(chunk, side) : null);
        clearAround(level, side, chunk.getPos());
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(event.getLevel() instanceof Level level)) return;
        Side side = side(level);
        ((SmoothChunkHolder) chunk).afl$setSmoothData(null);
        if (side.enabled() && applies(level)) clearAround(level, side, chunk.getPos());
    }

    @SubscribeEvent
    public static void serverAboutToStart(ServerAboutToStartEvent event) {
        boolean on;
        try {
            on = TerrainSmoothConfig.ENABLED.get();
        } catch (IllegalStateException notLoaded) {
            on = true;
        }
        SERVER.set(on, true);
        LOG.info("[AFL terrain smooth] natural surface slopes {} for this world (config terrainSmoothEnabled)", on ? "on" : "off");
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        SERVER.set(false, true);
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AflNetwork.sendTerrainSmooth(player, state());
    }

    public static TerrainSmoothStatePacket state() { return new TerrainSmoothStatePacket(SERVER.enabled(), SERVER.shapes()); }

    /** For the development command: change this server session's state and send it to every player. */
    public static void setServer(MinecraftServer server, boolean enabled, boolean shapes) {
        SERVER.set(enabled, shapes);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) AflNetwork.sendTerrainSmooth(p, state());
    }
}
