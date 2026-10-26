package com.antaurora.apofirstlight.world.biome;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;

/**
 * Biome lookups while vanilla SurfaceSystem runs one chunk (deep world performance, design 23.5). TerraBlender's
 * namespaced surface rule asks for the biome of every stone cell, so each lookup is paid once per block.
 * <ul>
 *   <li><b>Uniform corners:</b> {@code BiomeManager#getBiome} hashes the 8 quarts around a block (x−2 &gt;&gt; 2 …
 *   +1 on each axis) and returns the nearest one's biome. When all 8 are the same holder that holder is the answer
 *   whichever corner wins, so the hashing is skipped; the 4×4×4 blocks that share the corners share the check. Mixed
 *   corners fall back to vanilla.</li>
 *   <li><b>Uncached noise biomes:</b> corners in neighbours not yet at BIOMES are sampled from the climate
 *   ({@code WorldGenRegion#getUncachedNoiseBiome}); that depends only on the level and the quart, so it is kept for the
 *   pass.</li>
 * </ul>
 * Both return exactly what vanilla returns.
 */
public final class SurfaceBiomeCache {
    private static final ThreadLocal<Cache> CURRENT = new ThreadLocal<>();
    /** Marks corners that are not all the same biome. */
    private static final Object MIXED = new Object();

    private SurfaceBiomeCache() {
    }

    public static void begin() {
        CURRENT.set(new Cache());
    }

    public static void end() {
        CURRENT.remove();
    }

    /** The biome when the 8 corner quarts from (qx, qy, qz) all agree, else null (also outside a surface pass). */
    @SuppressWarnings("unchecked")
    public static Holder<Biome> uniformCorners(BiomeManager manager, BiomeManager.NoiseBiomeSource source, int qx, int qy, int qz) {
        Cache c = CURRENT.get();
        if (c == null) return null;
        // one manager per pass; another one (never seen in SurfaceSystem) goes the vanilla way
        if (c.manager == null) c.manager = manager;
        else if (c.manager != manager) return null;
        long key = BlockPos.asLong(qx, qy, qz);
        Object hit = c.corners.get(key);
        if (hit == null) {
            Holder<Biome> first = source.getNoiseBiome(qx, qy, qz);
            hit = first;
            for (int k = 1; k < 8 && hit != MIXED; k++)
                if (source.getNoiseBiome(qx + (k >> 2 & 1), qy + (k >> 1 & 1), qz + (k & 1)) != first) hit = MIXED;
            c.corners.put(key, hit);
        }
        return hit == MIXED ? null : (Holder<Biome>) hit;
    }

    public static Holder<Biome> uncached(ServerLevel level, int qx, int qy, int qz) {
        Cache c = CURRENT.get();
        return c == null || c.level != level ? null : c.uncached.get(BlockPos.asLong(qx, qy, qz));
    }

    public static void putUncached(ServerLevel level, int qx, int qy, int qz, Holder<Biome> biome) {
        Cache c = CURRENT.get();
        if (c == null || biome == null) return;
        // one level per pass; a pass left open by an exception never serves another level
        if (c.level == null) c.level = level;
        if (c.level == level) c.uncached.put(BlockPos.asLong(qx, qy, qz), biome);
    }

    private static final class Cache {
        final Long2ObjectOpenHashMap<Holder<Biome>> uncached = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<Object> corners = new Long2ObjectOpenHashMap<>();
        ServerLevel level;
        BiomeManager manager;
    }
}
