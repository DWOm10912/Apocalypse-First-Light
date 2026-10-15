package com.antaurora.apofirstlight.worldgen.network.contract;

import java.util.Arrays;

/**
 * A centre line with a half width (contract section 3; roads, driveways, corridors): points {x0, z0, x1, z1, ...}.
 * {@link #distance} = the distance to the centre line minus the half width.
 */
public record Polyline(double[] xz, double halfWidth) implements Shape {
    public Polyline {
        if (xz.length < 4 || xz.length % 2 != 0) throw new IllegalArgumentException("a polyline needs 2+ points");
        xz = xz.clone();
    }

    @Override
    public double[] bounds() {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < xz.length; i += 2) {
            b[0] = Math.min(b[0], xz[i] - halfWidth); b[1] = Math.min(b[1], xz[i + 1] - halfWidth);
            b[2] = Math.max(b[2], xz[i] + halfWidth); b[3] = Math.max(b[3], xz[i + 1] + halfWidth);
        }
        return b;
    }

    @Override
    public boolean contains(double x, double z) { return distance(x, z) <= 0; }

    @Override
    public double distance(double x, double z) {
        double d = Double.MAX_VALUE;
        for (int i = 0; i + 3 < xz.length; i += 2) d = Math.min(d, Shape.segment(x, z, xz[i], xz[i + 1], xz[i + 2], xz[i + 3]));
        return d - halfWidth;
    }

    @Override
    public boolean equals(Object o) { return o instanceof Polyline p && halfWidth == p.halfWidth && Arrays.equals(xz, p.xz); }

    @Override
    public int hashCode() { return 31 * Arrays.hashCode(xz) + Double.hashCode(halfWidth); }

    @Override
    public String toString() { return "Polyline" + Arrays.toString(xz) + " +-" + halfWidth; }
}
