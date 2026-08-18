package com.antaurora.apofirstlight.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;

public class PoplarTreeGrower extends AbstractTreeGrower {
    private static final ResourceKey<ConfiguredFeature<?, ?>> POPLAR_TREE = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            new ResourceLocation("apocalypse_firstlight", "poplar_tree"));

    @Override
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean hasFlowers) {
        return POPLAR_TREE;
    }
}
