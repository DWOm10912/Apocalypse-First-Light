package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.LightningAim;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lightning V2 (docs/gameplay/lightning_v2_plan.md 2): a strike's target is the rolling sphere's (world/LightningAim,
 * world/StrikePoint): height and standing alone decide it, an AFL lightning rod takes the strikes near it. Before (lightning
 * injury V1) the tallest thing within 3 blocks of a living thing near vanilla's random column. Vanilla's own (any living
 * thing within 3 blocks, a vanilla lightning rod) only with strike.enabled off.
 */
@Mixin(ServerLevel.class)
public abstract class LightningTargetServerLevelMixin {
    @Inject(method = "findLightningTargetAround", at = @At("HEAD"), cancellable = true)
    private void afl$rollingSphere(BlockPos pos, CallbackInfoReturnable<BlockPos> cir) {
        BlockPos target = LightningAim.target((ServerLevel) (Object) this, pos);
        if (target != null) cir.setReturnValue(target);
    }
}
