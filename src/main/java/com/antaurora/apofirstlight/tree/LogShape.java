package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.tree.TreeForms.Species;
import com.antaurora.apofirstlight.tree.TreeShape.Quad;

import java.util.ArrayList;
import java.util.List;

/**
 * Mesh Trees P2.2 (docs/rendering/mesh_trees_p2_felling.md, V): the log block's geometry, a rounded-square prism one
 * block long. Pure Java (the client model bakes it, the offline check tests it).
 * <ul>
 * <li>Cross-section: the full block square with rounded corners, {@value #ARC} segments a corner. Per species (the
 * end texture's outline matches: radius x 32 px): shagbark hickory 2.5/16, white oak 3/16, red oak 3.5/16 (the hard
 * hardwoods squarest), red maple 4.5/16, white pine 5/16, eastern hemlock 6/16 (the softwoods roundest). The flat middle of each side lies on the block's
 * face and is culled against a neighbour like a cube face; the corner strips are never culled. Stacked logs therefore
 * show the grooves of a log wall and no gap.</li>
 * <li>Bark round the sides: each side, with the halves of its two corners, is one bark tile across (the seams sit on the
 * corners' middles, where the seamless bark meets itself); the tile runs once along the block.</li>
 * <li>Ends: the end-grain cap of the log end texture, mapped flat (the texture holds the rounded outline and its bark rim):
 * a centre square, four side strips (axis-aligned quads, culled with the end face) and four corner fans.</li>
 * <li>Axis X / Y / Z like a vanilla log: the Y log turned.</li>
 * </ul>
 * Quads use TreeShape.Quad: positions in the block (0..1), per-corner normals (flat faces axis-aligned, corners radial),
 * sprite 0 bark / 1 end grain (the model maps them per species), kind WOOD; a cull direction per quad in {@link Part},
 * and for a corner strip its corner (the model lights it from the two sides it faces, {@link #cornerSides}).
 */
public final class LogShape {
    public static final int ARC = 4;
    public static final int BARK = 0, END = 1;
    /** Cull directions as Direction ordinals (DOWN, UP, NORTH, SOUTH, WEST, EAST) or -1: never culled. */
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5, NONE = -1;

    /** A quad, its cull direction (or NONE) and, for a corner strip, its corner 0..3 (NE, SE, SW, NW of the +Y log), else -1. */
    public record Part(Quad quad, int cull, int corner) {
        Part(Quad quad, int cull) { this(quad, cull, -1); }
    }

    private LogShape() {}

    /** Corner radius (blocks) of a species' log. */
    public static float radius(Species s) {
        return switch (s) {
            case WHITE_OAK -> 3 / 16F;
            case WHITE_PINE -> 5 / 16F;
            case RED_OAK -> 3.5F / 16;
            case SHAGBARK_HICKORY -> 2.5F / 16;
            case RED_MAPLE -> 4.5F / 16;
            case EASTERN_HEMLOCK -> 6 / 16F;
        };
    }

    /** The rounded outline, each point {x, z, nx, nz}: corners NE, SE, SW, NW, each arc from one side's normal to the next. */
    static List<float[]> outline(float r) {
        var pts = new ArrayList<float[]>();
        // arc centres (1-r, r) north-east, (1-r, 1-r) south-east, (r, 1-r) south-west, (r, r) north-west; between them the
        // flat sides east, south, west, north
        float[][] centres = {{1 - r, r}, {1 - r, 1 - r}, {r, 1 - r}, {r, r}};
        double[] start = {-Math.PI / 2, 0, Math.PI / 2, Math.PI};
        for (int k = 0; k < 4; k++) {
            for (int i = 0; i <= ARC; i++) {
                double a = start[k] + Math.PI / 2 * i / ARC;
                float nx = (float) Math.cos(a), nz = (float) Math.sin(a);
                pts.add(new float[]{centres[k][0] + nx * r, centres[k][1] + nz * r, nx, nz});
            }
        }
        return pts;
    }

    /** The log along +Y, its parts (positions in the block). */
    public static List<Part> build(Species s) {
        return build(radius(s));
    }

