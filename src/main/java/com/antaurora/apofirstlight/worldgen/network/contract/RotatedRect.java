package com.antaurora.apofirstlight.worldgen.network.contract;

/**
 * A rectangle turned about its centre (contract section 3): centre (cx, cz), half extents along its own axes, heading in
 * degrees (0 = its length along +X east, 90 = along +Z south).
 */
public record RotatedRect(double cx, double cz, double halfLength, double halfWidth, double heading) implements Shape {
    private double[] local(double x, double z) {
        double a = Math.toRadians(heading), c = Math.cos(a), s = Math.sin(a), dx = x - cx, dz = z - cz;
        return new double[]{dx * c + dz * s, -dx * s + dz * c};
    }

    @Override
    public double[] bounds() {
        double a = Math.toRadians(heading), c = Math.abs(Math.cos(a)), s = Math.abs(Math.sin(a));
        double ex = halfLength * c + halfWidth * s, ez = halfLength * s + halfWidth * c;
        return new double[]{cx - ex, cz - ez, cx + ex, cz + ez};
    }

    @Override
    public boolean contains(double x, double z) {
        double[] l = local(x, z);
        return Math.abs(l[0]) <= halfLength && Math.abs(l[1]) <= halfWidth;
    }

    @Override
    public double distance(double x, double z) {
        double[] l = local(x, z);
        double ox = Math.abs(l[0]) - halfLength, oz = Math.abs(l[1]) - halfWidth;
        if (ox <= 0 && oz <= 0) return Math.max(ox, oz);
        return Math.hypot(Math.max(ox, 0), Math.max(oz, 0));
    }

    /** This rectangle grown by {@code m} on every side. */
    public RotatedRect grow(double m) { return new RotatedRect(cx, cz, halfLength + m, halfWidth + m, heading); }
}
