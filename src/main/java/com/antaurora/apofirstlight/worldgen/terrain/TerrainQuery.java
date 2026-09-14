package com.antaurora.apofirstlight.worldgen.terrain;

/**
 * Minimal future query boundary; WG-01 provides no worldgen adapter or implementation.
 * A future implementation binds its permitted source/backend explicitly; it must return
 * a non-null sample retaining the requested, non-null source. Insufficient legal data
 * returns UNKNOWN, never another source, fabricated absence or force-loaded chunks.
 * Backend/snapshot identity belongs to future context/adapters, not a Level parameter here.
 */
@FunctionalInterface
public interface TerrainQuery {
    TerrainSample sample(int x, int z, TerrainSource source);
}
