package com.antaurora.apofirstlight.world.biome;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Set;

public final class AflVanillaBiomePolicy {
    private static final Set<ResourceKey<Biome>> VANILLA_UNDERGROUND_BIOMES = Set.of(
            Biomes.DRIPSTONE_CAVES,
            Biomes.LUSH_CAVES,
            Biomes.DEEP_DARK
    );

    private AflVanillaBiomePolicy() {}

    /** The only Vanilla surface biome retained by AFL's Overworld policy. */
    public static boolean isAllowedSurfaceBiome(ResourceKey<Biome> key) {
        return Biomes.PLAINS.equals(key);
    }

    /** Cave selection is kept separate from the surface policy. */
    public static boolean isAllowedUndergroundBiome(ResourceKey<Biome> key) {
        return key != null && VANILLA_UNDERGROUND_BIOMES.contains(key);
    }

    /**
     * Runtime guard for the Overworld MultiNoise source.  Non-Vanilla AFL
     * biomes remain available, Plains remains available, and Vanilla cave
     * biomes remain available.  Every other Vanilla key is a disallowed
     * surface candidate, without maintaining a brittle hand-written list of
     * every surface biome.
     */
    public static boolean isDisabled(ResourceKey<Biome> key) {
        return key != null
                && "minecraft".equals(key.location().getNamespace())
                && !isAllowedSurfaceBiome(key)
                && !isAllowedUndergroundBiome(key);
    }
}
