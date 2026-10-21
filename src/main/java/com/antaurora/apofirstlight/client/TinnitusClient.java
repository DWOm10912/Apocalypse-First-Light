package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.hearing.HearingPackets;
import com.antaurora.apofirstlight.hearing.TinnitusConfig;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tinnitus V2 on the client (docs/gameplay/tinnitus_v2_plan.md 3.4): the own load and stage from the server
 * (HearingPackets.State), counted down here between syncs so the badge's ring drains smoothly. While there is a stage, a spell of ringing
 * now and then (interval / length / volume of that stage), in the left ear, the right or both; when the stage goes up, one
 * at once. The world's sounds × the stage's hearing (HearingProtectionAudio). A jolt (one loud dose, HearingPackets.Jolt)
 * rings at once (explosion_tinnitus) and drops the world's sounds to jolt_hearing, back over jolt_recover seconds.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class TinnitusClient {
    private static final RandomSource RANDOM = RandomSource.create();
    /** A spell's own fade in and out (ticks) on top of the take's; a cut spell fades out over FADE_OUT. */
    private static final int FADE_IN = 8, FADE_OUT = 16;

    private static double load;
    private static int stage;
    private static boolean known;
    private static long ticks, nextSpell = -1;
    private static Ring ring;
    private static int joltLeft, joltTotal;

    private TinnitusClient() {
    }

    public static void accept(HearingPackets.State state) {
        int was = stage;
        load = Math.max(0, state.load());
        stage = Mth.clamp(state.stage(), 0, 3);
        if (stage == 0) {
            nextSpell = -1;
        } else if (known && stage > was && joltLeft <= 0) {
            nextSpell = ticks;
        } else if (nextSpell < 0) {
            schedule(0);
        }
        known = true;
    }

    public static void jolt(float strength) {
        var c = TinnitusConfig.get();
        double s = Mth.clamp(strength, 0, 1);
        joltTotal = joltLeft = (int) Math.round(20 * Mth.lerp(s, c.joltRecover[0], c.joltRecover[1]));
        if (ring != null) ring.cut();
        ring = new Ring(AflSounds.EXPLOSION_TINNITUS.get(), 0, (float) (0.25 + 0.55 * s), (int) Math.round(20 * (4 + 6 * s)));
        Minecraft.getInstance().getSoundManager().play(ring);
        if (nextSpell >= 0) nextSpell = Math.max(nextSpell, ticks + joltTotal + 20L * 5);
    }

    public static int stage() {
        return stage;
    }

    /** The world's sounds × this: the stage's hearing, × the jolt's dip while it lasts. */
    public static float hearingMultiplier() {
        var c = TinnitusConfig.get();
        double m = stage > 0 ? c.hearing[stage - 1] : 1;
        if (joltLeft > 0) {
            double back = 1 - (double) joltLeft / joltTotal;
            m *= Mth.lerp(back * back * (3 - 2 * back), c.joltHearing, 1);
        }
        return (float) m;
    }

    /**
     * The badge's ring: how bad the ears are, from the first stage (empty) to max_load (full), on a log scale so it drains
     * evenly as the load halves. No countdown beside it (user 2026-10-21: the time kept going up with every shot; as the
     * dehydration badge, the ring alone).
     */
    public static double left() {
        var c = TinnitusConfig.get();
        double from = c.stages[0];
        if (load <= from) return 0;
        return Mth.clamp(Math.log(load / from) / Math.log(c.maxLoad / from), 0, 1);
    }

    private static void schedule(long after) {
        double[] r = TinnitusConfig.get().interval[stage - 1];
        nextSpell = ticks + after + Math.round(20 * Mth.lerp(RANDOM.nextDouble(), r[0], r[1]));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.isPaused()) return;
        ticks++;
        if (joltLeft > 0) joltLeft--;
        var c = TinnitusConfig.get();
        if (load > 0) {
            load *= Math.pow(0.5, (mc.player.isSleeping() ? c.sleep : 1) / c.halfLifeTicks);
            if (load < 0.01) load = 0;
        }
        if (ring != null && (ring.isStopped() || !mc.getSoundManager().isActive(ring))) ring = null;
        if (stage <= 0 || nextSpell < 0 || ticks < nextSpell) return;
        if (ring != null || joltLeft > 0) {
            nextSpell = ticks + 20;
            return;
        }
        double[] len = c.length[stage - 1];
        int length = (int) Math.round(20 * Mth.lerp(RANDOM.nextDouble(), len[0], len[1]));
        // the left ear, the right, or both
        int side = RANDOM.nextInt(3) - 1;
        ring = new Ring(AflSounds.TINNITUS.get(), side, (float) c.volume[stage - 1], length);
        mc.getSoundManager().play(ring);
        schedule(length);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (ring != null) ring.cut();
        ring = null;
        load = 0;
        stage = 0;
        known = false;
        nextSpell = -1;
        joltLeft = joltTotal = 0;
    }

    /** One spell: beside the head (relative, side −1 left, 1 right, 0 both), no distance, its volume under an envelope. */
    public static final class Ring extends AbstractTickableSoundInstance {
        private final float peakVolume;
        private int age, length;

        Ring(SoundEvent sound, int side, float peakVolume, int length) {
            super(sound, SoundSource.PLAYERS, RandomSource.create(RANDOM.nextLong()));
            this.peakVolume = peakVolume;
            this.length = Math.max(FADE_IN + FADE_OUT, length);
            looping = false;
            delay = 0;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            x = side;
            y = 0;
            z = 0;
            volume = 0.001F;
        }

        @Override
        public void tick() {
            age++;
            if (age >= length) {
                stop();
                return;
            }
            float in = Math.min(1, (float) age / FADE_IN), out = Math.min(1, (float) (length - age) / FADE_OUT);
            volume = Math.max(0.001F, peakVolume * in * out);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        void cut() {
            length = Math.min(length, age + FADE_OUT);
        }
    }
}