    /**
     * The prism of corner radius r along +Y (the logs: their species' radius; the rounded timber, WoodBlocks: one radius
     * for every species), its parts; sprite BARK is the side texture, END the ends'.
     */
    public static List<Part> build(float r) {
        var pts = outline(r);
        var out = new ArrayList<Part>();
        int n = pts.size();                         // 4 corners x (ARC + 1) points; consecutive corners joined by a flat side
        // perimeter positions for the bark u: each side tile runs from the middle of one corner to the middle of the next
        float arcStep = (float) (Math.PI / 2 * r / ARC), flat = 1 - 2 * r;
        float tile = flat + 2 * (ARC / 2) * arcStep;
        for (int i = 0; i < n; i++) {
            float[] a = pts.get(i), b = pts.get((i + 1) % n);
            int corner = i / (ARC + 1), j = i % (ARC + 1);
            boolean flatSide = j == ARC;            // the last point of a corner to the first of the next: a flat side
            if (!flatSide && Math.hypot(b[0] - a[0], b[1] - a[1]) < 1e-6) continue;
            // u: which tile, and where along it (corner arcs split at their middle)
            float ua, ub;
            if (flatSide) {
                ua = (ARC / 2) * arcStep / tile;
                ub = ua + flat / tile;
            } else if (j < ARC / 2) {
                // first half of a corner: the end of the previous side's tile
                ua = (ARC / 2 * arcStep + flat + j * arcStep) / tile;
                ub = ua + arcStep / tile;
            } else {
                // second half: the start of the next side's tile
                ua = (j - ARC / 2) * arcStep / tile;
                ub = ua + arcStep / tile;
            }
            out.add(flatSide ? new Part(side(a, b, ua, ub, true), sideCull(corner)) : new Part(side(a, b, ua, ub, false), NONE, corner));
        }
        // ends: the top (y 1) and the bottom (y 0)
        for (int end = 0; end < 2; end++) {
            boolean top = end == 0;
            float y = top ? 1 : 0;
            int cull = top ? UP : DOWN;
            // centre square and side strips (rectangles), corner fans
            out.add(new Part(rect(r, 1 - r, r, 1 - r, y, top), cull));
            out.add(new Part(rect(r, 1 - r, 0, r, y, top), cull));
            out.add(new Part(rect(r, 1 - r, 1 - r, 1, y, top), cull));
            out.add(new Part(rect(0, r, r, 1 - r, y, top), cull));
            out.add(new Part(rect(1 - r, 1, r, 1 - r, y, top), cull));
            float[][] centres = {{1 - r, r}, {1 - r, 1 - r}, {r, 1 - r}, {r, r}};
            for (int k = 0; k < 4; k++) for (int i = 0; i < ARC; i++) {
                float[] a = pts.get(k * (ARC + 1) + i), b = pts.get(k * (ARC + 1) + i + 1);
                out.add(new Part(fan(centres[k], a, b, y, top), cull));
            }
        }
        return out;
    }

    /** The two sides a corner faces (NE: north, east; SE: east, south; SW: south, west; NW: west, north), turned to an axis. */
    public static int[] cornerSides(int corner, int axis) {
        int[][] sides = {{NORTH, EAST}, {EAST, SOUTH}, {SOUTH, WEST}, {WEST, NORTH}};
        return new int[]{turnCull(sides[corner][0], axis), turnCull(sides[corner][1], axis)};
    }

    /** The flat side between corner k and corner k + 1 faces east, south, west, north in turn. */
    private static int sideCull(int corner) {
        return switch (corner) { case 0 -> EAST; case 1 -> SOUTH; case 2 -> WEST; default -> NORTH; };
    }

    /** A side quad from outline point a to b, full height; wound to face outward. */
    private static Quad side(float[] a, float[] b, float ua, float ub, boolean flat) {
        var q = new Quad();
        q.kind = TreeShape.WOOD; q.sprite = BARK;
        float nx = flat ? (a[2] + b[2]) / 2 : 0, nz = flat ? (a[3] + b[3]) / 2 : 0;
        if (flat) { float l = (float) Math.hypot(nx, nz); nx /= l; nz /= l; }
        // corners: a top, a bottom, b bottom, b top (outward for a counter-clockwise outline, checked below)
        float[][] c = {{a[0], 1, a[1]}, {a[0], 0, a[1]}, {b[0], 0, b[1]}, {b[0], 1, b[1]}};
        float[][] nrm = flat ? new float[][]{{nx, 0, nz}, {nx, 0, nz}, {nx, 0, nz}, {nx, 0, nz}}
                : new float[][]{{a[2], 0, a[3]}, {a[2], 0, a[3]}, {b[2], 0, b[3]}, {b[2], 0, b[3]}};
        float[][] uv = {{ua, 0}, {ua, 1}, {ub, 1}, {ub, 0}};
        fill(q, c, nrm, uv);
        orient(q, new float[]{(a[2] + b[2]) / 2, 0, (a[3] + b[3]) / 2});
        return q;
    }

