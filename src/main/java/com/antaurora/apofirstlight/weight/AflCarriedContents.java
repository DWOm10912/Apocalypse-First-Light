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
 * (StackMassCalculator#fluid). Standard container NBT is read generically; a chest rig adds what hangs on its clips.
 */
public final class AflCarriedContents {
    private AflCarriedContents() {}

    /** Common setup. */
    public static void register() {
        // chest rig: its grid (standard Items) and its clips (RigClips)
        StackMassCalculator.registerContents(AflItems.FIELD_CHEST_RIG.getId(), stack -> {
            List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
            var items = stack.getTag() == null ? null : stack.getTag().getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
            if (items != null) for (int i = 0; i < items.size(); i++) out.add(net.minecraft.world.item.ItemStack.of(items.getCompound(i)));
            out.addAll(com.antaurora.apofirstlight.inventory.grid.RigClips.all(stack));
            return out;
        });
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
