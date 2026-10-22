package com.antaurora.apofirstlight.weather;

import com.antaurora.apofirstlight.worldgen.terrain.v2.PlanNoise;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Regional weather V1 (docs/gameplay/regional_weather_v1_plan.md 4): the weather anywhere at any time, worked out from a
 * seed, the day time (game ticks: sleeping moves it on too) and the {@link ClimateGrid}, so the server and every client get
 * the same answer without sending it. Two layers:
 * <ul>
 * <li>a {@link WeatherRegime} over the whole country for one to a few game days (cloud left over clear places, the wind,
 * the air temperature, how many storms start); next to each other they blend over two game hours. A front's rain band
 * sweeps across the country with its wind; long rain is a slow field over the wet regions;</li>
 * <li>rain {@link Cell}s: thunderstorms (mostly in the afternoon, most over the plains and mountains, dry over the
 * tableland: thunder, little rain reaching the ground) and showers (more where it is wet). Each is born, grows, rains,
 * dies, moving with the regime's steering wind (blocks per tick). A thunderstorm pushes a gust front out ahead of it.</li>
 * </ul>
 * The wind is one field: the regime's, stronger over open and high land (landform shares), turning slowly, plus the
 * storms' outflow and a front's push; everything that blows uses it.
 */
public final class RegionalWeather {
    /** Game hour and day (ticks). */
    public static final int HOUR = 1000, DAY = 24000;
    /** Rain below this (mm/h) counts as dry. */
    public static final double WET = 0.3;
    /** Regimes next to each other blend over this long. */
    private static final int BLEND = 6 * HOUR;
    /** Cells are made per game-hour slot; none lives longer than this. */
    private static final int MAX_LIFE = 10 * HOUR;
    /** A front crosses from this far behind the centre to this far past it (m, along its way). */
    private static final double FRONT_FROM = -15000, FRONT_TO = 15000;
    /** Per landform (MOUNTAIN, PLATEAU, PLAINS, LAKE_LOWLAND, HILLS, COASTAL_LOWLAND): wind, thunderstorm starts. */
    private static final double[] WIND_TERRAIN = {1.3, 1.25, 1.1, 1.0, 0.85, 1.15};
    private static final double[] STORM_START = {1.2, 0.5, 1.4, 0.9, 1.0, 0.9};

    public enum Kind { STORM, SHOWER }

    /**
     * One rain cell: where it was born and its velocity (blocks per tick), when (tick) and for how long, its radius at
     * full size (m), its heaviest rain (mm/h) and, a thunderstorm, how strong (0..1).
     */
    public record Cell(long id, Kind kind, double x0, double z0, double vx, double vz, long born, int life, double radius,
                       double peak, double strength) {
        public double x(long t) {
            return x0 + vx * (t - born);
        }

        public double z(long t) {
            return z0 + vz * (t - born);
        }

        public boolean alive(long t) {
            return t >= born && t < born + life;
        }

        double age(long t) {
            return (t - born) / (double) life;
        }

        /** Grows over the first quarter, dies over the last 40 %. */
        public double envelope(long t) {
            double a = age(t);
            return smooth(0, 0.25, a) * (1 - smooth(0.6, 1, a));
        }

        public double radiusNow(long t) {
            return radius * (0.5 + 0.5 * smooth(0, 0.25, age(t)));
        }

        /** Thunder now (0..1): the storm's middle part only. */
        public double thunder(long t) {
            if (kind != Kind.STORM) return 0;
            double a = age(t);
            return strength * smooth(0.15, 0.35, a) * (1 - smooth(0.7, 0.9, a));
        }

        /** Lightning strikes from this storm per real minute. */
        public double strikesPerMinute(long t) {
            return thunder(t) * (2 + 8 * strength);
        }
    }

    /** One regime: from start to end (ticks), its surface wind (m/s) and the steering wind cells move with (blocks/tick). */
    public record Span(WeatherRegime regime, long start, long end, double windX, double windZ, double steerX, double steerZ) {
    }

    /** The weather at one place and time. */
    public static final class Sample {
        /** Rain at the ground (mm/h), cloud cover (0..1), thunder (0..1), air temperature change (°C: the regime, a storm's outflow). */
        public double rain, cloud, thunder, temperature;
        /** Mean wind (m/s, the way it blows) and gusts (times the mean). */
        public double windX, windZ, gust;
        public WeatherRegime regime;

        public double windSpeed() {
            return Math.sqrt(windX * windX + windZ * windZ);
        }

