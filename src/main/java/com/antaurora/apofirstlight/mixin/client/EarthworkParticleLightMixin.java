package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.terrainsmooth.client.RockCarveMesh;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The same for a particle in a skin cell (EarthworkEntityLightMixin; digging dust in a pit): the light of a cell asked by
 * position only (Particle#getLightColor, block entities) comes from the first cell above that is not a skin cell. The
 * overload with the state in hand, which chunk meshing uses, keeps the cell's own light. A cell of carved or worn rock
 * asked in the level gets its hollow's light (RockCarveMesh#openLight; user 2026-10-24).
 */
@Mixin(LevelRenderer.class)
public abstract class EarthworkParticleLightMixin {
    @ModifyVariable(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"), argsOnly = true)
    private static BlockPos afl$cell(BlockPos pos, BlockAndTintGetter level) {
        return Earthwork.clientLightCell(level, pos);
    }

    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void afl$hollow(BlockAndTintGetter getter, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (getter instanceof Level level) cir.setReturnValue(RockCarveMesh.lightAt(level, pos, cir.getReturnValueI()));
    }
}
