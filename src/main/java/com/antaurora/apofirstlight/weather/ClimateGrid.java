package com.antaurora.apofirstlight.weather;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.Physiography;

/**
 * Regional weather V1 (docs/gameplay/regional_weather_v1_plan.md 3): the climate the weather reads, on a coarse grid
 * (temperature and moisture 0..1, and the share of each landform, in {@link Physiography} order). Built once from the
 * federal map on the server and sent to the client as it is, so both run {@link RegionalWeather} on the same numbers.
 * Outside the grid the edge values hold.
 */
public final class ClimateGrid {
    /** Values per grid point: temperature, moisture, the six landforms, then land (1) or sea (0). */
    public static final int STRIDE = 2 + 6 + 1;
    public static final int LAND = 8;
    /** Grid spacing (m) and how far past the land the grid reaches. */
    private static final double STEP = 512, MARGIN = 4096;

    public final double originX, originZ, step;
    public final int nx, nz;
    final float[] values;

    public ClimateGrid(double originX, double originZ, double step, int nx, int nz, float[] values) {
        if (values.length != nx * nz * STRIDE) throw new IllegalArgumentException("climate grid size");
        this.originX = originX;
        this.originZ = originZ;
        this.step = step;
        this.nx = nx;
        this.nz = nz;
        this.values = values;
    }

    /** The federal country's climate. */
    public static ClimateGrid federal(FederalMap map) {
        FederalMap.Bounds b = map.landBounds();
        double ox = Math.floor((b.minX() - MARGIN) / STEP) * STEP, oz = Math.floor((b.minZ() - MARGIN) / STEP) * STEP;
        int nx = (int) Math.ceil((b.maxX() + MARGIN - ox) / STEP) + 1, nz = (int) Math.ceil((b.maxZ() + MARGIN - oz) / STEP) + 1;
        float[] v = new float[nx * nz * STRIDE];
        double[] w = new double[Physiography.values().length];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double x = ox + i * STEP, z = oz + j * STEP;
                int k = (j * nx + i) * STRIDE;
                v[k] = (float) map.temperature(x, z);
                v[k + 1] = (float) map.moisture(x, z);
                map.physiography(x, z, w);
                for (int p = 0; p < w.length; p++) v[k + 2 + p] = (float) w[p];
                v[k + LAND] = map.isLand(x, z) ? 1 : 0;
            }
        return new ClimateGrid(ox, oz, STEP, nx, nz, v);
    }

    /** One mild climate everywhere (worlds without the federal map): middling, all plains. */
    public static ClimateGrid uniform() {
        float[] v = new float[STRIDE];
        v[0] = 0.5F;
        v[1] = 0.5F;
        v[2 + Physiography.PLAINS.ordinal()] = 1;
        v[LAND] = 1;
        return new ClimateGrid(0, 0, STEP, 1, 1, v);
    }

    /** The climate at (x, z) into out[STRIDE], bilinear between grid points. */
    public void at(double x, double z, double[] out) {
        double fx = Math.max(0, Math.min(nx - 1, (x - originX) / step)), fz = Math.max(0, Math.min(nz - 1, (z - originZ) / step));
        int i = Math.min(nx - 2, (int) fx), j = Math.min(nz - 2, (int) fz);
        if (nx == 1 || nz == 1) {
            for (int k = 0; k < STRIDE; k++) out[k] = values[k];
            return;
        }
        double u = fx - i, w = fz - j;
        int a = (j * nx + i) * STRIDE, b = a + STRIDE, c = a + nx * STRIDE, d = c + STRIDE;
        for (int k = 0; k < STRIDE; k++)
            out[k] = (values[a + k] * (1 - u) + values[b + k] * u) * (1 - w) + (values[c + k] * (1 - u) + values[d + k] * u) * w;
    }

    /** The raw values. */
    public float[] values() {
        return values;
    }

    /**
     * The same grid at the network's precision (every value 0..1 in 1/255 steps): the server runs on this one, so the
     * clients it is sent to get the very same numbers.
     */
    public ClimateGrid quantized() {
        float[] v = new float[values.length];
        for (int i = 0; i < v.length; i++) v[i] = Math.round(Math.max(0, Math.min(1, values[i])) * 255) / 255F;
        return new ClimateGrid(originX, originZ, step, nx, nz, v);
    }

    public byte[] bytes() {
        byte[] b = new byte[values.length];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Math.round(Math.max(0, Math.min(1, values[i])) * 255);
        return b;
    }

    public static ClimateGrid of(double originX, double originZ, double step, int nx, int nz, byte[] b) {
        float[] v = new float[b.length];
        for (int i = 0; i < v.length; i++) v[i] = (b[i] & 255) / 255F;
        return new ClimateGrid(originX, originZ, step, nx, nz, v);
    }
}
