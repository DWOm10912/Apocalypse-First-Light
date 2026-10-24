package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.vegetation.client.VegetationDynamics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A section rebuilt only for the mesh plants (the near layer's hand-over, a footprint: vegetation/client/VegetationDynamics,
 * docs/gameplay/vegetation_v1_design.md 12.5) is built on Embeddium's workers, not blocking the frame: Embeddium makes
 * every rebuild within 16 m of the camera an important one, built on the render thread before the frame goes on, and a
 * section of turf takes long enough to stutter. Embeddium 5681725; skipped without it.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class VegetationRebuildEmbeddiumMixin {
    @Shadow(remap = false)
    private static boolean allowImportantRebuilds() {
        throw new AssertionError();
    }

    @Redirect(method = "scheduleRebuild(IIIZ)V", remap = false, require = 0,
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSectionManager;allowImportantRebuilds()Z", remap = false))
    private boolean afl$plantsDeferred() {
        return !VegetationDynamics.deferRebuilds() && allowImportantRebuilds();
    }
}
