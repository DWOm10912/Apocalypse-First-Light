package com.antaurora.apofirstlight.dev.containersearch;

import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** DEVELOPMENT ONLY. Opens through vanilla {@code openMenu}; removal uses the search-safe drop rule. */
final class DevSearchCrateBlock extends Block implements EntityBlock {
    DevSearchCrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DevSearchCrateBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof DevSearchCrateBlockEntity crate) {
            player.openMenu(crate);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DevSearchCrateBlockEntity crate) {
            AflContainerSearch.dropContentsOnBreak(level, pos, crate);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
