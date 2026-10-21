package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Offline check of a town's building sites (Town buildings V1, docs/worldgen/town_buildings_v1.md), run by TownCheck:
 * the grid on the block grid, every footprint inside its lot and on lot or yard ground (never a street, sidewalk or
 * curb cut), no two footprints sharing a block, a house clear of its driveway, every front toward its lot's street;
 * reports the sites by kind, the lots left empty, and how far the footprints' ground lies from their pads (the footing
 * the pass carries down).
 */
final class TownBuildingsCheck {
    private TownBuildingsCheck() {}

    static boolean check(TownPlan p, TownWorks works) {
        boolean ok = true;
        if (!TownBuildings.onBlockGrid(p)) {
            System.out.printf(Locale.ROOT, "  BUILDINGS: town %s is not on the block grid (axis %.4f %.4f)%n", p.town, p.ax, p.az);
            return false;
        }
        List<TownBuildings.Site> sites = TownBuildings.sites(p, works.fuelStop(p));
        Map<String, Integer> kinds = new TreeMap<>(), empty = new TreeMap<>();
        Map<Long, Integer> taken = new HashMap<>();
        int outside = 0, offGround = 0, overlap = 0, onDrive = 0, wrongFront = 0, cells = 0;
        double worstBelow = 0, worstAbove = 0;
        String firstBad = null;
        boolean[] has = new boolean[p.lots.size()];
        for (TownBuildings.Site s : sites) {
            has[s.lot()] = true;
            kinds.merge(s.kind(), 1, Integer::sum);
            TownPlan.Lot l = p.lots.get(s.lot());
            // the driveway's strip along the street (a house keeps clear of it)
            double[][] dw = new double[0][];
            for (TownPlan.Block b : p.blocks)
                if (l.s0() >= b.s0() - 0.01 && l.s1() <= b.s1() + 0.01 && l.v0() >= b.v0() - 0.01 && l.v1() <= b.v1() + 0.01) dw = TownPlan.driveways(l, b.s0(), b.s1());
            for (int x = s.x0(); x <= s.x1(); x++)
                for (int z = s.z0(); z <= s.z1(); z++) {
                    cells++;
                    double[] t = p.local(x + 0.5, z + 0.5);
                    String bad = null;
                    if (t[0] < l.s0() || t[0] > l.s1() || t[1] < l.v0() || t[1] > l.v1()) { outside++; bad = "outside its lot"; }
                    TownWorks.Spot sp = works.spot(x + 0.5, z + 0.5);
                    if (sp == null || sp.part() != TownWorks.Part.LOT && sp.part() != TownWorks.Part.YARD) { offGround++; bad = "on " + (sp == null ? "no town ground" : sp.part()); }
                    else {
                        double d = sp.h() - (TownBuildings.FUEL_STOP.equals(s.kind()) ? s.floorY() : l.pad());
                        worstBelow = Math.min(worstBelow, d);
                        worstAbove = Math.max(worstAbove, d);
                    }
                    if (s.kind().startsWith("house") || s.kind().equals("mobile_home"))
                        for (double[] q : dw) if (t[0] > q[0] && t[0] < q[1]) { onDrive++; bad = "on its driveway"; }
                    Integer o = taken.put(((long) x << 32) ^ (z & 0xffffffffL), s.lot());
                    if (o != null) { overlap++; bad = "shared with lot " + o; }
                    if (bad != null && firstBad == null) firstBad = String.format(Locale.ROOT, "lot %d (%s) block %d %d %s", s.lot(), s.kind(), x, z, bad);
                }
            // the front: a step from the footprint's middle toward its front comes nearer the lot's street line
            double[] mid = {(s.x0() + s.x1() + 1) / 2.0, (s.z0() + s.z1() + 1) / 2.0}, out = {mid[0] + s.fx(), mid[1] + s.fz()};
            double vStreet = l.face() == TownPlan.FACE_V0 ? l.v0() : l.v1();
            if (Math.abs(p.local(out[0], out[1])[1] - vStreet) >= Math.abs(p.local(mid[0], mid[1])[1] - vStreet)) { wrongFront++; if (firstBad == null) firstBad = "lot " + s.lot() + " faces away from its street"; }
        }
        for (int i = 0; i < p.lots.size(); i++) if (!has[i]) empty.merge(p.lots.get(i).zone(), 1, Integer::sum);
        System.out.printf(Locale.ROOT, "  buildings %s: %d sites %s, lots without one %s; %d footprint blocks: outside the lot %d, off lot or yard ground %d, shared %d, on a driveway %d, facing away %d; ground under the footprints %+.2f .. %+.2f m from the pad%n",
                p.town, sites.size(), kinds, empty, cells, outside, offGround, overlap, onDrive, wrongFront, worstBelow, worstAbove);
        // Fuel Stop A1 (2026-10-20): where, and its two entrances against the driveway aprons the works cut
        TownBuildings.FuelStop fs = works.fuelStop(p);
        if (fs == null) {
            int hwy = 0;
            for (TownPlan.Lot l : p.lots) if ("hwy".equals(l.zone())) hwy++;
            System.out.printf(Locale.ROOT, "  fuel stop %s: none (%d highway lots, none a right-hand corner of 64 x 64)%n", p.town, hwy);
        } else {
            boolean first = true;
            List<TownBuildings.FuelStop> all = new ArrayList<>();
            all.add(fs);
            for (TownBuildings.FuelStop c : TownBuildings.fuelStops(p)) if (c.lot() != fs.lot()) all.add(c);
            for (TownBuildings.FuelStop c : all) {
                TownBuildings.Site s = c.site();
                TownPlan.Lot l = p.lots.get(c.lot());
                // each entrance: an apron within 8 m outside the template's edge, the sidewalk's height at the edge
                boolean[] apron = new boolean[2];
                double[] walk = new double[2];
                for (int k = 0; k < 2; k++) {
                    double a = (k == 0 ? c.main()[0] + c.main()[1] : c.side()[0] + c.side()[1]) / 2;
                    for (int m = 1; m <= 8; m++) {
                        double[] t = k == 0 ? new double[]{a, l.face() == TownPlan.FACE_V0 ? l.v0() - m + 0.5 : l.v1() + m - 0.5}
                                : new double[]{c.sideEdge() + c.sideDir() * (m - 0.5), a};
                        double[] w = p.world(t[0], t[1]);
                        TownWorks.Spot sp = works.spot(w[0], w[1]);
                        if (m == 1) walk[k] = sp == null ? Double.NaN : sp.h();
                        if (sp != null && sp.part() == TownWorks.Part.DRIVEWAY) apron[k] = true;
                    }
                }
                // the ground just outside each edge of the footprint against the template's surface (its floor Y)
                double[][] edge = new double[4][]; // front, right (side street), back, left; the back and left also off their ends' 5 m (the street corners' banks)
                for (int e = 0; e < 4; e++) edge[e] = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
                int dx = -s.fx(), dz = -s.fz(), rx = -dz, rz = dx;
                int cx = rx + dx > 0 ? s.x0() : s.x1(), cz = rz + dz > 0 ? s.z0() : s.z1();
                for (int i = 0; i < TownBuildings.A1; i++)
                    for (int e = 0; e < 4; e++) {
                        int u = e == 0 ? i : e == 1 ? TownBuildings.A1 : e == 2 ? i : -1, w = e == 0 ? -1 : e == 1 ? i : e == 2 ? TownBuildings.A1 : i;
                        TownWorks.Spot sp = works.spot(cx + rx * u + dx * w + 0.5, cz + rz * u + dz * w + 0.5);
                        if (sp == null) continue;
                        double d = s.floorY() - sp.h();
                        edge[e][0] = Math.min(edge[e][0], d);
                        edge[e][1] = Math.max(edge[e][1], d);
                        if (i >= 5 && i < TownBuildings.A1 - 5) { edge[e][2] = Math.min(edge[e][2], d); edge[e][3] = Math.max(edge[e][3], d); }
                    }
                System.out.printf(Locale.ROOT, "  fuel stop %s%s: lot %d (%.0f x %.0f m), blocks %d %d .. %d %d, front %d %d, floor Y %d (sidewalk at the main entrance %.2f, at the side one %.2f); aprons main %s side %s; surface over the ground outside: front %+.1f .. %+.1f, side street %+.1f .. %+.1f, back %+.1f .. %+.1f, left %+.1f .. %+.1f m%n",
                        p.town, first ? "" : " (not chosen)", c.lot(), l.s1() - l.s0(), l.v1() - l.v0(), s.x0(), s.z0(), s.x1(), s.z1(), s.fx(), s.fz(), s.floorY(), walk[0], walk[1],
                        apron[0], apron[1], edge[0][0], edge[0][1], edge[1][0], edge[1][1], edge[2][0], edge[2][1], edge[3][0], edge[3][1]);
                if (first && !(apron[0] && apron[1])) {
                    System.out.println("  FUEL STOP FAILED: an entrance meets no driveway apron");
                    ok = false;
                }
                if (first) {
                    // 2026-10-20: each entrance meets the apron at the template's surface (no step at the lot line); the
                    // banked strips along the streets: their ground over the floor, their steepest step between neighbouring
                    // cells and against the sidewalk beyond; the ground under the price sign's foot (its lift)
                    double[] step = new double[2];
                    for (int k = 0; k < 2; k++) {
                        double a = (k == 0 ? c.main()[0] + c.main()[1] : c.side()[0] + c.side()[1]) / 2;
                        double[] t = k == 0 ? new double[]{a, l.face() == TownPlan.FACE_V0 ? l.v0() - 0.1 : l.v1() + 0.1} : new double[]{c.sideEdge() + c.sideDir() * 0.1, a};
                        double[] w = p.world(t[0], t[1]);
                        TownWorks.Spot sp = works.spot(w[0], w[1]);
                        step[k] = sp == null ? Double.NaN : sp.h() - s.floorY();
                    }
                    double bankLo = Double.POSITIVE_INFINITY, bankHi = Double.NEGATIVE_INFINITY, steep = 0, sign = Double.POSITIVE_INFINITY;
                    for (int x = 0; x < TownBuildings.A1; x++)
                        for (int z = 0; z < TownBuildings.A1; z++) {
                            if (!TownBuildings.bank(x, z)) continue;
                            int[] b = c.world(x, z);
                            TownWorks.Spot sp = works.spot(b[0] + 0.5, b[1] + 0.5);
                            if (sp == null) continue;
                            bankLo = Math.min(bankLo, sp.h() - s.floorY());
                            bankHi = Math.max(bankHi, sp.h() - s.floorY());
                            if (z == 2 && x >= 10 && x <= 13) sign = Math.min(sign, sp.h() - s.floorY());
                            for (int[] o : new int[][]{{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}}) {
                                int[] ob = c.world(o[0], o[1]);
                                TownWorks.Spot on = works.spot(ob[0] + 0.5, ob[1] + 0.5);
                                if (on != null) steep = Math.max(steep, Math.abs(on.h() - sp.h()));
                            }
                        }
                    System.out.printf(Locale.ROOT, "  fuel stop %s: step at the lot line main %+.2f side %+.2f m; banked strips %+.2f .. %+.2f m over the floor, steepest %.2f m between neighbouring blocks; price sign foot %+.2f m (it borders the curb: not raised)%n",
                            p.town, step[0], step[1], bankLo, bankHi, steep, sign);
                    if (!(Math.abs(step[0]) <= 0.05 && Math.abs(step[1]) <= 0.05)) {
                        System.out.println("  FUEL STOP FAILED: a step between an entrance and its apron");
                        ok = false;
                    }
                    System.out.printf(Locale.ROOT, "  fuel stop %s: surface over the ground outside, off the street corners: back %+.2f .. %+.2f, left %+.2f .. %+.2f m%n",
                            p.town, edge[2][2], edge[2][3], edge[3][2], edge[3][3]);
                    if (edge[2][2] < -0.5 || edge[2][3] > 0.5 || edge[3][2] < -0.5 || edge[3][3] > 0.5) {
                        System.out.println("  FUEL STOP FAILED: the ground behind or beside it is more than 0.5 m off its edge");
                        ok = false;
                    }
                }
                first = false;
            }
        }
        if (outside + offGround + overlap + onDrive + wrongFront > 0) {
            System.out.println("  BUILDINGS FAILED: first " + firstBad);
            ok = false;
        }
        return ok;
    }
}
