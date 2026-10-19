package com.antaurora.apofirstlight.client.lightning;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightning V1 (docs/rendering/lightning_v1.md): one flash's channel, made the way the concept was
 * (design/rendering/lightning/sim-lightning.mjs) and scaled to its height. Straight pieces about 1/90 of the height long,
 * each bent |N(16, 11.5)| deg off the leader's heading, which itself wanders and is held downward; three finer wiggles
 * inside each piece. The stepped leader forks as it comes down, most often high up: branches leave downward, thinner and
 * dimmer, most die out in the air; now and then one reaches the ground too. Points are relative to the strike point (the
 * bolt entity), y up; the main strand ends at the strike point.
 */
public final class LightningChannel {
    /** One polyline: the main channel (the return strokes' path) or a branch, with each point's width and brightness share. */
    public static final class Strand {
        public final boolean main;
        private float[] xyz = new float[96], width = new float[32], bright = new float[32];
        private int count;

        Strand(boolean main) {
            this.main = main;
        }

        void add(double x, double y, double z, float w, float k) {
            if (count == width.length) {
                xyz = java.util.Arrays.copyOf(xyz, count * 6);
                width = java.util.Arrays.copyOf(width, count * 2);
                bright = java.util.Arrays.copyOf(bright, count * 2);
            }
            xyz[count * 3] = (float) x;
            xyz[count * 3 + 1] = (float) y;
            xyz[count * 3 + 2] = (float) z;
            width[count] = w;
            bright[count] = k;
            count++;
        }

        public int count() {
            return count;
        }

        public float x(int i) {
            return xyz[i * 3];
        }

        public float y(int i) {
            return xyz[i * 3 + 1];
        }

        public float z(int i) {
            return xyz[i * 3 + 2];
        }

        public float width(int i) {
            return width[i];
        }

        public float bright(int i) {
            return bright[i];
        }
    }

    private static final double DEG = Math.PI / 180;
    /** The concept's channel was 1500 m tall with 17 m pieces; lengths here scale by height / this. */
    private static final double CONCEPT_HEIGHT = 1500;

    public final List<Strand> strands = new ArrayList<>();
    /** The channel's top (relative to the strike point): where it leaves the cloud. */
    public final double topX, topY, topZ;

    private final RandomSource r;
    private final double top, scale;
    private int groundBranches;

    public LightningChannel(long seed, double height) {
        r = RandomSource.create(seed);
        top = height;
        scale = height / CONCEPT_HEIGHT;
        groundBranches = r.nextDouble() < 0.4 ? 1 : 0;
        double sx = (r.nextDouble() - 0.5) * 0.5 * top, sz = (r.nextDouble() - 0.5) * 0.3 * top;
        double[] start = {sx, top, sz}, heading = norm(new double[]{-sx, -top, -sz});
        leader(start, heading, true, 1, 1, top * 4, 0, new double[]{0, 0, 0});
        // the main strand ends where the leader met the ground; shear the whole flash so that is the strike point (the top
        // stays where it is)
        Strand main = strands.get(0);
        double ex = main.x(main.count() - 1), ez = main.z(main.count() - 1);
        for (Strand s : strands) {
            for (int i = 0; i < s.count; i++) {
                double f = 1 - Math.max(0, s.xyz[i * 3 + 1]) / top;
                s.xyz[i * 3] -= (float) (ex * f);
                s.xyz[i * 3 + 2] -= (float) (ez * f);
            }
        }
        main.xyz[(main.count - 1) * 3 + 1] = 0;
        topX = main.x(0);
        topY = main.y(0);
        topZ = main.z(0);
    }

    private void leader(double[] p, double[] heading, boolean main, double width, double bright, double budget, int depth,
                        double[] target) {
        Strand strand = new Strand(main);
        strands.add(strand);
        strand.add(p[0], p[1], p[2], (float) width, (float) bright);
        double travelled = 0;
        while (travelled < budget && p[1] > 0.01 && strand.count < 4000) {
            double step = 17 * scale * Math.exp(0.5 * r.nextGaussian());
            // the heading wanders over many pieces, held downward; the main one drifts toward its ground point
            heading = bend(heading, Math.abs(r.nextGaussian()) * 9 * DEG);
            if (target != null) heading = norm(add(heading, mul(norm(sub(target, p)), 0.06)));
            if (heading[1] > -0.35) heading = norm(new double[]{heading[0], -0.35, heading[2]});
            double[] d = bend(heading, Math.abs(16 + 11.5 * r.nextGaussian()) * DEG);
            if (d[1] > 0.1) d = norm(new double[]{d[0], 0.1, d[2]});
            double[] q = add(p, mul(d, step));
            if (q[1] < 0) q = add(p, mul(d, p[1] / -d[1]));
            // finer wiggle inside the piece
            double[][] tb = basis(d);
            for (int k = 1; k <= 3; k++) {
                double[] m = add(p, mul(sub(q, p), k / 3.0));
                if (k < 3) m = add(m, add(mul(tb[0], r.nextGaussian() * 0.07 * step), mul(tb[1], r.nextGaussian() * 0.07 * step)));
                double taper = main ? 1 : Math.max(0.2, 1 - (travelled + step * k / 3) / budget);
                strand.add(m[0], m[1], m[2], (float) (width * (0.4 + 0.6 * taper)), (float) (bright * (main ? 1 : 0.35 + 0.65 * taper)));
            }
            travelled += step;
            p = q;
            // forks, mostly in the upper part
            double high = p[1] / top;
            double chance = main ? 0.03 + 0.11 * high * high : 0.05 / depth;
            if (depth < 3 && p[1] > 40 * scale && r.nextDouble() < chance) {
                boolean toGround = main && groundBranches > 0 && high < 0.6 && r.nextDouble() < 0.5;
                if (toGround) groundBranches--;
                double[] off = bend(d, (20 + 35 * r.nextDouble()) * DEG);
                off[1] = Math.min(off[1], -0.2);
                double a = r.nextDouble(), b = r.nextDouble();
                // the main channel's forks run up to 1.2 times their height (held downward they mostly die out in the air),
                // theirs are shorter (the concept's budgets)
                double branchBudget = toGround ? p[1] * 3
                        : depth == 0 ? p[1] * 1.2 : Math.min(p[1] * 1.2, top * (0.05 + 0.3 * a * b) / Math.sqrt(depth));
                double[] branchTarget = toGround ? new double[]{p[0] + (r.nextDouble() - 0.5) * p[1], 0, p[2] + (r.nextDouble() - 0.5) * p[1]} : null;
                leader(p.clone(), norm(off), false, (main ? 0.5 : 0.7) * width, (main ? 0.5 : 0.7) * bright, branchBudget, depth + 1, branchTarget);
            }
        }
    }

    // ---- vectors ----
    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] mul(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static double[] norm(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-12 ? new double[]{0, -1, 0} : mul(a, 1 / l);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Two unit vectors square to d. */
    private static double[][] basis(double[] d) {
        double[] t = norm(cross(d, Math.abs(d[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0}));
        return new double[][]{t, cross(d, t)};
    }

    /** d turned by angle off itself, toward a random side. */
    private double[] bend(double[] d, double angle) {
        double[][] tb = basis(d);
        double az = 2 * Math.PI * r.nextDouble();
        double[] side = add(mul(tb[0], Math.cos(az)), mul(tb[1], Math.sin(az)));
        return norm(add(mul(d, Math.cos(angle)), mul(side, Math.sin(angle))));
    }
}
