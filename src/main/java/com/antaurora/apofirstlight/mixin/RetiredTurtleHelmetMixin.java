package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.VanillaRetirement;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla Survival Rework V1: a retired turtle shell gives no water breathing. */
@Mixin(Player.class)
public abstract class RetiredTurtleHelmetMixin {
    @Inject(method = "turtleHelmetTick", at = @At("HEAD"), cancellable = true)
    private void afl$retiredTurtleHelmet(CallbackInfo ci) {
        if (VanillaRetirement.isRetiredGear(((Player) (Object) this).getItemBySlot(EquipmentSlot.HEAD))) ci.cancel();
    }
}
