package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.FirstPersonBody;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person body V1 (docs/rendering/first_person_body_v1.md): while the body shows its own arms, vanilla's first-person
 * hand (empty, or an ordinary item) slides down out of view as the player looks down (client/FirstPersonBody#handDrop).
 * Hands drawn by AFL's own first-person rigs cancel RenderHandEvent and never get here.
 * <p>
 * The push/pop wraps each renderArmWithItem call in renderHandsWithItems, not renderArmWithItem itself: Oculus cancels
 * renderArmWithItem at HEAD for the pass that does not draw the hand (a held item with a translucent render type, such as
 * the static-mesh furniture, skips the opaque pass), and a RETURN pop would then never run, leaving the pose stack one
 * level deep for the rest of the frame.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererFirstPersonBodyMixin {
    private static final String RENDER_ARM = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem("
            + "Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;"
            + "FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = RENDER_ARM))
    private void afl$handDropBegin(float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer player,
            int light, CallbackInfo ci) {
        pose.pushPose();
        float drop = FirstPersonBody.handDrop(partialTick);
        if (drop > 0F) pose.translate(0F, -drop, 0F);
    }

    @Inject(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = RENDER_ARM, shift = At.Shift.AFTER))
    private void afl$handDropEnd(float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer player,
            int light, CallbackInfo ci) {
        pose.popPose();
    }
}
