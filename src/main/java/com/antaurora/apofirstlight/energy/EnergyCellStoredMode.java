package com.antaurora.apofirstlight.energy;

import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class EnergyCellStoredMode {
    public static final String MODE_KEY = "EnergyCellMode";

    private EnergyCellStoredMode() {
    }

    public static void write(ItemStack stack, EnergyCellMode mode) {
        CompoundTag existing = BlockItem.getBlockEntityData(stack);
        CompoundTag blockEntityData = existing == null ? new CompoundTag() : existing.copy();
        blockEntityData.putInt(MODE_KEY,
                (mode == null ? EnergyCellMode.CHARGE : mode).serializedValue());
        BlockItem.setBlockEntityData(stack, AflBlockEntities.ENERGY_CELL.get(), blockEntityData);
    }

    public static EnergyCellMode read(ItemStack stack) {
        CompoundTag blockEntityData = BlockItem.getBlockEntityData(stack);
        return blockEntityData == null
                ? EnergyCellMode.CHARGE
                : EnergyCellMode.fromSerialized(blockEntityData.getInt(MODE_KEY));
    }
}
