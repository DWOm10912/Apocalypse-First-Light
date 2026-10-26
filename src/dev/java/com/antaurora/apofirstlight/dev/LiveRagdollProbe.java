package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FirstPersonBody;
import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.skeleton.FallLandingClient;
import com.antaurora.apofirstlight.client.skeleton.PlayerSkeleton;
import com.antaurora.apofirstlight.client.skeleton.ProneClient;
import com.antaurora.apofirstlight.client.survivor.LiveRagdollClient;
import com.antaurora.apofirstlight.health.ClientKnockdown;
import com.antaurora.apofirstlight.survivor.LiveRagdolls;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Live ragdoll probe, the client half (dev only, 2026-10-22): with {@code -Dafl.liveragdollprobe=sp} (src/dev/live-ragdoll-probe-client.init.gradle,
 * a copy of a save in build/live-ragdoll-probe) or {@code =mp} (src/dev/live-ragdoll-probe-server.init.gradle, joining the probe's
 * dedicated server) the player is hit by a T15 on LiveRagdollServerProbe's schedule: in first person, in third person, at low
 * health in first person. From each hit on (seen as the health dropping) it writes every tick the client's (and in sp the server's) ragdoll state, the camera
 * request, the first-person body and hands, whether the vanilla hands drew, the collapse and the crawl to
 * {@code afl_live_ragdoll_probe_<mode>.txt} and captures a screenshot every {@link #EVERY} ticks; then quits.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class LiveRagdollProbe {
    private static final String MODE = System.getProperty("afl.liveragdollprobe", "");
    private static final boolean ON = !MODE.isEmpty();
    private static final String[] RUNS = {"fp", "tp", "low"};
    private static final int AFTER = 220, EVERY = 4;
    private static int ticks, run = -1, since;
    private static float health = -1;
    private static boolean done;
    private static int hands, handsFrame;
    private static final StringBuilder OUT = new StringBuilder();

    private LiveRagdollProbe() {
    }

    static {
        if (ON) {
            Thread watchdog = new Thread(() -> {
                try { Thread.sleep(10 * 60_000L); } catch (InterruptedException e) { return; }
                ApocalypseFirstLight.LOGGER.error("[AFL LIVE RAGDOLL PROBE] TIMEOUT: halting");
                Runtime.getRuntime().halt(3);
            }, "AFL live ragdoll probe watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
    }

    /** Vanilla's hands drew this frame (nothing cancelled RenderHandEvent). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hand(RenderHandEvent event) {
        if (ON && !event.isCanceled()) hands++;
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (ON && event.phase == TickEvent.Phase.START) handsFrame++;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ON || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        mc.options.pauseOnLostFocus = false;
        ticks++;
        if (ticks == LiveRagdollServerProbe.SETUP) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            OUT.append(String.format("mode %s; config: showBody %s, skeleton %s, PlayerSkeleton.enabled %s%n", MODE, FirstPersonBodyConfig.showBody(),
                    FirstPersonBodyConfig.skeleton(), PlayerSkeleton.enabled()));
        }
        // a hit: the health drops (the server's clock runs ahead of this one by the time the world took to load)
        float now = mc.player.getHealth();
        boolean hit = ticks > LiveRagdollServerProbe.SETUP && health - now > 5;
        health = now;
        if (hit && (run < 0 || since > 20)) {
            run++;
            since = 0;
            OUT.append(String.format("-- hit %d (%s) at client tick %d%n", run, RUNS[Math.min(run, RUNS.length - 1)], ticks));
        }
        if (run < 0 || run >= RUNS.length) return;
        if (since <= AFTER) {
            log(mc, RUNS[run], since);
            if (since % EVERY == 0) shot(String.format("%s_%s_t%03d", MODE, RUNS[run], since));
        }
        if (++since == AFTER) {
            write();
            if (run == RUNS.length - 1) {
                done = true;
                ApocalypseFirstLight.LOGGER.info("[AFL LIVE RAGDOLL PROBE] done");
                mc.stop();
            } else {
                mc.options.setCameraType(run + 1 == 1 ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
            }
        }
    }

    private static void write() {
        try { Files.writeString(Path.of("afl_live_ragdoll_probe_" + MODE + ".txt"), OUT.toString()); } catch (Exception e) { OUT.append("write failed ").append(e); }
    }

    private static void log(Minecraft mc, String run, int since) {
        var player = mc.player;
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = player.getEyePosition(), at = camera.getPosition();
        String server = "-";
        if (mc.getSingleplayerServer() != null) {
            ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(player.getUUID());
            server = String.valueOf(sp != null && LiveRagdolls.active(sp));
        }
        OUT.append(String.format("%s t%03d: server %s; client falling %s active %s fallingOver %s plays %s; camera open %s hud %s, cam-eye %.2f "
                        + "x %.0f y %.0f; body shown %s, hands %s, supportedPose %s pose %s prone %s collapsed %s; vanilla hands %d/%d frames; "
                        + "health %.1f; screen %s; pos %.2f %.2f %.2f%n",
                run, since, server, LiveRagdollClient.ownFalling(), LiveRagdollClient.active(player), FallLandingClient.fallingOver(),
                FallLandingClient.plays(player), CameraDirector.isOpen("afl:live_ragdoll"), CameraDirector.hud(), at.distanceTo(eye),
                camera.getXRot(), camera.getYRot(), FirstPersonBody.shown(camera), PlayerSkeleton.firstPersonHands(player),
                PlayerSkeleton.supportedPose(player), player.getPose(), ProneClient.prone(player), ClientKnockdown.collapsed(), hands, handsFrame,
                player.getHealth(), mc.screen == null ? "-" : mc.screen.getClass().getSimpleName(), player.getX(), player.getY(), player.getZ()));
        hands = 0;
        handsFrame = 0;
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "live_ragdoll_" + name + ".png", mc.getMainRenderTarget(), m -> {});
    }
}
