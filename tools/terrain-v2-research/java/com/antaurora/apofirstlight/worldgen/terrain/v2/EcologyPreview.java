package com.antaurora.apofirstlight.worldgen.terrain.v2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Ecology distribution preview (2026-10-10, for the user's review before anything reaches world generation): the
 * EcologyBiomes proposal over the shore-warped r1 plan, drawn as a biome map with relief shading, river water and
 * points.
 * <pre>java EcologyPreview SEED OUT_DIR [x z label]...</pre>
 * Writes ecology_national_8m.png, ecology_&lt;label&gt;_2km.png (2 m / px), ecology_&lt;label&gt;_500m.png (1 m / px) and
 * ecology_preview.md (shares by biome, by zone, per window).
 */
public final class EcologyPreview {
    static final String[] NAMES_ZH = {"海 / 河口湾", "沙滩", "温带草地", "栎-山核桃林地", "坡地混合阔叶林", "山脊栎林（山地林）",
            "谷地铁杉-白松林（局部针叶 / 混交）", "河岸与河漫滩林", "海岸松栎林", "海岸沙地疏林草地", "海岸沼泽林", "潮汐湿地", "湿草甸"};

    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        File out = new File(args[1]);
        out.mkdirs();
        TerrainPlanV2 p = new TerrainPlanV2(seed);
        TerrainPlanSurface s = p.toSurface();
        EcologyPlan.Zones zones = EcologyPlan.classify(p);
        StringBuilder md = new StringBuilder("# Ecology preview, seed " + seed + "\n\n");
        double ext = TerrainPlanSurface.N * TerrainPlanSurface.CELL;
        int n = (int) (ext / 8);
        int[] count = new int[EcologyBiomes.IDS.length];
        BufferedImage nat = render(s, zones, TerrainPlanSurface.ORIGIN, TerrainPlanSurface.ORIGIN, n, n, 8, count);
        for (int k = 2; k + 2 < args.length; k += 3)
            TerrainPlanMaps.mark(nat, TerrainPlanSurface.ORIGIN, TerrainPlanSurface.ORIGIN, 8, Double.parseDouble(args[k]), Double.parseDouble(args[k + 1]), 0xFFE0402A);
        ImageIO.write(nat, "png", new File(out, "ecology_national_8m.png"));
        shares(md, "Whole plan (8 m samples, land only)", count);
        for (int k = 2; k + 2 < args.length; k += 3) {
            double x = Double.parseDouble(args[k]), z = Double.parseDouble(args[k + 1]);
            String label = args[k + 2];
            int[] c2 = new int[EcologyBiomes.IDS.length];
            BufferedImage w2 = render(s, zones, x - 1000, z - 1000, 1000, 1000, 2, c2);
            TerrainPlanMaps.mark(w2, x - 1000, z - 1000, 2, x, z, 0xFFE0402A);
            ImageIO.write(w2, "png", new File(out, "ecology_" + label + "_2km.png"));
            BufferedImage w5 = render(s, zones, x - 250, z - 250, 500, 500, 1, new int[EcologyBiomes.IDS.length]);
            TerrainPlanMaps.mark(w5, x - 250, z - 250, 1, x, z, 0xFFE0402A);
            ImageIO.write(w5, "png", new File(out, "ecology_" + label + "_500m.png"));
            shares(md, label + " (" + (int) x + ", " + (int) z + "), 2 km window", c2);
        }
        legend(new File(out, "ecology_legend.png"));
        try (PrintWriter w = new PrintWriter(new File(out, "ecology_preview.md"), StandardCharsets.UTF_8)) { w.print(md); }
        System.out.print(md);
    }

    static void shares(StringBuilder md, String title, int[] c) {
        int land = 0;
        for (int k = 1; k < c.length; k++) land += c[k];
        md.append("## ").append(title).append("\n\n| biome | share of land |\n|---|---|\n");
        for (int k = 1; k < c.length; k++)
            md.append(String.format("| %s (%s) | %.1f %% |%n", NAMES_ZH[k], EcologyBiomes.IDS[k], 100.0 * c[k] / Math.max(1, land)));
        md.append('\n');
    }

    static BufferedImage render(TerrainPlanSurface s, EcologyPlan.Zones zones, double x0, double z0, int w, int h, double mpp, int[] count) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double[] row0 = new double[w + 1], row1 = new double[w + 1];
        for (int px = 0; px <= w; px++) row0[px] = s.heightAt(x0 + px * mpp, z0);
        RiverNetwork.Column col = new RiverNetwork.Column();
        for (int py = 0; py < h; py++) {
            for (int px = 0; px <= w; px++) row1[px] = s.heightAt(x0 + px * mpp, z0 + (py + 1) * mpp);
            for (int px = 0; px < w; px++) {
                double x = x0 + (px + 0.5) * mpp, z = z0 + (py + 0.5) * mpp;
                int b = EcologyBiomes.biomeAt(s, zones, x, z);
                count[b]++;
                int c = 0xFF000000 | EcologyBiomes.COLOURS[b];
                if (b != EcologyBiomes.SEA) {
                    double gx = (row0[px + 1] - row0[px] + row1[px + 1] - row1[px]) / (2 * mpp);
                    double gz = (row1[px] - row0[px] + row1[px + 1] - row0[px + 1]) / (2 * mpp);
                    c = TerrainPlanMaps.shade(c, gx, gz, mpp);
                    if (mpp <= 2 && s.rivers != null) {
                        s.rivers.column((int) Math.floor(x), (int) Math.floor(z), col);
                        if (col.kind == RiverNetwork.Column.RIVER) c = TerrainPlanMaps.RIVER;
                        else if (s.poolAt((int) Math.floor(x), (int) Math.floor(z)) && Math.ceil(s.heightAt(x, z)) - 1 == 63) c = TerrainPlanMaps.POOL;
                    }
                }
                img.setRGB(px, py, c);
            }
            double[] t = row0; row0 = row1; row1 = t;
        }
        if (mpp > 2 && s.rivers != null) TerrainPlanMaps.drawRivers(img, s.rivers, x0, z0, mpp);
        return img;
    }

    /** A plain swatch legend (labels are in the doc; Java2D text would need the AWT font stack). */
    static void legend(File f) throws Exception {
        int k = EcologyBiomes.IDS.length;
        BufferedImage img = new BufferedImage(k * 40, 40, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < k; i++) for (int y = 0; y < 40; y++) for (int x = 0; x < 38; x++) img.setRGB(i * 40 + x, y, EcologyBiomes.COLOURS[i]);
        ImageIO.write(img, "png", f);
    }
}
