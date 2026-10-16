package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.road.RoadClient;
import com.antaurora.vextris.client.GpuGeometry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * Frame times of the C2 harness client (dev only; -Dafl.c2 with frames=1): while a world is open, every frameWindow
 * seconds (default 5) the mean, 95th percentile and longest frame time, the road client's render-thread time in that
 * window (RoadClient.NANOS: light lookups, geometry builds, light uploads, draws, client tick) and the Vextris GpuGeometry
 * counters are logged; every frame over 33 ms is logged on its own with the road client's share of it. Used for the road
 * GPU cache self-test (docs/worldgen/federal_highways_c3.md, 10.5).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class C2FrameLog {
    private C2FrameLog() {}

    private static final long[] FRAMES = new long[8192];
    private static final long[] WINDOW = new long[5];
    private static int count, windowLights, windowBuilds, spikes;
    private static long last, windowStart;

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !C2Harness.active() || !"1".equals(C2Harness.arg("frames", ""))) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        long[] road = RoadClient.NANOS.clone();
        int lights = RoadClient.COUNTS[0], builds = RoadClient.COUNTS[1];
        Arrays.fill(RoadClient.NANOS, 0);
        Arrays.fill(RoadClient.COUNTS, 0);
        if (mc.level == null || mc.player == null) { last = 0; count = 0; windowStart = 0; return; }
        if (last != 0) {
            long frame = now - last;
            if (count < FRAMES.length) FRAMES[count++] = frame;
            for (int i = 0; i < road.length; i++) WINDOW[i] += road[i];
            windowLights += lights;
            windowBuilds += builds;
            if (frame > 33_000_000L && spikes++ < 400)
                C2Harness.log(String.format("spike %.1f ms at %.0f %.0f %.0f: road lights %.1f (%d chunks), build %.1f (%d), upload %.1f, draw %.1f, tick %.1f",
                        frame / 1e6, mc.player.getX(), mc.player.getY(), mc.player.getZ(), road[0] / 1e6, lights, road[1] / 1e6, builds,
                        road[2] / 1e6, road[3] / 1e6, road[4] / 1e6));
        }
        last = now;
        if (windowStart == 0) windowStart = now;
        long window = (long) (1e9 * Double.parseDouble(C2Harness.arg("frameWindow", "5")));
        if (now - windowStart < window) return;
        windowStart = now;
        if (count == 0) return;
        long[] f = Arrays.copyOf(FRAMES, count);
        Arrays.sort(f);
        long sum = 0;
        for (long v : f) sum += v;
        var s = GpuGeometry.stats();
        C2Harness.log(String.format("frames %d: mean %.2f ms (%.0f fps), p95 %.2f ms, max %.1f ms; player %.0f %.0f %.0f; road ms lights %.1f (%d chunks) build %.1f (%d) upload %.1f draw %.1f tick %.1f; gpu geometry builds %d, light uploads %d, draws %d",
                count, sum / 1e6 / count, 1e9 * count / sum, f[(int) (count * 0.95)] / 1e6, f[count - 1] / 1e6, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                WINDOW[0] / 1e6, windowLights, WINDOW[1] / 1e6, windowBuilds, WINDOW[2] / 1e6, WINDOW[3] / 1e6, WINDOW[4] / 1e6,
                s.builds, s.lightUploads, s.draws));
        Arrays.fill(WINDOW, 0);
        windowLights = windowBuilds = 0;
        count = 0;
    }
}
