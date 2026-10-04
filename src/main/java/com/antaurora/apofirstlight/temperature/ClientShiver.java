package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Random;

/**
 * Shivering: from the second cold stage on, short bursts of a small, fast camera jitter (first person only). Camera only:
 * the player's rotation, the crosshair target and the shot direction are untouched. Bursts of 0.35..0.45 s under a sine
 * envelope, an 18 Hz jitter on yaw / pitch / roll, gaps of about 3 s at the stage's start down to about 1 s at the
 * damaging stage. Timed in client ticks, so it stops while the game is paused. Driven by the core temperature from the
 * server; the stage temperatures mirror cold_stages in temperature_v1.json (the client has no copy of the data file).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientShiver {
    /** Core temperatures (°C): shivering starts below the second cold stage and is strongest from the third. */
    private static final double START = 35.0, FULL = 34.0;
    /** Peak angles in degrees at full strength; the jitter frequency in Hz. */
    private static final double YAW = 0.12, PITCH = 0.09, ROLL = 0.04, JITTER_HZ = 18;
    private static final Random RANDOM = new Random();
    private static long ticks;
    private static double burstStart = Double.NEGATIVE_INFINITY, burstLength, nextBurst = -1, phase, amplitude;
    private ClientShiver() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        ticks++;
        double now = ticks / 20.0, s = strength(mc);
        if (s < 0) { nextBurst = -1; return; }                       // a burst under way plays out
        if (nextBurst < 0) nextBurst = now + 0.5 * gap(s);           // the first one soon after the stage begins
        if (now >= nextBurst && now >= burstStart + burstLength) {
            burstStart = now;
            burstLength = 0.35 + 0.1 * RANDOM.nextDouble();
            phase = RANDOM.nextDouble() * 10;
            amplitude = 0.4 + 0.6 * s;
            nextBurst = now + burstLength + gap(s);
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        double t = (ticks + event.getPartialTick()) / 20.0 - burstStart;
        if (t < 0 || t >= burstLength || mc.player == null || event.getCamera().getEntity() != mc.player
                || !mc.options.getCameraType().isFirstPerson() || mc.player.isScoping()) return;
        double a = Math.sin(Math.PI * t / burstLength) * amplitude, f = (t + phase) * JITTER_HZ * Math.PI * 2;
        event.setYaw(event.getYaw() + (float)(a * YAW * Math.sin(f + 0.3)));
        event.setPitch(event.getPitch() + (float)(a * PITCH * Math.sin(f * 1.31 + 1.7)));
        event.setRoll(event.getRoll() + (float)(a * ROLL * Math.sin(f * 0.83 + 2.9)));
    }

    /** −1 when not shivering, else 0 at the second cold stage .. 1 at the third. */
    private static double strength(Minecraft mc) {
        var state = ClientTemperature.state();
        if (mc.player == null || state == null || mc.player.isSpectator() || !mc.player.isAlive() || state.core() >= START) return -1;
        return Math.min(1, (START - state.core()) / (START - FULL));
    }
    /** Seconds between bursts: about 3 at the second stage, about 1 at the third, ±25 %. */
    private static double gap(double s) { return (3 - 2 * s) * (0.75 + 0.5 * RANDOM.nextDouble()); }
}
