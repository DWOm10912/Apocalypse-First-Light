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
 * <li>Notch (the standing tree while it is chopped), a felling face cut: two planes through the trunk meeting at a
 * straight apex, a chord at the stage's depth on the chopping side, at the cut height; the upper plane rises at 45
 * degrees (less on a big trunk cut high, so it stays in the root cell), the lower one falls gently. Where the planes
 * are inside the trunk the bark is gone and cut wood shows; on the bark the notch's outline is the planes' intersection
 * with the trunk, smooth because the bands there are split into narrow columns. Every band at the cut height is cut
 * there (no T-junctions), and neighbouring columns share their edges, so the notch is closed (P2.1: was a ring of
 * points pulled toward the axis, which read as a groove round the whole front).</li>
 * </ul>
 */
final class TreeCut {
    /** Extra sprites (TreeCellModel's list): after every species' slots, cut wood per species ordinal, then end grain. */
    static final int CUT_SPRITE = TreeForms.Species.values().length * TreeShape.SLOTS, END_SPRITE = CUT_SPRITE + TreeForms.Species.values().length;
    /**
     * Notch per stage 1..4: apex depth as a share of the trunk radius at the cut (its outline on the bark then spans
     * 31, 44, 57 and 68 degrees either side of the chopping direction); columns per band where the notch reaches.
     */
    static final float[] DEPTH = {0.14F, 0.28F, 0.45F, 0.62F};
    static final int COLUMNS = 4;
    static final float LOWER = 0.2F;

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
        return slice(q, lo, hi, lo, hi);
    }

    /** The part of a band between heights on each tube edge (A: corners 0-3, B: corners 1-2), or null. */
    static Quad slice(Quad q, float loA, float hiA, float loB, float hiB) {
        float a0 = q.p[1], a1 = q.p[10], b0 = q.p[4], b1 = q.p[7];
        float ta0 = t(a0, a1, loA), ta1 = t(a0, a1, hiA), tb0 = t(b0, b1, loB), tb1 = t(b0, b1, hiB);
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

    /** The part of a band between fractions f0 and f1 across it (along its rings): a narrower band. */
    static Quad column(Quad q, float f0, float f1) {
        var r = like(q);
        put(r, 0, at(q, 0, 1, f0));
        put(r, 1, at(q, 0, 1, f1));
        put(r, 2, at(q, 3, 2, f1));
        put(r, 3, at(q, 3, 2, f0));
        return r;
    }

    /**
     * The root cell's quads with the notch of a cut stage (1..4) at the cut height, toward a direction (radians,
     * atan2(dz, dx)); stage 0 or no band at the cut: the quads unchanged.
     */
    static List<Quad> notch(List<Quad> quads, float cut, int stage, double side, int species) {
        if (stage <= 0) return quads;
        float[] ring = ring(quads, cut);
        if (ring == null) return quads;
        float r = ring[2], d = DEPTH[Math.min(FellingRules.STAGES, stage) - 1] * r;
        float nx = (float) Math.cos(side), nz = (float) Math.sin(side);
        // the upper plane at 45 degrees, flatter where the root cell's bands end before it would reach the bark
        float top = Float.MAX_VALUE;
        for (var q : quads) if (band(q) && minY(q) <= cut && maxY(q) >= cut) top = Math.min(top, Math.min(q.p[7], q.p[10]));
        float up = Math.max(0.2F, Math.min(1, (top - 0.02F - cut) / d));
        var g = new Notch(ring[0], ring[1], r - d, nx, nz, cut, up);
        int sprite = CUT_SPRITE + species;
        var out = new ArrayList<Quad>(quads.size() + 48);
        for (var q : quads) {
            if (!band(q) || minY(q) > cut || maxY(q) < cut) { out.add(q); continue; }
            boolean reached = g.depth(edge(q, 0, 3, cut)) > 0 || g.depth(edge(q, 1, 2, cut)) > 0;
            int cols = reached ? COLUMNS : 1;
            for (int j = 0; j < cols; j++) {
                Quad c = cols == 1 ? q : column(q, j / (float) cols, (j + 1) / (float) cols);
                float[] a = edge(c, 0, 3, cut), b = edge(c, 1, 2, cut);
                float da = g.depth(a), db = g.depth(b);
                Quad below = slice(c, -1e9F, cut - LOWER * da, -1e9F, cut - LOWER * db);
                Quad above = slice(c, cut + up * da, 1e9F, cut + up * db, 1e9F);
                if (below != null) out.add(below);
                if (above != null) out.add(above);
                if (da <= 0 && db <= 0) continue;
                float[] a0 = edge(c, 0, 3, cut - LOWER * da), b0 = edge(c, 1, 2, cut - LOWER * db);
                float[] a1 = edge(c, 0, 3, cut + up * da), b1 = edge(c, 1, 2, cut + up * db);
                float[] aa = g.apex(a, da), ba = g.apex(b, db);
                out.add(g.face(new float[][]{a0, b0, ba, aa}, sprite, false));
                out.add(g.face(new float[][]{aa, ba, b1, a1}, sprite, true));
            }
        }
        return out;
    }

    /** The point (p, n, uv, ao) where a band's tube edge (corners a to b) crosses a height. */
    private static float[] edge(Quad q, int a, int b, float y) {
        return at(q, a, b, t(q.p[a * 3 + 1], q.p[b * 3 + 1], y));
    }

    /** The notch's planes: apex chord at distance {@code plane} from the axis along (nx, nz), at the cut height. */
    private record Notch(float cx, float cz, float plane, float nx, float nz, float cut, float up) {
        /** How far a point at the cut height on the bark lies beyond the apex plane (0: the notch does not reach it). */
        float depth(float[] p) {
            return Math.max(0, (p[0] - cx) * nx + (p[2] - cz) * nz - plane);
        }

        /** The apex point behind a bark point: moved back along the notch direction onto the apex plane. */
        float[] apex(float[] p, float depth) {
            float[] v = p.clone();
            v[0] -= nx * depth;
            v[1] = cut;
            v[2] -= nz * depth;
            return v;
        }

        /** A cut-wood face (corner order as a band): its plane's normal, UVs planar across and along the notch. */
        Quad face(float[][] c, int sprite, boolean upper) {
            var q = new Quad();
            q.kind = TreeShape.WOOD; q.sprite = sprite;
            float[] e1 = {c[1][0] - c[0][0], c[1][1] - c[0][1], c[1][2] - c[0][2]}, e2 = {c[3][0] - c[0][0], c[3][1] - c[0][1], c[3][2] - c[0][2]};
            float[] n = TreeShape.norm(TreeShape.cross(e1, e2));
            for (int i = 0; i < 4; i++) {
                float x = c[i][0], y = c[i][1], z = c[i][2];
                float across = (x - cx) * -nz + (z - cz) * nx, out = (x - cx) * nx + (z - cz) * nz - plane;
                q.p[i * 3] = x; q.p[i * 3 + 1] = y; q.p[i * 3 + 2] = z;
                q.n[i * 3] = n[0]; q.n[i * 3 + 1] = n[1]; q.n[i * 3 + 2] = n[2];
                // inside the sprite (no atlas neighbour bleeds in): across at most 0.8 m either side, depth at most 0.48 m
                q.uv[i * 2] = Math.max(0.02F, Math.min(0.98F, 0.5F + across * 0.6F));
                q.uv[i * 2 + 1] = Math.max(0.02F, Math.min(0.98F, 0.5F + (upper ? -out : out)));
                // darker into the notch, toward the apex
                q.ao[i] = 0.74F + 0.21F * Math.min(1, out / 0.12F);
            }
            return q;
        }
    }
}
