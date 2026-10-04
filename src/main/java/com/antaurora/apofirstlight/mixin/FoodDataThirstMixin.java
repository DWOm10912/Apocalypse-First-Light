package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.temperature.PlayerTemperature;
import com.antaurora.apofirstlight.thirst.PlayerThirst;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Thirst V1 / Temperature V1: a dehydrated player, or one from the hypothermia / heat exhaustion stage on, does not
 * heal naturally. Both natural-regeneration branches of FoodData#tick ask
 * Player#isHurt first; answering "not hurt" skips them without spending saturation. Starvation is untouched.
 */
@Mixin(FoodData.class)
public abstract class FoodDataThirstMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z"))
    private boolean afl$dehydratedNoRegen(Player player) {
        return player.isHurt() && !PlayerThirst.blocksNaturalRegen(player) && !PlayerTemperature.blocksNaturalRegen(player);
    }
}
