package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LeadChestBlockEntity extends ChestBlockEntity {
    public LeadChestBlockEntity(BlockPos position, BlockState state) {
        super(AflBlockEntities.LEAD_CHEST.get(), position, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.apocalypse_firstlight.lead_chest");
    }
}
