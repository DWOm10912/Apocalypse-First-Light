package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.antaurora.apofirstlight.ground.GravelGroundBlock;
import com.antaurora.apofirstlight.ground.TopsoilBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
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

    /** Chunks filled and the time spent here (dev: /afl dev terrain_v2 ecology prints them). */
    public static final java.util.concurrent.atomic.LongAdder CHUNKS = new java.util.concurrent.atomic.LongAdder(),
            NANOS = new java.util.concurrent.atomic.LongAdder();

    /**
     * aflRivers (the world's {@code river_flow} gate, C7 of the river plan): a river column above the sea and away from
     * a lake or the sea gets no water, only its channel; AFL's flow field fills it (liquid/world/RiverFlow). A column
     * in or beside sea or lake water keeps vanilla water (the mouth joins the vanilla water as before). Where that vanilla
     * water meets the open channel it is not set flowing (no riffle mark): it stands until a block beside it changes, and
     * then runs a few blocks into the channel (R1's known limit, until lakes and the sea are AFL's too).
     */
    public static void apply(TerrainPlanSurface plan, ChunkAccess chunk, BlockState stone, boolean aflRivers) {
        apply(plan, chunk, stone, aflRivers && plan.rivers != null ? HydroQuery.of(plan.rivers) : null);
    }

    /**
     * The same with the world's river query (RiverChannels: its channel narrows at the world's road works); null: no AFL
     * rivers. R2 (river / lake V1 13): the channel is wider than the plan's, so a column the network leaves alone (no
     * river, no bank) is cut too where the query's channel or its banks reach it.
     */
    public static void apply(TerrainPlanSurface plan, ChunkAccess chunk, BlockState stone, HydroQuery hydro) {
        long t0 = System.nanoTime();
        try {
            applyTimed(plan, chunk, stone, hydro);
        } finally {
            CHUNKS.increment();
            NANOS.add(System.nanoTime() - t0);
        }
    }

    /** Is the column at the grid index's water the sea's or a lake's (not a river's)? */
    private static boolean fixedWater(int[] level, boolean[] fixed, int i) {
        return level[i] != Integer.MIN_VALUE && fixed[i];
    }

    private static void applyTimed(TerrainPlanSurface plan, ChunkAccess chunk, BlockState stone, HydroQuery hydro) {
        boolean aflRivers = hydro != null;
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        RiverNetwork.Column col = new RiverNetwork.Column();
        // water levels of the chunk and a one-column ring round it (riffle check): a river's level, the sea's Y62 in sea
        // and estuary columns, a federal lake's top inside its cells, MIN_VALUE elsewhere
        int[] level = new int[18 * 18];
        boolean[] fixed = new boolean[18 * 18];
        double[] share = new double[1];
        boolean any = plan.layers != null && nearLake(plan, x0, z0);
        for (int dz = -1; dz <= 16; dz++) for (int dx = -1; dx <= 16; dx++) {
            int x = x0 + dx, z = z0 + dz, wc = plan.waterClass(x, z);
            int l = wc == 1 || wc == 2 ? RiverNetwork.SEA_WATER_TOP : Integer.MIN_VALUE;
            boolean fix = l != Integer.MIN_VALUE;
            if (plan.rivers != null) {
                plan.rivers.column(x, z, col);
                if (col.kind == RiverNetwork.Column.RIVER) l = wc == 1 || wc == 2 ? Math.min(l, col.level) : col.level;
                if (col.kind != RiverNetwork.Column.NONE && dx >= 0 && dz >= 0 && dx < 16 && dz < 16) any = true;
            }
            if (plan.layers != null) {
                int lake = plan.lakeTopAt(x + 0.5, z + 0.5, share);
                if (lake != Integer.MIN_VALUE && share[0] > 0) {
                    l = l == Integer.MIN_VALUE ? lake : Math.min(l, lake);
                    fix = true;
                }
            }
            level[(dz + 1) * 18 + dx + 1] = l;
            fixed[(dz + 1) * 18 + dx + 1] = fix;
        }
        if (!any && hydro != null && hydro.touches(x0, z0)) any = true;
        boolean marsh = false;
        for (int k = 0; k < 9 && !marsh; k++) if (plan.waterClass(x0 + (k % 3) * 7 + 1, z0 + (k / 3) * 7 + 1) == 3) marsh = true;
        if (!any && !marsh && !nearMarsh(plan, x0, z0)) return;
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockState water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        RiverCarver.Edit e = new RiverCarver.Edit();
        HydroQuery.Point hp = hydro == null ? null : new HydroQuery.Point();
        RiverBed.Out bed = hydro == null ? null : new RiverBed.Out();
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx, z = z0 + lz;
            int top = ocean.getFirstAvailable(lx, lz) - 1;      // the highest solid block (water and air excluded)
            RiverCarver.plan(plan, x, z, top, col, e);
            // AFL's river (R1, R2): a river or bank column, or one the wider channel reaches, away from the sea's and
            // lakes' water takes the measured channel
            BlockState bedTop = null;
            if (hydro != null && (e.kind == RiverCarver.NONE || e.kind == RiverCarver.RIVER || e.kind == RiverCarver.BANK)
                    && !nearFixed(level, fixed, (lz + 1) * 18 + lx + 1) && hydro.near(x + 0.5, z + 0.5, RiverBed.REACH, hp)
                    && hp.level > RiverNetwork.SEA_WATER_TOP) {
                RiverBed.plan(hp, x, z, top, bed);
                if (e.kind == RiverCarver.NONE) e.kind = RiverCarver.BANK;
                e.ground = bed.top;
                e.water = Integer.MIN_VALUE;
                e.sealTo = Math.min(e.sealTo, bed.top - 2);
                bedTop = bedState(bed.material);
            }
            if (e.kind == RiverCarver.NONE) continue;
            int newTop = e.ground;
            // ground: fill up to the new top, clear above it (to the old top and any water the noise put there)
            int clearTo = Math.max(top, e.water == Integer.MIN_VALUE ? newTop : e.water);
            for (int y = Math.max(minY, Math.min(newTop, top) + 1); y <= Math.min(maxY, newTop); y++) set(chunk, lx, y, lz, stone, ocean, surface);
            int waterTop = e.water == Integer.MIN_VALUE ? newTop : e.water;
            boolean afl = false;
            if (aflRivers && e.kind == RiverCarver.RIVER && e.water > RiverNetwork.SEA_WATER_TOP) {
                int i = (lz + 1) * 18 + lx + 1;
                afl = !fixedWater(level, fixed, i);
                for (int nb : new int[]{i - 1, i + 1, i - 18, i + 18}) if (fixedWater(level, fixed, nb)) afl = false;
            }
            if (afl) waterTop = newTop;
            if (bedTop != null && newTop >= minY && newTop <= maxY) set(chunk, lx, newTop, lz, bedTop, ocean, surface);
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
            // riffle: lower water beside this source (the next reach, the sea at a mouth, a lake's outlet) flows once
            // the chunk is live
            // (in a world with AFL rivers, no river column is set flowing: the kept vanilla water at a mouth would run into
            // the open channel next to it)
            if (!(aflRivers && e.kind == RiverCarver.RIVER) && (e.kind == RiverCarver.RIVER || e.kind == RiverCarver.LAKE)) {
                int i = (lz + 1) * 18 + lx + 1;
                boolean step = false;
                for (int nb : new int[]{level[i - 1], level[i + 1], level[i - 18], level[i + 18]})
                    if (nb != Integer.MIN_VALUE && nb < e.water) step = true;
                if (step) chunk.markPosForPostprocessing(new BlockPos(x, e.water, z));
            }
        }
    }

    /** The column at the grid index, or one beside it, holds the sea's or a lake's water. */
    private static boolean nearFixed(int[] level, boolean[] fixed, int i) {
        if (fixedWater(level, fixed, i)) return true;
        for (int nb : new int[]{i - 1, i + 1, i - 18, i + 18}) if (fixedWater(level, fixed, nb)) return true;
        return false;
    }

    /** RiverBed's top material as the AFL block (natural ground N1c); null: left to the surface rules. */
    private static BlockState bedState(byte m) {
        return switch (m) {
            case RiverBed.GRAVEL -> AflBlocks.GRAVEL.get().defaultBlockState().setValue(GravelGroundBlock.KIND, GravelGroundBlock.Kind.ROUND);
            case RiverBed.SAND -> AflBlocks.SAND.get().defaultBlockState();
            case RiverBed.MUD -> AflBlocks.MUD.get().defaultBlockState();
            case RiverBed.BARE -> ((TopsoilBlock) AflBlocks.TOPSOIL.get()).bare();
            default -> null;
        };
    }

    /** A federal lake within two cells of the chunk (corners, edge middles and centre). */
    private static boolean nearLake(TerrainPlanSurface plan, int x0, int z0) {
        for (int k = 0; k < 9; k++) if (plan.lakeNear(x0 + (k % 3) * 7.5 + 0.5, z0 + (k / 3) * 7.5 + 0.5) != Integer.MIN_VALUE) return true;
        return false;
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
