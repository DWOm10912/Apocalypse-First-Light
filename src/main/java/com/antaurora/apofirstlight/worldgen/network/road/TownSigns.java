package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.town.TownControl;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownStreets;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;

import java.util.ArrayList;
import java.util.List;

/**
 * The framework towns' signs (Town Framework V1, docs/worldgen/federal_towns_v1.md 7), fixtures road_guide_sign
 * (GuideSigns STREET and LIMIT), road_stop_sign and road_speed_limit_sign placed from the plans like RoadSigns (pure
 * geometry: every chunk pass places the same):
 * <ul>
 *   <li>the limit sign (the town's name over CITY LIMIT and its population, #population) on the exit crossroad, for the
 *   traffic toward the town, LIMIT_IN before the crossroad's end at its Front St corner (moved back from the town in
 *   LIMIT_STEP steps while the place is not clear, RoadGuides#beside), and SPEED_IN past it the town's speed limit
 *   (R2-1, SPEED_LIMIT mph) on the same side (further on while not clear, else none);</li>
 *   <li>the limit sign on a county road into the town (G3, TownPlan.Entry), for its traffic toward the town (the county
 *   road runs +s into it), where it crosses the limit (moved back likewise);</li>
 *   <li>at every intersection a pair of street name blades on one post: the lower names the parallel street (its faces
 *   toward the cross street) with its hundred block (T4: the numbered cross street's number x 100, 2026-10-19), the
 *   upper the cross street, turned a quarter; the post on the sidewalk CORNER_IN inside the corner of the intersection's
 *   box away from the interstate (+s first; another corner where that one is not sidewalk);</li>
 *   <li>STOP (R1-1 at the town's 7 ft mounting: bone town; with the ALL WAY plaque R1-3P under it at an all-way stop:
 *   bone all_way) on every stopped approach (TownControl, 2026-10-19), on the right of the traffic at its stop bar, in
 *   the utility strip STOP_OUT beyond the asphalt edge (further from the intersection by STOP_STEP while a driveway's
 *   apron is there, else on the sidewalk).</li>
 * </ul>
 */
final class TownSigns {
    private TownSigns() {}

    static final double LIMIT_IN = 25, LIMIT_STEP = 10, LIMIT_SHIFT = 40, CORNER_IN = 1.0;
    /** The speed limit sign past the limit sign toward the town, its further tries, m; its legend, mph. */
    static final double SPEED_IN = 8, SPEED_STEP = 6, SPEED_SHIFT = 20;
    public static final int SPEED_LIMIT = 30;
    /** A STOP sign's post beyond the asphalt edge (the curb band and half the utility strip), its steps away past a driveway, m. */
    static final double STOP_OUT = 2.0, STOP_STEP = 1, STOP_SHIFT = 6;
    /** People per lot by zone (a house, a mobile home, the flats over a downtown shop) for the limit sign's POP line. */
    static final double PER_HOUSE = 2.5, PER_MOBILE = 2.3, PER_DOWNTOWN = 1.2;

    static List<TunnelMesh.Placement> all(RoadWorks works, RoadWorks.Ground ground) {
        List<TunnelMesh.Placement> out = new ArrayList<>();
        TownWorks tw = works.towns();
        if (tw.isEmpty()) return out;
        for (TownPlan p : tw.plans()) {
            limits(works, ground, p, out);
            blades(tw, p, out);
            stops(tw, p, out);
        }
        return out;
    }

