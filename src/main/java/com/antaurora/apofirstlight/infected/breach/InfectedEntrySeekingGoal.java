package com.antaurora.apofirstlight.infected.breach;

import com.antaurora.apofirstlight.infected.ai.InfectedAiDiagnostics;
import com.antaurora.apofirstlight.infected.ai.InfectedAiScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class InfectedEntrySeekingGoal extends Goal {
    public static final int NAVIGATION_REFRESH_TICKS = 20;
    public static final int FAILED_PATH_COOLDOWN_MIN_TICKS = 30;
    public static final int FAILED_PATH_COOLDOWN_MAX_TICKS = 40;

    private final Zombie zombie;
    private long nextNavigationAt;

    public InfectedEntrySeekingGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        InfectedBreachContext context = InfectedBreachAuthorization.getBreachContext(zombie);
        return context != null && InfectedEntrySeekingSystem.prepare(zombie, context);
    }

    @Override
    public boolean canContinueToUse() {
        InfectedBreachContext context = InfectedBreachAuthorization.getBreachContext(zombie);
        return context != null && InfectedEntrySeekingSystem.isActive(zombie, context);
    }

    @Override
    public void start() {
        nextNavigationAt = zombie.level().getGameTime();
        navigateIfDue(false);
    }

    @Override
    public void tick() {
        Vec3 approach = InfectedEntrySeekingSystem.approach(zombie);
        Vec3 interior = InfectedEntrySeekingSystem.interior(zombie);
        if (approach == null || interior == null) {
            return;
        }
        if (InfectedEntrySeekingSystem.isOpenOrGone(zombie)) {
            if (zombie.position().distanceToSqr(interior) <= 4.0) {
                InfectedEntrySeekingSystem.clear(zombie, "PassedEntry");
                return;
            }
            navigateIfDue(true);
        } else if (zombie.position().distanceToSqr(approach) > 4.0) {
            navigateIfDue(false);
        }
    }

    private void navigateIfDue(boolean useInterior) {
        long now = zombie.level().getGameTime();
        if (now < nextNavigationAt) {
            return;
        }
        Vec3 destination = useInterior ? InfectedEntrySeekingSystem.interior(zombie)
                : InfectedEntrySeekingSystem.approach(zombie);
        if (destination == null) {
            return;
        }
        if (zombie.level() instanceof ServerLevel level) {
            InfectedAiDiagnostics.pathAttempt(level);
        }
        boolean moved = zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
        if (moved) {
            nextNavigationAt = now + NAVIGATION_REFRESH_TICKS;
        } else {
            if (zombie.level() instanceof ServerLevel level) {
                InfectedAiDiagnostics.failedPathRetry(level);
            }
            nextNavigationAt = now + InfectedAiScheduler.staggeredDelay(zombie,
                    FAILED_PATH_COOLDOWN_MIN_TICKS, FAILED_PATH_COOLDOWN_MAX_TICKS);
        }
    }
}
