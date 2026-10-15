package com.antaurora.apofirstlight.worldgen.home;

import com.antaurora.apofirstlight.worldgen.structure.AflTemplatePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.util.Set;

/**
 * The forest start home's chunk pass (M4 round 1, docs/worldgen/forest_home_b_m4_placement.md): at the start of each
 * chunk's feature step (ChunkGeneratorForestHomeMixin), before structures and features, a chunk under the plot or its
 * graded ring
 * <ol>
 *   <li>grades its own columns: under the plot the ground is filled up to the template's floor and cut down to its top
 *   (the template, 37 x 13 x 26 with its air, overwrites everything between); across the ring of GRADE_MARGIN round the
 *   plot the ground blends from the yard's grass layer to the natural top (smoothstep), keeping the natural top block
 *   (grass, podzol, stone ...) and the block under it. Columns with water on the ground keep their banks (the planner
 *   never puts the plot over a stream);</li>
 *   <li>places its own slice of the template (the template clipped to the chunk; known shape, so no neighbour shape
 *   updates reach over the chunk's edge; liquids not kept; the AFL block entity processor turns the packed wiring and
 *   plug offsets with the house).</li>
 * </ol>
 * Each column depends on itself and the reservation only, and each slice on the chunk only, so chunks may generate in any
 * order. Features and structure pieces (this chunk's or a neighbour's) never write into the template box afterwards
 * (WorldGenRegionForestHomeGuardMixin). This is the interim earthwork until C3.2's TerrainWork (HOUSE_PAD) is in Main.
 */
public final class ForestHomeChunkPass {
    /** Deepest fill under the plot (blocks); the planner keeps the house ground within a few blocks of the plan. */
    static final int MAX_FILL = 32;
    private static final ThreadLocal<Boolean> PLACING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Set<Block> GRASSY = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MOSS_BLOCK);

    private ForestHomeChunkPass() {}

    public static void apply(WorldGenLevel level, ChunkAccess chunk) {
        ForestHomeRuntime.Active a = ForestHomeRuntime.active();
        if (a == null || level.getLevel() != a.level()) return;
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ(), x1 = x0 + 15, z1 = z0 + 15;
        if (x1 < a.minX() || x0 > a.maxX() || z1 < a.minZ() || z0 > a.maxZ()) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = Math.max(x0, a.minX()); x <= Math.min(x1, a.maxX()); x++)
            for (int z = Math.max(z0, a.minZ()); z <= Math.min(z1, a.maxZ()); z++) grade(chunk, a, x, z, p);
        BoundingBox slice = new BoundingBox(x0, chunk.getMinBuildHeight(), z0, x1, chunk.getMaxBuildHeight() - 1, z1);
        if (!slice.intersects(a.box())) return;
        StructurePlaceSettings s = AflTemplatePlacer.settings(a.rotation(), Mirror.NONE, false)
                .setBoundingBox(slice).setKnownShape(true).setKeepLiquids(false);
        PLACING.set(Boolean.TRUE);
        try {
            a.template().placeInWorld(level, a.origin(), a.origin(), s, RandomSource.create(a.seed() ^ cp.toLong()), Block.UPDATE_CLIENTS);
        } finally {
            PLACING.set(Boolean.FALSE);
        }
    }

    /** One column of the plot or its ring. */
    static void grade(ChunkAccess chunk, ForestHomeRuntime.Active a, int x, int z, BlockPos.MutableBlockPos p) {
        double d = a.plot().distance(x + 0.5, z + 0.5);
        if (d > ForestHomeLayout.GRADE_MARGIN) return;
        int top = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
        int open = Math.max(top, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15));
        int y0 = a.reservation().y();
        if (d <= 0) {
            for (int y = Math.max(top + 1, y0 - MAX_FILL); y < y0; y++) set(chunk, p, x, y, z, (y < y0 - 3 ? Blocks.STONE : Blocks.DIRT).defaultBlockState());
            for (int y = y0 + ForestHomeLayout.SIZE_Y; y <= open; y++) set(chunk, p, x, y, z, Blocks.AIR.defaultBlockState());
            return;
        }
        if (!chunk.getFluidState(p.set(x, top + 1, z)).isEmpty()) return;
        double t = d / ForestHomeLayout.GRADE_MARGIN;
        t = t * t * (3 - 2 * t);
        int grass = a.groundY() - 1;
        int target = (int) Math.round(grass + (top - grass) * t);
        if (target == top) return;
        BlockState cap = chunk.getBlockState(p.set(x, top, z));
        BlockState under = chunk.getBlockState(p.set(x, top - 1, z));
        if (under.isAir() || !under.getFluidState().isEmpty() || GRASSY.contains(under.getBlock())) under = Blocks.DIRT.defaultBlockState();
        if (target > top) {
            for (int y = Math.max(top, target - MAX_FILL); y < target; y++) set(chunk, p, x, y, z, under);
        } else {
            for (int y = target + 1; y <= open; y++) set(chunk, p, x, y, z, Blocks.AIR.defaultBlockState());
        }
        set(chunk, p, x, target, z, cap);
    }

    private static void set(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState s) {
        chunk.setBlockState(p.set(x, y, z), s, false);
    }

    /** WorldGenRegion#setBlock guard: no generation write but the template's own lands in the template box. */
    public static boolean guarded(WorldGenLevel level, BlockPos pos) {
        ForestHomeRuntime.Active a = ForestHomeRuntime.active();
        return a != null && !PLACING.get() && a.box().isInside(pos) && level.getLevel() == a.level();
    }
}
