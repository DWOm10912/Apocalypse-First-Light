package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Offline check of a framework town's works (TownCheck): the footprint sampled every STEP metres on the town's grid, its
 * parts counted, the height steps between neighbouring samples (none but the curb's at the gutter, the lots' terraces
 * apart), the crossroad's mouth against the crossroad's end, the block columns (paved, turf) and the slopes' reach; with
 * -PtownPng a picture of the finished works (parts over the finished surface's shading).
 */
final class TownWorksCheck {
    private TownWorksCheck() {}

    static final double STEP = 0.5;

    static boolean check(TownPlan p, RoadWorks works, RoadWorks.Ground ground, RouteTerrain terrain, File png) throws Exception {
        TownWorks tw = works.towns();
        boolean ok = true;
        Map<TownWorks.Part, Integer> count = new EnumMap<>(TownWorks.Part.class);
        // the worst step between neighbours on the footprint, by the pair of parts; and where
        int np = TownWorks.Part.values().length;
        double[][] worst = new double[np][np];
        double[][][] at = new double[np][np][];
        int ns = (int) Math.ceil((p.limit[1] - p.limit[0]) / STEP), nv = (int) Math.ceil((p.limit[3] - p.limit[2]) / STEP);
        TownWorks.Spot[] prevRow = new TownWorks.Spot[nv + 1];
        for (int i = 0; i <= ns; i++) {
            TownWorks.Spot left = null;
            TownWorks.Spot[] row = new TownWorks.Spot[nv + 1];
            for (int j = 0; j <= nv; j++) {
                double s = p.limit[0] + i * STEP, v = p.limit[2] + j * STEP;
                double[] w = p.world(s, v);
                TownWorks.Spot sp = tw.spot(w[0], w[1]);
                row[j] = sp;
                if (sp != null) count.merge(sp.part(), 1, Integer::sum);
                for (TownWorks.Spot o : new TownWorks.Spot[]{left, prevRow[j]}) {
                    if (sp == null || o == null) continue;
                    int a = Math.min(sp.part().ordinal(), o.part().ordinal()), b = Math.max(sp.part().ordinal(), o.part().ordinal());
                    double d = Math.abs(sp.h() - o.h());
                    if (d > worst[a][b]) { worst[a][b] = d; at[a][b] = new double[]{s, v}; }
                }
                left = sp;
            }
            prevRow = row;
        }
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "  works %s: samples", p.town));
        for (Map.Entry<TownWorks.Part, Integer> e : count.entrySet())
            sb.append(String.format(Locale.ROOT, " %s %.0f m2", e.getKey().name().toLowerCase(Locale.ROOT), e.getValue() * STEP * STEP));
        System.out.println(sb);
        // lot sizes per zone (frontage x depth, m): against the T3 table, docs/worldgen/federal_towns_v1.md 4 (2026-10-18)
        Map<String, double[]> sizes = new java.util.TreeMap<>();
        for (com.antaurora.apofirstlight.worldgen.network.town.TownPlan.Lot l : p.lots) {
            double ds = l.s1() - l.s0(), dv = l.v1() - l.v0();
            double[] m = sizes.computeIfAbsent(l.zone(), q -> new double[]{1e9, 0, 1e9, 0, 0});
            m[0] = Math.min(m[0], ds); m[1] = Math.max(m[1], ds); m[2] = Math.min(m[2], dv); m[3] = Math.max(m[3], dv); m[4]++;
        }
        StringBuilder ls = new StringBuilder(String.format(Locale.ROOT, "  lots %s (frontage x depth):", p.town));
        for (Map.Entry<String, double[]> e : sizes.entrySet()) {
            double[] m = e.getValue();
            ls.append(String.format(Locale.ROOT, " %s %.0f %.0f-%.0f x %.0f-%.0f;", e.getKey(), m[4], m[0], m[1], m[2], m[3]));
        }
        System.out.println(ls);
        // steps: within a part a 0.5 m step stays under the street grade (plus the crown), across the gutter the curb's
        TownWorks.Part[] parts = TownWorks.Part.values();
        for (int a = 0; a < np; a++)
            for (int b = a; b < np; b++) {
                if (at[a][b] == null) continue;
                // the curb's face, at a driveway's flare too
                double lim = parts[a] == TownWorks.Part.ASPHALT && (parts[b] == TownWorks.Part.CURB || parts[b] == TownWorks.Part.DRIVEWAY) ? 0.20 : 0.12;
                boolean lots = parts[a] == TownWorks.Part.LOT || parts[b] == TownWorks.Part.LOT || parts[a] == TownWorks.Part.YARD || parts[b] == TownWorks.Part.YARD;
                String flag = worst[a][b] > lim ? (lots ? "  (terrace)" : "  STEP") : "";
                if (worst[a][b] > lim && !lots) ok = false;
                System.out.printf(Locale.ROOT, "    step %s|%s %.3f m at s %.1f v %.1f%s%n", parts[a].name().toLowerCase(Locale.ROOT), parts[b].name().toLowerCase(Locale.ROOT),
                        worst[a][b], at[a][b][0], at[a][b][1], flag);
            }
        // driveways and pads (2026-10-18): every driveway cut (an apron in front of its middle), every lot not as wide as
        // its block on its pad at its middle, the pad against the sidewalk at its first driveway
        int drives = 0, uncut = 0, padded = 0, offPad = 0;
        double padRise = Double.NEGATIVE_INFINITY, padDrop = Double.POSITIVE_INFINITY;
        String firstUncut = null;
        Map<String, Integer> byZone = new java.util.TreeMap<>();
        for (TownPlan.Lot l : p.lots) {
            TownPlan.Block blk = null;
            for (TownPlan.Block b : p.blocks)
                if (l.s0() >= b.s0() - 0.01 && l.s1() <= b.s1() + 0.01 && l.v0() >= b.v0() - 0.01 && l.v1() <= b.v1() + 0.01) { blk = b; break; }
            if (blk == null) continue;
            double vEdge = l.face() == TownPlan.FACE_V0 ? l.v0() : l.v1(), dir = l.face() == TownPlan.FACE_V0 ? -1 : 1;
            // Fuel Stop A1's lot: the template's main entrance (TownWorks#fuelStop; its side one, TownBuildingsCheck)
            com.antaurora.apofirstlight.worldgen.network.town.TownBuildings.FuelStop fs = tw.fuelStop(p);
            double[][] dw = fs != null && p.lots.get(fs.lot()) == l ? new double[][]{fs.main()} : TownPlan.driveways(l, blk.s0(), blk.s1());
            for (double[] d : dw) {
                drives++;
                byZone.merge(l.zone(), 1, Integer::sum);
                double sc = (d[0] + d[1]) / 2;
                boolean cut = false;
                for (double o = 0.25; o <= 12 && !cut; o += 0.25) {
                    double[] w = p.world(sc, vEdge + dir * o);
                    TownWorks.Spot sp = tw.spot(w[0], w[1]);
                    if (sp != null && sp.part() == TownWorks.Part.DRIVEWAY) cut = true;
                }
                if (!cut) { uncut++; if (firstUncut == null) firstUncut = String.format(Locale.ROOT, "%s lot at s %.1f v %.1f", l.zone(), sc, vEdge); }
            }
            boolean whole = l.s0() - blk.s0() < 0.5 && blk.s1() - l.s1() < 0.5;
            if (whole) continue;
            padded++;
            // A1's lot lies at the template's surface (its floor), not at the lot's pad (2026-10-20)
            double pad = fs != null && p.lots.get(fs.lot()) == l ? fs.site().floorY() : l.pad();
            double[] mid = p.world((l.s0() + l.s1()) / 2, (l.v0() + l.v1()) / 2);
            TownWorks.Spot ms = tw.spot(mid[0], mid[1]);
            if (ms == null || Math.abs(ms.h() - pad) > 0.01) {
                offPad++;
                if (firstUncut == null) firstUncut = String.format(Locale.ROOT, "off pad: %s lot %.0f x %.0f at s %.1f v %.1f, %s %.2f vs pad %.2f", l.zone(), l.s1() - l.s0(), l.v1() - l.v0(),
                        (l.s0() + l.s1()) / 2, (l.v0() + l.v1()) / 2, ms == null ? "-" : ms.part(), ms == null ? Double.NaN : ms.h(), pad);
            }
            if (dw.length > 0) {
                double[] w = p.world((dw[0][0] + dw[0][1]) / 2, vEdge + dir * 0.25);
                TownWorks.Spot sw = tw.spot(w[0], w[1]);
                if (sw != null) { padRise = Math.max(padRise, pad - sw.h()); padDrop = Math.min(padDrop, pad - sw.h()); }
            }
        }
        System.out.printf(Locale.ROOT, "    driveways %d %s, not cut %d%s; lots on a pad %d (off their pad at the middle %d), pad over the sidewalk at the driveway %+.2f .. %+.2f m%n",
                drives, byZone, uncut, firstUncut == null ? "" : " (" + firstUncut + ")", padded, offPad, padDrop, padRise);
        if (uncut > 0 || offPad > 0) ok = false;
        // the vehicles' ground query on the town's pavement (RoadSurfaceQuery#pavement, 2026-10-18): every paved spot found
        // at its height, asphalt or concrete as it is, no steeper than 15 %
        RoadSurfaceQuery.Out out = new RoadSurfaceQuery.Out();
        int qn = 0, qMiss = 0, qOff = 0, qKind = 0, qSteep = 0;
        double qWorst = 0, a1Steep = 0;
        String qFirst = null;
        double minNy = 1 / Math.sqrt(1 + 0.15 * 0.15);
        for (double qs = p.limit[0]; qs <= p.limit[1]; qs += 1)
            for (double qv = p.limit[2]; qv <= p.limit[3]; qv += 1) {
                double[] w = p.world(qs + 0.37, qv + 0.61);
                TownWorks.Spot sp = tw.spot(w[0], w[1]);
                if (sp == null || !sp.part().paved()) continue;
                qn++;
                if (!RoadSurfaceQuery.pavement(works, w[0], w[1], sp.h(), out)) {
                    qMiss++;
                    if (qFirst == null) qFirst = String.format(Locale.ROOT, "missed %s at s %.1f v %.1f", sp.part(), qs, qv);
                    continue;
                }
                double d = Math.abs(out.height - sp.h());
                qWorst = Math.max(qWorst, d);
                if (d > 1e-6) qOff++;
                if ((out.surface == RoadSurfaceQuery.Surface.ASPHALT) != (sp.part() == TownWorks.Part.ASPHALT) && d <= 1e-6) qKind++;
                // Fuel Stop A1's aprons climb up to a metre across the sidewalk (2026-10-20): reported apart, at most 25 %
                if (tw.onFuelStopApron(w[0], w[1])) { a1Steep = Math.max(a1Steep, Math.sqrt(1 / (out.ny * out.ny) - 1)); continue; }
                if (out.ny < minNy) { qSteep++; if (qFirst == null) qFirst = String.format(Locale.ROOT, "steep %s at s %.1f v %.1f (ny %.3f)", sp.part(), qs, qv, out.ny); }
            }
        System.out.printf(Locale.ROOT, "    ground query on the pavement: %d points, missed %d, another height %d (a road's, worst %.3f m), wrong material %d, steeper than 15 %% %d%s; Fuel Stop A1's aprons at most %.0f %%%n",
                qn, qMiss, qOff, qWorst, qKind, qSteep, qFirst == null ? "" : " (" + qFirst + ")", a1Steep * 100);
        if (qMiss > 0 || qKind > 0 || qSteep > 0 || a1Steep > 0.25) ok = false;
        // where the lots are steepest: the ground across that point both ways, and what it is
        int li = TownWorks.Part.LOT.ordinal();
        if (at[li][li] != null) {
            double s0 = at[li][li][0], v0 = at[li][li][1];
            double[] w0 = p.world(s0, v0);
            System.out.println("    steepest lot point: " + tw.describe(w0[0], w0[1]));
            for (int axis = 0; axis < 2; axis++) {
                StringBuilder pr = new StringBuilder(axis == 0 ? "      along s:" : "      along v:");
                for (int k = -8; k <= 8; k++) {
                    double[] w = p.world(s0 + (axis == 0 ? k : 0), v0 + (axis == 1 ? k : 0));
                    TownWorks.Spot sp = tw.spot(w[0], w[1]);
                    pr.append(sp == null ? " -" : String.format(Locale.ROOT, " %.2f%s", sp.h(), sp.part().name().substring(0, 1).toLowerCase(Locale.ROOT)));
                }
                System.out.println(pr);
            }
        }
        // the crossroad's mouth: the crossroad's pavement at its end against the town's asphalt just past it
        RoadDesign x = works.road(p.exit);
        for (double[] e : p.exitEnds) {
            TownPlan.Node n = p.nodes.get((int) e[0]);
            double[] w = p.world(n.s(), e[1]);
            double[] l = x == null ? null : x.local(w[0], w[1], 5);
            double road = l == null ? Double.NaN : x.pavementY(Math.max(0, Math.min(x.length, l[0])), l[1]);
            double dir = Math.signum(n.v() - e[1]);
            double[] w2 = p.world(n.s(), e[1] + dir * 0.25);
            TownWorks.Spot sp = tw.spot(w2[0], w2[1]);
            double d = sp == null ? Double.NaN : sp.h() - road;
            System.out.printf(Locale.ROOT, "    mouth node %d: crossroad end %.2f, town %.2f (%s), step %+.3f m%n", (int) e[0], road,
                    sp == null ? Double.NaN : sp.h(), sp == null ? "-" : sp.part().name().toLowerCase(Locale.ROOT), d);
            if (!(Math.abs(d) <= 0.08)) ok = false;
        }
        // the block columns over the limit and the slopes
        int paved = 0, turf = 0, other = 0, steep = 0;
        double maxCut = 0, maxFill = 0;
        double[] c0 = p.world(p.limit[0] - TownWorks.REACH, p.limit[2] - TownWorks.REACH), c1 = p.world(p.limit[1] + TownWorks.REACH, p.limit[3] + TownWorks.REACH),
                c2 = p.world(p.limit[0] - TownWorks.REACH, p.limit[3] + TownWorks.REACH), c3 = p.world(p.limit[1] + TownWorks.REACH, p.limit[2] - TownWorks.REACH);
        int x0 = (int) Math.floor(Math.min(Math.min(c0[0], c1[0]), Math.min(c2[0], c3[0]))), x1 = (int) Math.ceil(Math.max(Math.max(c0[0], c1[0]), Math.max(c2[0], c3[0])));
        int z0 = (int) Math.floor(Math.min(Math.min(c0[1], c1[1]), Math.min(c2[1], c3[1]))), z1 = (int) Math.ceil(Math.max(Math.max(c0[1], c1[1]), Math.max(c2[1], c3[1])));
        int W = x1 - x0, H = z1 - z0;
        BufferedImage img = png == null ? null : new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        double[][] fin = png == null ? null : new double[H][W];
        long t0 = System.nanoTime();
        for (int z = z0; z < z1; z++)
            for (int xx = x0; xx < x1; xx++) {
                double[] l = p.local(xx + 0.5, z + 0.5);
                if (!p.inside(l[0], l[1], TownWorks.REACH)) continue;
                RoadWorks.Column c = works.column(xx, z, ground);
                switch (c.kind()) {
                    case PAVED -> paved++;
                    case TURF -> {
                        turf++;
                        int d = c.data();
                        int lo = Math.min(Math.min(d & 15, d >> 4 & 15), Math.min(d >> 8 & 15, d >> 12 & 15)), hi = Math.max(Math.max(d & 15, d >> 4 & 15), Math.max(d >> 8 & 15, d >> 12 & 15));
                        if (hi - lo >= 8) steep++;
                    }
                    case NONE -> { }
                    default -> other++;
                }
                if (c.kind() != RoadWorks.Kind.NONE) {
                    double g = ground.at(xx + 0.5, z + 0.5), f = works.finished(xx + 0.5, z + 0.5, ground);
                    maxFill = Math.max(maxFill, f - g);
                    maxCut = Math.max(maxCut, g - f);
                }
                if (fin != null) fin[z - z0][xx - x0] = works.finished(xx + 0.5, z + 0.5, ground);
            }
        System.out.printf(Locale.ROOT, "    columns: paved %d, turf %d (a block's rise over the square: %d), other %d; deepest cut %.1f m, highest fill %.1f m; %d ms%n",
                paved, turf, steep, other, maxCut, maxFill, (System.nanoTime() - t0) / 1_000_000);
        if (img != null) {
            for (int y = 0; y < H; y++)
                for (int xx = 0; xx < W; xx++) {
                    double wx = x0 + xx + 0.5, wz = z0 + y + 0.5;
                    double[] l = p.local(wx, wz);
                    if (!p.inside(l[0], l[1], TownWorks.REACH)) { img.setRGB(xx, y, 0xffffff); continue; }
                    double f = fin[y][xx];
                    double dx = fin[y][Math.min(W - 1, xx + 1)] - fin[y][Math.max(0, xx - 1)], dz = fin[Math.min(H - 1, y + 1)][xx] - fin[Math.max(0, y - 1)][xx];
                    double shade = Math.max(0.45, Math.min(1.25, 1 - 0.6 * (dx - dz) / 2));
                    TownWorks.Spot sp = works.towns().spot(wx, wz);
                    int rgb;
                    if (sp == null) rgb = terrain.water(wx, wz) != RouteTerrain.DRY ? 0x6f9fc8 : Math.abs(f - ground.at(wx, wz)) > 0.05 ? 0xb9a27a : 0x9cbf7c;
                    else rgb = switch (sp.part()) {
                        case ASPHALT -> 0x505357;
                        case CURB -> 0xe6e6e0;
                        case SIDEWALK -> 0xc9c7bf;
                        case UTILITY -> 0x7fae63;
                        case LOT -> 0xd9cba3;
                        case YARD -> 0xa8c98a;
                        case DRIVEWAY -> 0xb0aca2;
                    };
                    // a road's pavement over the town's ground (the crossroad, the interstate)
                    double[] pr = sp == null ? works.pavedRange(xx + x0, y + z0) : null;
                    if (pr != null) rgb = 0x3e4044;
                    // contours every metre
                    if (Math.floor(f) != Math.floor(fin[y][Math.min(W - 1, xx + 1)]) || Math.floor(f) != Math.floor(fin[Math.min(H - 1, y + 1)][xx])) shade *= 0.8;
                    int r = (int) Math.min(255, (rgb >> 16 & 255) * shade), g = (int) Math.min(255, (rgb >> 8 & 255) * shade), b = (int) Math.min(255, (rgb & 255) * shade);
                    img.setRGB(xx, y, r << 16 | g << 8 | b);
                }
            png.getParentFile().mkdirs();
            ImageIO.write(img, "png", png);
            System.out.println("    works picture " + png);
        }
        return ok;
    }

}
