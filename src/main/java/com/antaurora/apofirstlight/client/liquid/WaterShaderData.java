package com.antaurora.apofirstlight.client.liquid;

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
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * AFL's water for a shader pack that draws it as its own water (docs/rendering/afl_water_shader_interface_v1.md; Aureveil,
 * proposed 2026-10-28): the pack switches it on with its boolean option {@value #OPTION}. While it is on, ClientLiquid
 * writes the pack's attributes into the surface's vertices (Out#pack: the flow, white water and fade in the colour, the
 * kind of water in the texture coordinates inside the surface's sprite, the face's own normal) and AFL writes
 * {@code apocalypse_firstlight:dynamic/afl_water}, RGBA32F 4 x 1, nearest, one level, at the start of every frame
 * (RenderTickEvent START):
 * <ul>
 * <li>texel 0: (layout 1, metres of AFL water over the eye (0: the eye is not in it), 0, 4245: the pack's check);</li>
 * <li>texel 1: the surface sprite's atlas rectangle (u0, v0, u1, v1), by which the pack knows AFL's water;</li>
 * <li>texel 2: a river's water: its tint (display values, as a biome's water colour) and its murk (1 the pack's own water,
 * more scatters more);</li>
 * <li>texel 3: still water on the ground (rain water, puddles, a dug pit's water): the same.</li>
 * </ul>
 * A changed meaning raises the layout and Aureveil is told. Without the option (another pack, an older Aureveil) the
 * surface stays the plain translucent one it was.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WaterShaderData {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/afl_water");
    static final String OPTION = "AFL_WATER";
    static final int W = 4, H = 1, LAYOUT = 1;
    private static final float CHECK = 4245.0F;
    /** Kinds of water: a vertex's u at (kind + 0.5) / KINDS across the sprite, its v at the middle. */
    static final int KINDS = 4, STILL = 0, RIVER = 1;
    /** Flow in the colour: 0.5 + v / FLOW_RANGE / 2 per axis, so ±4 m/s fills a byte. */
    static final float FLOW_RANGE = 4;
    /** The tints and murk (texels 2, 3): a clear stream; rain water on earth, brown and cloudy. */
    private static final float[] RIVER_WATER = {0.32F, 0.50F, 0.52F, 1.0F}, STILL_WATER = {0.46F, 0.42F, 0.32F, 3.0F};

    private static final DataTexture TEX = new DataTexture();
    private static final FloatBuffer DATA = MemoryUtil.memAllocFloat(W * H * 4);
    /** Whether this frame's pack draws AFL's water (decided once at the frame's start). */
    private static boolean packDraws;
    private static long written;
    private static float lastEye;

    private WaterShaderData() {
    }

    private static final class DataTexture extends AbstractTexture {
        int allocated = -1;

        @Override
        public void load(ResourceManager manager) {
        }
    }

    /** Registered at client setup, before a shader pack first binds it, and again at every upload. */
    static void register() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        if (textures.getTexture(TEXTURE, null) != TEX) textures.register(TEXTURE, TEX);
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(WaterShaderData::register);
        }
    }

    /** Whether the frame's surface carries the pack's attributes (ClientLiquid). */
    static boolean packDraws() {
        return packDraws;
    }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        packDraws = mc.level != null && AflShaderCompat.shaderPackInUse()
                && ShaderPackOption.on(OPTION, "the pack draws AFL's water as its own; off: a plain translucent surface");
        if (!packDraws) return;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        lastEye = ClientLiquid.waterAbove(eye.x, eye.y, eye.z);
        upload(mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ClientLiquid.SPRITE), lastEye);
    }

    private static void upload(TextureAtlasSprite sprite, float eye) {
        register();
        FloatBuffer d = DATA;
        d.clear();
        d.put(0, LAYOUT).put(1, eye).put(2, 0).put(3, CHECK);
        d.put(4, sprite.getU0()).put(5, sprite.getV0()).put(6, sprite.getU1()).put(7, sprite.getV1());
        for (int k = 0; k < 4; k++) {
            d.put(8 + k, RIVER_WATER[k]);
            d.put(12 + k, STILL_WATER[k]);
        }
        d.position(0).limit(d.capacity());
        GlTex.checkFinite("afl_water", d, W);
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

    /** Dev readout (in /afl_render water status). */
    static String status() {
        return "shader pack water (" + OPTION + "): " + (packDraws ? String.format(java.util.Locale.ROOT, "on, eye under %.2f m", lastEye) : "off")
                + ", frames written " + written;
    }
}
