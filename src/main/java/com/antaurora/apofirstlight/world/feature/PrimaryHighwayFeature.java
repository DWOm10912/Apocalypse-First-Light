package com.antaurora.apofirstlight.world.feature;

import com.antaurora.apofirstlight.worldgen.highway.NaturalHighwayGenerationAdapter;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** One deterministic query/owned render pass for the currently decorating Overworld chunk. */
public final class PrimaryHighwayFeature extends Feature<NoneFeatureConfiguration> {
    public PrimaryHighwayFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return NaturalHighwayGenerationAdapter.generate(context.level(), context.chunkGenerator());
    }
}
