package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.registry.AflAttributes;
import com.antaurora.apofirstlight.survivor.SurvivorStats;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Survivor attribute hunger_rate: every food exhaustion the player takes (moving, fighting, healing, being sick) × the player's value. */
@Mixin(Player.class)
public abstract class PlayerHungerRateMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float afl$hungerRate(float exhaustion) {
        return exhaustion * (float)SurvivorStats.multiplier((Player)(Object)this, AflAttributes.HUNGER_RATE, 0);
    }
}
