package com.antaurora.apofirstlight.client.weather;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.mixin.client.SoundChannelAccessor;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Thunder V1 (docs/audio/thunder_sounds_v1_plan.md 5): weather sounds (thunder, vanilla rain) heard from under a roof or
 * from a shut T15 cab come through walls and glass, which take the highs off: an OpenAL EFX low-pass on the sound's
 * source, set once as it starts. How muffled is worked out each client tick from where the camera is: under a roof by
 * how little sky light reaches it (an open door lets some in), in the T15's front seats 1 with both doors shut, 0.4 with
 * one open. Without EFX on the sound device nothing is filtered.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WeatherMuffle {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Under a roof: no muffle at this sky light or more, full at OPEN - SPAN or less. */
    private static final int OPEN = 13;
    private static final float SPAN = 9;
    /** In the T15's front seats: both doors shut, a door open. */
    private static final float CAB_SHUT = 1, CAB_OPEN = 0.4F;
    /** Full muffle: the gain left overall and above about 5 kHz. */
    private static final float GAIN = 0.65F, GAIN_HF = 0.06F;
    /** Share of the way to the new muffle per tick (about half a second). */
    private static final float EASE = 0.15F;

    /** 0 open air, 1 deep indoors (client thread writes, the sound thread reads). */
    private static volatile float muffle;
    private static volatile boolean reload;
    // sound thread only
    private static int filter;
    private static boolean disabled;
    private static final Set<Integer> FILTERED = new HashSet<>();

    private WeatherMuffle() {
    }

    /** How muffled weather sounds started now are (0..1). */
    public static float muffle() {
        return muffle;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        float target = mc.level == null || mc.player == null ? 0 : target(mc, mc.level);
        float m = muffle;
        muffle = Math.abs(target - m) < 0.005F ? target : m + (target - m) * EASE;
    }

    private static float target(Minecraft mc, ClientLevel level) {
        float m = 0;
        if (mc.player.getVehicle() instanceof PickupTruckEntity truck) {
            int seat = truck.seatOf(mc.player);
            if (seat == PickupTruckLayout.DRIVER || seat == PickupTruckLayout.PASSENGER)
                m = truck.doorOpen(false) || truck.doorOpen(true) ? CAB_OPEN : CAB_SHUT;
        }
        BlockPos p = BlockPos.containing(mc.gameRenderer.getMainCamera().getPosition());
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) > p.getY())
            m = Math.max(m, Mth.clamp((OPEN - level.getBrightness(LightLayer.SKY, p)) / SPAN, 0, 1));
        return m;
    }

    @SubscribeEvent
    public static void loaded(SoundEngineLoadEvent event) {
        reload = true;
    }

    @SubscribeEvent
    public static void play(PlaySoundSourceEvent event) {
        apply(event.getSound(), event.getChannel());
    }

    @SubscribeEvent
    public static void stream(PlayStreamingSourceEvent event) {
        apply(event.getSound(), event.getChannel());
    }

    /** The sound thread, as the source starts: filter a weather sound; clear a reused source this class filtered before. */
    private static void apply(SoundInstance sound, Channel channel) {
        if (disabled) return;
        if (reload) {
            reload = false;
            filter = 0;
            FILTERED.clear();
        }
        float m = sound.getSource() == SoundSource.WEATHER && !sound.isRelative() ? muffle : 0;
        try {
            int source = ((SoundChannelAccessor) channel).afl$source();
            if (m < 0.02F) {
                if (FILTERED.remove(source)) AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                return;
            }
            if (filter == 0) {
                filter = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                if (filter == 0 || AL10.alGetError() != AL10.AL_NO_ERROR) throw new IllegalStateException("no low-pass filter");
            }
            // a source keeps a copy of the filter's settings, so one filter serves every level of muffle
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, Mth.lerp(m, 1, GAIN));
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, Mth.lerp(m, 1, GAIN_HF));
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
            FILTERED.add(source);
        } catch (Throwable e) {
            disabled = true;
            LOGGER.warn("Weather muffle: no OpenAL EFX low-pass ({}), weather sounds stay unfiltered", e.toString());
        }
    }
}
