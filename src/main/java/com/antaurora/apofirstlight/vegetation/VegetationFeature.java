package com.antaurora.apofirstlight.vegetation;

import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * AFL vegetation P2a (docs/gameplay/vegetation_v1_design.md 14.5): the last VEGETAL_DECORATION feature of the AFL biomes
 * (VegetationGate's modifier). Per chunk it plants what VegetationPlan chooses on each of the chunk's own 256 columns, one
 * plant block at most, never outside the chunk:
 * <ol>
 * <li>The ground now: OCEAN_FLOOR, which the FEATURES stage keeps up to date, so the roads, driveways, rock cuts and
 * buildings C's chunk passes wrote first and the mesh trees are in it (WG heightmaps are not).</li>
 * <li>The column's biome from the level (as the biome modifier saw it), its plan's choice.</li>
 * <li>Into air the plant goes where it survives (its own soil rule: the dirt tag, graded turf; the wetland plants also
 * mud, sand, clay, gravel); into water a block deep over its bed (a source, no water above) only a wetland plant,
 * waterlogged. Anything else (a road, a floor, a trunk, snow, deeper water) leaves the column bare. Under an AFL
 * river's water (air here, filled live) the plan's own rule ({@code VegetationPlan.Area#riverAllows}).</li>
 * <li>No plant inside a structure piece's box.</li>
 * </ol>
 * Written through WorldGenLevel#setBlock: C's guards on WorldGenRegion#setBlock keep the plants off the bare turf beside
 * the pavement and the trodden cave trails (C, 2026-10-24).
 */
final class VegetationFeature extends Feature<NoneFeatureConfiguration> {
    static final LongAdder CHUNKS = new LongAdder(), PLACED = new LongAdder(), WET = new LongAdder(), REFUSED = new LongAdder(), NANOS = new LongAdder();

    VegetationFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** The block a plant grows as (its own, or its stand-in until P2b makes it). */
    static BlockState block(VegetationPlan.Plant p) {
        return switch (p.standIn) {
            case BLUEGRASS -> AflBlocks.KENTUCKY_BLUEGRASS.get().defaultBlockState();
            case CATTAIL -> AflBlocks.BROADLEAF_CATTAIL.get().defaultBlockState();
            case YARROW -> AflBlocks.COMMON_YARROW.get().defaultBlockState();
            case BIG_BLUESTEM -> AflBlocks.BIG_BLUESTEM.get().defaultBlockState();
            case LITTLE_BLUESTEM -> AflBlocks.LITTLE_BLUESTEM.get().defaultBlockState();
            case PENNSYLVANIA_SEDGE -> AflBlocks.PENNSYLVANIA_SEDGE.get().defaultBlockState();
            case BRACKEN_FERN -> AflBlocks.BRACKEN_FERN.get().defaultBlockState();
            case PLANTAIN -> AflBlocks.BROADLEAF_PLANTAIN.get().defaultBlockState();
            case DANDELION -> AflBlocks.DANDELION.get().defaultBlockState();
            case PURPLE_CONEFLOWER -> AflBlocks.PURPLE_CONEFLOWER.get().defaultBlockState();
            case BLACK_EYED_SUSAN -> AflBlocks.BLACK_EYED_SUSAN.get().defaultBlockState();
            case CANADA_GOLDENROD -> AflBlocks.CANADA_GOLDENROD.get().defaultBlockState();
            case COMMON_REED -> AflBlocks.COMMON_REED.get().defaultBlockState();
            case ALLEGHENY_BLACKBERRY -> AflBlocks.ALLEGHENY_BLACKBERRY.get().defaultBlockState();
            case SWITCHGRASS -> AflBlocks.SWITCHGRASS.get().defaultBlockState();
            case SMOOTH_CORDGRASS -> AflBlocks.SMOOTH_CORDGRASS.get().defaultBlockState();
            case CHRISTMAS_FERN -> AflBlocks.CHRISTMAS_FERN.get().defaultBlockState();
            case CINNAMON_FERN -> AflBlocks.CINNAMON_FERN.get().defaultBlockState();
            case COMMON_MULLEIN -> AflBlocks.COMMON_MULLEIN.get().defaultBlockState();
            case WILD_MINT -> AflBlocks.WILD_MINT.get().defaultBlockState();
            case COMMON_MILKWEED -> AflBlocks.COMMON_MILKWEED.get().defaultBlockState();
            case POISON_IVY -> AflBlocks.POISON_IVY.get().defaultBlockState();
            case LOWBUSH_BLUEBERRY -> AflBlocks.LOWBUSH_BLUEBERRY.get().defaultBlockState();
            case AMERICAN_ELDERBERRY -> AflBlocks.AMERICAN_ELDERBERRY.get().defaultBlockState();
            case BIG_SAGEBRUSH -> AflBlocks.BIG_SAGEBRUSH.get().defaultBlockState();
            case AMERICAN_BEACHGRASS -> AflBlocks.AMERICAN_BEACHGRASS.get().defaultBlockState();
        };
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        VegetationPlan plan = VegetationGate.plan(level.getLevel());
        if (plan == null) return false;
        long t0 = System.nanoTime();
        ChunkPos chunk = new ChunkPos(context.origin());
        VegetationPlan.Area area = plan.area(chunk.x, chunk.z);
        List<BoundingBox> boxes = structures(level, chunk);
        var pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int i = 0; i < 16; i++)
            for (int j = 0; j < 16; j++) {
                int x = chunk.getMinBlockX() + i, z = chunk.getMinBlockZ() + j;
                if (area.excluded(x, z)) continue;
                int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                pos.set(x, y, z);
                String biome = level.getBiome(pos).unwrapKey()
                        .filter(k -> k.location().getNamespace().equals("apocalypse_firstlight")).map(k -> k.location().getPath()).orElse(null);
                VegetationPlan.Plant p = area.choose(x, z, biome);
                if (p == null) continue;
                if (inStructure(boxes, x, y, z) || !area.riverAllows(p, x, y, z)) { REFUSED.increment(); continue; }
                BlockState here = level.getBlockState(pos), plant = block(p);
                boolean wet = false;
                if (here.isAir()) {
                    if (plant.hasProperty(BlockStateProperties.WATERLOGGED)) plant = plant.setValue(BlockStateProperties.WATERLOGGED, false);
                } else if (p.wet() && here.is(Blocks.WATER) && here.getFluidState().isSource() && !level.getFluidState(pos.above()).is(FluidTags.WATER)
                        && plant.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    plant = plant.setValue(BlockStateProperties.WATERLOGGED, true);
                    wet = true;
                } else { REFUSED.increment(); continue; }
                if (!plant.canSurvive(level, pos) || !level.setBlock(pos, plant, Block.UPDATE_CLIENTS)) { REFUSED.increment(); continue; }
                placed++;
                if (wet) WET.increment();
            }
        CHUNKS.increment();
        PLACED.add(placed);
        NANOS.add(System.nanoTime() - t0);
        return placed > 0;
    }

    /** The boxes of the structures this chunk refers to (their starts within the region). */
    static List<BoundingBox> structures(WorldGenLevel level, ChunkPos chunk) {
        List<BoundingBox> out = new ArrayList<>();
        ChunkAccess c = level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_REFERENCES, false);
        if (c == null) return out;
        for (var e : c.getAllReferences().entrySet())
            for (long start : e.getValue()) {
                ChunkAccess sc = level.getChunk(ChunkPos.getX(start), ChunkPos.getZ(start), ChunkStatus.STRUCTURE_STARTS, false);
                if (sc == null) continue;
                StructureStart s = sc.getStartForStructure(e.getKey());
                if (s == null || !s.isValid()) continue;
                for (var piece : s.getPieces()) out.add(piece.getBoundingBox());
            }
        return out;
    }

    static boolean inStructure(List<BoundingBox> boxes, int x, int y, int z) {
        for (BoundingBox b : boxes)
            if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ() && y >= b.minY() - 1 && y <= b.maxY()) return true;
        return false;
    }

    static String status() {
        long n = CHUNKS.sum();
        return String.format(java.util.Locale.ROOT, "vegetation feature: %d chunks, %d plants (%d in water), %d refused, %.2f ms a chunk",
                n, PLACED.sum(), WET.sum(), REFUSED.sum(), n == 0 ? 0 : NANOS.sum() / 1e6 / n);
    }
}
