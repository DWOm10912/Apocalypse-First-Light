package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.contract.RoadNode;
import com.antaurora.apofirstlight.worldgen.network.contract.RotatedRect;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The forest home's driveway search (County road G3 side join, 2026-10-19, docs/worldgen/county_road_g3_side_join_plan.md
 * 2; user: "车道沿溪从侧面 T 字接入，车道长度按契约上限 250 m 内选"): a Dijkstra on a STEP m grid round the home's node H,
 * from STRAIGHT_OUT m out of the yard along H's heading, over at most RoadCounty.DRIVE_MAX less the driveway's straight
 * run to the road (RoadCounty.DRIVE_RUN). Every node it reaches is a place the driveway can turn square toward a county
 * road; RoadCounty routes the county road past the cheapest ones.
 * <ul>
 *   <li>closed: planned water and its banks (nearer than BANK to water: the contract's "不过溪"), the home's plot, every
 *   built road within ROAD_CLEAR of its paved edge, ground steeper than STEEP between two nodes;</li>
 *   <li>cost per metre: 1, plus GRADE_COST per unit of grade beyond GRADE_FREE (cut and fill for the profile), less
 *   ALONG_BONUS within ALONG_NEAR .. ALONG_FAR of water (along the creek, the concept's "沿溪下行"), plus NEAR_COST nearer
 *   than ALONG_NEAR (too close to the bank).</li>
 * </ul>
 * Deterministic: fixed scan orders, a heap with an id tie-break. Pure Java.
 */
final class DriveSearch {
    static final double STEP = 4, STRAIGHT_OUT = 8, BANK = 8, ALONG_NEAR = 10, ALONG_FAR = 40, ALONG_BONUS = 0.15, NEAR_COST = 0.5;
    static final double STEEP = 0.25, GRADE_FREE = 0.06, GRADE_COST = 6, ROAD_CLEAR = 25;
    private static final int[] DC = {1, -1, 0, 0, 1, 1, -1, -1, 2, 2, -2, -2, 1, -1, 1, -1};
    private static final int[] DR = {0, 0, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 2, 2, -2, -2};

    final double hx, hz, hy, ox, oz;
    final int n;
    /** Ground height, distance to water (m), closed; the search's cost, path length (from H), previous node (-1: the first). */
    private final double[] ground, water, cost, len;
    private final boolean[] closed;
    private final int[] prev;
    /** The first node (STRAIGHT_OUT along H's heading), or -1 when it is closed. */
    final int first;

    DriveSearch(RouteTerrain t, RoadNode h, RotatedRect plot, List<RoadDesign> designs) {
        hx = h.x();
        hz = h.z();
        hy = h.y();
        double reach = RoadCounty.DRIVE_MAX + 2 * STEP;
        int half = (int) Math.ceil(reach / STEP);
        n = 2 * half + 1;
        ox = hx - half * STEP;
        oz = hz - half * STEP;
        int nn = n * n;
        ground = new double[nn];
        water = new double[nn];
        closed = new boolean[nn];
        // the roads that come near: a centreline point within reach + their clearance
        List<RoadDesign> near = new ArrayList<>();
        for (RoadDesign d : designs) {
            double need = reach + d.section.edge + ROAD_CLEAR + 10;
            for (double s = 0; s <= d.length + 1e-9; s += 10) {
                double[] p = d.point(Math.min(s, d.length), 0);
                if (Math.hypot(p[0] - hx, p[1] - hz) <= need) { near.add(d); break; }
            }
        }
        RotatedRect home = plot.grow(0.5);
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                int i = r * n + c;
                double x = x(i), z = z(i);
                ground[i] = t.height(x, z);
                water[i] = t.water(x, z) != RouteTerrain.DRY ? 0 : Double.POSITIVE_INFINITY;
                if (home.contains(x, z)) closed[i] = true;
                for (RoadDesign d : near) {
                    double[] l = d.local(x, z, d.section.edge + ROAD_CLEAR);
                    if (l != null && Math.abs(l[1]) <= d.section.edge + ROAD_CLEAR) { closed[i] = true; break; }
                }
            }
        distances();
        cost = new double[nn];
        len = new double[nn];
        prev = new int[nn];
        Arrays.fill(cost, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);
        double hr = Math.toRadians(h.heading());
        int f = node(hx + Math.cos(hr) * STRAIGHT_OUT, hz + Math.sin(hr) * STRAIGHT_OUT);
        first = f < 0 || closed[f] || water[f] < BANK ? -1 : f;
        if (first >= 0) search();
    }

    /** Chamfer distance to the nearest water node, m (two passes). */
    private void distances() {
        double d1 = STEP, d2 = STEP * Math.sqrt(2);
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                int i = r * n + c;
                if (c > 0) water[i] = Math.min(water[i], water[i - 1] + d1);
                if (r > 0) {
                    water[i] = Math.min(water[i], water[i - n] + d1);
                    if (c > 0) water[i] = Math.min(water[i], water[i - n - 1] + d2);
                    if (c < n - 1) water[i] = Math.min(water[i], water[i - n + 1] + d2);
                }
            }
        for (int r = n - 1; r >= 0; r--)
            for (int c = n - 1; c >= 0; c--) {
                int i = r * n + c;
                if (c < n - 1) water[i] = Math.min(water[i], water[i + 1] + d1);
                if (r < n - 1) {
                    water[i] = Math.min(water[i], water[i + n] + d1);
                    if (c < n - 1) water[i] = Math.min(water[i], water[i + n + 1] + d2);
                    if (c > 0) water[i] = Math.min(water[i], water[i + n - 1] + d2);
                }
            }
    }

    private void search() {
        double limit = RoadCounty.DRIVE_MAX - RoadCounty.DRIVE_RUN;
        boolean[] done = new boolean[n * n];
        RouteGridHeap open = new RouteGridHeap();
        cost[first] = STRAIGHT_OUT;
        len[first] = STRAIGHT_OUT;
        open.push(cost[first], first);
        while (open.size > 0) {
            int i = open.pop();
            if (done[i]) continue;
            done[i] = true;
            int c = i % n, r = i / n;
            for (int k = 0; k < DC.length; k++) {
                int cc = c + DC[k], rr = r + DR[k];
                if (cc < 0 || rr < 0 || cc >= n || rr >= n) continue;
                int j = rr * n + cc;
                if (done[j] || closed[j] || water[j] < BANK) continue;
                double l = STEP * Math.hypot(DC[k], DR[k]), nl = len[i] + l;
                if (nl > limit) continue;
                double g = Math.abs(ground[j] - ground[i]) / l;
                if (g > STEEP) continue;
                double w = water[j];
                double f = 1 + GRADE_COST * Math.max(0, g - GRADE_FREE) + (w < ALONG_NEAR ? NEAR_COST : w <= ALONG_FAR ? -ALONG_BONUS : 0);
                double nc = cost[i] + l * f;
                if (nc < cost[j]) {
                    cost[j] = nc;
                    len[j] = nl;
                    prev[j] = i;
                    open.push(nc, j);
                }
            }
        }
    }

    double x(int i) { return ox + (i % n) * STEP; }

    double z(int i) { return oz + (i / n) * STEP; }

    /** The node nearest (x, z), or -1 outside the grid. */
    int node(double x, double z) {
        int c = (int) Math.round((x - ox) / STEP), r = (int) Math.round((z - oz) / STEP);
        return c < 0 || r < 0 || c >= n || r >= n ? -1 : r * n + c;
    }

    boolean reached(int i) { return i >= 0 && cost[i] < Double.POSITIVE_INFINITY; }

    double cost(int i) { return cost[i]; }

    double length(int i) { return len[i]; }

    double ground(int i) { return ground[i]; }

    /** Distance from node i to water, m (infinite: none on the grid). */
    double water(int i) { return water[i]; }

    /** The reached nodes, in index order. */
    int[] reachedNodes() {
        int k = 0;
        for (double c : cost) if (c < Double.POSITIVE_INFINITY) k++;
        int[] out = new int[k];
        k = 0;
        for (int i = 0; i < cost.length; i++) if (cost[i] < Double.POSITIVE_INFINITY) out[k++] = i;
        return out;
    }

    /** The cheapest reached node within r of (x, z) whose path length lies in [lo, hi] and that ok accepts, or -1. */
    int best(double x, double z, double r, double lo, double hi, java.util.function.IntPredicate ok) {
        int c0 = (int) Math.floor((x - r - ox) / STEP), c1 = (int) Math.ceil((x + r - ox) / STEP);
        int r0 = (int) Math.floor((z - r - oz) / STEP), r1 = (int) Math.ceil((z + r - oz) / STEP);
        int out = -1;
        for (int rr = Math.max(0, r0); rr <= Math.min(n - 1, r1); rr++)
            for (int cc = Math.max(0, c0); cc <= Math.min(n - 1, c1); cc++) {
                int i = rr * n + cc;
                if (!reached(i) || len[i] < lo || len[i] > hi || Math.hypot(x(i) - x, z(i) - z) > r || !ok.test(i)) continue;
                if (out < 0 || cost[i] < cost[out]) out = i;
            }
        return out;
    }

    /** The path from H to node i: {x0, z0, x1, z1, ...}, H first. */
    double[] path(int i) {
        List<Integer> ns = new ArrayList<>();
        for (int k = i; k >= 0; k = prev[k]) ns.add(k);
        double[] out = new double[2 * ns.size() + 2];
        out[0] = hx;
        out[1] = hz;
        for (int q = 0; q < ns.size(); q++) {
            int k = ns.get(ns.size() - 1 - q);
            out[2 + 2 * q] = x(k);
            out[3 + 2 * q] = z(k);
        }
        return out;
    }

    /** Min-heap of (double key, int id); ties broken by id. */
    static final class RouteGridHeap {
        double[] key = new double[1024];
        int[] id = new int[1024];
        int size;

        void push(double k, int v) {
            if (size == key.length) { key = Arrays.copyOf(key, size * 2); id = Arrays.copyOf(id, size * 2); }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (less(key[p], id[p], k, v)) break;
                key[i] = key[p]; id[i] = id[p]; i = p;
            }
            key[i] = k; id[i] = v;
        }

        int pop() {
            int top = id[0];
            double k = key[--size];
            int v = id[size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= size) break;
                int m = l + 1 < size && less(key[l + 1], id[l + 1], key[l], id[l]) ? l + 1 : l;
                if (!less(key[m], id[m], k, v)) break;
                key[i] = key[m]; id[i] = id[m]; i = m;
            }
            key[i] = k; id[i] = v;
            return top;
        }

        static boolean less(double ka, int ia, double kb, int ib) { return ka < kb || (ka == kb && ia < ib); }
    }
}
