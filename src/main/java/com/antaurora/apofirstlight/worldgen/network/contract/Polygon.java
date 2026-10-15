package com.antaurora.apofirstlight.worldgen.network.contract;

import java.util.Arrays;

/** A simple polygon (contract section 3): vertices {x0, z0, x1, z1, ...}, either winding, not self-intersecting. */
public record Polygon(double[] xz) implements Shape {
    public Polygon {
        if (xz.length < 6 || xz.length % 2 != 0) throw new IllegalArgumentException("a polygon needs 3+ vertices");
        xz = xz.clone();
    }

    public int vertices() { return xz.length / 2; }

    @Override
    public double[] bounds() {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < xz.length; i += 2) {
            b[0] = Math.min(b[0], xz[i]); b[1] = Math.min(b[1], xz[i + 1]);
            b[2] = Math.max(b[2], xz[i]); b[3] = Math.max(b[3], xz[i + 1]);
        }
        return b;
    }

    @Override
    public boolean contains(double x, double z) {
        boolean in = false;
        for (int i = 0, n = vertices(), j = n - 1; i < n; j = i++) {
            double xi = xz[2 * i], zi = xz[2 * i + 1], xj = xz[2 * j], zj = xz[2 * j + 1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
        }
        return in;
    }

    @Override
    public double distance(double x, double z) {
        double d = Double.MAX_VALUE;
        for (int i = 0, n = vertices(), j = n - 1; i < n; j = i++)
            d = Math.min(d, Shape.segment(x, z, xz[2 * j], xz[2 * j + 1], xz[2 * i], xz[2 * i + 1]));
        return contains(x, z) ? -d : d;
    }

    @Override
    public boolean equals(Object o) { return o instanceof Polygon p && Arrays.equals(xz, p.xz); }

    @Override
    public int hashCode() { return Arrays.hashCode(xz); }

    @Override
    public String toString() { return "Polygon" + Arrays.toString(xz); }
}
