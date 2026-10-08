package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Steel Door (Steel-frame doors V1): the leaf swings out toward the placer, out of the cell. */
public class SteelDoorBlockEntity extends SteelFrameDoorBlockEntity {
    public SteelDoorBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.STEEL_DOOR.get(), pos, state, "steel_door");
    }
}
