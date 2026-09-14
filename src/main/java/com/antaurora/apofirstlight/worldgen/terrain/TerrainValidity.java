package com.antaurora.apofirstlight.worldgen.terrain;

/** Knowledge of the query result, not a generator's placement acceptance policy. */
public enum TerrainValidity {
    /** Query succeeded; present observations and explicit categories are trustworthy. */
    VALID,
    /** Legal data/source coverage is insufficient; does not mean clear or empty. */
    UNKNOWN,
    /** Query explicitly failed or has no valid terrain result under its contract. */
    INVALID
}