        public boolean wet() {
            return rain >= WET;
        }
    }

    private final long seed;
    private final ClimateGrid climate;
    private final double minX, minZ, maxX, maxZ;
    private final List<Span> spans = new ArrayList<>();
    private final List<Force> forces = new ArrayList<>();
    private final List<Cell> extra = new ArrayList<>();
    private final Map<Long, List<Cell>> slots = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, List<Cell>> eldest) {
            return size() > 256;
        }
    };

    public RegionalWeather(long seed, ClimateGrid climate) {
        this.seed = seed;
        this.climate = climate;
        if (climate.nx > 1 && climate.nz > 1) {
            minX = climate.originX - 2000;
            minZ = climate.originZ - 2000;
            maxX = climate.originX + (climate.nx - 1) * climate.step + 2000;
            maxZ = climate.originZ + (climate.nz - 1) * climate.step + 2000;
        } else {
            minX = minZ = -12000;
            maxX = maxZ = 12000;
        }
    }

    public ClimateGrid climate() {
        return climate;
    }

    public long seed() {
        return seed;
    }

    // ------------------------------------------------------------------ regimes

    /** The regime at t. */
    public synchronized Span span(long t) {
        t = Math.max(0, t);
        if (spans.isEmpty()) spans.add(next(0, null));
        while (spans.get(spans.size() - 1).end <= t) spans.add(next(spans.size(), spans.get(spans.size() - 1)));
        int lo = 0, hi = spans.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (spans.get(mid).end <= t) lo = mid + 1;
            else hi = mid;
        }
        return spans.get(lo);
    }

    /** The span after last: a forced one where a force covers its start, else drawn, cut short where a force begins. */
    private Span next(int k, Span last) {
        long start = last == null ? 0 : last.end;
        for (Force f : forces) if (f.start <= start && start < f.end) return newSpan(k, f.regime, start, f.end);
        Span s = newSpan(k, last == null ? null : last.regime, start, -1);
        for (Force f : forces)
            if (f.start > start && f.start < s.end)
                s = new Span(s.regime, s.start, f.start, s.windX, s.windZ, s.steerX, s.steerZ);
        return s;
    }

    /** A regime set by hand (/afl weather) from start to end (ticks); a later force overrides what it overlaps. */
    public record Force(WeatherRegime regime, long start, long end) {
    }

    /** Sets a regime by hand: what was drawn there is forgotten (the same on every side that is told). */
    public synchronized void force(Force f) {
        forces.removeIf(o -> o.start < f.end && f.start < o.end);
        forces.add(f);
        forces.sort((a, b) -> Long.compare(a.start, b.start));
        spans.clear();
        slots.clear();
    }

    /** A cell put in by hand (/afl weather storm|shower). */
    public synchronized void add(Cell cell) {
        extra.add(cell);
    }

    /** Back to the seed's own weather. */
    public synchronized void reset() {
        forces.clear();
        extra.clear();
        spans.clear();
        slots.clear();
    }

    public synchronized List<Force> forces() {
        return List.copyOf(forces);
    }

    /** The hand-made cells not yet over at t. */
    public synchronized List<Cell> extra(long t) {
        extra.removeIf(c -> c.born + c.life <= t);
        return List.copyOf(extra);
    }

    /** A cell's id for one put in by hand. */
    public long handId(long t, int n) {
        return PlanNoise.mix(seed ^ t * 31 + n) | 1L << 62;
    }

    private Span newSpan(int k, WeatherRegime forced, long start, long forcedEnd) {
        SplittableRandom r = new SplittableRandom(PlanNoise.mix(seed ^ (k * 0x9E3779B97F4A7C15L) ^ 0x5745415448L));
        WeatherRegime g = forcedEnd >= 0 ? forced : forced == null ? WeatherRegime.CLEAR : pick(forced, r);
        double days = g.minDays + r.nextDouble() * (g.maxDays - g.minDays);
        long end = forcedEnd >= 0 ? forcedEnd : start + Math.max(1, Math.round(days * DAY / HOUR)) * HOUR;
        double dir = Math.toRadians(g.direction + g.spread * (2 * r.nextDouble() - 1));
        double speed = g.windMin + r.nextDouble() * (g.windMax - g.windMin);
        double steer = Math.max(0.4, Math.min(0.75, 0.4 + 0.035 * (speed - 2)));
        double steerDir = dir + Math.toRadians(10 * (2 * r.nextDouble() - 1));
        if (g == WeatherRegime.FRONT) {
            steer = (FRONT_TO - FRONT_FROM) / (end - start);
            steerDir = dir;
        }
        return new Span(g, start, end, Math.cos(dir) * speed, Math.sin(dir) * speed, Math.cos(steerDir) * steer, Math.sin(steerDir) * steer);
    }

    private static WeatherRegime pick(WeatherRegime previous, SplittableRandom r) {
        double sum = 0;
        for (WeatherRegime g : WeatherRegime.values()) sum += g.weightAfter(previous);
        double x = r.nextDouble() * sum;
        for (WeatherRegime g : WeatherRegime.values()) {
            x -= g.weightAfter(previous);
            if (x < 0) return g;
        }
        return WeatherRegime.CLEAR;
    }

    // ------------------------------------------------------------------ cells

    /** The cells alive at t. */
    public List<Cell> active(long t) {
        List<Cell> out = new ArrayList<>();
        for (long s = Math.floorDiv(t - MAX_LIFE, HOUR); s <= Math.floorDiv(t, HOUR); s++)
            for (Cell c : cells(s)) if (c.alive(t)) out.add(c);
        for (Cell c : extra(t)) if (c.alive(t)) out.add(c);
        return out;
    }

    private synchronized List<Cell> cells(long slot) {
        List<Cell> cached = slots.get(slot);
        if (cached != null) return cached;
        List<Cell> out = new ArrayList<>();
        if (slot >= 0) {
            long t0 = slot * HOUR;
            Span sp = span(t0);
            WeatherRegime g = sp.regime;
            SplittableRandom r = new SplittableRandom(PlanNoise.mix(seed ^ (slot * 0xD1B54A32D192ED03L) ^ 0x43454C4CL));
            double pm = Math.exp(-sq((hourOfDay(t0 + HOUR / 2) - 16) / 3));
            double storms = g == WeatherRegime.FRONT ? 1.2 : g.stormsPeak * pm + g.stormsOff * (1 - pm);
            double showers = g.showers * (0.6 + 0.8 * pm);
            int storm = poisson(r, storms), shower = poisson(r, showers);
            double[] c = new double[ClimateGrid.STRIDE];
            for (int i = 0; i < storm; i++) {
                Cell cell = g == WeatherRegime.FRONT ? frontStorm(sp, slot, i, t0, r, c) : storm(sp, slot, i, t0, r, c);
                if (cell != null) out.add(cell);
            }
            for (int i = 0; i < shower; i++) {
                Cell cell = shower(sp, slot, i, t0, r, c);
                if (cell != null) out.add(cell);
            }
        }
        slots.put(slot, out);
        return out;
    }

    /** A thunderstorm somewhere a storm likes to start (tries a few places). */
    private Cell storm(Span sp, long slot, int i, long t0, SplittableRandom r, double[] c) {
        for (int k = 0; k < 4; k++) {
            double x = minX + r.nextDouble() * (maxX - minX), z = minZ + r.nextDouble() * (maxZ - minZ);
            climate.at(x, z, c);
            double f = 0;
            for (int p = 0; p < 6; p++) f += c[2 + p] * STORM_START[p];
            f *= (0.6 + 0.8 * c[0]) * (0.25 + 0.75 * c[ClimateGrid.LAND]);
            if (r.nextDouble() * 2.0 < f) return stormAt(sp, slot * 64 + i, x, z, t0 + (long) (r.nextDouble() * HOUR), r, c);
        }
        return null;
    }

    /** A thunderstorm on a front's leading edge, moving with it. */
    private Cell frontStorm(Span sp, long slot, int i, long t0, SplittableRandom r, double[] c) {
        long born = t0 + (long) (r.nextDouble() * HOUR);
        double s = Math.hypot(sp.steerX, sp.steerZ), cx = sp.steerX / s, cz = sp.steerZ / s;
        double u = frontAt(sp, born) + 1200 * r.nextDouble() - 300, v = (2 * r.nextDouble() - 1) * 16000;
        double x = u * cx - v * cz, z = u * cz + v * cx;
        climate.at(x, z, c);
        if (r.nextDouble() > 0.4 + c[0]) return null;
        Cell cell = stormAt(sp, slot * 64 + i, x, z, born, r, c);
        return new Cell(cell.id, cell.kind, x, z, sp.steerX, sp.steerZ, born, cell.life, cell.radius * 0.8, cell.peak * 0.8, cell.strength * 0.8);
    }

    private Cell stormAt(Span sp, long id, double x, double z, long born, SplittableRandom r, double[] c) {
        double temp = c[0];
        int life = (int) ((4 + 6 * r.nextDouble()) * HOUR);
        double radius = (1000 + 1500 * r.nextDouble()) * (0.8 + 0.4 * temp);
        double peak = (20 + 30 * r.nextDouble()) * (0.7 + 0.6 * temp);
        double turn = Math.toRadians(25 * (2 * r.nextDouble() - 1)), pace = 0.85 + 0.3 * r.nextDouble();
        double vx = (sp.steerX * Math.cos(turn) - sp.steerZ * Math.sin(turn)) * pace, vz = (sp.steerX * Math.sin(turn) + sp.steerZ * Math.cos(turn)) * pace;
        return new Cell(seed ^ PlanNoise.mix(id), Kind.STORM, x, z, vx, vz, born, life, radius, peak, Math.max(0.3, Math.min(1, peak / 50)));
    }

    /** A shower, more likely where it is wet. */
    private Cell shower(Span sp, long slot, int i, long t0, SplittableRandom r, double[] c) {
        for (int k = 0; k < 4; k++) {
            double x = minX + r.nextDouble() * (maxX - minX), z = minZ + r.nextDouble() * (maxZ - minZ);
            climate.at(x, z, c);
            if (r.nextDouble() * 1.7 >= (0.3 + 1.4 * c[1]) * (0.4 + 0.6 * c[ClimateGrid.LAND])) continue;
            int life = (int) ((2 + 3 * r.nextDouble()) * HOUR);
            double radius = 600 + 900 * r.nextDouble(), peak = (3 + 9 * r.nextDouble()) * (0.5 + c[1]);
            double turn = Math.toRadians(25 * (2 * r.nextDouble() - 1));
            double vx = sp.steerX * Math.cos(turn) - sp.steerZ * Math.sin(turn), vz = sp.steerX * Math.sin(turn) + sp.steerZ * Math.cos(turn);
            return new Cell(seed ^ PlanNoise.mix(slot * 64 + 32 + i), Kind.SHOWER, x, z, vx, vz, t0 + (long) (r.nextDouble() * HOUR), life,
                    radius, peak, 0);
        }
        return null;
    }

    // ------------------------------------------------------------------ the weather at a point

    /** The weather at (x, z) at t into out. */
    public void sample(double x, double z, long t, Sample out) {
        double[] c = new double[ClimateGrid.STRIDE];
        climate.at(x, z, c);
        double moist = c[1];
        out.rain = out.cloud = out.thunder = out.temperature = out.windX = out.windZ = out.gust = 0;
        // the regime, blended with the next or the last near its ends
        Span sp = span(t), other = null;
        double into = t - sp.start, left = sp.end - t, wo = 0;
        if (into < BLEND / 2.0 && sp.start > 0) {
            other = span(sp.start - 1);
            wo = 0.5 - into / BLEND;
        } else if (left < BLEND / 2.0) {
            other = span(sp.end);
            wo = 0.5 - left / BLEND;
        }
        out.regime = sp.regime;
        double[] f = new double[4]; // cloud from fields, front push, push x, push z
        regime(sp, 1 - wo, x, z, t, moist, out, f);
        if (other != null) regime(other, wo, x, z, t, moist, out, f);
        // wind: stronger over open and high ground and in the afternoon, turning and swelling slowly; a front's push
        double terrain = 0;
        for (int p = 0; p < 6; p++) terrain += c[2 + p] * WIND_TERRAIN[p];
        terrain *= 0.75 + 0.4 * Math.exp(-sq((hourOfDay(t) - 14) / 4));
        double swell = Math.max(0.6, Math.min(1.4, 1 + 0.15 * PlanNoise.fbm(seed, x + 0.3 * t, z, 9000, 2, 0.5, 61)));
        double turn = 0.25 * PlanNoise.fbm(seed, x, z - 0.3 * t, 9000, 2, 0.5, 62), cs = Math.cos(turn), sn = Math.sin(turn);
        double wx = (out.windX * cs - out.windZ * sn) * terrain * swell, wz = (out.windX * sn + out.windZ * cs) * terrain * swell;
        out.windX = wx + f[2];
        out.windZ = wz + f[3];
        double cloud = f[0];
        // the cells
        double reach = Math.max(0.15, Math.min(1, (moist - 0.15) / 0.35)), outflow = 0;
        for (Cell cell : active(t)) {
            double R = cell.radiusNow(t), dx = x - cell.x(t), dz = z - cell.z(t), d = Math.sqrt(dx * dx + dz * dz);
            if (d > 2.5 * R) continue;
            double q = d / (R * (1 + 0.3 * PlanNoise.noise(cell.id, dx / R * 1.3, dz / R * 1.3, 7)));
            double e = cell.envelope(t);
            if (q < 1) out.rain += cell.peak * e * sq(1 - q * q) * (cell.kind == Kind.STORM ? reach : 1);
            cloud = Math.max(cloud, e * (1 - smooth(0.9, cell.kind == Kind.STORM ? 1.8 : 1.4, q)));
            double th = cell.thunder(t);
            if (th <= 0) continue;
            if (q < 1.2) out.thunder = Math.max(out.thunder, th * Math.min(1, (1.2 - q) / 0.2));
            // the outflow: cold air spreading out from under the storm, strongest ahead of it
            double ring = smooth(0.6, 1.1, q) * (1 - smooth(1.4, 2.4, q));
            if (ring <= 0 || d < 1) continue;
            double ux = dx / d, uz = dz / d, vs = Math.hypot(cell.vx, cell.vz);
            double ahead = vs > 1e-6 ? 1 + 0.6 * (ux * cell.vx + uz * cell.vz) / vs : 1;
            double push = 10 * cell.strength * th * ring * ahead;
            out.windX += ux * push;
            out.windZ += uz * push;
            outflow = Math.max(outflow, push);
            out.temperature -= 4 * ring * th;
        }
        if (outflow > 3) out.gust = Math.max(out.gust, 1.8);
        // the rain's own cooling is the temperature model's (temperature config weather.rain / thunder)
        out.cloud = Math.max(0, Math.min(1, Math.max(out.cloud, cloud)));
    }

    /** One regime's share (weight) of the weather: base cloud, wind, gusts, temperature, a front, long rain. */
    private void regime(Span sp, double weight, double x, double z, long t, double moist, Sample out, double[] f) {
        if (weight <= 0) return;
        WeatherRegime g = sp.regime;
        out.cloud += weight * Math.min(1, g.cloud * (0.6 + 0.8 * moist));
        out.windX += weight * sp.windX;
        out.windZ += weight * sp.windZ;
        out.gust += weight * g.gust;
        out.temperature += weight * g.temperature;
        if (g == WeatherRegime.FRONT) {
            double s = Math.hypot(sp.steerX, sp.steerZ), cx = sp.steerX / s, cz = sp.steerZ / s;
            double u = x * cx + z * cz, v = -x * cz + z * cx, uf = frontAt(sp, t), du = uf - u;
            double band = smooth(-300, 600, du) * (1 - smooth(1500, 4500, du));
            double mod = Math.max(0.15, Math.min(1.5, 0.7 + 0.45 * PlanNoise.fbm(seed, v, uf, 5000, 2, 0.5, 41)));
            out.rain += weight * 6 * mod * band * (0.5 + moist);
            f[0] = Math.max(f[0], weight * (smooth(-9000, -800, du) * (1 - smooth(4500, 10000, du))));
            double push = weight * 5 * Math.exp(-sq(du / 1500));
            f[2] += cx * push;
            f[3] += cz * push;
        } else if (g == WeatherRegime.OVERCAST) {
            double n = PlanNoise.fbm(seed, x - sp.steerX * (t - sp.start), z - sp.steerZ * (t - sp.start), 7000, 3, 0.5, 51);
            out.rain += weight * Math.max(0, Math.min(2.4, 0.8 * n + 3 * (moist - 0.45) - 0.3)) * 2.5;
            f[0] = Math.max(f[0], weight * Math.min(1, 0.75 + 0.2 * moist + 0.1 * n));
        }
    }

    /** Where a front is at t (m along its way). */
    private static double frontAt(Span sp, long t) {
        return FRONT_FROM + (FRONT_TO - FRONT_FROM) * (t - sp.start) / (double) (sp.end - sp.start);
    }

    /** Hour of the day (0..24; tick 0 is 6:00). */
    public static double hourOfDay(long t) {
        return (Math.floorMod(t, DAY) / 1000.0 + 6) % 24;
    }

    private static int poisson(SplittableRandom r, double mean) {
        if (mean <= 0) return 0;
        double l = Math.exp(-mean), p = 1;
        int k = 0;
        do {
            k++;
            p *= r.nextDouble();
        } while (p > l && k < 64);
        return k - 1;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static double sq(double x) {
        return x * x;
    }
}
