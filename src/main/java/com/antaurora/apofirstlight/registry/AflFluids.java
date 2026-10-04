package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.FuelFluid;
import com.antaurora.apofirstlight.fluid.FuelFluidType;
import com.antaurora.apofirstlight.fluid.IndustrialWasteFluid;
import com.antaurora.apofirstlight.fluid.IndustrialWasteFluidType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(
            ForgeRegistries.Keys.FLUID_TYPES, ApocalypseFirstLight.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(
            ForgeRegistries.FLUIDS, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<FluidType> INDUSTRIAL_WASTE_TYPE = FLUID_TYPES.register(
            "industrial_waste", IndustrialWasteFluidType::new);
    public static final RegistryObject<IndustrialWasteFluid.Source> INDUSTRIAL_WASTE = FLUIDS.register(
            "industrial_waste", () -> new IndustrialWasteFluid.Source(properties()));
    public static final RegistryObject<IndustrialWasteFluid.Flowing> FLOWING_INDUSTRIAL_WASTE = FLUIDS.register(
            "flowing_industrial_waste", () -> new IndustrialWasteFluid.Flowing(properties()));

    // Fuel fluids V1 (docs/gameplay/fuel_fluids_v1.md): no bucket, source blocks by command only. Gasoline: lighter and
    // thinner than water, spreads a little further and faster; diesel: heavier and oilier, spreads less and slower.
    public static final RegistryObject<FluidType> GASOLINE_TYPE = FLUID_TYPES.register("gasoline",
            () -> new FuelFluidType("gasoline", 740, 600, 0.016D, 0xC9A64A, 5.0F));
    public static final RegistryObject<FuelFluid.Source> GASOLINE = FLUIDS.register("gasoline",
            () -> new FuelFluid.Source(gasoline()));
    public static final RegistryObject<FuelFluid.Flowing> FLOWING_GASOLINE = FLUIDS.register("flowing_gasoline",
            () -> new FuelFluid.Flowing(gasoline()));
    public static final RegistryObject<FluidType> DIESEL_TYPE = FLUID_TYPES.register("diesel",
            () -> new FuelFluidType("diesel", 840, 2400, 0.010D, 0x8A5E1C, 3.0F));
    public static final RegistryObject<FuelFluid.Source> DIESEL = FLUIDS.register("diesel",
            () -> new FuelFluid.Source(diesel()));
    public static final RegistryObject<FuelFluid.Flowing> FLOWING_DIESEL = FLUIDS.register("flowing_diesel",
            () -> new FuelFluid.Flowing(diesel()));

    private static ForgeFlowingFluid.Properties gasoline() {
        return new ForgeFlowingFluid.Properties(GASOLINE_TYPE, GASOLINE, FLOWING_GASOLINE).block(AflBlocks.GASOLINE)
                .slopeFindDistance(5).levelDecreasePerBlock(1).tickRate(4).explosionResistance(100.0F);
    }

    private static ForgeFlowingFluid.Properties diesel() {
        return new ForgeFlowingFluid.Properties(DIESEL_TYPE, DIESEL, FLOWING_DIESEL).block(AflBlocks.DIESEL)
                .slopeFindDistance(3).levelDecreasePerBlock(2).tickRate(10).explosionResistance(100.0F);
    }

    private static ForgeFlowingFluid.Properties properties() {
        // MC 1.20.1 WaterFluid: slope 4, drop-off 1, tick delay 5, resistance 100.
        return new ForgeFlowingFluid.Properties(INDUSTRIAL_WASTE_TYPE, INDUSTRIAL_WASTE, FLOWING_INDUSTRIAL_WASTE)
                .bucket(AflItems.INDUSTRIAL_WASTE_BUCKET).block(AflBlocks.INDUSTRIAL_WASTE)
                .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5).explosionResistance(100.0F);
    }

    private AflFluids() {
    }
}