    /** The limit signs on the exit crossroad, one per Front St corner it ends at. */
    private static void limits(RoadWorks works, RoadWorks.Ground ground, TownPlan p, List<TunnelMesh.Placement> out) {
        RoadDesign x = p.exit == null ? null : works.road(p.exit);
        if (x == null) return;
        GuideSigns.Spec spec = new GuideSigns.Spec(GuideSigns.Type.LIMIT, "", "", "", List.of(p.name), "POP " + population(p), new double[]{0, 0});
        for (double[] e : p.exitEnds) {
            double target = e[1] - Math.signum(e[1]) * LIMIT_IN;
            // the crossroad's station there, and the way its traffic toward the town runs
            double best = Double.POSITIVE_INFINITY, sAt = Double.NaN;
            for (double s = 0; s <= x.length; s += 1) {
                double[] w = x.point(s, 0), l = p.local(w[0], w[1]);
                if (Math.abs(l[1] - target) < best) { best = Math.abs(l[1] - target); sAt = s; }
            }
            if (Double.isNaN(sAt) || best > 2) continue;
            double[] a = x.point(Math.max(0, sAt - 1), 0), b = x.point(Math.min(x.length, sAt + 1), 0);
            double va = p.local(a[0], a[1])[1], vb = p.local(b[0], b[1])[1];
            int k = Math.abs(vb) > Math.abs(va) ? 1 : -1;
            speed(works, ground, x, beside(works, ground, x, sAt, k, spec, out), k, out);
        }
        // the county roads into the town (G3): at the limit, for the traffic toward the town (+s)
        for (TownPlan.Entry e : p.entries) {
            RoadDesign c = works.road(e.road());
            if (c == null) continue;
            boolean par = e.dir() <= TownPlan.DIR_S_DOWN;
            double edge = switch (e.dir()) {
                case TownPlan.DIR_S_UP -> p.limit[1];
                case TownPlan.DIR_S_DOWN -> p.limit[0];
                default -> p.limit[3];
            };
            double best = Double.POSITIVE_INFINITY, sAt = Double.NaN;
            for (double s = 0; s <= c.length; s += 1) {
                double[] w = c.point(s, 0), l = p.local(w[0], w[1]);
                double d = Math.abs((par ? l[0] : l[1]) - edge);
                if (d < best) { best = d; sAt = s; }
            }
            if (Double.isNaN(sAt) || best > 2) continue;
            speed(works, ground, c, beside(works, ground, c, sAt, 1, spec, out), 1, out);
        }
    }

    /** The town's population for its limit sign: its lots by zone, to the nearest ten, at least 10 (no comma: the sign font has none). */
    static String population(TownPlan p) {
        double n = 0;
        for (TownPlan.Lot l : p.lots)
            n += switch (l.zone()) {
                case "res" -> PER_HOUSE;
                case "mobile" -> PER_MOBILE;
                case "downtown" -> PER_DOWNTOWN;
                default -> 0;
            };
        long r = Math.max(10, Math.round(n / 10) * 10);
        return Long.toString(r);
    }

    /** The speed limit sign SPEED_IN past station s (the limit sign's; NaN: none) toward the town on road x (traffic k). */
    private static void speed(RoadWorks works, RoadWorks.Ground ground, RoadDesign x, double s, int k, List<TunnelMesh.Placement> out) {
        if (Double.isNaN(s)) return;
        for (double d = SPEED_IN; d <= SPEED_SHIFT + 1e-9; d += SPEED_STEP) {
            TunnelMesh.Placement q = RoadSigns.roadside(works, ground, x, s + k * d, k, "speed_limit_sign", null);
            if (q != null) {
                out.add(q);
                return;
            }
        }
    }

    /**
     * The limit sign beside road x at station s for its traffic travelling k, moved back from the town until clear; the
     * station it stands at, NaN where none was clear.
     */
    private static double beside(RoadWorks works, RoadWorks.Ground ground, RoadDesign x, double s, int k, GuideSigns.Spec spec, List<TunnelMesh.Placement> out) {
        for (double d = 0; d <= LIMIT_SHIFT + 1e-9; d += LIMIT_STEP) {
            TunnelMesh.Placement q = RoadGuides.beside(works, ground, x, s - k * d, k, RoadGuides.SIDE_X, spec);
            if (q != null) {
                out.add(q);
                return s - k * d;
            }
        }
        return Double.NaN;
    }

