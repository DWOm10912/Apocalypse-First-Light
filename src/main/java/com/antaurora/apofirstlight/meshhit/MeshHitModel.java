package com.antaurora.apofirstlight.meshhit;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One render model's triangles for ray hits (docs/rendering/mesh_hit_runtime_v1.md): read from the mod jar's model JSON
 * ({@code "loader": "forge:obj"}) and its OBJ (vertices in block units, 0..1, as every AFL generator writes them), quads
 * split in two, degenerate faces dropped; a bounding volume hierarchy (median split on the longest axis, at most
 * {@link #LEAF} triangles a leaf) over them. Immutable once loaded; shared by the server and the client threads.
 */
public final class MeshHitModel {
    private static final int LEAF = 8;
    private static final float EPS = 1e-7F;

    /** Triangles: 9 floats each (three corners). */
    private final float[] tri;
    /** BVH nodes: bounds (6 floats each), and per node either children (left, right) or a leaf range in {@link #order}. */
    private final float[] bounds;
    private final int[] left, right, start, count;
    private final int[] order;
    private int nodes;

    private MeshHitModel(float[] tri) {
        this.tri = tri;
        int n = tri.length / 9, cap = Math.max(1, 2 * n);
        bounds = new float[cap * 6];
        left = new int[cap];
        right = new int[cap];
        start = new int[cap];
        count = new int[cap];
        order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        build(0, n);
    }

    public int triangles() {
        return tri.length / 9;
    }

    /** The model's bounds in block units {minX, minY, minZ, maxX, maxY, maxZ}. */
    public float[] bounds() {
        return Arrays.copyOf(bounds, 6);
    }

    /**
     * The nearest triangle along {@code origin + t * dir} for t in (0, tMax], both sides counting; null if none. The hit's
     * normal (unit, in model space) faces back toward the ray.
     */
    @Nullable
    public double[] intersect(double ox, double oy, double oz, double dx, double dy, double dz, double tMax) {
        if (nodes == 0) return null;
        double best = tMax;
        int bestTri = -1;
        // (an axis the ray runs square to: a tiny step instead of 0, so the slabs never see 0 x infinity)
        double ix = 1 / (dx == 0 ? 1e-12 : dx), iy = 1 / (dy == 0 ? 1e-12 : dy), iz = 1 / (dz == 0 ? 1e-12 : dz);
        int[] stack = new int[64];
        int sp = 0;
        stack[sp++] = 0;
        while (sp > 0) {
            int node = stack[--sp];
            if (!slab(node, ox, oy, oz, ix, iy, iz, best)) continue;
            if (count[node] > 0) {
                for (int k = start[node], end = k + count[node]; k < end; k++) {
                    double t = triangle(order[k], ox, oy, oz, dx, dy, dz);
                    if (t > 0 && t < best) {
                        best = t;
                        bestTri = order[k];
                    }
                }
            } else if (sp + 2 <= stack.length) {
                stack[sp++] = left[node];
                stack[sp++] = right[node];
            }
        }
        if (bestTri < 0) return null;
        int b = bestTri * 9;
        double e1x = tri[b + 3] - tri[b], e1y = tri[b + 4] - tri[b + 1], e1z = tri[b + 5] - tri[b + 2];
        double e2x = tri[b + 6] - tri[b], e2y = tri[b + 7] - tri[b + 1], e2z = tri[b + 8] - tri[b + 2];
        double nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= len;
        ny /= len;
        nz /= len;
        if (nx * dx + ny * dy + nz * dz > 0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        return new double[]{best, nx, ny, nz};
    }

    private boolean slab(int node, double ox, double oy, double oz, double ix, double iy, double iz, double tMax) {
        int b = node * 6;
        double t0 = 0, t1 = tMax;
        double a = (bounds[b] - ox) * ix, c = (bounds[b + 3] - ox) * ix;
        t0 = Math.max(t0, Math.min(a, c));
        t1 = Math.min(t1, Math.max(a, c));
        a = (bounds[b + 1] - oy) * iy;
        c = (bounds[b + 4] - oy) * iy;
        t0 = Math.max(t0, Math.min(a, c));
        t1 = Math.min(t1, Math.max(a, c));
        a = (bounds[b + 2] - oz) * iz;
        c = (bounds[b + 5] - oz) * iz;
        t0 = Math.max(t0, Math.min(a, c));
        t1 = Math.min(t1, Math.max(a, c));
        return t0 <= t1 + 1e-9;
    }

    /** Möller-Trumbore, both sides; t or -1. */
    private double triangle(int i, double ox, double oy, double oz, double dx, double dy, double dz) {
        int b = i * 9;
        double e1x = tri[b + 3] - tri[b], e1y = tri[b + 4] - tri[b + 1], e1z = tri[b + 5] - tri[b + 2];
        double e2x = tri[b + 6] - tri[b], e2y = tri[b + 7] - tri[b + 1], e2z = tri[b + 8] - tri[b + 2];
        double px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
        double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < EPS) return -1;
        double inv = 1 / det;
        double sx = ox - tri[b], sy = oy - tri[b + 1], sz = oz - tri[b + 2];
        double u = (sx * px + sy * py + sz * pz) * inv;
        if (u < 0 || u > 1) return -1;
        double qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x;
        double v = (dx * qx + dy * qy + dz * qz) * inv;
        if (v < 0 || u + v > 1) return -1;
        return (e2x * qx + e2y * qy + e2z * qz) * inv;
    }

    private int build(int from, int to) {
        int node = nodes++;
        int b = node * 6;
        float[] cMin = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, cMax = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        bounds[b] = bounds[b + 1] = bounds[b + 2] = Float.MAX_VALUE;
        bounds[b + 3] = bounds[b + 4] = bounds[b + 5] = -Float.MAX_VALUE;
        for (int k = from; k < to; k++) {
            int t = order[k] * 9;
            for (int c = 0; c < 3; c++) for (int a = 0; a < 3; a++) {
                float p = tri[t + c * 3 + a];
                bounds[b + a] = Math.min(bounds[b + a], p);
                bounds[b + 3 + a] = Math.max(bounds[b + 3 + a], p);
            }
            for (int a = 0; a < 3; a++) {
                float centre = (tri[t + a] + tri[t + 3 + a] + tri[t + 6 + a]) / 3;
                cMin[a] = Math.min(cMin[a], centre);
                cMax[a] = Math.max(cMax[a], centre);
            }
        }
        if (to - from <= LEAF) {
            start[node] = from;
            count[node] = to - from;
            return node;
        }
        int axis = 0;
        for (int a = 1; a < 3; a++) if (cMax[a] - cMin[a] > cMax[axis] - cMin[axis]) axis = a;
        final int ax = axis;
        Integer[] slice = new Integer[to - from];
        for (int k = from; k < to; k++) slice[k - from] = order[k];
        Arrays.sort(slice, (p, q) -> Float.compare(tri[p * 9 + ax] + tri[p * 9 + 3 + ax] + tri[p * 9 + 6 + ax],
                tri[q * 9 + ax] + tri[q * 9 + 3 + ax] + tri[q * 9 + 6 + ax]));
        for (int k = from; k < to; k++) order[k] = slice[k - from];
        int mid = (from + to) >>> 1;
        count[node] = 0;
        left[node] = build(from, mid);
        right[node] = build(mid, to);
        return node;
    }

    /** Loads {@code model} (e.g. apocalypse_firstlight:block/fuel_drum/body) from the jar, or null (not an OBJ model, missing). */
    @Nullable
    static MeshHitModel load(ResourceLocation model) {
        String json = "/assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json";
        try (InputStream in = MeshHitModel.class.getResourceAsStream(json)) {
            if (in == null) return null;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("loader") || !"forge:obj".equals(root.get("loader").getAsString()) || !root.has("model")) return null;
            ResourceLocation obj = new ResourceLocation(root.get("model").getAsString());
            float[] tri = readObj("/assets/" + obj.getNamespace() + "/" + obj.getPath());
            return tri == null || tri.length == 0 ? null : new MeshHitModel(tri);
        } catch (Exception e) {
            ApocalypseFirstLight.LOGGER.error("[AFL MESH HIT] could not read model {}", model, e);
            return null;
        }
    }

    @Nullable
    private static float[] readObj(String path) throws java.io.IOException {
        try (InputStream in = MeshHitModel.class.getResourceAsStream(path)) {
            if (in == null) return null;
            List<float[]> vertices = new ArrayList<>();
            List<Float> out = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.startsWith("v ")) {
                    String[] p = line.trim().split("\\s+");
                    vertices.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
                } else if (line.startsWith("f ")) {
                    String[] p = line.trim().split("\\s+");
                    int[] idx = new int[p.length - 1];
                    for (int k = 1; k < p.length; k++) {
                        int v = Integer.parseInt(p[k].split("/")[0]);
                        idx[k - 1] = v > 0 ? v - 1 : vertices.size() + v;
                    }
                    for (int k = 1; k + 1 < idx.length; k++) {   // a fan
                        float[] a = vertices.get(idx[0]), b = vertices.get(idx[k]), c = vertices.get(idx[k + 1]);
                        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
                        double cx = uy * wz - uz * wy, cy = uz * wx - ux * wz, cz = ux * wy - uy * wx;
                        if (cx * cx + cy * cy + cz * cz < 1e-14) continue;
                        for (float[] q : new float[][]{a, b, c}) for (float f : q) out.add(f);
                    }
                }
            }
            float[] tri = new float[out.size()];
            for (int i = 0; i < tri.length; i++) tri[i] = out.get(i);
            return tri;
        }
    }
}
