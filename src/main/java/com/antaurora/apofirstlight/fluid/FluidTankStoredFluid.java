package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public final class FluidTankStoredFluid {
    public static final String FLUID_KEY = "Fluid";
    private static final String BLOCK_ENTITY_TAG_KEY = "BlockEntityTag";

    private FluidTankStoredFluid() {
    }

    public static void write(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty() || fluid.getAmount() <= 0) {
            clear(stack);
            return;
        }

        FluidStack stored = fluid.copy();
        stored.setAmount(Math.min(FluidTankBlockEntity.CAPACITY_MB, stored.getAmount()));
        CompoundTag blockEntityData = new CompoundTag();
        blockEntityData.put(FLUID_KEY, stored.writeToNBT(new CompoundTag()));
        BlockItem.setBlockEntityData(stack, AflBlockEntities.FLUID_TANK.get(), blockEntityData);
    }

    public static FluidStack read(ItemStack stack) {
        return StoredFluidTooltip.read(BlockItem.getBlockEntityData(stack), FLUID_KEY,
                FluidTankBlockEntity.CAPACITY_MB);
    }

    public static void clear(ItemStack stack) {
        stack.removeTagKey(BLOCK_ENTITY_TAG_KEY);
    }

    public static boolean hasStoredFluid(ItemStack stack) {
        return !read(stack).isEmpty();
    }
}
