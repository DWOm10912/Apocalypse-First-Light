package com.antaurora.apofirstlight.liquid;

import com.antaurora.apofirstlight.liquid.ground.GroundMaterialV0;
import com.antaurora.apofirstlight.liquid.ground.GroundViewV0;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A ground for the offline liquid check, with C's GroundView V0 signature and meaning (6.2 / 6.3): every 1/16 m x 1/16 m
 * sub-column is solid below its own top (whole slabs), plus solid boxes in 1/16 m units; so openArea and openEdge are
 * exact counts (the integral means, nothing to round). Outside its columns and in unloaded chunks: UNKNOWN.
 */
final class SyntheticGround implements GroundViewV0 {
    interface Height {
        /** Ground height at a point, metres. */
        double at(double x, double z);
    }

    final int minX, minZ, sizeX, sizeZ;
    private final int stride;
    /** Per sub-column: solid below this absolute slab. */
    private final int[] top;
    /** Solid boxes, sub-units: x0, y0 (slab), z0, x1, y1, z1, exclusive ends. */
    private final List<int[]> boxes = new ArrayList<>();
    private final Set<Long> unloaded = new HashSet<>();
    GroundMaterialV0 material = new GroundMaterialV0("soil", GroundMaterialV0.Kind.SOIL, 35, 63);

    SyntheticGround(int minX, int minZ, int sizeX, int sizeZ, Height h) {
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        stride = sizeZ * 16;
        top = new int[sizeX * 16 * stride];
        shape(minX, minZ, minX + sizeX, minZ + sizeZ, (x, z, old) -> h.at(x, z));
    }

    interface Reshape {
        /** The new height at sub-column centre (x, z), metres, from the old one. */
        double at(double x, double z, double old);
    }

    /** Reshape the sub-columns whose centres lie in [x0, x1) x [z0, z1) metres. */
    void shape(double x0, double z0, double x1, double z1, Reshape f) {
        for (int sx = 0; sx < sizeX * 16; sx++) {
            double x = minX + (sx + 0.5) / 16;
            if (x < x0 || x >= x1) continue;
            for (int sz = 0; sz < sizeZ * 16; sz++) {
                double z = minZ + (sz + 0.5) / 16;
                if (z < z0 || z >= z1) continue;
                int i = sx * stride + sz;
                top[i] = (int) Math.round(f.at(x, z, top[i] / 16.0) * 16);
            }
        }
    }

    /** Lower the ground in the rectangle to at most h. */
    void dig(double x0, double z0, double x1, double z1, double h) {
        shape(x0, z0, x1, z1, (x, z, old) -> Math.min(old, h));
    }

    /** Set the ground in the rectangle to h. */
    void set(double x0, double z0, double x1, double z1, double h) {
        shape(x0, z0, x1, z1, (x, z, old) -> h);
    }

    /** A solid box, metres. */
    void box(double x0, double y0, double z0, double x1, double y1, double z1) {
        boxes.add(new int[]{(int) Math.round((x0 - minX) * 16), (int) Math.round(y0 * 16), (int) Math.round((z0 - minZ) * 16),
                (int) Math.round((x1 - minX) * 16), (int) Math.round(y1 * 16), (int) Math.round((z1 - minZ) * 16)});
    }

    void unload(int chunkX, int chunkZ) {
        unloaded.add(LiquidColumn.chunkKey(chunkX, chunkZ));
    }

    void load(int chunkX, int chunkZ) {
        unloaded.remove(LiquidColumn.chunkKey(chunkX, chunkZ));
    }

    private boolean known(int x, int z) {
        return x >= minX && z >= minZ && x < minX + sizeX && z < minZ + sizeZ && !unloaded.contains(LiquidColumn.chunkKey(x >> 4, z >> 4));
    }

    /** Is sub-cell (sx, slab, sz) solid? sx, sz relative to minX * 16, minZ * 16. */
    private boolean solid(int sx, int slab, int sz) {
        if (slab < top[sx * stride + sz]) return true;
        for (int[] b : boxes)
            if (sx >= b[0] && sx < b[3] && slab >= b[1] && slab < b[4] && sz >= b[2] && sz < b[5]) return true;
        return false;
    }

