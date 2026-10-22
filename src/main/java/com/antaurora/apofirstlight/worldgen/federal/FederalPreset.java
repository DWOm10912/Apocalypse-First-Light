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
 * The "AFL Federation" world preset (C2, 2026-10-14; docs/worldgen/federal_six_state_c2.md): an overworld on the noise
 * settings {@code apocalypse_firstlight:federal_experimental}, whose plan density functions carry
 * {@code "profile": "federal"} (the six-state FederalTerrainPlan). Data:
 * data/apocalypse_firstlight/worldgen/world_preset/federal_experimental.json, listed in the world-type button through
 * data/minecraft/tags/worldgen/world_preset/normal.json. The ids keep "experimental" from C2: a world's level.dat and
 * its version lock (profile afl_federal_experimental) name them, so they never change.
 *
 * <p>Since the user's decision of 2026-10-14 (section 12 of the C2 doc) this is the default for a NEW normal world:
 * the create-world screen opens on it (client.CreateWorldFederalDefaultMixin) and a dedicated server without a
 * level-type uses it (DedicatedServerFederalDefaultMixin). Existing worlds keep the generator in their level.dat (an r1
 * world stays r1, guarded by the version lock); superflat, minecraft:normal (r1) and other mods' types stay selectable.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FederalPreset {
    private FederalPreset() {}

    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "federal_experimental"));
    public static final ResourceKey<NoiseGeneratorSettings> NOISE_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "federal_experimental"));
    /**
     * U2 (2026-10-22, docs/worldgen/underground_geology_caves_v0_design.md section 20): the deep prototype, the same
     * federal plan on dimension_type overworld_deep (Y -256..319) with the planned caves instead of vanilla's. Preset
     * apocalypse_firstlight:federal_deep_proto, listed after the federal preset in the world-type button (#minecraft:normal; decide before merging whether it stays).
     */
    public static final ResourceKey<NoiseGeneratorSettings> DEEP_NOISE_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "federal_deep_proto"));

    /**
     * True for the create-world screen's own default (CreateWorldScreen#openFresh passes exactly minecraft:normal):
     * that one becomes the federal preset. A screen opened on any other type (re-create, another mod's default) is left.
     */
    public static boolean replacesDefault(java.util.Optional<ResourceKey<WorldPreset>> preset) {
        return preset.isPresent() && preset.get().equals(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL);
    }

    /**
     * server.properties level-type fallback: vanilla's minecraft:normal becomes the federal preset, anything else stays.
     * Not on the development GameTest server (GameTestMain runs the dedicated server on a fresh server.properties): its
     * test world stays r1, the terrain every GameTest was written and measured on (the wiring "template" test loads a
     * chunk 4000 blocks out and expects air there, which failed in a federal test world, measured 2026-10-14). Never true in production.
     */
    public static Object defaultLevelType(Object fallback) {
        if (net.minecraftforge.gametest.ForgeGameTestHooks.isGametestServer()) return fallback;
        return net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL.location().toString().equals(fallback)
                ? PRESET.location().toString() : fallback;
    }

    /** The overworld's noise settings when the world's overworld runs on the federal settings; otherwise null. */
    public static NoiseGeneratorSettings federalOverworldSettings(RegistryAccess registries) {
        var stems = registries.registry(Registries.LEVEL_STEM).orElse(null);
        if (stems == null) return null;
        LevelStem stem = stems.get(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator generator)) return null;
        Holder<NoiseGeneratorSettings> settings = generator.generatorSettings();
        return settings.is(NOISE_SETTINGS) || settings.is(DEEP_NOISE_SETTINGS) ? settings.value() : null;
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
