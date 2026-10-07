package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Facade Masonry Base V1 (docs/models/facade_masonry_base_v1.md): the ground-face block base course of a facade, a full
 * block. CAP: a light cast-stone band (2 px, 1 px proud) along the top of the outside face, shown wherever something other
 * than this base or storefront glazing sits on it (the glazing brings its own sill). FACING is the outside (placed facing
 * the player, as from outside the wall); SHAPE as the canopy fascia: an outer corner when the block behind is a base turned
 * to one of its sides, so the cap wraps the corner. Without a cap the facing and shape do not show.
 */
public class MasonryBaseBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty CAP = BooleanProperty.create("cap");
    public static final EnumProperty<FuelCanopyFasciaBlock.Shape> SHAPE = FuelCanopyFasciaBlock.SHAPE;

    public MasonryBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, FuelCanopyFasciaBlock.Shape.STRAIGHT).setValue(CAP, true));
    }

    private boolean capped(BlockState above) {
        return !above.is(this) && !(above.getBlock() instanceof StorefrontGlazingBlock);
    }

    private FuelCanopyFasciaBlock.Shape shapeOf(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState behind = level.getBlockState(pos.relative(facing.getOpposite()));
        if (behind.is(this)) {
            Direction other = behind.getValue(FACING);
            if (other == facing.getCounterClockWise()) return FuelCanopyFasciaBlock.Shape.OUTER_LEFT;
            if (other == facing.getClockWise()) return FuelCanopyFasciaBlock.Shape.OUTER_RIGHT;
        }
        return FuelCanopyFasciaBlock.Shape.STRAIGHT;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(SHAPE, shapeOf(context.getLevel(), pos, facing))
                .setValue(CAP, capped(context.getLevel().getBlockState(pos.above())));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) return state.setValue(CAP, capped(neighborState));
        if (direction == state.getValue(FACING).getOpposite()) return state.setValue(SHAPE, shapeOf(level, pos, state.getValue(FACING)));
        return state;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        FuelCanopyFasciaBlock.Shape shape = state.getValue(SHAPE);
        FuelCanopyFasciaBlock.Shape mirrored = mirror == Mirror.NONE || shape == FuelCanopyFasciaBlock.Shape.STRAIGHT ? shape
                : shape == FuelCanopyFasciaBlock.Shape.OUTER_LEFT ? FuelCanopyFasciaBlock.Shape.OUTER_RIGHT : FuelCanopyFasciaBlock.Shape.OUTER_LEFT;
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(SHAPE, mirrored);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, CAP);
    }
}
