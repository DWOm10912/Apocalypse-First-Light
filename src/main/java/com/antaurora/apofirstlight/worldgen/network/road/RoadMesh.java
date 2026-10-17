package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The pavement mesh of a built road for one chunk (C3.2, C3.3a; docs/worldgen/federal_highways_c3.md, 10 and 11): the
 * accepted R4 sample's mesh (dev R4Mesh) on a RoadDesign of any CrossSection, cut to the chunk's 16 x 16 square. Pure
 * Java; the client draws it.
 * <ul>
 *   <li>at a T junction (C3.3b, Junction; the pad itself: JunctionMesh) the host has stations at the fillets' tangent
 *   points T1 and between them, on the side the minor joins, no edge line and no side bevel (the pad meets its paved
 *   edge); the minor has no end bevel at the end that joins;</li>
 *   <li>"asphalt": both carriageways (R2: both halves) and their shoulders, the 30-degree safety edges along the sides
 *   and the ends; texture period 4 m both ways, the lateral period measured outward from the median edge (R2: the
 *   centreline);</li>
 *   <li>R4 markings: "yellow" inner edge line, "white" outer edge line and the 3 m / 12 m lane line, solid through a
 *   tunnel and RoadDesign.NO_PASS_LEAD before and after it;</li>
 *   <li>R2 markings (C3.3a): "white" edge lines, and the "yellow" centre line: a 3 m / 12 m broken line where passing is
 *   allowed, two solid lines 0.10 m apart in the no-passing zones (RoadDesign.noPassing: curves, crests, bridges, the
 *   ends);</li>
 *   <li>every line 0.15 m wide and coplanar with the asphalt (the renderer offsets them in depth), split on the crown
 *   and shoulder breaks;</li>
 *   <li>interchange ramps (C3.3b group 2, CrossSection.RAMP, RoadWorks.RampMarks): no centre line, the edge line toward
 *   the interstate "yellow" (white along the gore, between the painted and the physical nose), the other "white"; where
 *   the ramp lies on its interstate only what is beyond the interstate's paved edge is drawn (its lines beyond the
 *   interstate's edge line, the line away from it whole), no end bevel at the attached end; DOT_WIDTH "white" chevrons in
 *   the gore. The interstate's edge line beside a ramp: absent where the ramp's own edge line takes over (RampMarks gap),
 *   a DOT_WIDTH dotted line (DOT / DOT_PERIOD) along the deceleration taper and the acceleration lane (RampMarks dot).</li>
 * </ul>
 * Stations come from one global lattice (2 m, plus the dash ends), the same in every chunk; each triangle is generated
 * once in world space and clipped to the chunk, so neighbouring chunks meet without gaps or overlaps whatever the load
 * order. Coordinates are relative to the chunk's origin (x, z), heights absolute. Bridges: BridgeMesh (CONCRETE).
 */
public final class RoadMesh {
    public static final double STEP = 2, TEXTURE = 4, LINE = 0.15, DASH = 3, DASH_PERIOD = 12;
    /** R2: the double centre line's lines start this far from the centreline (0.10 m between them). */
    public static final double DOUBLE_GAP = 0.05;
    /**
     * C3.3b group 2 (MUTCD 3B.04 and 3B.24): the wide dotted line beside a ramp, its dots and their period, width (8 in);
     * the gore's chevrons (12 in across, at 45 degrees, their apex toward the painted nose), their spacing, the gore's
     * width the first one needs, the pieces an arm is cut into (it follows the pavement), m.
     */
    public static final double DOT = 0.9, DOT_PERIOD = 3.6, DOT_WIDTH = 0.2, CHEVRON = 0.3, CHEVRON_SPACING = 10, CHEVRON_FROM = 1.2,
            CHEVRON_PIECE = 1;
    private static final double BEVEL_UV = CrossSection.BEVEL_RUN / TEXTURE;
    private static final double[] ACROSS_R4 = makeAcross(CrossSection.R4), ACROSS_R2 = makeAcross(CrossSection.R2);

