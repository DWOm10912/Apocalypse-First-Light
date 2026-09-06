package com.antaurora.apofirstlight.infected.ai;

import net.minecraft.world.entity.Entity;

/** Deterministic per-entity offsets used to spread expensive AI work across server ticks. */
public final class InfectedAiScheduler {
    private InfectedAiScheduler() {
    }

    public static int stableOffset(Entity entity, int slots) {
        if (slots <= 1) {
            return 0;
        }
        return Math.floorMod(entity.getUUID().hashCode(), slots);
    }

    public static long staggeredDelay(Entity entity, int minimumTicks, int maximumTicks) {
        if (maximumTicks < minimumTicks) {
            throw new IllegalArgumentException("maximumTicks must be >= minimumTicks");
        }
        return minimumTicks + stableOffset(entity, maximumTicks - minimumTicks + 1);
    }
}
