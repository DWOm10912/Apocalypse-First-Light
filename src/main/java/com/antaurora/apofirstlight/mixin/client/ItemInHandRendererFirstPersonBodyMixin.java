package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.FirstPersonBody;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person body V1 (docs/rendering/first_person_body_v1.md): while the body shows its own arms, vanilla's first-person
 * hand (empty, or an ordinary item) slides down out of view as the player looks down (client/FirstPersonBody#handDrop).
 * Hands drawn by AFL's own first-person rigs cancel RenderHandEvent and never get here.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererFirstPersonBodyMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void afl$handDropBegin(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing,
            ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        pose.pushPose();
        float drop = FirstPersonBody.handDrop(partialTick);
        if (drop > 0F) pose.translate(0F, -drop, 0F);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void afl$handDropEnd(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing,
            ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        pose.popPose();
    }
}
