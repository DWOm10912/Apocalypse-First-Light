package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.ChemicalReactorBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ChemicalReactorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public ChemicalReactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public static Direction inputFluidFace(BlockState state) {
        return switch (state.getValue(FACING)) {
            case NORTH -> Direction.EAST;
            case SOUTH -> Direction.WEST;
            case EAST -> Direction.SOUTH;
            case WEST -> Direction.NORTH;
            default -> throw new IllegalStateException("Chemical Reactor FACING must be horizontal");
        };
    }

    public static Direction wasteFluidFace(BlockState state) {
        return inputFluidFace(state).getOpposite();
    }

    public static boolean isInputFluidFace(BlockState state, Direction face) {
        return state.is(com.antaurora.apofirstlight.registry.AflBlocks.CHEMICAL_REACTOR.get())
                && face == inputFluidFace(state);
    }

    public static boolean isWasteFluidFace(BlockState state, Direction face) {
        return state.is(com.antaurora.apofirstlight.registry.AflBlocks.CHEMICAL_REACTOR.get())
                && face == wasteFluidFace(state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(position) instanceof ChemicalReactorBlockEntity reactor) {
            NetworkHooks.openScreen(serverPlayer, reactor, position);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof ChemicalReactorBlockEntity reactor) {
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    reactor.writeDropData(drop);
                }
            }
        }
        return drops;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState,
                         boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(position) instanceof ChemicalReactorBlockEntity reactor) {
            Containers.dropContents(level, position, reactor);
        }
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new ChemicalReactorBlockEntity(position, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != AflBlockEntities.CHEMICAL_REACTOR.get()) {
            return null;
        }
        return (tickerLevel, tickerPosition, tickerState, blockEntity) ->
                ChemicalReactorBlockEntity.serverTick(tickerLevel, tickerPosition, tickerState,
                        (ChemicalReactorBlockEntity) blockEntity);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
