package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.VanillaRetirement;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Vanilla Survival Rework V1: retired leather armour no longer makes its wearer immune to powder-snow freezing. */
@Mixin(LivingEntity.class)
public abstract class RetiredFreezeImmunityMixin {
    @Redirect(method = "canFreeze", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean afl$retiredFreezeImmunity(ItemStack stack, TagKey<Item> tag) {
        return !VanillaRetirement.isRetiredGear(stack) && stack.is(tag);
    }
}
