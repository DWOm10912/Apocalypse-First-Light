package com.antaurora.apofirstlight.worldgen.core;

/** Placement scope: APPLIED_SLICE is local completion, never whole-plan COMPLETE. */
public enum PlacementStatus {
    REJECTED_BEFORE_WRITE, APPLIED_SLICE, COMPLETE, PARTIAL_COMMIT
}
