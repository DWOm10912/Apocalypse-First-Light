package com.antaurora.apofirstlight.noise;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.mixin.ExplosionAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExplosionNoiseEvents {
    private ExplosionNoiseEvents() {
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Explosion explosion = event.getExplosion();
        float strength = ((ExplosionAccessor) explosion).afl$getRadius();
        NoiseSystem.emit(new NoiseEvent(
                explosion.getExploder(),
                explosion.getPosition(),
                NoiseType.EXPLOSION,
                level.getGameTime(),
                null,
                ExplosionNoiseProfile.radius(strength)
        ), level);
    }
}
