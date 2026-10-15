package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wood Furniture V2 (docs/models/wood_furniture_v2.md, 2026-10-15): what hangs on a {@link WoodFurnitureV2.ToolRack}.
 * Slots 0..2: the pegs, right to left as seen from the front (long tools, head up); 3, 4: the shelf, left and right
 * (small items). Kept, synced and drawn by the chunk as the gun rack's ({@link WoodGunRackBlockEntity}).
 */
public class WoodToolRackBlockEntity extends WoodGunRackBlockEntity {
    public WoodToolRackBlockEntity(BlockPos pos, BlockState state) { super(WoodFurnitureV2.TOOL_RACK, pos, state); }
}
