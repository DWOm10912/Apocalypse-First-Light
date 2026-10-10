package com.antaurora.apofirstlight.worldgen.terrain.v2;

import java.awt.image.BufferedImage;

/**
 * Plan maps of a Terrain V2 surface (2026-10-10): the in-game export (/afl dev terrain_v2 export) and the offline
 * river audit draw with this one renderer, so what the tools check is what the game shows. Pure Java2D raster work
 * (BufferedImage only, no AWT toolkit), safe on a dedicated server.
 * <ul>
 *   <li>relief: hypsometric tint (sea level to 260) with a north-west hillshade from the planned surface;</li>
 *   <li>water: macro sea, drowned valleys (estuaries), tidal marsh with its pools, river water;</li>
 *   <li>at 1-2 m per pixel the rivers are drawn column by column from RiverNetwork.column (the carved channel);
 *   coarser, as lines with their real width (at least one pixel).</li>
 * </ul>
 */
public final class TerrainPlanMaps {
    private TerrainPlanMaps() {
    }

    public static final int SEA = 0xFF1F3F66, ESTUARY = 0xFF2F6496, MARSH = 0xFF5F8F7A, POOL = 0xFF3C78A0,
            RIVER = 0xFF2E7BC8, BANK = 0xFF8C7A5A;

