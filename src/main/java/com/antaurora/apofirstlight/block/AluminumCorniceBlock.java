package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Aluminum Cornice V1 (docs/models/aluminum_cornice_v1.md): the top wall cell of a parapet, a painted aluminium fascia
 * 1 px proud of the wall on the FACING (outside) side, a coping over the wall top and the roof membrane turned up on the
 * inside. SHAPE as the canopy fascia: an outer corner when the block behind is a cornice turned to one of its sides, so the
 * fascia and coping wrap the corner. BAND: a red polycarbonate band along the cornice's foot (unlit; there is no power
 * hook-up yet), toggled with sneak + use and an empty hand.
 */
public class AluminumCorniceBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<FuelCanopyFasciaBlock.Shape> SHAPE = FuelCanopyFasciaBlock.SHAPE;
    public static final BooleanProperty BAND = BooleanProperty.create("band");

    public AluminumCorniceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, FuelCanopyFasciaBlock.Shape.STRAIGHT).setValue(BAND, false));
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
        Direction facing = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(SHAPE, shapeOf(context.getLevel(), context.getClickedPos(), facing));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite()) return state.setValue(SHAPE, shapeOf(level, pos, state.getValue(FACING)));
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.cycle(BAND), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
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
        builder.add(FACING, SHAPE, BAND);
    }
}
