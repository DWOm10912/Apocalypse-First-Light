package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wood Furniture V2 (docs/models/wood_furniture_v2.md): the wooden storage chest's 27 slots, kept by its left (master)
 * cell. Since 2026-10-15 (the two-cell blanket chest) the furniture's searchable storage ({@link WoodCounterBlockEntity}:
 * the lid opened and closed by the player, world loot searched slot by slot, a player's own chest plain storage); its own
 * type keeps the chest's block entity ID, so a chest saved before (a vanilla chest's "Items") still loads its things.
 */
public class StorageChestBlockEntity extends WoodCounterBlockEntity {
    public StorageChestBlockEntity(BlockPos pos, BlockState state) { super(WoodFurnitureV2.CHEST, pos, state); }
}
