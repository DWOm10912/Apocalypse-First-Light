package com.antaurora.apofirstlight.energy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class MachineStoredEnergy {
    public static final String ENERGY_KEY = "EnergyStored";

    private MachineStoredEnergy() {
    }

    public static void write(ItemStack stack, BlockEntityType<?> blockEntityType, int storedEnergy) {
        if (storedEnergy <= 0) {
            return;
        }
        CompoundTag blockEntityData = new CompoundTag();
        blockEntityData.putInt(ENERGY_KEY, storedEnergy);
        BlockItem.setBlockEntityData(stack, blockEntityType, blockEntityData);
    }

    public static int read(ItemStack stack) {
        CompoundTag blockEntityData = BlockItem.getBlockEntityData(stack);
        return blockEntityData == null ? 0 : Math.max(0, blockEntityData.getInt(ENERGY_KEY));
    }
}
