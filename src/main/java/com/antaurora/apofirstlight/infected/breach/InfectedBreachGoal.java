package com.antaurora.apofirstlight.infected.breach;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.ai.InfectedAiDiagnostics;
import com.antaurora.apofirstlight.infected.ai.InfectedAiScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.EnumSet;

/** Breaks only a directly blocking, explicitly approved block during visual pursuit. */
public final class InfectedBreachGoal extends Goal {
    private static final double MAX_OBSTACLE_DISTANCE = 2.0;
    public static final int SCAN_COOLDOWN_MIN_TICKS = 10;
    public static final int SCAN_COOLDOWN_MAX_TICKS = 20;
    public static final int FAILED_SCAN_COOLDOWN_MIN_TICKS = 20;
    public static final int FAILED_SCAN_COOLDOWN_MAX_TICKS = 40;

    private final Zombie zombie;
    private BlockPos breachPos;
    private int breakTicks;
    private int requiredTicks;
    private int lastCrackStage = -1;
    private long nextScanTick = Long.MIN_VALUE;
    private long diagnosticScanCount;

    public InfectedBreachGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        long now = zombie.level().getGameTime();
        if (nextScanTick == Long.MIN_VALUE) {
            nextScanTick = now + InfectedAiScheduler.stableOffset(zombie, SCAN_COOLDOWN_MAX_TICKS + 1);
        }
        if (now < nextScanTick) {
            return false;
        }
        scheduleNextScan(now, SCAN_COOLDOWN_MIN_TICKS, SCAN_COOLDOWN_MAX_TICKS);
        diagnosticScanCount++;
        if (zombie.level() instanceof ServerLevel level) {
            InfectedAiDiagnostics.obstacleScan(level);
        }
        BlockPos obstacle = findDirectObstacle();
        if (obstacle == null) {
            scheduleNextScan(now, FAILED_SCAN_COOLDOWN_MIN_TICKS, FAILED_SCAN_COOLDOWN_MAX_TICKS);
            return false;
        }
        if (!InfectedBreakerClaims.tryClaim(zombie, obstacle)) {
            return false;
        }
        breachPos = obstacle;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return breachPos != null && isBreachAuthorized()
                && isDirectBlockingObstacle(breachPos, zombie.level().getBlockState(breachPos))
                && zombie.position().distanceToSqr(Vec3.atCenterOf(breachPos)) <= 9.0
                && InfectedBreakerClaims.ownsAndRefreshes(zombie, breachPos);
    }

    @Override
    public void start() {
        breakTicks = 0;
        lastCrackStage = -1;
        requiredTicks = InfectedBreachRules.breakTicks(zombie.level().getBlockState(breachPos));
        ApocalypseFirstLight.LOGGER.debug("[AFL BREACH] Zombie={} StartBreaking block={} pos={}",
                zombie.getId(), zombie.level().getBlockState(breachPos).getBlock(), breachPos);
    }

    @Override
    public void tick() {
        if (breachPos == null || !isBreachAuthorized()) {
            return;
        }
        if (!InfectedBreachRules.canBreak(zombie.level().getBlockState(breachPos))) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(breachPos);
        zombie.getLookControl().setLookAt(center.x(), center.y(), center.z(), 30.0F, 30.0F);
        if (breakTicks % 10 == 0) {
            zombie.swing(InteractionHand.MAIN_HAND);
        }
        breakTicks++;
        int crackStage = Mth.clamp((breakTicks * 10) / requiredTicks, 0, 9);
        if (crackStage != lastCrackStage) {
            zombie.level().destroyBlockProgress(zombie.getId(), breachPos, crackStage);
            lastCrackStage = crackStage;
        }
        if (breakTicks >= requiredTicks) {
            zombie.level().destroyBlockProgress(zombie.getId(), breachPos, -1);
            BlockPos completed = breachPos;
            zombie.level().destroyBlock(completed, false, zombie);
            ApocalypseFirstLight.LOGGER.debug("[AFL BREACH] Zombie={} Broken pos={}", zombie.getId(), completed);
            InfectedEntrySeekingSystem.onEntryBreachCompleted(zombie, completed);
            InfectedBreakerClaims.release(zombie, completed);
            breachPos = null;
            if (zombie.level() instanceof ServerLevel level) {
                InfectedAiDiagnostics.pathAttempt(level);
            }
            zombie.getNavigation().recomputePath();
        }
    }

    @Override
    public void stop() {
        if (breachPos != null && lastCrackStage >= 0) {
            zombie.level().destroyBlockProgress(zombie.getId(), breachPos, -1);
        }
        if (breachPos != null) {
            ApocalypseFirstLight.LOGGER.debug("[AFL BREACH] Zombie={} Cancel pos={}", zombie.getId(), breachPos);
            InfectedBreakerClaims.release(zombie, breachPos);
        }
        breachPos = null;
        breakTicks = 0;
        lastCrackStage = -1;
        scheduleNextScan(zombie.level().getGameTime(), FAILED_SCAN_COOLDOWN_MIN_TICKS,
                FAILED_SCAN_COOLDOWN_MAX_TICKS);
    }

    private BlockPos findDirectObstacle() {
        InfectedBreachContext context = InfectedBreachAuthorization.getBreachContext(zombie);
        if (context == null || !ForgeEventFactory.getMobGriefingEvent(zombie.level(), zombie)) {
            return null;
        }
        BlockPos explicit = InfectedEntrySeekingSystem.explicitObstacle(zombie, context);
        if (explicit != null && isDirectBlockingObstacle(explicit, zombie.level().getBlockState(explicit))) return explicit;
        Vec3 horizontal = context.targetPosition().subtract(zombie.position());
        horizontal = new Vec3(horizontal.x(), 0.0, horizontal.z());
        if (horizontal.lengthSqr() < 0.001) {
            return null;
        }
        Vec3 direction = horizontal.normalize();
        for (double distance = 0.75; distance <= MAX_OBSTACLE_DISTANCE; distance += 0.5) {
            BlockPos base = BlockPos.containing(zombie.position().add(direction.scale(distance)));
            for (int yOffset = 0; yOffset <= 1; yOffset++) {
                BlockPos pos = base.above(yOffset);
                BlockState state = zombie.level().getBlockState(pos);
                if (isDirectBlockingObstacle(pos, state)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private boolean isDirectBlockingObstacle(BlockPos pos, BlockState state) {
        if (!InfectedBreachRules.canBreak(state)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
            return false;
        }
        return !state.getCollisionShape(zombie.level(), pos).isEmpty();
    }

    private boolean isBreachAuthorized() {
        return InfectedBreachAuthorization.getBreachContext(zombie) != null
                && ForgeEventFactory.getMobGriefingEvent(zombie.level(), zombie);
    }

    private void scheduleNextScan(long now, int minimumTicks, int maximumTicks) {
        nextScanTick = now + InfectedAiScheduler.staggeredDelay(zombie, minimumTicks, maximumTicks);
    }

    /** DEV/GameTest-local evidence that does not depend on shared level diagnostics. */
    public long diagnosticScanCount() {
        return diagnosticScanCount;
    }
}
