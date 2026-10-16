package com.antaurora.apofirstlight.vehicle.physics;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.antaurora.apofirstlight.vehicle.VehicleColliders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game's side of {@link VehicleWorld} (docs/vehicles/first_pickup_v0_design.md section 5), on the thread that owns the
 * level (the server's, or the driving client's). Ground, in order:
 * <ol><li>road: the C3 road layer's own surface (RoadSurfaceQuery, not built yet: section 5.1; until then a road's blocks
 * answer as blocks);</li>
 * <li>smoothed terrain: a natural surface top (TerrainSmooth) answers with its drawn surface, two triangles per block,
 * not the 1/16 stepped collision boxes, so the tyres roll on the slope;</li>
 * <li>blocks: the top of the collision shape in that column.</li></ol>
 * Grip by block (a name keyword table, cached per block). Hull points: smoothed tops by their surface, other blocks by
 * their collision boxes, then other vehicles (VehicleColliders). Only loaded chunks are read.
 */
public final class LevelVehicleWorld implements VehicleWorld {
    private static final Map<Block, Double> GRIP = new ConcurrentHashMap<>();
    /** Name keyword to grip, first match wins (asphalt 1.0 is the reference; the tyre's own grip is in VehicleSpec). */
    private static final String[][] GRIP_WORDS = {
            {"sandstone", "0.90"}, {"blue_ice", "0.10"}, {"packed_ice", "0.12"}, {"ice", "0.15"}, {"snow", "0.40"}, {"mud", "0.45"}, {"clay", "0.55"},
            {"sand", "0.55"}, {"gravel", "0.65"}, {"grass", "0.70"}, {"podzol", "0.65"}, {"mycelium", "0.65"}, {"moss", "0.60"},
            {"dirt", "0.70"}, {"farmland", "0.60"}, {"leaves", "0.50"}, {"asphalt", "1.00"}, {"road", "1.00"}, {"concrete", "0.95"},
            {"terracotta", "0.80"}, {"stone", "0.90"}, {"brick", "0.90"}, {"planks", "0.80"}, {"log", "0.70"}, {"wool", "0.70"},
            {"slime", "0.30"}, {"honey", "0.30"}};
    private static final double DEFAULT_GRIP = 0.85;

    private final Level level;
    private final VehicleColliders.Collider self;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public LevelVehicleWorld(Level level, VehicleColliders.Collider self) {
        this.level = level;
        this.self = self;
    }

    @Override
    public boolean ground(double x, double z, double yTop, double yBottom, Ground out) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        LevelChunk chunk = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
        if (chunk == null) return false;
        boolean smooth = smooth();
        double fx = x - bx, fz = z - bz;
        int bottom = Math.max(Mth.floor(yBottom) - 2, level.getMinBuildHeight());   // smoothed tops reach 2 above their floor
        for (int y = Mth.floor(yTop); y >= bottom; y--) {
            BlockState state = chunk.getBlockState(pos.set(bx, y, bz));
            if (state.isAir()) continue;
            Block block = state.getBlock();
            if (smooth && TerrainSmooth.ground(block)) {
                long g = TerrainSmooth.geometry(level, chunk, bx, y, bz);
                if (g >= 0) {
                    float[] c = SmoothRules.corners(g);
                    double h = y + SmoothRules.surface(c, fx, fz);
                    if (h < yBottom) break;
                    // the triangle's slope (SmoothRules#surface: split on the nw-se diagonal)
                    double dx = fz >= fx ? c[3] - c[2] : c[1] - c[0], dz = fz >= fx ? c[2] - c[0] : c[3] - c[1];
                    double len = Math.sqrt(dx * dx + 1 + dz * dz);
                    out.set(h, -dx / len, 1 / len, -dz / len, grip(block), Source.SMOOTH);
                    return true;
                }
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = shape.max(Direction.Axis.Y, Mth.clamp(fz, 0.0, 0.9999), Mth.clamp(fx, 0.0, 0.9999));
            if (Double.isInfinite(top)) continue;   // nothing in this column of the block (a fence post beside it)
            double h = y + top;
            if (h < yBottom) break;
            out.set(h, 0, 1, 0, grip(block), Source.BLOCK);
            return true;
        }
        out.none();
        return true;
    }

    @Override
    public boolean solid(double x, double y, double z, Push out) {
        int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
        LevelChunk chunk = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
        if (chunk == null) return false;
        boolean smooth = smooth();
        for (int dy = 0; dy <= 2; dy++) {
            int yy = by - dy;
            if (yy < level.getMinBuildHeight()) break;
            BlockState state = chunk.getBlockState(pos.set(bx, yy, bz));
            if (state.isAir()) continue;
            if (smooth && TerrainSmooth.ground(state.getBlock())) {
                long g = TerrainSmooth.geometry(level, chunk, bx, yy, bz);
                if (g >= 0) {
                    float[] c = SmoothRules.corners(g);
                    double fx = x - bx, fz = z - bz, h = yy + SmoothRules.surface(c, fx, fz);
                    if (y >= h) continue;
                    double dx = fz >= fx ? c[3] - c[2] : c[1] - c[0], dz = fz >= fx ? c[2] - c[0] : c[3] - c[1];
                    double len = Math.sqrt(dx * dx + 1 + dz * dz);
                    out.set(-dx / len, 1 / len, -dz / len, (h - y) / len);
                    return true;
                }
            }
            if (dy == 2) continue;   // only a smoothed top reaches two blocks up; fences and walls one and a half
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            for (AABB b : shape.toAabbs()) {
                double x0 = bx + b.minX, x1 = bx + b.maxX, y0 = yy + b.minY, y1 = yy + b.maxY, z0 = bz + b.minZ, z1 = bz + b.maxZ;
                if (x < x0 || x > x1 || y < y0 || y > y1 || z < z0 || z > z1) continue;
                // out the nearest side (never down: a roof point under a block would be driven into the ground)
                double best = y1 - y;
                out.set(0, 1, 0, best);
                if (x - x0 < best) out.set(-1, 0, 0, best = x - x0);
                if (x1 - x < best) out.set(1, 0, 0, best = x1 - x);
                if (z - z0 < best) out.set(0, 0, -1, best = z - z0);
                if (z1 - z < best) out.set(0, 0, 1, z1 - z);
                return true;
            }
        }
        return VehicleColliders.pushOut(level, x, y, z, self, out);
    }

    private boolean smooth() {
        return TerrainSmooth.side(level).collides() && TerrainSmooth.applies(level);
    }

    /** A block's tyre grip (keyword table on its registry name). */
    public static double grip(Block block) {
        return GRIP.computeIfAbsent(block, b -> {
            String name = BuiltInRegistries.BLOCK.getKey(b).getPath();
            for (String[] w : GRIP_WORDS) if (name.contains(w[0])) return Double.parseDouble(w[1]);
            return DEFAULT_GRIP;
        });
    }
}
