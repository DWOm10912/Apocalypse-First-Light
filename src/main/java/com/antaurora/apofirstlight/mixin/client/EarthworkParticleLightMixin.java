package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.earthwork.Earthwork;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The same for a particle in a skin cell (EarthworkEntityLightMixin; digging dust in a pit): the light of a cell asked by
 * position only (Particle#getLightColor, block entities) comes from the first cell above that is not a skin cell. The
 * overload with the state in hand, which chunk meshing uses, keeps the cell's own light.
 */
@Mixin(LevelRenderer.class)
public abstract class EarthworkParticleLightMixin {
    @ModifyVariable(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"), argsOnly = true)
    private static BlockPos afl$cell(BlockPos pos, BlockAndTintGetter level) {
        return Earthwork.clientLightCell(level, pos);
    }
}
