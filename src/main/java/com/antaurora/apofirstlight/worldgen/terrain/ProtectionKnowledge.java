package com.antaurora.apofirstlight.worldgen.terrain;

/** Source-qualified knowledge, not a block scanner, spatial claim or write permission. */
public enum ProtectionKnowledge {
    KNOWN_CLEAR,
    KNOWN_PROTECTED,
    /** Includes sources that cannot establish whether protected content exists. */
    UNKNOWN
}
