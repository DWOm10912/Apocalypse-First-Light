package com.antaurora.apofirstlight.earthwork;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.terrainsmooth.SmoothChunkData;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.antaurora.apofirstlight.earthwork.EarthGrid.*;

/**
 * Earthwork A1 (docs/worldgen/earthwork_a1.md; design docs/gameplay/sandbox_v1_design.md 3): an editable height field
 * over the natural ground, only where it has been edited. A column is <b>edited</b> (E) from its first edit on: it is drawn
 * and collides as the skin of its 25 vertices (EarthGrid), its blocks follow the skin (a cell holds ground exactly when
 * the skin rises into it), and Terrain Smooth reads it as it was before (natural ground up to top0), so the slopes around
 * it never change and its border vertices, which it shares with them, stay where those slopes put them.
 * <ul>
 *   <li>Which columns: natural surface of loose ground (grass, dirt, sand, gravel, mud, clay...) with loose ground under
 *   it and a solid block under that, open above, whose four Terrain Smooth corners are free (SmoothRules#freeCorners: the
 *   slopes there are continuous, so the field can take them over exactly). A vertex moves only when every column that
 *   reads it is edited or can be.</li>
 *   <li>First edit of a column: its vertices not yet set take its Terrain Smooth top (a snapshot), plants and snow on
 *   it go, its first top block is kept for the undisturbed skin's texture.</li>
 *   <li>Volumes are whole quanta (EarthGrid): earth dug is exactly earth carried; the A1 development command keeps the
 *   carried earth per player.</li>
 *   <li>Server: data on the LevelChunk, saved with it under {@link #TAG}; changed chunks go to their players at the end
 *   of the tick (after vanilla's block changes), every chunk to a player that starts tracking it. Client: one copy per
 *   chunk in {@link #CLIENT}.</li>
 *   <li>Anything else that changes a ground cell of an edited column (commands, other mods) is followed at the end of
 *   the tick: the skin goes down below a cell that lost its ground. Players cannot break edited ground by hand (A2:
 *   with a shovel).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class Earthwork {
    public static final String TAG = ApocalypseFirstLight.MOD_ID + ":earth";
    /** the client's copies by chunk (ChunkPos#asLong), replaced whole */
    public static final Map<Long, EarthChunk> CLIENT = new ConcurrentHashMap<>();
    /**
     * A column's hard floor lies at most DEPTH cells below its first top (docs/worldgen/earthwork_a2.md 2.2); it fills up to
     * top0 + 2 (heights up to (top0 + 3) * Q); poured earth stays CLEARANCE quanta (0.05 m) below a block above it.
     */
    public static final int DEPTH = 3, RAISE = 3, CLEARANCE = 13;
    /**
     * Rock R2 (docs/gameplay/rock_r2_rock_face_mining_design.md 3.3): a pick cuts rock at most ROCK_DEPTH cells below a
     * column's first top (a test value), keeping a lid of one cell over a cave.
     */
    public static final int ROCK_DEPTH = 6;
    /** development (A1): the earth each player carries, in quanta */
    public static final Map<UUID, Long> CARRIED = new HashMap<>();

    /** The client half's reaction to a new copy (re-mesh, drop cached slopes), set by TerrainSmoothClient. */
    public interface ClientHooks {
        void changed(int cx, int cz, EarthChunk before, EarthChunk after);
    }

    public static volatile ClientHooks clientHooks;

    private static boolean converting;
    private static final LongOpenHashSet DIRTY = new LongOpenHashSet();
    private static final List<BlockPos> RECONCILE = new ArrayList<>();
    public static long mismatches;

    private Earthwork() {}

    // ------------------------------------------------------------------------------------------------- reading

    /** A chunk's data on the level's side, null when none or not loaded (client: any thread; level null: the client's copies). */
    public static EarthChunk get(Level level, int cx, int cz) {
        if (level == null || level.isClientSide()) return CLIENT.isEmpty() ? null : CLIENT.get(ChunkPos.asLong(cx, cz));
        LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
        return c == null ? null : ((EarthChunkHolder) c).afl$earth();
    }

    /** The same for a chunk already in hand. */
    public static EarthChunk get(Level level, LevelChunk chunk) {
        if (level.isClientSide()) return CLIENT.isEmpty() ? null : CLIENT.get(chunk.getPos().toLong());
        return ((EarthChunkHolder) chunk).afl$earth();
    }

    /** No edited ground anywhere on this side (the fast path of every hook). */
    public static boolean none(Level level) {
        return (level == null || level.isClientSide()) && CLIENT.isEmpty();
    }

    private static final ThreadLocal<int[]> SKIN_HEIGHTS = ThreadLocal.withInitial(() -> new int[SIDE * SIDE]);

    /**
     * Whether (x, y, z) holds a skin cell of an edited column in the client's copies (A1.1): its block is a full cube in the
     * data, but the ground inside it is the skin, often barely above the cell's floor. Such a cell does not darken its
     * neighbours' smooth lighting as a full block would (TerrainSmoothBlockStateMixin), or a fill's edge rings the grass
     * around it with a shadow. Chunk-builder threads; cheap unless the column is edited.
     */
    public static boolean clientSkinCell(Block b, int x, int y, int z) {
        return !CLIENT.isEmpty() && ground(b) && clientSkinCell(x, y, z);
    }

    /** The same without the block in hand (a skin cell's block is ground by the block invariant): section occlusion (A3). */
    public static boolean clientSkinCell(int x, int y, int z) {
        if (CLIENT.isEmpty() || editedTop(get(null, x >> 4, z >> 4), x, z) == Integer.MIN_VALUE) return false;
        int[] h = SKIN_HEIGHTS.get();
        return heights(null, x, z, h, null) && kind(min(h), max(h), y) == SURFACE;
    }

    /**
     * The cell whose light stands for pos (A3): pos itself, or, when it is a skin cell in the client's copies (a full block
     * in the data, so it stores no light, though open above its skin), the first cell above that is not one. For what is
     * drawn standing in a pit: entities, the hand, particles (RockCarveMesh#lightAt, EarthworkParticleLightMixin).
     */
    public static BlockPos clientLightCell(BlockGetter level, BlockPos pos) {
        if (CLIENT.isEmpty() || !clientSkinCell(level.getBlockState(pos).getBlock(), pos.getX(), pos.getY(), pos.getZ())) return pos;
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < 8; i++) {
            p.move(0, 1, 0);
            if (!clientSkinCell(level.getBlockState(p).getBlock(), p.getX(), p.getY(), p.getZ())) break;
        }
        return p.immutable();
    }

    /** top0 of edited column (x, z) of chunk data e, or Integer.MIN_VALUE when it is not edited. */
    public static int editedTop(EarthChunk e, int x, int z) {
        if (e == null) return Integer.MIN_VALUE;
        int col = column(x, z);
        return e.edited(col) ? e.top0(col) : Integer.MIN_VALUE;
    }

    public static int editedTop(Level level, int x, int z) {
        return none(level) ? Integer.MIN_VALUE : editedTop(get(level, x >> 4, z >> 4), x, z);
    }

    /**
     * The 25 heights of edited column (x, z) into h, and which are still at their first height into undisturbed (may be
     * null); false when the column is not edited or a vertex is missing (a neighbour chunk not loaded, or its copy not
     * here yet). Level null: the client's copies.
     */
    public static boolean heights(Level level, int x, int z, int[] h, boolean[] undisturbed) {
        if (none(level)) return false;
        EarthChunk own = get(level, x >> 4, z >> 4);
        if (own == null || !own.edited(column(x, z))) return false;
        EarthChunk e = own;
        int ecx = x >> 4, ecz = z >> 4;
        for (int j = 0; j <= PER_BLOCK; j++) for (int i = 0; i <= PER_BLOCK; i++) {
            int vx = x * PER_BLOCK + i, vz = z * PER_BLOCK + j, cx = vx >> 6, cz = vz >> 6;
            if (cx != ecx || cz != ecz) {
                e = cx == x >> 4 && cz == z >> 4 ? own : get(level, cx, cz);
                ecx = cx;
                ecz = cz;
            }
            if (e == null) return false;
            int k = vertex(vx & 63, vz & 63), v = e.h(k);
            if (v == NONE) return false;
            h[at(i, j)] = v;
            // Rock R2: loose earth on undisturbed ground (old rubble on rock, say) keeps the height, yet that ground no longer shows
            if (undisturbed != null) undisturbed[at(i, j)] = EarthGrid.undisturbed(v, e.base(k)) && e.loose(k) < TURF / 4;
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------- materials

    /** Loose ground a shovel moves (all of them Terrain Smooth ground: their models draw the skin). */
    public static boolean soil(Block b) {
        return b == Blocks.GRASS_BLOCK || b == Blocks.DIRT || b == Blocks.COARSE_DIRT || b == Blocks.PODZOL || b == Blocks.SAND
                || b == Blocks.RED_SAND || b == Blocks.GRAVEL || b == Blocks.MUD || b == Blocks.CLAY
                || b instanceof com.antaurora.apofirstlight.ground.NaturalGroundBlock g && g.loose();   // natural ground N1 (not snow)
    }

    /**
     * Natural rock a pick cuts where it lies bare (Rock R2, docs/gameplay/rock_r2_rock_face_mining_design.md 3.2): stone
     * and the geology's rocks (U3a, NaturalRockBlock; tag apocalypse_firstlight:natural_rock), the rocks Terrain Smooth
     * draws and the earthwork skin can follow; the vanilla granite, andesite, diorite and tuff blobs are not rock here.
     */
    public static boolean rock(Block b) {
        return b == Blocks.STONE || b instanceof com.antaurora.apofirstlight.block.NaturalRockBlock;
    }

    /** What an edited column's ground cells hold (the block invariant): loose ground, or rock under the skin. */
    public static boolean ground(Block b) {
        return soil(b) || rock(b);
    }

    /**
     * The ground a column's first top turns into once moved: grass and podzol lose their top, rock and the rest stay
     * themselves (the skin's bare look); AFL's topsoil keeps its soil with a bare top, its other soils stay as they are
     * (gravel keeps its kind).
     */
    public static BlockState bare(BlockState top) {
        Block b = top == null ? Blocks.DIRT : top.getBlock();
        if (b instanceof com.antaurora.apofirstlight.ground.TopsoilBlock t) return t.bare();
        if (b instanceof com.antaurora.apofirstlight.ground.NaturalGroundBlock g && g.loose()) return top;
        return b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL || !ground(b) ? Blocks.DIRT.defaultBlockState() : b.defaultBlockState();
    }

    /** The block a cell of edited column takes once its skin rises into it without loose earth over it: rock comes back as gravel. */
    private static BlockState refill(BlockState top) {
        if (top == null || !rock(top.getBlock())) return bare(top);
        // broken rock settles as AFL's angular gravel (natural ground N1b, 2026-10-25); vanilla's where the registry is not up
        // (the offline checks)
        var gravel = com.antaurora.apofirstlight.registry.AflBlocks.GRAVEL;
        return gravel.isPresent()
                ? gravel.get().defaultBlockState().setValue(com.antaurora.apofirstlight.ground.GravelGroundBlock.KIND, com.antaurora.apofirstlight.ground.GravelGroundBlock.Kind.ANGULAR)
                : Blocks.GRAVEL.defaultBlockState();
    }

    // ------------------------------------------------------------------------------------------------- shapes

    private record ShapeKey(int[] boxes, int grid) {
        @Override
        public boolean equals(Object o) { return o instanceof ShapeKey k && grid == k.grid && Arrays.equals(boxes, k.boxes); }

        @Override
        public int hashCode() { return 31 * Arrays.hashCode(boxes) + grid; }
    }

    private static final Map<ShapeKey, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    /**
     * Collision (8 x 8 columns) or selection (4 x 4) of the block at pos when it is a skin cell of an edited column (its
     * columns reach the skin at their centres, as Terrain Smooth's tops; mud 2/16 lower); null for anything else (a full
     * cell under the skin keeps its cube). The level's main thread.
     */
    public static VoxelShape shape(Level level, BlockPos pos, Block block, boolean collision) {
        if (none(level)) return null;
        int[] h = new int[SIDE * SIDE];
        if (!heights(level, pos.getX(), pos.getZ(), h, null)) return null;
        int y = pos.getY();
        if (kind(min(h), max(h), y) != SURFACE) return null;
        int grid = collision ? SmoothRules.GRID : 4;
        int[] b = boxes(h, y, grid, collision && (block == Blocks.MUD || block instanceof com.antaurora.apofirstlight.ground.MudGroundBlock) ? 2 : 0);
        ShapeKey key = new ShapeKey(b, grid);
        VoxelShape s = SHAPES.get(key);
        if (s != null) return s;
        if (SHAPES.size() > 16384) SHAPES.clear();
        VoxelShape out = Shapes.empty();
        double w = 1.0 / grid;
        for (int j = 0; j < grid; j++) for (int i = 0; i < grid; ) {
            int top = b[j * grid + i], run = i + 1;
            while (run < grid && b[j * grid + run] == top) run++;   // one box per run of equal heights along x
            if (top > 0) out = Shapes.or(out, Shapes.box(i * w, 0, j * w, run * w, top / 16.0, (j + 1) * w));
            i = run;
        }
        s = out.optimize();
        SHAPES.put(key, s);
        return s;
    }

    // ------------------------------------------------------------------------------------------------- editing

    /**
     * One edit: quanta moved (signed: dug negative), vertices moved, columns edited for the first time, vertices refused, and
     * the earth taken out by material (BulkMaterials index to quanta; negative: put down).
     */
    public record Result(long moved, int vertices, int converted, int refused, Int2LongOpenHashMap taken) {
        static Result none(int refused) { return new Result(0, 0, 0, refused, new Int2LongOpenHashMap()); }
    }

    /** How far an edit moves vertex v, now at height h (quanta). */
    interface Delta {
        int of(long v, int h);
    }

    private static final byte EDITED = 1, FREE = 2, FIXED = 3;

    /** The columns reading vertex (vx, vz): x in {vx / 4, and vx / 4 - 1 on a block edge}, the same for z. */
    private static long[] columnsOf(int vx, int vz) {
        int x = Math.floorDiv(vx, PER_BLOCK), z = Math.floorDiv(vz, PER_BLOCK);
        boolean ex = Math.floorMod(vx, PER_BLOCK) == 0, ez = Math.floorMod(vz, PER_BLOCK) == 0;
        long[] out = new long[(ex ? 2 : 1) * (ez ? 2 : 1)];
        int n = 0;
        for (int dz = ez ? -1 : 0; dz <= 0; dz++) for (int dx = ex ? -1 : 0; dx <= 0; dx++) out[n++] = key(x + dx, z + dz);
        return out;
    }

    private static boolean loaded(ServerLevel level, int x, int z) {
        for (int dz = 0; dz <= 1; dz++) for (int dx = 0; dx <= 1; dx++)
            if (level.getChunkSource().getChunkNow((x + dx) >> 4, (z + dz) >> 4) == null) return false;
        return true;
    }

    /**
     * Air, a plant, snow or loose stones: nothing a column's ground must keep (Rock R1a's boulder cells go only into such
     * cells; #settle drops the stones as items first).
     */
    public static boolean open(BlockState s) {
        return s.isAir() || s.getBlock() instanceof BushBlock || s.getBlock() instanceof SnowLayerBlock
                || com.antaurora.apofirstlight.boulder.LooseStones.is(s);
    }

    /** EDITED, FREE (can be edited: docs/worldgen/earthwork_a1.md 2.1) or FIXED. */
    private static byte status(ServerLevel level, int x, int z) {
        String why = why(level, x, z);
        return why == null ? FREE : why.isEmpty() ? EDITED : FIXED;
    }

    /**
     * Why column (x, z) cannot be edited (development output, English, docs/worldgen/earthwork_a1.md 2.1); "" when it is
     * edited already, null when it can be.
     */
    private static String why(ServerLevel level, int x, int z) {
        if (!loaded(level, x, z)) return "its chunk or the next one east / south is not loaded";
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        EarthChunk e = ((EarthChunkHolder) chunk).afl$earth();
        if (e != null && e.edited(column(x, z))) return "";
        SmoothChunkData d = TerrainSmooth.data(level, chunk);
        if (d == null) return "Terrain Smooth is off here";
        int t = d.surface(x, z);
        if (t == SmoothRules.NO_SURFACE) return "no natural surface (built over, water, an overhang)";
        var p = new BlockPos.MutableBlockPos(x, t, z);
        if (rock(level.getBlockState(p).getBlock())) {   // Rock R2: bare rock, a pick's (its floor is its top)
            // U3a-3 round 2: rock carved block by block within the open cut's reach stays carved (no skin over a carving)
            CarveChunk carved = RockCarve.get(level, x >> 4, z >> 4);
            if (carved != null && carved.anyInColumn(x, z, t - ROCK_DEPTH - 1, t)) return "its rock is carved by the pick block by block";
            for (int y = t + 1; y <= t + 2; y++)
                if (!open(level.getBlockState(p.setY(y)))) return (y - t) + " above its top is " + name(level.getBlockState(p)) + ", not open";
            return TerrainSmooth.notFree(level, x, t, z, true);
        }
        if (!soil(level.getBlockState(p).getBlock())) return "its top is " + name(level.getBlockState(p)) + ", not loose ground";
        // round 2 (2026-10-22): a thin soil straight on rock is loose ground too, its hard floor the rock's top (#hardFloor)
        Block under1 = level.getBlockState(p.setY(t - 1)).getBlock();
        if (!soil(under1) && !rock(under1)) return "under its top is " + name(level.getBlockState(p)) + ", neither loose ground nor rock";
        if (soil(under1)) {
            BlockState under = level.getBlockState(p.setY(t - 2));
            if (!under.isSolidRender(level, p)) return "two below its top is " + name(under) + ", not a solid block";
        }
        for (int y = t + 1; y <= t + 2; y++)
            if (!open(level.getBlockState(p.setY(y)))) return (y - t) + " above its top is " + name(level.getBlockState(p)) + ", not open";
        return TerrainSmooth.notFree(level, x, t, z, true);
    }

    private static String name(BlockState s) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    /** What an edit at these vertices may do: the vertices every reading column of which is edited or free. */
    public static boolean editable(ServerLevel level, int vx, int vz) {
        for (long c : columnsOf(vx, vz)) if (status(level, keyX(c), keyZ(c)) == FIXED) return false;
        return true;
    }

    /** Whether column (x, z) is edited or can be (the development info). */
    public static String describe(ServerLevel level, int x, int z) {
        String why = why(level, x, z);
        return why == null ? "editable" : why.isEmpty() ? "edited" : "fixed: " + why;
    }

    private static EarthChunk own(ServerLevel level, int cx, int cz) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        EarthChunk e = ((EarthChunkHolder) chunk).afl$earth();
        if (e == null) ((EarthChunkHolder) chunk).afl$setEarth(e = new EarthChunk());
        return e;
    }

    /** First edit of a free column: snapshot of its Terrain Smooth top into the vertices not set yet, plants off, marked. */
    private static void convert(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        int t = TerrainSmooth.data(level, chunk).surface(x, z);
        BlockState top = level.getBlockState(new BlockPos(x, t, z));
        long g = TerrainSmooth.fresh(level, chunk, x, t, z);   // before marking: the neighbours read as they are
        float[] c = g < 0 ? new float[]{1, 1, 1, 1} : SmoothRules.corners(g);
        for (int j = 0; j <= PER_BLOCK; j++) for (int i = 0; i <= PER_BLOCK; i++) {
            int vx = x * PER_BLOCK + i, vz = z * PER_BLOCK + j;
            EarthChunk o = own(level, vx >> 6, vz >> 6);
            int k = vertex(vx & 63, vz & 63);
            int b = t * Q + (int) Math.round(SmoothRules.surface(c, (double) i / PER_BLOCK, (double) j / PER_BLOCK) * Q);
            // a neighbour's snapshot put it elsewhere: the two columns of a natural step (Rock R2 round 4, SmoothRules#notFree),
            // whose pinned corners each take their own top; the column edited first keeps the vertex, EarthSkin closes the side
            if (o.h(k) == NONE) o.first(k, b);
            else if (o.base(k) != b) mismatches++;
            DIRTY.add(ChunkPos.asLong(vx >> 6, vz >> 6));
        }
        int floor = rock(top.getBlock()) ? (t + 1) * Q : hardFloor(level, x, t, z);
        own(level, x >> 4, z >> 4).mark(column(x, z), t, top, floor, rockLimit(level, x, z, Math.floorDiv(floor, Q), t));
        converting = true;
        try {
            var p = new BlockPos.MutableBlockPos();
            for (int y = t + 2; y > t; y--) if (!level.getBlockState(p.set(x, y, z)).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } finally {
            converting = false;
        }
    }

    /**
     * Road Mesh-ification P2 (docs/worldgen/road_mesh_damage_v1_design.md 8): a road column whose asphalt is all gone
     * becomes an edited column. Its road_surface blocks go (the cells the base reaches become gravel, the ones above air,
     * quietly: no reconcile), its vertices not set yet take the aggregate base's top (base: world y at each of the 25,
     * EarthGrid order), its first top is gravel. Its vertices shared with a road column still paved stay where they are:
     * that column is FIXED (#why: no natural surface), so the asphalt left locks the ground under it. False: not loaded or
     * edited already.
     */
    public static boolean road(ServerLevel level, int x, int z, double[] base, BlockState gravel) {
        if (!loaded(level, x, z)) return false;
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        EarthChunk e = ((EarthChunkHolder) chunk).afl$earth();
        if (e != null && e.edited(column(x, z))) return false;
        int hi = Integer.MIN_VALUE;
        int[] q = new int[base.length];
        for (int i = 0; i < base.length; i++) hi = Math.max(hi, q[i] = (int) Math.round(base[i] * Q));
        int t = Math.floorDiv(hi - 1, Q);
        quietly(() -> {
            var p = new BlockPos.MutableBlockPos();
            for (int y = t + 2; y >= t - 2; y--)
                if (level.getBlockState(p.set(x, y, z)).is(com.antaurora.apofirstlight.registry.AflBlocks.ROAD_SURFACE.get()))
                    level.setBlock(p, y <= t ? gravel : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        });
        for (int j = 0; j <= PER_BLOCK; j++) for (int i = 0; i <= PER_BLOCK; i++) {
            int vx = x * PER_BLOCK + i, vz = z * PER_BLOCK + j;
            EarthChunk o = own(level, vx >> 6, vz >> 6);
            int k = vertex(vx & 63, vz & 63);
            if (o.h(k) == NONE) o.first(k, q[at(i, j)]);
            DIRTY.add(ChunkPos.asLong(vx >> 6, vz >> 6));
        }
        int floor = hardFloor(level, x, t, z);
        own(level, x >> 4, z >> 4).mark(column(x, z), t, gravel, floor, rockLimit(level, x, z, Math.floorDiv(floor, Q), t));
        return true;
    }

    /**
     * The hard floor of a column whose natural top is cell t (quanta): the top of the first block under it a shovel cannot
     * move, at most DEPTH cells down; over a cave or water (not a solid block) the lowest loose cell stays as its lid, so the
     * blocks never have to fill a cave.
     */
    private static int hardFloor(ServerLevel level, int x, int t, int z) {
        var p = new BlockPos.MutableBlockPos(x, t, z);
        int f = soil(level.getBlockState(p.setY(t - 1)).getBlock()) ? t - 1 : t;   // #why: t loose ground, t - 1 loose ground or rock
        while (f > t - DEPTH && soil(level.getBlockState(p.setY(f - 1)).getBlock())) f--;
        BlockState under = level.getBlockState(p.setY(f - 1));
        if (!soil(under.getBlock()) && !under.isSolidRender(level, p)) f++;
        return f * Q;
    }

    /**
     * Rock R2: the deepest a pick may cut the rock under a column whose hard floor is the top of cell fc - 1 and whose
     * first top is cell t (quanta), or NONE when its floor is not rock: down through rock cells to ROCK_DEPTH cells below
     * t, a cell going only where a solid block stays under it (the lid over a cave or water).
     */
    private static int rockLimit(ServerLevel level, int x, int z, int fc, int t) {
        var p = new BlockPos.MutableBlockPos(x, fc, z);
        int y = fc;
        while (y > t + 1 - ROCK_DEPTH && rock(level.getBlockState(p.setY(y - 1)).getBlock()) && level.getBlockState(p.setY(y - 2)).isSolidRender(level, p)) y--;
        return y < fc ? y * Q : NONE;
    }

    /**
     * The highest a vertex of edited column (x, z) may rise (quanta): CLEARANCE below the first block above its first top that
     * is neither open nor loose ground, else top0 + RAISE.
     */
    private static int ceiling(ServerLevel level, int x, int z, int top) {
        var p = new BlockPos.MutableBlockPos();
        for (int y = top + 1; y <= top + RAISE; y++) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (!open(s) && !soil(s.getBlock())) return Math.min(y * Q - CLEARANCE, (top + RAISE) * Q);
        }
        return (top + RAISE) * Q;
    }

    /**
     * The material of the natural ground of vertex (vx, vz) at height hv: the block of the cell under it in the column it
     * starts (which reads it), else that column's first top, else topsoil.
     */
    private static int natural(ServerLevel level, int vx, int vz, int hv) {
        var table = BulkMaterials.table();
        int x = Math.floorDiv(vx, PER_BLOCK), z = Math.floorDiv(vz, PER_BLOCK);
        Block under = level.getBlockState(new BlockPos(x, Math.floorDiv(hv - 1, Q), z)).getBlock();
        if (rock(under)) return table.index("rubble");   // Rock R2: rock stands at rubble's excavation angle, the rock's own
        int m = table.of(under);
        if (m >= 0) return m;
        EarthChunk e = get(level, x >> 4, z >> 4);
        BlockState top = e == null ? null : e.topState(column(x, z));
        m = top == null ? -1 : table.of(top.getBlock());
        return m >= 0 ? m : table.index("topsoil");
    }

    /** An edit with slopes (#edit(ServerLevel, Long2IntOpenHashMap, boolean)). */
    public static Result edit(ServerLevel level, Long2IntOpenHashMap deltas) {
        return edit(level, deltas, true);
    }

    /** Moves vertices by deltas (global vertex key to quanta), no material, no limit (#edit). */
    static Result edit(ServerLevel level, Long2IntOpenHashMap deltas, boolean slopes) {
        return edit(level, deltas.keySet(), (v, h) -> deltas.get(v), slopes, -1, Long.MAX_VALUE, null, false);
    }

    static Result edit(ServerLevel level, it.unimi.dsi.fastutil.longs.LongCollection where, Delta delta, boolean slopes, int put, long limit,
                       Long2IntOpenHashMap rise) {
        return edit(level, where, delta, slopes, put, limit, rise, false);
    }

    /**
     * Moves the vertices where by delta, each kept within the columns that read it (their hard floors and ceilings), first
     * editing every free column involved; the blocks follow. Refuses vertices read by a column that cannot be edited. With
     * slopes (docs/worldgen/earthwork_a1.md 2.4, earthwork_a2.md 2) no vertex goes steeper than its ground stands against
     * the ground that cannot move (natural ground: its excavation angle; loose earth: its repose; next to locked ground 1:1),
     * and the edited ground slumps after; without (reset, reconcile) only the columns' ranges hold. Earth put down (of
     * material put, -1 none) is loose earth; limit: quanta the edit moves at most in all (a shovel's room or load), shared
     * out over what each vertex may move (EarthGrid#distribute); rise: how far vertices may rise at most (under entities).
     * Rock R2: a vertex over rock goes no lower than the rock's surface (EarthGrid#shovelFloor); cut: a pick's strike
     * instead (round 3, design 12): a vertex where the rock lies bare (every column reading it rock from its first top, or
     * no natural ground left over the rock, loose earth at most) goes down as far as the rock's depth limit, taking that
     * loose earth and then the rock out of the world; one over natural ground stays. No slumping: rock stands where it is
     * cut.
     */
    static Result edit(ServerLevel level, it.unimi.dsi.fastutil.longs.LongCollection where, Delta delta, boolean slopes, int put, long limit,
                       Long2IntOpenHashMap rise, boolean cut) {
        if (!TerrainSmooth.applies(level) || !TerrainSmooth.SERVER.enabled()) return Result.none(where.size());
        Long2ByteOpenHashMap status = new Long2ByteOpenHashMap();
        LongArrayList keep = new LongArrayList();
        int refused = 0;
        for (long v : where) {
            boolean ok = true;
            for (long c : columnsOf(keyX(v), keyZ(v))) {
                byte s = status.computeIfAbsent(c, k -> status(level, keyX(k), keyZ(k)));
                if (s == FIXED) ok = false;
            }
            if (ok) keep.add(v);
            else refused++;
        }
        int converted = 0;
        LongOpenHashSet touched = new LongOpenHashSet();
        for (long v : keep) for (long c : columnsOf(keyX(v), keyZ(v))) {
            if (status.get(c) != FREE) continue;
            convert(level, keyX(c), keyZ(c));
            status.put(c, EDITED);
            touched.add(c);   // its blocks follow the snapshot even where nothing moves (a top raised above its cell)
            converted++;
        }
        // the field: every vertex of the edited columns around the moved ones, one block further (slumping reaches that
        // far); a vertex some column of which is not edited is a wall, as is every neighbour of the field not read yet
        LongOpenHashSet near = new LongOpenHashSet();
        for (long v : keep) for (long c : columnsOf(keyX(v), keyZ(v)))
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) near.add(key(keyX(c) + dx, keyZ(c) + dz));
        var table = BulkMaterials.table();
        EarthGrid.Field f = new EarthGrid.Field();
        f.pile = table.pile;
        f.backfill = table.backfill;
        Long2IntOpenHashMap ceilings = new Long2IntOpenHashMap();
        for (long c : near) {
            if (status.computeIfAbsent(c, k -> status(level, keyX(k), keyZ(k))) != EDITED) continue;
            for (int j = 0; j <= PER_BLOCK; j++) for (int i = 0; i <= PER_BLOCK; i++) {
                int vx = keyX(c) * PER_BLOCK + i, vz = keyZ(c) * PER_BLOCK + j;
                long v = key(vx, vz);
                if (f.h.containsKey(v) || !read(level, f, v)) continue;
                int floor = Integer.MIN_VALUE, deepest = Integer.MIN_VALUE, hi = Integer.MAX_VALUE;
                boolean movable = true, bare = true;
                for (long r : columnsOf(vx, vz)) {
                    byte s = status.computeIfAbsent(r, k -> status(level, keyX(k), keyZ(k)));
                    if (s != EDITED) {
                        movable = false;
                        if (s == FIXED) f.guard.add(v);   // locked ground next to it: no cliff into it
                        continue;
                    }
                    EarthChunk e = get(level, keyX(r) >> 4, keyZ(r) >> 4);
                    int col = column(keyX(r), keyZ(r)), top = e.top0(col);
                    bare &= e.topState(col) != null && rock(e.topState(col).getBlock());
                    floor = Math.max(floor, e.floor(col));
                    deepest = Math.max(deepest, e.rock(col) == NONE ? e.floor(col) : e.rock(col));
                    hi = Math.min(hi, ceilings.computeIfAbsent(r, k -> ceiling(level, keyX(k), keyZ(k), top)));
                }
                if (!movable) continue;
                int[] range = {(cut ? pickFloor(floor, deepest, f.h.get(v), f.loose.get(v), bare) : shovelFloor(floor, deepest, f.h.get(v), f.loose.get(v))), hi};
                com.antaurora.apofirstlight.boulder.BoulderEarth.limit(level, vx, vz, f.h.get(v), range);   // Rock R1a: no cutting into rock
                int nat = natural(level, vx, vz, f.h.get(v));
                f.lo.put(v, range[0]);
                f.hi.put(v, range[1]);
                f.nat.put(v, nat);
                f.step.put(v, table.cut[nat]);
            }
        }
        for (long v : f.step.keySet().toLongArray()) for (long n : EarthGrid.Field.around(v)) {
            if (f.h.containsKey(n) || !read(level, f, n)) continue;
            for (long r : columnsOf(keyX(n), keyZ(n)))
                if (status.computeIfAbsent(r, k -> status(level, keyX(k), keyZ(k))) == FIXED) f.guard.add(n);
        }
        if (slopes) f.bound();
        else f.unbound();
        if (rise != null) for (var en : rise.long2IntEntrySet())
            if (f.movable(en.getLongKey())) f.cap(en.getLongKey(), f.h.get(en.getLongKey()) + en.getIntValue());
        Long2IntOpenHashMap wanted = new Long2IntOpenHashMap();
        for (long v : keep) if (f.movable(v) && !wanted.containsKey(v)) wanted.put(v, delta.of(v, f.h.get(v)));
        long moved = f.apply(wanted, put, limit);
        if (slopes && !cut) f.slump();
        int changed = 0;
        for (long v : f.step.keySet()) {
            int vx = keyX(v), vz = keyZ(v);
            EarthChunk o = own(level, vx >> 6, vz >> 6);
            int k = vertex(vx & 63, vz & 63), now = f.h.get(v), l = f.loose.get(v), m = l > 0 ? f.mat.get(v) : 0;
            if (now == o.h(k) && l == o.loose(k) && (l == 0 || m == o.mat(k))) continue;
            if (now != o.h(k)) changed++;
            o.set(k, now);
            o.setLoose(k, l, m);
            for (long c : columnsOf(vx, vz)) touched.add(c);
            DIRTY.add(ChunkPos.asLong(vx >> 6, vz >> 6));
        }
        for (long c : touched) settle(level, keyX(c), keyZ(c));
        return new Result(moved, changed, converted, refused, f.taken);
    }

    /** Vertex v's height, first height and loose earth into the field; false when it has none (or its chunk is not loaded). */
    private static boolean read(ServerLevel level, EarthGrid.Field f, long v) {
        int vx = keyX(v), vz = keyZ(v);
        EarthChunk e = get(level, vx >> 6, vz >> 6);
        if (e == null) return false;
        int k = vertex(vx & 63, vz & 63);
        if (e.h(k) == NONE) return false;
        f.h.put(v, e.h(k));
        f.base.put(v, e.base(k));
        if (e.loose(k) > 0) {
            f.loose.put(v, e.loose(k));
            f.mat.put(v, e.mat(k));
        }
        return true;
    }

    /**
     * The blocks of edited column (x, z), from its hard floor (Rock R2: the rock's depth limit under it) to top0 + 2, made
     * to follow its skin; a new block is its loose earth's material (the one at its middle vertex), else its first top's
     * bare ground (#refill: gravel over rock, which never grows back). A cell of a buried rock (Rock R1a)
     * the skin bares becomes a rock cell (BoulderBlocks), not air. Loose stones (Rock R1b) in the column drop as their items
     * first: the shovel moved the ground they lay on.
     */
    private static void settle(ServerLevel level, int x, int z) {
        int[] h = new int[SIDE * SIDE];
        if (!heights(level, x, z, h, null)) return;
        EarthChunk e = get(level, x >> 4, z >> 4);
        int col = column(x, z), t = e.top0(col), lo = min(h), hi = max(h), bottom = Math.floorDiv(e.bottom(col), Q);
        int mid = vertex((x * PER_BLOCK + PER_BLOCK / 2) & 63, (z * PER_BLOCK + PER_BLOCK / 2) & 63);
        BlockState fill = e.loose(mid) > 0 ? e.matBlock(e.mat(mid)) : refill(e.topState(col));
        var p = new BlockPos.MutableBlockPos();
        converting = true;
        try {
            for (int y = bottom; y <= t + RAISE; y++)
                if (com.antaurora.apofirstlight.boulder.LooseStones.is(level.getBlockState(p.set(x, y, z)))) level.destroyBlock(p, true);
            for (int y = t + RAISE - 1; y >= bottom; y--) {   // top down: nothing is left hanging
                BlockState s = level.getBlockState(p.set(x, y, z));
                if (kind(lo, hi, y) == AIR && ground(s.getBlock())) {
                    var rock = com.antaurora.apofirstlight.boulder.BoulderEarth.rockAt(level, p);
                    level.setBlock(p, rock != null ? com.antaurora.apofirstlight.boulder.BoulderBlocks.cellState(rock) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            for (int y = bottom; y < t + RAISE; y++) {   // bottom up: every new block stands on ground
                BlockState s = level.getBlockState(p.set(x, y, z));
                if (kind(lo, hi, y) != AIR && !ground(s.getBlock()) && open(s)) level.setBlock(p, fill, Block.UPDATE_ALL);
            }
        } finally {
            converting = false;
        }
        DIRTY.add(ChunkPos.asLong(x >> 4, z >> 4));
    }

    /**
     * Rock R1a (docs/gameplay/rock_r1_stones_boulders_design.md 3.3): block changes a boulder strike makes (its cells going,
     * its pieces' cells coming), run without the lost-ground follow-up (#blockChanged): the crater is #hollow's.
     */
    public static void quietly(Runnable changes) {
        boolean was = converting;
        converting = true;
        try {
            changes.run();
        } finally {
            converting = was;
        }
    }

    /**
     * Rock R1a: the hole a broken rock leaves. The rock's span over each vertex (global key; bottom, top in quanta): where
     * its top reaches the skin (the rock showed there, or the skin ran inside it) the vertex goes down to its bottom, never
     * up, editing the columns it needs; with slopes, so the ground around slumps into it at its repose. Where loose ground
     * still covers the rock the ground stays. The earth that moves is the rock's room, carried by no one.
     */
    public static Result hollow(ServerLevel level, Long2IntOpenHashMap bottom, Long2IntOpenHashMap top) {
        return edit(level, bottom.keySet(), (v, h) -> top.get(v) >= h - Q / 16 ? Math.min(0, bottom.get(v) - h) : 0, true, -1, Long.MAX_VALUE, null);
    }

    /** A round dig at (cx, cz): radius and depth in blocks. Earth taken goes to the player's carried earth (development). */
    public static Result dig(ServerLevel level, ServerPlayer player, double cx, double cz, double radius, double depth) {
        Result r = edit(level, brush(cx, cz, radius, depth, -1));
        CARRIED.merge(player.getUUID(), -r.moved(), Long::sum);
        return r;
    }

    /**
     * A round fill at (cx, cz) from the player's carried earth (at most that much, exactly what is put down is taken), as
     * loose topsoil (development).
     */
    public static Result fill(ServerLevel level, ServerPlayer player, double cx, double cz, double radius, double depth) {
        long carried = CARRIED.getOrDefault(player.getUUID(), 0L);
        Long2IntOpenHashMap w = brush(cx, cz, radius, depth, 1);
        long want = 0;
        for (int v : w.values()) want += v;
        Long2IntOpenHashMap deltas = new Long2IntOpenHashMap();
        // only the vertices that may move share the earth, so none of it is lost to a refused vertex
        Long2IntOpenHashMap movable = new Long2IntOpenHashMap();
        for (var en : w.long2IntEntrySet()) if (editable(level, keyX(en.getLongKey()), keyZ(en.getLongKey()))) movable.put(en.getLongKey(), en.getIntValue());
        deltas.putAll(distribute(Math.min(want, carried), movable));
        Result r = edit(level, deltas.keySet(), (v, h) -> deltas.get(v), true, BulkMaterials.table().index("topsoil"), Long.MAX_VALUE, null);
        CARRIED.put(player.getUUID(), carried - r.moved());
        return r;
    }

    // ------------------------------------------------------------------------------------------------- the shovel

    /**
     * Shovel brushes (docs/gameplay/sandbox_v1_design.md 5.3, docs/worldgen/earthwork_a2.md 2, 4.4): a scoop r 0.8 m at most
     * 0.5 m deep (about 297 L: user 2026-10-22, the big shovel, about 300 L a stroke; it was r 0.5 m, 0.42 m, 97 L); pouring
     * a cone r 0.9 m (the big shovel's load spreads wider; it was 0.6 m); scraping r 0.8 m. LIFT: an entity rises with
     * poured earth at most 0.1 m (quanta).
     */
    public static final double DIG_RADIUS = 0.8, DIG_DEPTH = 0.5, POUR_RADIUS = 0.9, SCRAPE_RADIUS = 0.8;
    public static final int LIFT = 25;

    /** One scoop at (cx, cz) into a shovel with room quanta left; Result#taken: the earth it took by material. */
    public static Result scoop(ServerLevel level, double cx, double cz, int room) {
        if (room <= 0) return Result.none(0);
        Long2IntOpenHashMap w = brush(cx, cz, DIG_RADIUS, DIG_DEPTH, -1);
        return edit(level, w.keySet(), (v, h) -> w.get(v), true, -1, room, null);
    }

    /**
     * A pick's strike on bare rock (Rock R2, docs/gameplay/rock_r2_rock_face_mining_design.md 12, user 2026-10-22: as in
     * Valheim): a brush r STRIKE_RADIUS at most STRIKE_DEPTH deep cuts the rock at (cx, cz) down where it lies bare, no
     * rubble left and no shovel needed (#edit cut); Result#moved the quanta cut (negative), Result#taken them by material
     * (rock counts as rubble: #natural), what the strike's stones come from.
     */
    public static Result strike(ServerLevel level, double cx, double cz) {
        return strike(level, cx, cz, STRIKE_DEPTH);
    }

    /** The same with the brush depth at the middle given (U3a-2: a softer rock gives more to a strike, RockTypes#strikeScale). */
    public static Result strike(ServerLevel level, double cx, double cz, double depth) {
        Long2IntOpenHashMap w = brush(cx, cz, STRIKE_RADIUS, depth, -1);
        return edit(level, w.keySet(), (v, h) -> w.get(v), true, -1, Long.MAX_VALUE, null, true);
    }

    /**
     * Strike brush (m): r 0.6 m, 0.15 m deep at the middle: 0.297 pi r^2 depth, about 50 L (round 2, user 2026-10-22: 12 L a
     * strike was too slow).
     */
    public static final double STRIKE_RADIUS = 0.6, STRIKE_DEPTH = 0.15;

    /**
     * Whether the column under (x, z) is one a strike can work at all (server; a quick check before the swing): null when it
     * can, else the message key's last part ("hard": fixed ground, a cliff).
     */
    public static String strikable(ServerLevel level, double x, double z) {
        return status(level, (int) Math.floor(x), (int) Math.floor(z)) == FIXED ? "hard" : null;
    }

    /**
     * Pours amount quanta of material at (cx, cy, cz) (a cone, then slumping at its repose); only what goes down is in
     * Result#moved. An entity standing there rises with it by LIFT at most; under it the earth rises no further.
     */
    public static Result pour(ServerLevel level, double cx, double cy, double cz, int amount, int material) {
        Long2IntOpenHashMap w = cone(cx, cz, POUR_RADIUS), movable = new Long2IntOpenHashMap();
        for (var en : w.long2IntEntrySet()) if (editable(level, keyX(en.getLongKey()), keyZ(en.getLongKey()))) movable.put(en.getLongKey(), en.getIntValue());
        if (amount <= 0 || movable.isEmpty()) return Result.none(w.size());
        Long2IntOpenHashMap d = distribute(amount, movable);
        List<net.minecraft.world.entity.Entity> on = standing(level, cx, cy, cz, POUR_RADIUS);
        Result r = edit(level, d.keySet(), (v, h) -> d.get(v), true, material, amount, lifts(on));
        lift(level, on);
        return r;
    }

    /**
     * Scraping (sneak) around (cx, cy, cz) towards height target (quanta, the player's feet): cut, the ground above it into
     * a shovel with room quanta left; else the ground below it raised from the shovel's load (room: the load, of material).
     */
    public static Result scrape(ServerLevel level, double cx, double cy, double cz, int target, boolean cut, int room, int material) {
        if (room <= 0) return Result.none(0);
        Long2IntOpenHashMap w = brush(cx, cz, SCRAPE_RADIUS, 1, 1);
        Delta d = (v, h) -> {
            int q = (int) Math.round((target - h) * (double) w.get(v) / Q);
            return cut ? Math.min(0, q) : Math.max(0, q);
        };
        List<net.minecraft.world.entity.Entity> on = cut ? List.of() : standing(level, cx, cy, cz, SCRAPE_RADIUS);
        Result r = edit(level, w.keySet(), d, true, cut ? -1 : material, room, cut ? null : lifts(on));
        lift(level, on);
        return r;
    }

    private static final ThreadLocal<int[]> GROUND_HEIGHTS = ThreadLocal.withInitial(() -> new int[SIDE * SIDE]);

    /**
     * The skin under (x, z) for a vehicle's tyre (A3, docs/gameplay/sandbox_v1_design.md 3.8): out = {height (world y),
     * the triangle's upward unit normal x, y, z, grip, -}; grip -1 on natural ground (the caller's block table), else the
     * loose earth's at the nearest vertex. False where (x, z) is not in an edited column. The level's thread (a client:
     * its copies).
     */
    public static boolean vehicleGround(Level level, double x, double z, double[] out) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int[] h = GROUND_HEIGHTS.get();
        if (!heights(level, bx, bz, h, null)) return false;
        EarthGrid.ground(h, x - bx, z - bz, out);
        int vx = bx * PER_BLOCK + (int) out[4], vz = bz * PER_BLOCK + (int) out[5];
        EarthChunk c = get(level, vx >> 6, vz >> 6);
        int k = vertex(vx & 63, vz & 63);
        out[4] = c != null && c.loose(k) > 0 ? c.matGrip(c.mat(k)) : -1;
        return true;
    }

    /** The skin's height (blocks) at (x, z) of an edited column, NaN where there is none. */
    public static double surfaceAt(Level level, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int[] h = new int[SIDE * SIDE];
        if (!heights(level, bx, bz, h, null)) return Double.NaN;
        return skin(relative(h, 0), x - bx, z - bz);
    }

    /** Entities standing on the ground within radius of (cx, cz), their feet within half a block of cy. */
    private static List<net.minecraft.world.entity.Entity> standing(ServerLevel level, double cx, double cy, double cz, double radius) {
        var box = new net.minecraft.world.phys.AABB(cx - radius - 1, cy - 0.5, cz - radius - 1, cx + radius + 1, cy + 1, cz + radius + 1);
        return level.getEntities((net.minecraft.world.entity.Entity) null, box, e -> !e.isSpectator() && !e.noPhysics && e.isAlive() && !e.isPassenger());
    }

    /** The vertices under each entity's feet, each allowed to rise LIFT at most. */
    private static Long2IntOpenHashMap lifts(List<net.minecraft.world.entity.Entity> on) {
        if (on.isEmpty()) return null;
        Long2IntOpenHashMap out = new Long2IntOpenHashMap();
        for (var e : on) {
            var b = e.getBoundingBox();
            for (int vz = (int) Math.floor(b.minZ * PER_BLOCK); vz <= (int) Math.ceil(b.maxZ * PER_BLOCK); vz++)
                for (int vx = (int) Math.floor(b.minX * PER_BLOCK); vx <= (int) Math.ceil(b.maxX * PER_BLOCK); vx++) out.put(key(vx, vz), LIFT);
        }
        return out;
    }

    /** Each entity up onto the skin under its feet where that rose above them (by LIFT and a little at most). */
    private static void lift(ServerLevel level, List<net.minecraft.world.entity.Entity> on) {
        for (var e : on) {
            var b = e.getBoundingBox();
            double top = Double.NEGATIVE_INFINITY;
            for (double[] p : new double[][]{{b.minX, b.minZ}, {b.maxX, b.minZ}, {b.minX, b.maxZ}, {b.maxX, b.maxZ}, {e.getX(), e.getZ()}}) {
                double s = surfaceAt(level, p[0], p[1]);
                if (!Double.isNaN(s)) top = Math.max(top, s);
            }
            double up = top - e.getY();
            if (!(up > 1e-4) || up > (LIFT + 4) / (double) Q) continue;
            if (e instanceof ServerPlayer p) p.connection.teleport(p.getX(), top, p.getZ(), p.getYRot(), p.getXRot());
            else e.setPos(e.getX(), top, e.getZ());
        }
    }

    /** Edited columns within radius back to their first look and no longer edited; the number of columns. */
    public static int reset(ServerLevel level, int bx, int bz, int radius) {
        LongArrayList cols = new LongArrayList();
        Long2IntOpenHashMap back = new Long2IntOpenHashMap();
        for (int z = bz - radius; z <= bz + radius; z++) for (int x = bx - radius; x <= bx + radius; x++) {
            if (editedTop(level, x, z) == Integer.MIN_VALUE || !loaded(level, x, z)) continue;
            cols.add(key(x, z));
            for (int j = 0; j <= PER_BLOCK; j++) for (int i = 0; i <= PER_BLOCK; i++) {
                int vx = x * PER_BLOCK + i, vz = z * PER_BLOCK + j;
                EarthChunk o = get(level, vx >> 6, vz >> 6);
                int k = vertex(vx & 63, vz & 63);
                if (o.h(k) != o.base(k)) back.put(key(vx, vz), o.base(k) - o.h(k));
                o.setLoose(k, 0, 0);
            }
        }
        if (!back.isEmpty()) edit(level, back, false);
        var p = new BlockPos.MutableBlockPos();
        for (long c : cols) {
            int x = keyX(c), z = keyZ(c), col = column(x, z);
            EarthChunk e = get(level, x >> 4, z >> 4);
            int t = e.top0(col);
            BlockState top = e.topState(col);
            e.unmark(col);
            converting = true;
            try {
                for (int y = t + RAISE - 1; y > t; y--) if (soil(level.getBlockState(p.set(x, y, z)).getBlock())) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                if (top != null) level.setBlock(p.set(x, t, z), top, Block.UPDATE_ALL);
                // a rock top (Rock R2) had anything under it: the rock dug out came back as gravel (#refill)
                if (top != null && soil(top.getBlock()) && !ground(level.getBlockState(p.set(x, t - 1, z)).getBlock())) level.setBlock(p, bare(top), Block.UPDATE_ALL);
            } finally {
                converting = false;
            }
            DIRTY.add(ChunkPos.asLong(x >> 4, z >> 4));
        }
        // vertices no edited column reads any more
        for (long c : cols) {
            int x = keyX(c), z = keyZ(c);
            for (int j = 0; j <= PER_BLOCK; j++) for (int i = 0; i <= PER_BLOCK; i++) {
                int vx = x * PER_BLOCK + i, vz = z * PER_BLOCK + j;
                boolean read = false;
                for (long r : columnsOf(vx, vz)) if (editedTop(level, keyX(r), keyZ(r)) != Integer.MIN_VALUE) read = true;
                if (!read) get(level, vx >> 6, vz >> 6).clear(vertex(vx & 63, vz & 63));
                DIRTY.add(ChunkPos.asLong(vx >> 6, vz >> 6));
            }
        }
        return cols.size();
    }

    /**
     * Development check of the loaded chunks within radius of (cx, cz): {edited columns, columns with a missing vertex,
     * cells whose block does not follow the skin, vertices outside their columns' range}.
     */
    public static int[] check(ServerLevel level, int ccx, int ccz, int radius) {
        int[] out = new int[4];
        int[] h = new int[SIDE * SIDE];
        var p = new BlockPos.MutableBlockPos();
        for (int cz = ccz - radius; cz <= ccz + radius; cz++) for (int cx = ccx - radius; cx <= ccx + radius; cx++) {
            EarthChunk e = get(level, cx, cz);
            if (e == null) continue;
            for (int col = 0; col < 256; col++) {
                if (!e.edited(col)) continue;
                int x = cx * 16 + (col & 15), z = cz * 16 + (col >> 4), t = e.top0(col);
                out[0]++;
                if (!heights(level, x, z, h, null)) {
                    out[1]++;
                    continue;
                }
                int lo = min(h), hi = max(h);
                for (int y = Math.floorDiv(e.bottom(col), Q); y < t + RAISE; y++)
                    if ((kind(lo, hi, y) != AIR) != ground(level.getBlockState(p.set(x, y, z)).getBlock())) out[2]++;
                if (lo < e.bottom(col) - Q || hi > (t + RAISE + 1) * Q) out[3]++;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------- events

    /** Hook (TerrainSmooth#changed, both sides): a ground cell of an edited column changed by something else. */
    public static void blockChanged(Level level, BlockPos pos) {
        if (converting || level.isClientSide()) return;
        EarthChunk e = get(level, pos.getX() >> 4, pos.getZ() >> 4);
        int col = column(pos.getX(), pos.getZ());
        if (e == null || !e.edited(col) || pos.getY() < Math.floorDiv(e.bottom(col), Q) || pos.getY() >= e.top0(col) + RAISE) return;
        RECONCILE.add(pos.immutable());
    }

    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ground(event.getState().getBlock())) return;
        int[] h = new int[SIDE * SIDE];
        BlockPos pos = event.getPos();
        if (!heights(level, pos.getX(), pos.getZ(), h, null) || kind(min(h), max(h), pos.getY()) == AIR) return;
        event.setCanceled(true);
        event.getPlayer().displayClientMessage(Component.translatable(rock(event.getState().getBlock())
                ? "message.apocalypse_firstlight.pick.needs_pick" : "message.apocalypse_firstlight.earthwork.needs_shovel"), true);
    }

    /** Whether pos holds ground of an edited column (a skin cell or below it): the shovel's and the pick's, not a hand's. */
    private static boolean editedGround(Level level, BlockPos pos, BlockState state) {
        if (!ground(state.getBlock())) return false;
        int[] h = new int[SIDE * SIDE];
        return heights(level, pos.getX(), pos.getZ(), h, null) && kind(min(h), max(h), pos.getY()) != AIR;
    }

    /**
     * No break progress there in survival (Rock R2 round 2, user 2026-10-22: a rubble cell cracked and sounded as gravel
     * under a hand, and only the finished break was refused, #breaking).
     */
    @SubscribeEvent
    public static void breakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (event.getEntity().isCreative() || event.getPosition().isEmpty()) return;
        if (editedGround(event.getEntity().level(), event.getPosition().get(), event.getState())) event.setNewSpeed(0);
    }

    /** The first hit there says what it takes (server side, once per press; rock: boulder.RockFace). */
    @SubscribeEvent
    public static void hit(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || event.getAction() != net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START
                || event.getEntity().isCreative()) return;
        BlockState s = event.getLevel().getBlockState(event.getPos());
        if (!rock(s.getBlock()) && editedGround(event.getLevel(), event.getPos(), s))
            event.getEntity().displayClientMessage(Component.translatable("message.apocalypse_firstlight.earthwork.needs_shovel"), true);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (DIRTY.isEmpty() && RECONCILE.isEmpty())) return;
        ServerLevel level = event.getServer().overworld();
        if (!RECONCILE.isEmpty()) {
            List<BlockPos> list = new ArrayList<>(RECONCILE);
            RECONCILE.clear();
            int[] h = new int[SIDE * SIDE];
            for (BlockPos pos : list) {
                // a ground cell lost its ground (not to the skin): the skin goes down below it, as far as its vertices may
                if (ground(level.getBlockState(pos).getBlock()) || !heights(level, pos.getX(), pos.getZ(), h, null)) continue;
                int floor = pos.getY() * Q;
                Long2IntOpenHashMap down = new Long2IntOpenHashMap();
                for (int j = 0; j <= PER_BLOCK; j++) for (int i = 0; i <= PER_BLOCK; i++)
                    if (h[at(i, j)] > floor) down.put(key(pos.getX() * PER_BLOCK + i, pos.getZ() * PER_BLOCK + j), floor - h[at(i, j)]);
                if (!down.isEmpty()) edit(level, down, false);
            }
        }
        for (long c : DIRTY) {
            int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            EarthChunk e = ((EarthChunkHolder) chunk).afl$earth();
            if (e != null && e.isEmpty()) ((EarthChunkHolder) chunk).afl$setEarth(e = null);
            chunk.setUnsaved(true);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) TerrainSmooth.forget(level, cx + dx, cz + dz);
            AflNetwork.sendEarth(chunk, new EarthChunkPacket(cx, cz, e));
            int[] ys = e == null ? null : e.yRange();
            com.antaurora.apofirstlight.ground.GroundEvents.areaChanged(level, cx, cz,
                    ys == null ? level.getMinBuildHeight() : ys[0], ys == null ? level.getMaxBuildHeight() : ys[1]);
        }
        DIRTY.clear();
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Watch event) {
        EarthChunk e = ((EarthChunkHolder) event.getChunk()).afl$earth();
        if (e != null && !e.isEmpty()) AflNetwork.sendEarth(event.getPlayer(), new EarthChunkPacket(event.getPos().x, event.getPos().z, e));
    }

    @SubscribeEvent
    public static void load(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !event.getData().contains(TAG, Tag.TAG_COMPOUND)) return;
        EarthChunk e = EarthChunk.load(event.getData().getCompound(TAG));
        ((EarthChunkHolder) chunk).afl$setEarth(e.isEmpty() ? null : e);
    }

    @SubscribeEvent
    public static void save(ChunkDataEvent.Save event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        EarthChunk e = ((EarthChunkHolder) chunk).afl$earth();
        if (e != null && !e.isEmpty()) event.getData().put(TAG, e.save());
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        DIRTY.clear();
        RECONCILE.clear();
        CARRIED.clear();
    }

    // ------------------------------------------------------------------------------------------------- client copies

    /** A chunk's copy from the server (client main thread). */
    static void received(int cx, int cz, EarthChunk data) {
        long k = ChunkPos.asLong(cx, cz);
        EarthChunk before = data == null ? CLIENT.remove(k) : CLIENT.put(k, data);
        ClientHooks hooks = clientHooks;
        if (hooks != null) hooks.changed(cx, cz, before, data);
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() != null && event.getLevel().isClientSide()) CLIENT.remove(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) CLIENT.clear();
    }
}
