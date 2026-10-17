package com.antaurora.apofirstlight.vehicle;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;

/**
 * The T15's headlamp beams as block light (docs/vehicles/first_pickup_v0_design.md 8.10): a fan of rays from the lamps
 * through the world's cells, each open cell it passes lit by how far it is along the beam and how far off its middle.
 * A full opaque block stops a ray unlit (its faces take the light of the open cell in front of them, as vanilla light);
 * a partial solid block (a road_surface layer, a slab) takes the light itself (its top face is lit from its own cell) and
 * stops the ray where the ray meets its solid top (World#surfaceTop): over a layer the lamps sit in, the beam goes on. The two headlamps of a beam are one source (at block size their two beams cannot be told apart), cast from
 * the middle between them. Pure: the client (client/vehicle/HeadlightField) and the offline check (VehicleCheck) give
 * the cells.
 * <p>Light: {@link Pattern#near} within {@link #NEAR} m, falling with the log of the distance to {@link Pattern#far} at
 * the reach; off the middle it drops with the square of the share of the half spread (an ellipse), by up to
 * {@link #EDGE}. The low beam reaches {@link Pattern#rightReach} farther on its right edge (the kerb side, as a real
 * asymmetric low beam), and is aimed {@link Pattern#aimDown} deg down so it meets a flat road about 40 m ahead.
 */
public final class HeadlightBeam {
    /** What a cell does to the beam. */
    public enum Cell {
        /** Lets it through and is lit (air, plants, glass panes would be OPAQUE-free too). */
        OPEN,
        /** Takes it on its own surface (lit) and stops it: a partial solid block. */
        SURFACE,
        /** Stops it unlit: a full opaque block (its faces read the open cell in front). */
        OPAQUE
    }

    /** The world's cells for the beam. */
    public interface World {
        Cell cell(int x, int y, int z);

        /** A SURFACE cell's solid top (share of the cell, 0-1): the beam passes over it in the open part above. */
        default double surfaceTop(int x, int y, int z) { return 1.0; }
    }

    /**
     * A beam: aim below the lamp's forward (deg); half spread across, the span below and above the aim (deg); reach (m);
     * the light near the lamp and at the reach; the extra reach on the right edge (share).
     */
    public record Pattern(double aimDown, double across, double below, double above, double reach, int near, int far, double rightReach) {}

    public static final Pattern LOW = new Pattern(1.2, 24, 8, 4.0, 36, 14, 6, 0.15),
            HIGH = new Pattern(0.0, 14, 5, 4, 64, 14, 7, 0);
    /** Full light this close (m); rays start this far out (inside the bumper). */
    public static final double NEAR = 4, START = 0.4;
    /** March step along a ray (m); ray spacing across and up (deg). */
    public static final double STEP = 0.3, RAY_ACROSS = 1.5, RAY_UP = 1.2;
    /** Light lost at the spread's edge (levels). */
    public static final double EDGE = 5;

    private HeadlightBeam() {
    }

    /**
     * One beam into {@code out} (cell key BlockPos#asLong -> light 1..15, the highest kept). The lamp at (ox, oy, oz),
     * forward and up unit vectors of the truck (world). {@code cache} keeps the world's answers across beams.
     */
    public static void cast(double ox, double oy, double oz, double[] fwd, double[] up, Pattern p, World w, Long2ByteOpenHashMap out,
                            Long2ObjectOpenHashMap<Cell> cache) {
        double[] right = {fwd[1] * up[2] - fwd[2] * up[1], fwd[2] * up[0] - fwd[0] * up[2], fwd[0] * up[1] - fwd[1] * up[0]};
        for (double h = -p.across(); h <= p.across() + 1e-6; h += RAY_ACROSS) {
            double hr = Math.toRadians(h), share = h / p.across();
            double reach = p.reach() * (1 + p.rightReach() * Math.max(0, share));
            for (double v = -p.below(); v <= p.above() + 1e-6; v += RAY_UP) {
                double vr = Math.toRadians(v - p.aimDown()), cv = Math.cos(vr);
                double dx = fwd[0] * cv * Math.cos(hr) + right[0] * cv * Math.sin(hr) + up[0] * Math.sin(vr);
                double dy = fwd[1] * cv * Math.cos(hr) + right[1] * cv * Math.sin(hr) + up[1] * Math.sin(vr);
                double dz = fwd[2] * cv * Math.cos(hr) + right[2] * cv * Math.sin(hr) + up[2] * Math.sin(vr);
                double vs = v < 0 ? v / p.below() : v / p.above();
                double edge = EDGE * (share * share + vs * vs);
                ray(ox, oy, oz, dx, dy, dz, reach, p, edge, w, out, cache);
            }
        }
    }

    private static void ray(double ox, double oy, double oz, double dx, double dy, double dz, double reach, Pattern p, double edge, World w,
                            Long2ByteOpenHashMap out, Long2ObjectOpenHashMap<Cell> cache) {
        long last = Long.MIN_VALUE;
        Cell c = Cell.OPEN;
        double span = Math.log(reach / NEAR), top = 1;
        for (double d = START; d <= reach; d += STEP) {
            double py = oy + dy * d;
            int x = (int) Math.floor(ox + dx * d), y = (int) Math.floor(py), z = (int) Math.floor(oz + dz * d);
            long key = BlockPos.asLong(x, y, z);
            if (key != last) {
                last = key;
                c = cache.get(key);
                if (c == null) {
                    c = w.cell(x, y, z);
                    cache.put(key, c);
                }
                if (c == Cell.OPAQUE) return;
                if (c == Cell.SURFACE) top = w.surfaceTop(x, y, z);
                double t = d <= NEAR ? 0 : Math.log(d / NEAR) / span;
                int level = (int) Math.floor(p.near() - (p.near() - p.far()) * t - edge);
                if (level > 0 && level > out.get(key)) out.put(key, (byte) Math.min(15, level));
                // an open cell's light reaches the partial block under it one level down (as vanilla light spreads): a
                // thick road layer's top face reads its own cell, which a beam skimming over it never enters
                if (c == Cell.OPEN && level > 1) {
                    long below = BlockPos.asLong(x, y - 1, z);
                    Cell b = cache.get(below);
                    if (b == null) {
                        b = w.cell(x, y - 1, z);
                        cache.put(below, b);
                    }
                    if (b == Cell.SURFACE && level - 1 > out.get(below)) out.put(below, (byte) Math.min(15, level - 1));
                }
            }
            // into a surface's solid part: stopped there
            if (c == Cell.SURFACE && py - y <= top) return;
        }
    }
}
