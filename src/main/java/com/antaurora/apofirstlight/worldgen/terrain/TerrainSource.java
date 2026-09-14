package com.antaurora.apofirstlight.worldgen.terrain;

/** Explicit query provenance; no automatic source selection or fallback. */
public enum TerrainSource {
    /** Noise/base terrain, not the actual decorated world's block state. */
    NOISE_PRE_DECORATION,
    /** Explicit frozen/reconstructible planning source; never implicit live-world fallback. */
    STRUCTURE_PLANNING,
    /** Pre-construction snapshot within the lifecycle's legally writable region. */
    WRITABLE_PRECOMMIT,
    /** Current state in legally readable regions; never force-load adjacent chunks. */
    CURRENT_POST_FEATURE
}
