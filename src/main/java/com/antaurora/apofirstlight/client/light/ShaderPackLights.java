package com.antaurora.apofirstlight.client.light;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ShaderPackOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * The lights for a shader pack that draws them itself (docs/rendering/dynamic_lights_v1.md 3.1; Aureveil from alpha.46):
 * the pack switches them on with its boolean option {@code AFL_VEHICLE_LIGHTS} and reads two textures that it declares as
 * {@code customTexture.aflLightTex=apocalypse_firstlight:dynamic/afl_lights} and
 * {@code customTexture.aflOccTex=apocalypse_firstlight:dynamic/afl_occupancy} (Oculus binds a resource custom texture by
 * looking it up in the TextureManager at every bind, so these are plain registered textures whose contents change).
 * <ul>
 * <li>afl_lights, RGBA32F 6 x 33: row 0 the header, pixel 0 (lights, occluders, 0, 4242: the pack's check that AFL is
 * there), 1 (grid min block - camera block, w 1 when the grid is placed), 2 (camera block & 127), 3 (camera block
 * fraction); rows 1-8 the occluders (pixel 0, camera-relative); rows 9-32 the lights, pixels 0-5 each (the 6 vec4 of
 * afl_dynamic_light.glsl, sorted by occluder).</li>
 * <li>afl_occupancy, R8 2048 x 1024: the block grid laid flat, g = world block & 127 at (g.x * 16 + (g.z & 15),
 * g.y + 128 * (g.z >> 4)) ({@link OccupancyGrid}, flat layout).</li>
 * </ul>
 * Positions are relative to Camera#getPosition() at the frame's partial tick, which is the shader pack's scene origin.
 */
final class ShaderPackLights {
    static final ResourceLocation LIGHTS = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/afl_lights"),
            OCCUPANCY = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/afl_occupancy");
    static final String OPTION = "AFL_VEHICLE_LIGHTS";
    static final int LIGHT_W = 6, LIGHT_H = 1 + DynamicLights.MAX_OCCLUDERS + DynamicLights.MAX_LIGHTS, OCC_W = 2048, OCC_H = 1024;
    private static final float CHECK = 4242.0F;

    private static final DataTexture LIGHT_TEX = new DataTexture(), OCC_TEX = new DataTexture();
    private static final FloatBuffer DATA = MemoryUtil.memAllocFloat(LIGHT_W * LIGHT_H * 4);
    /** The header's light count last uploaded (a zero header goes up once when the lights stop). */
    private static int uploadedCount = -1;

    private ShaderPackLights() {
    }

    /** A texture whose contents AFL writes itself (nothing to load from resources). */
    private static final class DataTexture extends AbstractTexture {
        /** The GL id the storage was made for (a closed and re-made texture gets a new id: storage again). */
        int allocated = -1;

        @Override
        public void load(ResourceManager manager) {
        }
    }

    /** Client setup (render thread): both textures registered before the shader pack first binds them. */
    static void register() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        if (textures.getTexture(LIGHTS, null) != LIGHT_TEX) textures.register(LIGHTS, LIGHT_TEX);
        if (textures.getTexture(OCCUPANCY, null) != OCC_TEX) textures.register(OCCUPANCY, OCC_TEX);
    }

    /** The shader pack in use has the option and it is on (false when unknown). */
    static boolean wanted() {
        return ShaderPackOption.on(OPTION, "the pack draws the lamps; off: block light");
    }

    /** The occupancy texture's GL id, its storage (zeros) made when new (true: the contents were lost, refill the grid). */
    static boolean prepareOccupancy() {
        register();
        int id = OCC_TEX.getId();
        if (OCC_TEX.allocated == id) return false;
        OCC_TEX.allocated = id;
        ByteBuffer zeros = MemoryUtil.memCalloc(OCC_W * OCC_H);
        try {
            allocate(id, GL30.GL_R8, OCC_W, OCC_H, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, zeros);
        } finally {
            MemoryUtil.memFree(zeros);
        }
        return true;
    }

    static int occupancyId() {
        return OCC_TEX.getId();
    }

    /** This frame's lights (sorted, {@code count} of them) and the grid's placement into afl_lights (render thread). */
    static void upload(FloatBuffer lights, int count, float[] occluders, int occluderCount, boolean gridPlaced,
                       int gridDX, int gridDY, int gridDZ, int camBX, int camBY, int camBZ, float fx, float fy, float fz) {
        register();
        int id = LIGHT_TEX.getId();
        boolean fresh = LIGHT_TEX.allocated != id;
        if (!fresh && count == 0 && uploadedCount == 0) return;
        if (fresh) {
            LIGHT_TEX.allocated = id;
            allocate(id, GL30.GL_RGBA32F, LIGHT_W, LIGHT_H, GL11.GL_RGBA, GL11.GL_FLOAT, null);
        }
        FloatBuffer d = DATA;
        d.clear();
        MemoryUtil.memSet(MemoryUtil.memAddress(d), 0, (long) d.capacity() * Float.BYTES);
        d.put(0, count).put(1, occluderCount).put(3, CHECK);
        d.put(4, gridDX).put(5, gridDY).put(6, gridDZ).put(7, gridPlaced ? 1 : 0);
        d.put(8, camBX & 127).put(9, camBY & 127).put(10, camBZ & 127);
        d.put(12, fx).put(13, fy).put(14, fz);
        for (int i = 0; i < occluderCount; i++)
            for (int k = 0; k < 3; k++) d.put(((1 + i) * LIGHT_W) * 4 + k, occluders[i * 4 + k]);
        for (int i = 0; i < count; i++)
            for (int k = 0; k < LIGHT_W * 4; k++) d.put(((1 + DynamicLights.MAX_OCCLUDERS + i) * LIGHT_W) * 4 + k, lights.get(i * LIGHT_W * 4 + k));
        d.position(0).limit(d.capacity());
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        OccupancyGrid.unpackDefaults();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, LIGHT_W, LIGHT_H, GL11.GL_RGBA, GL11.GL_FLOAT, d);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
        uploadedCount = count;
    }

    /**
     * A complete texture (nearest, one level, clamped: texelFetch on an incomplete one reads 0) with its storage. Bound and
     * restored on the active unit with plain GL, so GlStateManager's cached binding stays true.
     */
    private static void allocate(int id, int format, int w, int h, int layout, int type, ByteBuffer data) {
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        OccupancyGrid.unpackDefaults();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, w, h, 0, layout, type, data);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
    }
}
