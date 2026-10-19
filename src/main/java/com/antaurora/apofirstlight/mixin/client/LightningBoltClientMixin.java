package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.lightning.AflLightning;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lightning V1 (client/lightning/AflLightning, docs/rendering/lightning_v1.md): on the client a bolt ticks AFL's flash
 * instead of vanilla's (its sounds, its sky flash per tick, its random re-flashes); its culling box spans the channel, so
 * Embeddium's entity culling keeps it while the strike point is hidden behind a hill.
 */
@Mixin(LightningBolt.class)
public abstract class LightningBoltClientMixin extends Entity {
    private LightningBoltClientMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void afl$clientFlash(CallbackInfo ci) {
        if (!level().isClientSide) return;
        AflLightning.clientTick((LightningBolt) (Object) this);
        ci.cancel();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return AflLightning.cullingBox((LightningBolt) (Object) this);
    }
}
