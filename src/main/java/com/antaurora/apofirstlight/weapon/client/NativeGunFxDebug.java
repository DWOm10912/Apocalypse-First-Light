package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Opt-in diagnostics only. No shader API, render state writes, or gameplay changes. */
public final class NativeGunFxDebug {
    public static final boolean ENABLED = Boolean.getBoolean("afl.gunFxDebug");
    private static final int MAX_LINES = 1200;
    private static int lines;
    private static long frame, previousNanos;
    private static double frameMs;
    private static boolean announced;
    private static long sampleGun, sampleFrame;
    private static final Matrix4f ANCHOR = new Matrix4f(), HAND = new Matrix4f(), VIEW = new Matrix4f(), WORLD = new Matrix4f();
    private static String sampleCamera, sampleOrigin;
    private static double rawLength;
    private static Object normalized;
    private static boolean directionAccepted;
    public static void direction(double length, Object unit, boolean accepted) {
        if (!ENABLED || lines >= MAX_LINES) return;
        rawLength = length; normalized = unit; directionAccepted = accepted;
    }
    private NativeGunFxDebug() {}

    public static void frame(long currentFrame) {
        if (!ENABLED) return;
        frame = currentFrame;
        long now = System.nanoTime();
        frameMs = previousNanos == 0 ? 0 : (now - previousNanos) / 1_000_000.0;
        previousNanos = now;
        if (!announced) {
            announced = true;
            log("BOOT", 0, "version=projection-compat-v2 label=" + System.getProperty("afl.gunFxDebugLabel", "UNLABELLED")
                    + " maxLines=" + MAX_LINES + " matrices=column-major submissionsAreNotPixelProof=true");
        }
    }

    public static void sample(long gun, Matrix4fc anchor, Matrix4fc hand, Matrix4fc view, Matrix4fc world,
                              Object camera, Object origin) {
        if (!ENABLED || lines >= MAX_LINES) return;
        sampleGun = gun; sampleFrame = frame;
        ANCHOR.set(anchor); HAND.set(hand); VIEW.set(view); WORLD.set(world);
        sampleCamera = camera.toString(); sampleOrigin = origin.toString();
    }

    public static void capture(long id, String result) {
        if (!ENABLED || lines >= MAX_LINES) return;
        var mc = Minecraft.getInstance();
        log("CAPTURE", id, result + " sampleGun=" + sampleGun + " sampleFrame=" + sampleFrame
                + " frameMs=" + frameMs + " camera=" + sampleCamera + " worldOrigin=" + sampleOrigin
                + " item=" + (mc.player == null ? "none" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(mc.player.getMainHandItem().getItem()))
                + " fovOption=" + mc.options.fov().get() + " window=" + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
                + " cameraType=" + mc.options.getCameraType());
        var sanitized = FirstPersonProjectionSanitizer.sanitize(HAND, WORLD);
        log("PROJECTION_SANITIZE", id, "firstPerson=true sampleFrame=" + sampleFrame
                + " handDepthBefore=" + depth(HAND) + " worldDepth=" + depth(WORLD)
                + " handDepthAfter=" + depth(sanitized)
                + " changed=" + (HAND.m22()!=sanitized.m22() || HAND.m32()!=sanitized.m32()));
        log("SNAPSHOT_DIRECTION", id, "sampleFrame=" + sampleFrame + " rawLength=" + rawLength
                + " normalized=" + normalized + " accepted=" + directionAccepted);
        if (Boolean.getBoolean("afl.gunFxDebugMatrices"))
            log("MATRICES", id, "anchor=" + matrix(ANCHOR) + " handProjection=" + matrix(HAND)
                    + " worldView=" + matrix(VIEW) + " worldProjection=" + matrix(WORLD));
    }

    private static String depth(Matrix4fc m) { return "[" + m.m22() + "," + m.m32() + "]"; }

    private static String matrix(Matrix4fc matrix) { return java.util.Arrays.toString(matrix.get(new float[16])); }

    public static void log(String event, long id, String data) {
        if (!ENABLED || lines >= MAX_LINES) return;
        ApocalypseFirstLight.LOGGER.info("[AFL GUN FX DEBUG] event={} shotId={} frame={} {}", event, id, frame, data);
        if (++lines == MAX_LINES) ApocalypseFirstLight.LOGGER.info("[AFL GUN FX DEBUG] LIMIT_REACHED; restart to record more");
    }
}
