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

    // C2 federal experimental biomes (2026-10-14, docs/worldgen/federal_six_state_c2.md): placed only in worlds made with
    // the "AFL Federation (experimental)" preset; possible biomes of that world's source only (BiomeSourceFederalMixin),
    // never of an r1 world (ECOLOGY and the TerraBlender region stay as they were)
    public static final ResourceKey<Biome> MONTANE_CONIFER_FOREST = key("montane_conifer_forest");
    public static final ResourceKey<Biome> ALPINE_MEADOW = key("alpine_meadow");
    public static final ResourceKey<Biome> ALPINE_PEAKS = key("alpine_peaks");
    public static final ResourceKey<Biome> LAKESHORE_MIXED_FOREST = key("lakeshore_mixed_forest");
    public static final ResourceKey<Biome> LAKE_FEN = key("lake_fen");
    public static final ResourceKey<Biome> FRESHWATER_LAKE = key("freshwater_lake");
    public static final ResourceKey<Biome> PLATEAU_STEPPE = key("plateau_steppe");
    public static final ResourceKey<Biome> CANYON_BADLANDS = key("canyon_badlands");

    /** The federal biomes, in FederalEcologyBiomes index order from MONTANE_CONIFER (index 13) on. */
    public static final List<ResourceKey<Biome>> FEDERAL = List.of(MONTANE_CONIFER_FOREST, ALPINE_MEADOW, ALPINE_PEAKS,
            LAKESHORE_MIXED_FOREST, LAKE_FEN, FRESHWATER_LAKE, PLATEAU_STEPPE, CANYON_BADLANDS);

    /** The natural biome for an EcologyBiomes land index (GRASSLAND .. WET_PRAIRIE, then the federal biomes). */
    public static ResourceKey<Biome> ecology(int index) {
        int k = index - EcologyBiomes.GRASSLAND;
        return k < ECOLOGY.size() ? ECOLOGY.get(k) : FEDERAL.get(k - ECOLOGY.size());
    }

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, new ResourceLocation(ApocalypseFirstLight.MOD_ID, path));
    }

    private AflBiomes() {
    }
}
