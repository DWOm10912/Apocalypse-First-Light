package com.antaurora.apofirstlight.client.lightning;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.GlTex;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Lightning V1 for a shader pack (docs/rendering/lightning_v1.md 3.3; Aureveil from alpha.61, its interface of 2026-10-18):
 * the flashes as they are this frame, so the pack lights its sky and ground in step with AFL's strokes, bolts out of
 * sight included. A registered texture the pack declares as a custom texture
 * ({@code apocalypse_firstlight:dynamic/afl_lightning}), RGBA32F 4 x 5, nearest, written each frame at AFTER_SKY while a
 * shader pack is in use (like client/light/ShaderPackLights' afl_lights):
 * <ul>
 * <li>row 0, texel 0: (bolts n, 0, 0, 4243: the pack's check that AFL is there);</li>
 * <li>rows 1-4, one bolt each, brightest first (at most 4): texel 0 the strike point relative to the camera (m), w the
 * channel's height (m); texel 1 (k main, k branch, s since the first stroke (negative during the leader), 1 during the
 * leader else 0), the k the renderer multiplies the core by this frame; texel 2 the channel's top relative to the camera,
 * w 0; texel 3 zero (both reserved).</li>
 * </ul>
 * The camera is Camera#getPosition() at the frame's partial tick (the pack's scene origin). With no bolts n is 0 (uploaded
 * once when they stop).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningShaderData {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/afl_lightning");
    static final int W = 4, H = 5, MAX_BOLTS = H - 1;
    private static final float CHECK = 4243.0F;

    private static final DataTexture TEX = new DataTexture();
    private static final FloatBuffer DATA = MemoryUtil.memAllocFloat(W * H * 4);
    private static int uploadedCount = -1;
    /** Diagnostics: the last upload's values (dev/LightningProbe). */
    public static final float[] LAST = new float[W * H * 4];

    private LightningShaderData() {
    }

    /** A texture whose contents AFL writes itself. */
    private static final class DataTexture extends AbstractTexture {
        int allocated = -1;

        @Override
        public void load(ResourceManager manager) {
        }
    }

    /** Client setup (render thread) and every upload: registered before the shader pack first binds it. */
    static void register() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        if (textures.getTexture(TEXTURE, null) != TEX) textures.register(TEXTURE, TEX);
    }

    private record Lit(LightningBolt bolt, LightningFlash flash, double t, float main, float branch, boolean leader) {
    }

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !AflShaderCompat.shaderPackInUse()) return;
        if (AflShaderCompat.activeShadowPass()) return;
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        List<Lit> lit = new ArrayList<>();
        for (Map.Entry<LightningBolt, LightningFlash> e : AflLightning.flashes().entrySet()) {
            LightningBolt bolt = e.getKey();
            if (bolt.isRemoved() || bolt.level() != Minecraft.getInstance().level) continue;
            LightningFlash f = e.getValue();
            double t = f.time(pt);
            if (t < -LightningFlash.LEADER || t > f.end()) continue;
            if (t < 0) lit.add(new Lit(bolt, f, t, LightningRenderer.LEADER_K, LightningRenderer.LEADER_K, true));
            else {
                f.at(t);
                lit.add(new Lit(bolt, f, t, (float) f.main, (float) f.branch, false));
            }
        }
        lit.sort(Comparator.comparingDouble((Lit l) -> Math.max(l.main, l.branch)).reversed());
        upload(lit, cam);
    }

    private static void upload(List<Lit> lit, Vec3 cam) {
        register();
        int id = TEX.getId();
        boolean fresh = TEX.allocated != id;
        int n = Math.min(lit.size(), MAX_BOLTS);
        if (!fresh && n == 0 && uploadedCount == 0) return;
        FloatBuffer d = DATA;
        d.clear();
        MemoryUtil.memSet(MemoryUtil.memAddress(d), 0, (long) d.capacity() * Float.BYTES);
        d.put(0, n).put(3, CHECK);
        for (int i = 0; i < n; i++) {
            Lit l = lit.get(i);
            LightningFlash f = l.flash;
            int o = (1 + i) * W * 4;
            double sx = l.bolt.getX() - cam.x, sy = l.bolt.getY() - cam.y, sz = l.bolt.getZ() - cam.z;
            d.put(o, (float) sx).put(o + 1, (float) sy).put(o + 2, (float) sz).put(o + 3, (float) f.height);
            d.put(o + 4, l.main).put(o + 5, l.branch).put(o + 6, (float) l.t).put(o + 7, l.leader ? 1 : 0);
            d.put(o + 8, (float) (sx + f.channel.topX)).put(o + 9, (float) (sy + f.channel.topY)).put(o + 10, (float) (sz + f.channel.topZ));
        }
        d.position(0).limit(d.capacity());
        GlTex.checkFinite("afl_lightning", d, W);
        d.get(0, LAST);
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        if (fresh) {
            TEX.allocated = id;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, W, H, 0, GL11.GL_RGBA, GL11.GL_FLOAT, d);
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, W, H, GL11.GL_RGBA, GL11.GL_FLOAT, d);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
        uploadedCount = n;
    }

    /** Dev: the texture's contents read back from the GPU (render thread; stalls). */
    public static float[] readBack() {
        float[] out = new float[W * H * 4];
        int id = TEX.getId();
        if (TEX.allocated != id) return out;
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, out);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
        return out;
    }
}