    /** The lateral breaks of one side's pavement (w outward from the median edge): the shoulders, lanes and texture tiles. */
    private static double[] makeAcross(CrossSection sec) {
        TreeSet<Double> set = new TreeSet<>(List.of(0.0, wInner(sec), wOuter(sec), wEdge(sec)));
        for (double w = TEXTURE; w < wEdge(sec) - 1e-9; w += TEXTURE) set.add(w);
        return set.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double[] across(CrossSection sec) { return sec == CrossSection.R4 ? ACROSS_R4 : sec == CrossSection.R2 ? ACROSS_R2 : makeAcross(sec); }

    private static double wInner(CrossSection sec) { return sec.inner; }

    private static double wOuter(CrossSection sec) { return sec.inner + sec.lanes * sec.lane; }

    private static double wEdge(CrossSection sec) { return sec.edge - sec.halfMedian; }

    /**
     * Drawn materials: the solid pass (asphalt, concrete, a tunnel's wall panels, its arch, steel) and the marking pass
     * (lines, a tunnel's slot drains); PART: an equipment part's own texture (TunnelParts: atlas UVs already).
     */
    public enum Material {
        ASPHALT, WHITE, YELLOW, CONCRETE, TUNNEL_PANEL, TUNNEL_ARCH, STEEL, DRAIN, PART;

        /** Drawn in the solid pass (and hit by rays, meshhit/RoadStructureHits); the others are paint on it. */
        public boolean solid() { return this != WHITE && this != YELLOW && this != DRAIN; }
    }

    public record Vertex(double x, double y, double z, double u, double v) {
        Vertex lerp(Vertex b, double t) { return new Vertex(x + (b.x - x) * t, y + (b.y - y) * t, z + (b.z - z) * t, u + (b.u - u) * t, v + (b.v - v) * t); }
    }

    /** One triangle, wound counter-clockwise seen from above, with the normal of its unclipped source triangle. */
    public record Triangle(Material material, Vertex a, Vertex b, Vertex c, double nx, double ny, double nz) {}

    private RoadMesh() {}

    /** The triangles of a road inside chunk (cx, cz), relative to (cx * 16, 0, cz * 16). Empty when the road misses it. */
    public static List<Triangle> chunk(RoadDesign d, int cx, int cz) { return chunk(d, cx, cz, List.of()); }

    /** #chunk(RoadDesign, int, int) with the T junctions the road takes part in (C3.3b). */
    public static List<Triangle> chunk(RoadDesign d, int cx, int cz, List<Junction> junctions) { return chunk(d, cx, cz, junctions, List.of()); }

    /**
     * #chunk(RoadDesign, int, int, List) with the interchange ramps it takes part in (C3.3b group 2, RoadWorks#marksOf: a
     * ramp's own marks, an interstate's ramps').
     */
    public static List<Triangle> chunk(RoadDesign d, int cx, int cz, List<Junction> junctions, List<RoadWorks.RampMarks> ramps) {
        double x0 = cx * 16, z0 = cz * 16;
        CrossSection sec = d.section;
        double[] across = across(sec);
        double wIn = wInner(sec), wOut = wOuter(sec), wEdge = wEdge(sec);
        // the station range whose pavement can reach the chunk: every point of the chunk is within 2.9 m of this grid
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 4; i++) for (int j = 0; j <= 4; j++) {
            double[] l = d.local(x0 + i * 4, z0 + j * 4, sec.edge + CrossSection.BEVEL_RUN + 4);
            if (l == null) continue;
            lo = Math.min(lo, l[0]);
            hi = Math.max(hi, l[0]);
        }
        List<Triangle> out = new ArrayList<>();
        if (lo > hi) return out;
        boolean start = lo <= 6, end = hi >= d.length - 6;
        for (Junction j : junctions)
            if (j.minor == d) {
                if (j.atEnd) end = false;
                else start = false;
            }
        // an attached ramp (C3.3b group 2): its own marks; it goes on as its interstate's pavement at the attached end
        RoadWorks.RampMarks own = null;
        for (RoadWorks.RampMarks m : ramps) if (m.ramp() == d) own = m;
        if (own != null) {
            if (own.exit()) start = false;
            else end = false;
        }
        int left = own == null ? -1 : -own.outward();
        lo = Math.max(0, lo - 6);
        hi = Math.min(d.length, hi + 6);
        List<Triangle> world = new ArrayList<>();
        // only the end bevels can reach a chunk beyond the route's ends
        Double[] st = lo < hi ? stations(d.length, lo, hi) : new Double[0];
        if (st.length > 1 && !junctions.isEmpty()) st = withMouths(st, d, junctions);
        if (st.length > 1 && own != null) st = with(st, d.attach.paint(), d.attach.nose());
        // a ramp's cuts at its stations (outward offsets where its interstate's paved edge and edge line lie; NaN: none)
        double[] cutEdge = null, cutLine = null;
        if (own != null) {
            cutEdge = new double[st.length];
            cutLine = new double[st.length];
            double v = own.host().section.lanesOut + LINE / 2 + LINE_CUT;
            for (int k = 0; k < st.length; k++) {
                cutEdge[k] = cross(own, st[k], own.host().section.edge);
                cutLine[k] = cross(own, st[k], v);
            }
        }
        double gore0 = own == null ? 0 : Math.min(d.attach.paint(), d.attach.nose()), gore1 = own == null ? 0 : Math.max(d.attach.paint(), d.attach.nose());
        for (int k = 0; k + 1 < st.length; k++) {
            double s0 = st[k], s1 = st[k + 1];
            double vCell = Math.floor((s0 + 1e-9) / TEXTURE) * TEXTURE;
            boolean dash = (s0 + 1e-9) % DASH_PERIOD < DASH;
            boolean noPass = !sec.divided && !sec.oneWay && d.noPassing(s0);
            double ce0 = cutEdge == null ? Double.NaN : cutEdge[k], ce1 = cutEdge == null ? Double.NaN : cutEdge[k + 1];
            for (int side : new int[]{-1, 1}) {
                // a ramp's offsets times this side's sign are outward offsets (cut: kept where at least the cut)
                int o = own == null ? 0 : side * own.outward();
                for (int i = 0; i + 1 < across.length; i++) {
                    double w0 = across[i], w1 = across[i + 1];
                    double uCell = Math.floor((w0 + 1e-9) / TEXTURE) * TEXTURE;
                    if (own == null) patch(world, d, Material.ASPHALT, side, s0, s1, w0, w1, (w0 - uCell) / TEXTURE, (w1 - uCell) / TEXTURE, vCell);
                    else cut(world, d, Material.ASPHALT, side, o, s0, s1, w0, w1, (w0 - uCell) / TEXTURE, (w1 - uCell) / TEXTURE, 0, 0, vCell, ce0, ce1);
                }
                if (sec.oneWay) {
                    // a ramp's edge lines (below); no centre line
                } else if (sec.divided) {
                    line(world, d, Material.YELLOW, side, s0, s1, wIn, vCell);
                } else if (noPass) {
                    // one of the two solid centre lines, on this side of the centreline
                    patch(world, d, Material.YELLOW, side, s0, s1, DOUBLE_GAP, DOUBLE_GAP + LINE, 0, 1, vCell);
                } else if (dash) {
                    // this side's half of the broken centre line
                    patch(world, d, Material.YELLOW, side, s0, s1, 0, LINE / 2, 0.5, 1, vCell);
                }
                boolean mouth = mouth(d, junctions, side, s0, s1);
                if (sec.oneWay && side == left) {
                    // toward the interstate: yellow, white along the gore; on the interstate only beyond its edge line
                    Material m = own != null && s0 >= gore0 - 1e-6 && s1 <= gore1 + 1e-6 ? Material.WHITE : Material.YELLOW;
                    if (own == null) line(world, d, m, side, s0, s1, wOut, vCell);
                    else {
                        cut(world, d, m, side, o, s0, s1, wOut - LINE / 2, wOut, 0, 0.5, 0, 0, vCell, cutLine[k], cutLine[k + 1]);
                        cut(world, d, m, side, o, s0, s1, wOut, wOut + LINE / 2, 0.5, 1, 0, 0, vCell, cutLine[k], cutLine[k + 1]);
                    }
                } else if (!mouth) edgeLine(world, d, ramps, side, s0, s1, wOut, vCell);
                // solid lane lines in a tunnel and its lead (no lane changes, Tunnel V1)
                if (dash || d.solidLanes(s0)) for (int q = 1; q < sec.lanes; q++) line(world, d, Material.WHITE, side, s0, s1, wIn + q * sec.lane, vCell);
                if (!mouth) {
                    if (own == null) bevel(world, d, side, s0, s1, wEdge, wEdge + CrossSection.BEVEL_RUN, vCell);
                    else cut(world, d, Material.ASPHALT, side, o, s0, s1, wEdge, wEdge + CrossSection.BEVEL_RUN, 0, BEVEL_UV, 0, CrossSection.BEVEL_DROP,
                            vCell, ce0, ce1);
                }
                if (sec.divided) bevel(world, d, side, s0, s1, 0, -CrossSection.BEVEL_RUN, vCell);
            }
        }
        // beside the interstate's ramps: the dotted edge line; in a ramp's gore: the chevrons
        for (RoadWorks.RampMarks m : ramps) {
            if (m.host() == d) dots(world, d, m, lo, hi, wOut);
            if (m == own) chevrons(world, m, lo, hi);
        }
        for (int side : new int[]{-1, 1}) {
            if (start) endBevel(world, d, side, 0, -CrossSection.BEVEL_RUN);
            if (end) endBevel(world, d, side, d.length, d.length + CrossSection.BEVEL_RUN);
        }
        for (Triangle t : world) clipInto(out, t, x0, z0);
        return out;
    }

