package com.antaurora.apofirstlight.vehicle.physics;

import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.antaurora.apofirstlight.vehicle.VehicleColliders;
import com.antaurora.apofirstlight.worldgen.network.road.RoadSurfaceQuery;
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
 * <ol><li>road: the C3 road layer's own surface (worldgen/network/road/RoadSurfaceQuery, section 5.1, hooked up
 * 2026-10-16): the road design's continuous height and normal for lanes, shoulders, decks and tunnel bores, graded turf
 * as drawn; grip by surface (asphalt 1.0, concrete 0.95, turf 0.70). A blocking answer (parapet, barrier, walkway) is
 * no ground: the layers below answer, so its blocks stop the wheel and the body as any wall does;</li>
 * <li>edited earth (Earthwork A3, docs/gameplay/sandbox_v1_design.md 3.8): a column dug or filled answers with its
 * skin's triangle, height and normal, not the 1/8 column boxes; grip the loose earth's (earthwork bulk material) or,
 * on natural ground, the block's;</li>
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
    /** The road layer's answer (one per vehicle: asked on the thread that owns the level only). */
    private final RoadSurfaceQuery.Out road = new RoadSurfaceQuery.Out();
    /** Earthwork#vehicleGround's answer: height, normal, grip */
    private final double[] earth = new double[6];

    public LevelVehicleWorld(Level level, VehicleColliders.Collider self) {
        this.level = level;
        this.self = self;
    }

    @Override
    public boolean ground(double x, double z, double yHint, double yTop, double yBottom, Ground out) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        LevelChunk chunk = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
        if (chunk == null) return false;
        if (RoadSurfaceQuery.sample(level, x, z, yHint, road) && !road.blocking && road.height <= yTop && road.height >= yBottom) {
            out.set(road.height, road.nx, road.ny, road.nz, roadGrip(road.surface), Source.ROAD);
            return true;
        }
        boolean smooth = smooth();
        double fx = x - bx, fz = z - bz;
        int bottom = Math.max(Mth.floor(yBottom) - 2, level.getMinBuildHeight());   // smoothed tops reach 2 above their floor
        for (int y = Mth.floor(yTop); y >= bottom; y--) {
            BlockState state = chunk.getBlockState(pos.set(bx, y, bz));
            if (state.isAir()) continue;
            Block block = state.getBlock();
            if (smooth && Earthwork.ground(block) && Earthwork.vehicleGround(level, x, z, earth)) {
                if (earth[0] < yBottom) break;
                out.set(earth[0], earth[1], earth[2], earth[3], earth[4] >= 0 ? earth[4] : grip(block), Source.EARTH);
                return true;
            }
            if (smooth && TerrainSmooth.ground(block)) {
                long g = TerrainSmooth.geometry(level, chunk, bx, y, bz);
                if (g >= 0) {
                    float[] c = TerrainSmooth.top(level, chunk, bx, y, bz, g);   // R2b: the smooth top
                    double h = y + SmoothRules.surface(c, fx, fz);
                    if (h < yBottom) break;
                    // the drawn face's slope (SmoothRules#slope: its diagonal or its fan, dig R1)
                    double[] d = SmoothRules.slope(c, fx, fz);
                    double dx = d[0], dz = d[1];
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
            if (smooth && Earthwork.ground(state.getBlock()) && Earthwork.vehicleGround(level, x, z, earth)) {
                if (y >= earth[0]) continue;   // above the skin (a lower cell of the column gives the same skin)
                out.set(earth[1], earth[2], earth[3], (earth[0] - y) * earth[2]);
                return true;
            }
            if (smooth && TerrainSmooth.ground(state.getBlock())) {
                long g = TerrainSmooth.geometry(level, chunk, bx, yy, bz);
                if (g >= 0) {
                    float[] c = TerrainSmooth.top(level, chunk, bx, yy, bz, g);   // R2b: the smooth top
                    double fx = x - bx, fz = z - bz, h = yy + SmoothRules.surface(c, fx, fz);
                    if (y >= h) continue;
                    double[] d = SmoothRules.slope(c, fx, fz);
                    double dx = d[0], dz = d[1];
                    double len = Math.sqrt(dx * dx + 1 + dz * dz);
                    out.set(-dx / len, 1 / len, -dz / len, (h - y) / len);
                    return true;
                }
            }
            if (dy == 2) continue;   // only a smoothed top reaches two blocks up; fences and walls one and a half
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty() || box(boxes(shape), bx, yy, bz, x, y, z) == null) continue;
            // out the nearest way out of the solid (never down: a roof point under a block would be driven into the ground)
            double best = exit(x, y, z, 0, 1, 0);
            out.set(0, 1, 0, best);
            for (int[] d : SIDES) {
                double e = exit(x, y, z, d[0], 0, d[1]);
                if (e < best) out.set(d[0], 0, d[1], best = e);
            }
            return true;
        }
        return VehicleColliders.pushOut(level, x, y, z, self, out);
    }

    /** The four horizontal ways out (x, z). */
    private static final int[][] SIDES = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    /** Exits searched this far (m; further counts as no way out that side) in at most this many boxes. */
    private static final double EXIT_REACH = 2.0;
    private static final int EXIT_HOPS = 48;

    /**
     * How far point (x, y, z) must move along an axis direction to leave the blocks' collision (2026-10-16). Not the
     * nearest face of the one box it is in: a shape cut into thin columns (road_structure follows the bridge mesh on 1/8
     * block columns, BridgeMesh.solid) has a face of each column a few cm away, inside the next column, and the hull was
     * pushed from column to column through a wall (user in game, T15 through a bridge's wing wall). From box to box
     * along the direction, across block borders, until out; EXIT_REACH when it is not out by then.
     */
    private double exit(double x, double y, double z, int dx, int dy, int dz) {
        double t = 0;
        for (int hop = 0; hop < EXIT_HOPS && t < EXIT_REACH; hop++) {
            double px = x + dx * (t + 1e-4), py = y + dy * (t + 1e-4), pz = z + dz * (t + 1e-4);
            AABB b = solidBox(px, py, pz);
            if (b == null) return t;
            // the box's far face along the direction (world coordinates)
            t = dx > 0 ? b.maxX - x : dx < 0 ? x - b.minX : dy > 0 ? b.maxY - y : dz > 0 ? b.maxZ - z : z - b.minZ;
        }
        return EXIT_REACH;
    }

    /** The collision box (world coordinates) holding point (x, y, z): of its block, or of the block under it (fences, walls). */
    private AABB solidBox(double x, double y, double z) {
        int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
        LevelChunk chunk = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
        if (chunk == null) return null;
        for (int yy = by; yy >= by - 1 && yy >= level.getMinBuildHeight(); yy--) {
            BlockState state = chunk.getBlockState(pos.set(bx, yy, bz));
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            AABB b = box(boxes(shape), bx, yy, bz, x, y, z);
            if (b != null) return b;
        }
        return null;
    }

    private static AABB box(AABB[] boxes, int bx, int by, int bz, double x, double y, double z) {
        for (AABB b : boxes) {
            double x0 = bx + b.minX, x1 = bx + b.maxX, y0 = by + b.minY, y1 = by + b.maxY, z0 = bz + b.minZ, z1 = bz + b.maxZ;
            if (x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1) return new AABB(x0, y0, z0, x1, y1, z1);
        }
        return null;
    }

    /** A shape's boxes, kept (shapes are shared per state, road_structure's per cell): toAabbs builds a new list each call. */
    private final Map<VoxelShape, AABB[]> shapeBoxes = new java.util.IdentityHashMap<>();

    private AABB[] boxes(VoxelShape shape) {
        AABB[] b = shapeBoxes.get(shape);
        if (b == null) {
            if (shapeBoxes.size() > 512) shapeBoxes.clear();
            b = shape.toAabbs().toArray(new AABB[0]);
            shapeBoxes.put(shape, b);
        }
        return b;
    }

    private boolean smooth() {
        return TerrainSmooth.side(level).collides() && TerrainSmooth.applies(level);
    }

    /** A block's tyre grip (keyword table on its registry name). */
    /** Tyre grip on a road surface (the road layer gives the kind, Main the number: section 5.1). */
    static double roadGrip(RoadSurfaceQuery.Surface surface) {
        return switch (surface) {
            case ASPHALT -> 1.00;
            case CONCRETE -> 0.95;
            case TURF -> 0.70;
            case GRAVEL -> 0.65;   // the forest home's driveway: the gravel block's grip (GRIP_WORDS)
        };
    }

    public static double grip(Block block) {
        return GRIP.computeIfAbsent(block, b -> {
            String name = BuiltInRegistries.BLOCK.getKey(b).getPath();
            for (String[] w : GRIP_WORDS) if (name.contains(w[0])) return Double.parseDouble(w[1]);
            return DEFAULT_GRIP;
        });
    }
}
