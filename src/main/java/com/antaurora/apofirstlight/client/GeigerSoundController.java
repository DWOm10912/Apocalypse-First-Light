package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.ThreadLocalRandom;

/** Client-only, one-shot Geiger click scheduler driven by the synced instrument data. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GeigerSoundController {
    private static final double MAX_CPS = 20.0D;

    private GeigerSoundController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !isHeld(minecraft)) {
            return;
        }

        ClientGeigerData.Snapshot data = ClientGeigerData.snapshot();
        if (data.stale()) {
            return;
        }

        double measuredRate = data.measuredRate();
        double cps = radiationCps(measuredRate);
        if (cps <= 0.0) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() < Math.min(1.0, cps / MAX_CPS)) {
            float pitch = ThreadLocalRandom.current().nextFloat(0.95F, 1.05F);
            float volume = ThreadLocalRandom.current().nextFloat(0.90F, 1.00F);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AflSounds.GEIGER_CLICK.get(), pitch, volume));
        }
    }

    private static boolean isHeld(Minecraft minecraft) {
        return minecraft.player.getMainHandItem().is(AflItems.GEIGER_COUNTER.get())
                || minecraft.player.getOffhandItem().is(AflItems.GEIGER_COUNTER.get());
    }

    private static double radiationCps(double rate) {
        if (rate >= ClientGeigerData.GEIGER_MAX_RATE) return MAX_CPS;
        return interpolate(rate, new double[][]{
                {0.00, 0.0}, {0.10, 0.25}, {0.50, 0.75}, {1.50, 2.0},
                {3.00, 4.0}, {6.00, 7.0}, {9.00, 10.0}, {12.0, 12.0},
                {ClientGeigerData.GEIGER_MAX_RATE, MAX_CPS}
        });
    }

    private static double interpolate(double value, double[][] points) {
        if (value <= points[0][0]) {
            return points[0][1];
        }
        for (int index = 1; index < points.length; index++) {
            if (value <= points[index][0]) {
                double[] lower = points[index - 1];
                double[] upper = points[index];
                double progress = (value - lower[0]) / (upper[0] - lower[0]);
                return lower[1] + (upper[1] - lower[1]) * progress;
            }
        }
        return points[points.length - 1][1];
    }
}
