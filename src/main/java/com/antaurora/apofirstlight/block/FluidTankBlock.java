package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

public final class FluidTankBlock extends BaseEntityBlock {
    public static final BooleanProperty TOP_CONNECTED = BooleanProperty.create("top_connected");
    public static final BooleanProperty BOTTOM_CONNECTED = BooleanProperty.create("bottom_connected");

    public FluidTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(TOP_CONNECTED, false)
                .setValue(BOTTOM_CONNECTED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos position = context.getClickedPos();
        Level level = context.getLevel();
        return defaultBlockState()
                .setValue(TOP_CONNECTED, isFluidPipe(level.getBlockState(position.above())))
                .setValue(BOTTOM_CONNECTED, isFluidPipe(level.getBlockState(position.below())));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        if (direction == Direction.UP) {
            return state.setValue(TOP_CONNECTED, isFluidPipe(neighborState));
        }
        if (direction == Direction.DOWN) {
            return state.setValue(BOTTOM_CONNECTED, isFluidPipe(neighborState));
        }
        return state;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        Direction side = hit.getDirection();
        if (side != Direction.UP && side != Direction.DOWN) {
            return InteractionResult.PASS;
        }

        ItemStack heldItem = player.getItemInHand(hand);
        if (!FluidUtil.getFluidHandler(heldItem).isPresent()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        return FluidUtil.interactWithFluidHandler(player, hand, level, position, side)
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP_CONNECTED, BOTTOM_CONNECTED);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new FluidTankBlockEntity(position, state);
    }

    private static boolean isFluidPipe(BlockState state) {
        return state.getBlock() instanceof FluidPipeBlock;
    }
}
