package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.LightningStrikes;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lightning V1 (world/LightningStrikes, docs/rendering/lightning_v1.md): the server keeps a bolt at least
 * {@link LightningStrikes#MIN_TICKS} ticks. Vanilla removes it after its random re-flashes, as soon as 5 ticks; the
 * removal reaches the client and would cut AFL's flash (up to ~0.8 s of strokes) short. The client removes its own copy
 * when the flash is over.
 */
@Mixin(LightningBolt.class)
public abstract class LightningBoltLifeMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;discard()V"))
    private void afl$holdForTheFlash(LightningBolt bolt) {
        if (bolt.tickCount >= LightningStrikes.MIN_TICKS) bolt.discard();
    }
}
