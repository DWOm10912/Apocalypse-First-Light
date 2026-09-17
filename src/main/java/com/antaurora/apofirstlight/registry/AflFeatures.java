package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.world.feature.DegradedGroundPatchFeature;
import com.antaurora.apofirstlight.world.feature.PrimaryHighwayFeature;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, ApocalypseFirstLight.MOD_ID);
    public static final RegistryObject<Feature<?>> COARSE_DIRT_PATCH = FEATURES.register("coarse_dirt_patch",
            () -> new DegradedGroundPatchFeature(Blocks.COARSE_DIRT.defaultBlockState(), 2));
    public static final RegistryObject<Feature<?>> DIRT_PATCH = FEATURES.register("dirt_patch",
            () -> new DegradedGroundPatchFeature(Blocks.DIRT.defaultBlockState(), 1));
    public static final RegistryObject<Feature<?>> FALLOUT_SOIL_PATCH = FEATURES.register("fallout_soil_patch",
            () -> new DegradedGroundPatchFeature(AflBlocks.FALLOUT_SOIL.get().defaultBlockState(), 3));
    public static final RegistryObject<Feature<?>> PRIMARY_HIGHWAY = FEATURES.register("primary_highway",
            PrimaryHighwayFeature::new);

    private AflFeatures() {
    }
}
