package com.antaurora.apofirstlight.client;

import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL45C;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-frame texture uploads without touching (or reading) any binding: direct state access (GL 4.5 or
 * ARB_direct_state_access). A glGet to save and put back a binding makes the game thread wait for the driver thread
 * (NVIDIA threaded optimisation), which with a shader pack cost whole milliseconds a frame (Aureveil A, 2026-10-17).
 * Without DSA: bound, written and put back the old way. The texture must have been bound once (its storage made).
 * <p>Every upload first puts the pixel unpack state back to the defaults (set, never read): vanilla's NativeImage leaves
 * its row length and skips behind, and the driver then reads a small buffer as rows of someone else's image, past its
 * end (a crash in nvoglv64.dll inside glTextureSubImage2D, vegetation 13.9, 2026-10-24).
 */
public final class GlTex {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int dsa = -1;   // -1 unknown, 0 none, 1 core 4.5, 2 ARB
    /** Per float texture: the time (ms) its last non-finite value was logged. */
    private static final Map<String, Long> NON_FINITE_LOGGED = new HashMap<>();

    private GlTex() {}

    private static int dsa() {
        if (dsa < 0) {
            var caps = GL.getCapabilities();
            dsa = caps.OpenGL45 ? 1 : caps.GL_ARB_direct_state_access ? 2 : 0;
        }
        return dsa;
    }

    /**
     * A float texture for a shader pack about to be uploaded (w texels a row, RGBA): a NaN or infinity in it is logged
     * (the first one's texel and component, and how many), at most every 10 s per texture. A shader pack's lighting reads
     * these for every pixel, so one bad value can take the whole picture (a black screen with Aureveil alpha.49 only
     * while the game is not paused, user 2026-10-17: Aureveil A's way to find it). Positions only; the values stay.
     */
    public static void checkFinite(String name, FloatBuffer d, int w) {
        int bad = 0, first = -1, n = d.capacity();
        for (int i = 0; i < n; i++)
            if (!Float.isFinite(d.get(i)) && bad++ == 0) first = i;
        if (bad == 0) return;
        long now = System.currentTimeMillis();
        Long last = NON_FINITE_LOGGED.get(name);
        if (last != null && now - last < 10_000) return;
        NON_FINITE_LOGGED.put(name, now);
        int texel = first / 4;
        LOGGER.warn("AFL {}: {} non-finite floats; first at texel ({}, {}) component {} = {}", name, bad, texel % w, texel / w,
                "rgba".charAt(first % 4), d.get(first));
    }

    /** The pixel unpack state at its defaults (no unpack buffer, tight rows, no skips), set without reading it. */
    public static void unpackDefaults() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    public static void subImage(int id, int x, int y, int w, int h, int format, int type, ByteBuffer data) {
        unpackDefaults();
        switch (dsa()) {
            case 1 -> GL45C.glTextureSubImage2D(id, 0, x, y, w, h, format, type, data);
            case 2 -> ARBDirectStateAccess.glTextureSubImage2D(id, 0, x, y, w, h, format, type, data);
            default -> {
                int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, w, h, format, type, data);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
            }
        }
    }

    public static void subImage(int id, int x, int y, int w, int h, int format, int type, FloatBuffer data) {
        unpackDefaults();
        switch (dsa()) {
            case 1 -> GL45C.glTextureSubImage2D(id, 0, x, y, w, h, format, type, data);
            case 2 -> ARBDirectStateAccess.glTextureSubImage2D(id, 0, x, y, w, h, format, type, data);
            default -> {
                int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, w, h, format, type, data);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
            }
        }
    }
}
