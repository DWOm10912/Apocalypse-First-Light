package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.GlTex;
import com.antaurora.apofirstlight.client.ShaderPackOption;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * The mesh plants' wind for a shader pack that waves them itself (docs/gameplay/vegetation_v1_design.md 13.9; Aureveil
 * from alpha.70, its interface of 2026-10-24): the pack switches it on with its boolean option {@value #OPTION} and reads
 * {@code apocalypse_firstlight:dynamic/afl_vegetation}, RGBA32F 32 x 3 (layout 3, P4, agreed with Aureveil 2026-10-24,
 * doc 20.6; layout 2 was 16 x 2 with 64 columns, layout 1 8 x 2 with 32), nearest, one level, written at the start of every
 * frame (RenderTickEvent START: before the pack's shadow pass, so both passes read the same values):
 * <ul>
 * <li>row 0: texel 0 (layout 3, 0, 0, 4244: the pack's check that AFL is there); texel 1 this frame's wind (time s, x and
 * z m/s the way it blows, gust 0..1: VegetationWindMath#now); texel 2 the last frame's (the pack's motion vectors);
 * texel 3 the standing sheet's atlas rectangle (u0, v0, u1, v1); texel 4 the still sheet's; texel 5 the underwater sway
 * (period s, reach factor, 0, 0: zero until the water plants, P4b); texels 6-31 zero.</li>
 * <li>row 1: the 128 columns' wind reach (PlantShape#WIND), four to a texel (texels 0-31), as afl_VegScale; a vertex's
 * column is its u's share of the rectangle times 128.</li>
 * <li>row 2: the 128 columns' mode, four to a texel: 0 the wind, 1 underwater, 2 a floating leaf (all 0 until P4b).</li>
 * </ul>
 * The pack finds AFL's plants by their texture coordinates in those rectangles, in the chunk mesh and in the near layer
 * alike, waves the standing ones with VegetationWind's formula (its world position wrapped by itself) and gives both the
 * plants' material (the near layer has no wind of its own with a pack: VegetationDynamics, 13.10). The formula changing means the
 * layout number goes up and Aureveil is told.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VegetationShaderData {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/afl_vegetation");
    static final String OPTION = "AFL_VEGETATION";
    static final int W = PlantShape.COLUMNS / 4, H = 3, LAYOUT = 3;
    private static final float CHECK = 4244.0F;

    private static final DataTexture TEX = new DataTexture();
    private static final FloatBuffer DATA = MemoryUtil.memAllocFloat(W * H * 4);
    private static float[] last;
    /** Whether this frame's pack waves the plants (decided once at the frame's start). */
    private static boolean packWaves;
    /** Diagnostics: frames written. */
    private static long written;

    private VegetationShaderData() {
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

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        packWaves = mc.level != null && AflShaderCompat.shaderPackInUse()
                && ShaderPackOption.on(OPTION, "the pack waves AFL's plants; off: the pack's own short grass sway, the near layer still");
        if (!packWaves) {
            last = null;
            return;
        }
        upload(VegetationWindMath.now());
    }

    private static void upload(float[] wind) {
        register();
        float[] prev = last == null ? wind : last;
        last = wind;
        FloatBuffer d = DATA;
        d.clear();
        MemoryUtil.memSet(MemoryUtil.memAddress(d), 0, (long) d.capacity() * Float.BYTES);
        d.put(0, LAYOUT).put(3, CHECK);
        for (int k = 0; k < 4; k++) {
            d.put(4 + k, wind[k]);
            d.put(8 + k, prev[k]);
        }
        rect(d, 12, PlantModel.sprite());
        rect(d, 16, PlantModel.stillSprite());
        for (int c = 0; c < PlantShape.COLUMNS; c++) d.put(W * 4 + c, PlantShape.WIND[c]);
        // row 2: every column waves in the wind (mode 0) until the water plants (P4b)
        d.position(0).limit(d.capacity());
        GlTex.checkFinite("afl_vegetation", d, W);
        int id = TEX.getId();
        if (TEX.allocated != id) {
            TEX.allocated = id;
            int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, W, H, 0, GL11.GL_RGBA, GL11.GL_FLOAT, d);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        } else {
            // no glGet each frame (NVIDIA's threaded optimization waits on it): direct state access
            GlTex.subImage(id, 0, 0, W, H, GL11.GL_RGBA, GL11.GL_FLOAT, d);
        }
        written++;
    }

    private static void rect(FloatBuffer d, int at, TextureAtlasSprite s) {
        d.put(at, s.getU0()).put(at + 1, s.getV0()).put(at + 2, s.getU1()).put(at + 3, s.getV1());
    }

    static String status() {
        return "shader pack data (" + OPTION + "): " + (packWaves ? "on, the pack waves the plants" : "off") + ", frames written " + written;
    }
}
