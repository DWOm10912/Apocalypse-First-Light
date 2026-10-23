package com.antaurora.apofirstlight.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lightning V2 (docs/gameplay/lightning_v2_plan.md 6): the rolling sphere (StrikePoint) on a made-up scene. Flat ground
 * (tops at 64); a person 1.8 m tall alone at (0, 0); a 20 m tree at (40, 0); a 6 m barn at x -46..-34, z -6..6 with a lightning
 * rod (tip 0.976 m, its reach 6 m) in the middle of its roof, a person on the ground beside the barn at (-31, 0) and one on
 * the roof beside the rod at (-38, 0). Checks the capture radii, the strike distance's mean, who is struck by leaders right
 * over them and that the rod keeps both people at the barn from being struck; counts 20000 leaders within 60 m of the
 * lone person. Prints the numbers; exits 1 on a failed check.
 */
public final class LightningStrikeCheck {
    private static final double[] TRI = {20, 45, 80};
    private static final double GROUND = 64, ROD_TIP = 0.976, REACH = 6;
    private static int failed;

    public static void main(String[] args) {
        check("capture of a person at D 45 (12.6 m)", StrikePoint.capture(1.8, 45), 12.6, 0.1);
        check("capture of a 20 m tree at D 45 (37.4 m)", StrikePoint.capture(20, 45), 37.4, 0.1);
        double sum = 0;
        int n = 100000;
        for (int i = 0; i < n; i++) sum += StrikePoint.distance((i + 0.5) / n, TRI);
        check("mean strike distance (48.3 m)", sum / n, (TRI[0] + TRI[1] + TRI[2]) / 3, 0.05);
        check("least strike distance", StrikePoint.distance(0, TRI), 20, 1e-9);
        check("most strike distance", StrikePoint.distance(0.999999, TRI), 80, 0.2);

        List<StrikePoint.Thing> things = List.of(
                new StrikePoint.Thing(0, 0, GROUND + 1.8, "alone"),
                new StrikePoint.Thing(-31, 0, GROUND + 1.8, "by the barn"),
                new StrikePoint.Thing(-38, 0, GROUND + 6 + 1.8, "on the roof"));
        StrikePoint.Columns columns = (x, z, out) -> {
            out[1] = 0;
            if (x == 40 && z == 0) out[0] = GROUND + 20;
            else if (x >= -46 && x < -34 && z >= -6 && z < 6) {
                out[0] = GROUND + 6;
                if (x == -40 && z == 0) {
                    out[0] += ROD_TIP;
                    out[1] = REACH;
                }
            } else out[0] = GROUND;
            return true;
        };
        expect("leader over the lone person, D 45", StrikePoint.pick(0.3, 0.2, 45, columns, things), "alone");
        expect("leader over the tree's side, D 45", StrikePoint.pick(30, 5, 45, columns, things), "tree");
        expect("leader over the person by the barn, D 45", StrikePoint.pick(-31, 0, 45, columns, things), "rod");
        expect("leader over the person on the roof, D 20", StrikePoint.pick(-38, 0, 20, columns, things), "rod");
        expect("leader over the open ground 25 m out, D 20", StrikePoint.pick(0, 25, 20, columns, things), "ground");
        check("nothing loaded", StrikePoint.pick(0, 0, 45, (x, z, out) -> false, things) == null ? 0 : 1, 0, 0);

        Random random = new Random(20261022L);
        int alone = 0, byBarn = 0, onRoof = 0, tree = 0, rod = 0, ground = 0, leaders = 20000;
        List<Double> d = new ArrayList<>();
        for (int i = 0; i < leaders; i++) {
            double r = 60 * Math.sqrt(random.nextDouble()), a = random.nextDouble() * 2 * Math.PI;
            StrikePoint.Pick p = StrikePoint.pick(r * Math.cos(a), r * Math.sin(a), StrikePoint.distance(random.nextDouble(), TRI), columns, things);
            switch (name(p)) {
                case "alone" -> alone++;
                case "by the barn" -> byBarn++;
                case "on the roof" -> onRoof++;
                case "tree" -> tree++;
                case "rod" -> rod++;
                default -> ground++;
            }
        }
        // a lone person's share: their capture area (about pi 12.6²) over the 60 m disc, less what the tree and the rod take
        // (the rod model V2 tip at 0.976 m takes a few more: 1.99% with this seed, so the floor is 1.5%)
        double share = (double) alone / leaders;
        System.out.printf("20000 leaders within 60 m of the lone person: alone %d (%.2f%%), by the barn %d, on the roof %d, tree %d, rod %d, ground %d%n",
                alone, share * 100, byBarn, onRoof, tree, rod, ground);
        check("lone person's share (1.5-6%)", share, 0.0375, 0.0225);
        check("people at the barn struck", byBarn + onRoof, 0, 0);
        check("tree takes more than the lone person", tree > alone ? 1 : 0, 1, 0);
        System.out.println(failed == 0 ? "lightning strike: PASS" : "lightning strike: FAIL (" + failed + ")");
        if (failed > 0) System.exit(1);
    }

    private static String name(StrikePoint.Pick p) {
        if (p == null) return "none";
        if (!p.column()) return (String) p.thing();
        int x = (int) Math.floor(p.x()), z = (int) Math.floor(p.z());
        if (x == 40 && z == 0) return "tree";
        if (x == -40 && z == 0) return "rod";
        return x >= -46 && x < -34 && z >= -6 && z < 6 ? "barn" : "ground";
    }

    private static void expect(String what, StrikePoint.Pick p, String want) {
        String got = name(p);
        boolean ok = got.equals(want);
        System.out.printf("%s: %s%s%n", what, got, ok ? "" : " (want " + want + ") FAIL");
        if (!ok) failed++;
    }

    private static void check(String what, double got, double want, double tolerance) {
        boolean ok = Math.abs(got - want) <= tolerance;
        System.out.printf("%s: %.3f%s%n", what, got, ok ? "" : " (want " + want + ") FAIL");
        if (!ok) failed++;
    }
}
