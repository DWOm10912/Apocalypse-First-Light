package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Flight of the C2 harness player along a path (dev only; -Dafl.c2 with fly=FILE,SPEED and no probe): FILE (in the game
 * directory) holds one "x z y yaw" point per metre (feet), SPEED is in m/s. The player waits flyWait seconds (default 30)
 * at the first point, then is moved along the path every server tick, flying, then waits 5 s at the end and the client
 * quits. With frames=1 (C2FrameLog) the client logs the frame times of the flight: chunks are loaded and their road
 * meshes built for the first time on the way, as when a player flies fast through new country.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class C2Fly {
    private C2Fly() {}

    private static List<double[]> path;
    private static int ticks = -1;
    private static double at;

    static boolean active() { return C2Harness.ARGS.containsKey("fly"); }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !C2Harness.active() || !active()) return;
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        ServerLevel level = server.overworld();
        try {
            if (path == null) path = load();
            ticks++;
            int wait = 20 * Integer.parseInt(C2Harness.arg("flyWait", "30"));
            double speed = Double.parseDouble(C2Harness.arg("fly", "").split(",")[1].trim());
            if (ticks == 0) C2Harness.log("fly: " + path.size() + " m at " + speed + " m/s after " + wait / 20 + " s");
            if (ticks == wait) C2Harness.log("fly: start");
            if (ticks > wait) at = Math.min(path.size() - 1, at + speed / 20);
            double[] p = path.get((int) at);
            for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
                if (!pl.getAbilities().flying) {
                    pl.getAbilities().flying = true;
                    pl.onUpdateAbilities();
                }
                pl.teleportTo(level, p[0], p[2], p[1], (float) p[3], 0F);
            }
            if (at >= path.size() - 1 && ticks > wait) {
                if (ticks % 20 == 0) C2Harness.log("fly: end");
                if (ticks > wait + path.size() * 20 / speed + 100) {
                    ticks = Integer.MIN_VALUE / 2;
                    C2HarnessClient.quit();
                }
            }
        } catch (Throwable t) {
            C2Harness.log("fly failed: " + t);
            ticks = Integer.MIN_VALUE / 2;
            C2HarnessClient.quit();
        }
    }

    private static List<double[]> load() throws IOException {
        String file = C2Harness.arg("fly", "").split(",")[0].trim();
        List<double[]> out = new ArrayList<>();
        for (String line : Files.readAllLines(FMLPaths.GAMEDIR.get().resolve(file))) {
            String[] v = line.trim().split("\\s+");
            if (v.length < 4) continue;
            out.add(new double[]{Double.parseDouble(v[0]), Double.parseDouble(v[1]), Double.parseDouble(v[2]), Double.parseDouble(v[3])});
        }
        if (out.isEmpty()) throw new IOException("empty fly path " + file);
        return out;
    }
}
