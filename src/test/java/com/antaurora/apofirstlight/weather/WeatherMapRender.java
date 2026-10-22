package com.antaurora.apofirstlight.weather;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Regional weather V1 offline look (docs/gameplay/regional_weather_v1_plan.md 10): the federal country over a few game
 * days, a frame every three game hours: land by landform, rain (blue to purple), thunder (yellow), cloud (white veil), the
 * wind (arrows every 2 km), and a timeline of the regimes. Prints how wet and windy it was.
 * Args: out.png [seed] [first day] [days].
 */
public final class WeatherMapRender {
    private static final double MPP = 100;
    private static final int COLS = 8, HEAD = 22;
    private static final Map<WeatherRegime, String> NAME = new EnumMap<>(WeatherRegime.class);
    private static final Map<WeatherRegime, Color> TINT = new EnumMap<>(WeatherRegime.class);

    static {
        NAME.put(WeatherRegime.CLEAR, "晴好");
        NAME.put(WeatherRegime.UNSTABLE, "不稳定（午后雷暴）");
        NAME.put(WeatherRegime.FRONT, "锋面过境");
        NAME.put(WeatherRegime.OVERCAST, "阴雨连绵");
        NAME.put(WeatherRegime.WINDY, "大风天");
        NAME.put(WeatherRegime.COLD_SNAP, "寒潮");
        NAME.put(WeatherRegime.HEAT, "热浪");
        TINT.put(WeatherRegime.CLEAR, new Color(0x9fd3f0));
        TINT.put(WeatherRegime.UNSTABLE, new Color(0xe8b84a));
        TINT.put(WeatherRegime.FRONT, new Color(0x5a6fd0));
        TINT.put(WeatherRegime.OVERCAST, new Color(0x8a8f99));
        TINT.put(WeatherRegime.WINDY, new Color(0x5fb39a));
        TINT.put(WeatherRegime.COLD_SNAP, new Color(0xb9d7ff));
        TINT.put(WeatherRegime.HEAT, new Color(0xe0603a));
    }

    // MOUNTAIN, PLATEAU, PLAINS, LAKE_LOWLAND, HILLS, COASTAL_LOWLAND
    private static final int[] LAND = {0x8a8478, 0xc4a77a, 0xb5c98a, 0x86b39a, 0x7ea36a, 0xcfd6a8};

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/weather_w1.png";
        long seed = args.length > 1 ? Long.parseLong(args[1]) : -295378578869149513L;
        int first = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        int days = args.length > 3 ? Integer.parseInt(args[3]) : 3;

        long t0 = System.nanoTime();
        FederalMap map = FederalMap.forSeed(seed);
        ClimateGrid grid = ClimateGrid.federal(map);
        RegionalWeather w = new RegionalWeather(seed, grid);
        System.out.printf("climate grid %dx%d in %.1f s%n", grid.nx, grid.nz, (System.nanoTime() - t0) / 1e9);

        FederalMap.Bounds b = map.landBounds();
        double ox = b.minX() - 2000, oz = b.minZ() - 2000;
        int fw = (int) Math.ceil((b.maxX() - b.minX() + 4000) / MPP), fh = (int) Math.ceil((b.maxZ() - b.minZ() + 4000) / MPP);
        // the land, once
        int[] base = new int[fw * fh];
        boolean[] land = new boolean[fw * fh];
        double[] c = new double[ClimateGrid.STRIDE];
        for (int j = 0; j < fh; j++)
            for (int i = 0; i < fw; i++) {
                double x = ox + (i + 0.5) * MPP, z = oz + (j + 0.5) * MPP;
                int k = j * fw + i;
                land[k] = map.isLand(x, z);
                if (!land[k]) {
                    base[k] = 0x2b3a4a;
                    continue;
                }
                grid.at(x, z, c);
                double r = 0, g = 0, bl = 0;
                for (int p = 0; p < 6; p++) {
                    r += c[2 + p] * (LAND[p] >> 16 & 255);
                    g += c[2 + p] * (LAND[p] >> 8 & 255);
                    bl += c[2 + p] * (LAND[p] & 255);
                }
                base[k] = (int) r << 16 | (int) g << 8 | (int) bl;
            }

