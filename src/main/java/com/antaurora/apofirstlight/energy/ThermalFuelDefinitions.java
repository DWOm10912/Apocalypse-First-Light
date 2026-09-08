package com.antaurora.apofirstlight.energy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import java.util.List;
import java.util.Map;

/** Liquid identities map to existing canonical item-fuel energies, never a second FE table. */
public final class ThermalFuelDefinitions {
    public static final int TANK_CAPACITY_MB = 4000;
    public static final int DISPLAY_VOLUME_MB = 1000;
    private static final Map<Fluid, ResourceLocation> LIQUID_SOURCES = Map.of(
            Fluids.LAVA, new ResourceLocation("minecraft", "lava_bucket"));

    private ThermalFuelDefinitions() {}

    public record LiquidFuel(Fluid fluid, int energyPer1000Mb) {}

    public record DisplayFuel(ResourceLocation id, net.minecraft.world.item.ItemStack item,
                              FluidStack fluid, int energyFe) {}

    public static List<DisplayFuel> displayFuels(Map<ResourceLocation, Integer> energies) {
        java.util.ArrayList<DisplayFuel> result = new java.util.ArrayList<>();
        energies.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(entry.getKey());
            if (item != net.minecraft.world.item.Items.AIR && entry.getValue() > 0)
                result.add(new DisplayFuel(new ResourceLocation("apocalypse_firstlight", "thermal/item/"
                        + entry.getKey().getNamespace() + "/" + entry.getKey().getPath()),
                        new net.minecraft.world.item.ItemStack(item), FluidStack.EMPTY, entry.getValue()));
        });
        for (LiquidFuel fuel : liquids(energies)) {
            var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fuel.fluid());
            result.add(new DisplayFuel(new ResourceLocation("apocalypse_firstlight", "thermal/fluid/"
                    + id.getNamespace() + "/" + id.getPath()), net.minecraft.world.item.ItemStack.EMPTY,
                    new FluidStack(fuel.fluid(), DISPLAY_VOLUME_MB), fuel.energyPer1000Mb()));
        }
        return List.copyOf(result);
    }

    public static List<LiquidFuel> liquids(Map<ResourceLocation, Integer> itemEnergies) {
        return LIQUID_SOURCES.entrySet().stream().filter(e -> itemEnergies.getOrDefault(e.getValue(),0)>0)
                .map(e -> new LiquidFuel(e.getKey(),itemEnergies.get(e.getValue()))).toList();
    }

    public static int energyPer1000Mb(FluidStack fluid) {
        ResourceLocation source=LIQUID_SOURCES.get(fluid.getFluid());
        return source==null?0:MachineBalanceManager.thermalGeneratorFuelEnergies().getOrDefault(source,0);
    }

    public static boolean accepts(FluidStack fluid) {
        return !fluid.isEmpty() && energyPer1000Mb(fluid)>0;
    }
}
