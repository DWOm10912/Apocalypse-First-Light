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
 * (StackMassCalculator#fluid). Magazines that hold rounds (weapon/NativeMagazines) carry them as their contents. No other
 * AFL item keeps an item inventory; standard container NBT is read generically.
 */
public final class AflCarriedContents {
    private AflCarriedContents() {}

    /** Common setup. */
    public static void register() {
        // a shovel's load: its material's loose density (earthwork/BulkMaterials), counted up to ShovelItem.LOAD_MASS_CAP
        StackMassCalculator.registerBulkContents(AflItems.ROUND_POINT_SHOVEL.getId(), stack -> {
            long g = 0;
            for (var e : com.antaurora.apofirstlight.earthwork.BulkHolder.InStack.read(stack).entrySet()) {
                var t = com.antaurora.apofirstlight.earthwork.BulkMaterials.table();
                g += t.get(t.index(e.getKey())).grams(e.getValue());
            }
            return Math.min(g, com.antaurora.apofirstlight.item.ShovelItem.LOAD_MASS_CAP);
        });
        StackMassCalculator.registerFluidContents(AflItems.FLUID_TANK.getId(), stack -> List.of(FluidTankStoredFluid.read(stack)));
        // fuel containers (docs/models/fuel_containers_v1.md): the fuel in their BlockEntityTag
        for (var item : List.of(AflItems.JERRY_CAN, AflItems.SMALL_FUEL_DRUM, AflItems.FUEL_DRUM))
            StackMassCalculator.registerFluidContents(item.getId(), stack -> List.of(com.antaurora.apofirstlight.item.FuelCanItem.fluid(stack)));
        // ThermalGeneratorBlockEntity#writeDropData
        StackMassCalculator.registerFluidContents(AflItems.THERMAL_GENERATOR.getId(), stack -> List.of(StoredFluidTooltip.read(
                BlockItem.getBlockEntityData(stack), "LiquidTank", ThermalFuelDefinitions.TANK_CAPACITY_MB)));
        for (var item : List.of(AflItems.P9_01_MAGAZINE, AflItems.P9_01_EXTENDED_MAGAZINE))
            StackMassCalculator.registerContents(item.getId(), stack -> {
                int rounds = com.antaurora.apofirstlight.weapon.NativeMagazines.rounds(stack);
                var gun = stack.getItem() instanceof com.antaurora.apofirstlight.weapon.NativeMagazineItem m ? m.roundGun() : null;
                var ammo = gun == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(gun.ammoType());
                return rounds <= 0 || ammo == null ? List.of() : List.of(new net.minecraft.world.item.ItemStack(ammo, rounds));
            });
        StackMassCalculator.registerFluidContents(AflItems.CHEMICAL_REACTOR.getId(), stack -> {
            var data = BlockItem.getBlockEntityData(stack);
            int capacity = ChemicalReactorBlockEntity.TANK_CAPACITY_MB;
            return List.of(StoredFluidTooltip.read(data, ChemicalReactorBlockEntity.INPUT_TANK_KEY, capacity),
                    StoredFluidTooltip.read(data, ChemicalReactorBlockEntity.WASTE_TANK_KEY, capacity));
        });
    }
}