    @Override
    public void openArea(int x, int z, int y0, int y1, short[] out) {
        int n = (y1 - y0) * 16;
        if (!known(x, z)) {
            Arrays.fill(out, 0, n, (short) UNKNOWN);
            return;
        }
        int base = y0 * 16, sx0 = (x - minX) * 16, sz0 = (z - minZ) * 16;
        int[] diff = new int[n + 1];
        List<int[]> boxed = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            for (int k = 0; k < 16; k++) {
                int sx = sx0 + i, sz = sz0 + k;
                boolean inBox = false;
                for (int[] b : boxes) inBox |= sx >= b[0] && sx < b[3] && sz >= b[2] && sz < b[5];
                if (inBox) {
                    boxed.add(new int[]{sx, sz});
                    continue;
                }
                int from = Math.max(0, top[sx * stride + sz] - base);
                if (from < n) diff[from]++;
            }
        }
        int open = 0;
        for (int s = 0; s < n; s++) {
            open += diff[s];
            int v = open;
            for (int[] p : boxed) if (!solid(p[0], base + s, p[1])) v++;
            out[s] = (short) v;
        }
    }

    @Override
    public void openEdge(int x, int z, Direction dir, int y0, int y1, byte[] out) {
        switch (dir) {
            case WEST -> { openEdge(x - 1, z, Direction.EAST, y0, y1, out); return; }
            case NORTH -> { openEdge(x, z - 1, Direction.SOUTH, y0, y1, out); return; }
            default -> { }
        }
        int n = (y1 - y0) * 16;
        boolean east = dir == Direction.EAST;
        int nx = east ? x + 1 : x, nz = east ? z : z + 1;
        if (!known(x, z) || !known(nx, nz)) {
            Arrays.fill(out, 0, n, (byte) UNKNOWN);
            return;
        }
        int base = y0 * 16, sx0 = (x - minX) * 16, sz0 = (z - minZ) * 16;
        for (int s = 0; s < n; s++) {
            int w = 0;
            for (int k = 0; k < 16; k++) {
                // the sub-cells on both sides of the edge
                int ax = east ? sx0 + 15 : sx0 + k, az = east ? sz0 + k : sz0 + 15;
                int bx = east ? sx0 + 16 : sx0 + k, bz = east ? sz0 + k : sz0 + 16;
                if (!solid(ax, base + s, az) && !solid(bx, base + s, bz)) w++;
            }
            out[s] = (byte) w;
        }
    }

    @Override
    public double surfaceBelow(double px, double y, double pz) {
        int x = (int) Math.floor(px), z = (int) Math.floor(pz);
        if (!known(x, z)) return Double.NaN;
        int sx = (int) Math.floor((px - minX) * 16), sz = (int) Math.floor((pz - minZ) * 16);
        int s = (int) Math.floor(y * 16);
        if (solid(sx, s, sz)) {
            // inside solid: the top of that solid run (C 2026-10-24)
            while (solid(sx, s + 1, sz)) s++;
            return (s + 1) / 16.0;
        }
        for (; s >= top[sx * stride + sz]; s--) if (solid(sx, s, sz)) return (s + 1) / 16.0;
        return top[sx * stride + sz] / 16.0;
    }

    @Override
    public GroundMaterialV0 materialAt(int x, int y, int z) {
        return known(x, z) ? material : GroundMaterialV0.NONE;
    }

    @Override
    public void surfaceGrid(int chunkX, int chunkZ, int yFrom, int[] out) {
        for (int i = 0; i <= 64; i++) {
            for (int k = 0; k <= 64; k++) {
                double px = chunkX * 16 + i * 0.25, pz = chunkZ * 16 + k * 0.25;
                int x = (int) Math.floor(Math.min(px, chunkX * 16 + 15.999)), z = (int) Math.floor(Math.min(pz, chunkZ * 16 + 15.999));
                double s = known((int) Math.floor(px), (int) Math.floor(pz)) ? surfaceBelow(Math.min(px, minX + sizeX - 1e-6), yFrom, Math.min(pz, minZ + sizeZ - 1e-6)) : Double.NaN;
                out[i * 65 + k] = !known(x, z) ? UNKNOWN_HEIGHT : Double.isNaN(s) ? NO_GROUND : (int) Math.round(s * 256);
            }
        }
    }
}
