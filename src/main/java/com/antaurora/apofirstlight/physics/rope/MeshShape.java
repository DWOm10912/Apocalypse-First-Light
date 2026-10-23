package com.antaurora.apofirstlight.physics.rope;

import java.util.Arrays;

/**
 * Triangles of a model in its own units (a gun bone's mesh, in model blocks relative to the pivot), bucketed in a uniform
 * grid for nearest-point and segment queries (docs/native_guns/attachments/sling_physics_v1.md 4). Immutable once built; queries
 * allocate nothing and share no state, so chunk threads may use one shape together.
 */
public final class MeshShape {
    private final float[] tri, normal;
    private final boolean[] solid;
    private final double cell, ox, oy, oz;
    private final int nx, ny, nz;
    private final int[] cellStart, cellTris;
    public final double minX, minY, minZ, maxX, maxY, maxZ;

    private MeshShape(float[] tri, float[] normal, boolean[] solid, double cell) {
        this.tri = tri; this.normal = normal; this.solid = solid; this.cell = cell;
        int count = solid.length;
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < count * 3; i++)
            for (int a = 0; a < 3; a++) { lo[a] = Math.min(lo[a], tri[i * 3 + a]); hi[a] = Math.max(hi[a], tri[i * 3 + a]); }
        if (count == 0) { Arrays.fill(lo, 0); Arrays.fill(hi, 0); }
        minX = lo[0]; minY = lo[1]; minZ = lo[2]; maxX = hi[0]; maxY = hi[1]; maxZ = hi[2];
        ox = lo[0]; oy = lo[1]; oz = lo[2];
        nx = Math.max(1, (int) Math.ceil((hi[0] - lo[0]) / cell) + 1);
        ny = Math.max(1, (int) Math.ceil((hi[1] - lo[1]) / cell) + 1);
        nz = Math.max(1, (int) Math.ceil((hi[2] - lo[2]) / cell) + 1);
        int[] counts = new int[nx * ny * nz + 1], bucketed = null;
        for (int pass = 0; pass < 2; pass++) {
            int[] fill = pass == 1 ? Arrays.copyOf(counts, counts.length) : null;
            int[] out = pass == 1 ? new int[counts[counts.length - 1]] : null;
            for (int t = 0; t < count; t++) {
                int x0 = ix(Math.min(tri[t * 9], Math.min(tri[t * 9 + 3], tri[t * 9 + 6])), ox, nx);
                int x1 = ix(Math.max(tri[t * 9], Math.max(tri[t * 9 + 3], tri[t * 9 + 6])), ox, nx);
                int y0 = ix(Math.min(tri[t * 9 + 1], Math.min(tri[t * 9 + 4], tri[t * 9 + 7])), oy, ny);
                int y1 = ix(Math.max(tri[t * 9 + 1], Math.max(tri[t * 9 + 4], tri[t * 9 + 7])), oy, ny);
                int z0 = ix(Math.min(tri[t * 9 + 2], Math.min(tri[t * 9 + 5], tri[t * 9 + 8])), oz, nz);
                int z1 = ix(Math.max(tri[t * 9 + 2], Math.max(tri[t * 9 + 5], tri[t * 9 + 8])), oz, nz);
                for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
                    int c = (x * ny + y) * nz + z;
                    if (pass == 0) counts[c + 1]++;
                    else out[fill[c]++] = t;
                }
            }
            if (pass == 0) for (int c = 1; c < counts.length; c++) counts[c] += counts[c - 1];
            else bucketed = out;
        }
        cellStart = counts;
        cellTris = bucketed;
    }

    private int ix(double v, double o, int n) { return Math.max(0, Math.min(n - 1, (int) Math.floor((v - o) / cell))); }

    public int triangles() { return solid.length; }
    public boolean isEmpty() { return solid.length == 0; }

    /**
     * The point of this shape nearest to (px, py, pz) within radius: the point into out[0 .. 2], the triangle's unit
     * normal into out[3 .. 5]. Returns the triangle (negative when none is that close), with bit 30 set when it is part of
     * a closed solid (so a point behind it is inside).
     */
    public int nearest(double px, double py, double pz, double radius, double[] out) {
        if (!Double.isFinite(px + py + pz + radius)) return -1;
        if (px < minX - radius || px > maxX + radius || py < minY - radius || py > maxY + radius || pz < minZ - radius || pz > maxZ + radius)
            return -1;
        int x0 = ix(px - radius, ox, nx), x1 = ix(px + radius, ox, nx);
        int y0 = ix(py - radius, oy, ny), y1 = ix(py + radius, oy, ny);
        int z0 = ix(pz - radius, oz, nz), z1 = ix(pz + radius, oz, nz);
        double best = radius * radius, bx = 0, by = 0, bz = 0;
        int found = -1;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            int c = (x * ny + y) * nz + z;
            for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
                int t = cellTris[k], o = t * 9;
                // closest point on triangle (Ericson, Real-Time Collision Detection 5.1.5)
                double ax = tri[o], ay = tri[o + 1], az = tri[o + 2];
                double abx = tri[o + 3] - ax, aby = tri[o + 4] - ay, abz = tri[o + 5] - az;
                double acx = tri[o + 6] - ax, acy = tri[o + 7] - ay, acz = tri[o + 8] - az;
                double apx = px - ax, apy = py - ay, apz = pz - az;
                double d1 = abx * apx + aby * apy + abz * apz, d2 = acx * apx + acy * apy + acz * apz;
                double qx, qy, qz;
                if (d1 <= 0 && d2 <= 0) { qx = ax; qy = ay; qz = az; }
                else {
                    double bpx = px - tri[o + 3], bpy = py - tri[o + 4], bpz = pz - tri[o + 5];
                    double d3 = abx * bpx + aby * bpy + abz * bpz, d4 = acx * bpx + acy * bpy + acz * bpz;
                    double cpx = px - tri[o + 6], cpy = py - tri[o + 7], cpz = pz - tri[o + 8];
                    double d5 = abx * cpx + aby * cpy + abz * cpz, d6 = acx * cpx + acy * cpy + acz * cpz;
                    double vc = d1 * d4 - d3 * d2, vb = d5 * d2 - d1 * d6, va = d3 * d6 - d5 * d4;
                    if (d3 >= 0 && d4 <= d3) { qx = tri[o + 3]; qy = tri[o + 4]; qz = tri[o + 5]; }
                    else if (d6 >= 0 && d5 <= d6) { qx = tri[o + 6]; qy = tri[o + 7]; qz = tri[o + 8]; }
                    else if (vc <= 0 && d1 >= 0 && d3 <= 0) {
                        double v = d1 / (d1 - d3); qx = ax + abx * v; qy = ay + aby * v; qz = az + abz * v;
                    } else if (vb <= 0 && d2 >= 0 && d6 <= 0) {
                        double v = d2 / (d2 - d6); qx = ax + acx * v; qy = ay + acy * v; qz = az + acz * v;
                    } else if (va <= 0 && d4 - d3 >= 0 && d5 - d6 >= 0) {
                        double v = (d4 - d3) / ((d4 - d3) + (d5 - d6));
                        qx = tri[o + 3] + (tri[o + 6] - tri[o + 3]) * v; qy = tri[o + 4] + (tri[o + 7] - tri[o + 4]) * v;
                        qz = tri[o + 5] + (tri[o + 8] - tri[o + 5]) * v;
                    } else {
                        double den = 1 / (va + vb + vc), v = vb * den, w = vc * den;
                        qx = ax + abx * v + acx * w; qy = ay + aby * v + acy * w; qz = az + abz * v + acz * w;
                    }
                }
                double dx = px - qx, dy = py - qy, dz = pz - qz, d = dx * dx + dy * dy + dz * dz;
                if (d < best) { best = d; bx = qx; by = qy; bz = qz; found = t; }
            }
        }
        if (found < 0) return -1;
        out[0] = bx; out[1] = by; out[2] = bz;
        out[3] = normal[found * 3]; out[4] = normal[found * 3 + 1]; out[5] = normal[found * 3 + 2];
        return solid[found] ? found | 1 << 30 : found;
    }

    /**
     * The first triangle the segment a .. b enters through (it crosses from the front of a closed part's face to its back;
     * an open triangle from either side): the crossing point into out[0 .. 2], the normal facing a into out[3 .. 5], the
     * share of the segment travelled into out[6]. Returns the triangle, or -1 when the segment crosses none.
     */
    public int sweep(double ax, double ay, double az, double bx, double by, double bz, double[] out) {
        if (!Double.isFinite(ax + ay + az + bx + by + bz)) return -1;
        double lx = Math.min(ax, bx), hx = Math.max(ax, bx), ly = Math.min(ay, by), hy = Math.max(ay, by), lz = Math.min(az, bz), hz = Math.max(az, bz);
        if (hx < minX || lx > maxX || hy < minY || ly > maxY || hz < minZ || lz > maxZ) return -1;
        int x0 = ix(lx, ox, nx), x1 = ix(hx, ox, nx), y0 = ix(ly, oy, ny), y1 = ix(hy, oy, ny), z0 = ix(lz, oz, nz), z1 = ix(hz, oz, nz);
        double dx = bx - ax, dy = by - ay, dz = bz - az, best = 1;
        int found = -1;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            int c = (x * ny + y) * nz + z;
            for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
                int t = cellTris[k], o = t * 9;
                double nd = dx * normal[t * 3] + dy * normal[t * 3 + 1] + dz * normal[t * 3 + 2];
                if (solid[t] && nd >= 0) continue;
                // Moller-Trumbore
                double e1x = tri[o + 3] - tri[o], e1y = tri[o + 4] - tri[o + 1], e1z = tri[o + 5] - tri[o + 2];
                double e2x = tri[o + 6] - tri[o], e2y = tri[o + 7] - tri[o + 1], e2z = tri[o + 8] - tri[o + 2];
                double px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
                double det = e1x * px + e1y * py + e1z * pz;
                if (!(Math.abs(det) >= 1e-18)) continue;
                double inv = 1 / det, tx = ax - tri[o], ty = ay - tri[o + 1], tz = az - tri[o + 2];
                double u = (tx * px + ty * py + tz * pz) * inv;
                if (!(u >= 0 && u <= 1)) continue;
                double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
                double v = (dx * qx + dy * qy + dz * qz) * inv;
                if (!(v >= 0 && u + v <= 1)) continue;
                double s = (e2x * qx + e2y * qy + e2z * qz) * inv;
                if (!(s >= 0 && s < best)) continue;
                best = s;
                found = t;
            }
        }
        if (found < 0) return -1;
        double sign = dx * normal[found * 3] + dy * normal[found * 3 + 1] + dz * normal[found * 3 + 2] > 0 ? -1 : 1;
        out[0] = ax + dx * best; out[1] = ay + dy * best; out[2] = az + dz * best;
        out[3] = normal[found * 3] * sign; out[4] = normal[found * 3 + 1] * sign; out[5] = normal[found * 3 + 2] * sign;
        out[6] = best;
        return found;
    }

    public static Builder builder(double cell) { return new Builder(cell); }

    public static final class Builder {
        private final double cell;
        private float[] tri = new float[9 * 64], normal = new float[3 * 64];
        private boolean[] solid = new boolean[64];
        private int count;

        private Builder(double cell) { this.cell = cell; }

        /** One triangle, counter-clockwise seen from outside; degenerate ones are dropped. */
        public Builder triangle(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, boolean closed) {
            double ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-12) return this;
            if (count == solid.length) {
                tri = Arrays.copyOf(tri, tri.length * 2); normal = Arrays.copyOf(normal, normal.length * 2); solid = Arrays.copyOf(solid, solid.length * 2);
            }
            int o = count * 9;
            tri[o] = (float) ax; tri[o + 1] = (float) ay; tri[o + 2] = (float) az;
            tri[o + 3] = (float) bx; tri[o + 4] = (float) by; tri[o + 5] = (float) bz;
            tri[o + 6] = (float) cx; tri[o + 7] = (float) cy; tri[o + 8] = (float) cz;
            normal[count * 3] = (float) (nx / len); normal[count * 3 + 1] = (float) (ny / len); normal[count * 3 + 2] = (float) (nz / len);
            solid[count++] = closed;
            return this;
        }

        public MeshShape build() {
            return new MeshShape(Arrays.copyOf(tri, count * 9), Arrays.copyOf(normal, count * 3), Arrays.copyOf(solid, count), cell);
        }
    }
}