    // ------------------------------------------------------------------ interchange ramps (C3.3b group 2)

    /** A ramp's line is cut this far beyond its interstate's edge line's outer edge (the two never overlap), m. */
    private static final double LINE_CUT = 0.025;

    /** The stations with extra ones inside their range (the same in every chunk). */
    private static Double[] with(Double[] st, double... extra) {
        TreeSet<Double> set = null;
        for (double t : extra) {
            if (t <= st[0] + 1e-6 || t >= st[st.length - 1] - 1e-6) continue;
            if (set == null) set = new TreeSet<>(List.of(st));
            Double f = set.floor(t), c = set.ceiling(t);
            if (f != null && t - f < 1e-6 || c != null && c - t < 1e-6) continue;
            set.add(t);
        }
        return set == null ? st : set.toArray(new Double[0]);
    }

    /**
     * The outward offset (the ramp's lateral times RampMarks#outward) at the ramp's station s where its interstate's |u| is
     * v (secant steps along the ramp's normal; the two nearly parallel); NaN off the interstate.
     */
    static double cross(RoadWorks.RampMarks m, double s, double v) {
        double a = 0, fa = beside(m, s, a);
        if (Double.isNaN(fa)) return Double.NaN;
        double b = a - fa, fb = beside(m, s, b);
        for (int i = 0; i < 8 && !Double.isNaN(fb) && Math.abs(fb - v) > 1e-5; i++) {
            if (Math.abs(fb - fa) < 1e-12) break;
            double c = b - (fb - v) * (b - a) / (fb - fa);
            a = b;
            fa = fb;
            b = c;
            fb = beside(m, s, b);
        }
        return Double.isNaN(fb) ? Double.NaN : b;
    }

