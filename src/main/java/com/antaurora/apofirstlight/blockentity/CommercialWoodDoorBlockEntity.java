package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.CommercialDoorStyle;
import com.antaurora.apofirstlight.block.CommercialWoodDoorBlock;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Commercial Wood Door (Steel-frame doors V1): the leaf swings into its own cell. The block's STYLE picks the visible child
 * bones of 'leaf': 'leaf_plain' (the solid slab, PLAIN / RESTROOM), 'leaf_lite' (the slab with the lite, VISION),
 * 'restroom' (indicator and plaque, RESTROOM). One mesh and profile per hinge side for all three looks.
 */
public class CommercialWoodDoorBlockEntity extends SteelFrameDoorBlockEntity {
    public CommercialWoodDoorBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.COMMERCIAL_WOOD_DOOR.get(), pos, state, "commercial_wood_door");
    }

    @Override
    public boolean meshPartVisible(String part) {
        BlockState state = getBlockState();
        CommercialDoorStyle style = state.hasProperty(CommercialWoodDoorBlock.STYLE) ? state.getValue(CommercialWoodDoorBlock.STYLE) : CommercialDoorStyle.PLAIN;
        return switch (part) {
            case "leaf_plain" -> style != CommercialDoorStyle.VISION;
            case "leaf_lite" -> style == CommercialDoorStyle.VISION;
            case "restroom" -> style == CommercialDoorStyle.RESTROOM;
            default -> true;
        };
    }
}
