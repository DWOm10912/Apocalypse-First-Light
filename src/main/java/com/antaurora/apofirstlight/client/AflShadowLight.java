package com.antaurora.apofirstlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * The shader pack's shadow light and shadow setup, as seen from a block entity renderer during the Oculus shadow pass
 * (docs/dev/render_performance_v1.md, step 1). Checked against the Oculus 1.8.0 / Embeddium 0.3.31 bytecode (2026-10-08):
 * <ul>
 * <li>Oculus renders the shadow pass's block entities through Embeddium's SodiumWorldRenderer#renderBlockEntity with the
 * shadow model-view on the pose stack, then translate(block entity - camera); a renderer's incoming
 * {@code pose.last().normal()} is the shadow rotation, under which the light looks down -Z. Its row 2
 * {@code (m02, m12, m22)} is the world-space unit vector toward the light: the sun by day, the moon by night (the
 * antipode, so y is never negative), the pack's sunPathRotation included. It must be read before the renderer's own
 * transforms.</li>
 * <li>The shadow camera sits 100 blocks toward the light from the (grid-snapped) player camera, orthographic, with the
 * pack's near plane and no depth clamp: terrain more than {@code 100 - near} blocks toward the light is clipped.</li>
 * <li>Leaving a hidden block entity out changes nothing only when the pack draws terrain into the shadow map
 * ({@code shadowTerrain}), draws block entities as shadows ({@code shadowBlockEntities}; otherwise Oculus may call the
 * renderers only to collect lights), and culls with the plain light-extruded frustum (AdvancedShadowCullingFrustum, not
 * its reversed subclass used by voxelizing packs, nor a distance-only frustum).</li>
 * </ul>
 * {@link #setup} answers all of that once per frame; anything it cannot read means no culling.
 */
public final class AflShadowLight {
    /**
     * This frame's shadow setup: occluders count only within {@code occluderDepth} blocks toward the light from the camera,
     * and only in chunk sections within {@code boxDistance} blocks of it on every axis (Oculus' BoxCuller; infinite when
     * there is none).
     */
    public record Setup(double occluderDepth, double boxDistance) {}

    private static final String RENDERER = "net.irisshaders.iris.shadows.ShadowRenderer",
            ADVANCED = "net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum",
            BOX = "net.irisshaders.iris.shadows.frustum.BoxCuller",
            IRIS = "net.irisshaders.iris.Iris", PIPELINE = "net.irisshaders.iris.pipeline.IrisRenderingPipeline";
    private static final double MAX_DEPTH = 64.0, CAMERA_SNAP = 2.0;
    private static boolean resolved, broken;
    private static Field frustum, projection, boxCuller, maxDistance, shadowRenderer, terrain, blockEntities, nearPlane;
    private static Method pipelineManager, pipelineNullable;
    private static Class<?> advanced, pipeline;

    private AflShadowLight() {}

    /** Shadow pass only: the world-space unit vector toward the shadow light from the renderer's incoming pose, or null. */
    public static Vector3f direction(PoseStack pose) {
        Matrix3f n = pose.last().normal();
        var toLight = new Vector3f(n.m02(), n.m12(), n.m22());
        float length = toLight.length();
        if (!Float.isFinite(length) || length < 1e-6F) return null;
        return toLight.div(length);
    }

    /** The current shadow pass may leave fully hidden block entities out: its setup, or null (cull nothing). */
    public static Setup setup() {
        if (!resolve()) return null;
        try {
            Object f = frustum.get(null);
            if (f == null || f.getClass() != advanced) return null;   // exactly the light-extruded frustum
            if (!(projection.get(null) instanceof Matrix4f p) || p.m23() != 0 || p.m33() != 1) return null;   // orthographic
            Object manager = pipelineManager.invoke(null);
            Object active = manager == null ? null : pipelineNullable.invoke(manager);
            if (active == null || !pipeline.isInstance(active)) return null;
            Object renderer = shadowRenderer.get(active);
            if (renderer == null || !terrain.getBoolean(renderer) || !blockEntities.getBoolean(renderer)) return null;
            double near = Math.max(0, nearPlane.getFloat(renderer));
            double depth = Math.min(MAX_DEPTH, 100.0 - near - CAMERA_SNAP);
            if (!(depth > 0)) return null;
            Object box = boxCuller.get(f);
            double distance = box == null ? Double.POSITIVE_INFINITY : maxDistance.getDouble(box);
            return Double.isNaN(distance) ? null : new Setup(depth, distance);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            return null;
        }
    }

    private static boolean resolve() {
        if (resolved) return !broken;
        resolved = true;
        try {
            Class<?> renderer = Class.forName(RENDERER);
            frustum = renderer.getField("FRUSTUM");
            projection = renderer.getField("PROJECTION");
            terrain = accessible(renderer, "shouldRenderTerrain");
            blockEntities = accessible(renderer, "shouldRenderBlockEntities");
            nearPlane = accessible(renderer, "nearPlane");
            advanced = Class.forName(ADVANCED);
            boxCuller = accessible(advanced, "boxCuller");
            maxDistance = accessible(Class.forName(BOX), "maxDistance");
            pipeline = Class.forName(PIPELINE);
            shadowRenderer = accessible(pipeline, "shadowRenderer");
            pipelineManager = Class.forName(IRIS).getMethod("getPipelineManager");
            pipelineNullable = pipelineManager.getReturnType().getMethod("getPipelineNullable");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
        }
        return !broken;
    }

    private static Field accessible(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
