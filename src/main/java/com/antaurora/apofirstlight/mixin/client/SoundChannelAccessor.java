package com.antaurora.apofirstlight.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Thunder V1 (client/sound/WeatherMuffle): a playing sound's OpenAL source, to put a low-pass filter on it. */
@Mixin(Channel.class)
public interface SoundChannelAccessor {
    @Accessor("source")
    int afl$source();
}
