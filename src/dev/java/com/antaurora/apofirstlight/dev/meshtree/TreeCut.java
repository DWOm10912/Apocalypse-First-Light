package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeShape.Quad;

import java.util.ArrayList;
import java.util.List;

/**
 * Mesh Trees P2 (docs/rendering/mesh_trees_p2_felling.md, D/I/J): the cut through a trunk, on the tree's own quads.
 * Pure Java (the client model and renderer use it, the offline check tests it).
 * <ul>
 * <li>A trunk quad is a band of TreeShape's tube: corners 0 and 1 on its lower ring, 3 above 0 and 2 above 1. A band
 * is cut by cutting its two edges along the tube at a height, so neighbouring bands, which share those edges, stay
 * joined.</li>
 * <li>Stump: the trunk below the cut and an end-grain cap on it; falling tree: everything above the cut and a cap
 * under it.</li>
 * <li>Notch (the standing tree while it is chopped): in the band round the cut, the edges' points at the cut height
 * move in toward the trunk's axis, most on the chopping side and nothing beyond the notch's half-angle; the band there
 * becomes an upper slanted face and a lower nearly flat face of cut wood. The same edge point moves the same way in
 * both bands that share it, so the notch is closed.</li>
 * </ul>
 */
final class TreeCut {
    /** Extra sprites (TreeCellModel's list): cut wood and end grain, per species ordinal. */
    static final int CUT_SPRITE = 8, END_SPRITE = 10;
    /** Notch per stage 1..4: depth (share of the trunk radius at the cut) and half-angle (degrees). */
    static final float[] DEPTH = {0.14F, 0.28F, 0.45F, 0.62F}, HALF = {38, 50, 62, 72};

    private TreeCut() {}

    static boolean band(Quad q) {
        return q.kind == TreeShape.WOOD && q.p[10] > q.p[1] + 1e-4F && q.p[7] > q.p[4] + 1e-4F;
    }

    static float minY(Quad q) { return Math.min(Math.min(q.p[1], q.p[4]), Math.min(q.p[7], q.p[10])); }

    static float maxY(Quad q) { return Math.max(Math.max(q.p[1], q.p[4]), Math.max(q.p[7], q.p[10])); }

    private static float t(float y0, float y1, float y) {
        return Math.max(0, Math.min(1, (y - y0) / (y1 - y0)));
    }

    /** Corner values (p, n, uv, ao) of the point at t along the tube edge from corner a to corner b. */
    private static float[] at(Quad q, int a, int b, float t) {
        float[] v = new float[9];
        for (int k = 0; k < 3; k++) {
            v[k] = q.p[a * 3 + k] + (q.p[b * 3 + k] - q.p[a * 3 + k]) * t;
            v[3 + k] = q.n[a * 3 + k] + (q.n[b * 3 + k] - q.n[a * 3 + k]) * t;
        }
        v[6] = q.uv[a * 2] + (q.uv[b * 2] - q.uv[a * 2]) * t;
        v[7] = q.uv[a * 2 + 1] + (q.uv[b * 2 + 1] - q.uv[a * 2 + 1]) * t;
        v[8] = q.ao[a] + (q.ao[b] - q.ao[a]) * t;
        return v;
    }

    private static void put(Quad q, int c, float[] v) {
        System.arraycopy(v, 0, q.p, c * 3, 3);
        System.arraycopy(v, 3, q.n, c * 3, 3);
        q.uv[c * 2] = v[6];
        q.uv[c * 2 + 1] = v[7];
        q.ao[c] = v[8];
    }

    private static Quad like(Quad q) {
        var r = new Quad();
        r.kind = q.kind; r.sprite = q.sprite; r.tint = q.tint; r.twoSided = q.twoSided;
        return r;
    }

    /** The part of a band between heights lo and hi (both edges cut there), or null when nothing is left. */
    static Quad slice(Quad q, float lo, float hi) {
        float a0 = q.p[1], a1 = q.p[10], b0 = q.p[4], b1 = q.p[7];
        float ta0 = t(a0, a1, lo), ta1 = t(a0, a1, hi), tb0 = t(b0, b1, lo), tb1 = t(b0, b1, hi);
        if (ta1 - ta0 < 1e-4F && tb1 - tb0 < 1e-4F) return null;
        var r = like(q);
        put(r, 0, at(q, 0, 3, ta0));
        put(r, 1, at(q, 1, 2, tb0));
        put(r, 2, at(q, 1, 2, tb1));
        put(r, 3, at(q, 0, 3, ta1));
        return r;
    }

