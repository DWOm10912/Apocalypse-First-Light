package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.registry.AflAttributes;
import com.antaurora.apofirstlight.survivor.SurvivorStats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Survivor attribute natural_regen: the health both natural-regeneration branches of FoodData#tick give (saturation
 * fast regen and the slow one) × the player's value. The food they spend is unchanged.
 */
@Mixin(FoodData.class)
public abstract class FoodDataSurvivorStatsMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"), require = 2)
    private void afl$naturalRegen(Player player, float amount) {
        player.heal(amount * (float)SurvivorStats.multiplier(player, AflAttributes.NATURAL_REGEN, 0));
    }
}
