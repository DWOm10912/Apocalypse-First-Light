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
        registration.registerBlockDataProvider(FuelHazardJadeProvider.INSTANCE,
                com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity.class);
        registration.registerBlockDataProvider(FuelHazardJadeProvider.INSTANCE, FluidTankBlockEntity.class);
        registration.registerBlockDataProvider(FuelHazardJadeProvider.INSTANCE,
                com.antaurora.apofirstlight.blockentity.UndergroundFuelTankBlockEntity.class);
        registration.registerBlockDataProvider(FuelCanJadeProvider.INSTANCE, com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity.class);
        registration.registerBlockDataProvider(FuelHazardJadeProvider.INSTANCE, com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity.class);
        registration.registerBlockDataProvider(FuelCanJadeProvider.Pump.INSTANCE, com.antaurora.apofirstlight.blockentity.HandFuelPumpBlockEntity.class);
        registration.registerBlockDataProvider(EmergencyLightJadeProvider.INSTANCE, com.antaurora.apofirstlight.blockentity.EmergencyLightBlockEntity.class);
        registration.registerBlockDataProvider(LightPoleJadeProvider.INSTANCE, com.antaurora.apofirstlight.blockentity.LightPoleBaseBlockEntity.class);
        registration.registerBlockDataProvider(PriceSignJadeProvider.INSTANCE, com.antaurora.apofirstlight.blockentity.PriceSignBlockEntity.class);
        registration.registerBlockDataProvider(DieselGeneratorJadeProvider.INSTANCE, com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity.class);
        registration.registerBlockDataProvider(PortableDieselGeneratorJadeProvider.INSTANCE, com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity.class);
        registration.registerBlockDataProvider(GeneratorInletJadeProvider.INSTANCE, com.antaurora.apofirstlight.blockentity.GeneratorInletBoxBlockEntity.class);
        registration.registerBlockDataProvider(DistributionPanelJadeProvider.INSTANCE, com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity.class);
        registration.registerBlockDataProvider(BoxStoveJadeProvider.INSTANCE, com.antaurora.apofirstlight.cabin.BoxStoveBlockEntity.class);
        registration.registerBlockDataProvider(EmergencyRadioJadeProvider.INSTANCE, com.antaurora.apofirstlight.cabin.EmergencyRadioBlockEntity.class);
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
        registration.registerBlockComponent(FuelHazardJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.FuelDispenserBlock.class);
        registration.registerBlockComponent(FuelHazardJadeProvider.INSTANCE, FluidTankBlock.class);
        registration.registerBlockComponent(FuelHazardJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.UndergroundFuelTankBlock.class);
        registration.registerBlockComponent(FuelCanJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.FuelCanBlock.class);
        registration.registerBlockComponent(FuelHazardJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.FuelCanBlock.class);
        registration.registerBlockComponent(FuelCanJadeProvider.Pump.INSTANCE, com.antaurora.apofirstlight.block.HandFuelPumpBlock.class);
        registration.registerBlockComponent(EmergencyLightJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.EmergencyLightBlock.class);
        registration.registerBlockComponent(LightPoleJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.LightPoleBaseBlock.class);
        registration.registerBlockComponent(PriceSignJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.PriceSignBlock.class);
        registration.registerBlockComponent(DieselGeneratorJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.DieselGeneratorBlock.class);
        registration.registerBlockComponent(PortableDieselGeneratorJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.PortableDieselGeneratorBlock.class);
        registration.registerBlockComponent(DistributionPanelJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.DistributionPanelBlock.class);
        registration.registerBlockComponent(WallSwitchJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.WallSwitchBlock.class);
        registration.registerBlockComponent(GeneratorInletJadeProvider.INSTANCE, com.antaurora.apofirstlight.block.GeneratorInletBoxBlock.class);
        registration.registerBlockComponent(BoxStoveJadeProvider.INSTANCE, com.antaurora.apofirstlight.cabin.BoxStove.Stove.class);
        registration.registerBlockComponent(EmergencyRadioJadeProvider.INSTANCE, com.antaurora.apofirstlight.cabin.EmergencyRadio.Radio.class);
        for (Class<? extends net.minecraft.world.level.block.Block> trunk : java.util.List.of(com.antaurora.apofirstlight.tree.TreeBlocks.Root.class,
                com.antaurora.apofirstlight.tree.TreeBlocks.Wood.class, com.antaurora.apofirstlight.tree.TreeBlocks.Stump.class)) {
            registration.registerBlockComponent(TreeSpeciesJadeProvider.INSTANCE, trunk);
            registration.registerBlockIcon(TreeSpeciesJadeProvider.INSTANCE, trunk);
        }
        PriceSignJadeProvider.registerRedirect(registration);
        PavementMarkingJadeRedirect.register(registration);
        WoodFurnitureV2JadeRedirect.register(registration);
        registration.registerEntityComponent(PickupTruckJadeProvider.INSTANCE, com.antaurora.apofirstlight.vehicle.PickupTruckEntity.class);
        registration.registerEntityComponent(PickupTruckJadeProvider.INSTANCE, com.antaurora.apofirstlight.vehicle.PickupTruckEntity.Part.class);
    }
}