        int frames = days * 8, rows = (frames + COLS - 1) / COLS;
        int pad = 6, tl = 90;
        BufferedImage sheet = new BufferedImage(COLS * (fw + pad) + pad, rows * (fh + HEAD + pad) + pad + tl, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x15181c));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        Font font = new Font("Microsoft YaHei", Font.PLAIN, 12);
        g.setFont(font);

        RegionalWeather.Sample s = new RegionalWeather.Sample();
        int landPx = 0;
        for (boolean l : land) if (l) landPx++;
        for (int f = 0; f < frames; f++) {
            long t = (long) (first + f / 8) * RegionalWeather.DAY + (f % 8) * 3L * RegionalWeather.HOUR;
            BufferedImage img = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_RGB);
            int wet = 0, thunder = 0;
            double maxRain = 0, wind = 0;
            for (int j = 0; j < fh; j++)
                for (int i = 0; i < fw; i++) {
                    int k = j * fw + i;
                    double x = ox + (i + 0.5) * MPP, z = oz + (j + 0.5) * MPP;
                    w.sample(x, z, t, s);
                    int col = base[k];
                    col = mix(col, 0xf2f4f7, 0.45 * s.cloud);
                    if (s.rain >= RegionalWeather.WET) {
                        double a = Math.min(1, 0.35 + s.rain / 12);
                        int rc = s.rain < 8 ? 0x4a8ff0 : s.rain < 25 ? 0x2b4fd8 : 0x8a3ad8;
                        col = mix(col, rc, Math.min(0.9, a));
                    }
                    if (s.thunder > 0.25) col = mix(col, 0xffe04a, 0.35 + 0.4 * s.thunder);
                    img.setRGB(i, j, col);
                    if (land[k]) {
                        if (s.wet()) wet++;
                        if (s.thunder > 0.25) thunder++;
                        maxRain = Math.max(maxRain, s.rain);
                        wind += s.windSpeed();
                    }
                }
            Graphics2D gi = img.createGraphics();
            gi.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gi.setStroke(new BasicStroke(1.2F));
            int step = (int) (2000 / MPP);
            for (int j = step / 2; j < fh; j += step)
                for (int i = step / 2; i < fw; i += step) {
                    w.sample(ox + i * MPP, oz + j * MPP, t, s);
                    double sp = s.windSpeed();
                    if (sp < 0.5) continue;
                    double len = Math.min(step * 0.95, 1.2 * sp), ux = s.windX / sp, uz = s.windZ / sp;
                    gi.setColor(sp > 12 ? new Color(0xff5a3a) : sp > 8 ? new Color(0xffb02a) : new Color(0x202428));
                    int x1 = (int) (i - ux * len / 2), y1 = (int) (j - uz * len / 2), x2 = (int) (i + ux * len / 2), y2 = (int) (j + uz * len / 2);
                    gi.drawLine(x1, y1, x2, y2);
                    gi.drawLine(x2, y2, (int) (x2 - ux * 4 + uz * 3), (int) (y2 - uz * 4 - ux * 3));
                    gi.drawLine(x2, y2, (int) (x2 - ux * 4 - uz * 3), (int) (y2 - uz * 4 + ux * 3));
                }
            // cell centres
            for (RegionalWeather.Cell cell : w.active(t)) {
                int px = (int) ((cell.x(t) - ox) / MPP), py = (int) ((cell.z(t) - oz) / MPP);
                gi.setColor(cell.kind() == RegionalWeather.Kind.STORM ? new Color(0xffe04a) : new Color(0xbfe0ff));
                gi.drawOval(px - 2, py - 2, 4, 4);
            }
            gi.dispose();
            int col = f % COLS, row = f / COLS;
            int x0 = pad + col * (fw + pad), y0 = pad + row * (fh + HEAD + pad);
            RegionalWeather.Span sp = w.span(t);
            double hour = RegionalWeather.hourOfDay(t);
            g.setColor(new Color(0xe6e6e6));
            g.drawString(String.format("第 %d 天 %02d:00 · %s", day(t), (int) hour, NAME.get(sp.regime())), x0, y0 + 15);
            g.drawImage(img, x0, y0 + HEAD, null);
            System.out.printf("day %d %02d:00 %-9s wet %4.1f%% thunder %4.1f%% max %5.1f mm/h wind %4.1f m/s cells %d%n",
                    day(t), (int) hour, sp.regime(), 100.0 * wet / landPx, 100.0 * thunder / landPx, maxRain, wind / landPx, w.active(t).size());
        }

        // the regimes over the next ten days
        int ty = sheet.getHeight() - tl + 10, tx = pad, tw = sheet.getWidth() - 2 * pad;
        long from = (long) first * RegionalWeather.DAY, to = from + 10L * RegionalWeather.DAY;
        for (long t = from; t < to; ) {
            RegionalWeather.Span sp = w.span(t);
            long end = Math.min(sp.end(), to);
            int a = tx + (int) ((t - from) * tw / (to - from)), e = tx + (int) ((end - from) * tw / (to - from));
            g.setColor(TINT.get(sp.regime()));
            g.fillRect(a, ty, Math.max(1, e - a - 1), 24);
            g.setColor(new Color(0x15181c));
            g.drawString(NAME.get(sp.regime()), a + 4, ty + 17);
            t = end;
        }
        g.setColor(new Color(0xe6e6e6));
        for (int d = 0; d <= 10; d++) {
            int x = tx + d * tw / 10;
            g.drawLine(x, ty + 26, x, ty + 32);
            if (d < 10) g.drawString("第 " + (first + d + 1) + " 天", x + 4, ty + 46);
        }
        g.drawString("雨：浅蓝 < 8 mm/h，蓝 < 25，紫 ≥ 25 · 黄 = 雷暴 · 白 = 云 · 箭头 = 风（黑 < 8 m/s，橙 < 12，红 ≥ 12）· 圆圈 = 雨团中心（黄 = 雷暴，浅蓝 = 阵雨）· 一格 = 100 m，箭头间距 2 km",
                tx, ty + 66);
        g.dispose();
        File file = new File(out);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", file);

        // thirty days at a few places
        double[][] places = {{0, 0}, {-6000, 0}, {6000, 0}, {0, -5000}, {0, 5000}};
        for (double[] p : places) {
            int wetHours = 0, storms = 0, windy = 0, n = 0;
            double rainSum = 0;
            for (long t = 0; t < 30L * RegionalWeather.DAY; t += 250, n++) {
                w.sample(p[0], p[1], t, s);
                if (s.wet()) wetHours++;
                if (s.thunder > 0.25) storms++;
                if (s.windSpeed() > 10) windy++;
                rainSum += s.rain * 250 / RegionalWeather.HOUR;
            }
            grid.at(p[0], p[1], c);
            System.out.printf("(%6.0f,%6.0f) temp %.2f moist %.2f: wet %4.1f%%  thunder %4.1f%%  wind>10 %4.1f%%  rain %.0f mm / 30 days%n",
                    p[0], p[1], c[0], c[1], 100.0 * wetHours / n, 100.0 * storms / n, 100.0 * windy / n, rainSum);
        }
        int[] count = new int[WeatherRegime.values().length];
        long t = 0;
        for (int k = 0; k < 400; k++) {
            RegionalWeather.Span sp = w.span(t);
            count[sp.regime().ordinal()] += (int) ((sp.end() - sp.start()) / RegionalWeather.HOUR);
            t = sp.end();
        }
        StringBuilder days30 = new StringBuilder("first 30 days:");
        for (long u = 0; u < 30L * RegionalWeather.DAY; ) {
            RegionalWeather.Span sp = w.span(u);
            days30.append(String.format(" %s@%.1f", sp.regime(), (sp.start() + 6000) / (double) RegionalWeather.DAY + 1));
            u = sp.end();
        }
        System.out.println(days30);
        StringBuilder sb = new StringBuilder("regime share of time:");
        for (WeatherRegime r : WeatherRegime.values()) sb.append(String.format(" %s %.0f%%", r, 100.0 * count[r.ordinal()] * RegionalWeather.HOUR / t));
        System.out.println(sb);
        List<RegionalWeather.Cell> sample = w.active(first * (long) RegionalWeather.DAY + 10 * RegionalWeather.HOUR);
        System.out.printf("done in %.1f s, %d cells at day %d 16:00%n", (System.nanoTime() - t0) / 1e9, sample.size(), first + 1);
    }

    /** The day on the clock (tick 0 is 6:00 of day 1). */
    private static long day(long t) {
        return Math.floorDiv(t + 6000, RegionalWeather.DAY) + 1;
    }

    private static int mix(int a, int b, double f) {
        f = Math.max(0, Math.min(1, f));
        int r = (int) ((a >> 16 & 255) * (1 - f) + (b >> 16 & 255) * f);
        int g = (int) ((a >> 8 & 255) * (1 - f) + (b >> 8 & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return r << 16 | g << 8 | bl;
    }
}