    /** The interstate's |u| at the ramp's station s, outward offset o; NaN off it. */
    private static double beside(RoadWorks.RampMarks m, double s, double o) {
        double[] p = m.ramp().point(s, m.outward() * o), l = m.host().local(p[0], p[1], RoadWorks.REACH);
        return l == null ? Double.NaN : Math.abs(l[1]);
    }

    /**
     * #patch on a ramp, kept where the outward offset (o times w; o: this side's sign times RampMarks#outward) is at least
     * the cut, linear from c0 at s0 to c1 at s1 (NaN: no cut); the corners dropped drop0 at w0 to drop1 at w1 (a bevel).
     */
    private static void cut(List<Triangle> out, RoadDesign d, Material m, int side, int o, double s0, double s1, double w0, double w1,
                            double u0, double u1, double drop0, double drop1, double vCell, double c0, double c1) {
        double[][] q = {{s0, w0}, {s0, w1}, {s1, w1}, {s1, w0}};
        List<double[]> p = new ArrayList<>(6);
        if (Double.isNaN(c0) || Double.isNaN(c1)) p.addAll(List.of(q));
        else {
            double[] f = new double[4];
            boolean all = true, none = true;
            for (int i = 0; i < 4; i++) {
                f[i] = o * q[i][1] - (q[i][0] == s0 ? c0 : c1);
                all &= f[i] >= 0;
                none &= f[i] <= 0;
            }
            if (none) return;
            if (all) p.addAll(List.of(q));
            else
                for (int i = 0; i < 4; i++) {
                    int j = (i + 1) % 4;
                    if (f[i] >= 0) p.add(q[i]);
                    if (f[i] >= 0 != f[j] >= 0) {
                        double t = f[i] / (f[i] - f[j]);
                        p.add(new double[]{q[i][0] + (q[j][0] - q[i][0]) * t, q[i][1] + (q[j][1] - q[i][1]) * t});
                    }
                }
        }
        if (p.size() < 3) return;
        List<Vertex> v = new ArrayList<>(p.size());
        for (double[] c : p) {
            double t = (c[1] - w0) / (w1 - w0);
            v.add(vertex(d, side, c[0], c[1], u0 + (u1 - u0) * t, tv(c[0], vCell), drop0 + (drop1 - drop0) * t));
        }
        for (int i = 1; i + 1 < v.size(); i++) tri(out, m, v.get(0), v.get(i), v.get(i + 1));
    }

    /**
     * The edge line on one side over [s0, s1]: whole, but beside an interstate's ramp only outside its gap and its dotted
     * stretch (RoadWorks.RampMarks).
     */
    private static void edgeLine(List<Triangle> out, RoadDesign d, List<RoadWorks.RampMarks> ramps, int side, double s0, double s1, double w, double vCell) {
        List<double[]> keep = new ArrayList<>(List.of(new double[]{s0, s1}));
        for (RoadWorks.RampMarks m : ramps) {
            if (m.host() != d || m.side() != side) continue;
            for (double[] x : new double[][]{{m.gap0(), m.gap1()}, {m.dot0(), m.dot1()}}) {
                List<double[]> next = new ArrayList<>(keep.size() + 1);
                for (double[] r : keep) {
                    if (x[1] <= r[0] || x[0] >= r[1]) { next.add(r); continue; }
                    if (x[0] > r[0]) next.add(new double[]{r[0], x[0]});
                    if (x[1] < r[1]) next.add(new double[]{x[1], r[1]});
                }
                keep = next;
            }
        }
        for (double[] r : keep) if (r[1] - r[0] > 1e-6) line(out, d, Material.WHITE, side, r[0], r[1], w, vCell);
    }

