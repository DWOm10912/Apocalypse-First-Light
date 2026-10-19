package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.light.DynamicLights;
import com.antaurora.apofirstlight.client.lightning.AflLightning;
import com.antaurora.apofirstlight.client.lightning.LightningFlash;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightning V1 probe (dev only; src/dev/lightning-probe-client.init.gradle, 2026-10-18): with {@code -Dafl.lightningprobe=WORLD}
 * a development client in build/rain-perf opens a copy of a save, makes a thunderstorm at midnight and strikes ahead of the
 * player at 40, 120 and 220 m, one every 3 s. Per bolt it writes to {@code afl_lightning_probe.txt}: whether AFL's renderer
 * drew it and in how many frames, the client ticks the bolt lived, the ticks with a sky flash, the dynamic lights while it
 * flashed, when its thunder and crack sounds started (ticks after the strike, against distance / 343 m/s) and the frame
 * times before and during it; then quits. No captures.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class LightningProbe {
    private static final String WORLD = System.getProperty("afl.lightningprobe", "");
    private static final double[] DISTANCES = {40, 120, 220};
    private static final int WARM = 20 * 20, EACH = 20 * 3;
    private static boolean started;
    private static int ticks = -1, bolt = -1, struckAt;
    private static LightningBolt watched;
    private static String texture;
    private static double distance;
    private static int seenAt = -1, liveTicks, flashTicks, maxLights, thunderAt, crackAt, drawnFrames;
    private static final List<Long> BEFORE = new ArrayList<>(), DURING = new ArrayList<>();
    private static long lastFrame;
    private static final StringBuilder OUT = new StringBuilder();

    private LightningProbe() {}

    static {
        if (!WORLD.isEmpty()) {
            Thread watchdog = new Thread(() -> {
                try { Thread.sleep(6 * 60_000L); } catch (InterruptedException e) { return; }
                log("TIMEOUT: halting");
                Runtime.getRuntime().halt(3);
            }, "AFL lightning probe watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
    }

    private static void log(String s) { ApocalypseFirstLight.LOGGER.info("[AFL LIGHTNING PROBE] {}", s); }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post event) {
        if (WORLD.isEmpty() || started || !(event.getScreen() instanceof TitleScreen screen)) return;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.enableVsync().set(false);
        mc.getWindow().updateVsync(false);
        mc.options.framerateLimit().set(260);
        mc.tell(() -> mc.createWorldOpenFlows().loadLevel(screen, WORLD));
    }

    @SubscribeEvent
    public static void onFrame(TickEvent.RenderTickEvent event) {
        if (WORLD.isEmpty() || event.phase != TickEvent.Phase.END || ticks < 0) return;
        long now = System.nanoTime();
        if (lastFrame != 0) {
            if (watched != null && liveTicks > 0 && !watched.isRemoved()) DURING.add(now - lastFrame);
            else if (watched == null && ticks > WARM - 40) BEFORE.add(now - lastFrame);
        }
        lastFrame = now;
        if (watched != null && !watched.isRemoved()) {
            float[] last = com.antaurora.apofirstlight.client.lightning.LightningShaderData.LAST;
            if (texture == null && AflShaderCompat.shaderPackInUse() && last[0] > 0 && last[20] > 0.5F) {
                float[] gpu = com.antaurora.apofirstlight.client.lightning.LightningShaderData.readBack();
                boolean same = java.util.Arrays.equals(gpu, last);
                texture = String.format("texture: header (%.0f, %.0f, %.0f, %.0f); bolt 1 strike (%.1f, %.1f, %.1f) h %.0f, k (%.2f, %.2f) t %.3f leader %.0f, top (%.1f, %.1f, %.1f); GPU read back %s",
                        last[0], last[1], last[2], last[3], last[16], last[17], last[18], last[19], last[20], last[21], last[22], last[23],
                        last[24], last[25], last[26], same ? "matches" : "DIFFERS: " + java.util.Arrays.toString(gpu));
            }
            maxLights = Math.max(maxLights, DynamicLights.lastLights);
            if (com.antaurora.apofirstlight.client.lightning.LightningRenderer.drawn > 0) {
                drawnFrames++;
                com.antaurora.apofirstlight.client.lightning.LightningRenderer.drawn = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        if (watched == null || event.getSound() == null) return;
        var id = event.getSound().getLocation();
        if (id.equals(SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation()) && thunderAt < 0) thunderAt = ticks - struckAt;
        if (id.equals(SoundEvents.LIGHTNING_BOLT_IMPACT.getLocation()) && crackAt < 0) crackAt = ticks - struckAt;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (WORLD.isEmpty() || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (ticks < 0) {
            ticks = 0;
            OUT.append("world ").append(WORLD).append(", shader pack ").append(AflShaderCompat.shaderPackInUse() ? AflShaderCompat.currentPackLabel() : "off")
                    .append(", dynamic lights ").append(DynamicLights.status()).append('\n');
            mc.getSingleplayerServer().execute(() -> {
                ServerLevel level = mc.getSingleplayerServer().overworld();
                level.setDayTime(18000);
                level.setWeatherParameters(0, 6000, true, true);
            });
        }
        ticks++;
        if (watched != null) {
            if (!watched.isRemoved()) {
                liveTicks++;
                if (mc.level.getSkyFlashTime() > 0) flashTicks++;
            }
        }
        // the client copy of the bolt the server struck
        if (watched == null && bolt >= 0 && ticks > struckAt) {
            for (Entity e : mc.level.entitiesForRendering())
                if (e instanceof LightningBolt b && !b.isRemoved()) watched = b;
            if (watched != null) seenAt = ticks - struckAt;
        }
        if (ticks >= WARM && (ticks - WARM) % EACH == 0) {
            if (bolt >= 0) report(mc);
            bolt++;
            if (bolt >= DISTANCES.length) {
                try { Files.writeString(Path.of("afl_lightning_probe.txt"), OUT.toString()); } catch (Exception e) { log("write failed: " + e); }
                log("\n" + OUT);
                mc.stop();
                return;
            }
            distance = DISTANCES[bolt];
            Vec3 look = mc.player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            Vec3 at = mc.player.position().add(flat.scale(distance));
            struckAt = ticks;
            watched = null;
            liveTicks = flashTicks = maxLights = drawnFrames = 0;
            thunderAt = crackAt = seenAt = -1;
            texture = null;
            DURING.clear();
            mc.getSingleplayerServer().execute(() -> {
                ServerLevel level = mc.getSingleplayerServer().overworld();
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(at));
                LightningBolt b = EntityType.LIGHTNING_BOLT.create(level);
                if (b == null) return;
                b.moveTo(Vec3.atBottomCenterOf(top));
                level.addFreshEntity(b);
            });
        }
    }

    private static void report(Minecraft mc) {
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        String flash = "no client bolt seen";
        if (watched != null) {
            LightningFlash f = AflLightning.flash(watched);
            flash = String.format("height %.0f, strands %d, strokes end %.0f ms, camera %.0f m", f.height, f.channel.strands.size(),
                    f.end() * 1000, cam.distanceTo(watched.position()));
        }
        OUT.append(String.format("bolt at %.0f m: %s; drawn in %d frames; client ticks %d (removed %s); sky flash ticks %d; lights max %d; "
                        + "client bolt seen after %d ticks; thunder after %d ticks, crack after %d (expected %.1f at 343 m/s; set %d / %d); frames before %s, during %s%n",
                distance, flash, drawnFrames, liveTicks, watched != null && watched.isRemoved(), flashTicks, maxLights, seenAt, thunderAt, crackAt,
                distance / 343 * 20, AflLightning.lastThunderDelay, AflLightning.lastCrackDelay, stats(BEFORE), stats(DURING)));
        if (texture != null) OUT.append("  ").append(texture).append('\n');
        else if (AflShaderCompat.shaderPackInUse()) OUT.append("  texture: no frame with a lit bolt seen\n");
        BEFORE.clear();
    }

    private static String stats(List<Long> frames) {
        if (frames.isEmpty()) return "-";
        List<Long> s = new ArrayList<>(frames);
        s.sort(Long::compare);
        double mean = s.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        return String.format("mean %.2f ms, worst %.2f ms (%d)", mean, s.get(s.size() - 1) / 1e6, s.size());
    }
}
