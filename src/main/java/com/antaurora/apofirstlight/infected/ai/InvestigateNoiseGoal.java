package com.antaurora.apofirstlight.infected.ai;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.perception.InfectedHearingState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class InvestigateNoiseGoal extends Goal {
    public static final double MOVE_SPEED = 1.0;
    public static final double ARRIVAL_DISTANCE = 2.5;
    public static final long WAIT_TICKS = 40L;
    public static final long MAX_LIFETIME = 200L;
    public static final double SEARCH_RADIUS = 8.0;
    public static final int SEARCH_POINT_COUNT = 3;
    public static final long SEARCH_POINT_WAIT_TICKS = 20L;
    public static final long MAX_SEARCH_LIFETIME = 160L;
    public static final double SEARCH_MOVE_SPEED = 1.0;
    public static final double SEARCH_ARRIVAL_DISTANCE = 2.5;
    private static final int SEARCH_ATTEMPTS_PER_POINT = 8;

    private final Zombie zombie;
    private Vec3 activeDestination;
    private boolean reportedCanUse;

    public InvestigateNoiseGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        boolean result = canInvestigate();
        if (result && !reportedCanUse) {
            reportedCanUse = true;
            ApocalypseFirstLight.LOGGER.debug("[AFL HEARING DEBUG] Zombie={} InvestigateGoal canUse=true", zombie.getId());
        } else if (!result) {
            reportedCanUse = false;
        }
        return result;
    }
    @Override public boolean canContinueToUse() { return canInvestigate(); }

    @Override
    public void start() {
        activeDestination = null;
        Vec3 center = InfectedHearingState.lastHeardPosition(zombie);
        if (center != null) {
            zombie.getNavigation().moveTo(center.x(), center.y(), center.z(), MOVE_SPEED);
            InfectedHearingState.markPathRefresh(zombie, center, zombie.level().getGameTime());
        }
        ApocalypseFirstLight.LOGGER.debug("[AFL HEARING DEBUG] Zombie={} InvestigateGoal start pos={}", zombie.getId(), center);
        ApocalypseFirstLight.LOGGER.debug("[AFL HEARING] Zombie={} Investigating Pos={}", zombie.getId(), InfectedHearingState.lastHeardPosition(zombie));
    }

    @Override
    public void tick() {
        long now = zombie.level().getGameTime();
        if (InfectedHearingState.phase(zombie) == InfectedHearingState.Phase.INVESTIGATING) {
            tickInvestigation(now);
        } else {
            tickSearch(now);
        }
    }

    @Override
    public void stop() {
        if (zombie.getTarget() != null && zombie.getTarget().isAlive()) return;
        if (InfectedHearingState.isValid(zombie)) clearState("Stopped");
    }

    private void tickInvestigation(long now) {
        Vec3 center = InfectedHearingState.lastHeardPosition(zombie);
        if (center == null) return;
        if (zombie.position().distanceTo(center) <= ARRIVAL_DISTANCE) {
            long waitUntil = InfectedHearingState.waitUntil(zombie);
            if (waitUntil == 0L) {
                InfectedHearingState.setWaitUntil(zombie, now + WAIT_TICKS);
            } else if (now >= waitUntil) {
                List<Vec3> points = generateSearchPoints(center);
                if (points.isEmpty()) {
                    clearState("NoSearchPoints");
                } else {
                    InfectedHearingState.beginSearching(zombie, now, points);
                    ApocalypseFirstLight.LOGGER.debug("[AFL SEARCH] Zombie={} Enter Search Center=({}, {}, {})", zombie.getId(), center.x(), center.y(), center.z());
                    moveToCurrentSearchPoint();
                }
            }
        } else {
            refreshInvestigationPath();
        }
    }

    private void tickSearch(long now) {
        if (now - InfectedHearingState.searchStart(zombie) >= MAX_SEARCH_LIFETIME) {
            clearState("Finished");
            return;
        }
        List<Vec3> points = InfectedHearingState.searchPoints(zombie);
        int index = InfectedHearingState.searchIndex(zombie);
        if (index >= points.size()) { clearState("Finished"); return; }
        Vec3 point = points.get(index);
        if (activeDestination == null || activeDestination.distanceToSqr(point) > 0.01) moveToCurrentSearchPoint();
        if (zombie.position().distanceTo(point) <= SEARCH_ARRIVAL_DISTANCE) {
            long waitUntil = InfectedHearingState.waitUntil(zombie);
            if (waitUntil == 0L) {
                InfectedHearingState.setWaitUntil(zombie, now + SEARCH_POINT_WAIT_TICKS);
            } else if (now >= waitUntil) {
                InfectedHearingState.setSearchIndex(zombie, index + 1);
                activeDestination = null;
                if (index + 1 >= points.size()) clearState("Finished"); else moveToCurrentSearchPoint();
            }
        }
    }

    private void moveToCurrentSearchPoint() {
        List<Vec3> points = InfectedHearingState.searchPoints(zombie);
        int index = InfectedHearingState.searchIndex(zombie);
        if (index >= points.size()) return;
        Vec3 point = points.get(index);
        if (zombie.getNavigation().createPath(BlockPos.containing(point), 0) == null) {
            InfectedHearingState.setSearchIndex(zombie, index + 1);
            activeDestination = null;
            return;
        }
        zombie.getNavigation().moveTo(point.x(), point.y(), point.z(), SEARCH_MOVE_SPEED);
        activeDestination = point;
        ApocalypseFirstLight.LOGGER.debug("[AFL SEARCH] Zombie={} Point={} Pos=({}, {}, {})", zombie.getId(), index + 1, point.x(), point.y(), point.z());
    }

    private void refreshInvestigationPath() {
        Vec3 center = InfectedHearingState.lastHeardPosition(zombie);
        if (center == null) return;
        long now = zombie.level().getGameTime();
        if (InfectedHearingState.shouldRefreshPath(zombie, center, now)) {
            zombie.getNavigation().moveTo(center.x(), center.y(), center.z(), MOVE_SPEED);
            InfectedHearingState.markPathRefresh(zombie, center, now);
        }
    }

    private List<Vec3> generateSearchPoints(Vec3 center) {
        List<Vec3> points = new ArrayList<>();
        for (int pointIndex = 0; pointIndex < SEARCH_POINT_COUNT; pointIndex++) {
            for (int attempt = 0; attempt < SEARCH_ATTEMPTS_PER_POINT; attempt++) {
                double angle = zombie.getRandom().nextDouble() * Math.PI * 2.0;
                double distance = 2.0 + zombie.getRandom().nextDouble() * (SEARCH_RADIUS - 2.0);
                int x = (int) Math.floor(center.x() + Math.cos(angle) * distance);
                int z = (int) Math.floor(center.z() + Math.sin(angle) * distance);
                int y = zombie.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos blockPos = new BlockPos(x, y, z);
                if (!zombie.level().getFluidState(blockPos).isEmpty()
                        || !zombie.level().getFluidState(blockPos.below()).isEmpty()
                        || !zombie.level().getBlockState(blockPos).getCollisionShape(zombie.level(), blockPos).isEmpty()
                        || zombie.getNavigation().createPath(blockPos, 0) == null) continue;
                Vec3 point = Vec3.atBottomCenterOf(blockPos);
                if (points.stream().noneMatch(existing -> existing.distanceToSqr(point) < 4.0)) { points.add(point); break; }
            }
        }
        return points;
    }

    private boolean canInvestigate() {
        if (!InfectedHearingState.isValid(zombie) || (zombie.getTarget() != null && zombie.getTarget().isAlive())) return false;
        long now = zombie.level().getGameTime();
        if (InfectedHearingState.phase(zombie) == InfectedHearingState.Phase.SEARCHING) return now - InfectedHearingState.searchStart(zombie) < MAX_SEARCH_LIFETIME;
        return now - InfectedHearingState.investigateStart(zombie) <= MAX_LIFETIME;
    }

    private void clearState(String reason) {
        zombie.getNavigation().stop();
        InfectedHearingState.clear(zombie);
        ApocalypseFirstLight.LOGGER.debug("[AFL SEARCH] Zombie={} {}", zombie.getId(), reason);
    }
}
