package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Guns are held right-handed only (docs/dev/gun_handling_reference_v1.md 4.8): while the main hand holds one, the main
 * arm is the right one whatever the Main Hand option says, so every gun path (first-person view model, third-person
 * hands, the skeleton's stance, the right-thigh holster's draw) has one side. Other items keep the option.
 */
@Mixin(Player.class)
public abstract class PlayerGunRightArmMixin {
    @Inject(method = "getMainArm", at = @At("HEAD"), cancellable = true)
    private void afl$gunsRightHanded(CallbackInfoReturnable<HumanoidArm> cir) {
        if (((Player) (Object) this).getMainHandItem().getItem() instanceof NativeGunItem) cir.setReturnValue(HumanoidArm.RIGHT);
    }
}
