package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.registry.AflBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Resolves radiation tendency from the surface biome of an X/Z column. */
public final class BiomeRadiationResolver {
    private static final int CACHE_LIMIT = 4096;
    private static final Map<ServerLevel, LinkedHashMap<Long, Resolution>> CACHE = new WeakHashMap<>();

    private BiomeRadiationResolver() {
    }

    public static Resolution resolve(ServerLevel level, int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return new Resolution(level.getMinBuildHeight(), null, BiomeRadiationProfile.UNKNOWN);
        }
        synchronized (CACHE) {
            LinkedHashMap<Long, Resolution> levelCache = CACHE.computeIfAbsent(level,
                    ignored -> new LinkedHashMap<>(256, 0.75F, true));
            Resolution cached = levelCache.get(key);
            if (cached != null) return cached;
        }

        // Runtime sampling must never turn a distant/unloaded position into a chunk
        // generation request. Player positions are normally already loaded; callers
        // outside that guarantee receive the raw/unknown fallback instead.
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            return new Resolution(level.getMinBuildHeight(), null, BiomeRadiationProfile.UNKNOWN);
        }

        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos surface = new BlockPos(x, Math.max(level.getMinBuildHeight(), surfaceY - 1), z);
        var biome = level.getBiome(surface);
        BiomeRadiationProfile profile = biome.is(AflBiomes.SCORCHED_LANDS)
                ? BiomeRadiationProfile.EXTREME
                : biome.is(AflBiomes.FALLOUT_BARRENS)
                ? BiomeRadiationProfile.HEAVY_FALLOUT
                : biome.is(AflBiomes.IRRADIATED_WOODLAND)
                ? BiomeRadiationProfile.IRRADIATED
                : biome.is(net.minecraft.world.level.biome.Biomes.PLAINS)
                ? BiomeRadiationProfile.SAFE
                : BiomeRadiationProfile.UNKNOWN;
        ResourceLocation biomeId = biome.unwrapKey().map(keyRef -> keyRef.location()).orElse(null);
        Resolution resolution = new Resolution(surfaceY, biomeId, profile);
        synchronized (CACHE) {
            LinkedHashMap<Long, Resolution> levelCache = CACHE.computeIfAbsent(level,
                    ignored -> new LinkedHashMap<>(256, 0.75F, true));
            levelCache.put(key, resolution);
            while (levelCache.size() > CACHE_LIMIT) levelCache.remove(levelCache.keySet().iterator().next());
        }
        return resolution;
    }

    public record Resolution(int surfaceY, ResourceLocation biomeId, BiomeRadiationProfile profile) {
    }
}
