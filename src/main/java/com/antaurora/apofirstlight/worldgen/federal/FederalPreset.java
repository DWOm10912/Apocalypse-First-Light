package com.antaurora.apofirstlight.worldgen.federal;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The "AFL Federation (experimental)" world preset (C2, 2026-10-14; docs/worldgen/federal_six_state_c2.md): an
 * overworld on the noise settings {@code apocalypse_firstlight:federal_experimental}, whose plan density functions
 * carry {@code "profile": "federal"} (the six-state FederalTerrainPlan). Data:
 * data/apocalypse_firstlight/worldgen/world_preset/federal_experimental.json, listed in the world-type button through
 * data/minecraft/tags/worldgen/world_preset/normal.json. The default preset stays r1; nothing switches to this one.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FederalPreset {
    private FederalPreset() {}

    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "federal_experimental"));
    public static final ResourceKey<NoiseGeneratorSettings> NOISE_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "federal_experimental"));

    /** The overworld's noise settings when the world's overworld runs on the federal settings; otherwise null. */
    public static NoiseGeneratorSettings federalOverworldSettings(RegistryAccess registries) {
        var stems = registries.registry(Registries.LEVEL_STEM).orElse(null);
        if (stems == null) return null;
        LevelStem stem = stems.get(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator generator)) return null;
        Holder<NoiseGeneratorSettings> settings = generator.generatorSettings();
        return settings.is(NOISE_SETTINGS) ? settings.value() : null;
    }

    /**
     * Before the levels are made (the chunk generator sorts its features lazily, on the first decoration): a federal
     * overworld's biome source gets the federal biomes as extra possible biomes (BiomeSourceFederalMixin). An r1 world
     * is not touched.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        var registries = event.getServer().registryAccess();
        if (federalOverworldSettings(registries) == null) return;
        var biomes = registries.registryOrThrow(Registries.BIOME);
        List<Holder<net.minecraft.world.level.biome.Biome>> extra = new ArrayList<>();
        for (var key : com.antaurora.apofirstlight.registry.AflBiomes.FEDERAL) extra.add(biomes.getHolderOrThrow(key));
        var source = registries.registryOrThrow(Registries.LEVEL_STEM).getOrThrow(LevelStem.OVERWORLD).generator().getBiomeSource();
        ((FederalBiomeSourceAccess) source).apocalypse$setFederalBiomes(extra);
        ApocalypseFirstLight.LOGGER.info("[AFL federal C2] {} federal biomes added to the overworld's possible biomes", extra.size());
    }
}
