package com.antaurora.apofirstlight.worldgen.core;

/** Shared failure categories; system-specific details belong in the message. */
public enum GenerationFailureReason {
    INVALID_TERRAIN, UNKNOWN_TERRAIN, COLLISION, OUT_OF_BOUNDS, PROTECTED_CONTENT, MISSING_RESOURCE, VERSION_MISMATCH, BUDGET_EXCEEDED, INTERNAL_ERROR
}
