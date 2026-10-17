package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.vehicle.HeadlightField;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;

/**
 * Headlight light probe (dev only; src/dev/headlight-probe-client.init.gradle, 2026-10-16): with
 * {@code -Dafl.lightprobe=WORLD} a development client in its own run directory opens a copy of a save, turns the nearest
 * T15's lights on, waits, then writes what the light reads give in front of it (HeadlightField's own level, the stored
 * block light, LevelRenderer#getLightColor) to {@code afl_light_probe.txt} and quits. No camera moves, no captures.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class HeadlightProbe {
    private static final String WORLD = System.getProperty("afl.lightprobe", "");
    private static boolean started;
    private static int ticks = -1;

    private HeadlightProbe() {}

    static {
        if (!WORLD.isEmpty()) {
            Thread watchdog = new Thread(() -> {
                try { Thread.sleep(8 * 60_000L); } catch (InterruptedException e) { return; }
                log("TIMEOUT: halting");
                Runtime.getRuntime().halt(3);
            }, "AFL light probe watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
    }

    private static void log(String s) { ApocalypseFirstLight.LOGGER.info("[AFL LIGHT PROBE] {}", s); }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post event) {
        if (WORLD.isEmpty() || started || !(event.getScreen() instanceof TitleScreen screen)) return;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            log("opening " + WORLD);
            mc.createWorldOpenFlows().loadLevel(screen, WORLD);
        });
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (WORLD.isEmpty() || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        if (ticks == 60) lightsOn(mc);
        if (ticks == 200) {
            String report = probe(mc);
            try {
                Files.writeString(FMLPaths.GAMEDIR.get().resolve("afl_light_probe.txt"), report, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log("write failed " + e);
            }
            log("report written");
            if (mc.level != null) mc.level.disconnect();
            mc.clearLevel(new GenericDirtMessageScreen(Component.literal("AFL light probe")));
            mc.stop();
        }
    }

    private static void lightsOn(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        Vec3 at = mc.player.position();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(18000);
            var trucks = level.getEntitiesOfClass(PickupTruckEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(64));
            trucks.sort(Comparator.comparingDouble(t -> t.distanceToSqr(at)));
            if (trucks.isEmpty()) { log("no truck near " + at); return; }
            PickupTruckEntity t = trucks.get(0);
            try {
                if (!t.headlights()) {
                    var m = PickupTruckEntity.class.getDeclaredMethod("lampSwitch", int.class);
                    m.setAccessible(true);
                    m.invoke(t, PickupTruckEntity.SWITCH_LIGHTS);
                }
                log("lights on: truck at " + t.position() + " yaw " + t.getYRot());
            } catch (ReflectiveOperationException e) {
                log("lamp switch failed " + e);
            }
        });
    }

    private static String probe(Minecraft mc) {
        StringBuilder out = new StringBuilder();
        ClientLevel level = mc.level;
        var trucks = level.getEntitiesOfClass(PickupTruckEntity.class, mc.player.getBoundingBox().inflate(64));
        trucks.sort(Comparator.comparingDouble(t -> t.distanceToSqr(mc.player)));
        out.append("player ").append(mc.player.position()).append(" vehicle ").append(mc.player.getVehicle()).append('\n');
        if (trucks.isEmpty()) return out.append("no truck\n").toString();
        PickupTruckEntity t = trucks.get(0);
        out.append("truck ").append(t.position()).append(" yaw ").append(t.getYRot()).append(" headlights ").append(t.headlights())
                .append(" field cells ").append(HeadlightField.lastCells).append(" cast us ").append(HeadlightField.lastMicros).append('\n');
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double r : new double[]{-1.5, 0, 1.5})
            for (int d = 0; d <= 26; d += 2) {
                Vec3 c = t.point(2.4 + d, r, 0);
                int x = (int) Math.floor(c.x), z = (int) Math.floor(c.z), top = (int) Math.floor(t.getY()) + 3;
                int y = top;
                while (y > top - 8 && level.getBlockState(p.set(x, y, z)).isAir()) y--;
                BlockState ground = level.getBlockState(p.set(x, y, z));
                out.append(String.format("f %4.1f r %4.1f  (%d,%d,%d) %-40s", 2.4 + d, r, x, y, z, BuiltInRegistries.BLOCK.getKey(ground.getBlock())));
                for (int yy = y; yy <= y + 2; yy++) {
                    p.set(x, yy, z);
                    int packed = LevelRenderer.getLightColor(level, p);
                    out.append(String.format("  y%d field %2d stored %2d color %2d", yy, HeadlightField.level(p), level.getBrightness(LightLayer.BLOCK, p),
                            packed >> 4 & 0xF));
                }
                out.append('\n');
            }
        out.append("road client: ").append(com.antaurora.apofirstlight.client.road.RoadClient.status()).append('\n');
        return out.toString();
    }
}
