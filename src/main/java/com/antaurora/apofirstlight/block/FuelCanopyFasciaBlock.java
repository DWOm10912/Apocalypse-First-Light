package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Fuel Canopy Kit V1 fascia: the canopy's edge block, a faded red panel on the FACING side with a drip edge below and a
 * coping above, the soffit behind it. SHAPE as stairs do: an outer corner when the block behind it is a fascia turned to
 * one of its sides (OUTER_LEFT: the facing's counter-clockwise side, whose face it also shows). Placed, it faces away from
 * the canopy when the canopy is on one side of it (else toward the player). Carries the wiring (FuelCanopyNetwork).
 */
public class FuelCanopyFasciaBlock extends HorizontalDirectionalBlock implements FuelCanopyNetwork.Part {
    public enum Shape implements StringRepresentable {
        STRAIGHT("straight"), OUTER_LEFT("outer_left"), OUTER_RIGHT("outer_right");

        private final String name;

        Shape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public FuelCanopyFasciaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.STRAIGHT));
    }

    private static boolean outward(BlockGetter level, BlockPos pos, Direction side) {
        return FuelCanopyNetwork.isPart(level.getBlockState(pos.relative(side.getOpposite())))
                && !FuelCanopyNetwork.isPart(level.getBlockState(pos.relative(side)));
    }

    private static Shape shapeOf(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState behind = level.getBlockState(pos.relative(facing.getOpposite()));
        if (behind.getBlock() instanceof FuelCanopyFasciaBlock) {
            Direction other = behind.getValue(FACING);
            if (other == facing.getCounterClockWise()) return Shape.OUTER_LEFT;
            if (other == facing.getClockWise()) return Shape.OUTER_RIGHT;
        }
        return Shape.STRAIGHT;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!outward(level, pos, facing)) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (outward(level, pos, side)) {
                    facing = side;
                    break;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(SHAPE, shapeOf(level, pos, facing));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite()) return state.setValue(SHAPE, shapeOf(level, pos, state.getValue(FACING)));
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean movedByPiston) {
        if (!state.is(replacement.getBlock())) FuelCanopyNetwork.removed(level, pos);
        super.onRemove(state, level, pos, replacement, movedByPiston);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Shape shape = state.getValue(SHAPE);
        Shape mirrored = mirror == Mirror.NONE || shape == Shape.STRAIGHT ? shape : shape == Shape.OUTER_LEFT ? Shape.OUTER_RIGHT : Shape.OUTER_LEFT;
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(SHAPE, mirrored);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }
}
