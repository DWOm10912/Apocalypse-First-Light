package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
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

    private static ForgeFlowingFluid.Properties properties() {
        // MC 1.20.1 WaterFluid: slope 4, drop-off 1, tick delay 5, resistance 100.
        return new ForgeFlowingFluid.Properties(INDUSTRIAL_WASTE_TYPE, INDUSTRIAL_WASTE, FLOWING_INDUSTRIAL_WASTE)
                .bucket(AflItems.INDUSTRIAL_WASTE_BUCKET).block(AflBlocks.INDUSTRIAL_WASTE)
                .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5).explosionResistance(100.0F);
    }

    private AflFluids() {
    }
}
