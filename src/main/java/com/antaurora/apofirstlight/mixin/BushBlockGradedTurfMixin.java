package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.block.GradedTurfBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * C3.3a group B (docs/worldgen/federal_highways_c3.md 11.11): the plants that grow on dirt (grass, ferns, flowers, tall
 * plants: BushBlock's own mayPlaceOn) grow on graded_turf too, at world generation (the features' survival pre-check) and
 * in play (Forge's canSustainPlant asks the same method). Saplings do not: graded_turf stays out of #minecraft:dirt, so no
 * tree (vanilla or the mesh forest, which roots on that tag) stands on the road's earthworks.
 */
@Mixin(BushBlock.class)
public abstract class BushBlockGradedTurfMixin {
    @Inject(method = "mayPlaceOn", at = @At("HEAD"), cancellable = true)
    private void apocalypse$growOnTurf(BlockState ground, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ground.getBlock() instanceof GradedTurfBlock && !((Object) this instanceof SaplingBlock)) cir.setReturnValue(true);
    }
}