    /** Map of the window [x0, x0 + w * mpp) x [z0, z0 + h * mpp), north up. */
    public static BufferedImage render(TerrainPlanSurface s, double x0, double z0, int w, int h, double mpp) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        boolean fine = mpp <= 2.0;
        double[] row0 = new double[w + 1], row1 = new double[w + 1];
        for (int px = 0; px <= w; px++) row0[px] = height(s, x0 + px * mpp, z0, fine);
        RiverNetwork.Column col = new RiverNetwork.Column();
        for (int py = 0; py < h; py++) {
            double z = z0 + (py + 1) * mpp;
            for (int px = 0; px <= w; px++) row1[px] = height(s, x0 + px * mpp, z, fine);
            for (int px = 0; px < w; px++) {
                double x = x0 + (px + 0.5) * mpp, zz = z0 + (py + 0.5) * mpp;
                int wc = s.waterClass(x, zz);
                double hh = row0[px];
                int c;
                // open water wherever the ground is under Y63 in the sea, a drowned valley or the low shore beside them
                // (what the game fills to sea level), so the coast follows the ground, not the plan's 16 m cells
                if (hh < 63 && s.seaFloodAt(x, zz, hh)) c = wc == 1 ? SEA : ESTUARY;
                else {
                    double gx = (row0[px + 1] - row0[px] + row1[px + 1] - row1[px]) / (2 * mpp);
                    double gz = (row1[px] - row0[px] + row1[px + 1] - row0[px + 1]) / (2 * mpp);
                    c = shade(tint(hh), gx, gz, mpp);
                    if (wc == 3) c = mixColour(c, MARSH, 0.65);
                    if (fine) {
                        int bx = (int) Math.floor(x), bz = (int) Math.floor(zz);
                        if (s.rivers != null) {
                            s.rivers.column(bx, bz, col);
                            if (col.kind == RiverNetwork.Column.RIVER) c = RIVER;
                        }
                        if (col.kind != RiverNetwork.Column.RIVER && s.poolAt(bx, bz) && Math.ceil(s.heightAt(bx, bz)) - 1 == 63) c = POOL;
                    }
                }
                img.setRGB(px, py, c);
            }
            double[] t = row0; row0 = row1; row1 = t;
        }
        if (!fine && s.rivers != null) drawRivers(img, s.rivers, x0, z0, mpp);
        return img;
    }

    /** Ecology map of the window: EcologyBiomes colours with relief shading; rivers as in {@link #render}. */
    public static BufferedImage renderEcology(TerrainPlanSurface s, double x0, double z0, int w, int h, double mpp) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        boolean fine = mpp <= 2.0;
        double[] row0 = new double[w + 1], row1 = new double[w + 1];
        for (int px = 0; px <= w; px++) row0[px] = height(s, x0 + px * mpp, z0, fine);
        RiverNetwork.Column col = new RiverNetwork.Column();
        for (int py = 0; py < h; py++) {
            for (int px = 0; px <= w; px++) row1[px] = height(s, x0 + px * mpp, z0 + (py + 1) * mpp, fine);
            for (int px = 0; px < w; px++) {
                double x = x0 + (px + 0.5) * mpp, z = z0 + (py + 0.5) * mpp;
                int b = EcologyBiomes.biomeAt(s, x, z);
                int c = 0xFF000000 | EcologyBiomes.COLOURS[b];
                if (b != EcologyBiomes.SEA) {
                    double gx = (row0[px + 1] - row0[px] + row1[px + 1] - row1[px]) / (2 * mpp);
                    double gz = (row1[px] - row0[px] + row1[px + 1] - row0[px + 1]) / (2 * mpp);
                    c = shade(c, gx, gz, mpp);
                    if (fine && s.rivers != null) {
                        s.rivers.column((int) Math.floor(x), (int) Math.floor(z), col);
                        if (col.kind == RiverNetwork.Column.RIVER) c = RIVER;
                    }
                }
                img.setRGB(px, py, c);
            }
            double[] t = row0; row0 = row1; row1 = t;
        }
        if (!fine && s.rivers != null) drawRivers(img, s.rivers, x0, z0, mpp);
        return img;
    }

    private static double height(TerrainPlanSurface s, double x, double z, boolean fine) {
        return fine ? s.heightAt(x, z) : s.baseHeightAt(x, z);
    }

    static void drawRivers(BufferedImage img, RiverNetwork r, double x0, double z0, double mpp) {
        int w = img.getWidth(), h = img.getHeight();
        for (int line = 0; line < r.lines(); line++) for (int i = r.lineStart(line); i < r.lineEnd(line) - 1; i++) {
            double ax = (r.vertexX(i) - x0) / mpp, az = (r.vertexZ(i) - z0) / mpp;
            double bx = (r.vertexX(i + 1) - x0) / mpp, bz = (r.vertexZ(i + 1) - z0) / mpp;
            if (Math.max(ax, bx) < -4 || Math.min(ax, bx) > w + 4 || Math.max(az, bz) < -4 || Math.min(az, bz) > h + 4) continue;
            double rad = Math.max(0.5, r.vertexWidth(i) / 2 / mpp);
            int steps = (int) Math.ceil(Math.hypot(bx - ax, bz - az) * 2) + 1;
            for (int k = 0; k <= steps; k++) {
                double t = (double) k / steps, cx = ax + (bx - ax) * t, cz = az + (bz - az) * t;
                for (int yy = (int) Math.floor(cz - rad); yy <= (int) Math.ceil(cz + rad); yy++)
                    for (int xx = (int) Math.floor(cx - rad); xx <= (int) Math.ceil(cx + rad); xx++) {
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                        double dx = xx + 0.5 - cx, dz = yy + 0.5 - cz;
                        if (dx * dx + dz * dz <= rad * rad + 0.25) img.setRGB(xx, yy, RIVER);
                    }
            }
        }
    }

    /** A labelled marker (a ring and a dot) at world position (x, z). */
    public static void mark(BufferedImage img, double x0, double z0, double mpp, double x, double z, int colour) {
        int cx = (int) Math.round((x - x0) / mpp), cz = (int) Math.round((z - z0) / mpp);
        for (int dz = -7; dz <= 7; dz++) for (int dx = -7; dx <= 7; dx++) {
            int xx = cx + dx, zz = cz + dz;
            if (xx < 0 || zz < 0 || xx >= img.getWidth() || zz >= img.getHeight()) continue;
            double d = Math.sqrt(dx * dx + dz * dz);
            if ((d >= 5 && d <= 7) || d <= 1.5) img.setRGB(xx, zz, colour);
        }
    }

    static int tint(double y) {
        double[] stops = {63, 66, 72, 80, 95, 120, 160, 210, 260};
        int[] cols = {0xFF7FA36A, 0xFF8DB06F, 0xFFA6BD77, 0xFFC2C682, 0xFFD2C38A, 0xFFC4A578, 0xFFA88462, 0xFF8E7A6A, 0xFFE0DCD6};
        if (y <= stops[0]) return cols[0];
        for (int k = 1; k < stops.length; k++)
            if (y <= stops[k]) return mixColour(cols[k - 1], cols[k], (y - stops[k - 1]) / (stops[k] - stops[k - 1]));
        return cols[cols.length - 1];
    }

    static int shade(int c, double gx, double gz, double mpp) {
        double ex = Math.max(1, Math.min(4, 4 / Math.sqrt(mpp)));           // stronger relief at coarse scales
        double nx = -gx * ex, nz = -gz * ex, ny = 1, nl = Math.sqrt(nx * nx + nz * nz + ny * ny);
        double lx = -0.5, lz = -0.5, ly = 0.7071, ll = Math.sqrt(lx * lx + lz * lz + ly * ly);
        double d = (nx * lx + nz * lz + ny * ly) / (nl * ll);
        double f = Math.max(0.45, Math.min(1.25, 0.35 + 0.85 * d));
        int r = (int) Math.min(255, ((c >> 16) & 255) * f), g = (int) Math.min(255, ((c >> 8) & 255) * f), b = (int) Math.min(255, (c & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int mixColour(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
