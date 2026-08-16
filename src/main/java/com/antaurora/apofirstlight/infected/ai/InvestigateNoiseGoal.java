package com.antaurora.apofirstlight.infected.ai;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.perception.InfectedHearingState;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class InvestigateNoiseGoal extends Goal {
    public static final double MOVE_SPEED = 1.0;
    public static final double ARRIVAL_DISTANCE = 2.5;
    public static final long WAIT_TICKS = 40L;
    public static final long MAX_LIFETIME = 200L;

    private final Zombie zombie;

    public InvestigateNoiseGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return canInvestigate();
    }

    @Override
    public boolean canContinueToUse() {
        return canInvestigate();
    }

    @Override
    public void start() {
        refreshPathIfNeeded();
        ApocalypseFirstLight.LOGGER.debug("[AFL HEARING] Zombie={} Investigating Pos={}", zombie.getId(), InfectedHearingState.lastHeardPosition(zombie));
    }

    @Override
    public void tick() {
        Vec3 position = InfectedHearingState.lastHeardPosition(zombie);
        if (position == null) {
            return;
        }
        if (zombie.position().distanceTo(position) <= ARRIVAL_DISTANCE) {
            long waitUntil = InfectedHearingState.waitUntil(zombie);
            if (waitUntil == 0L) {
                InfectedHearingState.setWaitUntil(zombie, zombie.level().getGameTime() + WAIT_TICKS);
            } else if (zombie.level().getGameTime() >= waitUntil) {
                clearState();
            }
            return;
        }
        refreshPathIfNeeded();
    }

    @Override
    public void stop() {
        if (InfectedHearingState.isValid(zombie) && zombie.getTarget() != null && zombie.getTarget().isAlive()) {
            return;
        }
        if (InfectedHearingState.isValid(zombie)) {
            clearState();
        }
    }

    private boolean canInvestigate() {
        if (!InfectedHearingState.isValid(zombie)) {
            return false;
        }
        if (zombie.getTarget() != null && zombie.getTarget().isAlive()) {
            return false;
        }
        long now = zombie.level().getGameTime();
        return now - InfectedHearingState.investigateStart(zombie) <= MAX_LIFETIME;
    }

    private void refreshPathIfNeeded() {
        Vec3 position = InfectedHearingState.lastHeardPosition(zombie);
        if (position == null) {
            return;
        }
        long now = zombie.level().getGameTime();
        if (InfectedHearingState.shouldRefreshPath(zombie, position, now)) {
            zombie.getNavigation().moveTo(position.x(), position.y(), position.z(), MOVE_SPEED);
            InfectedHearingState.markPathRefresh(zombie, position, now);
        }
    }

    private void clearState() {
        zombie.getNavigation().stop();
        InfectedHearingState.clear(zombie);
        ApocalypseFirstLight.LOGGER.debug("[AFL HEARING] Zombie={} Arrived/Cleared", zombie.getId());
    }
}
