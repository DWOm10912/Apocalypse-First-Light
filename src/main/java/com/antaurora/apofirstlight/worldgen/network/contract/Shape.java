package com.antaurora.apofirstlight.worldgen.network.contract;

/**
 * A horizontal footprint (Main ↔ C3 interface contract V1, docs/worldgen/main_c3_interface_contract_v1.md section 3): metres
 * (= blocks), x east, z south. Pure Java, no Minecraft types, deterministic. Three kinds: {@link Polygon},
 * {@link RotatedRect}, {@link Polyline} (a centre line with a half width, for roads and driveways).
 * <p>
 * {@link #distance}: outside positive (the Euclidean distance to the shape), inside 0 or negative (minus the distance to
 * the edge), so {@code distance(x, z) <= 0} is {@link #contains}.
 */
public interface Shape {
    /** {minX, minZ, maxX, maxZ}. */
    double[] bounds();

    boolean contains(double x, double z);

    /** Signed: positive outside (distance to the shape), 0 or negative inside (minus the distance to the edge). */
    double distance(double x, double z);

    /** Distance from (x, z) to the segment a..b. */
    static double segment(double x, double z, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
        double t = l2 > 1e-12 ? Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2)) : 0;
        return Math.hypot(ax + dx * t - x, az + dz * t - z);
    }
}
