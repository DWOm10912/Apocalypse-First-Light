package com.antaurora.apofirstlight.worldgen.core;

/** Finite operation limit. Consumption and enforcement belong to the caller. */
public record QueryBudget(int maxOperations) {
    public QueryBudget {
        if (maxOperations < 0) throw new IllegalArgumentException("Negative operation budget");
    }
}
