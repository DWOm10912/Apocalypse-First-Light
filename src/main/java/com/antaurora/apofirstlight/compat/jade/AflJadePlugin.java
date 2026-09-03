package com.antaurora.apofirstlight.compat.jade;

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
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
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
    }
}
