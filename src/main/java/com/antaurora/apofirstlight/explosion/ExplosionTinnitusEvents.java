package com.antaurora.apofirstlight.explosion;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.mixin.ExplosionAccessor;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExplosionTinnitusEvents {
    private ExplosionTinnitusEvents() {}

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Explosion explosion = event.getExplosion();
        float radius = ((ExplosionAccessor) explosion).afl$getRadius();
        // Do not use affectedEntities: armor, exposure and other mods' damage filters are irrelevant.
        // Only iterate this dimension's players, once per actual detonation, never every tick.
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            float severity = ExplosionTinnitusProfile.severity(radius,
                    player.getEyePosition().distanceTo(explosion.getPosition()));
            if (ExplosionTinnitusProfile.shouldTrigger(severity)
                    || ExplosionTinnitusProfile.shouldTriggerOverlay(severity)) {
                AflNetwork.sendExplosionTinnitus(player, severity);
            }
        }
    }
}
