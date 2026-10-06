package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.HandFuelPumpBlock;
import com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity;
import com.antaurora.apofirstlight.blockentity.HandFuelPumpBlockEntity;
import com.antaurora.apofirstlight.fluid.FuelCanTransfers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the fuel containers (docs/models/fuel_containers_v1.md): the fuel and its bar, as a fluid tank; a drum with a
 * hand pump on it says so. {@link Pump}: the hand pump, what it draws from and what its hose fills.
 */
public enum FuelCanJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fuel_can");
    private static final String PUMPED = "AflCanPumped";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof FuelCanBlockEntity can)) return;
        FluidStack fluid = can.tank().getFluid();
        if (!fluid.isEmpty()) data.put(FluidTankJadeServerDataProvider.FLUID, fluid.writeToNBT(new CompoundTag()));
        data.putInt(FluidTankJadeServerDataProvider.FLUID_AMOUNT, fluid.isEmpty() ? 0 : fluid.getAmount());
        data.putInt(FluidTankJadeServerDataProvider.FLUID_CAPACITY, can.tank().getCapacity());
        if (can.getLevel() != null && can.getLevel().getBlockState(can.getBlockPos().above()).getBlock() instanceof HandFuelPumpBlock) data.putBoolean(PUMPED, true);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        FluidTankJadeComponentProvider.appendFluid(tooltip, data);
        if (data.getBoolean(PUMPED)) tooltip.add(Component.translatable("jade.apocalypse_firstlight.fuel_can.pump"));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return 2000;
    }

    public enum Pump implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
        INSTANCE;

        public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "hand_fuel_pump");

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof HandFuelPumpBlockEntity pump) || !(pump.getLevel() instanceof ServerLevel level)) return;
            put(data, "AflPumpFrom", pump.source(level));
            put(data, "AflPumpTo", pump.target(level));
        }

        private static void put(CompoundTag data, String key, IFluidHandler handler) {
            if (handler == null) return;
            CompoundTag tag = new CompoundTag();
            FluidStack fluid = FuelCanTransfers.contents(handler);
            if (!fluid.isEmpty()) tag.put("Fluid", fluid.writeToNBT(new CompoundTag()));
            tag.putInt("Amount", fluid.getAmount());
            tag.putInt("Capacity", handler.getTanks() > 0 ? handler.getTankCapacity(0) : 0);
            data.put(key, tag);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            line(tooltip, data, "AflPumpFrom", "from");
            line(tooltip, data, "AflPumpTo", "to");
        }

        private static void line(ITooltip tooltip, CompoundTag data, String key, String what) {
            String base = "jade.apocalypse_firstlight.hand_fuel_pump." + what;
            if (!data.contains(key, Tag.TAG_COMPOUND)) {
                tooltip.add(Component.translatable(base + ".none"));
                return;
            }
            CompoundTag tag = data.getCompound(key);
            FluidStack fluid = tag.contains("Fluid", Tag.TAG_COMPOUND) ? FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid")) : FluidStack.EMPTY;
            Component name = fluid.isEmpty() ? Component.translatable("jade.apocalypse_firstlight.empty_fluid") : fluid.getDisplayName();
            tooltip.add(Component.translatable(base, name, String.format("%,d", tag.getInt("Amount")), String.format("%,d", tag.getInt("Capacity"))));
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
}
