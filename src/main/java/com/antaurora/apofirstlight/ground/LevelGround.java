package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.earthwork.RockCarve;
import com.antaurora.apofirstlight.earthwork.RockRelief;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.antaurora.apofirstlight.worldgen.network.road.RoadSurfaceQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A level's ground for GroundGeometry (terrain rework R0): Earthwork's edited columns, RockCarve's carved and RockRelief's
 * worn blocks, Terrain Smooth's sloped tops, roads' drawn tops (road drainage D1: RoadSurfaceQuery#ground), and every
 * other block's collision shape. The level's own thread.
 */
final class LevelGround implements GroundSource {
    private static final double[] EMPTY = new double[0], FULL = {0, 0, 0, 1, 1, 1};
    private static final Map<VoxelShape, double[]> BOXES = new IdentityHashMap<>();

    private final Level level;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private LevelChunk last;
    private int lastX = Integer.MIN_VALUE, lastZ;

    LevelGround(Level level) {
        this.level = level;
    }

    Level level() { return level; }

    LevelChunk chunk(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        if (cx != lastX || cz != lastZ) {
            last = level.getChunkSource().getChunkNow(cx, cz);
            lastX = cx;
            lastZ = cz;
        }
        return last;
    }

    BlockState state(int x, int y, int z) {
        LevelChunk c = chunk(x, z);
        return c == null ? null : c.getBlockState(pos.set(x, y, z));
    }

    @Override
    public boolean loaded(int x, int z) { return chunk(x, z) != null; }

    @Override
    public int minY() { return level.getMinBuildHeight(); }

    @Override
    public int top(int x, int z) {
        LevelChunk c = chunk(x, z);
        return c == null ? Integer.MIN_VALUE : c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
    }

    @Override
    public boolean earth(int x, int z, int[] h) { return Earthwork.heights(level, x, z, h, null); }

    @Override
    public long[] gone(int x, int y, int z) {
        if (level.dimension() != Level.OVERWORLD) return null;
        BlockState s = state(x, y, z);
        if (s == null) return null;
        Block b = s.getBlock();
        if (!Earthwork.rock(b) && !RockRelief.soil(b)) return null;
        return RockCarve.occupancy(level, level, pos.set(x, y, z), b);
    }

    @Override
    public long smooth(int x, int y, int z) {
        LevelChunk c = chunk(x, z);
        return c == null ? -1 : TerrainSmooth.geometry(level, c, x, y, z);
    }

    @Override
    public float[] smoothTop(int x, int y, int z, long k) {
        LevelChunk c = chunk(x, z);
        return c == null ? com.antaurora.apofirstlight.terrainsmooth.SmoothRules.corners(k) : TerrainSmooth.top(level, c, x, y, z, k);
    }

    @Override
    public boolean drawn(int x, int y, int z, double[] rel) {
        BlockState s = state(x, y, z);
        if (s == null || !(s.getBlock() instanceof RoadSurfaceBlock || s.getBlock() instanceof GradedTurfBlock)) return false;
        // the column's top: a road block under another solid block is that block's floor
        BlockState up = state(x, y + 1, z);
        if (up != null && !up.isAir() && !up.getCollisionShape(level, pos.set(x, y + 1, z)).isEmpty()) return false;
        if (!RoadSurfaceQuery.ground(RoadStructureBlock.boundWorks(), s, x, y, z, rel)) return false;
        // broken asphalt (road Mesh-ification P2): a vertex all of whose sub-cells in the column are gone lies on the base
        var c = s.getBlock() instanceof RoadSurfaceBlock ? com.antaurora.apofirstlight.road.RoadDamage.col(level, x, z) : null;
        if (c != null) com.antaurora.apofirstlight.road.RoadDamageChunk.holes(c, y, rel);
        return true;
    }

    @Override
    public double[] boxes(int x, int y, int z) {
        BlockState s = state(x, y, z);
        if (s == null || s.isAir()) return EMPTY;
        VoxelShape shape = s.getCollisionShape(level, pos.set(x, y, z));
        if (shape.isEmpty()) return EMPTY;
        if (shape == Shapes.block()) return FULL;
        synchronized (BOXES) {
            double[] b = BOXES.get(shape);
            if (b != null) return b;
            List<AABB> list = shape.toAabbs();
            b = new double[list.size() * 6];
            for (int i = 0; i < list.size(); i++) {
                AABB a = list.get(i);
                b[i * 6] = a.minX;
                b[i * 6 + 1] = a.minY;
                b[i * 6 + 2] = a.minZ;
                b[i * 6 + 3] = a.maxX;
                b[i * 6 + 4] = a.maxY;
                b[i * 6 + 5] = a.maxZ;
            }
            if (BOXES.size() > 4096) BOXES.clear();   // dynamic shapes (slopes elsewhere, carvings) come and go
            BOXES.put(shape, b);
            return b;
        }
    }
}
