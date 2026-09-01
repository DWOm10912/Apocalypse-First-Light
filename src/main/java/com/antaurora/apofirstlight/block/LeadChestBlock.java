package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.LeadChestBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LeadChestBlock extends ChestBlock {
    public LeadChestBlock(Properties properties) {
        super(properties, () -> AflBlockEntities.LEAD_CHEST.get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new LeadChestBlockEntity(position, state);
    }
}
