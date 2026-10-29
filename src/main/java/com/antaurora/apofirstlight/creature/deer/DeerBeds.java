package com.antaurora.apofirstlight.creature.deer;

import com.antaurora.apofirstlight.block.CurbGeometry;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.antaurora.apofirstlight.tree.TreeBlocks;
import com.antaurora.apofirstlight.vegetation.MeshPlantBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a deer beds down (docs/gameplay/afl_whitetail_deer_d0_design.md 13): in cover and away from roads. Up to 20 walkable
 * spots within 40 blocks (and where it stands) are scored, once each time it goes to bed, from the blocks round them:
 * <ul>
 *   <li><b>screen</b>: tall plants, saplings and bushes, leaves and trunks at its own height in the 7 x 7 round it (hides a
 *   lying deer);</li>
 *   <li><b>canopy</b>: of those 49 columns, the share with leaves or a tree 2–7 blocks up;</li>
 *   <li><b>road</b>: road surface (CurbGeometry.ROAD) or anything not natural ground underfoot, 4–16 blocks out in 8
 *   directions, the nearer the worse.</li>
 * </ul>
 * Only loaded chunks are read. A follower beds 2–5 blocks from where its leader lies ({@link #near}).
 */
final class DeerBeds {
    private static final int TRIES = 20, RANGE = 40;

    private DeerBeds() {
    }

    /** The best spot found, or null (nothing walkable on natural ground: it lies where it is). */
    @Nullable
    static BlockPos find(DeerEntity deer) {
        if (!(deer.level() instanceof ServerLevel level)) return null;
        BlockPos best = null;
        double bestScore = score(level, deer.blockPosition());
        if (bestScore > -1e8) best = deer.blockPosition();
        for (int i = 0; i < TRIES; i++) {
            Vec3 v = LandRandomPos.getPos(deer, RANGE, 8);
            if (v == null) continue;
            BlockPos p = BlockPos.containing(v);
            double s = score(level, p);
            if (s > bestScore) {
                bestScore = s;
                best = p;
            }
        }
        return best;
    }

    /** A spot 2–5 blocks from {@code center} (the leader's bed) on natural ground, or null. */
    @Nullable
    static BlockPos near(ServerLevel level, BlockPos center, RandomSource random) {
        for (int i = 0; i < 8; i++) {
            double a = random.nextDouble() * Mth.TWO_PI, r = 2 + random.nextDouble() * 3;
            int x = Mth.floor(center.getX() + 0.5 + Math.cos(a) * r), z = Mth.floor(center.getZ() + 0.5 + Math.sin(a) * r);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(feet.getY() - center.getY()) <= 2 && lieable(level, feet)) return feet;
        }
        return null;
    }

    /** Natural ground under it, no water, room for a lying deer. */
    private static boolean lieable(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) return false;
        BlockState ground = level.getBlockState(feet.below());
        if (!TerrainSmooth.ground(ground.getBlock()) || !level.getFluidState(feet).isEmpty()) return false;
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
    }

    static double score(ServerLevel level, BlockPos feet) {
        if (!lieable(level, feet)) return -1e9;
        int screen = 0, canopy = 0;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = feet.getX() + dx, z = feet.getZ() + dz;
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
                for (int dy = 0; dy <= 1; dy++) {
                    if (screens(level.getBlockState(q.set(x, feet.getY() + dy, z)))) screen++;
                }
                for (int dy = 2; dy <= 7; dy++) {
                    BlockState s = level.getBlockState(q.set(x, feet.getY() + dy, z));
                    if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || TreeBlocks.isTree(s)) {
                        canopy++;
                        break;
                    }
                }
            }
        }
        return 3.0 * screen / 98.0 + 2.0 * canopy / 49.0 - 4.0 * road(level, feet);
    }

    private static boolean screens(BlockState s) {
        if (s.isAir()) return false;
        if (s.getBlock() instanceof MeshPlantBlock plant) return plant.species.tall;
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.SAPLINGS) || TreeBlocks.isTree(s);
    }

    /** 0..1: how near a road (1) or other made ground (half) is, within 16 blocks. */
    private static double road(ServerLevel level, BlockPos feet) {
        double worst = 0;
        for (int r = 4; r <= 16; r += 4) {
            for (int k = 0; k < 8; k++) {
                double a = k * Mth.TWO_PI / 8;
                int x = Mth.floor(feet.getX() + 0.5 + Math.cos(a) * r), z = Mth.floor(feet.getZ() + 0.5 + Math.sin(a) * r);
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockState s = level.getBlockState(new BlockPos(x, y, z));
                double near = (20.0 - r) / 16.0;
                if (s.is(CurbGeometry.ROAD)) worst = Math.max(worst, near);
                else if (!TerrainSmooth.ground(s.getBlock()) && s.getFluidState().isEmpty() && !s.is(BlockTags.LOGS)
                        && !s.is(BlockTags.LEAVES) && !TreeBlocks.isTree(s)) worst = Math.max(worst, near * 0.5);
            }
            if (worst >= 1.0) break;
        }
        return worst;
    }
}