    /** The interstate's dotted edge line beside a ramp (RampMarks dot0 .. dot1), the dots within [lo, hi]. */
    private static void dots(List<Triangle> out, RoadDesign d, RoadWorks.RampMarks m, double lo, double hi, double w) {
        for (double a = m.dot0(); a < m.dot1() - 1e-6; a += DOT_PERIOD) {
            double b = Math.min(a + DOT, m.dot1());
            if (b < lo || a > hi) continue;
            double vCell = Math.floor((a + 1e-9) / TEXTURE) * TEXTURE;
            patch(out, d, Material.WHITE, m.side(), a, b, w - DOT_WIDTH / 2, w + DOT_WIDTH / 2, 0, 1, vCell);
        }
    }

    /**
     * The gore's chevrons on a ramp (between its painted and its physical nose): within the gore between the interstate's
     * edge line and the ramp's edge line toward it, each a V of two 45-degree arms, its apex on the gore's middle toward
     * the painted nose, every CHEVRON_SPACING from where the gore is CHEVRON_FROM wide; the arms on the pavement (the
     * interstate's where they lie on it, else the ramp's). Only those reaching [lo, hi].
     */
    private static void chevrons(List<Triangle> out, RoadWorks.RampMarks m, double lo, double hi) {
        RoadDesign d = m.ramp();
        double paint = d.attach.paint(), nose = d.attach.nose(), g = nose > paint ? 1 : -1;
        double inner = -(d.section.lanesOut + LINE / 2), v = m.host().section.lanesOut + LINE / 2, along = CHEVRON * Math.sqrt(2);
        double first = Double.NaN;
        for (double q = 0; q <= Math.abs(nose - paint); q += 0.5) {
            double c = cross(m, paint + g * q, v);
            if (!Double.isNaN(c) && inner - c >= CHEVRON_FROM) { first = q; break; }
        }
        if (Double.isNaN(first)) return;
        for (double q = first; q <= Math.abs(nose - paint); q += CHEVRON_SPACING) {
            double sk = paint + g * q, c = cross(m, sk, v);
            if (Double.isNaN(c)) continue;
            double mid = (c + inner) / 2, half = (inner - c) / 2;
            // the farthest reach of the arms along the ramp: within the gore and the chunk's range
            double far = sk + g * (along + half * 1.2);
            if (g * (far - nose) > 0) break;
            if (Math.max(sk, far) < lo || Math.min(sk, far) > hi) continue;
            for (int k : new int[]{-1, 1}) {
                // the arm toward the interstate (k -1) or the ramp's line (k 1): its two edges from the apex at 45 degrees
                double[] lead = armEnd(m, sk, mid, g, k, inner, v), trail = armEnd(m, sk + g * along, mid, g, k, inner, v);
                if (lead == null || trail == null) continue;
                arm(out, m, new double[]{sk, mid}, new double[]{sk + g * along, mid}, trail, lead);
            }
        }
    }

    /**
     * Where an arm's edge from (s, o) at 45 degrees (along g, across k) meets the gore's side: the ramp's line (k 1, its
     * outward offset inner) or the interstate's line (k -1, where its |u| is v); null off the interstate.
     */
    private static double[] armEnd(RoadWorks.RampMarks m, double s, double o, double g, int k, double inner, double v) {
        if (k > 0) { double t = inner - o; return new double[]{s + g * t, inner}; }
        double t = 0;
        for (int i = 0; i < 6; i++) {
            double c = cross(m, s + g * t, v);
            if (Double.isNaN(c)) return null;
            t = o - c;
        }
        return new double[]{s + g * t, o - t};
    }

