package com.antaurora.apofirstlight.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No round blob shadows under entities (user 2026-10-28: "能不能把这种一个圈的阴影删掉，现在好像玩家/生物/皮卡都有呢"): the
 * player, the deer and the infected, the T15, bodies and items all go without, whatever the Entity Shadows option says.
 * The shader pack casts real shadows; without one the ground under a creature stays as lit. Cancelled at the head, as
 * Embeddium's fast shadows do.
 *
 * <p>Until then this mixin moved the shadow with a body drawn away from its entity (CameraDirector#bodyOffset); with no
 * shadow that is not needed.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherShadowMixin {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void afl$noShadow(PoseStack pose, MultiBufferSource buffers, Entity entity, float opacity, float partial,
                                    LevelReader level, float radius, CallbackInfo ci) {
        ci.cancel();
    }
}
