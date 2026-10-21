package com.antaurora.apofirstlight.client.lightning;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.LightningInjuryConfig;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Lightning injury V1 (docs/gameplay/lightning_injury_v1_plan.md 3.6): a strike close by and in view leaves its shape in the
 * eyes. At its first stroke's peak the main channel is projected onto the screen once; the after-image stays where it was
 * on the screen (on the retina, not in the world) as a soft dim violet line, fading over afterimage_seconds (nearest ..
 * farthest within afterimage_range). Drawn in the GUI pass under the HUD; nothing while there is none.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class LightningAfterimage {
    /** The stroke counts as seen once its main channel is this bright. */
    private static final double SEEN = 0.6;
    /** Line half widths (GUI px, at 10 m: nearer wider) of the glow and the core, and their alpha at full strength. */
    private static final float GLOW = 5F, CORE = 1.4F, GLOW_ALPHA = 0.10F, CORE_ALPHA = 0.32F;
    private static final float R = 0.56F, G = 0.44F, B = 0.78F;

    /** One after-image: screen points (0..1, y down), the width scale, made at (ns), lasting (s). */
    private record Image(float[] xs, float[] ys, float width, long made, double seconds) {}

    private static final List<Image> IMAGES = new ArrayList<>();
    private static final Set<LightningBolt> SEEN_BOLTS = Collections.newSetFromMap(new WeakHashMap<>());

    private LightningAfterimage() {
    }

    @SubscribeEvent
    public static void level(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || AflShaderCompat.activeShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideLightningFlash().get()) return;
        var c = LightningInjuryConfig.get();
        if (!(c.afterimageRange > 0)) return;
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f view = event.getPoseStack().last().pose(), projection = event.getProjectionMatrix();
        for (Map.Entry<LightningBolt, LightningFlash> e : AflLightning.flashes().entrySet()) {
            LightningBolt bolt = e.getKey();
            if (bolt.isRemoved() || bolt.level() != mc.level || SEEN_BOLTS.contains(bolt)) continue;
            double distance = Math.hypot(bolt.getX() - cam.x, bolt.getZ() - cam.z);
            if (distance > c.afterimageRange) {
                SEEN_BOLTS.add(bolt);
                continue;
            }
            LightningFlash f = e.getValue();
            double t = f.time(event.getPartialTick());
            if (t < 0 || f.at(t).main < SEEN) continue;
            SEEN_BOLTS.add(bolt);
            capture(bolt, f, cam, view, projection, distance, c);
        }
    }

    private static void capture(LightningBolt bolt, LightningFlash f, Vec3 cam, Matrix4f view, Matrix4f projection, double distance,
                                LightningInjuryConfig c) {
        LightningChannel.Strand main = f.channel.strands.get(0);
        int n = main.count();
        float[] xs = new float[n], ys = new float[n];
        int seen = 0;
        Vector4f v = new Vector4f();
        for (int i = 0; i < n; i++) {
            v.set((float) (bolt.getX() + main.x(i) - cam.x), (float) (bolt.getY() + main.y(i) - cam.y), (float) (bolt.getZ() + main.z(i) - cam.z), 1);
            view.transform(v);
            projection.transform(v);
            if (v.w <= 0.05F) {
                xs[i] = Float.NaN;
                continue;
            }
            float x = v.x / v.w, y = v.y / v.w;
            xs[i] = x * 0.5F + 0.5F;
            ys[i] = 0.5F - y * 0.5F;
            if (x > -1 && x < 1 && y > -1 && y < 1) seen++;
        }
        if (seen < 2) return;
        double near = Mth.clamp(distance / c.afterimageRange, 0, 1);
        double seconds = Mth.lerp(near, c.afterimageSeconds[0], c.afterimageSeconds[1]);
        float width = (float) Mth.clamp(10 / Math.max(distance, 3), 0.4, 2.5);
        IMAGES.add(new Image(xs, ys, width, System.nanoTime(), seconds));
    }

    /** A soft fade: full for the first fifth, then down. */
    private static float strength(Image image, long now) {
        double t = (now - image.made) / 1e9 / image.seconds;
        if (t >= 1) return 0;
        if (t < 0.2) return 1;
        double k = (t - 0.2) / 0.8;
        return (float) ((1 - k) * (1 - k));
    }

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        if (IMAGES.isEmpty()) return;
        long now = System.nanoTime();
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Iterator<Image> it = IMAGES.iterator(); it.hasNext(); ) {
            Image image = it.next();
            float k = strength(image, now);
            if (k <= 0) {
                it.remove();
                continue;
            }
            line(buffer, matrix, image, width, height, GLOW * image.width, GLOW_ALPHA * k);
            line(buffer, matrix, image, width, height, CORE * image.width, CORE_ALPHA * k);
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.defaultBlendFunc();
    };

    private static void line(BufferBuilder buffer, Matrix4f matrix, Image image, int width, int height, float half, float alpha) {
        for (int i = 0; i + 1 < image.xs.length; i++) {
            if (Float.isNaN(image.xs[i]) || Float.isNaN(image.xs[i + 1])) continue;
            float x0 = image.xs[i] * width, y0 = image.ys[i] * height, x1 = image.xs[i + 1] * width, y1 = image.ys[i + 1] * height;
            float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-3F || len > width * 2) continue;
            // across the segment, and a little along it so the joints close
            float nx = -dy / len * half, ny = dx / len * half, ax = dx / len * half * 0.5F, ay = dy / len * half * 0.5F;
            buffer.vertex(matrix, x0 + nx - ax, y0 + ny - ay, 0).color(R, G, B, alpha).endVertex();
            buffer.vertex(matrix, x0 - nx - ax, y0 - ny - ay, 0).color(R, G, B, alpha).endVertex();
            buffer.vertex(matrix, x1 - nx + ax, y1 - ny + ay, 0).color(R, G, B, alpha).endVertex();
            buffer.vertex(matrix, x1 + nx + ax, y1 + ny + ay, 0).color(R, G, B, alpha).endVertex();
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        IMAGES.clear();
        SEEN_BOLTS.clear();
    }
}
