package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Exit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Kit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Need;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.PlaceKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Step;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.StepKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Searches on the underground route graph (docs/worldgen/underground_geology_caves_v0_design.md 19.3): what a kit
 * reaches from the surface, what can walk back out, the traps between the two (places a kit gets into and not out
 * of), and the shortest exit from a place with the least kit.
 */
public final class UndergroundRoutes {
    private UndergroundRoutes() {}

    /** The places a kit reaches walking in from every surface place. */
    public static BitSet reachIn(UndergroundPlan p, Kit kit) {
        BitSet seen = new BitSet();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (UndergroundPlan.Place pl : p.places) if (pl.kind() == PlaceKind.SURFACE) { seen.set(pl.id()); q.add(pl.id()); }
        List<List<Integer>> adj = p.adjacency();
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int si : adj.get(u)) {
                Step s = p.steps.get(si);
                int v = s.a() == u ? s.b() : s.a();
                if (seen.get(v) || UndergroundPlan.needFrom(s, u, kit) == null) continue;
                seen.set(v);
                q.add(v);
            }
        }
        return seen;
    }

    /** The places from which the kit can walk out to a surface place (each step taken in its own direction). */
    public static BitSet reachOut(UndergroundPlan p, Kit kit) {
        BitSet out = new BitSet();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (UndergroundPlan.Place pl : p.places) if (pl.kind() == PlaceKind.SURFACE) { out.set(pl.id()); q.add(pl.id()); }
        List<List<Integer>> adj = p.adjacency();
        while (!q.isEmpty()) {
            int v = q.poll();
            for (int si : adj.get(v)) {
                Step s = p.steps.get(si);
                int u = s.a() == v ? s.b() : s.a();
                // u -> v must be walkable for u to get out through v
                if (out.get(u) || UndergroundPlan.needFrom(s, u, kit) == null) continue;
                out.set(u);
                q.add(u);
            }
        }
        return out;
    }

    /** Traps for a kit: reached walking in, no way back out with the same kit (19.3; must be empty). */
    public static BitSet traps(UndergroundPlan p, Kit kit) {
        BitSet in = reachIn(p, kit);
        in.andNot(reachOut(p, kit));
        return in;
    }

    /**
     * The shortest exit (by length, a pitch counting four times its height) from a place to the surface with the kit,
     * or null. reliable: no sump, no V3 climbing and no crawl on it.
     */
    public static Exit exit(UndergroundPlan p, int from, Kit kit) {
        int n = p.places.size();
        double[] dist = new double[n];
        int[] via = new int[n];
        Arrays.fill(dist, Double.MAX_VALUE);
        Arrays.fill(via, -1);
        dist[from] = 0;
        PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        q.add(new double[]{0, from});
        List<List<Integer>> adj = p.adjacency();
        int goal = -1;
        while (!q.isEmpty()) {
            double[] e = q.poll();
            int u = (int) e[1];
            if (e[0] > dist[u]) continue;
            if (p.places.get(u).kind() == PlaceKind.SURFACE) { goal = u; break; }
            for (int si : adj.get(u)) {
                Step s = p.steps.get(si);
                int v = s.a() == u ? s.b() : s.a();
                if (UndergroundPlan.needFrom(s, u, kit) == null) continue;
                double w = s.length() + (vertical(s.kind()) ? 3 * Math.abs(s.dy()) : 0);
                if (dist[u] + w < dist[v]) {
                    dist[v] = dist[u] + w;
                    via[v] = si;
                    q.add(new double[]{dist[v], v});
                }
            }
        }
        if (goal < 0) return null;
        List<Integer> path = new ArrayList<>();
        Need need = Need.NONE;
        double len = 0, up = 0;
        int pitches = 0, haul = 0;
        boolean reliable = true;
        for (int v = goal; v != from; ) {
            Step s = p.steps.get(via[v]);
            int u = s.a() == v ? s.b() : s.a();
            path.add(s.id());
            Need nd = UndergroundPlan.needFrom(s, u, kit);
            need = need.plus(nd);
            len += s.length();
            double rise = s.a() == u ? s.dy() : -s.dy();
            if (rise > 0) up += rise;
            if (vertical(s.kind())) pitches++;
            haul = Math.max(haul, s.haul());
            if (s.kind() == StepKind.SUMP || nd.climb() || nd.crawl()) reliable = false;
            v = u;
        }
        Collections.reverse(path);
        return new Exit(goal, need, path.size(), len, up, pitches, haul, reliable, path);
    }

    public static boolean vertical(StepKind k) {
        return k == StepKind.ROPE || k == StepKind.FIXED_ROPE || k == StepKind.ROPE_LADDER || k == StepKind.FIXED_LADDER;
    }

    /** The least kit (index into KITS) whose walk in from the surface reaches the place, or -1. */
    public static int leastKitIn(UndergroundPlan p, int place, List<BitSet> reachByKit) {
        for (int k = 0; k < reachByKit.size(); k++) if (reachByKit.get(k).get(place)) return k;
        return -1;
    }
}
