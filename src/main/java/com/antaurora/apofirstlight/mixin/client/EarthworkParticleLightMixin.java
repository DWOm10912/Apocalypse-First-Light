package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.earthwork.Earthwork;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A light asked by position only (LevelRenderer#getLightColor(getter, pos): particles, block entities, the earthwork skin's
 * own neighbour reads in a chunk build region) in a skin cell of an edited column comes from the first cell above that is
 * not a skin cell (Earthwork A3, user 2026-10-21). The overload with the state in hand, which chunk meshing uses, keeps the
 * cell's own light. On the client level itself GroundLightMixin does this and more for every read; this stays for the
 * build regions until the rework's one mesher owns their light (docs/worldgen/terrain_rework_v1_design.md 3.7).
 */
@Mixin(LevelRenderer.class)
public abstract class EarthworkParticleLightMixin {
    @ModifyVariable(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"), argsOnly = true)
    private static BlockPos afl$cell(BlockPos pos, BlockAndTintGetter level) {
        return Earthwork.clientLightCell(level, pos);
    }
}
