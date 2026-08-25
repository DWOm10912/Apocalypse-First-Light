package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, plan-time description of one deterministic procedural farm plot. */
public final class RuralFarmPlot {
    private final int index;
    private final String ownerId;
    private final ShapeType shape;
    private final BoundingBox bounds;
    private final int baseY;
    private final CropType crop;
    private final IrrigationType irrigationType;
    private final List<Cell> cells;
    private final Set<Long> cellKeys;
    private final List<Fence> fences;
    private final List<Gate> gates;
    private final List<BlockPos> irrigationCells;
    private final List<BlockPos> pathCells;
    private final Map<Long, Integer> surfaceYs;
    private final boolean valid;
    private final String rejectionReason;

    public RuralFarmPlot(int index, String ownerId, ShapeType shape, BoundingBox bounds, int baseY,
                         CropType crop, IrrigationType irrigationType, List<Cell> cells,
                         List<Fence> fences, List<Gate> gates, List<BlockPos> irrigationCells,
                         List<BlockPos> pathCells, Map<Long, Integer> surfaceYs,
                         boolean valid, String rejectionReason) {
        this.index = index;
        this.ownerId = ownerId;
        this.shape = shape;
        this.bounds = bounds;
        this.baseY = baseY;
        this.crop = crop;
        this.irrigationType = irrigationType;
        this.cells = List.copyOf(cells);
        Set<Long> keys = new HashSet<>();
        for (Cell cell : cells) keys.add(cell.key());
        this.cellKeys = Set.copyOf(keys);
        this.fences = List.copyOf(fences);
        this.gates = List.copyOf(gates);
        this.irrigationCells = List.copyOf(irrigationCells);
        this.pathCells = List.copyOf(pathCells);
        this.surfaceYs = Map.copyOf(surfaceYs);
        this.valid = valid;
        this.rejectionReason = rejectionReason;
    }

    public int index() { return index; }
    public String ownerId() { return ownerId; }
    public ShapeType shape() { return shape; }
    public BoundingBox bounds() { return bounds; }
    public int baseY() { return baseY; }
    public CropType crop() { return crop; }
    public IrrigationType irrigationType() { return irrigationType; }
    public List<Cell> cells() { return cells; }
    public List<Fence> fences() { return fences; }
    public List<Gate> gates() { return gates; }
    public List<BlockPos> irrigationCells() { return irrigationCells; }
    public List<BlockPos> pathCells() { return pathCells; }
    public Map<Long, Integer> surfaceYs() { return surfaceYs; }
    public boolean valid() { return valid; }
    public String rejectionReason() { return rejectionReason; }
    public int cellCount() { return cells.size(); }
    public boolean contains(int x, int z) { return cellKeys.contains(BlockPos.asLong(x, 0, z)); }

    public enum ShapeType {
        RECTANGLE,
        L_SHAPE,
        CUT_CORNER,
        STEPPED,
        TWO_PATCH
    }

    public enum IrrigationType {
        CENTER_CHANNEL,
        OFFSET_CHANNEL,
        TWO_SHORT_CHANNELS,
        WATER_POINTS
    }

    public enum GrowthBand {
        EARLY,
        MID,
        LATE,
        MATURE
    }

    public enum CropType {
        WHEAT(Blocks.WHEAT),
        CARROTS(Blocks.CARROTS),
        POTATOES(Blocks.POTATOES),
        BEETROOT(Blocks.BEETROOTS);

        private final Block block;
        private final int maxAge;

        CropType(Block block) {
            this.block = block;
            if (!(block instanceof CropBlock cropBlock)) {
                throw new IllegalStateException("Rural crop is not a CropBlock: " + block);
            }
            this.maxAge = cropBlock.getMaxAge();
        }

        public Block block() { return block; }
        public int maxAge() { return maxAge; }
        public BlockState state(GrowthBand band) {
            CropBlock cropBlock = (CropBlock) block;
            int age = switch (band) {
                case EARLY -> 0;
                case MID -> Math.max(1, maxAge / 2);
                case LATE -> Math.max(1, maxAge - 1);
                case MATURE -> maxAge;
            };
            BlockState state = cropBlock.getStateForAge(Math.max(0, Math.min(maxAge, age)));
            boolean hasAgeProperty = state.getProperties().stream()
                    .anyMatch(property -> "age".equals(property.getName()));
            if (!hasAgeProperty) {
                throw new IllegalStateException("Rural crop state has no age property: " + block);
            }
            return state;
        }
    }

    public record Cell(int x, int z, long key) {
        public BlockPos at(int y) { return new BlockPos(x, y, z); }
    }

    public record Fence(BlockPos pos, Direction facing) {
    }

    public record Gate(BlockPos pos, BlockPos insideCell, Direction facing) {
    }
}
