package com.antaurora.apofirstlight.liquid.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The ground for the liquid in game until C's GroundView merges (docs/gameplay/water_v1_design.md 16.2): every block's
 * collision shape, rasterised to 1/16 m cells, answers with V0's signature and meaning (C 6.2 / 6.3: open area per slab in
 * 1/256 m2, open edge width per slab in 1/16 m, the same from both sides). So it sees what collides: AFL's smoothed slope
 * tops and earthwork as far as their collision shapes carry them, vanilla and AFL blocks; water blocks are open. A shape
 * reaching above its own block (a smoothed top is drawn up to 2 m above its block's floor, a fence 1.5 m) is solid in
 * the block above too (falling water 18: cut at 1 m, the ground under a slope's upper half was a hollow the water stood
 * in, a level sheet showing through the slope in triangles, in game 2026-10-25). Chunks not loaded on the server are
 * {@link #UNKNOWN}. Server thread only; one per level.
 */
public final class BlockGroundView implements GroundViewV0 {
    private static final long[] EMPTY = new long[64], FULL = new long[64];

    static {
        java.util.Arrays.fill(FULL, -1L);
    }

    private static final GroundMaterialV0 SOIL = new GroundMaterialV0("soil", GroundMaterialV0.Kind.SOIL, 35, 63),
            SAND = new GroundMaterialV0("sand", GroundMaterialV0.Kind.SAND, 34, 34),
            GRAVEL = new GroundMaterialV0("gravel", GroundMaterialV0.Kind.GRAVEL, 38, 45),
            CLAY = new GroundMaterialV0("clay", GroundMaterialV0.Kind.CLAY, 30, 70),
            MUD = new GroundMaterialV0("mud", GroundMaterialV0.Kind.MUD, 15, 20),
            ROCK = new GroundMaterialV0("rock", GroundMaterialV0.Kind.ROCK, Double.NaN, Double.NaN),
            SNOW = new GroundMaterialV0("snow", GroundMaterialV0.Kind.SNOW, Double.NaN, Double.NaN);

    private final Level level;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    /** Each shape's solid 1/16 m cells (16 slabs x 16 x 16, bit slab * 256 + x * 16 + z), by identity. */
    private final Map<VoxelShape, long[]> cells = new IdentityHashMap<>();
    /** The same for the part of a shape above its own block (1 to 2 m), in the block above. */
    private final Map<VoxelShape, long[]> above = new IdentityHashMap<>();

    public BlockGroundView(Level level) {
        this.level = level;
    }

    private LevelChunk chunk(int x, int z) {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4);
    }

    /**
     * The solid cells in block (x, y, z): {@link #EMPTY}, {@link #FULL} or a rasterised shape: its own block's shape, and
     * the part of the block below's that reaches up into it.
     */
    private long[] solid(LevelChunk chunk, int x, int y, int z) {
        if (y < level.getMinBuildHeight()) return FULL;
        if (y >= level.getMaxBuildHeight()) return EMPTY;
        long[] own = own(chunk, x, y, z);
        if (own == FULL) return FULL;
        long[] up = reaching(chunk, x, y - 1, z);
        if (up == EMPTY) return own;
        if (own == EMPTY) return up;
        long[] both = new long[64];
        for (int i = 0; i < 64; i++) both[i] = own[i] | up[i];
        return both;
    }

    /**
     * Block (x, y, z)'s own shape, in its block. Asked with a CollisionContext: the two-argument getCollisionShape hands
     * back the state's cached shape (a full cube for grass and dirt) without asking the block, past the hook that gives a
     * smoothed top its slope (TerrainSmoothBlockStateMixin): the liquid read every slope as cubes in game, the water
     * standing on their tops above the slopes as drawn (falling water 21; the offline scenes build their own ground).
     */
    private long[] own(LevelChunk chunk, int x, int y, int z) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        if (section.hasOnlyAir()) return EMPTY;
        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
        if (state.isAir()) return EMPTY;
        VoxelShape shape = state.getCollisionShape(level, pos.set(x, y, z), CollisionContext.empty());
        if (shape.isEmpty()) return EMPTY;
        if (shape == Shapes.block()) return FULL;
        long[] bits = cells.get(shape);
        if (bits == null) {
            if (cells.size() > 4096) cells.clear();
            cells.put(shape, bits = rasterise(shape.toAabbs(), 0));
        }
        return bits;
    }

    /** The part of block (x, y, z)'s shape above its own block, in the block above; EMPTY when it reaches no higher. */
    private long[] reaching(LevelChunk chunk, int x, int y, int z) {
        if (y < level.getMinBuildHeight()) return EMPTY;
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        if (section.hasOnlyAir()) return EMPTY;
        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
        if (state.isAir()) return EMPTY;
        VoxelShape shape = state.getCollisionShape(level, pos.set(x, y, z), CollisionContext.empty());
        if (shape.isEmpty() || shape.max(Direction.Axis.Y) <= 1 + 1e-6) return EMPTY;
        long[] bits = above.get(shape);
        if (bits == null) {
            if (above.size() > 4096) above.clear();
            above.put(shape, bits = rasterise(shape.toAabbs(), 1));
        }
        return bits;
    }

    /**
     * Boxes (block coordinates) into solid 1/16 m cells of the block {@code up} blocks above theirs: a cell is solid where a
     * box covers it, rounded to the 1/16 m grid. Public for the offline check.
     */
    public static long[] rasterise(Iterable<AABB> boxes, int up) {
        long[] bits = new long[64];
        for (AABB b : boxes) {
            int x0 = cell(b.minX), x1 = cell(b.maxX), y0 = cell(b.minY - up), y1 = cell(b.maxY - up), z0 = cell(b.minZ), z1 = cell(b.maxZ);
            for (int s = y0; s < y1; s++)
                for (int i = x0; i < x1; i++)
                    for (int k = z0; k < z1; k++) {
                        int bit = s * 256 + i * 16 + k;
                        bits[bit >> 6] |= 1L << (bit & 63);
                    }
        }
        return bits;
    }

    /** How many 1/16 m cells of {@link #rasterise}'s result are solid (the offline check). */
    public static int count(long[] bits) {
        int n = 0;
        for (long b : bits) n += Long.bitCount(b);
        return n;
    }

    private static int cell(double v) {
        return Math.max(0, Math.min(16, (int) Math.round(v * 16)));
    }

    private static boolean at(long[] bits, int slab, int i, int k) {
        int bit = slab * 256 + i * 16 + k;
        return (bits[bit >> 6] & (1L << (bit & 63))) != 0;
    }

    @Override
    public void openArea(int x, int z, int y0, int y1, short[] out) {
        int n = (y1 - y0) * SLABS;
        LevelChunk chunk = chunk(x, z);
        if (chunk == null) {
            java.util.Arrays.fill(out, 0, n, (short) UNKNOWN);
            return;
        }
        for (int y = y0; y < y1; y++) {
            long[] bits = solid(chunk, x, y, z);
            int base = (y - y0) * SLABS;
            for (int s = 0; s < SLABS; s++) {
                int solid = bits == EMPTY ? 0 : bits == FULL ? AREA : Long.bitCount(bits[s * 4]) + Long.bitCount(bits[s * 4 + 1])
                        + Long.bitCount(bits[s * 4 + 2]) + Long.bitCount(bits[s * 4 + 3]);
                out[base + s] = (short) (AREA - solid);
            }
        }
    }

    @Override
    public void openEdge(int x, int z, Direction dir, int y0, int y1, byte[] out) {
        switch (dir) {
            case WEST -> { openEdge(x - 1, z, Direction.EAST, y0, y1, out); return; }
            case NORTH -> { openEdge(x, z - 1, Direction.SOUTH, y0, y1, out); return; }
            default -> { }
        }
        int n = (y1 - y0) * SLABS;
        boolean east = dir == Direction.EAST;
        int nx = east ? x + 1 : x, nz = east ? z : z + 1;
        LevelChunk a = chunk(x, z), b = chunk(nx, nz);
        if (a == null || b == null) {
            java.util.Arrays.fill(out, 0, n, (byte) UNKNOWN);
            return;
        }
        for (int y = y0; y < y1; y++) {
            long[] sa = solid(a, x, y, z), sb = solid(b, nx, y, nz);
            int base = (y - y0) * SLABS;
            for (int s = 0; s < SLABS; s++) {
                int w;
                if (sa == FULL || sb == FULL) w = 0;
                else if (sa == EMPTY && sb == EMPTY) w = SLABS;
                else {
                    w = 0;
                    for (int k = 0; k < SLABS; k++) {
                        // the cells either side of the edge: a's last row, b's first
                        boolean ca = east ? at(sa, s, 15, k) : at(sa, s, k, 15), cb = east ? at(sb, s, 0, k) : at(sb, s, k, 0);
                        if (!ca && !cb) w++;
                    }
                }
                out[base + s] = (byte) w;
            }
        }
    }

    @Override
    public double surfaceBelow(double px, double y, double pz) {
        int x = (int) Math.floor(px), z = (int) Math.floor(pz);
        LevelChunk chunk = chunk(x, z);
        if (chunk == null) return Double.NaN;
        int i = Math.min(15, (int) ((px - x) * 16)), k = Math.min(15, (int) ((pz - z) * 16));
        int slab = (int) Math.floor(y * SLABS);
        boolean inside = solidAt(chunk, x, z, i, k, slab);
        if (inside) {
            // inside solid: the top of that solid run (C 2026-10-24)
            while (slab + 1 < level.getMaxBuildHeight() * SLABS && solidAt(chunk, x, z, i, k, slab + 1)) slab++;
            return (slab + 1) / (double) SLABS;
        }
        for (int s = slab; s >= level.getMinBuildHeight() * SLABS; s--) if (solidAt(chunk, x, z, i, k, s)) return (s + 1) / (double) SLABS;
        return NO_GROUND_METRES;
    }

    /** No solid below at all (the void): {@link #NO_GROUND} in metres, as surfaceGrid gives it. */
    private static final double NO_GROUND_METRES = NO_GROUND / 256.0;

    private boolean solidAt(LevelChunk chunk, int x, int z, int i, int k, int slab) {
        long[] bits = solid(chunk, x, Math.floorDiv(slab, SLABS), z);
        return bits == FULL || bits != EMPTY && at(bits, Math.floorMod(slab, SLABS), i, k);
    }

    /** Either column's surface block is drawn sloped by the terrain smoothing (TerrainSmooth), a block apart at most. */
    @Override
    public boolean ramp(int x0, int z0, int y0, int x1, int z1, int y1) {
        if (Math.abs(y0 - y1) > 1 || !com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth.applies(level)) return false;
        LevelChunk c0 = chunk(x0, z0), c1 = chunk(x1, z1);
        if (c0 == null || c1 == null) return false;
        return com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth.geometry(level, c0, x0, y0, z0) >= 0
                || com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth.geometry(level, c1, x1, y1, z1) >= 0;
    }

    @Override
    public GroundMaterialV0 materialAt(int x, int y, int z) {
        LevelChunk chunk = chunk(x, z);
        if (chunk == null) return GroundMaterialV0.NONE;
        BlockState s = chunk.getBlockState(pos.set(x, y, z));
        if (s.isAir() || s.getCollisionShape(level, pos).isEmpty()) return GroundMaterialV0.NONE;
        if (s.is(BlockTags.SAND)) return SAND;
        if (s.is(Blocks.GRAVEL)) return GRAVEL;
        if (s.is(Blocks.CLAY)) return CLAY;
        if (s.is(Blocks.MUD)) return MUD;
        if (s.is(BlockTags.SNOW)) return SNOW;
        if (s.is(BlockTags.DIRT)) return SOIL;
        return ROCK;
    }

    @Override
    public void surfaceGrid(int chunkX, int chunkZ, int yFrom, int[] out) {
        for (int i = 0; i <= 64; i++) {
            for (int k = 0; k <= 64; k++) {
                double px = chunkX * 16 + Math.min(i * 0.25, 15.999), pz = chunkZ * 16 + Math.min(k * 0.25, 15.999);
                if (i == 64) px = chunkX * 16 + 16;
                if (k == 64) pz = chunkZ * 16 + 16;
                double s = surfaceBelow(px, yFrom, pz);
                out[i * 65 + k] = Double.isNaN(s) ? UNKNOWN_HEIGHT : s == NO_GROUND_METRES ? NO_GROUND : (int) Math.round(s * 256);
            }
        }
    }
}
