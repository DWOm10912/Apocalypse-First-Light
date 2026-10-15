package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wood Furniture V2 (docs/models/wood_furniture_v2.md, 2026-10-15): the wooden storage chest's contents, a vanilla chest's
 * (27 slots, joined into 54 with a second one, hoppers, comparators, the chest screen and sounds). Its own type and ID; no
 * lid renderer: the lid is the block's {@code open} look ({@link WoodFurnitureV2.StorageChest}), set while anyone has it
 * open (both halves of a double chest open together, as their openers are counted together).
 */
public class StorageChestBlockEntity extends ChestBlockEntity {
    public StorageChestBlockEntity(BlockPos pos, BlockState state) { super(WoodFurnitureV2.CHEST, pos, state); }

    @Override
    protected void signalOpenCount(Level level, BlockPos pos, BlockState state, int eventId, int openCount) {
        super.signalOpenCount(level, pos, state, eventId, openCount);
        boolean open = openCount > 0;
        if (state.hasProperty(WoodFurnitureV2.StorageChest.OPEN) && state.getValue(WoodFurnitureV2.StorageChest.OPEN) != open)
            level.setBlock(pos, state.setValue(WoodFurnitureV2.StorageChest.OPEN, open), 3);
    }

    @Override
    protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
}
