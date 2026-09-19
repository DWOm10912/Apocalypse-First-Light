package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashSet;
import java.util.Set;

/** Replays the saved agricultural plan; all reads/writes stay inside the current chunk. */
public final class RuralFarmlandPainter {
    private RuralFarmlandPainter() { }

    public record Result(int attempted, int written, int cropBlocks, int irrigationBlocks,
                         RuralTerrainAdapter.PreparationResult preparation, int skippedColumns) { }

    public static Result paint(WorldGenLevel level, RuralFarmPlot plot, BoundingBox chunk) {
        RuralAgriculturalLot lot = java.util.Objects.requireNonNull(plot.agriculturalLot());
        Stats stats = new Stats();
        Set<Long> fences = keys(plot.fences().stream().map(RuralFarmPlot.Fence::pos).toList());
        Set<Long> water = keys(plot.irrigationCells()), paths = keys(plot.pathCells());
        BoundingBox b = lot.lotBounds(), berm = lot.bermBounds(), crop = lot.cropBounds();
        for (int x = Math.max(b.minX(), chunk.minX()); x <= Math.min(b.maxX(), chunk.maxX()); x++)
            for (int z = Math.max(b.minZ(), chunk.minZ()); z <= Math.min(b.maxZ(), chunk.maxZ()); z++) {
                BlockPos ground = new BlockPos(x, plot.baseY(), z);
                if (!prepare(level, ground, chunk, stats)) continue;
                long key = key(x, z);
                boolean gate = x == lot.gate().getX() && z == lot.gate().getZ();
                BlockState soil;
                BlockState above = Blocks.AIR.defaultBlockState();
                boolean cropBlock = false, wet = false;
                if (RuralAgriculturalLot.edge(berm, x, z)) {
                    // Solid earth beneath every fence, gate and corner post, never farmland.
                    soil = earth(lot, x, z);
                    if (lot.corner(x, z)) above = Blocks.STRIPPED_OAK_LOG.defaultBlockState();
                    else if (gate) above = Blocks.OAK_FENCE_GATE.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, lot.facing());
                    else if (fences.contains(key)) above = fence(lot, fences, x, z);
                } else if (paths.contains(key)) {
                    soil = Blocks.DIRT_PATH.defaultBlockState();
                } else if (RuralAccessPlanner.contains(crop, x, z)) {
                    if (lot.channel(x, z)) {
                        wet = water.contains(key);
                        soil = wet ? Blocks.WATER.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                    } else {
                        boolean emptyRow = lot.roll(lot.row(x, z), 0, 21) < lot.variant().emptyRowPercent;
                        cropBlock = !emptyRow && lot.roll(x, z, 22) < lot.variant().cropPercent;
                        if (cropBlock) {
                            soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
                            int band = lot.roll(x, z, 23) / 25;
                            above = plot.crop().state(RuralFarmPlot.GrowthBand.values()[band]);
                        } else if (lot.roll(x, z, 24) < (lot.variant() == RuralFarmlandVariant.OVERGROWN_FIELD ? 10 : 35)) {
                            soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 3);
                        } else {
                            soil = degraded(lot, x, z);
                            if (soil.is(Blocks.GRASS_BLOCK) && lot.roll(x, z, 25) < 45)
                                above = Blocks.GRASS.defaultBlockState();
                        }
                    }
                } else {
                    soil = degraded(lot, x, z);
                    if (soil.is(Blocks.GRASS_BLOCK) && lot.roll(x, z, 26) < 30)
                        above = Blocks.GRASS.defaultBlockState();
                }
                if (write(level, chunk, ground, soil, stats) && wet) stats.irrigation++;
                if (write(level, chunk, ground.above(), above, stats) && cropBlock) stats.crops++;
                if (lot.corner(x, z)) write(level, chunk, ground.above(2), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), stats);
            }
        for (BlockPos ground : lot.access()) {
            if (RuralAccessPlanner.contains(b, ground.getX(), ground.getZ())
                    || RuralAccessPlanner.contains(lot.sourceRoad().bounds(), ground.getX(), ground.getZ())
                    || !chunk.isInside(ground)) continue;
            if (prepare(level, ground, chunk, stats))
                write(level, chunk, ground, earth(lot, ground.getX(), ground.getZ()), stats);
        }
        return new Result(stats.attempted, stats.written, stats.crops, stats.irrigation,
                new RuralTerrainAdapter.PreparationResult(stats.prepared, 0, 0, stats.vegetation,
                        stats.cut, stats.fill, stats.maxCut, stats.maxFill, 0, 0), stats.skipped);
    }

    /** Guard unsampled terrain pockets; never extend cut/fill beyond the existing two-block farm limit. */
    private static boolean prepare(WorldGenLevel level, BlockPos ground, BoundingBox chunk, Stats stats) {
        if (!chunk.isInside(ground)) return false;
        var sample = RuralTerrainSampler.sample(level, ground.getX(), ground.getZ());
        int surface = sample.surfaceY() - 1;
        int delta = ground.getY() - surface;
        if (!sample.valid() || sample.water() || Math.abs(delta) > RuralFarmPlanner.MAX_CELL_ADJUST) {
            stats.skipped++;
            return false;
        }
        // Clearance is reserved by the agricultural footprint, without an out-of-lot clearing margin.
        for (int y = Math.min(surface + 1, ground.getY()); y <= Math.max(surface, ground.getY()) + 4; y++) {
            BlockPos p = new BlockPos(ground.getX(), y, ground.getZ());
            if (!chunk.isInside(p)) continue;
            BlockState previous = level.getBlockState(p);
            if (previous.hasBlockEntity()) { stats.skipped++; return false; }
        }
        for (int y = surface + 1; y < ground.getY(); y++)
            if (write(level, chunk, new BlockPos(ground.getX(), y, ground.getZ()), Blocks.DIRT.defaultBlockState(), stats))
                stats.prepared++;
        for (int y = ground.getY() + 1; y <= Math.max(surface, ground.getY()) + 4; y++) {
            BlockPos p = new BlockPos(ground.getX(), y, ground.getZ());
            if (!chunk.isInside(p)) continue;
            BlockState previous = level.getBlockState(p);
            if (y <= surface || previous.canBeReplaced() || previous.is(BlockTags.LOGS)
                    || previous.is(BlockTags.LEAVES)) {
                if (write(level, chunk, p, Blocks.AIR.defaultBlockState(), stats)) {
                    stats.prepared++;
                    if (y > surface) stats.vegetation++;
                }
            }
        }
        stats.cut += Math.max(0, -delta);
        stats.fill += Math.max(0, delta);
        stats.maxCut = Math.max(stats.maxCut, -delta);
        stats.maxFill = Math.max(stats.maxFill, delta);
        return true;
    }

    private static BlockState earth(RuralAgriculturalLot lot, int x, int z) {
        int roll = lot.roll(x, z, 31);
        return (roll < 65 ? Blocks.COARSE_DIRT : roll < 93 ? Blocks.DIRT : Blocks.GRAVEL).defaultBlockState();
    }

    private static BlockState degraded(RuralAgriculturalLot lot, int x, int z) {
        int grass = lot.variant() == RuralFarmlandVariant.OVERGROWN_FIELD ? 70
                : lot.variant() == RuralFarmlandVariant.ABANDONED_FIELD ? 20 : 5;
        return lot.roll(x, z, 32) < grass ? Blocks.GRASS_BLOCK.defaultBlockState() : earth(lot, x, z);
    }

    private static BlockState fence(RuralAgriculturalLot lot, Set<Long> fences, int x, int z) {
        BlockState result = Blocks.OAK_FENCE.defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            boolean connected = fences.contains(key(nx, nz)) || lot.corner(nx, nz)
                    || nx == lot.gate().getX() && nz == lot.gate().getZ();
            result = result.setValue(switch (d) {
                case NORTH -> BlockStateProperties.NORTH; case SOUTH -> BlockStateProperties.SOUTH;
                case WEST -> BlockStateProperties.WEST; case EAST -> BlockStateProperties.EAST;
                default -> throw new IllegalArgumentException("Horizontal fence direction required");
            }, connected);
        }
        return result;
    }

    private static long key(int x, int z) { return BlockPos.asLong(x, 0, z); }
    private static Set<Long> keys(java.util.List<BlockPos> positions) {
        Set<Long> result = new HashSet<>();
        for (BlockPos p : positions) result.add(key(p.getX(), p.getZ()));
        return result;
    }

    private static boolean write(WorldGenLevel level, BoundingBox chunk, BlockPos p, BlockState state, Stats stats) {
        if (!chunk.isInside(p)) return false;
        stats.attempted++;
        if (level.getBlockState(p).equals(state)) return false;
        if (!level.setBlock(p, state, 2)) return false;
        stats.written++;
        return true;
    }

    private static final class Stats {
        int attempted, written, crops, irrigation, prepared, vegetation, cut, fill, maxCut, maxFill, skipped;
    }
}
