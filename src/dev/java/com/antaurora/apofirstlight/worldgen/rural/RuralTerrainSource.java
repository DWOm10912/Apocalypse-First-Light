package com.antaurora.apofirstlight.worldgen.rural;

/** Read-only terrain sampling abstraction shared by natural planning paths. */
@FunctionalInterface
public interface RuralTerrainSource {
    RuralTerrainSampler.Sample sample(int x, int z);
}