    /** An end rectangle [x0, x1] x [z0, z1] at height y, facing up or down; UVs = the block's x, z. */
    private static Quad rect(float x0, float x1, float z0, float z1, float y, boolean up) {
        var q = new Quad();
        q.kind = TreeShape.WOOD; q.sprite = END;
        float[][] c = {{x0, y, z0}, {x0, y, z1}, {x1, y, z1}, {x1, y, z0}};
        float[][] nrm = new float[4][];
        float[][] uv = new float[4][];
        for (int i = 0; i < 4; i++) { nrm[i] = new float[]{0, up ? 1 : -1, 0}; uv[i] = new float[]{c[i][0], up ? c[i][2] : 1 - c[i][2]}; }
        fill(q, c, nrm, uv);
        orient(q, new float[]{0, up ? 1 : -1, 0});
        return q;
    }

    /** A corner fan triangle at height y, wound to face up or down, its repeated corner last (centre, a, b, b or centre, b, a, a). */
    private static Quad fan(float[] centre, float[] a, float[] b, float y, boolean up) {
        var q = new Quad();
        q.kind = TreeShape.WOOD; q.sprite = END;
        // (a - c) x (b - c), y component: positive faces up
        float ny = (a[1] - centre[1]) * (b[0] - centre[0]) - (a[0] - centre[0]) * (b[1] - centre[1]);
        float[] p = a, r = b;
        if ((ny > 0) != up) { p = b; r = a; }
        float[][] c = {{centre[0], y, centre[1]}, {p[0], y, p[1]}, {r[0], y, r[1]}, {r[0], y, r[1]}};
        float[][] nrm = new float[4][];
        float[][] uv = new float[4][];
        for (int i = 0; i < 4; i++) { nrm[i] = new float[]{0, up ? 1 : -1, 0}; uv[i] = new float[]{c[i][0], up ? c[i][2] : 1 - c[i][2]}; }
        fill(q, c, nrm, uv);
        return q;
    }

    private static void fill(Quad q, float[][] c, float[][] nrm, float[][] uv) {
        for (int i = 0; i < 4; i++) {
            q.p[i * 3] = c[i][0]; q.p[i * 3 + 1] = c[i][1]; q.p[i * 3 + 2] = c[i][2];
            q.n[i * 3] = nrm[i][0]; q.n[i * 3 + 1] = nrm[i][1]; q.n[i * 3 + 2] = nrm[i][2];
            q.uv[i * 2] = uv[i][0]; q.uv[i * 2 + 1] = uv[i][1];
            q.ao[i] = 1;
        }
    }

    /** Reverses the corner order when the quad faces away from the given outward direction (front: counter-clockwise). */
    static void orient(Quad q, float[] out) {
        float[] e1 = {q.p[3] - q.p[0], q.p[4] - q.p[1], q.p[5] - q.p[2]}, e2 = {q.p[6] - q.p[0], q.p[7] - q.p[1], q.p[8] - q.p[2]};
        float[] n = TreeShape.cross(e1, e2);
        if (n[0] * out[0] + n[1] * out[1] + n[2] * out[2] >= 0) return;
        swap(q, 1, 3);
    }

    private static void swap(Quad q, int i, int j) {
        for (int k = 0; k < 3; k++) { float t = q.p[i * 3 + k]; q.p[i * 3 + k] = q.p[j * 3 + k]; q.p[j * 3 + k] = t;
            t = q.n[i * 3 + k]; q.n[i * 3 + k] = q.n[j * 3 + k]; q.n[j * 3 + k] = t; }
        for (int k = 0; k < 2; k++) { float t = q.uv[i * 2 + k]; q.uv[i * 2 + k] = q.uv[j * 2 + k]; q.uv[j * 2 + k] = t; }
        float t = q.ao[i]; q.ao[i] = q.ao[j]; q.ao[j] = t;
    }

    // ------------------------------------------------------------------------------------- vanilla corner order

    /**
     * Vanilla's FaceInfo corner order per direction (DOWN, UP, NORTH, SOUTH, WEST, EAST): per corner, each axis at its
     * min (0), max (1) or the face's own plane (-1). Smooth lighting reads a face quad's corners in this order (a
     * different order lights the wrong corners: checkerboard shading).
     */
    static final int[][][] FACE_INFO = {
            {{0, -1, 1}, {0, -1, 0}, {1, -1, 0}, {1, -1, 1}},
            {{0, -1, 0}, {0, -1, 1}, {1, -1, 1}, {1, -1, 0}},
            {{1, 1, -1}, {1, 0, -1}, {0, 0, -1}, {0, 1, -1}},
            {{0, 1, -1}, {0, 0, -1}, {1, 0, -1}, {1, 1, -1}},
            {{-1, 1, 0}, {-1, 0, 0}, {-1, 0, 1}, {-1, 1, 1}},
            {{-1, 1, 1}, {-1, 0, 1}, {-1, 0, 0}, {-1, 1, 0}}};

