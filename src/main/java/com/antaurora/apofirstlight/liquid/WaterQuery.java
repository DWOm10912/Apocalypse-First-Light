package com.antaurora.apofirstlight.liquid;

import com.antaurora.apofirstlight.liquid.world.LiquidWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Asking about AFL's water (docs/gameplay/water_v1_design.md 5.8, package 4; river plan water_river_v1_design.md 7):
 * where its surface is above a point, and how deep and how fast it is around a box. The server asks the engine, the client
 * the surfaces it was sent (ClientLiquid), so a body goes into the water where the water is drawn. Only water at least
 * {@link #MIN_DEPTH} deep counts: a film on wet ground is not water to stand in. Natural water bodies (vanilla's water
 * blocks, the engine's boundaries) are not asked here: vanilla's own fluid code has them; a river's flow field (R1) is
 * AFL's and is.
 */
public final class WaterQuery {
    /** Shallower is wet ground, not water (m): the client draws a full surface from here (ClientLiquid.SHOW_FULL). */
    public static final double MIN_DEPTH = 0.02;
    /** Wet layers a column can report. */
    public static final int MAX_LAYERS = 8;
    /** Doubles a layer takes in {@link Source#layers}: floor (m), surface (m), velocity x and z (m/s, east and south). */
    public static final int STRIDE = 4;

    /** One side's water by column. */
    public interface Source {
        /** The column's wet layers at least MIN_DEPTH deep, bottom first, into out (STRIDE a layer); how many. */
        int layers(int x, int z, double[] out);
    }

    /** What a box is in: the deepest water over its bottom (m) and the water's mean velocity there (m/s). */
    public static final class Immersion {
        public double height, vx, vz;
    }

    private static volatile Source client;
    private static final double[] SCRATCH = new double[MAX_LAYERS * STRIDE];
    private static final double[] CLIENT_SCRATCH = new double[MAX_LAYERS * STRIDE];

    private WaterQuery() {}

    /** The client's surfaces (ClientLiquid sets it with its first packet: no AFL water on the client before that). */
    public static void setClientSource(Source source) {
        client = source;
    }

    /** The server's water: the engine's columns already there (none made by asking). */
    public static Source server(LiquidEngine engine) {
        double[] v = new double[2];
        return (x, z, out) -> {
            LiquidColumn col = engine.existingColumn(x, z);
            if (col == null) return 0;
            int n = 0;
            for (LiquidCell c : col.cells()) {
                if (n == MAX_LAYERS) break;
                // a river's water is AFL's (river plan R1); other held levels are vanilla's water bodies
                boolean water = c.river() || (!c.boundary() && c.volume() > 0 && c.liquid() == Liquids.WATER);
                if (!water || c.level() - c.floor() < MIN_DEPTH) continue;
                c.velocity(v);
                int o = n++ * STRIDE;
                out[o] = c.floor();
                out[o + 1] = c.level();
                out[o + 2] = v[0];
                out[o + 3] = v[1];
            }
            return n;
        };
    }

    private static Source source(Level level) {
        if (level.isClientSide()) return client;
        if (!(level instanceof ServerLevel server)) return null;
        LiquidEngine e = LiquidWorld.engineIfPresent(server);
        if (e == null) return null;
        if (e != lastEngine) {
            lastServer = server(e);
            lastEngine = e;
        }
        return lastServer;
    }

    /** The last server engine asked and its source (server thread). */
    private static LiquidEngine lastEngine;
    private static Source lastServer;

    private static double[] scratch(Level level) {
        return level.isClientSide() ? CLIENT_SCRATCH : SCRATCH;
    }

    /** The surface of the AFL water at (x, y, z), m, when y is in it (above its layer's floor, under its surface); else NaN. */
    public static double surfaceAt(Level level, double x, double y, double z) {
        Source s = source(level);
        return s == null ? Double.NaN : surfaceAt(s, scratch(level), x, y, z);
    }

    public static double surfaceAt(Source s, double[] buf, double x, double y, double z) {
        int n = s.layers((int) Math.floor(x), (int) Math.floor(z), buf);
        for (int i = 0; i < n; i++) {
            double floor = buf[i * STRIDE], top = buf[i * STRIDE + 1];
            if (y >= floor && y < top) return top;
        }
        return Double.NaN;
    }

    /** What the box [min, max] is in: false when no AFL water reaches its bottom. */
    public static boolean immersion(Level level, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                    Immersion out) {
        Source s = source(level);
        return s != null && immersion(s, scratch(level), minX, minY, minZ, maxX, maxY, maxZ, out);
    }

    /**
     * The deepest water over the box's bottom in the columns it covers (as vanilla takes the highest fluid block it
     * touches), counting only layers the box is in (a pool overhead is not water in the cave under it); the velocity is
     * the mean over those layers, each by how much of the box it covers.
     */
    public static boolean immersion(Source s, double[] buf, double minX, double minY, double minZ, double maxX, double maxY,
                                    double maxZ, Immersion out) {
        int x0 = (int) Math.floor(minX), x1 = (int) Math.floor(maxX), z0 = (int) Math.floor(minZ), z1 = (int) Math.floor(maxZ);
        double best = 0, vx = 0, vz = 0, weight = 0;
        boolean any = false;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int n = s.layers(x, z, buf);
                for (int i = 0; i < n; i++) {
                    int o = i * STRIDE;
                    double floor = buf[o], top = buf[o + 1];
                    if (floor >= maxY || top <= minY) continue;
                    double h = top - minY;
                    any = true;
                    best = Math.max(best, h);
                    double w = Math.min(top, maxY) - Math.max(floor, minY);
                    vx += buf[o + 2] * w;
                    vz += buf[o + 3] * w;
                    weight += w;
                }
            }
        }
        if (!any) return false;
        out.height = best;
        out.vx = weight > 0 ? vx / weight : 0;
        out.vz = weight > 0 ? vz / weight : 0;
        return true;
    }
}
