package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * Ecology V1 offline numbers (2026-10-10): plan build time with the ecology zones, the cost of one uncached biome
 * evaluation (EcologyBiomes.biomeAt; the game caches per quart column), the land shares at the quart lattice the game
 * samples, and the ecology acceptance points.
 * <pre>java EcologyBench SEED</pre>
 */
public final class EcologyBench {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        long t0 = System.nanoTime();
        TerrainPlanV2 p = new TerrainPlanV2(seed);
        TerrainPlanSurface s = p.toSurface();
        System.out.printf("plan + rivers + ecology built in %d ms; report %s%n", (System.nanoTime() - t0) / 1_000_000, p.report);
        // warm-up, then timed evaluations on a fixed pseudo-random land sample
        java.util.SplittableRandom rnd = new java.util.SplittableRandom(7);
        int n = 400_000;
        double[] xs = new double[n], zs = new double[n];
        for (int i = 0; i < n; i++) { xs[i] = -9000 + rnd.nextDouble() * 18000; zs[i] = -9000 + rnd.nextDouble() * 18000; }
        int sink = 0;
        for (int i = 0; i < 50_000; i++) sink += EcologyBiomes.biomeAt(s, xs[i], zs[i]);
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) sink += EcologyBiomes.biomeAt(s, xs[i], zs[i]);
        double ns = (System.nanoTime() - t1) / (double) n;
        System.out.printf("biomeAt: %.0f ns per uncached evaluation (single thread, %d samples, sink %d)%n", ns, n, sink);
        // one chunk = 16 quart columns; the game evaluates each once per worker thread (cache), plus neighbours for zoom
        System.out.printf("per chunk: about 16-36 evaluations = %.2f-%.2f ms (biome fill + surface rules hit the cache)%n", 16 * ns / 1e6, 36 * ns / 1e6);
        int[] count = new int[EcologyBiomes.IDS.length];
        for (int x = -11000; x < 11000; x += 16) for (int z = -11000; z < 11000; z += 16) count[EcologyBiomes.biomeAt(s, x, z)]++;
        int land = 0;
        for (int k = 1; k < count.length; k++) land += count[k];
        for (int k = 1; k < count.length; k++) System.out.printf("  %-26s %5.1f %% of land%n", EcologyBiomes.IDS[k], 100.0 * count[k] / land);
        for (TerrainV2TestPoints.Point pt : TerrainV2TestPoints.ecology(s))
            System.out.printf("| %s | %d %d | %.1f | %s |%n", pt.name(), pt.x(), pt.z(), s.heightAt(pt.x(), pt.z()), pt.note());
    }
}
