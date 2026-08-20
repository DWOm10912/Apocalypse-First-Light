package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.world.feature.DegradedGroundPatchFeature;
import com.antaurora.apofirstlight.world.feature.IrradiatedTreeFeature;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, ApocalypseFirstLight.MOD_ID);
    public static final RegistryObject<Feature<?>> DAMAGED_OAK = FEATURES.register("damaged_oak",
            () -> new IrradiatedTreeFeature(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(), false, false));
    public static final RegistryObject<Feature<?>> DEAD_OAK = FEATURES.register("dead_oak",
            () -> new IrradiatedTreeFeature(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(), true, false));
    public static final RegistryObject<Feature<?>> DAMAGED_BIRCH = FEATURES.register("damaged_birch",
            () -> new IrradiatedTreeFeature(Blocks.BIRCH_LOG.defaultBlockState(), Blocks.BIRCH_LEAVES.defaultBlockState(), false, true));
    public static final RegistryObject<Feature<?>> DEAD_BIRCH = FEATURES.register("dead_birch",
            () -> new IrradiatedTreeFeature(Blocks.BIRCH_LOG.defaultBlockState(), Blocks.BIRCH_LEAVES.defaultBlockState(), true, true));
    public static final RegistryObject<Feature<?>> COARSE_DIRT_PATCH = FEATURES.register("coarse_dirt_patch",
            () -> new DegradedGroundPatchFeature(Blocks.COARSE_DIRT.defaultBlockState(), 2));
    public static final RegistryObject<Feature<?>> PODZOL_PATCH = FEATURES.register("podzol_patch",
            () -> new DegradedGroundPatchFeature(Blocks.PODZOL.defaultBlockState(), 1));
    public static final RegistryObject<Feature<?>> DIRT_PATCH = FEATURES.register("dirt_patch",
            () -> new DegradedGroundPatchFeature(Blocks.DIRT.defaultBlockState(), 1));
    public static final RegistryObject<Feature<?>> FALLOUT_SOIL_PATCH = FEATURES.register("fallout_soil_patch",
            () -> new DegradedGroundPatchFeature(AflBlocks.FALLOUT_SOIL.get().defaultBlockState(), 3));

    private AflFeatures() {
    }
}
