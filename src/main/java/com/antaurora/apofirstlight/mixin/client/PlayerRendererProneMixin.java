package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.skeleton.PlayerSkeleton;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lying on the front on land (docs/gameplay/lightning_injury_v1_plan.md 10; client/skeleton/ProneClient): the skeleton's
 * prone clips lay the body down themselves, so vanilla's swim turn of the whole model (pitched down, shifted a block)
 * stays off for a player the skeleton draws lying. Swimming in water keeps vanilla's.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererProneMixin {
    @Redirect(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSwimAmount(F)F"))
    private float afl$proneSwimAmount(AbstractClientPlayer player, float partial) {
        return PlayerSkeleton.drawsProne(player) ? 0F : player.getSwimAmount(partial);
    }
}
