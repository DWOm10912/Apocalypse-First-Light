package com.antaurora.apofirstlight.terrainsmooth;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * Terrain Smooth data of one loaded chunk on one side, held by the LevelChunk itself (SmoothChunkHolder, so it goes away
 * with the chunk): the natural surface of its 256 columns and the slope geometry of the blocks collision and selection
 * asked for. Surfaces are written on the side's main thread only (chunk load, block change) and also read by chunk-builder
 * threads (plain int reads); the geometry cache is used on the main thread only. Data made under another
 * TerrainSmooth.Side#epoch (the feature switched, a refresh) is stale as a whole and made again.
 */
public final class SmoothChunkData {
    /** the side's epoch this data belongs to */
    final int epoch;
    /** y of each column's natural surface block ([z * 16 + x], chunk-local), or SmoothRules.NO_SURFACE */
    final int[] surface = new int[256];
    private Int2LongOpenHashMap geometry;

    SmoothChunkData(int epoch) { this.epoch = epoch; }

    static int index(int x, int z) { return (z & 15) << 4 | (x & 15); }

    /** chunk-local key: x 4 bits, z 4 bits, y the rest (any build height) */
    static int key(int x, int y, int z) { return y << 8 | (z & 15) << 4 | (x & 15); }

    public int surface(int x, int z) { return surface[index(x, z)]; }

    /** Cached geometry of block key, or Long.MIN_VALUE when not cached (main thread). */
    long cached(int key) { return geometry == null ? Long.MIN_VALUE : geometry.get(key); }

    void store(int key, long g) {
        if (geometry == null) {
            geometry = new Int2LongOpenHashMap(64);
            geometry.defaultReturnValue(Long.MIN_VALUE);
        }
        if (geometry.size() >= 4096) geometry.clear();   // 256 tops per chunk; far more means unusual queries: start again
        geometry.put(key, g);
    }

    void clearGeometry() {
        if (geometry != null) geometry.clear();
        if (tops != null) tops.clear();
    }

    /** R2b: the smooth tops (SmoothTop#top) of the blocks asked for, by block key (main thread, cleared with the geometry). */
    private it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<float[]> tops;

    float[] top(int key) { return tops == null ? null : tops.get(key); }

    void storeTop(int key, float[] top) {
        if (tops == null) tops = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(64);
        if (tops.size() >= 4096) tops.clear();
        tops.put(key, top);
    }

    int cachedCount() { return geometry == null ? 0 : geometry.size(); }
}
