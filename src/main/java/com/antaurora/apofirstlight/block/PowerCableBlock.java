package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public final class PowerCableBlock extends PipeBlock {
    private static final float HALF_WIDTH = 2.0F / 16.0F;

    public PowerCableBlock(Properties properties) {
        super(HALF_WIDTH, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        BlockPos pos = context.getClickedPos();
        for (Direction direction : Direction.values()) {
            BlockState neighborState = context.getLevel().getBlockState(pos.relative(direction));
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(direction, neighborState));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(direction, neighborState));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    public static boolean isConnected(BlockState cableState, Direction direction) {
        return cableState.is(AflBlocks.POWER_CABLE.get())
                && cableState.getValue(PROPERTY_BY_DIRECTION.get(direction));
    }

    public static Direction utilityPortFace(BlockState machineState) {
        return machineState.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
    }

    public static boolean isUtilityPortFace(BlockState machineState, Direction face) {
        return (machineState.is(AflBlocks.THERMAL_GENERATOR.get())
                || machineState.is(AflBlocks.ENERGY_CELL.get())
                || machineState.is(AflBlocks.CRUSHER.get())
                || machineState.is(AflBlocks.INDUSTRIAL_FURNACE.get())
                || machineState.is(AflBlocks.COMPRESSOR.get()))
                && face == utilityPortFace(machineState);
    }

    private static boolean connectsTo(Direction directionToNeighbor, BlockState neighborState) {
        if (neighborState.is(AflBlocks.POWER_CABLE.get())) {
            return true;
        }
        return isUtilityPortFace(neighborState, directionToNeighbor.getOpposite());
    }
}
