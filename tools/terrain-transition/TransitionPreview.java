import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Terrain R2 + ground transitions, offline preview (docs/worldgen/terrain_rework_v1_design.md 13): one river bank like the
 * one the user showed (2026-10-28), ray-marched from the same eye three ways, with AFL's own ground textures:
 * <ol>
 *   <li>now: corner heights on 1/4 m, each block's top two flat triangles split nw-se, each block's top its own
 *   material;</li>
 *   <li>R2: the same corners through a smooth (Catmull-Rom) height field, and the materials as a field over the 0.25 m
 *   vertices (each block's material spread between block centres, pushed about by world noise), each 0.25 m cell split
 *   along the field's boundary: smooth, irregular edges in geometry, any render path;</li>
 *   <li>R2 + fringe: along a boundary the material whose texture stands higher (pebbles, tufts) shows through the other
 *   by its own brightness, a band about 0.2 m wide.</li>
 * </ol>
 * <pre>javac -encoding UTF-8 -d &lt;tmp&gt; tools/terrain-transition/TransitionPreview.java
 * java -cp &lt;tmp&gt; TransitionPreview &lt;textures/block/ground dir&gt; &lt;out.png&gt;</pre>
 */
public class TransitionPreview {
    static final int GRASS = 0, GRAVEL = 1, SAND = 2, M = 3;
    static final String[] NAMES = {"grass", "gravel_round", "sand"};
    static final int[][][] TEX = new int[M][4][];
    static final int TS = 128;
    static final int OX = -8, OZ = -14, NX = 80, NZ = 44;
    static final double[][] HC = new double[NX + 1][NZ + 1];
    static final int[][] MAT = new int[NX][NZ];
    static final int W = 960, H = 540, SS = 2;

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        for (int m = 0; m < M; m++)
            for (int v = 0; v < 4; v++) {
                BufferedImage img = ImageIO.read(new File(dir, NAMES[m] + "_" + v + ".png"));
                TEX[m][v] = img.getRGB(0, 0, TS, TS, null, 0, TS);
            }
        for (int i = 0; i <= NX; i++)
            for (int j = 0; j <= NZ; j++) HC[i][j] = Math.round(trueHeight(OX + i, OZ + j) * 4) / 4.0;
        for (int i = 0; i < NX; i++)
            for (int j = 0; j < NZ; j++) MAT[i][j] = columnMaterial(i, j);
        String[] titles = {"现在：每格一种材质，坡顶两个平三角形", "R2 方案：平滑高度场 + 不规则分界（几何切开，不开光影也一样）",
                "R2 方案 + 边缘碎屑：分界处卵石、草按贴图亮度互相穿插"};
        BufferedImage out = new BufferedImage(W, H * 3, BufferedImage.TYPE_INT_RGB);
        for (int mode = 0; mode < 3; mode++) {
            BufferedImage panel = render(mode);
            Graphics2D g = out.createGraphics();
            g.drawImage(panel, 0, H * mode, null);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, H * mode, W, 38);
            g.setColor(Color.WHITE);
            g.drawString(titles[mode], 14, H * mode + 27);
            g.dispose();
            System.out.println("panel " + mode + " done");
        }
        ImageIO.write(out, "png", new File(args[1]));
    }

    // ---- the scene

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** A river along +x, water at y 0: the near (south) bank rises to the camera, the far one to the north. */
    static double trueHeight(double x, double z) {
        double zs = 5 + 1.3 * Math.sin(x * 0.21) + 0.6 * Math.sin(x * 0.57 + 1.0);
        double zn = -2.5 + 1.0 * Math.sin(x * 0.25 + 2.0) + 0.4 * Math.sin(x * 0.8);
        if (z >= zs) {
            double d = z - zs;
            double gentle = 0.5 + 0.5 * Math.sin(x * 0.17 + 0.5);       // a beach here, a steeper bank there
            return 0.12 + 0.35 * Math.min(d, 1.5) * (1 - 0.6 * gentle) + 2.0 * smooth(1.0 + 2.0 * gentle, 6.0, d) + 0.25 * Math.sin(x * 0.4 + z * 0.3);
        }
        if (z <= zn) {
            double d = zn - z;
            return 0.12 + 0.3 * Math.min(d, 1) + 1.8 * smooth(0.8, 4.5, d) + 0.2 * Math.sin(x * 0.33);
        }
        double c = (zs + zn) / 2, half = (zs - zn) / 2, q = (z - c) / half;
        return -0.15 - 1.1 * (1 - q * q) * (0.7 + 0.3 * Math.sin(x * 0.13));
    }

    static int hash(int a, int b, int salt) {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ salt * 0x5bd1e995;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        return h;
    }

    static double unit(int h) {
        return (h >>> 8) / (double) (1 << 24);
    }

    /** As RiverBed's materials: wet gravel with sand patches, a beach of sand and gravel, gravel patches, then grass. */
    static int columnMaterial(int i, int j) {
        double h = (HC[i][j] + HC[i + 1][j] + HC[i][j + 1] + HC[i + 1][j + 1]) / 4;
        double u = unit(hash(i, j, 7));
        if (h < -0.05) return u < 0.2 ? SAND : GRAVEL;
        if (h < 0.45) return u < 0.5 ? SAND : GRAVEL;
        if (h < 0.8 && u < 0.35) return GRAVEL;
        return GRASS;
    }

    // ---- heights

    static double corner(int i, int j) {
        return HC[Math.max(0, Math.min(NX, i))][Math.max(0, Math.min(NZ, j))];
    }

    static double flatHeight(double x, double z) {
        double px = x - OX, pz = z - OZ;
        int i = (int) Math.floor(px), j = (int) Math.floor(pz);
        double fx = px - i, fz = pz - j;
        double h00 = corner(i, j), h10 = corner(i + 1, j), h01 = corner(i, j + 1), h11 = corner(i + 1, j + 1);
        return fx >= fz ? h00 + (h10 - h00) * fx + (h11 - h10) * fz : h00 + (h01 - h00) * fz + (h11 - h01) * fx;
    }

    static double cr(double p0, double p1, double p2, double p3, double t) {
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
    }

    static double smoothHeight(double x, double z) {
        double px = x - OX, pz = z - OZ;
        int i = (int) Math.floor(px), j = (int) Math.floor(pz);
        double fx = px - i, fz = pz - j;
        double[] col = new double[4];
        for (int k = 0; k < 4; k++)
            col[k] = cr(corner(i - 1, j - 1 + k), corner(i, j - 1 + k), corner(i + 1, j - 1 + k), corner(i + 2, j - 1 + k), fx);
        return cr(col[0], col[1], col[2], col[3], fz);
    }

    static double height(int mode, double x, double z) {
        return mode == 0 ? flatHeight(x, z) : smoothHeight(x, z);
    }

    static double[] normal(int mode, double x, double z) {
        double gx, gz;
        if (mode == 0) {
            double px = x - OX, pz = z - OZ;
            int i = (int) Math.floor(px), j = (int) Math.floor(pz);
            double fx = px - i, fz = pz - j;
            double h00 = corner(i, j), h10 = corner(i + 1, j), h01 = corner(i, j + 1), h11 = corner(i + 1, j + 1);
            if (fx >= fz) { gx = h10 - h00; gz = h11 - h10; } else { gx = h11 - h01; gz = h01 - h00; }
        } else {
            double e = 0.02;
            gx = (smoothHeight(x + e, z) - smoothHeight(x - e, z)) / (2 * e);
            gz = (smoothHeight(x, z + e) - smoothHeight(x, z - e)) / (2 * e);
        }
        double l = Math.sqrt(gx * gx + 1 + gz * gz);
        return new double[]{-gx / l, 1 / l, -gz / l};
    }

    // ---- materials

    static double noise(double x, double z, int salt) {
        int i = (int) Math.floor(x), j = (int) Math.floor(z);
        double fx = x - i, fz = z - j;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = unit(hash(i, j, salt)), b = unit(hash(i + 1, j, salt)), c = unit(hash(i, j + 1, salt)), d = unit(hash(i + 1, j + 1, salt));
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    static double fbm(double x, double z, int salt) {
        // three octaves, the fine ones strong enough that an edge is ragged at every scale, not a smooth blob
        return (noise(x * 0.8, z * 0.8, salt) * 0.45 + noise(x * 2.1, z * 2.1, salt + 1) * 0.33 + noise(x * 5.3, z * 5.3, salt + 2) * 0.22) * 2 - 1;
    }

    /** Material m's weight at a 0.25 m vertex: its share of the four block centres round it, pushed by noise. */
    static double vertexWeight(int m, double x, double z) {
        double px = x - OX - 0.5, pz = z - OZ - 0.5;
        int i = (int) Math.floor(px), j = (int) Math.floor(pz);
        double fx = px - i, fz = pz - j;
        double w = 0;
        for (int dj = 0; dj < 2; dj++)
            for (int di = 0; di < 2; di++) {
                int ci = Math.max(0, Math.min(NX - 1, i + di)), cj = Math.max(0, Math.min(NZ - 1, j + dj));
                double k = (di == 0 ? 1 - fx : fx) * (dj == 0 ? 1 - fz : fz);
                if (MAT[ci][cj] == m) w += k;
            }
        return w + 0.45 * fbm(x, z, 100 + m * 10);
    }

    /** The materials' weights at a point: linear inside its 0.25 m cell, as the split geometry is. */
    static void weights(double x, double z, double[] out) {
        double gx = Math.floor(x * 4) / 4, gz = Math.floor(z * 4) / 4, fx = (x - gx) * 4, fz = (z - gz) * 4;
        for (int m = 0; m < M; m++) {
            double a = vertexWeight(m, gx, gz), b = vertexWeight(m, gx + 0.25, gz), c = vertexWeight(m, gx, gz + 0.25), d = vertexWeight(m, gx + 0.25, gz + 0.25);
            out[m] = (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
        }
    }

    static int texel(int m, double x, double z) {
        int ci = (int) Math.floor(x), cj = (int) Math.floor(z);
        int v = hash(ci, cj, 3) & 3;
        int u = (int) ((x - ci) * TS), w = (int) ((z - cj) * TS);
        return TEX[m][v][Math.min(TS - 1, w) * TS + Math.min(TS - 1, u)];
    }

    static double luma(int rgb) {
        return (0.3 * (rgb >> 16 & 255) + 0.59 * (rgb >> 8 & 255) + 0.11 * (rgb & 255)) / 255;
    }

    static int lastMaterial;

    static int groundColor(int mode, double x, double z) {
        if (mode == 0) {
            int i = Math.max(0, Math.min(NX - 1, (int) Math.floor(x) - OX)), j = Math.max(0, Math.min(NZ - 1, (int) Math.floor(z) - OZ));
            return texel(MAT[i][j], x, z);
        }
        double[] w = new double[M];
        weights(x, z, w);
        int best = 0;
        double bestScore = -9;
        for (int m = 0; m < M; m++) {
            double s = w[m];
            // fringe: the texture's own brightness (pebbles, tufts stand up) decides near the boundary
            if (mode == 2) s += 0.55 * (luma(texel(m, x, z)) - 0.45);
            if (s > bestScore) { bestScore = s; best = m; }
        }
        lastMaterial = best;
        return texel(best, x, z);
    }

    /** The grass top is grey and tinted by the biome, as in game: a temperate forest's green. */
    static final double[] GRASS_TINT = {0.50, 0.78, 0.36};

    static boolean isGrass(int mode, double x, double z) {
        if (mode == 0) {
            int i = Math.max(0, Math.min(NX - 1, (int) Math.floor(x) - OX)), j = Math.max(0, Math.min(NZ - 1, (int) Math.floor(z) - OZ));
            return MAT[i][j] == GRASS;
        }
        return lastMaterial == GRASS;
    }

    static int tint(int c, double[] t) {
        return rgb((c >> 16 & 255) / 255.0 * t[0], (c >> 8 & 255) / 255.0 * t[1], (c & 255) / 255.0 * t[2]);
    }

    // ---- the camera

    static BufferedImage render(int mode) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        double ex = 2, ez = 8.2, ey = smoothHeight(ex, ez) + 1.65;
        double[] fwd = norm(new double[]{1, -0.36, -0.30});
        double[] right = norm(cross(fwd, new double[]{0, 1, 0}));
        double[] up = cross(right, fwd);
        double tanHalf = Math.tan(Math.toRadians(70) / 2), aspect = W / (double) H;
        double[] sun = norm(new double[]{-0.35, 0.85, 0.40});
        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                double r = 0, g = 0, b = 0;
                for (int sy = 0; sy < SS; sy++)
                    for (int sx = 0; sx < SS; sx++) {
                        double u = ((px + (sx + 0.5) / SS) / W * 2 - 1) * tanHalf * aspect, v = (1 - (py + (sy + 0.5) / SS) / H * 2) * tanHalf;
                        double[] d = norm(new double[]{fwd[0] + right[0] * u + up[0] * v, fwd[1] + right[1] * u + up[1] * v, fwd[2] + right[2] * u + up[2] * v});
                        double[] c = trace(mode, ex, ey, ez, d, sun);
                        r += c[0];
                        g += c[1];
                        b += c[2];
                    }
                int n = SS * SS;
                img.setRGB(px, py, rgb(r / n, g / n, b / n));
            }
        }
        return img;
    }

    static double[] sky(double[] d) {
        double t = Math.max(0, d[1]);
        return new double[]{0.62 - 0.25 * t, 0.76 - 0.18 * t, 0.95 - 0.05 * t};
    }

    static double[] trace(int mode, double ox, double oy, double oz, double[] d, double[] sun) {
        double t = 0.3, last = t, water = -1;
        boolean hit = false;
        while (t < 70) {
            double x = ox + d[0] * t, y = oy + d[1] * t, z = oz + d[2] * t;
            if (water < 0 && y < 0) water = t;
            if (y < height(mode, x, z)) { hit = true; break; }
            last = t;
            t += 0.015 + t * 0.006;
        }
        if (!hit) {
            double[] s = sky(d);
            if (water > 0) return mixWater(s, s, d, 0.5);
            return s;
        }
        double lo = last, hi = t;
        for (int k = 0; k < 10; k++) {
            double mid = (lo + hi) / 2;
            double x = ox + d[0] * mid, y = oy + d[1] * mid, z = oz + d[2] * mid;
            if (y < height(mode, x, z)) hi = mid; else lo = mid;
        }
        t = hi;
        double x = ox + d[0] * t, z = oz + d[2] * t;
        int c = groundColor(mode, x, z);
        if (isGrass(mode, x, z)) c = tint(c, GRASS_TINT);
        double[] n = normal(mode, x, z);
        double shade = 0.42 + 0.58 * Math.max(0, n[0] * sun[0] + n[1] * sun[1] + n[2] * sun[2]);
        double[] col = {(c >> 16 & 255) / 255.0 * shade, (c >> 8 & 255) / 255.0 * shade, (c & 255) / 255.0 * shade};
        if (water > 0 && water < t) col = mixWater(col, sky(reflect(d)), d, t - water);
        double fog = 1 - Math.exp(-t / 120);
        double[] s = sky(new double[]{d[0], 0, d[2]});
        for (int k = 0; k < 3; k++) col[k] = col[k] * (1 - fog) + s[k] * fog;
        return col;
    }

    static double[] reflect(double[] d) {
        return new double[]{d[0], -d[1], d[2]};
    }

    static double[] mixWater(double[] bed, double[] skyColor, double[] d, double path) {
        double[] k = {0.75, 0.32, 0.26}, w = {0.07, 0.19, 0.20};
        double[] out = new double[3];
        double cos = Math.max(0, -d[1]), fresnel = 0.02 + 0.98 * Math.pow(1 - cos, 5);
        for (int i = 0; i < 3; i++) {
            double tr = Math.exp(-k[i] * path);
            double body = bed[i] * tr + w[i] * (1 - tr);
            out[i] = body * (1 - fresnel) + skyColor[i] * fresnel;
        }
        return out;
    }

    static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static int rgb(double r, double g, double b) {
        int ri = (int) Math.round(Math.max(0, Math.min(1, r)) * 255), gi = (int) Math.round(Math.max(0, Math.min(1, g)) * 255), bi = (int) Math.round(Math.max(0, Math.min(1, b)) * 255);
        return ri << 16 | gi << 8 | bi;
    }
}
