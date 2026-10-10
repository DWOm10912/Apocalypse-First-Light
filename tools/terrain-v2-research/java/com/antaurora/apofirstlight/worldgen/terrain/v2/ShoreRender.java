package com.antaurora.apofirstlight.worldgen.terrain.v2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Simulated block-top render of a window (2026-10-10), drawn like tools/terrain-v2-research/save_render.py so a fix can
 * be compared with what a save generated: ground top = ceil(h) - 1, sea-level water where the game fills it (sea and
 * estuary cells, the low shore within 48 m), then RiverCarver's column rule; steps on a plan-cell (= chunk) border red.
 * <pre>java ShoreRender SEED X Z HALF OUT.png</pre>
 */
public final class ShoreRender {
    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        int cx = Integer.parseInt(args[1]), cz = Integer.parseInt(args[2]), half = Integer.parseInt(args[3]);
        TerrainPlanSurface s = new TerrainPlanV2(seed).toSurface();
        int w = 2 * half + 2;
        int[][] g = new int[w][w], wt = new int[w][w];
        RiverNetwork.Column col = new RiverNetwork.Column();
        RiverCarver.Edit e = new RiverCarver.Edit();
        int borderSteps = 0, steps = 0;
        for (int j = 0; j < w; j++) for (int i = 0; i < w; i++) {
            int x = cx - half - 1 + i, z = cz - half - 1 + j;
            double h = s.heightAt(x, z);
            int top = (int) Math.ceil(h) - 1, wc = s.waterClass(x, z);
            int water = Integer.MIN_VALUE;
            if (top < 62 && s.seaFloodAt(x, z, h)) water = 62;
            RiverCarver.plan(s, x, z, top, col, e);
            if (e.kind != RiverCarver.NONE) { top = e.ground; water = e.water; }
            g[j][i] = top;
            wt[j][i] = water;
        }
        // exposed sea-level water: a water column next to a dry column whose ground is under the water's top
        int exposed = 0;
        StringBuilder ex = new StringBuilder();
        for (int j = 1; j < w - 1; j++) for (int i = 1; i < w - 1; i++) {
            if (wt[j][i] == Integer.MIN_VALUE || wt[j][i] <= g[j][i]) continue;
            int[][] nb4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nb4) {
                int ii = i + d[0], jj = j + d[1];
                boolean wet = wt[jj][ii] != Integer.MIN_VALUE && wt[jj][ii] > g[jj][ii];
                int open = Math.max(g[jj][ii], wet ? wt[jj][ii] : Integer.MIN_VALUE) + 1;
                if (open <= wt[j][i] && !(wet && wt[jj][ii] < wt[j][i])) {
                    exposed++;
                    if (ex.length() < 800) ex.append(String.format(" (%d %d g%d class %d)", cx - half - 1 + ii, cz - half - 1 + jj, g[jj][ii],
                            s.waterClass(cx - half - 1 + ii, cz - half - 1 + jj)));
                }
            }
        }
        System.out.println("exposed water faces " + exposed + (exposed > 0 ? ":" + ex : ""));
        int S = 3;
        BufferedImage img = new BufferedImage((w - 2) * S, (w - 2) * S, BufferedImage.TYPE_INT_RGB);
        for (int j = 1; j < w - 1; j++) for (int i = 1; i < w - 1; i++) {
            int x = cx - half - 1 + i, z = cz - half - 1 + j;
            double t = Math.max(0, Math.min(1, (g[j][i] - 55) / 20.0));
            int r = (int) (90 + 120 * t), gg = (int) (80 + 110 * t), b = (int) (60 + 60 * t);
            boolean red = false;
            int[][] nb = {{-1, 0}, {0, -1}};
            for (int[] d : nb) {
                int n = g[j + d[1]][i + d[0]];
                if (n != g[j][i]) {
                    double f = n < g[j][i] ? 1.25 : 0.7;
                    r = Math.min(255, (int) (r * f)); gg = Math.min(255, (int) (gg * f)); b = Math.min(255, (int) (b * f));
                    boolean onBorder = (Math.floorMod(x, 16) == 0 && d[0] != 0) || (Math.floorMod(z, 16) == 0 && d[1] != 0);
                    if (onBorder) { red = true; borderSteps++; }
                    steps++;
                }
            }
            if (wt[j][i] != Integer.MIN_VALUE && wt[j][i] > g[j][i]) {
                int depth = wt[j][i] - g[j][i];
                r = 30 + 20 / (1 + depth); gg = 90 + 40 / (1 + depth); b = 170 + 40 / (1 + depth);
            }
            if (red) { r = 230; gg = 40; b = 40; }
            int rgb = (r << 16) | (gg << 8) | b;
            for (int yy = 0; yy < S; yy++) for (int xx = 0; xx < S; xx++) img.setRGB((i - 1) * S + xx, (j - 1) * S + yy, rgb);
        }
        ImageIO.write(img, "png", new File(args[4]));
        System.out.printf("steps %d, on cell borders %d (%.1f%%; 12.5%% if borders were not special)%n", steps, borderSteps, 100.0 * borderSteps / Math.max(1, steps));
    }
}