    /** The stump: the trunk bands below the cut (the rest of the cell's quads dropped) and an end-grain cap. */
    static List<Quad> stump(List<Quad> quads, float cut, int species) {
        var out = new ArrayList<Quad>();
        for (var q : quads) {
            if (!band(q) || minY(q) >= cut) continue;
            Quad s = maxY(q) <= cut ? q : slice(q, -1e9F, cut);
            if (s != null) out.add(s);
        }
        out.addAll(cap(out, cut, true, END_SPRITE + species));
        return out;
    }

    /** The falling tree: every quad above the cut (trunk bands cut there) and a cap under the cut. */
    static List<Quad> fallen(List<Quad> quads, float cut, int species) {
        var out = new ArrayList<Quad>();
        var ring = new ArrayList<Quad>();
        for (var q : quads) {
            if (!band(q)) { if (q.kind != TreeShape.WOOD || minY(q) >= cut) out.add(q); continue; }
            if (maxY(q) <= cut) continue;
            if (minY(q) >= cut) { out.add(q); continue; }
            Quad s = slice(q, cut, 1e9F);
            if (s != null) { out.add(s); ring.add(s); }
        }
        out.addAll(cap(ring, cut, false, END_SPRITE + species));
        return out;
    }

    /**
     * A cap over the cut ring of cut bands (their edge ends at the cut height): a fan of triangles (as quads with the
     * last corner repeated) from the ring's centre, wound to face up (stump) or down (falling log), end-grain UVs
     * centred on the ring.
     */
    static List<Quad> cap(List<Quad> cutBands, float cut, boolean up, int sprite) {
        var seg = new ArrayList<float[]>();
        for (var q : cutBands) {
            int a = up ? 3 : 0, b = up ? 2 : 1;
            if (Math.abs(q.p[a * 3 + 1] - cut) > 1e-3F || Math.abs(q.p[b * 3 + 1] - cut) > 1e-3F) continue;
            seg.add(new float[]{q.p[a * 3], q.p[a * 3 + 1], q.p[a * 3 + 2], q.p[b * 3], q.p[b * 3 + 1], q.p[b * 3 + 2]});
        }
        var out = new ArrayList<Quad>();
        if (seg.size() < 3) return out;
        float cx = 0, cz = 0;
        for (float[] s : seg) { cx += s[0] + s[3]; cz += s[2] + s[5]; }
        cx /= 2 * seg.size(); cz /= 2 * seg.size();
        float rad = 0;
        for (float[] s : seg) rad = Math.max(rad, Math.max((float) Math.hypot(s[0] - cx, s[2] - cz), (float) Math.hypot(s[3] - cx, s[5] - cz)));
        float scale = 1 / (2.1F * Math.max(0.05F, rad));
        for (float[] s : seg) {
            float[] A = {s[0], cut, s[2]}, B = {s[3], cut, s[5]};
            // (A - C) x (B - C), y component: positive faces up
            float ny = (A[2] - cz) * (B[0] - cx) - (A[0] - cx) * (B[2] - cz);
            if ((ny > 0) != up) { float[] t = A; A = B; B = t; }
            var q = new Quad();
            q.kind = TreeShape.WOOD; q.sprite = sprite;
            float[][] c = {{cx, cut, cz}, A, B, B};
            for (int i = 0; i < 4; i++) {
                q.p[i * 3] = c[i][0]; q.p[i * 3 + 1] = cut; q.p[i * 3 + 2] = c[i][2];
                q.n[i * 3 + 1] = up ? 1 : -1;
                q.uv[i * 2] = 0.5F + (c[i][0] - cx) * scale;
                q.uv[i * 2 + 1] = 0.5F + (c[i][2] - cz) * scale;
                q.ao[i] = 0.95F;
            }
            out.add(q);
        }
        return out;
    }

    /** The trunk's centre and mean radius at a height, from the bands crossing it. {x, z, r} or null. */
    static float[] ring(List<Quad> quads, float y) {
        float sx = 0, sz = 0;
        int n = 0;
        var pts = new ArrayList<float[]>();
        for (var q : quads) {
            if (!band(q) || minY(q) > y || maxY(q) < y) continue;
            float[] a = at(q, 0, 3, t(q.p[1], q.p[10], y)), b = at(q, 1, 2, t(q.p[4], q.p[7], y));
            pts.add(a); pts.add(b);
            sx += a[0] + b[0]; sz += a[2] + b[2]; n += 2;
        }
        if (n < 6) return null;
        float cx = sx / n, cz = sz / n, r = 0;
        for (float[] p : pts) r += (float) Math.hypot(p[0] - cx, p[2] - cz);
        return new float[]{cx, cz, r / n};
    }

