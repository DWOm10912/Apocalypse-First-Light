package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.block.ChemicalReactorBlock;
import com.antaurora.apofirstlight.blockentity.ChemicalReactorBlockEntity;

import com.antaurora.apofirstlight.block.CrusherBlock;
import com.antaurora.apofirstlight.block.EnergyCellBlock;
import com.antaurora.apofirstlight.block.FluidTankBlock;
import com.antaurora.apofirstlight.block.IndustrialFurnaceBlock;
import com.antaurora.apofirstlight.block.ThermalGeneratorBlock;
import com.antaurora.apofirstlight.block.CompressorBlock;
import com.antaurora.apofirstlight.block.AlloyFurnaceBlock;
import com.antaurora.apofirstlight.blockentity.CrusherBlockEntity;
import com.antaurora.apofirstlight.blockentity.EnergyCellBlockEntity;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.blockentity.IndustrialFurnaceBlockEntity;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.blockentity.CompressorBlockEntity;
import com.antaurora.apofirstlight.blockentity.AlloyFurnaceBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public final class AflJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MachineJadeServerDataProvider.INSTANCE,
                ChemicalReactorBlockEntity.class);
        registration.registerBlockDataProvider(MachineJadeServerDataProvider.INSTANCE,
                ThermalGeneratorBlockEntity.class);
        registration.registerBlockDataProvider(MachineJadeServerDataProvider.INSTANCE,
                EnergyCellBlockEntity.class);
        registration.registerBlockDataProvider(MachineJadeServerDataProvider.INSTANCE,
                CrusherBlockEntity.class);
        registration.registerBlockDataProvider(MachineJadeServerDataProvider.INSTANCE,
                IndustrialFurnaceBlockEntity.class);
        registration.registerBlockDataProvider(MachineJadeServerDataProvider.INSTANCE,
                CompressorBlockEntity.class);
        registration.registerBlockDataProvider(MachineJadeServerDataProvider.INSTANCE,
                AlloyFurnaceBlockEntity.class);
        registration.registerBlockDataProvider(FluidTankJadeServerDataProvider.INSTANCE,
                FluidTankBlockEntity.class);
        registration.registerBlockDataProvider(UndergroundFuelTankJadeProvider.INSTANCE,
                com.antaurora.apofirstlight.blockentity.UndergroundFuelTankBlockEntity.class);
        registration.registerBlockDataProvider(IntakePumpJadeProvider.INSTANCE,
                com.antaurora.apofirstlight.blockentity.IntakePumpBlockEntity.class);
        registration.registerBlockDataProvider(FuelDispenserJadeProvider.INSTANCE,
                com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity.class);
        registration.registerBlockDataProvider(SubmersibleFuelPumpJadeProvider.INSTANCE,
                com.antaurora.apofirstlight.blockentity.SubmersibleFuelPumpBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MachineJadeComponentProvider.INSTANCE,
                ChemicalReactorBlock.class);
        registration.registerBlockComponent(MachineJadeComponentProvider.INSTANCE,
                ThermalGeneratorBlock.class);
        registration.registerBlockComponent(MachineJadeComponentProvider.INSTANCE,
                EnergyCellBlock.class);
        registration.registerBlockComponent(MachineJadeComponentProvider.INSTANCE,
                CrusherBlock.class);
        registration.registerBlockComponent(MachineJadeComponentProvider.INSTANCE,
                IndustrialFurnaceBlock.class);
        registration.registerBlockComponent(MachineJadeComponentProvider.INSTANCE,
                CompressorBlock.class);
        registration.registerBlockComponent(MachineJadeComponentProvider.INSTANCE,
                AlloyFurnaceBlock.class);
        registration.registerBlockComponent(FluidTankJadeComponentProvider.INSTANCE,
                FluidTankBlock.class);
        registration.registerBlockComponent(UndergroundFuelTankJadeProvider.INSTANCE,
                com.antaurora.apofirstlight.block.UndergroundFuelTankBlock.class);
        UndergroundFuelTankJadeProvider.registerRedirect(registration);
        registration.registerBlockComponent(IntakePumpJadeProvider.INSTANCE,
                com.antaurora.apofirstlight.block.IntakePumpBlock.class);
        IntakePumpJadeProvider.registerRedirect(registration);
        registration.registerBlockComponent(SubmersibleFuelPumpJadeProvider.INSTANCE,
                com.antaurora.apofirstlight.block.SubmersibleFuelPumpBlock.class);
        registration.registerBlockComponent(FuelDispenserJadeProvider.INSTANCE,
                com.antaurora.apofirstlight.block.FuelDispenserBlock.class);
        FuelDispenserJadeProvider.registerRedirect(registration);
        registration.registerBlockComponent(FuelDispenserJadeProvider.Sump.INSTANCE,
                com.antaurora.apofirstlight.block.FuelDispenserSumpBlock.class);
    }
}
