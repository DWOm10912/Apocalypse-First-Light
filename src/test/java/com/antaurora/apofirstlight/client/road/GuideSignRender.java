package com.antaurora.apofirstlight.client.road;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline front view of guide signs as the client bakes them (GuideSignParts, C3.3b group 2 package B): the faces turned
 * toward the viewer (+z), drawn back to front with the sheet's texels (nearest), shaded by their normal; for checking
 * the layout, the border, the cells and the UVs without a game. Arguments: the sheet PNG, the output PNG, bones.
 */
public final class GuideSignRender {
    private GuideSignRender() {}

    static final double PX = 90;

    public static void main(String[] args) throws Exception {
        BufferedImage sheet = ImageIO.read(new File(args[0]));
        List<float[][]> signs = new ArrayList<>();
        double width = 0, height = 0;
        for (int i = 2; i < args.length; i++) {
            GuideSignParts.Local l = GuideSignParts.build(args[i], new float[]{0, 1, 0, 1});
            signs.add(new float[][]{l.solid(), l.overlay()});
            double[] b = bounds(l.solid());
            width += b[1] - b[0] + 0.5;
            height = Math.max(height, b[3] - b[2]);
        }
        int w = (int) Math.ceil(width * PX) + 40, h = (int) Math.ceil(height * PX) + 40;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] zb = new int[w * h];
        double[] depth = new double[w * h];
        java.util.Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setRGB(x, y, 0xB8C8D8);
        double ox = 20;
        for (float[][] s : signs) {
            double[] b = bounds(s[0]);
            double shiftX = ox / PX - b[0], baseY = h - 20;
            for (float[] v : s) draw(out, depth, sheet, v, shiftX, baseY, b[2]);
            ox += (b[1] - b[0] + 0.5) * PX;
        }
        ImageIO.write(out, "png", new File(args[1]));
        System.out.println(w + "x" + h);
    }

    private static double[] bounds(float[] v) {
        double x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
        for (int i = 0; i < v.length; i += RoadClient.STRIDE) {
            x0 = Math.min(x0, v[i]); x1 = Math.max(x1, v[i]);
            y0 = Math.min(y0, v[i + 1]); y1 = Math.max(y1, v[i + 1]);
        }
        return new double[]{x0, x1, y0, y1};
    }

    /** The quads (four vertices, a triangle repeating its last) facing +z, rasterised with their texels. */
    private static void draw(BufferedImage out, double[] depth, BufferedImage sheet, float[] v, double shiftX, double baseY, double y0) {
        int w = out.getWidth(), h = out.getHeight(), S = RoadClient.STRIDE;
        for (int q = 0; q + 4 * S <= v.length; q += 4 * S) {
            if (v[q + 7] < 0.5) continue;
            for (int[] t : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                double[][] p = new double[3][];
                for (int k = 0; k < 3; k++) {
                    int o = q + t[k] * S;
                    p[k] = new double[]{(v[o] + shiftX) * PX, baseY - (v[o + 1] - y0) * PX, v[o + 2], v[o + 3], v[o + 4]};
                }
                double area = (p[1][0] - p[0][0]) * (p[2][1] - p[0][1]) - (p[2][0] - p[0][0]) * (p[1][1] - p[0][1]);
                if (Math.abs(area) < 1e-9) continue;
                // counter-clockwise from the front in world (y up) is clockwise on screen (y down): negative area
                if (area > 0) System.out.println("WINDING " + q / S);
                int xa = (int) Math.floor(Math.min(p[0][0], Math.min(p[1][0], p[2][0]))), xb = (int) Math.ceil(Math.max(p[0][0], Math.max(p[1][0], p[2][0])));
                int ya = (int) Math.floor(Math.min(p[0][1], Math.min(p[1][1], p[2][1]))), yb = (int) Math.ceil(Math.max(p[0][1], Math.max(p[1][1], p[2][1])));
                for (int y = Math.max(0, ya); y <= Math.min(h - 1, yb); y++)
                    for (int x = Math.max(0, xa); x <= Math.min(w - 1, xb); x++) {
                        double px = x + 0.5, py = y + 0.5;
                        double l0 = ((p[1][0] - px) * (p[2][1] - py) - (p[2][0] - px) * (p[1][1] - py)) / area;
                        double l1 = ((p[2][0] - px) * (p[0][1] - py) - (p[0][0] - px) * (p[2][1] - py)) / area;
                        double l2 = 1 - l0 - l1;
                        if (l0 < -1e-9 || l1 < -1e-9 || l2 < -1e-9) continue;
                        double z = l0 * p[0][2] + l1 * p[1][2] + l2 * p[2][2];
                        if (z < depth[x + y * w] - 1e-6) continue;
                        depth[x + y * w] = z;
                        double u = l0 * p[0][3] + l1 * p[1][3] + l2 * p[2][3], vv = l0 * p[0][4] + l1 * p[1][4] + l2 * p[2][4];
                        int sx = Math.max(0, Math.min(sheet.getWidth() - 1, (int) (u * sheet.getWidth()))), sy = Math.max(0, Math.min(sheet.getHeight() - 1, (int) (vv * sheet.getHeight())));
                        out.setRGB(x, y, sheet.getRGB(sx, sy) & 0xFFFFFF);
                    }
            }
        }
    }
}
