package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.LeadChestBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import javax.annotation.Nullable;

public class LeadChestBlock extends ChestBlock {
    public LeadChestBlock(Properties properties) {
        super(properties, () -> AflBlockEntities.LEAD_CHEST.get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new LeadChestBlockEntity(position, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(TYPE, ChestType.SINGLE);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        return super.updateShape(state.setValue(TYPE, ChestType.SINGLE), direction, neighborState,
                level, position, neighborPosition).setValue(TYPE, ChestType.SINGLE);
    }
}
