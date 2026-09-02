package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FluidPipeBlock extends PipeBlock {
    public static final BooleanProperty SHOW_CORE = BooleanProperty.create("show_core");

    private static final float HALF_WIDTH = 2.0F / 16.0F;
    private static final VoxelShape STRAIGHT_X_SHAPE = Block.box(0, 6, 6, 16, 10, 10);
    private static final VoxelShape STRAIGHT_Y_SHAPE = Block.box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape STRAIGHT_Z_SHAPE = Block.box(6, 6, 0, 10, 10, 16);

    public FluidPipeBlock(Properties properties) {
        super(HALF_WIDTH, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(SHOW_CORE, true));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        BlockPos pos = context.getClickedPos();
        for (Direction direction : Direction.values()) {
            BlockState neighborState = context.getLevel().getBlockState(pos.relative(direction));
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), canConnectTo(neighborState));
        }
        return state.setValue(SHOW_CORE, shouldShowCore(context.getLevel(), pos, state));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState updatedState = state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                canConnectTo(neighborState));
        return updatedState.setValue(SHOW_CORE, shouldShowCore(level, pos, updatedState));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(SHOW_CORE)) {
            if (state.getValue(NORTH) && state.getValue(SOUTH)) {
                return STRAIGHT_Z_SHAPE;
            }
            if (state.getValue(EAST) && state.getValue(WEST)) {
                return STRAIGHT_X_SHAPE;
            }
            if (state.getValue(UP) && state.getValue(DOWN)) {
                return STRAIGHT_Y_SHAPE;
            }
        }
        return super.getShape(state, level, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, SHOW_CORE);
    }

    private static boolean canConnectTo(BlockState neighborState) {
        return neighborState.is(AflBlocks.FLUID_PIPE.get());
    }

    private static boolean shouldShowCore(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction firstConnection = null;
        Direction secondConnection = null;
        int connectionCount = 0;

        for (Direction direction : Direction.values()) {
            if (!state.getValue(PROPERTY_BY_DIRECTION.get(direction))) {
                continue;
            }
            connectionCount++;
            if (firstConnection == null) {
                firstConnection = direction;
            } else if (secondConnection == null) {
                secondConnection = direction;
            }
        }

        if (connectionCount != 2 || secondConnection != firstConnection.getOpposite()) {
            return true;
        }

        return !level.getBlockState(pos.relative(firstConnection)).is(AflBlocks.FLUID_PIPE.get())
                || !level.getBlockState(pos.relative(secondConnection)).is(AflBlocks.FLUID_PIPE.get());
    }
}
