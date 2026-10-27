package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.FirstPersonBody;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The own first-person body is never culled (client/FirstPersonBody#neverCulled; docs/gameplay/furniture_bench_work_v1.md
 * 12.10). The view may sit on a posed eye a block or more from the player's spot (a bench action's step, a seat, the ground
 * sit); the player's box is then behind the view and vanilla's frustum test left the body out, so it was not solved, the
 * camera fell back to the player's own eye 3 frames on, the body came into view again: first and third person in turn.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherOwnBodyMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void afl$ownBody(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (FirstPersonBody.neverCulled(entity)) cir.setReturnValue(true);
    }
}
