package com.antaurora.apofirstlight.infected.ai;

import com.antaurora.apofirstlight.infected.vision.InfectedVisionSystem;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;

import java.util.EnumSet;

public final class AflPlayerTargetGoal extends Goal {
    private final Zombie zombie;

    public AflPlayerTargetGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return zombie.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return zombie.getTarget() == null || InfectedVisionSystem.isManagingConfirmedTarget(zombie);
    }

    @Override
    public void tick() {
        InfectedVisionSystem.tick(zombie);
    }
}