    /** The street name blades of every intersection. */
    private static void blades(TownWorks tw, TownPlan p, List<TunnelMesh.Placement> out) {
        int n = p.nodes.size();
        String[] par = new String[n], cross = new String[n];
        double[] extS = new double[n], extV = new double[n];
        for (TownPlan.Street st : p.streets) {
            TownPlan.Node a = p.nodes.get(st.a()), b = p.nodes.get(st.b());
            boolean along = Math.abs(a.v() - b.v()) < 1e-6;
            double hr = TownStreets.layout(st.cls()).halfRow();
            for (int i : new int[]{st.a(), st.b()}) {
                if (along) { if (par[i] == null) par[i] = st.name(); extV[i] = Math.max(extV[i], hr); }
                else { if (cross[i] == null) cross[i] = st.name(); extS[i] = Math.max(extS[i], hr); }
            }
        }
        for (int i = 0; i < n; i++) {
            if (par[i] == null && cross[i] == null) continue;
            TownPlan.Node q = p.nodes.get(i);
            double es = extS[i] > 0 ? extS[i] : extV[i], ev = extV[i] > 0 ? extV[i] : extS[i];
            int away = q.v() >= 0 ? 1 : -1;
            double[] at = null;
            double foot = 0;
            for (int[] c : new int[][]{{1, away}, {-1, away}, {1, -away}, {-1, -away}}) {
                double s = q.s() + c[0] * (es - CORNER_IN), v = q.v() + c[1] * (ev - CORNER_IN);
                double[] w = p.world(s, v);
                TownWorks.Spot sp = tw.spot(w[0], w[1]);
                if (sp == null || sp.part() != TownWorks.Part.SIDEWALK) continue;
                at = w;
                foot = sp.h();
                break;
            }
            if (at == null) continue;
            String lower = par[i] != null ? par[i] : cross[i], upper = par[i] != null ? cross[i] : null;
            // the parallel street's hundred block: the numbered cross street's number x 100 (T4)
            String block = par[i] != null && cross[i] != null ? hundred(cross[i]) : "";
            // the lower blade along the parallel street: its faces toward v (the cross street's traffic reads it)
            double[] fl = par[i] != null ? new double[]{p.bx, p.bz} : new double[]{p.ax, p.az};
            out.add(RoadSigns.part("guide_sign", new GuideSigns.Spec(GuideSigns.Type.STREET, block, "", "", List.of(lower), "", new double[]{0, 0}).bone(),
                    at[0], foot, at[1], fl[0], fl[1]));
            if (upper != null)
                out.add(RoadSigns.part("guide_sign", new GuideSigns.Spec(GuideSigns.Type.STREET, "", "", "", List.of(upper), "top", new double[]{0, 0}).bone(),
                        at[0], foot, at[1], p.ax, p.az));
        }
    }

    /** "600" for "6th St"; "" for a cross street that is not numbered. */
    static String hundred(String cross) {
        int n = 0, i = 0;
        while (i < cross.length() && Character.isDigit(cross.charAt(i))) n = n * 10 + (cross.charAt(i++) - '0');
        return i == 0 ? "" : Integer.toString(n * 100);
    }

    /** The STOP signs of the stopped approaches (TownControl). */
    private static void stops(TownWorks tw, TownPlan p, List<TunnelMesh.Placement> out) {
        for (TownControl.Stop st : tw.control(p).stops()) {
            TownPlan.Node q = p.nodes.get(st.node());
            double[] a = TownPlan.arm(st.dir());
            boolean alongS = st.dir() <= TownPlan.DIR_S_DOWN;
            TunnelMesh.Placement placed = null;
            // the utility strip, else the sidewalk's middle beyond it
            for (TownWorks.Part want : new TownWorks.Part[]{TownWorks.Part.UTILITY, TownWorks.Part.SIDEWALK}) {
                double w = st.side() * (st.half() + STOP_OUT + (want == TownWorks.Part.SIDEWALK ? 2 : 0));
                for (double o = st.offset() + TownControl.BAR / 2; o <= st.offset() + STOP_SHIFT + 1e-9 && placed == null; o += STOP_STEP) {
                    double ps = alongS ? q.s() + a[0] * o : q.s() + w, pv = alongS ? q.v() + w : q.v() + a[1] * o;
                    double[] xz = p.world(ps, pv);
                    TownWorks.Spot sp = tw.spot(xz[0], xz[1]);
                    if (sp == null || sp.part() != want) continue;
                    placed = RoadSigns.part("stop_sign", st.allWay() ? "all_way" : "town", xz[0], sp.h(), xz[1], p.ax * a[0] + p.bx * a[1], p.az * a[0] + p.bz * a[1]);
                }
                if (placed != null) break;
            }
            if (placed != null) out.add(placed);
        }
    }
}
