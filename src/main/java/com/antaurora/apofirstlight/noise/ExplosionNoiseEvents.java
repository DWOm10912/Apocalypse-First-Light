package com.antaurora.apofirstlight.noise;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.mixin.ExplosionAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
        double radius = ExplosionNoiseProfile.radius(strength);
        NoiseSystem.emit(new NoiseEvent(
                explosion.getExploder(),
                explosion.getPosition(),
                NoiseType.EXPLOSION,
                level.getGameTime(),
                null,
                radius
        ), level);
        // the blast's sound, heard as far as its noise (vanilla's own, at 64 blocks, is taken out: mixin/client/ExplosionSoundMixin);
        // vanilla's pitch
        float pitch = (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F;
        RangedSound.play(level, explosion.getPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, radius, 1.0F, pitch);
    }
}
