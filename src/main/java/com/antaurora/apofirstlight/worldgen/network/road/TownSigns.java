package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownStreets;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;

import java.util.ArrayList;
import java.util.List;

/**
 * The framework towns' signs (Town Framework V1, docs/worldgen/federal_towns_v1.md 7), fixtures road_guide_sign
 * (GuideSigns STREET and LIMIT) placed from the plans like RoadSigns (pure geometry: every chunk pass places the same):
 * <ul>
 *   <li>the limit sign (the town's name over CITY LIMIT) on the exit crossroad, for the traffic toward the town,
 *   LIMIT_IN before the crossroad's end at its Front St corner (moved back from the town in LIMIT_STEP steps while the
 *   place is not clear, RoadGuides#beside);</li>
 *   <li>at every intersection a pair of street name blades on one post: the lower names the parallel street (its faces
 *   toward the cross street), the upper the cross street, turned a quarter; the post on the sidewalk CORNER_IN inside
 *   the corner of the intersection's box away from the interstate (+s first; another corner where that one is not
 *   sidewalk).</li>
 * </ul>
 */
final class TownSigns {
    private TownSigns() {}

    static final double LIMIT_IN = 25, LIMIT_STEP = 10, LIMIT_SHIFT = 40, CORNER_IN = 1.0;

    static List<TunnelMesh.Placement> all(RoadWorks works, RoadWorks.Ground ground) {
        List<TunnelMesh.Placement> out = new ArrayList<>();
        TownWorks tw = works.towns();
        if (tw.isEmpty()) return out;
        for (TownPlan p : tw.plans()) {
            limits(works, ground, p, out);
            blades(tw, p, out);
        }
        return out;
    }

    /** The limit signs on the exit crossroad, one per Front St corner it ends at. */
    private static void limits(RoadWorks works, RoadWorks.Ground ground, TownPlan p, List<TunnelMesh.Placement> out) {
        RoadDesign x = p.exit == null ? null : works.road(p.exit);
        if (x == null) return;
        GuideSigns.Spec spec = new GuideSigns.Spec(GuideSigns.Type.LIMIT, "", "", "", List.of(p.name), "", new double[]{0, 0});
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
            for (double d = 0; d <= LIMIT_SHIFT + 1e-9; d += LIMIT_STEP) {
                TunnelMesh.Placement q = RoadGuides.beside(works, ground, x, sAt - k * d, k, RoadGuides.SIDE_X, spec);
                if (q != null) {
                    out.add(q);
                    break;
                }
            }
        }
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
            // the lower blade along the parallel street: its faces toward v (the cross street's traffic reads it)
            double[] fl = par[i] != null ? new double[]{p.bx, p.bz} : new double[]{p.ax, p.az};
            out.add(RoadSigns.part("guide_sign", new GuideSigns.Spec(GuideSigns.Type.STREET, "", "", "", List.of(lower), "", new double[]{0, 0}).bone(),
                    at[0], foot, at[1], fl[0], fl[1]));
            if (upper != null)
                out.add(RoadSigns.part("guide_sign", new GuideSigns.Spec(GuideSigns.Type.STREET, "", "", "", List.of(upper), "top", new double[]{0, 0}).bone(),
                        at[0], foot, at[1], p.ax, p.az));
        }
    }
}
