package com.antaurora.apofirstlight.infected.breach;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
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

    private final Zombie zombie;
    private BlockPos breachPos;
    private int breakTicks;
    private int requiredTicks;
    private int lastCrackStage = -1;

    public InfectedBreachGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        BlockPos obstacle = findDirectObstacle();
        if (obstacle == null) {
            return false;
        }
        breachPos = obstacle;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos directObstacle = findDirectObstacle();
        return breachPos != null && isBreachAuthorized() && InfectedBreachRules.canBreak(zombie.level().getBlockState(breachPos))
                && breachPos.equals(directObstacle);
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
            zombie.level().destroyBlock(breachPos, false, zombie);
            ApocalypseFirstLight.LOGGER.debug("[AFL BREACH] Zombie={} Broken pos={}", zombie.getId(), breachPos);
            InfectedEntrySeekingSystem.onEntryBreachCompleted(zombie, breachPos);
            breachPos = null;
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
        }
        breachPos = null;
        breakTicks = 0;
        lastCrackStage = -1;
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
}
