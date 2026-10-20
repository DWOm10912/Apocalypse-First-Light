package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.VanillaRetirement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.common.ToolAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla Survival Rework V1: a retired sword (VanillaRetirement.SWORDS) has none of the sword's tool actions, so no sweep
 * attack (Player.attack asks SWORD_SWEEP) and no instant bamboo or other SWORD_DIG breaking. Its damage and speed are
 * cleared in VanillaRetirementEvents; other mods' swords (not in the tag) are untouched. canPerformAction is a Forge
 * method, hence remap = false.
 */
@Mixin(SwordItem.class)
public abstract class RetiredSwordMixin {
    @Inject(method = "canPerformAction", at = @At("HEAD"), cancellable = true, remap = false)
    private void afl$noRetiredSwordActions(ItemStack stack, ToolAction action, CallbackInfoReturnable<Boolean> cir) {
        if (VanillaRetirement.isRetiredSword(stack)) cir.setReturnValue(false);
    }
}
