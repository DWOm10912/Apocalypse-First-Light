package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.FuelStainIndex;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the fire costs this client, on the F3 screen (2026-10-05, docs/gameplay/fuel_fire_v1.md "性能"; a burning fuel
 * station still cost a lot of frame time, user): the CPU time of each part of the fuel stains' world pass
 * (ClientFuelStains#render, each part up to and including its draw), averaged over a second, what is drawn, and how
 * often a fire's light block came or went (each one relights the blocks round it and rebuilds their chunk sections, not
 * counted in the pass). Timed only while F3 is open. GPU time is not measured.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FireStats {
    static final int HOLES = 0, GROUND = 1, STREAMS = 2, SMOKE = 3, FLAMES = 4, GLOWS = 5;
    private static final String[] NAMES = {"弹孔", "地面", "漏油", "烟", "火焰", "火光/火星"};
    private static final long[] SUM = new long[NAMES.length];
    private static final double[] SHOWN = new double[NAMES.length];
    private static boolean on;
    private static long last, window;
    private static int frames, lightChanges, lightRate;

    private FireStats() {
    }

    /** The pass starts (timed while F3 is open). */
    static void begin() {
        boolean debug = Minecraft.getInstance().options.renderDebug;
        long now = System.nanoTime();
        if (debug && !on) window = now;
        on = debug;
        last = now;
    }

    /** {@code part} is done (drawn). */
    static void lap(int part) {
        if (!on) return;
        long now = System.nanoTime();
        SUM[part] += now - last;
        last = now;
    }

    /** The pass is over: once a second the averages are taken. */
    static void end() {
        if (!on) return;
        frames++;
        long now = System.nanoTime();
        if (now - window < 1_000_000_000L) return;
        for (int k = 0; k < SUM.length; k++) {
            SHOWN[k] = SUM[k] / 1e6 / frames;
            SUM[k] = 0;
        }
        lightRate = Math.round(lightChanges * 1e9F / (now - window));
        lightChanges = 0;
        frames = 0;
        window = now;
    }

    /** A light block came or went on this client (LevelRendererFireTrackMixin). */
    public static void lightChanged() {
        lightChanges++;
    }

    @SubscribeEvent
    public static void debugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!Minecraft.getInstance().options.renderDebug) return;
        int stains = 0, burning = 0;
        for (FuelStainIndex.Stain s : FuelStainIndex.CLIENT.all()) {
            stains++;
            if (s.burning()) burning++;
        }
        if (stains == 0 && Scorches.isEmpty() && FireFx.isEmpty() && FireBlockFlames.isEmpty() && BulletHoles.isEmpty()) return;
        double total = 0;
        StringBuilder parts = new StringBuilder();
        for (int k = 0; k < NAMES.length; k++) {
            total += SHOWN[k];
            parts.append(k == 0 ? "" : " · ").append(NAMES[k]).append(' ').append(String.format("%.2f", SHOWN[k]));
        }
        var left = event.getLeft();
        left.add("");
        left.add(String.format("[AFL 火场] 每帧 %.2f ms（CPU）：", total) + parts);
        left.add(String.format("[AFL 火场] 油斑 %d（燃烧 %d）· 油池 %d 格 %d 顶点 · 焦痕 %d 格 %d 顶点（余烬另计，最多 3 层）",
                stains, burning, FuelPuddleMesher.cellCount(), FuelPuddleMesher.vertexCount(), Scorches.cellCount(), Scorches.vertexCount()));
        left.add(String.format("[AFL 火场] 烟 %d · 火星 %d · 火方块 %d · 光源方块变化 %d 次/秒",
                FireFx.puffCount(), FireFx.sparkCount(), FireBlockFlames.count(), lightRate));
    }
}
