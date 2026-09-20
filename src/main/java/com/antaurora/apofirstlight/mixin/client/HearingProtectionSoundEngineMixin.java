package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.HearingProtectionAudio;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** One multiplier site, after vanilla's volume clamp; no source/range/options mutation. */
@Mixin(SoundEngine.class)
public abstract class HearingProtectionSoundEngineMixin {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow private float calculateVolume(SoundInstance sound) { throw new AssertionError(); }
    @Unique private float afl$lastHearingMultiplier = 1.0F;

    // Initial play uses the float/category overload. Route it to the same per-instance
    // calculation used by ticking sounds and category updates, including Forge-replaced sounds.
    @Redirect(method = "play", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float afl$initialVolume(SoundEngine engine, float volume, SoundSource source, SoundInstance sound) {
        return calculateVolume(sound);
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"), cancellable = true)
    private void afl$listenerVolume(SoundInstance sound, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(HearingProtectionAudio.attenuate(sound, callback.getReturnValueF()));
    }

    @Inject(method = "tickNonPaused", at = @At("TAIL"))
    private void afl$refreshOnEquipmentChange(CallbackInfo callback) {
        float multiplier = HearingProtectionAudio.listenerMultiplier();
        if (Float.compare(multiplier, afl$lastHearingMultiplier) == 0) return;
        afl$lastHearingMultiplier = multiplier;
        // Non-ticking/streamed loops also need an immediate refresh when equipped/removed.
        // Recompute from the unchanged SoundInstance, never from an already attenuated gain.
        instanceToChannel.forEach((sound, handle) -> {
            if (HearingProtectionAudio.isWorldSound(sound) && !handle.isStopped()) {
                float volume = calculateVolume(sound);
                handle.execute(channel -> channel.setVolume(volume));
            }
        });
    }
}
