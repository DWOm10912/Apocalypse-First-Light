package com.antaurora.apofirstlight.worldgen.core;

/** Single operation outcome. NOT_OWNED is not an error; UNAVAILABLE never authorizes force-loading. */
public enum WriteOutcome {
    CHANGED, ALREADY_MATCHED, NOT_OWNED, PROTECTED, UNAVAILABLE, FAILED
}
