package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Pavement Markings V1: a pavement arrow, one cell per block. Straight: 1 x 3 cells, parts 0 (head) .. 2 (tail). Turn:
 * 2 x 3, part = row * 2 + col of the left-turn drawing (row 0 at the head end); the right turn is its mirror image; part 4
 * (beside the shaft's end, under the head) is empty and never placed. The
 * tail cell (the shaft's end) is where the item puts it; FACING is the way it points. Placed whole by
 * {@link com.antaurora.apofirstlight.item.PavementArrowItem}; breaking any cell removes the arrow.
 */
public final class PavementArrowBlock extends PavementMarkingBlock {
    public enum Kind implements StringRepresentable {
        STRAIGHT("straight"), LEFT("left"), RIGHT("right");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);

    /** A part and its cell: forward (the way it points) and right of the tail cell. */
    public record Cell(int part, int forward, int right) {}

    public PavementArrowBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(KIND, Kind.STRAIGHT).setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KIND, PART);
    }

    public static Cell[] cells(Kind kind) {
        if (kind == Kind.STRAIGHT) return new Cell[]{new Cell(0, 2, 0), new Cell(1, 1, 0), new Cell(2, 0, 0)};
        java.util.List<Cell> out = new java.util.ArrayList<>();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 2; col++) {   // the left turn's shaft is col 1, its head col 0
            if (row * 2 + col == 4) continue;                                    // no paint there
            out.add(new Cell(row * 2 + col, 2 - row, kind == Kind.LEFT ? col - 1 : 1 - col));
        }
        return out.toArray(Cell[]::new);
    }

    public static BlockPos at(BlockPos tail, Direction facing, Cell cell) {
        return tail.relative(facing, cell.forward()).relative(facing.getClockWise(), cell.right());
    }

    /** The state of one cell of an arrow. */
    public BlockState part(Kind kind, Direction facing, int part) {
        return defaultBlockState().setValue(FACING, facing).setValue(KIND, kind).setValue(PART, part);
    }

    /** Placed only as a whole arrow, by its item. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return null;
    }

    /** Breaking one cell takes the arrow's other cells with it (no drops: paint is not recovered). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Kind kind = state.getValue(KIND);
        Direction facing = state.getValue(FACING);
        Cell self = null;
        for (Cell c : cells(kind)) if (c.part() == state.getValue(PART)) self = c;
        if (self != null) {
            BlockPos tail = pos.relative(facing, -self.forward()).relative(facing.getClockWise(), -self.right());
            for (Cell c : cells(kind)) {
                BlockPos other = at(tail, facing, c);
                if (other.equals(pos)) continue;
                BlockState there = level.getBlockState(other);
                if (there.is(this) && there.getValue(KIND) == kind && there.getValue(FACING) == facing && there.getValue(PART) == c.part())
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** A mirrored template swaps the turn (the cells mirror with it; the part numbers stay). */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = super.mirror(state, mirror);
        if (mirror == Mirror.NONE) return turned;
        Kind kind = state.getValue(KIND);
        return kind == Kind.STRAIGHT ? turned : turned.setValue(KIND, kind == Kind.LEFT ? Kind.RIGHT : Kind.LEFT);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(switch (state.getValue(KIND)) {
            case STRAIGHT -> AflItems.PAVEMENT_ARROW_STRAIGHT.get();
            case LEFT -> AflItems.PAVEMENT_ARROW_LEFT.get();
            case RIGHT -> AflItems.PAVEMENT_ARROW_RIGHT.get();
        });
    }
}