    /** One chevron arm (the quad a, b, c, e in the ramp's station and outward offset) on the pavement, in CHEVRON_PIECE pieces. */
    private static void arm(List<Triangle> out, RoadWorks.RampMarks m, double[] a, double[] b, double[] c, double[] e) {
        double len = Math.max(Math.hypot(c[0] - b[0], c[1] - b[1]), Math.hypot(e[0] - a[0], e[1] - a[1]));
        int n = Math.max(1, (int) Math.ceil(len / CHEVRON_PIECE));
        Vertex pa = null, pb = null;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n, tv = Math.min(1, len * t / TEXTURE);
            Vertex qa = gore(m, a[0] + (e[0] - a[0]) * t, a[1] + (e[1] - a[1]) * t, 0, tv),
                    qb = gore(m, b[0] + (c[0] - b[0]) * t, b[1] + (c[1] - b[1]) * t, 1, tv);
            if (pa != null) {
                tri(out, Material.WHITE, pa, pb, qb);
                tri(out, Material.WHITE, pa, qb, qa);
            }
            pa = qa;
            pb = qb;
        }
    }

    /** A point of the gore (the ramp's station s, outward offset o) on the pavement: the interstate's on its paved band, else the ramp's. */
    private static Vertex gore(RoadWorks.RampMarks m, double s, double o, double tu, double tv) {
        RoadDesign d = m.ramp(), h = m.host();
        double u = m.outward() * o;
        double[] p = d.point(s, u), l = h.local(p[0], p[1], RoadWorks.REACH);
        double y = l != null && Math.abs(l[1]) <= h.section.edge && l[0] >= 0 && l[0] <= h.length ? h.pavementY(l[0], l[1])
                : d.pavementY(s, Math.max(-d.section.edge, Math.min(d.section.edge, u)));
        return new Vertex(p[0], y, p[1], tu, tv);
    }

    /** The global station lattice restricted to [lo, hi] (whole intervals): 2 m steps, the end, the dash ends. */
    static Double[] stations(double length, double lo, double hi) {
        TreeSet<Double> set = new TreeSet<>();
        double a = Math.max(0, Math.floor(lo / STEP) * STEP), b = Math.min(length, Math.ceil(hi / STEP) * STEP);
        for (double s = a; s <= b + 1e-9 && s < length - 1e-9; s += STEP) set.add(s);
        if (b >= length - 1e-9) set.add(length);
        else set.add(b);
        for (double s = Math.floor(a / DASH_PERIOD) * DASH_PERIOD; s <= b; s += DASH_PERIOD)
            if (s + DASH < length && s + DASH >= a && s + DASH <= b) set.add(s + DASH);
        return set.toArray(new Double[0]);
    }

    /** The stations with the T1 of every junction the road hosts inside their range (the same in every chunk). */
    private static Double[] withMouths(Double[] st, RoadDesign d, List<Junction> junctions) {
        TreeSet<Double> set = null;
        for (Junction j : junctions) {
            if (j.host != d) continue;
            for (int k = 0; k < 2; k++) {
                double t = j.t1(k);
                if (t <= st[0] + 1e-6 || t >= st[st.length - 1] - 1e-6) continue;
                if (set == null) set = new TreeSet<>(List.of(st));
                Double f = set.floor(t), c = set.ceiling(t);
                if (f != null && t - f < 1e-6 || c != null && c - t < 1e-6) continue;
                set.add(t);
            }
        }
        return set == null ? st : set.toArray(new Double[0]);
    }

    /** Whether [s0, s1] on this side of a host lies in the mouth of a junction (between its two T1). */
    private static boolean mouth(RoadDesign d, List<Junction> junctions, int side, double s0, double s1) {
        for (Junction j : junctions)
            if (j.host == d && j.side == side && s0 >= j.mouthFrom() - 0.01 && s1 <= j.mouthTo() + 0.01) return true;
        return false;
    }

    private static double lateral(RoadDesign d, int side, double w) { return side * (d.section.halfMedian + w); }

    private static Vertex vertex(RoadDesign d, int side, double s, double w, double tu, double tv, double drop) {
        double[] p = d.point(s, lateral(d, side, w));
        double y = d.pavementY(s, lateral(d, side, Math.max(0, Math.min(wEdge(d.section), w)))) - drop;
        return new Vertex(p[0], y, p[1], tu, tv);
    }

    private static double tv(double s, double vCell) { return Math.max(0, Math.min(1, (s - vCell) / TEXTURE)); }

    private static void patch(List<Triangle> out, RoadDesign d, Material m, int side, double s0, double s1, double w0, double w1,
                              double u0, double u1, double vCell) {
        Vertex a = vertex(d, side, s0, w0, u0, tv(s0, vCell), 0), b = vertex(d, side, s0, w1, u1, tv(s0, vCell), 0),
                c = vertex(d, side, s1, w1, u1, tv(s1, vCell), 0), e = vertex(d, side, s1, w0, u0, tv(s1, vCell), 0);
        quad(out, m, a, b, c, e);
    }

    private static void line(List<Triangle> out, RoadDesign d, Material m, int side, double s0, double s1, double w, double vCell) {
        patch(out, d, m, side, s0, s1, w - LINE / 2, w, 0, 0.5, vCell);
        patch(out, d, m, side, s0, s1, w, w + LINE / 2, 0.5, 1, vCell);
    }

    private static void bevel(List<Triangle> out, RoadDesign d, int side, double s0, double s1, double wTop, double wBottom, double vCell) {
        Vertex a = vertex(d, side, s0, wTop, 0, tv(s0, vCell), 0), b = vertex(d, side, s0, wBottom, BEVEL_UV, tv(s0, vCell), CrossSection.BEVEL_DROP),
                c = vertex(d, side, s1, wBottom, BEVEL_UV, tv(s1, vCell), CrossSection.BEVEL_DROP), e = vertex(d, side, s1, wTop, 0, tv(s1, vCell), 0);
        quad(out, Material.ASPHALT, a, b, c, e);
    }

    private static void endBevel(List<Triangle> out, RoadDesign d, int side, double sTop, double sBottom) {
        double[] across = across(d.section);
        double wEdge = wEdge(d.section);
        for (int i = 0; i + 1 < across.length; i++) {
            double w0 = across[i], w1 = across[i + 1], uCell = Math.floor((w0 + 1e-9) / TEXTURE) * TEXTURE;
            double u0 = (w0 - uCell) / TEXTURE, u1 = (w1 - uCell) / TEXTURE;
            quad(out, Material.ASPHALT, endVertex(d, side, sTop, sBottom, w0, u0, false), endVertex(d, side, sTop, sBottom, w1, u1, false),
                    endVertex(d, side, sTop, sBottom, w1, u1, true), endVertex(d, side, sTop, sBottom, w0, u0, true));
        }
        double[][] corners = d.section.divided ? new double[][]{{0, -CrossSection.BEVEL_RUN}, {wEdge, wEdge + CrossSection.BEVEL_RUN}}
                : new double[][]{{wEdge, wEdge + CrossSection.BEVEL_RUN}};
        for (double[] corner : corners) {
            Vertex a = endVertex(d, side, sTop, sBottom, corner[0], 0, false), e = endVertex(d, side, sTop, sBottom, corner[0], 0, true);
            Vertex b = vertex(d, side, sTop, corner[1], BEVEL_UV, 0, CrossSection.BEVEL_DROP);
            tri(out, Material.ASPHALT, a, b, e);
        }
    }

    private static Vertex endVertex(RoadDesign d, int side, double sTop, double sBottom, double w, double tu, boolean bottom) {
        double u = lateral(d, side, w);
        double[] p = d.point(bottom ? sBottom : sTop, u);
        double y = d.pavementY(sTop, lateral(d, side, Math.max(0, Math.min(wEdge(d.section), w)))) - (bottom ? CrossSection.BEVEL_DROP : 0);
        return new Vertex(p[0], y, p[1], tu, bottom ? BEVEL_UV : 0);
    }

    private static void quad(List<Triangle> out, Material m, Vertex a, Vertex b, Vertex c, Vertex e) {
        tri(out, m, a, b, c);
        tri(out, m, a, c, e);
    }

    /** Adds the triangle wound so its normal points up, with that normal; degenerate ones (in plan) are dropped. */
    private static void tri(List<Triangle> out, Material m, Vertex a, Vertex b, Vertex c) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        if (Math.abs(ux * vz - uz * vx) < 1e-10) return;
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (ny < 0) { Vertex t = b; b = c; c = t; nx = -nx; ny = -ny; nz = -nz; }
        out.add(new Triangle(m, a, b, c, nx / n, ny / n, nz / n));
    }

    // ------------------------------------------------------------------ clipping

    /** Clip a world-space triangle to [x0, x0 + 16] x [z0, z0 + 16] and add the pieces relative to (x0, z0). */
    public static void clipInto(List<Triangle> out, Triangle t, double x0, double z0) {
        double minX = Math.min(t.a().x(), Math.min(t.b().x(), t.c().x())), maxX = Math.max(t.a().x(), Math.max(t.b().x(), t.c().x()));
        double minZ = Math.min(t.a().z(), Math.min(t.b().z(), t.c().z())), maxZ = Math.max(t.a().z(), Math.max(t.b().z(), t.c().z()));
        // half-open ownership: a face lying in a chunk's boundary plane (a vertical bridge face) belongs to the chunk on its + side
        if (maxX - minX < 1e-9 ? minX < x0 || minX >= x0 + 16 : maxX <= x0 || minX >= x0 + 16) return;
        if (maxZ - minZ < 1e-9 ? minZ < z0 || minZ >= z0 + 16 : maxZ <= z0 || minZ >= z0 + 16) return;
        List<Vertex> p = List.of(t.a(), t.b(), t.c());
        p = plane(p, true, x0, true);
        p = plane(p, true, x0 + 16, false);
        p = plane(p, false, z0, true);
        p = plane(p, false, z0 + 16, false);
        for (int i = 1; i + 1 < p.size(); i++) {
            Vertex a = rel(p.get(0), x0, z0), b = rel(p.get(i), x0, z0), c = rel(p.get(i + 1), x0, z0);
            if (area3(a, b, c) <= 1e-9) continue;
            out.add(new Triangle(t.material(), a, b, c, t.nx(), t.ny(), t.nz()));
        }
    }

    /**
     * A chunk's triangles (chunk-relative) without their parts over the removed block cells (removed[lx | lz << 4]):
     * a triangle touching one is cut into its per-cell pieces and those over removed cells are dropped (a vertical face in
     * a cell boundary belongs to the cell on its + side).
     */
    public static List<Triangle> without(List<Triangle> tris, boolean[] removed) { return without(tris, removed, null, null); }

    /**
     * #without, a removed cell i only over its height range lo[i] .. hi[i] (a piece reaching into it is dropped; C3.3b
     * group 2: an overpass's deck and the road under it are cut apart); lo / hi null: the whole column.
     */
    public static List<Triangle> without(List<Triangle> tris, boolean[] removed, double[] lo, double[] hi) {
        List<Triangle> out = new ArrayList<>(tris.size());
        for (Triangle t : tris) {
            int x0 = cell(Math.min(t.a().x(), Math.min(t.b().x(), t.c().x()))), x1 = Math.max(x0, cell(Math.max(t.a().x(), Math.max(t.b().x(), t.c().x())) - 1e-9));
            int z0 = cell(Math.min(t.a().z(), Math.min(t.b().z(), t.c().z()))), z1 = Math.max(z0, cell(Math.max(t.a().z(), Math.max(t.b().z(), t.c().z())) - 1e-9));
            double y0 = Math.min(t.a().y(), Math.min(t.b().y(), t.c().y())), y1 = Math.max(t.a().y(), Math.max(t.b().y(), t.c().y()));
            boolean touches = false;
            for (int z = z0; z <= z1 && !touches; z++) for (int x = x0; x <= x1; x++) {
                int k = x | z << 4;
                if (removed[k] && (lo == null || y1 >= lo[k] && y0 <= hi[k])) { touches = true; break; }
            }
            if (!touches) { out.add(t); continue; }
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
                int k = x | z << 4;
                if (removed[k] && lo == null) continue;
                List<Vertex> p = List.of(t.a(), t.b(), t.c());
                p = plane(p, true, x, true);
                p = plane(p, true, x + 1, false);
                p = plane(p, false, z, true);
                p = plane(p, false, z + 1, false);
                if (removed[k] && !p.isEmpty()) {
                    double py0 = Double.POSITIVE_INFINITY, py1 = Double.NEGATIVE_INFINITY;
                    for (Vertex v : p) { py0 = Math.min(py0, v.y()); py1 = Math.max(py1, v.y()); }
                    if (py1 >= lo[k] && py0 <= hi[k]) continue;
                }
                for (int i = 1; i + 1 < p.size(); i++) {
                    Vertex a = p.get(0), b = p.get(i), c = p.get(i + 1);
                    if (area3(a, b, c) <= 1e-9) continue;
                    out.add(new Triangle(t.material(), a, b, c, t.nx(), t.ny(), t.nz()));
                }
            }
        }
        return out;
    }

    /** Twice the triangle's area in space (vertical faces have none in plan but are kept). */
    static double area3(Vertex a, Vertex b, Vertex c) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        return Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    private static int cell(double v) { return Math.max(0, Math.min(15, (int) Math.floor(v))); }

    private static Vertex rel(Vertex v, double x0, double z0) { return new Vertex(v.x() - x0, v.y(), v.z() - z0, v.u(), v.v()); }

    private static List<Vertex> plane(List<Vertex> in, boolean x, double edge, boolean lower) {
        if (in.isEmpty()) return in;
        List<Vertex> out = new ArrayList<>(6);
        Vertex a = in.get(in.size() - 1);
        double da = (x ? a.x() : a.z()) - edge;
        boolean ia = lower ? da >= 0 : da <= 0;
        for (Vertex b : in) {
            double db = (x ? b.x() : b.z()) - edge;
            boolean ib = lower ? db >= 0 : db <= 0;
            if (ia != ib) {
                Vertex v = a.lerp(b, da / (da - db));
                out.add(x ? new Vertex(edge, v.y(), v.z(), v.u(), v.v()) : new Vertex(v.x(), v.y(), edge, v.u(), v.v()));
            }
            if (ib) out.add(b);
            a = b;
            da = db;
            ia = ib;
        }
        return out;
    }
}
