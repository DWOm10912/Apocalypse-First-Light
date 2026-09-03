package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum FluidTankJadeServerDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    public static final ResourceLocation UID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fluid_tank");
    public static final String FLUID = "AflFluid";
    public static final String FLUID_AMOUNT = "AflFluidAmount";
    public static final String FLUID_CAPACITY = "AflFluidCapacity";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof FluidTankBlockEntity tank)) {
            return;
        }

        FluidStack fluid = tank.getFluid();
        int amount = fluid.isEmpty() ? 0 : Math.max(0, fluid.getAmount());
        if (!fluid.isEmpty()) {
            data.put(FLUID, fluid.writeToNBT(new CompoundTag()));
        }
        data.putInt(FLUID_AMOUNT, amount);
        data.putInt(FLUID_CAPACITY, Math.max(0, tank.getCapacity()));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
