package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.rain.RainRenderer;
import com.antaurora.apofirstlight.client.rain.WindshieldRenderer;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Rain frame time probe (dev only; src/dev/rain-perf-client.init.gradle, 2026-10-17): with {@code -Dafl.rainperf=WORLD} a
 * development client in its own run directory opens a copy of a save with a T15, makes it rain at noon, seats the player
 * in the driver's seat looking ahead (first person), lets the glass get wet, then runs phases of PHASE seconds each
 * (rain + glass water, glass water off, rain off, both off; then the same from third person) and writes the frame times
 * (mean, 95th percentile, worst) and the rain / glass water status lines (CPU and GPU parts) of each to
 * {@code afl_rain_perf.txt}, then quits. Vsync off, frame cap 260. No captures.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class RainPerfProbe {
    private static final String WORLD = System.getProperty("afl.rainperf", "");
    private static final int WARM = 20 * 25, SETTLE = 20 * 6, PHASE = 20 * 12 + SETTLE;
    /** -Dafl.rainperf.shaders=true: the shader pack is on (its own rain from AFL's data, the glass map baked by AFL). */
    private static final boolean SHADERS = Boolean.getBoolean("afl.rainperf.shaders");
    /** -Dafl.rainperf.walk=true: on foot 80 m from the truck with a BR-51 in hand, no shader pack (first person). */
    private static final boolean WALK = Boolean.getBoolean("afl.rainperf.walk");
    /**
     * -Dafl.rainperf.here=true: where the save left the player, its time and weather as they are (a copy of the player's
     * own world: the frame rate there, not at the truck), first person; looking as saved, then turned round; then third
     * person from behind and in front (the guns carried on the body in the captures).
     */
    private static final boolean HERE = Boolean.getBoolean("afl.rainperf.here");
    /** -Dafl.rainperf.hud=true (with here): survival; the HUD gauges steady / all redrawn every frame / the HUD hidden. */
    private static final boolean HUD = Boolean.getBoolean("afl.rainperf.hud");
    private static final String[] PHASES = HERE && HUD
            ? new String[]{"hud: survival, gauges steady", "hud: every gauge moving (GPU bands)", "hud: every gauge moving (CPU raster, as before)", "hud: hidden (F1)"}
            : HERE
            ? new String[]{"here: as saved", "here: turned round", "here: third person, behind", "here: third person, in front"}
            : WALK
            ? new String[]{"walk: rain all on (gun)", "walk: splashes off", "walk: AFL rain off (vanilla rain)", "walk: clear weather", "walk: clear weather, empty hand"}
            : SHADERS
            ? new String[]{"pack: all on (cab)", "pack: glass bake off (cab)", "pack: clear weather (cab)", "pack: all on (3rd person)", "pack: clear weather (3rd person)", "pack: outside, 3.5 m ahead of the windshield"}
            : new String[]{"all on (cab)", "glass water off (cab)", "rain off (cab)", "both off (cab)", "all on (3rd person)", "both off (3rd person)"};
    private static boolean started;
    private static int ticks = -1, seatedAt = -1, phase = -1;
    private static final List<Long> FRAMES = new ArrayList<>();
    private static long lastFrame, phaseStart;
    private static final StringBuilder OUT = new StringBuilder();

    private RainPerfProbe() {}

    static {
        if (!WORLD.isEmpty()) {
            Thread watchdog = new Thread(() -> {
                try { Thread.sleep(10 * 60_000L); } catch (InterruptedException e) { return; }
                log("TIMEOUT: halting");
                Runtime.getRuntime().halt(3);
            }, "AFL rain perf watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
    }

    private static void log(String s) { ApocalypseFirstLight.LOGGER.info("[AFL RAIN PERF] {}", s); }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post event) {
        if (WORLD.isEmpty() || started || !(event.getScreen() instanceof TitleScreen screen)) return;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.enableVsync().set(false);
        mc.getWindow().updateVsync(false);
        mc.options.framerateLimit().set(260);
        mc.tell(() -> {
            log("opening " + WORLD);
            mc.createWorldOpenFlows().loadLevel(screen, WORLD);
        });
    }

    @SubscribeEvent
    public static void onFrame(TickEvent.RenderTickEvent event) {
        if (WORLD.isEmpty() || event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        if (phase >= 0 && lastFrame != 0) FRAMES.add(now - lastFrame);
        lastFrame = now;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (WORLD.isEmpty() || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        if (ticks == 60 && !HERE) setUp(mc);
        if (ticks == 80 && !WALK && !HERE) sitDown(mc);
        if (HERE && seatedAt < 0 && ticks == 100) {
            seatedAt = ticks;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (HUD) {
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> server.getPlayerList().getPlayers().get(0).setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
            }
            log("here at " + mc.player.position() + ", yaw " + mc.player.getYRot() + ", rain " + mc.level.isRaining() + ", time " + mc.level.getDayTime());
        }
        if (WALK && seatedAt < 0 && ticks == 100) {
            seatedAt = ticks;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setXRot(5);
            log("on foot at " + mc.player.position() + ", hand " + mc.player.getMainHandItem());
        }
        if (seatedAt < 0 && mc.player.getVehicle() instanceof PickupTruckEntity truck) {
            seatedAt = ticks;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            // straight ahead through the windshield (a point 6 m ahead of the driver, a little below the eyes)
            float[] look = look(mc.player.getEyePosition(), truck.framePoint(6, -0.37, 1.1, 1F));
            mc.player.setYRot(look[0]);
            mc.player.setYHeadRot(look[0]);
            mc.player.setXRot(look[1]);
            log("seated at tick " + ticks + ", truck " + truck.position());
        }
        if (ticks == 400 && seatedAt < 0) {
            OUT.append("not seated\n");
            finish(mc);
            return;
        }
        if (seatedAt < 0) return;
        int t = ticks - seatedAt - WARM;
        if (t >= 0 && phase >= 0 && t % PHASE == SETTLE) { FRAMES.clear(); phaseStart = System.currentTimeMillis(); }   // weather and camera settled: measure from here
        if (t < 0 || t % PHASE != 0) return;
        if (phase >= 0) report(PHASES[phase]);
        phase++;
        if (phase >= PHASES.length) {
            finish(mc);
            return;
        }
        boolean third = !WALK && !HERE && (SHADERS ? phase == 3 || phase == 4 : phase >= 4);
        mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        if (HERE && HUD) {
            // "moving": every gauge's texture redrawn every frame (with the GPU bands only the glyph textures remain)
            com.antaurora.apofirstlight.client.AflGauge.forceRedraw = phase == 1 || phase == 2;
            com.antaurora.apofirstlight.client.AflGaugeProgram.enabled = phase != 2;
            mc.options.hideGui = phase == 3;
            com.antaurora.apofirstlight.client.AflGauge.status();   // counts from the phase's start
        } else if (HERE) {
            if (phase >= 2) mc.options.setCameraType(phase == 2 ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
            if (phase == 1) {
                mc.player.setYRot(mc.player.getYRot() + 180);
                mc.player.setYHeadRot(mc.player.getYRot());
            }
        } else if (WALK) {
            RainRenderer.splashes = phase != 1;
            RainRenderer.enabled = phase != 2;
            weather(mc, phase <= 2);
            if (phase == 4) {
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> server.getPlayerList().getPlayers().get(0).getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY));
            }
        } else if (SHADERS) {
            WindshieldRenderer.enabled = phase != 1;
            weather(mc, phase != 2 && phase != 4);
            if (phase == 5) standAhead(mc);
        } else {
            boolean both = phase == 0 || phase == 4;
            WindshieldRenderer.enabled = both || phase == 2;
            RainRenderer.enabled = both || phase == 1;
        }
        FRAMES.clear();
        log("phase " + PHASES[phase]);
    }

    private static void weather(Minecraft mc, boolean rain) {
        var server = mc.getSingleplayerServer();
        if (server != null) server.execute(() -> server.overworld().setWeatherParameters(rain ? 0 : 24000, rain ? 24000 : 0, rain, false));
    }

    private static void setUp(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        Vec3 at = mc.player.position();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.setWeatherParameters(0, 24000, true, false);
            var trucks = level.getEntitiesOfClass(PickupTruckEntity.class, new AABB(at, at).inflate(160));   // a walk run leaves the player 80 m away in the save
            trucks.sort(Comparator.comparingDouble(tr -> tr.distanceToSqr(at)));
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            if (WALK) {
                // 80 m from the nearest truck (no glass water, no truck in the rain data), on the ground, gun in hand
                Vec3 base = trucks.isEmpty() ? at : trucks.get(0).position();
                int x = (int) Math.floor(base.x + 80), z = (int) Math.floor(base.z);
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                player.teleportTo(level, x + 0.5, y, z + 0.5, 90, 5);
                player.getInventory().setItem(com.antaurora.apofirstlight.inventory.HotbarRoles.PRIMARY_SLOT,
                        new net.minecraft.world.item.ItemStack(com.antaurora.apofirstlight.registry.AflItems.BR51_01.get()));
                player.getInventory().selected = com.antaurora.apofirstlight.inventory.HotbarRoles.PRIMARY_SLOT;
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(com.antaurora.apofirstlight.inventory.HotbarRoles.PRIMARY_SLOT));
                return;
            }
            if (trucks.isEmpty()) { log("no truck near " + at); return; }
            PickupTruckEntity truck = trucks.get(0);
            // beside the driver's door first; seated a second later (sitDown): seated in the same tick as the teleport the
            // client kept the teleport spot (above the cab) and the camera never was in the cab
            Vec3 door = truck.framePoint(-0.2, -2.2, 0, 1F);
            player.teleportTo(level, door.x, truck.getY(), door.z, truck.getYRot(), 0);
            seatTruck = truck;
        });
    }

    private static PickupTruckEntity seatTruck;

    private static void sitDown(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null || seatTruck == null) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            try {
                var sit = PickupTruckEntity.class.getDeclaredMethod("sit", Player.class, int.class);
                sit.setAccessible(true);
                log("sit " + sit.invoke(seatTruck, player, 0));
            } catch (ReflectiveOperationException e) {
                log("sit failed " + e);
            }
        });
    }

    /** Shader mode's last phase: out of the cab, 3.5 m ahead of the windshield, looking back at it. */
    private static void standAhead(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null || seatTruck == null) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.stopRiding();
            Vec3 at = seatTruck.framePoint(4.2, -0.2, 0, 1F);
            float[] look = look(new Vec3(at.x, seatTruck.getY() + 1.62, at.z), seatTruck.framePoint(1.0, 0, 1.45, 1F));
            player.teleportTo(server.overworld(), at.x, seatTruck.getY(), at.z, look[0], look[1]);
        });
    }

    /** Minecraft yaw and pitch (degrees) from an eye towards a point. */
    private static float[] look(Vec3 eye, Vec3 at) {
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        return new float[]{(float) Math.toDegrees(Math.atan2(dz, dx)) - 90F, (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    private static void report(String name) {
        long[] f = FRAMES.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(f);
        double mean = Arrays.stream(f).average().orElse(0) / 1e6;
        double p95 = f.length == 0 ? 0 : f[Math.min(f.length - 1, (int) (f.length * 0.95))] / 1e6;
        double worst = f.length == 0 ? 0 : f[f.length - 1] / 1e6;
        OUT.append(String.format("   wall ms %d-%d%n", phaseStart, System.currentTimeMillis()));
        OUT.append(String.format("== %s: %d frames, mean %.2f ms (%.0f fps), p95 %.2f ms, worst %.2f ms%n", name, f.length, mean, mean > 0 ? 1000 / mean : 0, p95, worst));
        OUT.append("   rain: ").append(RainRenderer.status()).append('\n');
        OUT.append("   glass: ").append(WindshieldRenderer.status()).append('\n');
        if (HUD) OUT.append("   gauges (whole phase): ").append(com.antaurora.apofirstlight.client.AflGauge.status()).append('\n');
        if (SHADERS || WALK || HERE) frame(Minecraft.getInstance(), phase);
        if (SHADERS && !HERE) {
            int[] g =com.antaurora.apofirstlight.client.rain.RainShaderPack.debugGlassSample();
            OUT.append("   glass map, windshield middle (slot 0): ").append(g == null ? "not read"
                    : "R " + g[0] + " G " + g[1] + " B (cover) " + g[2] + ", baked " + (g[3] == 1)).append('\n');
        }
        log(OUT.substring(OUT.lastIndexOf("== ")));
    }

    /** The test client's last frame (its own run directory): rainperf_<phase>.png and the mean brightness in the middle. */
    private static void frame(Minecraft mc, int phase) {
        try (var img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = img.getWidth(), h = img.getHeight();
            long sum = 0;
            int n = 0;
            for (int y = h / 2 - 64; y < h / 2 + 64; y += 4)
                for (int x = w / 2 - 64; x < w / 2 + 64; x += 4) {
                    int c = img.getPixelRGBA(x, y);
                    sum += (c & 255) + (c >> 8 & 255) + (c >> 16 & 255);
                    n += 3;
                }
            OUT.append("   screen middle mean ").append(sum / n).append(" (0-255); rainperf_").append(phase).append(".png\n");
            img.writeToFile(FMLPaths.GAMEDIR.get().resolve("rainperf_" + phase + ".png"));
        } catch (Exception e) {
            OUT.append("   screen read failed ").append(e).append('\n');
        }
    }

    private static void finish(Minecraft mc) {
        Minecraft m = Minecraft.getInstance();
        OUT.insert(0, "GL " + com.mojang.blaze3d.platform.GlUtil.getRenderer() + (SHADERS ? "; shader pack on" : "") + "; window " + m.getWindow().getWidth() + "x" + m.getWindow().getHeight() + "\n");
        try {
            Files.writeString(FMLPaths.GAMEDIR.get().resolve("afl_rain_perf.txt"), OUT.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("write failed " + e);
        }
        log("report written");
        if (mc.level != null) mc.level.disconnect();
        mc.clearLevel(new GenericDirtMessageScreen(Component.literal("AFL rain perf")));
        mc.stop();
    }
}
