package com.antaurora.apofirstlight.fluid;

import net.minecraft.world.level.Level;

public final class FluidPortTransferBudget {
    public static final int STANDARD_FLUID_TRANSFER_RATE_MB_PER_TICK = 25;

    private final int maximumPerTick;
    private long lastGameTime = Long.MIN_VALUE;
    private int transferredThisTick;

    public FluidPortTransferBudget() {
        this(STANDARD_FLUID_TRANSFER_RATE_MB_PER_TICK);
    }

    public FluidPortTransferBudget(int maximumPerTick) {
        this.maximumPerTick = Math.max(0, maximumPerTick);
    }

    public int limit(Level level, int requestedAmount) {
        resetForCurrentTick(level);
        int remaining = Math.max(0, maximumPerTick - transferredThisTick);
        return Math.min(Math.max(0, requestedAmount), remaining);
    }

    public void record(Level level, int transferredAmount) {
        resetForCurrentTick(level);
        transferredThisTick = Math.min(maximumPerTick,
                transferredThisTick + Math.max(0, transferredAmount));
    }

    private void resetForCurrentTick(Level level) {
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (lastGameTime != gameTime) {
            lastGameTime = gameTime;
            transferredThisTick = 0;
        }
    }
}
