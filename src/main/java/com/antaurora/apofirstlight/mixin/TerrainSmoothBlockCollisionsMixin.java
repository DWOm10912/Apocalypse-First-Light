package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Terrain Smooth V1: vanilla's collision sweep only looks at the outer layer of cells around a box for states flagged as
 * having a large collision shape (fences). A smoothed top rises up to 0.75 above its cell, so for the ground blocks
 * (TerrainSmooth.GROUND) the layer just under the box counts too: there, at that one position, not by flagging the states
 * (the P1 prototype did, which made every collision in the world scan all of them in its outer layer).
 */
@Mixin(BlockCollisions.class)
public abstract class TerrainSmoothBlockCollisionsMixin {
    @Shadow @Final private BlockPos.MutableBlockPos pos;
    @Shadow @Final private AABB box;

    @Redirect(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;hasLargeCollisionShape()Z"))
    private boolean afl$smoothRisesIntoBox(BlockState state) {
        return state.hasLargeCollisionShape() || TerrainSmooth.risesIntoBox(state, this.pos, this.box);
    }
}
