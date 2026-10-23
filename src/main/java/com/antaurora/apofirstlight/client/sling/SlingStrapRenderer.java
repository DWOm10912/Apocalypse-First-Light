package com.antaurora.apofirstlight.client.sling;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.MaintenanceGunRendering;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws a sling strap from its cache (SlingStraps#cache): the webbing as a flat box along the chain, textured from the
 * gun_sling_strap mesh (a 6 cm piece, mirrored piece by piece along the strap), and a steel snap hook (gun_sling_hook) on
 * each end segment, its pin on the swivel's bar and its folded web at the strap's exit
 * (docs/native_guns/attachments/sling_physics_v1.md 5).
 */
final class SlingStrapRenderer {
    private static final ResourceLocation HOOK_GEO = id("geo/gun_sling_hook.geo.json"), STRAP_GEO = id("geo/gun_sling_strap.geo.json");
    private static final ResourceLocation HOOK_TEXTURE = id("textures/item/gun_sling_hook_mesh.png"),
            STRAP_TEXTURE = id("textures/item/gun_sling_strap_mesh.png");
    /** Hook mesh: pin above the strap's exit (mesh blocks; 1 px is 1 cm), tools/build-equipment-meshes-v1.mjs gunSlingHook. */
    private static final float HOOK_PIN = 8.17f / 16;
    /** Strap piece: its length (metres) the texture repeats over. */
    private static final double PIECE = 0.06;

    /** The strap mesh the texture maps were read from, and the maps (one pair, read together by any thread). */
    private record Uv(MeshModel mesh, float[] maps) {}
    private static volatile Uv strapUv;

    private SlingStrapRenderer() {}

    private static ResourceLocation id(String path) { return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path); }

    static void draw(float[] c, Matrix4f pose, Matrix3f normal, MultiBufferSource buffers, int light) {
        int n = (c.length + 1) / 10;
        if (n < 4) return;
        var faces = buffers instanceof MaintenanceGunRendering.ClosedFaces f ? f : null;
        float[] uv = strapUv();
        if (uv != null) {
            if (faces != null) faces.closedFaces(false);
            VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(STRAP_TEXTURE));
            double along = 0;
            for (int i = 1; i < n - 2; i++) {
                double len = c[n * 9 + i];
                segment(out, c, i, along, along + len, uv, pose, normal, light);
                along += len;
            }
        }
        MeshModel hook = VextrisMeshes.byGeometry(HOOK_GEO);
        if (hook == null) return;
        List<MeshPart> parts = hook.parts("gun_sling_hook", MeshLayer.CUTOUT);
        if (parts.isEmpty()) return;
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(HOOK_TEXTURE));
        hook(parts, out, c, 0, 1, pose, light, faces);
        hook(parts, out, c, n - 1, n - 2, pose, light, faces);
        if (faces != null) faces.closedFaces(false);
    }

    /**
     * The webbing between nodes i and i + 1 (a and b: its distance along the strap from the first hook's exit), split where
     * the texture's pieces meet.
     */
    private static void segment(VertexConsumer out, float[] c, int i, double a, double b, float[] uv, Matrix4f pose, Matrix3f normal, int light) {
        double from = a;
        while (from < b - 1e-6) {
            double edge = (Math.floor(from / PIECE + 1e-6) + 1) * PIECE, to = Math.min(b, edge);
            int piece = (int) Math.floor(from / PIECE + 1e-6);
            double f0 = (from - a) / (b - a), f1 = (to - a) / (b - a);
            double t0 = from / PIECE - piece, t1 = to / PIECE - piece;
            if ((piece & 1) == 1) { t0 = 1 - t0; t1 = 1 - t1; }
            box(out, c, i, f0, f1, t0, t1, uv, pose, normal, light);
            from = to;
        }
    }

    /** One piece of webbing: the two flat faces and the two edges, t along the texture's piece (0 .. 1). */
    private static void box(VertexConsumer out, float[] c, int i, double f0, double f1, double t0, double t1, float[] uv,
                            Matrix4f pose, Matrix3f normal, int light) {
        float[] p0 = new float[9], p1 = new float[9];
        lerp(c, i, f0, p0);
        lerp(c, i, f1, p1);
        // flat faces (+thickness, -thickness), then edges (+width, -width): corners across s = -1 .. 1
        for (int side = 0; side < 4; side++) {
            boolean flat = side < 2;
            float sign = (side & 1) == 0 ? 1 : -1;
            int nOff = flat ? 6 : 3, sOff = flat ? 3 : 6;
            int u = flat ? 0 : 6;
            float[][] q = new float[4][];
            double[] s = {-1, 1, 1, -1}, t = {t0, t0, t1, t1};
            for (int k = 0; k < 4; k++) {
                float[] p = k < 2 ? p0 : p1;
                double across = s[k] * sign;
                q[k] = new float[] {
                        (float) (p[0] + p[nOff] * sign + p[sOff] * across),
                        (float) (p[1] + p[nOff + 1] * sign + p[sOff + 1] * across),
                        (float) (p[2] + p[nOff + 2] * sign + p[sOff + 2] * across),
                        (float) (uv[u] + uv[u + 1] * across + uv[u + 2] * t[k]),
                        (float) (uv[u + 3] + uv[u + 4] * across + uv[u + 5] * t[k])};
            }
            float nx = (p0[nOff] + p1[nOff]) * sign, ny = (p0[nOff + 1] + p1[nOff + 1]) * sign, nz = (p0[nOff + 2] + p1[nOff + 2]) * sign;
            quad(out, q, nx, ny, nz, pose, normal, light);
        }
    }

    private static void lerp(float[] c, int i, double f, float[] out) {
        int a = i * 9, b = (i + 1) * 9;
        for (int k = 0; k < 9; k++) out[k] = (float) (c[a + k] + (c[b + k] - c[a + k]) * f);
    }

    private static void quad(VertexConsumer out, float[][] q, float nx, float ny, float nz, Matrix4f pose, Matrix3f normal, int light) {
        // chunk threads draw still straps: no shared scratch
        Vector3f V = new Vector3f(), N = normal.transform(new Vector3f(nx, ny, nz));
        if (N.lengthSquared() < 1e-20f) N.set(0, 1, 0); else N.normalize();
        for (float[] v : q) {
            pose.transformPosition(V.set(v[0], v[1], v[2]));
            out.vertex(V.x, V.y, V.z, 1, 1, 1, 1, v[3], v[4], OverlayTexture.NO_OVERLAY, light, N.x, N.y, N.z);
        }
    }

    /**
     * A hook on the end segment from node pin (on the swivel's bar) to node exit (where the strap leaves the hook's fold):
     * the mesh's +y from exit to pin, its +x along the strap's width at the exit.
     */
    private static void hook(List<MeshPart> parts, VertexConsumer out, float[] c, int pin, int exit, Matrix4f pose, int light,
                             MaintenanceGunRendering.ClosedFaces faces) {
        int e = exit * 9, p = pin * 9;
        Vector3f y = new Vector3f(c[p] - c[e], c[p + 1] - c[e + 1], c[p + 2] - c[e + 2]);
        float length = y.length();
        if (length < 1e-6f) return;
        y.div(length);
        Vector3f x = new Vector3f(c[e + 3], c[e + 4], c[e + 5]);
        x.sub(new Vector3f(y).mul(x.dot(y)));
        if (x.lengthSquared() < 1e-12f) return;
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y);
        float scale = length / HOOK_PIN;
        Matrix4f local = new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, c[e], c[e + 1], c[e + 2], 1).scale(scale);
        PoseStack stack = new PoseStack();
        stack.last().pose().set(pose).mul(local);
        Matrix3f linear = new Matrix3f(stack.last().pose());
        float unit = (float) Math.cbrt(Math.abs(linear.determinant()));
        stack.last().normal().set(linear.invert().transpose().scale(unit));
        for (MeshPart part : parts) {
            if (faces != null) faces.closedFaces(part.closed());
            MeshRenderer.renderParts(List.of(part), stack, out, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
    }

    /**
     * The strap piece's texture rectangles as affine maps: for the flat faces u, du/ds, du/dt, v, dv/ds, dv/dt (s across
     * the width -1 .. 1, t along the piece 0 .. 1), then the same for the edges (s across the thickness). Null until the
     * mesh is loaded; read again after a resource reload.
     */
    private static float[] strapUv() {
        MeshModel mesh = VextrisMeshes.byGeometry(STRAP_GEO);
        if (mesh == null) return null;
        Uv cached = strapUv;
        if (cached != null && cached.mesh == mesh) return cached.maps;
        float[] out = null, flat = null, edge = null;
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, wx = 0, wz = 0;
        for (MeshPart part : mesh.allParts())
            for (int f = 0; f < part.faceCount(); f++)
                for (int k = 0; k < part.faceSize(f); k++) {
                    int i = part.faceStart(f) + k;
                    lo = Math.min(lo, part.value(i, 1)); hi = Math.max(hi, part.value(i, 1));
                    wx = Math.max(wx, Math.abs(part.value(i, 0))); wz = Math.max(wz, Math.abs(part.value(i, 2)));
                }
        for (MeshPart part : mesh.allParts())
            for (int f = 0; f < part.faceCount(); f++) {
                int s = part.faceStart(f);
                float nx = part.value(s, 5), nz = part.value(s, 7);
                if (flat == null && nz > 0.9f) flat = fit(part, s, part.faceSize(f), 0, wx, lo, hi);
                if (edge == null && nx > 0.9f) edge = fit(part, s, part.faceSize(f), 2, wz, lo, hi);
            }
        if (flat != null && edge != null) {
            out = new float[12];
            System.arraycopy(flat, 0, out, 0, 6);
            System.arraycopy(edge, 0, out, 6, 6);
        }
        strapUv = new Uv(mesh, out);
        return out;
    }

    /** u and v as affine functions of s (axis across over half), t ((y - lo) / (hi - lo)), from three corners of a face. */
    private static float[] fit(MeshPart part, int start, int size, int axis, float half, float lo, float hi) {
        if (size < 3 || half <= 0 || hi <= lo) return null;
        double[][] m = new double[3][];
        for (int k = 0; k < 3; k++) {
            int i = start + k;
            m[k] = new double[] {part.value(i, axis) / half, (part.value(i, 1) - lo) / (hi - lo), part.value(i, 3), part.value(i, 4)};
        }
        double a1 = m[1][0] - m[0][0], b1 = m[1][1] - m[0][1], a2 = m[2][0] - m[0][0], b2 = m[2][1] - m[0][1], det = a1 * b2 - a2 * b1;
        if (Math.abs(det) < 1e-9) return null;
        float[] out = new float[6];
        for (int c = 0; c < 2; c++) {
            double d1 = m[1][2 + c] - m[0][2 + c], d2 = m[2][2 + c] - m[0][2 + c];
            double ds = (d1 * b2 - d2 * b1) / det, dt = (a1 * d2 - a2 * d1) / det;
            out[c * 3] = (float) (m[0][2 + c] - ds * m[0][0] - dt * m[0][1]);
            out[c * 3 + 1] = (float) ds;
            out[c * 3 + 2] = (float) dt;
        }
        return out;
    }
}
