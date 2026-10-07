package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Metal Wall Panel V1 (docs/models/metal_wall_panel_v1.md): charcoal linear metal cladding, a full block with the same
 * horizontal planks on every side, so it has no facing. CAP: with no panel above, the top turns into the aluminium coping,
 * with a lip down the outside of every side that has no panel next to it (NORTH / EAST / SOUTH / WEST: a panel on that side),
 * and a corner square where two lips meet.
 */
public class MetalWallPanelBlock extends Block {
    public static final BooleanProperty CAP = BooleanProperty.create("cap");
    public static final BooleanProperty NORTH = PipeBlock.NORTH, EAST = PipeBlock.EAST, SOUTH = PipeBlock.SOUTH, WEST = PipeBlock.WEST;

    public MetalWallPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CAP, true)
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    private BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        state = state.setValue(CAP, !level.getBlockState(pos.above()).is(this));
        for (Direction d : Direction.Plane.HORIZONTAL)
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), level.getBlockState(pos.relative(d)).is(this));
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) return state.setValue(CAP, !neighborState.is(this));
        if (direction.getAxis().isHorizontal()) return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), neighborState.is(this));
        return state;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH)).setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> state.setValue(NORTH, state.getValue(EAST)).setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST)).setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 -> state.setValue(NORTH, state.getValue(WEST)).setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST)).setValue(WEST, state.getValue(SOUTH));
            default -> state;
        };
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CAP, NORTH, EAST, SOUTH, WEST);
    }
}
