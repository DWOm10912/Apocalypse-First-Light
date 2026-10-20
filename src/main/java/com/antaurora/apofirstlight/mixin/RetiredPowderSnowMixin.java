package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.VanillaRetirement;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla Survival Rework V1: retired leather boots no longer carry their wearer over powder snow.
 * canWalkOnPowderedSnow is a Forge method, hence remap = false on the target.
 */
@Mixin(PowderSnowBlock.class)
public abstract class RetiredPowderSnowMixin {
    @Redirect(method = "canEntityWalkOnPowderSnow", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/item/ItemStack;canWalkOnPowderedSnow(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private static boolean afl$retiredPowderSnowBoots(ItemStack stack, LivingEntity wearer) {
        return !VanillaRetirement.isRetiredGear(stack) && stack.canWalkOnPowderedSnow(wearer);
    }
}
