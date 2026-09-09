package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.radiation.RadiationZone;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One unmodified recording loops for the server-synchronized radiation zone. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GeigerSoundController {
    private static ZoneLoop active;
    private static int startupTicks;

    private GeigerSoundController() {}

    private static RadiationZone requestedZone(Minecraft mc) {
        if (mc.player == null || mc.level == null
                || (!mc.player.getMainHandItem().is(AflItems.GEIGER_COUNTER.get())
                && !mc.player.getOffhandItem().is(AflItems.GEIGER_COUNTER.get()))) return RadiationZone.SAFE;
        var data = ClientGeigerData.snapshot();
        return data.stale() ? RadiationZone.SAFE : data.zone();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.isPaused()) return;
        RadiationZone desired = requestedZone(mc);
        if (active != null && (active.level != mc.level || active.zone != desired)) {
            active.stopNow();
            mc.getSoundManager().stop(active);
            active = null;
        }
        // Recover after sound-engine reload; allow asynchronous startup first.
        if (active != null && ++startupTicks >= 40 && !mc.getSoundManager().isActive(active)) {
            active.stopNow();
            active = null;
        }
        if (active != null || desired == RadiationZone.SAFE) return;
        SoundEvent sound = switch (desired) {
            case IRRADIATED -> AflSounds.GEIGER_LOW.get();
            case HEAVY_FALLOUT -> AflSounds.GEIGER_MEDIUM.get();
            case EXTREME -> AflSounds.GEIGER_EXTREME.get();
            case SAFE -> throw new IllegalStateException("Safe zones are silent");
        };
        active = new ZoneLoop(mc.level, desired, sound);
        startupTicks = 0;
        mc.getSoundManager().play(active);
    }

    private static final class ZoneLoop extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final RadiationZone zone;

        ZoneLoop(ClientLevel level, RadiationZone zone, SoundEvent sound) {
            super(sound, SoundSource.MASTER, RandomSource.create());
            this.level = level;
            this.zone = zone;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.8F;
            pitch = 1.0F;
        }

        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != level || requestedZone(mc) != zone) stop();
        }

        void stopNow() { stop(); }
    }
}
