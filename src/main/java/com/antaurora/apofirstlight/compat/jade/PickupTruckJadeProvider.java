package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the T15 pickup (docs/vehicles/first_pickup_v0_design.md 8.2): under its name, the diesel in the tank and its
 * bar, as a fluid tank. Read from the truck's synced fuel (no server data); the cab and its two picking parts alike.
 */
public enum PickupTruckJadeProvider implements IEntityComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "pickup_t15");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        PickupTruckEntity truck = accessor.getEntity() instanceof PickupTruckEntity t ? t
                : accessor.getEntity() instanceof PickupTruckEntity.Part part ? part.getParent() : null;
        if (truck == null) return;
        CompoundTag data = new CompoundTag();
        int litres = truck.fuel();
        if (litres > 0) data.put(FluidTankJadeServerDataProvider.FLUID, new FluidStack(AflFluids.DIESEL.get(), litres).writeToNBT(new CompoundTag()));
        data.putInt(FluidTankJadeServerDataProvider.FLUID_AMOUNT, litres);
        data.putInt(FluidTankJadeServerDataProvider.FLUID_CAPACITY, PickupTruckEntity.TANK);
        FluidTankJadeComponentProvider.appendFluid(tooltip, data);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
