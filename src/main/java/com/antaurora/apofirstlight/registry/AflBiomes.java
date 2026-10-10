package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

public final class AflBiomes {
    /** Kept registered (old worlds, later disaster content); no longer placed naturally since 2026-10-10. */
    public static final ResourceKey<Biome> FALLOUT_BARRENS = key("fallout_barrens");

    // Terrain V2 ecology V1 (2026-10-10, docs/worldgen/terrain_v2_ecology_v1.md): the natural biomes of the island, built
    // from vanilla trees and plants (data/apocalypse_firstlight/worldgen/biome/*.json, tools/terrain-v2-research/build_ecology_data.py)
    public static final ResourceKey<Biome> TEMPERATE_GRASSLAND = key("temperate_grassland");
    public static final ResourceKey<Biome> OAK_HICKORY_WOODLAND = key("oak_hickory_woodland");
    public static final ResourceKey<Biome> MIXED_MESOPHYTIC_FOREST = key("mixed_mesophytic_forest");
    public static final ResourceKey<Biome> RIDGE_OAK_FOREST = key("ridge_oak_forest");
    public static final ResourceKey<Biome> HEMLOCK_HOLLOW = key("hemlock_hollow");
    public static final ResourceKey<Biome> RIPARIAN_FOREST = key("riparian_forest");
    public static final ResourceKey<Biome> COASTAL_PINE_OAK_FOREST = key("coastal_pine_oak_forest");
    public static final ResourceKey<Biome> COASTAL_SANDY_CLEARING = key("coastal_sandy_clearing");
    public static final ResourceKey<Biome> COASTAL_SWAMP_FOREST = key("coastal_swamp_forest");
    public static final ResourceKey<Biome> TIDAL_MARSH = key("tidal_marsh");
    public static final ResourceKey<Biome> WET_PRAIRIE = key("wet_prairie");

    /** The natural biomes, in EcologyBiomes index order from GRASSLAND (index 2) on. */
    public static final List<ResourceKey<Biome>> ECOLOGY = List.of(TEMPERATE_GRASSLAND, OAK_HICKORY_WOODLAND,
            MIXED_MESOPHYTIC_FOREST, RIDGE_OAK_FOREST, HEMLOCK_HOLLOW, RIPARIAN_FOREST, COASTAL_PINE_OAK_FOREST,
            COASTAL_SANDY_CLEARING, COASTAL_SWAMP_FOREST, TIDAL_MARSH, WET_PRAIRIE);

    /** The natural biome for an EcologyBiomes land index (GRASSLAND .. WET_PRAIRIE). */
    public static ResourceKey<Biome> ecology(int index) {
        return ECOLOGY.get(index - EcologyBiomes.GRASSLAND);
    }

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, new ResourceLocation(ApocalypseFirstLight.MOD_ID, path));
    }

    private AflBiomes() {
    }
}