    /** How far the notch reaches in at an angle round the trunk (0 outside its half-angle, 1 on its centre line). */
    static float taper(double angle, double side, float halfDeg) {
        double c = Math.cos(angle - side), lim = Math.cos(Math.toRadians(halfDeg));
        double w = (c - lim) / (1 - lim);
        return w <= 0 ? 0 : (float) Math.pow(Math.min(1, w), 0.7);
    }

    /**
     * The root cell's quads with the notch of a cut stage (1..4) at the cut height, centred on a direction (radians,
     * atan2(dz, dx)); stage 0 or no band at the cut: the quads unchanged.
     */
    static List<Quad> notch(List<Quad> quads, float cut, int stage, double side, int species) {
        if (stage <= 0) return quads;
        float[] ring = ring(quads, cut);
        if (ring == null) return quads;
        int s = Math.min(FellingRules.STAGES, stage) - 1;
        float depth = DEPTH[s] * ring[2], up = depth, lo = depth * 0.3F, half = HALF[s];
        var out = new ArrayList<Quad>(quads.size() + 16);
        for (var q : quads) {
            if (!band(q) || maxY(q) <= cut - lo || minY(q) >= cut + up) { out.add(q); continue; }
            Quad below = slice(q, -1e9F, cut - lo), above = slice(q, cut + up, 1e9F), mid = slice(q, cut - lo, cut + up);
            if (below != null) out.add(below);
            if (above != null) out.add(above);
            if (mid == null) continue;
            float[] a0 = at(q, 0, 3, t(q.p[1], q.p[10], cut - lo)), a1 = at(q, 0, 3, t(q.p[1], q.p[10], cut + up));
            float[] b0 = at(q, 1, 2, t(q.p[4], q.p[7], cut - lo)), b1 = at(q, 1, 2, t(q.p[4], q.p[7], cut + up));
            float[] am = at(q, 0, 3, t(q.p[1], q.p[10], cut)), bm = at(q, 1, 2, t(q.p[4], q.p[7], cut));
            float wa = taper(Math.atan2(am[2] - ring[1], am[0] - ring[0]), side, half);
            float wb = taper(Math.atan2(bm[2] - ring[1], bm[0] - ring[0]), side, half);
            if (wa <= 0 && wb <= 0) { out.add(mid); continue; }
            inward(am, ring, depth * wa);
            inward(bm, ring, depth * wb);
            int sprite = CUT_SPRITE + species;
            out.add(face(new float[][]{a0, b0, bm, am}, sprite, new float[]{1, 1, 0.55F, 0.55F}, wa, wb));
            out.add(face(new float[][]{am, bm, b1, a1}, sprite, new float[]{0.55F, 0.55F, 0, 0}, wa, wb));
        }
        return out;
    }

    private static void inward(float[] v, float[] ring, float d) {
        float dx = ring[0] - v[0], dz = ring[1] - v[2], l = (float) Math.hypot(dx, dz);
        if (l < 1e-5F || d <= 0) return;
        v[0] += dx / l * d;
        v[2] += dz / l * d;
    }

    /** A cut-wood face over four points (corner order as a band), its own flat normal, v from the given values. */
    private static Quad face(float[][] c, int sprite, float[] v, float wa, float wb) {
        var q = new Quad();
        q.kind = TreeShape.WOOD; q.sprite = sprite;
        float[] e1 = {c[1][0] - c[0][0], c[1][1] - c[0][1], c[1][2] - c[0][2]}, e2 = {c[3][0] - c[0][0], c[3][1] - c[0][1], c[3][2] - c[0][2]};
        float[] n = TreeShape.norm(TreeShape.cross(e1, e2));
        for (int i = 0; i < 4; i++) {
            q.p[i * 3] = c[i][0]; q.p[i * 3 + 1] = c[i][1]; q.p[i * 3 + 2] = c[i][2];
            q.n[i * 3] = n[0]; q.n[i * 3 + 1] = n[1]; q.n[i * 3 + 2] = n[2];
            q.uv[i * 2] = c[i][6];
            q.uv[i * 2 + 1] = v[i];
            // deeper inside the notch is darker
            float w = i == 0 || i == 3 ? wa : wb;
            q.ao[i] = (v[i] == 0.55F ? 0.78F + 0.12F * (1 - w) : 0.95F);
        }
        return q;
    }
}
