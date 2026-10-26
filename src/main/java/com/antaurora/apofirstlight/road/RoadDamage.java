package com.antaurora.apofirstlight.road;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.road.RoadSurfaceQuery;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.antaurora.apofirstlight.road.RoadDamageChunk.N;

/**
 * Broken asphalt (road Mesh-ification P1, docs/worldgen/road_mesh_damage_v1_design.md 4, 7). A road column a strike has
 * broken into keeps which of its 12.5 cm sub-cells of the wearing course are gone (RoadDamageChunk); its road_surface
 * blocks stay (light, every block rule as before), collide as the aggregate base up to the asphalt's underside plus the
 * sub-cells still there ({@link #shape}), and are drawn with the gone sub-cells cut out (client: RoadDamageMesh). A
 * vehicle's wheel over a gone sub-cell stands on the base (RoadSurfaceQuery#sample).
 * <ul>
 *   <li>A strike ({@link #strike}) takes about RoadDamage.STRIKE_LITRES of asphalt where it lands: the sub-cells nearest
 *   the struck one, grown toward asphalt already broken or the pavement's edge (a hole widens from its rim), roughened by a
 *   hash, so no two are alike (design 4, RockCarve#cut's 2D cousin).</li>
 *   <li>Server: data on the LevelChunk (RoadDamageHolder), saved with it under {@link #TAG}; changed chunks go to their
 *   players at the end of the tick, every chunk to a player that starts tracking it. Client: one copy per chunk in
 *   {@link #CLIENT}.</li>
 *   <li>A road_surface block that goes (a command, an explosion) drops its column's entry: the block-sized hole of the
 *   whole block (RoadCells) takes over there.</li>
 * </ul>
 * The overworld only (the roads).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class RoadDamage {
    public static final String TAG = ApocalypseFirstLight.MOD_ID + ":road_damage";
    /** Asphalt one pick mattock strike takes, L (design 10, item 4: about 7 strikes for a county road's square metre). */
    public static final double STRIKE_LITRES = 15;
    /** A sub-cell's area, m². */
    public static final double SUB_AREA = 1.0 / (N * N);
    /** How far a piece may reach from the struck sub-cell, and how far a strike into a hole looks for asphalt, sub-cells. */
    static final int REACH = 5, SEEK = 3;
    /** A piece's growth: the pull toward broken or unpaved neighbours (per neighbour, sub-cells), the roughness. */
    static final double OPEN_PULL = 0.7, ROUGH = 0.5;
    /** Columns whose collision tops differ by more than this from the struck one are another road (a deck, a ramp), m. */
    static final double SAME_ROAD = 0.6;

    /** the client's copies by chunk (ChunkPos#asLong), replaced whole */
    public static final Map<Long, RoadDamageChunk> CLIENT = new ConcurrentHashMap<>();
    /** loaded overworld chunks on the server that hold broken asphalt (the shape hook's fast path; any thread) */
    private static final Set<Long> SERVER = ConcurrentHashMap.newKeySet();
    private static final LongOpenHashSet DIRTY = new LongOpenHashSet();

    /** The client half's reaction to a new copy (re-mesh the chunk and its neighbours), set by RoadClient. */
    public static volatile java.util.function.BiConsumer<Integer, Integer> clientChanged;

    private RoadDamage() {}

    // ------------------------------------------------------------------------------------------------- reading

    /** No broken asphalt anywhere on either side (the hooks' fast path). */
    public static boolean none() { return SERVER.isEmpty() && CLIENT.isEmpty(); }

    /** A chunk's broken asphalt on the level's side, null when none (client: any thread; level null: the client's copies). */
    public static RoadDamageChunk get(Level level, int cx, int cz) {
        if (level == null || level.isClientSide()) return CLIENT.isEmpty() ? null : CLIENT.get(ChunkPos.asLong(cx, cz));
        if (SERVER.isEmpty() || !SERVER.contains(ChunkPos.asLong(cx, cz)) || level.dimension() != Level.OVERWORLD) return null;
        LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
        return c == null ? null : ((RoadDamageHolder) c).afl$roadDamage();
    }

    /** Column (x, z)'s entry on the level's side, null when whole. */
    public static RoadDamageChunk.Col col(Level level, int x, int z) {
        RoadDamageChunk c = get(level, x >> 4, z >> 4);
        return c == null ? null : c.get(x, z);
    }

    /** Whether the asphalt over world point (x, z) is gone (a wheel there stands on the base). */
    public static RoadDamageChunk.Col goneAt(Level level, double x, double z) {
        if (none()) return null;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        RoadDamageChunk.Col c = col(level, bx, bz);
        if (c == null) return null;
        int sx = Math.min(N - 1, (int) ((x - bx) * N)), sz = Math.min(N - 1, (int) ((z - bz) * N));
        return RoadDamageChunk.gone(c.gone(), sx, sz) ? c : null;
    }

    /** The level of a shape query (a level, or the chunk the block is in), null for render, pathfinding and worldgen regions. */
    private static Level levelOf(BlockGetter getter) {
        if (getter instanceof LevelChunk c) return c.getLevel();
        return getter instanceof Level l ? l : null;
    }

    /**
     * Hook (RoadSurfaceBlock#getShape): a road_surface block in a broken column, its shape: the base up to the asphalt's
     * underside, the sub-cells still there up to the column's top, each within the block; null for a whole column.
     */
    public static VoxelShape shape(BlockGetter getter, BlockPos pos) {
        if (none()) return null;
        Level level = levelOf(getter);
        if (level == null || level.dimension() != Level.OVERWORLD) return null;
        RoadDamageChunk.Col c = col(level, pos.getX(), pos.getZ());
        if (c == null) return null;
        double base = Math.max(0, Math.min(1, c.top() - c.depth() - pos.getY())), top = Math.max(0, Math.min(1, c.top() - pos.getY()));
        return shapeOf(c.gone(), base, top);
    }

    private record Key(long gone, int base, int top) {}

    private static final Map<Key, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    /**
     * The base slab (0 .. base) and the boxes of the sub-cells still there (base .. top; greedy: runs along x, grown along z),
     * joined unoptimised and optimised once (memory project-dynamic-shape-first-load-cost), cached.
     */
    public static VoxelShape shapeOf(long gone, double base, double top) {
        Key key = new Key(gone, (int) Math.round(base * 1024), (int) Math.round(top * 1024));
        VoxelShape s = SHAPES.get(key);
        if (s != null) return s;
        if (SHAPES.size() > 32768) SHAPES.clear();
        double b = key.base / 1024.0, t = key.top / 1024.0, w = 1.0 / N;
        VoxelShape out = b > 0 ? Shapes.box(0, 0, 0, 1, b, 1) : Shapes.empty();
        if (t > b + 1e-6) {
            boolean[] solid = new boolean[N * N];
            for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) solid[x + N * z] = !RoadDamageChunk.gone(gone, x, z);
            for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) {
                if (!solid[x + N * z]) continue;
                int x1 = x + 1;
                while (x1 < N && solid[x1 + N * z]) x1++;
                int z1 = z + 1;
                while (z1 < N && row(solid, x, x1, z1)) z1++;
                for (int zz = z; zz < z1; zz++) for (int xx = x; xx < x1; xx++) solid[xx + N * zz] = false;
                out = Shapes.joinUnoptimized(out, Shapes.box(x * w, b, z * w, x1 * w, t, z1 * w), BooleanOp.OR);
            }
        }
        s = out.optimize();
        SHAPES.put(key, s);
        return s;
    }

    private static boolean row(boolean[] solid, int x0, int x1, int z) {
        for (int x = x0; x < x1; x++) if (!solid[x + N * z]) return false;
        return true;
    }

    // ------------------------------------------------------------------------------------------------- strikes

    /** A strike's result: the asphalt taken (L) and whether a column lost its last asphalt. */
    public record Taken(double litres, boolean emptied) {}

    /**
     * A column's asphalt as the server sees it: its entry, else (paved with asphalt, a road_surface top near yHint) a new
     * whole entry; null where there is no asphalt.
     */
    private static RoadDamageChunk.Col column(ServerLevel level, RoadWorks works, Long2ObjectOpenHashMap<RoadDamageChunk.Col> seen, int x, int z, double yHint) {
        long k = ((long) x << 32) ^ (z & 0xffffffffL);
        if (seen.containsKey(k)) return seen.get(k);
        RoadDamageChunk.Col c = null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk != null) {
            RoadDamageChunk d = ((RoadDamageHolder) chunk).afl$roadDamage();
            c = d == null ? null : d.get(x, z);
            if (c == null) {
                double[] a = RoadSurfaceQuery.asphalt(works, x + 0.5, z + 0.5, yHint);
                BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
                for (int y = (int) Math.floor(yHint) + 1; a != null && y >= (int) Math.floor(yHint) - 2; y--) {
                    BlockState s = chunk.getBlockState(p.set(x, y, z));
                    if (!s.is(AflBlocks.ROAD_SURFACE.get())) continue;
                    c = new RoadDamageChunk.Col(0, (float) (y + s.getValue(RoadSurfaceBlock.LAYERS) / 32.0), (float) a[1]);
                    break;
                }
            }
        }
        seen.put(k, c);
        return c;
    }

    private static boolean intact(RoadDamageChunk.Col c, int i, int j) {
        return c != null && !RoadDamageChunk.gone(c.gone(), Math.floorMod(i, N), Math.floorMod(j, N));
    }

    /**
     * One strike at the hit point at: the piece it takes (the struck sub-cell, else the nearest asphalt within SEEK), about
     * STRIKE_LITRES at the struck column's thickness, written back; null when there is no asphalt to take.
     */
    public static Taken strike(ServerLevel level, Vec3 at, RandomSource random) {
        RoadWorks works = RoadStructureBlock.boundWorks();
        if (works == null || level.dimension() != Level.OVERWORLD) return null;
        Long2ObjectOpenHashMap<RoadDamageChunk.Col> seen = new Long2ObjectOpenHashMap<>();
        int i0 = (int) Math.floor(at.x * N), j0 = (int) Math.floor(at.z * N);
        // the struck sub-cell, else the nearest one still there
        int si = 0, sj = 0;
        RoadDamageChunk.Col seed = null;
        search:
        for (int r = 0; r <= SEEK; r++) for (int dj = -r; dj <= r; dj++) for (int di = -r; di <= r; di++) {
            if (Math.max(Math.abs(di), Math.abs(dj)) != r) continue;
            int i = i0 + di, j = j0 + dj;
            RoadDamageChunk.Col c = column(level, works, seen, Math.floorDiv(i, N), Math.floorDiv(j, N), at.y);
            if (intact(c, i, j)) { si = i; sj = j; seed = c; break search; }
        }
        if (seed == null) return null;
        double per = seed.depth() * SUB_AREA * 1000;
        double want = STRIKE_LITRES / per;
        int count = Math.max(1, (int) want + (random.nextDouble() < want - (int) want ? 1 : 0));
        RoadDamageChunk.Col from = seed;
        LongOpenHashSet taken = piece(si, sj, count, random.nextLong(), (i, j) -> {
            RoadDamageChunk.Col c = column(level, works, seen, Math.floorDiv(i, N), Math.floorDiv(j, N), at.y);
            return intact(c, i, j) && Math.abs(c.top() - from.top()) <= SAME_ROAD;
        });
        // write back, by column
        double litres = 0;
        boolean emptied = false;
        Long2ObjectOpenHashMap<long[]> bits = new Long2ObjectOpenHashMap<>();
        for (long s : taken) {
            int i = (int) (s >> 32), j = (int) s;
            long ck = ((long) Math.floorDiv(i, N) << 32) ^ (Math.floorDiv(j, N) & 0xffffffffL);
            bits.computeIfAbsent(ck, k -> new long[1])[0] |= 1L << RoadDamageChunk.bit(Math.floorMod(i, N), Math.floorMod(j, N));
        }
        for (var en : bits.long2ObjectEntrySet()) {
            int x = (int) (en.getLongKey() >> 32), z = (int) en.getLongKey();
            RoadDamageChunk.Col c = seen.get(en.getLongKey());
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (c == null || chunk == null) continue;
            long gone = c.gone() | en.getValue()[0];
            litres += Long.bitCount(en.getValue()[0] & ~c.gone()) * c.depth() * SUB_AREA * 1000;
            emptied |= gone == RoadDamageChunk.ALL;
            RoadDamageChunk d = ((RoadDamageHolder) chunk).afl$roadDamage();
            if (d == null) ((RoadDamageHolder) chunk).afl$setRoadDamage(d = new RoadDamageChunk());
            d.put(x, z, new RoadDamageChunk.Col(gone, c.top(), c.depth()));
            long k = ChunkPos.asLong(x >> 4, z >> 4);
            SERVER.add(k);
            DIRTY.add(k);
            if (gone == RoadDamageChunk.ALL) dig(level, works, x, z, c);
        }
        return litres <= 0 ? null : new Taken(litres, emptied);
    }

    /**
     * P2 (design 8): a column whose last asphalt went is the earthwork's from now on (Earthwork#road): the aggregate base's
     * top, the drawn pavement less the asphalt's thickness at each of its 25 vertices (where the hole's floor was drawn,
     * RoadDamageMesh), AFL angular gravel. Its road_surface blocks go with it, and with them its entry (#removed).
     */
    private static void dig(ServerLevel level, RoadWorks works, int x, int z, RoadDamageChunk.Col c) {
        int n = com.antaurora.apofirstlight.earthwork.EarthGrid.SIDE, per = com.antaurora.apofirstlight.earthwork.EarthGrid.PER_BLOCK;
        double[] base = new double[n * n];
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            double[] a = RoadSurfaceQuery.asphalt(works, x + (double) i / per, z + (double) j / per, c.top());
            base[j * n + i] = (a == null ? c.top() : a[0]) - c.depth();
        }
        BlockState gravel = AflBlocks.GRAVEL.get().defaultBlockState()
                .setValue(com.antaurora.apofirstlight.ground.GravelGroundBlock.KIND, com.antaurora.apofirstlight.ground.GravelGroundBlock.Kind.ANGULAR);
        com.antaurora.apofirstlight.earthwork.Earthwork.road(level, x, z, base, gravel);
    }

    /** Whether asphalt sub-cell (i, j) (global sub-cell indices) is there to take. */
    public interface Asphalt {
        boolean at(int i, int j);
    }

    /**
     * A strike's piece (design 4): up to count sub-cells grown from (si, sj) within REACH, the cheapest frontier sub-cell
     * next (its distance, roughened by a hash of salt, less OPEN_PULL for each neighbour with no asphalt: a hole widens
     * from its rim, the pavement breaks in from its edge). Pure; RoadDamageCheck tests it.
     */
    public static LongOpenHashSet piece(int si, int sj, int count, long salt, Asphalt asphalt) {
        LongOpenHashSet taken = new LongOpenHashSet();
        java.util.PriorityQueue<double[]> frontier = new java.util.PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        frontier.add(new double[]{0, si, sj});
        LongOpenHashSet queued = new LongOpenHashSet();
        queued.add(sub(si, sj));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!frontier.isEmpty() && taken.size() < count) {
            double[] f = frontier.poll();
            int i = (int) f[1], j = (int) f[2];
            taken.add(sub(i, j));
            for (int[] d : dirs) {
                int ni = i + d[0], nj = j + d[1];
                if (Math.abs(ni - si) > REACH || Math.abs(nj - sj) > REACH || !queued.add(sub(ni, nj)) || !asphalt.at(ni, nj)) continue;
                int open = 0;
                for (int[] e : dirs) {
                    int oi = ni + e[0], oj = nj + e[1];
                    if (!taken.contains(sub(oi, oj)) && !asphalt.at(oi, oj)) open++;
                }
                double dist = Math.hypot(ni - si, nj - sj);
                frontier.add(new double[]{dist * (1 + ROUGH * noise(ni, nj, salt)) - OPEN_PULL * open, ni, nj});
            }
        }
        return taken;
    }

    public static long sub(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    /** A hash in 0..1 of a sub-cell and the strike's salt. */
    private static double noise(int i, int j, long salt) {
        long h = salt ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /** The asphalt still in a column's entry as a fraction (1: whole). */
    public static double left(RoadDamageChunk.Col c) { return c == null ? 1 : 1 - Long.bitCount(c.gone()) / (double) (N * N); }

    // ------------------------------------------------------------------------------------------------- events

    /** Hook (RoadSurfaceBlock#onRemove, server): a road_surface block went; its column's entry goes with it. */
    public static void removed(Level level, BlockPos pos) {
        if (level.isClientSide() || SERVER.isEmpty() || !SERVER.contains(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) return;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        RoadDamageChunk d = chunk == null ? null : ((RoadDamageHolder) chunk).afl$roadDamage();
        if (d != null && d.remove(pos.getX(), pos.getZ())) DIRTY.add(chunk.getPos().toLong());
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DIRTY.isEmpty()) return;
        ServerLevel level = event.getServer().overworld();
        for (long k : DIRTY) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(k), ChunkPos.getZ(k));
            if (chunk == null) continue;
            RoadDamageChunk d = ((RoadDamageHolder) chunk).afl$roadDamage();
            if (d != null && d.isEmpty()) {
                ((RoadDamageHolder) chunk).afl$setRoadDamage(d = null);
                SERVER.remove(k);
            }
            chunk.setUnsaved(true);
            AflNetwork.sendTrackingChunk(chunk, new RoadDamagePacket(ChunkPos.getX(k), ChunkPos.getZ(k), d));
        }
        DIRTY.clear();
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Watch event) {
        RoadDamageChunk d = ((RoadDamageHolder) event.getChunk()).afl$roadDamage();
        if (d != null && !d.isEmpty()) AflNetwork.roadDamageTo(event.getPlayer(), new RoadDamagePacket(event.getPos().x, event.getPos().z, d));
    }

    @SubscribeEvent
    public static void load(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !event.getData().contains(TAG, Tag.TAG_COMPOUND)) return;
        RoadDamageChunk d = RoadDamageChunk.load(event.getData().getCompound(TAG));
        if (d.isEmpty()) return;
        ((RoadDamageHolder) chunk).afl$setRoadDamage(d);
        if (chunk.getLevel() != null && !chunk.getLevel().isClientSide() && chunk.getLevel().dimension() == Level.OVERWORLD) SERVER.add(chunk.getPos().toLong());
    }

    @SubscribeEvent
    public static void save(ChunkDataEvent.Save event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        RoadDamageChunk d = ((RoadDamageHolder) chunk).afl$roadDamage();
        if (d != null && !d.isEmpty()) event.getData().put(TAG, d.save());
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() == null) return;
        long k = event.getChunk().getPos().toLong();
        if (event.getLevel().isClientSide()) CLIENT.remove(k);
        else if (event.getLevel() instanceof Level l && l.dimension() == Level.OVERWORLD) SERVER.remove(k);
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) CLIENT.clear();
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        DIRTY.clear();
        SERVER.clear();
    }

    /** A chunk's copy from the server (client main thread). */
    static void received(int cx, int cz, RoadDamageChunk data) {
        long k = ChunkPos.asLong(cx, cz);
        if (data == null) CLIENT.remove(k);
        else CLIENT.put(k, data);
        var hook = clientChanged;
        if (hook != null) hook.accept(cx, cz);
    }
}
