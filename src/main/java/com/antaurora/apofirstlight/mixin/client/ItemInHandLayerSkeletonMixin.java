package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.skeleton.PlayerSkeleton;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player skeleton V1 prototype: the gun the skeleton holds in both hands (client/skeleton/SkeletonGunStance) is not drawn again in the vanilla hand. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerSkeletonMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void afl$skeletonGun(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (PlayerSkeleton.ownsHeldItem(entity, stack)) ci.cancel();
    }
}
