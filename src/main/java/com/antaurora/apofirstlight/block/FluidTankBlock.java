package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.fluid.FluidTankStacks;
import com.antaurora.apofirstlight.fluid.FluidTankStoredFluid;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FluidTankBlock extends BaseEntityBlock {
    public static final BooleanProperty HAS_TANK_ABOVE = BooleanProperty.create("has_tank_above");
    public static final BooleanProperty HAS_TANK_BELOW = BooleanProperty.create("has_tank_below");
    public static final BooleanProperty TOP_CONNECTED = BooleanProperty.create("top_connected");
    public static final BooleanProperty BOTTOM_CONNECTED = BooleanProperty.create("bottom_connected");

    public FluidTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(HAS_TANK_ABOVE, false)
                .setValue(HAS_TANK_BELOW, false)
                .setValue(TOP_CONNECTED, false)
                .setValue(BOTTOM_CONNECTED, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos position = context.getClickedPos();
        Level level = context.getLevel();
        if (!FluidTankStacks.canPlaceTank(level, position,
                FluidTankStoredFluid.read(context.getItemInHand()))) {
            return null;
        }
        return defaultBlockState()
                .setValue(TOP_CONNECTED, isFluidPipe(level.getBlockState(position.above())))
                .setValue(BOTTOM_CONNECTED, isFluidPipe(level.getBlockState(position.below())));
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(position) instanceof FluidTankBlockEntity tank) {
            tank.preparePlayerBreakDrop(!player.isCreative());
        }
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof FluidTankBlockEntity tank) {
            FluidStack preservedFluid = tank.getPreparedDropFluid();
            if (!preservedFluid.isEmpty()) {
                for (ItemStack drop : drops) {
                    if (drop.is(asItem())) {
                        FluidTankStoredFluid.write(drop, preservedFluid);
                    }
                }
            }
        }
        return drops;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            FluidTankStacks.rebuildColumn(serverLevel, position);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        if (direction.getAxis().isVertical()) {
            level.scheduleTick(position, this, 1);
        }
        if (direction == Direction.UP) {
            return state.setValue(TOP_CONNECTED,
                    !state.getValue(HAS_TANK_ABOVE) && isFluidPipe(neighborState));
        }
        if (direction == Direction.DOWN) {
            return state.setValue(BOTTOM_CONNECTED,
                    !state.getValue(HAS_TANK_BELOW) && isFluidPipe(neighborState));
        }
        return state;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        FluidTankStacks.rebuildColumn(level, position);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState,
                         boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(position) instanceof FluidTankBlockEntity tank) {
            FluidTankStacks.handleTankRemoved(serverLevel, position, tank);
        }
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        Direction side = hit.getDirection();
        if ((side == Direction.UP && state.getValue(HAS_TANK_ABOVE))
                || (side == Direction.DOWN && state.getValue(HAS_TANK_BELOW))
                || side.getAxis().isHorizontal()) {
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
        builder.add(HAS_TANK_ABOVE, HAS_TANK_BELOW, TOP_CONNECTED, BOTTOM_CONNECTED);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new FluidTankBlockEntity(position, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != AflBlockEntities.FLUID_TANK.get()) {
            return null;
        }
        return (tickerLevel, tickerPosition, tickerState, blockEntity) ->
                FluidTankBlockEntity.serverTick(tickerLevel, tickerPosition, tickerState,
                        (FluidTankBlockEntity) blockEntity);
    }

    private static boolean isFluidPipe(BlockState state) {
        return state.getBlock() instanceof FluidPipeBlock;
    }
}
