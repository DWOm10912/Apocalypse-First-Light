package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.earthwork.Earthwork;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Section occlusion culling: an edited column's skin cell is a full block in the data but open above its skin, so it never
 * closes a section's visibility graph. Otherwise a pit whose floor dips just below a 16-block section line, every cell on
 * the line a skin cell, sealed the lower section off and its part of the floor was not drawn: sky showed through the
 * bottom of the pit (Earthwork A3, user 2026-10-21; seen with the camera in the air only, as a spectator inside a block
 * turns culling off). Vanilla's chunk compile and Embeddium's meshing task both mark opaque cells here, on the
 * chunk-builder threads.
 */
@Mixin(VisGraph.class)
public abstract class EarthworkVisGraphMixin {
    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void afl$skinCellsOpen(BlockPos pos, CallbackInfo ci) {
        if (Earthwork.clientSkinCell(pos.getX(), pos.getY(), pos.getZ())) ci.cancel();
    }
}
