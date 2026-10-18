package com.antaurora.apofirstlight.client.rain;

/**
 * Where the panes of glass lie in the shader pack's glass water map (dynamic/afl_glass_water, docs/rendering/rain_v1.md
 * section 6): {@link #W} x {@link #H}, the nearest truck in the top {@link #W} x {@link #W} square (slot 0), the second in
 * the {@link #W} x {@link #H} - {@link #W} strip below at half its texels per metre or less (slot 1). Each pane's glass range
 * (TruckGlass.Pane uMin..uMax, s0..s1, each widened by {@link #MARGIN}: the trucks texture's t4) is a rectangle with
 * {@link #PAD} texels of empty map round it (so the mip levels do not run into the next pane), u along x and s along y,
 * shelf-packed tallest first at the most texels per metre that fit (at most {@link #MAX_DENSITY}).
 */
final class GlassAtlas {
    static final int W = 2048, H = 3072, SLOTS = 2, PAD = 16, LEVELS = 5;
    static final double MARGIN = 0.01, MAX_DENSITY = 1250;
    /** Per slot and pane: the glass range's rectangle in texels (x, y, w, h) and as texture coordinates (x0, y0, x1, y1). */
    static final int[][][] TEXELS = new int[SLOTS][TruckGlass.PANES.length][4];
    static final float[][][] RECT = new float[SLOTS][TruckGlass.PANES.length][4];
    /** Texels per metre of each slot. */
    static final double[] DENSITY = new double[SLOTS];

    static {
        DENSITY[0] = pack(0, 0, W, W, MAX_DENSITY);
        DENSITY[1] = pack(1, W, W, H - W, DENSITY[0] / 2);
    }

    private GlassAtlas() {
    }

    static double u0(TruckGlass.Pane p) { return p.uMin - MARGIN; }
    static double u1(TruckGlass.Pane p) { return p.uMax + MARGIN; }
    static double s0(TruckGlass.Pane p) { return p.s0 - MARGIN; }
    static double s1(TruckGlass.Pane p) { return p.s1 + MARGIN; }

    /** The most texels per metre (from max down, 1 % steps) at which the panes fit the slot's area at (0, y); its layout kept. */
    private static double pack(int slot, int y, int w, int h, double max) {
        for (double k = max; k > 50; k *= 0.99)
            if (fits(slot, y, w, h, k)) return k;
        throw new IllegalStateException("glass atlas: the panes do not fit");
    }

    private static boolean fits(int slot, int y0, int w, int h, double k) {
        int n = TruckGlass.PANES.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(s1(TruckGlass.PANES[b]) - s0(TruckGlass.PANES[b]), s1(TruckGlass.PANES[a]) - s0(TruckGlass.PANES[a])));
        int x = 0, y = 0, shelf = 0;
        for (int i : order) {
            TruckGlass.Pane p = TruckGlass.PANES[i];
            int pw = (int) Math.ceil((u1(p) - u0(p)) * k), ph = (int) Math.ceil((s1(p) - s0(p)) * k);
            int bw = pw + 2 * PAD, bh = ph + 2 * PAD;
            if (bw > w) return false;
            if (x + bw > w) {
                x = 0;
                y += shelf;
                shelf = 0;
            }
            if (y + bh > h) return false;
            int[] t = TEXELS[slot][i];
            t[0] = x + PAD;
            t[1] = y0 + y + PAD;
            t[2] = pw;
            t[3] = ph;
            float[] r = RECT[slot][i];
            r[0] = (float) t[0] / W;
            r[1] = (float) t[1] / H;
            r[2] = (float) (t[0] + pw) / W;
            r[3] = (float) (t[1] + ph) / H;
            x += bw;
            shelf = Math.max(shelf, bh);
        }
        return true;
    }
}
