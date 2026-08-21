package com.antaurora.apofirstlight.world.biome;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Set;

public final class AflVanillaBiomePolicy {
    public static final int DISABLED_COUNT = 51;
    public static final int ALLOWED_VANILLA_COUNT = 2;
    public static final Set<ResourceKey<Biome>> DISABLED_OVERWORLD_BIOMES = Set.of(
            Biomes.FLOWER_FOREST, Biomes.DARK_FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
            Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_TAIGA, Biomes.MANGROVE_SWAMP,
            Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.MUSHROOM_FIELDS, Biomes.DESERT,
            Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA, Biomes.JUNGLE,
            Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.BADLANDS, Biomes.WOODED_BADLANDS,
            Biomes.ERODED_BADLANDS, Biomes.FROZEN_PEAKS, Biomes.GROVE, Biomes.SNOWY_SLOPES,
            Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.FROZEN_RIVER, Biomes.SNOWY_BEACH,
            Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
            Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.WARM_OCEAN,
            Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.LUSH_CAVES, Biomes.DEEP_DARK,
            Biomes.BEACH, Biomes.STONY_SHORE, Biomes.RIVER,
            Biomes.SUNFLOWER_PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST,
            Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.SWAMP, Biomes.WINDSWEPT_HILLS,
            Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST,
            Biomes.STONY_PEAKS, Biomes.JAGGED_PEAKS
    );

    private AflVanillaBiomePolicy() {}

    public static boolean isDisabled(ResourceKey<Biome> key) {
        return key != null && "minecraft".equals(key.location().getNamespace())
                && DISABLED_OVERWORLD_BIOMES.contains(key);
    }

    /**
     * Surface selection must never use cave biomes.  Cave biomes are handled
     * separately by the OverworldBiomeBuilder underground branch.
     */
    public static boolean isAllowedSurfaceBiome(ResourceKey<Biome> key) {
        return !isDisabled(key) && !Biomes.DRIPSTONE_CAVES.equals(key);
    }

    /**
     * Keep the remaining vanilla cave ecology while applying the existing
     * AFL disable policy to the cave biomes that AFL does not retain.
     */
    public static boolean isAllowedUndergroundBiome(ResourceKey<Biome> key) {
        return !isDisabled(key) || Biomes.DRIPSTONE_CAVES.equals(key);
    }
}
