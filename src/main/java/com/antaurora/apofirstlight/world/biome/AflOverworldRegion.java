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
    /** Candidate fallback only; MainNationBiomeRegionPlan owns final MAIN_NATION land selection. */
    public static final int REGION_WEIGHT = 190;

    public AflOverworldRegion(ResourceLocation name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> biomeRegistry,
                          Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        addBiome(mapper,
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-0.35F, 1.0F),
                Climate.Parameter.span(-0.11F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.point(0.0F),
                0.0F,
                AflBiomes.FALLOUT_BARRENS);
        addBiome(mapper,
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, -0.35F),
                Climate.Parameter.span(-0.11F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                0.0F,
                AflBiomes.FALLOUT_BARRENS);
    }
}
