package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.MeshHitPicking;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The block part of the crosshair pick through hit meshes (client/MeshHitPicking, docs/rendering/mesh_hit_runtime_v1.md
 * P2): GameRenderer#pick's Entity#pick, the rest of the pick (entities, reach) unchanged.
 */
@Mixin(GameRenderer.class)
public abstract class MeshHitPickMixin {
    @Redirect(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult afl$pickMeshes(Entity entity, double range, float partialTick, boolean fluids) {
        return MeshHitPicking.pick(entity, range, partialTick, fluids);
    }
}
