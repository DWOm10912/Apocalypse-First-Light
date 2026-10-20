package com.antaurora.apofirstlight.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Survival and health V1a: vanilla's food regeneration is off; health/PlayerHealth heals instead (its delay after damage,
 * rates, the food / thirst / temperature conditions and natural_regen in one place). Both natural-regeneration branches of
 * FoodData#tick ask Player#isHurt first; answering "not hurt" skips them without spending saturation or food.
 * Starvation is untouched. (Before V1a this mixin only blocked them while dehydrated or hypothermic / overheated.)
 */
@Mixin(FoodData.class)
public abstract class FoodDataThirstMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z"))
    private boolean afl$noVanillaRegen(Player player) {
        return false;
    }
}
