package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.blockentity.ChemicalReactorBlockEntity;
import com.antaurora.apofirstlight.energy.ThermalFuelDefinitions;
import com.antaurora.apofirstlight.fluid.FluidTankStoredFluid;
import com.antaurora.apofirstlight.fluid.StoredFluidTooltip;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.world.item.BlockItem;
import java.util.List;

/**
 * AFL items that keep their block's fluid when broken: the fluid counts toward the carried item
 * (StackMassCalculator#fluid). No AFL item keeps an item inventory; standard container NBT is read generically.
 */
public final class AflCarriedContents {
    private AflCarriedContents() {}

    /** Common setup. */
    public static void register() {
        StackMassCalculator.registerFluidContents(AflItems.FLUID_TANK.getId(), stack -> List.of(FluidTankStoredFluid.read(stack)));
        // fuel containers (docs/models/fuel_containers_v1.md): the fuel in their BlockEntityTag
        for (var item : List.of(AflItems.JERRY_CAN, AflItems.SMALL_FUEL_DRUM, AflItems.FUEL_DRUM))
            StackMassCalculator.registerFluidContents(item.getId(), stack -> List.of(com.antaurora.apofirstlight.item.FuelCanItem.fluid(stack)));
        // ThermalGeneratorBlockEntity#writeDropData
        StackMassCalculator.registerFluidContents(AflItems.THERMAL_GENERATOR.getId(), stack -> List.of(StoredFluidTooltip.read(
                BlockItem.getBlockEntityData(stack), "LiquidTank", ThermalFuelDefinitions.TANK_CAPACITY_MB)));
        StackMassCalculator.registerFluidContents(AflItems.CHEMICAL_REACTOR.getId(), stack -> {
            var data = BlockItem.getBlockEntityData(stack);
            int capacity = ChemicalReactorBlockEntity.TANK_CAPACITY_MB;
            return List.of(StoredFluidTooltip.read(data, ChemicalReactorBlockEntity.INPUT_TANK_KEY, capacity),
                    StoredFluidTooltip.read(data, ChemicalReactorBlockEntity.WASTE_TANK_KEY, capacity));
        });
    }
}