    /** Every culled rectangle (four distinct corners on its face) put into vanilla's corner order; fans and curves as they are. */
    public static List<Part> vanillaOrder(List<Part> parts) {
        var out = new ArrayList<Part>(parts.size());
        for (var part : parts) {
            var q = part.quad();
            if (part.cull() == NONE || !rectangle(q)) { out.add(part); continue; }
            float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int c = 0; c < 4; c++) for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], q.p[c * 3 + k]); hi[k] = Math.max(hi[k], q.p[c * 3 + k]); }
            var t = new Quad();
            t.kind = q.kind; t.sprite = q.sprite; t.tint = q.tint; t.twoSided = q.twoSided;
            boolean ok = true;
            for (int c = 0; c < 4 && ok; c++) {
                int[] want = FACE_INFO[part.cull()][c];
                int found = -1;
                for (int v = 0; v < 4 && found < 0; v++) {
                    boolean match = true;
                    for (int k = 0; k < 3; k++) if (want[k] >= 0 && Math.abs(q.p[v * 3 + k] - (want[k] == 1 ? hi[k] : lo[k])) > 1e-5F) match = false;
                    if (match) found = v;
                }
                if (found < 0) { ok = false; break; }
                for (int k = 0; k < 3; k++) { t.p[c * 3 + k] = q.p[found * 3 + k]; t.n[c * 3 + k] = q.n[found * 3 + k]; }
                t.uv[c * 2] = q.uv[found * 2]; t.uv[c * 2 + 1] = q.uv[found * 2 + 1]; t.ao[c] = q.ao[found];
            }
            out.add(ok ? new Part(t, part.cull(), part.corner()) : part);
        }
        return out;
    }

    static boolean rectangle(Quad q) {
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++)
            if (Math.abs(q.p[i * 3] - q.p[j * 3]) + Math.abs(q.p[i * 3 + 1] - q.p[j * 3 + 1]) + Math.abs(q.p[i * 3 + 2] - q.p[j * 3 + 2]) < 1e-6F) return false;
        return true;
    }

    // ------------------------------------------------------------------------------------------------- axes

    /** Turns a +Y log's parts to an axis (0 X, 1 Y, 2 Z): X maps (x, y, z) to (y, 1 - x, z), Z to (x, 1 - z, y). */
    public static List<Part> axis(List<Part> y, int axis) {
        if (axis == 1) return y;
        var out = new ArrayList<Part>(y.size());
        for (var part : y) {
            var q = part.quad();
            var t = new Quad();
            t.kind = q.kind; t.sprite = q.sprite; t.tint = q.tint; t.twoSided = q.twoSided;
            for (int c = 0; c < 4; c++) {
                float x = q.p[c * 3], yy = q.p[c * 3 + 1], z = q.p[c * 3 + 2], nx = q.n[c * 3], ny = q.n[c * 3 + 1], nz = q.n[c * 3 + 2];
                if (axis == 0) { t.p[c * 3] = yy; t.p[c * 3 + 1] = 1 - x; t.p[c * 3 + 2] = z; t.n[c * 3] = ny; t.n[c * 3 + 1] = -nx; t.n[c * 3 + 2] = nz; }
                else { t.p[c * 3] = x; t.p[c * 3 + 1] = 1 - z; t.p[c * 3 + 2] = yy; t.n[c * 3] = nx; t.n[c * 3 + 1] = -nz; t.n[c * 3 + 2] = ny; }
                t.uv[c * 2] = q.uv[c * 2]; t.uv[c * 2 + 1] = q.uv[c * 2 + 1]; t.ao[c] = q.ao[c];
            }
            out.add(new Part(t, turnCull(part.cull(), axis), part.corner()));
        }
        return out;
    }

    /** A cull direction turned with the log (X: up -> east, down -> west, east -> down, west -> up; Z: up -> south, down -> north, south -> down, north -> up). */
    static int turnCull(int cull, int axis) {
        if (cull == NONE || axis == 1) return cull;
        if (axis == 0) return switch (cull) { case UP -> EAST; case DOWN -> WEST; case EAST -> DOWN; case WEST -> UP; default -> cull; };
        return switch (cull) { case UP -> SOUTH; case DOWN -> NORTH; case SOUTH -> DOWN; case NORTH -> UP; default -> cull; };
    }
}
