package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.post.AflPost;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AFL picture upgrade (client/post/AflPost): the last passes run after the world and the hand, before the HUD. */
@Mixin(GameRenderer.class)
public abstract class PostGameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            shift = At.Shift.AFTER))
    private void afl$afterLevel(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        AflPost.afterLevel();
    }
}
