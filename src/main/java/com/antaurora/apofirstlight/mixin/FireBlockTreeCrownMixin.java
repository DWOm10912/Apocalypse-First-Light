package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.LightningTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lightning V2 (world/LightningTrees): a struck crown's fire keeps burning in its cells for its time (a tree's crown is not
 * flammable to vanilla fire, which would not stay beside it), aging and going out in the rain as any fire. It spreads to
 * nothing the crown holds: the crown neither burns away nor carries the fire to the next tree.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockTreeCrownMixin {
    @Inject(method = "isValidFireLocation", at = @At("HEAD"), cancellable = true)
    private void afl$crownFire(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof Level l && LightningTrees.burning(l, pos)) cir.setReturnValue(true);
    }
}
