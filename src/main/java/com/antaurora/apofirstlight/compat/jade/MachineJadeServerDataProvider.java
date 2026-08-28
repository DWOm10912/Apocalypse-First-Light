package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.CrusherBlockEntity;
import com.antaurora.apofirstlight.blockentity.EnergyCellBlockEntity;
import com.antaurora.apofirstlight.blockentity.IndustrialFurnaceBlockEntity;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum MachineJadeServerDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    public static final ResourceLocation UID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "machine_status");
    public static final String MACHINE_TYPE = "AflMachineType";
    public static final String ENERGY_STORED = "AflEnergyStored";
    public static final String ENERGY_CAPACITY = "AflEnergyCapacity";
    public static final String FUEL = "AflFuel";
    public static final String INPUT = "AflInput";
    public static final String OUTPUTS = "AflOutputs";
    public static final String PROCESSING_PROGRESS = "AflProcessingProgress";
    public static final String PROCESSING_TIME = "AflProcessingTime";
    public static final String PROCESSING_LANES = "AflProcessingLanes";

    public static final String THERMAL_GENERATOR = "thermal_generator";
    public static final String ENERGY_CELL = "energy_cell";
    public static final String CRUSHER = "crusher";
    public static final String INDUSTRIAL_FURNACE = "industrial_furnace";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ThermalGeneratorBlockEntity generator) {
            data.putString(MACHINE_TYPE, THERMAL_GENERATOR);
            putEnergy(data, generator.getStoredEnergy(),
                    MachineBalanceManager.thermalGenerator().capacityFe());
            putStack(data, FUEL, generator.getItem(ThermalGeneratorBlockEntity.FUEL_SLOT));
            return;
        }

        if (accessor.getBlockEntity() instanceof EnergyCellBlockEntity cell) {
            data.putString(MACHINE_TYPE, ENERGY_CELL);
            putEnergy(data, cell.getStoredEnergy(), MachineBalanceManager.energyCell().capacityFe());
            return;
        }

        if (accessor.getBlockEntity() instanceof CrusherBlockEntity crusher) {
            data.putString(MACHINE_TYPE, CRUSHER);
            putEnergy(data, crusher.getStoredEnergy(), MachineBalanceManager.crusher().capacityFe());
            putStack(data, INPUT, crusher.getItem(CrusherBlockEntity.INPUT_SLOT));

            ListTag outputs = new ListTag();
            for (int slot = CrusherBlockEntity.FIRST_OUTPUT_SLOT;
                 slot < CrusherBlockEntity.FIRST_OUTPUT_SLOT + CrusherBlockEntity.OUTPUT_SLOT_COUNT;
                 slot++) {
                ItemStack stack = crusher.getItem(slot);
                if (!stack.isEmpty()) {
                    outputs.add(stack.copy().save(new CompoundTag()));
                }
            }
            if (!outputs.isEmpty()) {
                data.put(OUTPUTS, outputs);
            }

            data.putInt(PROCESSING_PROGRESS, crusher.getProcessingProgress());
            data.putInt(PROCESSING_TIME, crusher.getProcessingTime());
            return;
        }

        if (accessor.getBlockEntity() instanceof IndustrialFurnaceBlockEntity furnace) {
            data.putString(MACHINE_TYPE, INDUSTRIAL_FURNACE);
            putEnergy(data, furnace.getStoredEnergy(),
                    MachineBalanceManager.industrialFurnace().capacityFe());

            ListTag lanes = new ListTag();
            for (int lane = 0; lane < IndustrialFurnaceBlockEntity.LANE_COUNT; lane++) {
                int progress = furnace.getLaneProgress(lane);
                if (progress <= 0) {
                    continue;
                }
                CompoundTag laneData = new CompoundTag();
                ItemStack input = furnace.getItem(IndustrialFurnaceBlockEntity.inputSlot(lane)).copy();
                if (!input.isEmpty()) {
                    input.setCount(1);
                    putStack(laneData, INPUT, input);
                }
                laneData.putInt(PROCESSING_PROGRESS, progress);
                laneData.putInt(PROCESSING_TIME,
                        furnace.getLaneRequiredTicks(accessor.getLevel(), lane));
                lanes.add(laneData);
            }
            if (!lanes.isEmpty()) {
                data.put(PROCESSING_LANES, lanes);
            }

            ListTag outputs = new ListTag();
            for (int lane = 0; lane < IndustrialFurnaceBlockEntity.LANE_COUNT; lane++) {
                ItemStack stack = furnace.getItem(IndustrialFurnaceBlockEntity.outputSlot(lane));
                if (!stack.isEmpty()) {
                    outputs.add(stack.copy().save(new CompoundTag()));
                }
            }
            if (!outputs.isEmpty()) {
                data.put(OUTPUTS, outputs);
            }
        }
    }

    private static void putEnergy(CompoundTag data, int stored, int capacity) {
        data.putInt(ENERGY_STORED, Math.max(0, stored));
        data.putInt(ENERGY_CAPACITY, Math.max(0, capacity));
    }

    private static void putStack(CompoundTag data, String key, ItemStack stack) {
        if (!stack.isEmpty()) {
            data.put(key, stack.copy().save(new CompoundTag()));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
