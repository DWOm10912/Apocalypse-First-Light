package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.skeleton.PlayerSkeleton;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player skeleton V1 prototype: the gun the skeleton holds in both hands (client/skeleton/SkeletonGunStance) is not drawn
 * again in the vanilla hand; an action's prop (player actions S2: the jerry can) is drawn where the action places it; any
 * other held item hangs on the skeleton's hand bone, not the vanilla upper arm, so it follows a bent elbow (player actions
 * V1, docs/dev/player_actions_v1_plan.md).
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerSkeletonMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void afl$skeletonGun(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (PlayerSkeleton.ownsHeldItem(entity, stack) || PlayerSkeleton.drawProp(entity, stack, pose, buffers, light)) ci.cancel();
    }

    @Redirect(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void afl$skeletonHand(ArmedModel model, HumanoidArm side, PoseStack stack, LivingEntity entity, ItemStack item,
            ItemDisplayContext context, HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!PlayerSkeleton.translateToHand(entity, side, stack)) model.translateToHand(side, stack);
    }
}
