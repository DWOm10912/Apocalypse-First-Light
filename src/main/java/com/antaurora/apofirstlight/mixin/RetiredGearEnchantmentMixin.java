package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.VanillaRetirement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla Survival Rework V1: enchantments on retired gear (VanillaRetirement.GEAR) do nothing. The two points every
 * gameplay read goes through: the per-item level (knockback, fire aspect, sweeping edge, looting, frost walker, depth
 * strider, soul speed, respiration, binding / vanishing curses, unbreaking) and the per-item iteration (sharpness and the
 * other damage bonuses, all protections, feather falling, thorns and other post-hurt effects). The NBT stays untouched,
 * so the enchantments come back when the retirement switch is off.
 */
@Mixin(EnchantmentHelper.class)
public abstract class RetiredGearEnchantmentMixin {
    @Inject(method = "getItemEnchantmentLevel", at = @At("HEAD"), cancellable = true)
    private static void afl$retiredLevel(Enchantment enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (VanillaRetirement.isRetiredGear(stack)) cir.setReturnValue(0);
    }

    @Inject(method = "runIterationOnItem", at = @At("HEAD"), cancellable = true)
    private static void afl$retiredIteration(@Coerce Object visitor, ItemStack stack, CallbackInfo ci) {
        if (VanillaRetirement.isRetiredGear(stack)) ci.cancel();
    }
}
