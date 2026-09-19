package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.geography.MacroHeightDensity;
import com.antaurora.apofirstlight.worldgen.geography.MacroTerrainDensity;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AflDensityFunctions {
    public static final DeferredRegister<Codec<? extends DensityFunction>> TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, ApocalypseFirstLight.MOD_ID);
    public static final RegistryObject<Codec<? extends DensityFunction>> MACRO_HEIGHT =
            TYPES.register("macro_height", () -> MacroHeightDensity.CODEC.codec());
    public static final RegistryObject<Codec<? extends DensityFunction>> MACRO_TERRAIN =
            TYPES.register("macro_terrain", () -> MacroTerrainDensity.CODEC.codec());
    private AflDensityFunctions() {}
}
