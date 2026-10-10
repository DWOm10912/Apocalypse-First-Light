package com.antaurora.apofirstlight.worldgen.terrain.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Applies the Phase 2b river water (RiverCarver's column rule) to a chunk right after its noise fill
 * (mixin/NoiseFillRiverMixin, 2026-10-10; docs/worldgen/terrain_v2_phase2b_rivers_v1.md). Runs on the worker that
 * filled the chunk while it still holds the section locks, so it writes the sections unchecked and keeps the two
 * worldgen heightmaps in step itself (as NoiseBasedChunkGenerator.doFill does). Surface rules, carvers, features and
 * the fluid post-processing all come after it.
 * <ul>
 *   <li>Every column is decided from that column alone (RiverCarver.plan), so neighbouring chunks agree on their
 *   border;</li>
 *   <li>a river source with a lower river beside it (a riffle) is marked for post-processing: when the chunk becomes
 *   live it flows over the one-block step instead of standing as a water wall;</li>
 *   <li>cave air under the bed and under the banks is sealed with the default block (no water hanging over a cave,
 *   none leaking sideways into one).</li>
 * </ul>
 */
public final class RiverWaterFill {
    private RiverWaterFill() {
    }

    public static void apply(TerrainPlanSurface plan, ChunkAccess chunk, BlockState stone) {
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        RiverNetwork.Column col = new RiverNetwork.Column();
        // water levels of the chunk and a one-column ring round it (riffle check): a river's level, the sea's Y62 in sea
        // and estuary columns, MIN_VALUE elsewhere
        int[] level = new int[18 * 18];
        boolean any = false;
        for (int dz = -1; dz <= 16; dz++) for (int dx = -1; dx <= 16; dx++) {
            int x = x0 + dx, z = z0 + dz, wc = plan.waterClass(x, z);
            int l = wc == 1 || wc == 2 ? RiverNetwork.SEA_WATER_TOP : Integer.MIN_VALUE;
            if (wc != 1 && wc != 2 && plan.rivers != null) {
                plan.rivers.column(x, z, col);
                if (col.kind == RiverNetwork.Column.RIVER) l = col.level;
                if (col.kind != RiverNetwork.Column.NONE && dx >= 0 && dz >= 0 && dx < 16 && dz < 16) any = true;
            }
            level[(dz + 1) * 18 + dx + 1] = l;
        }
        boolean marsh = false;
        for (int k = 0; k < 9 && !marsh; k++) if (plan.waterClass(x0 + (k % 3) * 7 + 1, z0 + (k / 3) * 7 + 1) == 3) marsh = true;
        if (!any && !marsh && !nearMarsh(plan, x0, z0)) return;
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockState water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        RiverCarver.Edit e = new RiverCarver.Edit();
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx, z = z0 + lz;
            int top = ocean.getFirstAvailable(lx, lz) - 1;      // the highest solid block (water and air excluded)
            RiverCarver.plan(plan, x, z, top, col, e);
            if (e.kind == RiverCarver.NONE) continue;
            int newTop = e.ground;
            // ground: fill up to the new top, clear above it (to the old top and any water the noise put there)
            int clearTo = Math.max(top, e.water == Integer.MIN_VALUE ? newTop : e.water);
            for (int y = Math.max(minY, Math.min(newTop, top) + 1); y <= Math.min(maxY, newTop); y++) set(chunk, lx, y, lz, stone, ocean, surface);
            int waterTop = e.water == Integer.MIN_VALUE ? newTop : e.water;
            for (int y = newTop + 1; y <= Math.min(maxY, waterTop); y++) set(chunk, lx, y, lz, water, ocean, surface);
            for (int y = Math.max(waterTop, newTop) + 1; y <= Math.min(maxY, clearTo); y++) {
                BlockState old = chunk.getBlockState(pos.set(x, y, z));
                if (!old.isAir()) set(chunk, lx, y, lz, air, ocean, surface);
            }
            // seal cave air (and cave fluid) under the bed / bank
            for (int y = Math.min(newTop, maxY); y >= Math.max(minY + 1, e.sealTo); y--) {
                BlockState old = chunk.getBlockState(pos.set(x, y, z));
                if (old.isAir() || !old.getFluidState().isEmpty()) set(chunk, lx, y, lz, stone, ocean, surface);
            }
            // riffle: lower water beside this source (the next reach, the sea at a mouth) flows once the chunk is live
            if (e.kind == RiverCarver.RIVER) {
                int i = (lz + 1) * 18 + lx + 1;
                boolean step = false;
                for (int nb : new int[]{level[i - 1], level[i + 1], level[i - 18], level[i + 18]})
                    if (nb != Integer.MIN_VALUE && nb < e.water) step = true;
                if (step) chunk.markPosForPostprocessing(new BlockPos(x, e.water, z));
            }
        }
    }

    private static boolean nearMarsh(TerrainPlanSurface plan, int x0, int z0) {
        for (int k = 0; k < 4; k++) if (plan.waterClass(x0 + (k & 1) * 15, z0 + (k >> 1) * 15) == 3) return true;
        return false;
    }

    private static void set(ChunkAccess chunk, int lx, int y, int lz, BlockState state, Heightmap a, Heightmap b) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(lx, y & 15, lz, state, false);
        a.update(lx, y, lz, state);
        b.update(lx, y, lz, state);
    }
}
