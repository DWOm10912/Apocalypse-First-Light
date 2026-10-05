package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.SubmersibleFuelPumpBlockEntity;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the submersible fuel pump (docs/models/fuel_station_sump_v1.md): its status (running, or why not), what the
 * tank under it holds, the FE buffer and the outlet buffer.
 */
public enum SubmersibleFuelPumpJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "submersible_fuel_pump");
    private static final String STATUS = "AflSubPumpStatus";
    private static final String SOURCE = "AflSubPumpSource";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof SubmersibleFuelPumpBlockEntity pump)) return;
        data.putString(STATUS, pump.status().key());
        FluidStack source = pump.tankFluid();
        ResourceLocation id = source.isEmpty() ? null : ForgeRegistries.FLUIDS.getKey(source.getFluid());
        if (id != null) data.putString(SOURCE, id.toString());
        data.putInt(MachineJadeServerDataProvider.ENERGY_STORED, pump.energyStored());
        data.putInt(MachineJadeServerDataProvider.ENERGY_CAPACITY, MachineBalanceManager.intakePump().capacityFe());
        FluidStack fluid = pump.buffer().getFluid();
        if (!fluid.isEmpty()) data.put(FluidTankJadeServerDataProvider.FLUID, fluid.writeToNBT(new CompoundTag()));
        data.putInt(FluidTankJadeServerDataProvider.FLUID_AMOUNT, fluid.isEmpty() ? 0 : fluid.getAmount());
        data.putInt(FluidTankJadeServerDataProvider.FLUID_CAPACITY, pump.buffer().getCapacity());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(STATUS, Tag.TAG_STRING)) return;
        tooltip.remove(Identifiers.UNIVERSAL_ENERGY_STORAGE);
        String status = data.getString(STATUS);
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.submersible_fuel_pump.status",
                Component.translatable("jade.apocalypse_firstlight.submersible_fuel_pump.status." + status)
                        .withStyle(status.equals("running") ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        Fluid source = data.contains(SOURCE, Tag.TAG_STRING) ? ForgeRegistries.FLUIDS.getValue(new ResourceLocation(data.getString(SOURCE))) : null;
        Component sourceName = source == null ? Component.translatable("jade.apocalypse_firstlight.intake_pump.source.none")
                : new FluidStack(source, 1).getDisplayName();
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.intake_pump.source", sourceName));
        MachineJadeComponentProvider.addEnergy(tooltip, data);
        FluidTankJadeComponentProvider.appendFluid(tooltip, data);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return 2000;
    }
}
