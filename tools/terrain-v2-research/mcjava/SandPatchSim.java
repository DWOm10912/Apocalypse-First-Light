import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

import java.util.ArrayDeque;

/**
 * Sand-patch simulation for the ecology surface rules (2026-10-10 acceptance fix), with Minecraft's own NormalNoise
 * seeded the way RandomState seeds surface-rule noises (Xoroshiro positional factory, fromHashOf(noise id)), so the
 * patch sizes are the ones a world of that seed gets. Compares the old rule (sand where surface > T) with the new
 * inland rule (surface > A and ice > B) and reports the sand share and the largest connected patches.
 * <pre>java -cp forge.jar;. SandPatchSim SEED X0 Z0 SIZE</pre>
 */
public final class SandPatchSim {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        int x0 = Integer.parseInt(a[1]), z0 = Integer.parseInt(a[2]), n = Integer.parseInt(a[3]);
        PositionalRandomFactory pos = WorldgenRandom.Algorithm.XOROSHIRO.newInstance(seed).forkPositional();
        Normal surface = new Normal(pos.fromHashOf("minecraft:surface"), -6, 1.0, 1.0, 1.0);
        Normal ice = new Normal(pos.fromHashOf("minecraft:ice"), -4, 1.0, 1.0, 1.0, 1.0);
        double[][] s = new double[n][n], c = new double[n][n];
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            s[j][i] = surface.getValue(x0 + i, 0, z0 + j);
            c[j][i] = ice.getValue(x0 + i, 0, z0 + j);
        }
        report("old clearing: surface > -0.15", n, (i, j) -> s[j][i] > -0.15);
        report("old pine-oak: surface > 0.35", n, (i, j) -> s[j][i] > 0.35);
        report("clearing under Y65 (dunes): surface > -0.20", n, (i, j) -> s[j][i] > -0.20);
        report("clearing Y65-67: surface > 0, ice > 0.15", n, (i, j) -> s[j][i] > 0.0 && c[j][i] > 0.15);
        report("clearing over Y67: sand surface > 0, ice > 0.40", n, (i, j) -> s[j][i] > 0.0 && c[j][i] > 0.40);
        report("clearing over Y67: coarse dirt ring", n, (i, j) -> s[j][i] > 0.0 && c[j][i] > 0.10 && c[j][i] <= 0.40);
        report("pine-oak under Y65: surface > 0.25", n, (i, j) -> s[j][i] > 0.25);
        report("pine-oak over Y65: sand surface > 0.10, ice > 0.45", n, (i, j) -> s[j][i] > 0.10 && c[j][i] > 0.45);
    }

    interface Mask { boolean at(int i, int j); }

    /** NormalNoise without its NoiseParameters class (whose static codec pulls in the registries): same arithmetic. */
    static final class Normal {
        final PerlinNoise first, second;
        final double factor;

        Normal(RandomSource random, int firstOctave, double... amplitudes) {
            DoubleArrayList list = new DoubleArrayList(amplitudes);
            first = PerlinNoise.create(random, firstOctave, list);
            second = PerlinNoise.create(random, firstOctave, list);
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int k = 0; k < amplitudes.length; k++) if (amplitudes[k] != 0) { lo = Math.min(lo, k); hi = Math.max(hi, k); }
            factor = 0.16666666666666666 / (0.1 * (1.0 + 1.0 / (hi - lo + 1)));
        }

        double getValue(double x, double y, double z) {
            return (first.getValue(x, y, z) + second.getValue(x * 1.0181268882175227, y * 1.0181268882175227, z * 1.0181268882175227)) * factor;
        }
    }

    static void report(String name, int n, Mask m) {
        boolean[][] seen = new boolean[n][n];
        int total = 0, patches = 0;
        int[] top = new int[5];
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            if (!m.at(i, j) || seen[j][i]) continue;
            int size = 0;
            ArrayDeque<int[]> q = new ArrayDeque<>();
            q.add(new int[]{i, j});
            seen[j][i] = true;
            while (!q.isEmpty()) {
                int[] p = q.poll();
                size++;
                int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] e : d) {
                    int ii = p[0] + e[0], jj = p[1] + e[1];
                    if (ii < 0 || jj < 0 || ii >= n || jj >= n || seen[jj][ii] || !m.at(ii, jj)) continue;
                    seen[jj][ii] = true;
                    q.add(new int[]{ii, jj});
                }
            }
            total += size;
            patches++;
            for (int k = 0; k < top.length; k++) if (size > top[k]) { System.arraycopy(top, k, top, k + 1, top.length - k - 1); top[k] = size; break; }
        }
        System.out.printf("%-52s %5.1f %% of columns, %5d patches, largest %s columns%n", name, 100.0 * total / (n * n), patches, java.util.Arrays.toString(top));
    }
}
