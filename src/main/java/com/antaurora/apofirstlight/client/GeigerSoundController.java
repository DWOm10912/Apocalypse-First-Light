package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Geiger counter V2 sound (2026-10-15, docs/models/geiger_counter_v2_design.md section 4): one tick per simulated count
 * (GeigerReadout, the same counts that flash the LED), from one of four ticks cut from the user's recording with a
 * small random pitch; as the count rate climbs from {@link #BLEND_FROM} to {@link #BLEND_TO} counts per second the
 * ticks fade out and the dense loop (a seamless 4 s cut of the user's high-rate recording) fades in, so very high rates
 * do not need hundreds of sound instances. Only the holder hears it (relative, no attenuation), as before.
 * Steps GeigerReadout once per frame while a counter is in either hand.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GeigerSoundController {
    /** Count rates (per second) between which the ticks hand over to the dense loop. */
    private static final double BLEND_FROM = 25, BLEND_TO = 110;
    private static final float TICK_VOLUME = 0.7F, DENSE_VOLUME = 0.75F;
    /** At most this many ticks per frame (the rest are inaudible inside the same few milliseconds). */
    private static final int MAX_TICKS_PER_FRAME = 3;
    private static final RandomSource RANDOM = RandomSource.create();
    private static DenseLoop dense;
    private static int startupTicks;
    private static double blend;

    static {
        GeigerReadout.listen(GeigerSoundController::onCounts);
    }

    private GeigerSoundController() {}

    private static boolean holding(Minecraft mc) {
        // in the hand or clipped on (clips, user 2026-10-21)
        return mc.player != null && mc.level != null
                && com.antaurora.apofirstlight.equipment.AflEquipmentSlots.inUse(mc.player, AflItems.GEIGER_COUNTER.get());
    }

    /** Every frame: step the reading (counts play ticks through onCounts). */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (!holding(mc) || mc.isPaused()) { GeigerReadout.pause(); return; }
        GeigerReadout.advance();
    }

    /** Share of the dense loop at a count rate (0..1, smooth). */
    private static double blend(double cps) {
        double t = Math.max(0, Math.min(1, (cps - BLEND_FROM) / (BLEND_TO - BLEND_FROM)));
        return t * t * (3 - 2 * t);
    }

    private static void onCounts(int n, double cps) {
        Minecraft mc = Minecraft.getInstance();
        float volume = (float)(TICK_VOLUME * (1 - blend(cps)));
        if (volume < 0.02F) return;
        for (int i = 0; i < Math.min(n, MAX_TICKS_PER_FRAME); i++) {
            float pitch = 0.94F + 0.12F * RANDOM.nextFloat();
            mc.getSoundManager().play(new SimpleSoundInstance(AflSounds.GEIGER_CLICK.get().getLocation(), SoundSource.MASTER,
                    volume, pitch, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        }
    }

    /** Every client tick: the dense loop's share follows the shown rate; it starts and stops with it. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.isPaused()) return;
        double rate = holding(mc) ? GeigerReadout.rate() : Double.NaN;
        double target = Double.isNaN(rate) ? 0 : blend(GeigerReadout.cps(rate));
        blend += (target - blend) * 0.35;
        if (dense != null && (dense.level != mc.level || blend < 0.01)) {
            dense.stopNow();
            mc.getSoundManager().stop(dense);
            dense = null;
        }
        // recover after a sound-engine reload; allow asynchronous startup first
        if (dense != null && ++startupTicks >= 40 && !mc.getSoundManager().isActive(dense)) {
            dense.stopNow();
            dense = null;
        }
        if (dense == null && blend >= 0.01 && mc.level != null) {
            dense = new DenseLoop(mc.level);
            startupTicks = 0;
            mc.getSoundManager().play(dense);
        }
    }

    private static final class DenseLoop extends AbstractTickableSoundInstance {
        private final ClientLevel level;

        DenseLoop(ClientLevel level) {
            super(AflSounds.GEIGER_DENSE.get(), SoundSource.MASTER, RandomSource.create());
            this.level = level;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = (float)(DENSE_VOLUME * Math.max(blend, 0.01));
            pitch = 1.0F;
        }

        @Override public void tick() {
            if (Minecraft.getInstance().level != level) { stop(); return; }
            volume = (float)(DENSE_VOLUME * blend);
        }

        void stopNow() { stop(); }
    }
}
