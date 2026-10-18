package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.rain.RainShaderPack;
import com.antaurora.apofirstlight.client.rain.WindshieldRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * One-frame glitch watch (dev only; -Dafl.rainperf.glitch=true with the rain probe's shader drive, 2026-10-18): the user's
 * alpha.56 videos showed single frames of broken windshield / door glass (spikes, shifted copies of the scene). Each frame
 * this reads back (a wait: the frame rates of such a run mean nothing) what AFL hands the pack, and the finished frame:
 * <ul>
 * <li>AFL's side: the glass map bakes and trucks texture uploads this frame (one each expected), the trucks in it, and per
 * baked pane the glass map's mean cover and its texels at the lens threshold (RainShaderPack.debugPaneCover);</li>
 * <li>the frame: the main target scaled to SW x SH, cut into tiles; frame N is a one-frame event where a tile of it
 * differs from both N - 1 and N + 1 by much more than N - 1 and N + 1 differ from each other.</li>
 * </ul>
 * An event is logged with AFL's data of the three frames, the worst ones saved as glitch_<n>.png (N - 1, N, N + 1 side by
 * side); a one-frame jump in AFL's own data is counted on its own. RainPerfProbe's report has the totals.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class GlitchWatch {
    static final boolean ON = Boolean.getBoolean("afl.rainperf.glitch");
    private static final int SW = 960, SH = 540, TILE = 30, TX = SW / TILE, TY = SH / TILE;
    /** A tile's one-frame score (0-255 luminance) for an event, and the most frames saved. */
    private static final int EVENT = 14, SAVES = 12;
    private static final ByteBuffer[] RING = new ByteBuffer[3];
    private static final float[][] TILES = new float[3][TX * TY];
    private static final String[] AFL = new String[3];
    private static final int[][][] COVER = new int[3][][];
    private static int fbo = -1, color = -1, filled, frame, saved;
    static int frames, events, aflOdd, aflJumps, maxScore;
    static final int[] SCORES = new int[6];   // frames whose best tile scored over 4, 8, 14, 24, 40, 64
    static final StringBuilder LOG = new StringBuilder();

    private GlitchWatch() {}

    /** The cover right after this frame's bake (RenderTickEvent START), against the cover at the frame's end. */
    private static int[][] baked;
    private static final int[][][] BAKED_RING = new int[3][][];
    static int compared, changedInFrame, bakedJumps;
    static String aStats = "";
    private static boolean hooked;

    private static void afterBake() {
        baked = RainShaderPack.debugPaneCover();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFrame(TickEvent.RenderTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END || RainPerfProbe.phaseNow() < 0) return;
        if (!hooked) {
            hooked = true;
            WindshieldRenderer.debugAfterBake = GlitchWatch::afterBake;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // AFL's side of this frame
        int bakes = RainShaderPack.debugBakes, uploads = RainShaderPack.debugUploads, trucks = RainShaderPack.debugTrucks;
        RainShaderPack.debugBakes = RainShaderPack.debugUploads = 0;
        int[][] cover = RainShaderPack.debugPaneCover();
        StringBuilder a = new StringBuilder().append("bakes ").append(bakes).append(" uploads ").append(uploads).append(" trucks ").append(trucks).append(" cover");
        if (cover != null) for (int[] c : cover) a.append(c[0] < 0 ? " -" : " " + c[0] + "/" + c[1]);
        a.append(" water").append(com.antaurora.apofirstlight.client.rain.WindshieldRenderer.debugWater());
        if (bakes != 1 || uploads != 1) aflOdd++;
        // the frame, scaled down
        RenderTarget main = mc.getMainRenderTarget();
        if (fbo < 0) {
            fbo = GL30.glGenFramebuffers();
            color = GL11.glGenTextures();
            GlStateManager._bindTexture(color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SW, SH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GlStateManager._bindTexture(0);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            for (int i = 0; i < 3; i++) RING[i] = MemoryUtil.memAlloc(SW * SH * 4);
        }
        int at = frame % 3;
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, SW, SH, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        RING[at].clear();
        GL11.glReadPixels(0, 0, SW, SH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, RING[at]);
        main.bindWrite(true);
        tiles(RING[at], TILES[at]);
        AFL[at] = a.toString();
        COVER[at] = cover;
        BAKED_RING[at] = baked;
        if (baked != null && cover != null) {
            compared++;
            for (int i = 0; i < cover.length; i++)
                if (Math.abs(baked[i][0] - cover[i][0]) >= 10) {
                    if (changedInFrame++ < 12) LOG.append(String.format("   changed after the bake, frame %d pane %d: baked %d/%d, frame end %d/%d%n", frame, i, baked[i][0], baked[i][1], cover[i][0], cover[i][1]));
                    break;
                }
        }
        baked = null;
        frame++;
        frames++;
        if (frames % 300 == 0) aStats = RainShaderPack.debugStreakStats();
        if (++filled < 3) return;
        // frame N is the middle one of the last three
        int p = (frame - 3) % 3, n = (frame - 2) % 3, q = (frame - 1) % 3;
        float best = 0;
        int hot = 0;
        for (int k = 0; k < TX * TY; k++) {
            float s = Math.min(Math.abs(TILES[n][k] - TILES[p][k]), Math.abs(TILES[n][k] - TILES[q][k])) - 2 * Math.abs(TILES[q][k] - TILES[p][k]);
            if (s > best) best = s;
            if (s > EVENT / 2) hot++;
        }
        int[] steps = {4, 8, 14, 24, 40, 64};
        for (int i = 0; i < steps.length; i++) if (best > steps[i]) SCORES[i]++;
        maxScore = Math.max(maxScore, (int) best);
        if (jump(BAKED_RING[p], BAKED_RING[n], BAKED_RING[q])) bakedJumps++;
        if (jump(COVER[p], COVER[n], COVER[q]) && aflJumps++ < 30)
            LOG.append(String.format("   AFL jump at frame %d (phase %d), screen score %.0f%n      N-1 %s%n      N   %s%n      N+1 %s%n", frame - 2, RainPerfProbe.phaseNow(), best, AFL[p], AFL[n], AFL[q]));
        if (best > EVENT) {
            events++;
            String line = String.format("   event at frame %d (phase %d): best tile %.0f, tiles over %d: %d%n      N-1 %s%n      N   %s%n      N+1 %s%n",
                    frame - 2, RainPerfProbe.phaseNow(), best, EVENT / 2, hot, AFL[p], AFL[n], AFL[q]);
            LOG.append(line);
            ApocalypseFirstLight.LOGGER.info("[AFL GLITCH] {}", line.trim());
            if (saved < SAVES) save(p, n, q, saved++);
        }
    }

    /** One pane's mean cover jumping in frame N alone (by 25 or more against both neighbours). */
    private static boolean jump(int[][] p, int[][] n, int[][] q) {
        if (p == null || n == null || q == null) return false;
        for (int i = 0; i < n.length; i++) {
            if (n[i][0] < 0 || p[i][0] < 0 || q[i][0] < 0) continue;
            if (Math.abs(n[i][0] - p[i][0]) >= 25 && Math.abs(n[i][0] - q[i][0]) >= 25) return true;
        }
        return false;
    }

    private static void tiles(ByteBuffer b, float[] out) {
        java.util.Arrays.fill(out, 0);
        for (int y = 0; y < TY * TILE; y++)
            for (int x = 0; x < TX * TILE; x++) {
                int o = (y * SW + x) * 4;
                out[(y / TILE) * TX + x / TILE] += 0.299F * (b.get(o) & 0xFF) + 0.587F * (b.get(o + 1) & 0xFF) + 0.114F * (b.get(o + 2) & 0xFF);
            }
        for (int k = 0; k < out.length; k++) out[k] /= TILE * TILE;
    }

    private static void save(int p, int n, int q, int index) {
        try (NativeImage img = new NativeImage(SW * 3, SH, false)) {
            int[] order = {p, n, q};
            for (int k = 0; k < 3; k++) {
                ByteBuffer b = RING[order[k]];
                for (int y = 0; y < SH; y++)
                    for (int x = 0; x < SW; x++)
                        img.setPixelRGBA(k * SW + x, SH - 1 - y, b.getInt((y * SW + x) * 4) | 0xFF000000);
            }
            img.writeToFile(FMLPaths.GAMEDIR.get().resolve("glitch_" + index + ".png"));
        } catch (Exception e) {
            LOG.append("   save failed ").append(e).append('\n');
        }
    }

    static String summary() {
        StringBuilder s = new StringBuilder("== glitch watch: ").append(frames).append(" frames, ").append(events).append(" one-frame events (tile score over ").append(EVENT)
                .append("), best score ").append(maxScore).append('\n');
        s.append("   frames whose best tile scored over 4/8/14/24/40/64:");
        for (int c : SCORES) s.append(' ').append(c);
        s.append('\n');
        s.append("   AFL: frames without exactly one bake + one upload ").append(aflOdd).append(", one-frame jumps of a pane's mean cover ").append(aflJumps).append('\n');
        s.append("   right after the bake: one-frame jumps ").append(bakedJumps).append("; frames whose cover changed between the bake and the frame's end ").append(changedInFrame).append(" of ").append(compared).append('\n');
        s.append("   windshield (slot 0) mip 0, last sample: ").append(aStats).append('\n');
        return s.append(LOG).toString();
    }
}
