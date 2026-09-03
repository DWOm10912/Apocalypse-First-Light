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

    private static final float HALF_WIDTH = 3.0F / 16.0F;
    private static final VoxelShape STRAIGHT_X_SHAPE = Block.box(0, 5, 5, 16, 11, 11);
    private static final VoxelShape STRAIGHT_Y_SHAPE = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape STRAIGHT_Z_SHAPE = Block.box(5, 5, 0, 11, 11, 16);

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
        return withStructuralConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return withStructuralConnections(level, pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockState structuralState = withStructuralConnections(level, pos, state);
        if (!structuralState.getValue(SHOW_CORE)) {
            if (structuralState.getValue(NORTH) && structuralState.getValue(SOUTH)) {
                return STRAIGHT_Z_SHAPE;
            }
            if (structuralState.getValue(EAST) && structuralState.getValue(WEST)) {
                return STRAIGHT_X_SHAPE;
            }
            if (structuralState.getValue(UP) && structuralState.getValue(DOWN)) {
                return STRAIGHT_Y_SHAPE;
            }
        }
        return super.getShape(structuralState, level, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, SHOW_CORE);
    }

    public static boolean isConnected(BlockState state, Direction direction) {
        return state.getBlock() instanceof FluidPipeBlock
                && state.getValue(PROPERTY_BY_DIRECTION.get(direction));
    }

    public static boolean canPipeEdgeConnect(BlockGetter level, BlockPos pipePosition,
                                             BlockPos neighborPosition, Direction directionToNeighbor) {
        if (!neighborPosition.equals(pipePosition.relative(directionToNeighbor))) {
            return false;
        }
        BlockState neighborState = level.getBlockState(neighborPosition);
        if (neighborState.is(AflBlocks.FLUID_PIPE.get())) {
            Direction.Axis firstRunAxis = findUniqueRunAxis(level, pipePosition, neighborPosition);
            Direction.Axis secondRunAxis = findUniqueRunAxis(level, neighborPosition, pipePosition);
            return firstRunAxis == null
                    || secondRunAxis == null
                    || firstRunAxis != secondRunAxis
                    || firstRunAxis == directionToNeighbor.getAxis();
        }
        return canConnectToTank(neighborState, directionToNeighbor);
    }

    public static BlockState withStructuralConnections(BlockGetter level, BlockPos position, BlockState state) {
        BlockState updatedState = state;
        for (Direction direction : Direction.values()) {
            updatedState = updatedState.setValue(PROPERTY_BY_DIRECTION.get(direction),
                    canPipeEdgeConnect(level, position, position.relative(direction), direction));
        }
        return updatedState.setValue(SHOW_CORE, shouldShowCore(level, position, updatedState));
    }

    private static Direction.Axis findUniqueRunAxis(BlockGetter level, BlockPos pipePosition,
                                                    BlockPos ignoredNeighborPosition) {
        Direction.Axis runAxis = null;
        int axisCount = 0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (!hasContinuationAlongAxis(level, pipePosition, ignoredNeighborPosition, axis)) {
                continue;
            }
            runAxis = axis;
            axisCount++;
            if (axisCount > 1) {
                return null;
            }
        }
        return runAxis;
    }

    private static boolean hasContinuationAlongAxis(BlockGetter level, BlockPos pipePosition,
                                                    BlockPos ignoredNeighborPosition,
                                                    Direction.Axis axis) {
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != axis) {
                continue;
            }
            BlockPos neighborPosition = pipePosition.relative(direction);
            if (neighborPosition.equals(ignoredNeighborPosition)) {
                continue;
            }
            BlockState neighborState = level.getBlockState(neighborPosition);
            if (neighborState.is(AflBlocks.FLUID_PIPE.get())
                    || canConnectToTank(neighborState, direction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canConnectToTank(BlockState neighborState, Direction directionToNeighbor) {
        if (!neighborState.is(AflBlocks.FLUID_TANK.get()) || !directionToNeighbor.getAxis().isVertical()) {
            return false;
        }
        return directionToNeighbor == Direction.DOWN
                ? !neighborState.getValue(FluidTankBlock.HAS_TANK_ABOVE)
                : !neighborState.getValue(FluidTankBlock.HAS_TANK_BELOW);
    }

    private static boolean shouldShowCore(BlockGetter level, BlockPos pos, BlockState state) {
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

        return !isValidStraightThroughEndpoint(level, pos, firstConnection)
                || !isValidStraightThroughEndpoint(level, pos, secondConnection);
    }

    private static boolean isValidStraightThroughEndpoint(BlockGetter level, BlockPos position,
                                                          Direction directionToNeighbor) {
        return canPipeEdgeConnect(level, position, position.relative(directionToNeighbor), directionToNeighbor);
    }
}
