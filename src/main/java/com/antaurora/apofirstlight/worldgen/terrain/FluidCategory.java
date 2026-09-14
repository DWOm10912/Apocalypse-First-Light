package com.antaurora.apofirstlight.worldgen.terrain;

/** Classification only. No registry/tag lookup or industrial-fluid mapping is performed. */
public enum FluidCategory {
    /** Proven absence only when the enclosing sample is VALID. */
    NONE,
    WATER,
    INDUSTRIAL_WASTE,
    OTHER_FLUID
}
