package com.antaurora.apofirstlight.earthwork;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

import java.util.Arrays;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import static com.antaurora.apofirstlight.earthwork.CarveChunk.N;

/**
 * Carved rock (U3a-3 round 2, docs/worldgen/underground_u3_rocks_ores_design.md 13; user 2026-10-24: every strike must
 * visibly take a piece off the rock, no cube blocks, no vanilla cracks). A natural rock block a pick has cut into keeps
 * which of its 8 x 8 x 8 sub-cells (1/8 m, {@link #SUB_LITRES} each) are gone (CarveChunk); the block stays in the world
 * (light, occlusion and every block rule as before) and is drawn as a smooth surface over the sub-cells left
 * (client: terrainsmooth.client.RockCarveMesh), collides and is aimed at as their boxes ({@link #shape}). A block with all
 * gone turns to air.
 * <ul>
 *   <li>A strike ({@link #cut}) takes a piece the size of the rock's strike_litres where it lands: the solid sub-cells
 *   nearest a point just inside the hit, grown from the struck sub-cell so it always comes off the face, flattened along
 *   the blow (wider than deep, as a chip comes off) and roughened by a hash, so no two are alike.</li>
 *   <li>Server: data on the LevelChunk (CarveChunkHolder), saved with it under {@link #TAG}; changed chunks go to their
 *   players at the end of the tick, every chunk to a player that starts tracking it. Client: one copy per chunk in
 *   {@link #CLIENT}.</li>
 *   <li>Anything that replaces a carved block (a command, an explosion) drops its carving.</li>
 * </ul>
 * The overworld only (the geology's rocks).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class RockCarve {
    public static final String TAG = ApocalypseFirstLight.MOD_ID + ":carve";
    /** A sub-cell's volume, L. */
    public static final double SUB_LITRES = 1000.0 / CarveChunk.CELLS;
    /** the client's copies by chunk (ChunkPos#asLong), replaced whole */
    public static final Map<Long, CarveChunk> CLIENT = new ConcurrentHashMap<>();
    /** loaded overworld chunks on the server that hold carved rock (the shape hooks' fast path; any thread) */
    private static final Set<Long> SERVER = ConcurrentHashMap.newKeySet();
    private static final LongOpenHashSet DIRTY = new LongOpenHashSet();
    /** How far a piece may reach from its centre, sub-cells; how much flatter than wide (along the blow). */
    static final double REACH = 6, FLAT = 1.7, ROUGH = 0.45;

    /** The client half's reaction to a new copy (re-mesh), set by RockCarveMesh. */
    public interface ClientHooks {
        void changed(int cx, int cz, CarveChunk before, CarveChunk after);
    }

    public static volatile ClientHooks clientHooks;

    private RockCarve() {}

    // ------------------------------------------------------------------------------------------------- reading

    /** A chunk's carved rock on the level's side, null when none (client: any thread; level null: the client's copies). */
    public static CarveChunk get(Level level, int cx, int cz) {
        if (level == null || level.isClientSide()) return CLIENT.isEmpty() ? null : CLIENT.get(ChunkPos.asLong(cx, cz));
        if (SERVER.isEmpty() || !SERVER.contains(ChunkPos.asLong(cx, cz)) || level.dimension() != Level.OVERWORLD) return null;
        LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
        return c == null ? null : ((CarveChunkHolder) c).afl$carve();
    }

    /** The gone sub-cells of the block at (x, y, z) on the level's side, null when it is whole. */
    public static long[] bits(Level level, int x, int y, int z) {
        CarveChunk c = get(level, x >> 4, z >> 4);
        return c == null ? null : c.get(x, y, z);
    }

    public static long[] bits(Level level, BlockPos pos) { return bits(level, pos.getX(), pos.getY(), pos.getZ()); }

    /** No carved rock anywhere on either side (the hooks' fast path). */
    public static boolean none() { return SERVER.isEmpty() && CLIENT.isEmpty(); }

    /**
     * The gone sub-cells of the block at pos as it is drawn, collides and is cut: its carving, else the relief of a natural
     * rock face (RockRelief, U6; shared, read only), null for a whole block. level: the side's (null: the client's copies);
     * getter: reads the neighbours.
     */
    public static long[] occupancy(Level level, BlockGetter getter, BlockPos pos, Block block) {
        long[] b = bits(level, pos);
        return b != null ? b : RockRelief.of(level, getter, pos, block);
    }

    /** The level of a shape query (a level, or the chunk the block is in), null for render, pathfinding and worldgen regions. */
    private static Level levelOf(BlockGetter getter) {
        if (getter instanceof LevelChunk c) return c.getLevel();
        return getter instanceof Level l ? l : null;
    }

    /** Hook (TerrainSmooth#shape): a carved or worn block's collision and selection, its sub-cells' boxes; null for anything else. */
    public static VoxelShape shape(Block block, BlockGetter getter, BlockPos pos) {
        if (!Earthwork.rock(block) && !RockRelief.soil(block)) return null;
        Level level = levelOf(getter);
        if (level == null || level.dimension() != Level.OVERWORLD) return null;
        long[] b = occupancy(level, level, pos, block);
        return b == null ? null : shapeOf(b);
    }

    /** Hook (TerrainSmooth#notSuffocating): a carved or worn block is no full block (a player standing in its hollow is not pushed out). */
    public static boolean carved(Block block, BlockGetter getter, BlockPos pos) {
        if (!Earthwork.rock(block) && !RockRelief.soil(block)) return false;
        Level level = levelOf(getter);
        return level != null && level.dimension() == Level.OVERWORLD && occupancy(level, level, pos, block) != null;
    }

    private record Key(long[] bits) {
        @Override
        public boolean equals(Object o) { return o instanceof Key k && Arrays.equals(bits, k.bits); }

        @Override
        public int hashCode() { return Arrays.hashCode(bits); }
    }

    private static final Map<Key, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    /**
     * The boxes of the sub-cells left (greedy: runs along x, grown along z, then y), joined unoptimised and optimised once
     * (a join per box re-optimises each time: quadratic, memory project-dynamic-shape-first-load-cost), cached by the bits.
     */
    public static VoxelShape shapeOf(long[] gone) {
        Key key = new Key(gone);
        VoxelShape s = SHAPES.get(key);
        if (s != null) return s;
        if (SHAPES.size() > 32768) SHAPES.clear();
        boolean[] solid = new boolean[CarveChunk.CELLS];
        for (int y = 0; y < N; y++) for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) solid[x + N * (z + N * y)] = !CarveChunk.gone(gone, x, y, z);
        VoxelShape out = Shapes.empty();
        double w = 1.0 / N;
        for (int y = 0; y < N; y++) for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) {
            if (!solid[x + N * (z + N * y)]) continue;
            int x1 = x + 1;
            while (x1 < N && solid[x1 + N * (z + N * y)]) x1++;
            int z1 = z + 1;
            while (z1 < N && row(solid, x, x1, z1, y)) z1++;
            int y1 = y + 1;
            grow:
            while (y1 < N) {
                for (int zz = z; zz < z1; zz++) if (!row(solid, x, x1, zz, y1)) break grow;
                y1++;
            }
            for (int yy = y; yy < y1; yy++) for (int zz = z; zz < z1; zz++) for (int xx = x; xx < x1; xx++) solid[xx + N * (zz + N * yy)] = false;
            out = Shapes.joinUnoptimized(out, Shapes.box(x * w, y * w, z * w, x1 * w, y1 * w, z1 * w), BooleanOp.OR);
        }
        s = out.optimize();
        SHAPES.put(new Key(gone.clone()), s);
        return s;
    }

    private static boolean row(boolean[] solid, int x0, int x1, int z, int y) {
        for (int x = x0; x < x1; x++) if (!solid[x + N * (z + N * y)]) return false;
        return true;
    }

    // ------------------------------------------------------------------------------------------------- cutting

    /** One strike's piece: the sub-cells taken by the rock block they came from, how many blocks it emptied (now air). */
    public record Cut(Object2IntOpenHashMap<BlockState> taken, int removed, int emptied) {
        public double litres(BlockState rock) { return taken.getInt(rock) * SUB_LITRES; }
    }

    private record Node(double score, int x, int y, int z) {}

    /** Sub-cells (world sub-cell coordinates, block * 8 + 0..7) a piece is taken from (a strike's working copy; a test's grid). */
    public interface Grid {
        /** Whether the sub-cell is rock that may be taken and not gone yet. */
        boolean solid(int x, int y, int z);

        /** Whether the sub-cell is open space (air, anything not a full block, or carved away). */
        boolean open(int x, int y, int z);

        void take(int x, int y, int z);
    }

    /**
     * Take a piece of about litres off the rock where a blow along dir lands at at (server thread). Only sub-cells of
     * natural rock blocks that cuttable allows are taken; seed: the piece's roughness ({@link #piece}).
     */
    public static Cut cut(ServerLevel level, Vec3 at, Vec3 dir, double litres, long seed, BiPredicate<Level, BlockPos> cuttable) {
        Work w = new Work(level, cuttable);
        if (level.dimension() != Level.OVERWORLD) return new Cut(w.taken, 0, 0);
        int removed = piece(w, at.x, at.y, at.z, dir.x, dir.y, dir.z, Math.max(1, (int) Math.round(litres / SUB_LITRES)), seed);
        return new Cut(w.taken, removed, w.apply());
    }

    /**
     * The piece itself: up to want sub-cells round a centre a little inside the hit point (ax, ay, az, m), grown from the
     * sub-cell just inside it if it lies on the face (next to open space), else the face's sub-cell nearest the centre,
     * so it always comes off the face struck and never leaves a hidden pocket; nearest first by the
     * distance across the blow plus FLAT times the distance along it (a chip: wider than deep), each roughened by the hash
     * (no two alike). The number taken.
     */
    public static int piece(Grid w, double ax, double ay, double az, double dx, double dy, double dz, int want, long seed) {
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-9) {
            dx = 0;
            dy = -1;
            dz = 0;
        } else {
            dx /= dl;
            dy /= dl;
            dz /= dl;
        }
        double cx = (ax + dx * 0.06) * N, cy = (ay + dy * 0.06) * N, cz = (az + dz * 0.06) * N;
        int sx = (int) Math.floor((ax + dx * 0.02) * N), sy = (int) Math.floor((ay + dy * 0.02) * N), sz = (int) Math.floor((az + dz * 0.02) * N);
        if (!face(w, sx, sy, sz)) {   // the struck sub-cell is gone, not cuttable or inside: the face's one nearest the centre
            double best = Double.MAX_VALUE;
            int bx = 0, by = 0, bz = 0;
            for (int z = sz - 2; z <= sz + 2; z++) for (int y = sy - 2; y <= sy + 2; y++) for (int x = sx - 2; x <= sx + 2; x++) {
                if (!face(w, x, y, z)) continue;
                double e = sq(x + 0.5 - cx) + sq(y + 0.5 - cy) + sq(z + 0.5 - cz);
                if (e < best) {
                    best = e;
                    bx = x;
                    by = y;
                    bz = z;
                }
            }
            if (best == Double.MAX_VALUE) return 0;
            sx = bx;
            sy = by;
            sz = bz;
        }
        PriorityQueue<Node> queue = new PriorityQueue<>((a, b) -> Double.compare(a.score, b.score));
        LongOpenHashSet seen = new LongOpenHashSet();
        queue.add(new Node(0, sx, sy, sz));
        seen.add(pack(0, 0, 0));
        int removed = 0;
        while (!queue.isEmpty() && removed < want) {
            Node n = queue.poll();
            if (!w.solid(n.x, n.y, n.z)) continue;
            w.take(n.x, n.y, n.z);
            removed++;
            for (int k = 0; k < 6; k++) {
                int x = n.x + (k == 0 ? 1 : k == 1 ? -1 : 0), y = n.y + (k == 2 ? 1 : k == 3 ? -1 : 0), z = n.z + (k == 4 ? 1 : k == 5 ? -1 : 0);
                if (!seen.add(pack(x - sx, y - sy, z - sz))) continue;
                double vx = x + 0.5 - cx, vy = y + 0.5 - cy, vz = z + 0.5 - cz;
                double len2 = vx * vx + vy * vy + vz * vz;
                if (len2 > REACH * REACH || !w.solid(x, y, z)) continue;
                double along = vx * dx + vy * dy + vz * dz;
                double score = (len2 - along * along) + sq(FLAT * along);
                queue.add(new Node(score * (1 + ROUGH * hash(x, y, z, seed)), x, y, z));
            }
        }
        return removed;
    }

    /** A cuttable sub-cell next to open space. */
    private static boolean face(Grid w, int x, int y, int z) {
        return w.solid(x, y, z) && (w.open(x + 1, y, z) || w.open(x - 1, y, z) || w.open(x, y + 1, z) || w.open(x, y - 1, z)
                || w.open(x, y, z + 1) || w.open(x, y, z - 1));
    }

    private static double sq(double v) { return v * v; }

    private static long pack(int x, int y, int z) {
        return (long) (x + (1 << 20)) << 42 | (long) (y + (1 << 20)) << 21 | (z + (1 << 20));
    }

    /** -1..1 from a sub-cell and the seed. */
    static double hash(int x, int y, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    /** A strike's working copy of the blocks it touches: the bits it writes, whether each block may be cut. */
    private static final class Work implements Grid {
        final ServerLevel level;
        final Object2IntOpenHashMap<BlockState> taken = new Object2IntOpenHashMap<>();
        final BiPredicate<Level, BlockPos> cuttable;
        final Long2ByteOpenHashMap ok = new Long2ByteOpenHashMap();
        final Long2ObjectOpenHashMap<long[]> bits = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        /** the blocks a sub-cell was taken from (only these are written back: a worn face merely looked at stays unstored) */
        final LongOpenHashSet changed = new LongOpenHashSet();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Work(ServerLevel level, BiPredicate<Level, BlockPos> cuttable) {
            this.level = level;
            this.cuttable = cuttable;
        }

        /** 1: cuttable rock (its bits in bits), 2: open (not a full block, or not loaded), 3: anything else solid (carved or worn bits kept in bits). */
        private byte kind(int x, int y, int z) {
            long k = BlockPos.asLong(x >> 3, y >> 3, z >> 3);
            if (!ok.containsKey(k)) {
                pos.set(x >> 3, y >> 3, z >> 3);
                byte kind;
                if (!level.isLoaded(pos)) kind = 2;
                else {
                    BlockState s = level.getBlockState(pos);
                    long[] b = occupancy(level, level, pos, s.getBlock());   // a worn face is cut on from its relief (U6)
                    if (Earthwork.rock(s.getBlock()) && cuttable.test(level, pos)) {
                        kind = 1;
                        bits.put(k, b == null ? new long[N] : b.clone());
                        states.put(k, s);
                    } else if (b != null) {
                        kind = 3;
                        bits.put(k, b);
                    } else kind = s.isSolidRender(level, pos) ? (byte) 3 : 2;
                }
                ok.put(k, kind);
            }
            return ok.get(k);
        }

        private long block(int x, int y, int z) {
            return kind(x, y, z) == 1 ? BlockPos.asLong(x >> 3, y >> 3, z >> 3) : Long.MIN_VALUE;
        }

        @Override
        public boolean open(int x, int y, int z) {
            byte kind = kind(x, y, z);
            if (kind == 2) return true;
            long[] b = bits.get(BlockPos.asLong(x >> 3, y >> 3, z >> 3));
            return b != null && CarveChunk.gone(b, x & 7, y & 7, z & 7);
        }

        @Override
        public boolean solid(int x, int y, int z) {
            long k = block(x, y, z);
            return k != Long.MIN_VALUE && !CarveChunk.gone(bits.get(k), x & 7, y & 7, z & 7);
        }

        @Override
        public void take(int x, int y, int z) {
            long k = block(x, y, z);
            bits.get(k)[y & 7] |= 1L << ((x & 7) | (z & 7) << 3);
            taken.addTo(states.get(k), 1);
            changed.add(k);
        }

        /** Write the blocks cut back; a block with nothing left turns to air. The number emptied. */
        int apply() {
            int emptied = 0;
            for (var en : bits.long2ObjectEntrySet()) {
                if (ok.get(en.getLongKey()) != 1 || !changed.contains(en.getLongKey())) continue;
                long[] b = en.getValue();
                int n = CarveChunk.count(b);
                if (n == 0) continue;
                BlockPos p = BlockPos.of(en.getLongKey());
                LevelChunk chunk = level.getChunkAt(p);
                CarveChunk c = ((CarveChunkHolder) chunk).afl$carve();
                if (c == null) ((CarveChunkHolder) chunk).afl$setCarve(c = new CarveChunk());
                if (n >= CarveChunk.CELLS) {
                    c.remove(p.getX(), p.getY(), p.getZ());
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    emptied++;
                } else {
                    System.arraycopy(b, 0, c.edit(p.getX(), p.getY(), p.getZ()), 0, N);
                }
                long ck = ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
                SERVER.add(ck);
                DIRTY.add(ck);
            }
            return emptied;
        }
    }

    // ------------------------------------------------------------------------------------------------- events

    /** The client half's re-mesh round a changed block (the worn faces two blocks out read it), set by RockCarveMesh. */
    public static volatile java.util.function.Consumer<BlockPos> clientChanged;

    /** Hook (TerrainSmooth#changed, both sides): the block at pos changed; a carved one that was replaced loses its carving. */
    public static void blockChanged(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            var hook = clientChanged;
            if (hook != null) hook.accept(pos);
            return;
        }
        if (SERVER.isEmpty() || !SERVER.contains(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) return;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        CarveChunk c = chunk == null ? null : ((CarveChunkHolder) chunk).afl$carve();
        if (c != null && c.remove(pos.getX(), pos.getY(), pos.getZ())) DIRTY.add(chunk.getPos().toLong());
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DIRTY.isEmpty()) return;
        ServerLevel level = event.getServer().overworld();
        for (long k : DIRTY) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(k), ChunkPos.getZ(k));
            if (chunk == null) continue;
            CarveChunk c = ((CarveChunkHolder) chunk).afl$carve();
            if (c != null && c.isEmpty()) {
                ((CarveChunkHolder) chunk).afl$setCarve(c = null);
                SERVER.remove(k);
            }
            chunk.setUnsaved(true);
            AflNetwork.sendTrackingChunk(chunk, new CarveChunkPacket(ChunkPos.getX(k), ChunkPos.getZ(k), c));
        }
        DIRTY.clear();
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Watch event) {
        CarveChunk c = ((CarveChunkHolder) event.getChunk()).afl$carve();
        if (c != null && !c.isEmpty()) AflNetwork.carveTo(event.getPlayer(), new CarveChunkPacket(event.getPos().x, event.getPos().z, c));
    }

    @SubscribeEvent
    public static void load(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !event.getData().contains(TAG, Tag.TAG_COMPOUND)) return;
        CarveChunk c = CarveChunk.load(event.getData().getCompound(TAG));
        if (c.isEmpty()) return;
        ((CarveChunkHolder) chunk).afl$setCarve(c);
        if (chunk.getLevel() != null && !chunk.getLevel().isClientSide() && chunk.getLevel().dimension() == Level.OVERWORLD) SERVER.add(chunk.getPos().toLong());
    }

    @SubscribeEvent
    public static void save(ChunkDataEvent.Save event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        CarveChunk c = ((CarveChunkHolder) chunk).afl$carve();
        if (c != null && !c.isEmpty()) event.getData().put(TAG, c.save());
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
    static void received(int cx, int cz, CarveChunk data) {
        long k = ChunkPos.asLong(cx, cz);
        CarveChunk before = data == null ? CLIENT.remove(k) : CLIENT.put(k, data);
        ClientHooks hooks = clientHooks;
        if (hooks != null) hooks.changed(cx, cz, before, data);
    }
}
