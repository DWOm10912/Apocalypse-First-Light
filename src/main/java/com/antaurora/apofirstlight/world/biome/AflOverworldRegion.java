package com.antaurora.apofirstlight.world.biome;

import com.antaurora.apofirstlight.registry.AflBiomes;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.function.Consumer;

public final class AflOverworldRegion extends Region {
    public AflOverworldRegion(ResourceLocation name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> biomeRegistry,
                          Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        addBiome(mapper,
                Climate.Parameter.span(-0.5F, 0.5F),
                Climate.Parameter.span(0.0F, 1.0F),
                Climate.Parameter.span(-0.11F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.point(0.0F),
                0.0F,
                AflBiomes.IRRADIATED_WOODLAND);
        addBiome(mapper,
                Climate.Parameter.span(-1.0F, -0.2F),
                Climate.Parameter.span(-1.0F, -0.1F),
                Climate.Parameter.span(-0.11F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.point(0.0F),
                0.0F,
                AflBiomes.FALLOUT_BARRENS);
    }
}
