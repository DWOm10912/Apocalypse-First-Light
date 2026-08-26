package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/** Single-plan terrain sample cache with a deterministic hard cap. */
public final class RuralTerrainProbeCache implements RuralTerrainSource {
    public static final int MAX_UNIQUE_TERRAIN_PROBES_PER_START = 256;

    private final RuralTerrainSource source;
    private final Map<Long, RuralTerrainSampler.Sample> samples = new HashMap<>();
    private int uniqueProbes;
    private int cacheHits;
    private boolean budgetExceeded;

    public RuralTerrainProbeCache(RuralTerrainSource source) {
        this.source = source;
    }

    @Override
    public RuralTerrainSampler.Sample sample(int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        RuralTerrainSampler.Sample cached = samples.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        if (samples.size() >= MAX_UNIQUE_TERRAIN_PROBES_PER_START) {
            budgetExceeded = true;
            return new RuralTerrainSampler.Sample(0, false, 0, false);
        }
        RuralTerrainSampler.Sample result = source.sample(x, z);
        samples.put(key, result);
        uniqueProbes++;
        return result;
    }

    public int uniqueProbes() {
        return uniqueProbes;
    }

    public int cacheHits() {
        return cacheHits;
    }

    public boolean budgetExceeded() {
        return budgetExceeded;
    }
}
