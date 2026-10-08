package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Development-only timing of AFL's block entity renderers (2026-10-08: the user's Fuel Stop A1 stutters when the view
 * turns toward the back of house, worst with Sundial). Every renderer registered in {@link AflBlockEntityRenderers} goes
 * through {@link AflBlockEntityRendering#wrap}, which times it here; outside the development environment nothing is timed
 * (start the game with -Dafl.renderProfiler=true to time a release build). Every 5 s the log gets one line: frames, frame time, and per
 * renderer the calls per frame and the CPU time per frame in the main pass and in the shader pack's shadow pass (the
 * time spent filling the vertex buffers; the GPU draw happens later and is not in these numbers). Renderers can also time
 * their own sections ({@link #begin} / {@link #end}: nested, so a section's time is also inside its renderer's). Each line
 * names the active shader pack and the optimization switches ({@link AflRenderDev}), so a before / after comparison can
 * check that it ran under the same pack and the intended switches (tools/afl_minecraft_mcp/render_benchmark.mjs reads
 * these lines).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AflRenderProfiler {
    public static final boolean ENABLED = !FMLEnvironment.production || Boolean.getBoolean("afl.renderProfiler");
    private static final long WINDOW = 5_000_000_000L;
    /** per renderer: main calls, main nanos, main max nanos, shadow calls, shadow nanos */
    private static final Map<String, long[]> STATS = new HashMap<>();
    private static long frames, frameNanos, lastFrame, windowStart;

    private AflRenderProfiler() {}

    /** Start of a timed section inside a renderer (0 when the profiler is off). */
    public static long begin() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    /** End of a section started with {@link #begin}. */
    public static void end(String name, long start) {
        if (start != 0L) record(name, System.nanoTime() - start);
    }

    /** A call that did no work (a block entity left out of the shadow pass), counted under its own name. */
    public static void count(String name) {
        if (ENABLED) record(name, 0L);
    }

    private static void record(String name, long nanos) {
        long[] s = STATS.computeIfAbsent(name, k -> new long[5]);
        if (AflShaderCompat.activeShadowPass()) { s[3]++; s[4] += nanos; }
        else { s[0]++; s[1] += nanos; s[2] = Math.max(s[2], nanos); }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        long now = System.nanoTime();
        if (lastFrame != 0) frameNanos += now - lastFrame;
        lastFrame = now;
        frames++;
        if (windowStart == 0) windowStart = now;
        if (now - windowStart < WINDOW) return;
        if (!STATS.isEmpty() && frames > 1) {
            double f = frames;
            var rows = new ArrayList<>(STATS.entrySet());
            rows.sort((a, b) -> Long.compare(b.getValue()[1] + b.getValue()[4], a.getValue()[1] + a.getValue()[4]));
            // sections (names with a '.') are inside their renderer's time: the total counts renderers only
            long total = 0;
            for (var e : rows) if (e.getKey().indexOf('.') < 0) total += e.getValue()[1] + e.getValue()[4];
            var line = new StringBuilder(String.format("[AFL RENDER PROFILE] pack=%s; switches=%s; %d frames, %.2f ms/frame (%.0f fps); AFL BERs %.2f ms/frame |",
                    AflShaderCompat.currentPackLabel(), AflRenderDev.label(), frames, frameNanos / f / 1e6, f * 1e9 / Math.max(1, frameNanos), total / f / 1e6));
            for (var e : rows) {
                long[] s = e.getValue();
                line.append(String.format(" %s: %.1f+%.1f calls, %.3f+%.3f ms, max %.3f ms |", e.getKey(), s[0] / f, s[3] / f,
                        s[1] / f / 1e6, s[4] / f / 1e6, s[2] / 1e6));
            }
            ApocalypseFirstLight.LOGGER.info(line.toString());
        }
        STATS.clear();
        frames = 0; frameNanos = 0; windowStart = now;
    }
}
