package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The U1 underground plan of one seed (docs/worldgen/underground_geology_caves_v0_design.md 7–9 and 19): sparse
 * objects (caves, giant caves, entrances, ore bodies, mines, the research facility), the shapes that hollow them, a
 * 512 m spatial index over the shapes, the route graph through them (places, steps, pitches, platforms, anchors), the
 * document points and the survivor succession candidates. Built once per seed by {@link UndergroundPlanner}; queries
 * only. Not read by any world generation yet (U2 decides that).
 */
public final class UndergroundPlan {
    /** The spatial index's cell, m (7.1). */
    public static final double INDEX_CELL = 512;

    // ------------------------------------------------------------------ shapes

    /** One hollow (or, for a pillar or a talus, solid) volume. owner = the cave object. */
    public sealed interface Shape permits Ellipsoid, Capsule, Box, Pillar, Open, Talus {
        int owner();

        /** {minX, minY, minZ, maxX, maxY, maxZ}. */
        double[] bounds();

        boolean inside(double x, double y, double z);

        /** Solid rock left in a hollow (a pillar, a talus), not a hollow itself. */
        default boolean solid() { return false; }
    }

    /**
     * U4 (22.2–22.3): a cut open to the sky, the ground taken down to floorAt inside its footprint. It cuts only what is
     * solid, so above the planned ground it changes nothing and the ground after the cut is min(planned, floorAt).
     */
    public sealed interface Open extends Shape permits Bowl, Notch, Pit {
        /** The cut's floor over (x, z), +inf outside the footprint. */
        double floorAt(double x, double z);
    }

    /** Wobble of a bowl's outline (share of the radius, either way). */
    public static final double BOWL_WOB = 0.08;

    /**
     * An entrance bowl (22.2): the flat floor floorY out to rf, then a wall rising `slope` per m (easing in over its
     * first 2 m) until it meets the ground, so on a hillside the uphill wall runs further out than the downhill one; R
     * is where it is over top, the highest ground round it. rf and R wobble with the angle by up to BOWL_WOB. water: its
     * pond's level (-inf dry), whatever its cave's water level.
     */
    public record Bowl(int owner, double x, double z, double floorY, double rf, double R, double top, double slope, int salt, double water) implements Open {
        /** The wall's foot, where it eases from the floor into its slope. */
        public static final double TOE = 2;

        public double[] bounds() {
            double e = R * (1 + BOWL_WOB);
            return new double[]{x - e, floorY, z - e, x + e, top, z + e};
        }

        public double floorAt(double px, double pz) {
            double dx = px - x, dz = pz - z, r = Math.sqrt(dx * dx + dz * dz);
            if (r >= R * (1 + BOWL_WOB)) return Double.POSITIVE_INFINITY;
            double rho = r / scale(dx, dz, r);
            if (rho <= rf) return floorY;
            if (rho >= R) return Double.POSITIVE_INFINITY;
            double u = rho - rf;
            return floorY + slope * (u < TOE ? u * u / (2 * TOE) : u - TOE / 2);
        }

        /** The outline's stretch in the direction (dx, dz): smooth and periodic in the angle. */
        double scale(double dx, double dz, double r) {
            if (r < 1e-6) return 1;
            return 1 + BOWL_WOB * (2 * valueNoise(50 + 1.6 * dx / r, 50 + 1.6 * dz / r, 0, salt) - 1);
        }

        public boolean inside(double px, double py, double pz) { return py < top && py > floorAt(px, pz); }
    }

    /**
     * A mouth's porch (22.3), cut back into the slope from the mouth (x, z) downhill along the unit (dx, dz): the floor
     * falls 0.1 per m from floorY, the back, the front past len and the sides (half width w at the floor) lean out as
     * they rise, so the cut meets the ground without a step. Always dry.
     */
    public record Notch(int owner, double x, double z, double dx, double dz, double floorY, double len, double w, double top) implements Open {
        public double[] bounds() {
            double hgt = top - floorY + 0.1 * len, a0 = -0.8 * hgt, a1 = len + 0.8 * hgt, l = w + 0.6 * hgt;
            double[] xs = {x + dx * a0 - dz * l, x + dx * a0 + dz * l, x + dx * a1 - dz * l, x + dx * a1 + dz * l};
            double[] zs = {z + dz * a0 + dx * l, z + dz * a0 - dx * l, z + dz * a1 + dx * l, z + dz * a1 - dx * l};
            return new double[]{Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3])), floorY - 0.1 * a1, Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3])),
                    Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3])), top, Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]))};
        }

        public double floorAt(double px, double pz) {
            double qx = px - x, qz = pz - z, a = qx * dx + qz * dz, l = Math.abs(-qx * dz + qz * dx);
            double f = floorY - 0.1 * Math.max(0, a);
            double c = Math.max(f, Math.max(f + (l - w) / 0.6, Math.max(f - a / 0.8, f + (a - len) / 0.8)));
            return c >= top ? Double.POSITIVE_INFINITY : c;
        }

        public boolean inside(double px, double py, double pz) { return py < top && py > floorAt(px, pz); }
    }

    /**
     * A great sinkhole (22.8), a big chamber's roof fallen in: the flat floor floorY inside a lobed outline (radius r,
     * stretched along the unit (ux, uz) the chamber's passages run), cliffs up to collarY in bedding bands `band` m high,
     * each band's face set back by a sparse ledge and roughened by its own amount (never back in, so the ground stays
     * one height per column), then the collar's shelf and its slope, 3–9 m of it, and over that the cliff again up to the
     * ground (top: the highest ground round it), so a pit under a hill keeps its footprint. At the
     * angles k0..k3 (NaN: unused; the rim, the talus side, the rope stages) the face stays at r all the way up, so the
     * rigging of 19.2 stands where it would on a round shaft.
     */
    public record Pit(int owner, double x, double z, double floorY, double r, double collarY, double top, double ux, double uz,
                      double band, double k0, double k1, double k2, double k3, int salt) implements Open {
        /** Outline lobes and stretch (shares of r), a ledge's width and the most the bands step back in all, the face's roughness, m. */
        public static final double LOBE = 0.14, STRETCH = 0.08, LEDGE = 2.2, STEP_BACK = 6, ROUGH = 0.7;
        /** The collar: its shelf at collarY, then its slope per m, easing in over its first TOE m, RUN to RUN + RUN_VAR m long. */
        public static final double SHELF = 2.5, COLLAR = 0.6, TOE = 2, RUN = 3, RUN_VAR = 6;

        /** The farthest the cliff top reaches from (x, z). */
        public double cliffReach() { return r * (1 + LOBE + STRETCH) + STEP_BACK + ROUGH; }

        /** The footprint's radius: the cliff top, the shelf and the collar's slope at its longest. */
        public double outer() { return cliffReach() + SHELF + RUN + RUN_VAR + 0.5; }

        public double[] bounds() {
            double e = outer();
            return new double[]{x - e, floorY, z - e, x + e, top, z + e};
        }

        /** 1 away from the kept angles, 0 at them (smooth over max(0.2 rad, 9 m of arc)). */
        double weight(double cx, double cz) {
            double th = Math.atan2(cz, cx), wd = Math.max(0.2, 9 / r), w = 1;
            for (double k : new double[]{k0, k1, k2, k3}) {
                if (Double.isNaN(k)) continue;
                double d = Math.abs(Math.IEEEremainder(th - k, 2 * Math.PI)) / wd;
                if (d < 1) w *= d * d * (3 - 2 * d);
            }
            return w;
        }

        /** The face's distance from the centre at the floor, in the unit direction (cx, cz) of weight w. */
        public double base(double cx, double cz, double w) {
            double d = cx * ux + cz * uz;
            return r * (1 + w * (STRETCH * (2 * d * d - 1) + LOBE * (2 * valueNoise(50 + 1.3 * cx, 50 + 1.3 * cz, 0, salt) - 1)));
        }

        /** The face of band j given the face of the band under it (cum[0]: the step-backs so far, carried up). */
        double face(double cx, double cz, double w, double base, int j, double below, double[] cum) {
            if (w <= 0) return base;
            if (j > 0) {
                double l = (valueNoise(cx * r / 14, j * 0.7, cz * r / 14, salt + 3) - 0.55) / 0.3;
                cum[0] = Math.min(STEP_BACK, cum[0] + LEDGE * Math.max(0, Math.min(1, l)));
            }
            return Math.max(below, base + w * (cum[0] + ROUGH * (2 * valueNoise(cx * r / 3.5, j * 1.7, cz * r / 3.5, salt + 5) - 1)));
        }

        public double floorAt(double px, double pz) {
            double dx = px - x, dz = pz - z, rr = Math.sqrt(dx * dx + dz * dz);
            if (rr >= outer()) return Double.POSITIVE_INFINITY;
            double cx = rr < 1e-6 ? 1 : dx / rr, cz = rr < 1e-6 ? 0 : dz / rr, w = weight(cx, cz), base = base(cx, cz, w);
            if (rr < base) return floorY;
            double[] cum = {0};
            double f = face(cx, cz, w, base, 0, base, cum);
            if (rr < f) return floorY;
            int n = Math.max(1, (int) Math.ceil((collarY - floorY) / band));
            for (int j = 1; j < n; j++) {
                f = face(cx, cz, w, base, j, f, cum);
                if (rr < f) return Math.min(collarY, floorY + j * band);
            }
            double u = rr - f - SHELF;
            if (u <= 0) return collarY;
            if (u >= RUN + RUN_VAR * valueNoise(cx * r / 10, 0, cz * r / 10, salt + 7)) return Double.POSITIVE_INFINITY;
            double c = collarY + COLLAR * (u < TOE ? u * u / (2 * TOE) : u - TOE / 2);
            return c >= top ? Double.POSITIVE_INFINITY : c;
        }

        public boolean inside(double px, double py, double pz) { return py < top && py > floorY && py > floorAt(px, pz); }
    }

    /**
     * Solid (22.4): a rubble cone on the floor of the hollow `host` (a shape index), apex (x, apexY, z), flanks rising
     * `slope` per m, from y0 up, the surface roughened by up to 0.3 m away from the apex. It fills only its host.
     */
    public record Talus(int owner, int host, double x, double z, double apexY, double slope, double y0, int salt) implements Shape {
        public double[] bounds() {
            double l = (apexY + 0.3 - y0) / slope;
            return new double[]{x - l, y0, z - l, x + l, apexY + 0.3, z + l};
        }

        /** The cone's surface over (px, pz). */
        public double surfaceAt(double px, double pz) {
            double r = Math.hypot(px - x, pz - z);
            return apexY - slope * r + 0.3 * (2 * valueNoise(px / 2.5, 0, pz / 2.5, salt) - 1) * Math.min(1, r / 3);
        }

        public boolean inside(double px, double py, double pz) { return py >= y0 && py < surfaceAt(px, pz); }

        @Override public boolean solid() { return true; }
    }

    /** Chamber kinds (21.3): a plain dome, or a breakdown room (flat bedding ceiling, rubble heaped along the walls). */
    public static final int DOME = 0, BREAKDOWN = 1;

    /**
     * A chamber: an ellipsoid with a flat floor at floorY. room: DOME or BREAKDOWN; rough: how far the wall relief
     * pulls the walls and roof in (share of the radius, never out, so bounds and roofAt stay upper limits).
     */
    public record Ellipsoid(int owner, double x, double y, double z, double rx, double ry, double rz, double floorY, int room, double rough) implements Shape {
        public Ellipsoid(int owner, double x, double y, double z, double rx, double ry, double rz, double floorY) {
            this(owner, x, y, z, rx, ry, rz, floorY, DOME, 0);
        }

        public double[] bounds() { return new double[]{x - rx, floorY, z - rz, x + rx, y + ry, z + rz}; }

        public boolean inside(double px, double py, double pz) {
            if (py < floorY) return false;
            double a = (px - x) / rx, b = (py - y) / ry, c = (pz - z) / rz, r2 = a * a + b * b + c * c;
            if (r2 >= 1) return false;
            if (room == DOME && rough <= 0) return true;
            double cap = Double.POSITIVE_INFINITY;
            if (room == BREAKDOWN) {
                double hgt = y + ry - floorY;
                cap = floorY + 0.8 * hgt;
                if (py >= cap) return false;
                // rubble heaped along the walls; the middle (where the place stands) stays clear
                double rn = Math.sqrt(a * a + c * c);
                if (rn > 0.7) {
                    double ramp = Math.min(1, (rn - 0.7) / 0.25);
                    double mound = Math.min(3, 0.25 * hgt) * ramp * ramp * (0.45 + 0.55 * valueNoise(px / 6, 0, pz / 6, owner + 7));
                    if (py < floorY + mound) return false;
                }
            }
            double r = Math.sqrt(r2);
            if (r < 1 - rough && py < cap - rough * ry) return true;
            double wn = wallNoise(px, py, pz, owner);
            double gq = 1 - rough * wn * Math.min(1, (py - floorY) / 1.2);
            return r < gq && py < cap - rough * ry * wn;
        }

        /** The roof's Y over (px, pz), or NaN outside the footprint. */
        public double roofAt(double px, double pz) {
            double a = (px - x) / rx, c = (pz - z) / rz, q = 1 - a * a - c * c;
            return q <= 0 ? Double.NaN : y + ry * Math.sqrt(q);
        }
    }

    /** Passage sections (21.3): the old ellipse, a phreatic tube, a vadose canyon, a keyhole, a bedding-plane low passage. */
    public static final int SMOOTH = 0, TUBE = 1, CANYON = 2, KEYHOLE = 3, LOW = 4;

    /**
     * A passage or shaft: a capsule a-b with an elliptic section (half width rh, half height rv); a gentle one has a flat
     * floor. profile: the section (21.3) inside the rh x rv envelope; rough: the wall relief (share of the section,
     * pulled in only); stream: depth of a water trench under the floor (0 none), kept `clear` m from both ends.
     */
    public record Capsule(int owner, double ax, double ay, double az, double bx, double by, double bz, double rh, double rv,
                          int profile, double rough, double stream, double clear) implements Shape {
        public Capsule(int owner, double ax, double ay, double az, double bx, double by, double bz, double rh, double rv) {
            this(owner, ax, ay, az, bx, by, bz, rh, rv, SMOOTH, 0, 0, 0);
        }

        public double[] bounds() {
            double below = Math.max(rv, (rv >= 1.5 ? 0.7 * rv : rv) + stream);
            return new double[]{Math.min(ax, bx) - rh, Math.min(ay, by) - below, Math.min(az, bz) - rh,
                    Math.max(ax, bx) + rh, Math.max(ay, by) + rv, Math.max(az, bz) + rh};
        }

        /** Clear height over the floor in the middle of the section, the relief taken at its worst (walk's sump test). */
        public double headroom() {
            double top = switch (profile) {
                case TUBE -> 0.75;
                case LOW -> 0.6;
                default -> 1;
            };
            return ((rv >= 1.5 ? 0.7 : 1) + top * (1 - rough)) * rv;
        }

        /** Whether the point is in the stream trench (water whatever the cave's water level). */
        public boolean inTrench(double px, double py, double pz) { return stream > 0 && where(px, py, pz) == 2; }

        public double length() { return Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az)); }

        public boolean vertical() { return Math.abs(by - ay) > 1.5 * Math.hypot(bx - ax, bz - az); }

        public boolean inside(double px, double py, double pz) {
            if (profile != SMOOTH || rough > 0 || stream > 0) {
                double lh = Math.hypot(bx - ax, bz - az);
                if (lh > 1e-6 && !vertical()) return where(px, py, pz) != 0;
            }
            double k = rh / rv;
            double ux = bx - ax, uy = (by - ay) * k, uz = bz - az, l2 = ux * ux + uy * uy + uz * uz;
            double qx = px - ax, qy = (py - ay) * k, qz = pz - az;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, (qx * ux + qy * uy + qz * uz) / l2));
            double dx = qx - ux * t, dy = qy - uy * t, dz = qz - uz * t;
            if (dx * dx + dy * dy + dz * dz >= rh * rh) return false;
            if (rv >= 1.5 && !vertical()) return py >= ay + (by - ay) * t - 0.7 * rv;
            return true;
        }

        /**
         * The shaped section in the passage's own frame (along s, lateral d, up v from the axis): 0 rock, 1 open,
         * 2 the stream trench. Gentle passages only (shafts keep the plain capsule).
         */
        int where(double px, double py, double pz) {
            double ux = bx - ax, uz = bz - az, lh = Math.hypot(ux, uz), hx = ux / lh, hz = uz / lh;
            double qx = px - ax, qz = pz - az;
            double sa = qx * hx + qz * hz, t = Math.max(0, Math.min(1, sa / lh));
            double e = sa < 0 ? -sa : sa > lh ? sa - lh : 0;
            double d = -qx * hz + qz * hx, v = py - (ay + (by - ay) * t);
            double fl = rv >= 1.5 ? -0.7 * rv : -rv;
            // a canyon swings from wall to wall; its trench follows the swing
            double m = 0;
            if (profile == CANYON || profile == KEYHOLE) {
                double ph = 6.2832 * frac(owner * 0.618 + ax * 0.0137 + az * 0.0071), wl = 16 + 10 * frac(owner * 0.377 + 0.5);
                m = (profile == CANYON ? 0.32 : 0.16) * Math.sin(6.2832 * sa / wl + ph) + (profile == CANYON ? 0.1 : 0.05) * Math.sin(14.5 * sa / wl + 1.3 * ph);
            }
            if (v < fl) {
                if (stream <= 0 || v < fl - stream || sa < clear || sa > lh - clear) return 0;
                return Math.abs(d / rh - m) * rh < 0.55 + 0.1 * rh ? 2 : 0;
            }
            double X = d / rh, Y = v / rv, E = e / rh;
            double r = switch (profile) {
                case TUBE -> Math.max(Math.sqrt(X * X + Y * Y + E * E), Y / 0.75);
                case CANYON -> {
                    double w = 0.55 * (1 - 0.45 * Math.max(0, Y) * Math.max(0, Y));
                    yield Math.max(Math.hypot((X - m) / w, E / w), Y);
                }
                case KEYHOLE -> {
                    double tube = Math.sqrt(X * X + (Y - 0.35) * (Y - 0.35) / (0.65 * 0.65) + E * E);
                    double slot = Y <= 0.35 ? Math.hypot((X - m) / 0.32, E / 0.32) : Double.POSITIVE_INFINITY;
                    yield Math.min(tube, slot);
                }
                case LOW -> Math.max(Math.sqrt(X * X * X * X + E * E * E * E + Y * Y), Y / 0.6);
                default -> Math.sqrt(X * X + Y * Y + E * E);
            };
            if (r >= 1) return 0;
            if (r < 1 - rough) return 1;
            double wn = wallNoise(px, py, pz, owner);
            return r < 1 - rough * wn * Math.min(1, (v - fl) / 1.2) ? 1 : 0;
        }
    }

    static double frac(double x) { return x - Math.floor(x); }

    /**
     * The wall relief (21.3), 0..1: a bump field of about 4.5 m (squashed along the bedding), a finer one of 1.7 m, and
     * bedding ledges every 2.4 m of height. Pure function of the point and the cave, so every chunk agrees.
     */
    static double wallNoise(double x, double y, double z, int owner) {
        double n = 0.6 * valueNoise(x / 4.5, y / 3.0, z / 4.5, owner) + 0.25 * valueNoise(x / 1.7, y / 1.7, z / 1.7, owner + 1);
        return Math.min(1, n + 0.15 * frac((y + 0.37 * owner) / 2.4));
    }

    /** Trilinear value noise on the unit lattice, 0..1. */
    static double valueNoise(double x, double y, double z, int salt) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        double c00 = lerp(lattice(ix, iy, iz, salt), lattice(ix + 1, iy, iz, salt), fx);
        double c10 = lerp(lattice(ix, iy + 1, iz, salt), lattice(ix + 1, iy + 1, iz, salt), fx);
        double c01 = lerp(lattice(ix, iy, iz + 1, salt), lattice(ix + 1, iy, iz + 1, salt), fx);
        double c11 = lerp(lattice(ix, iy + 1, iz + 1, salt), lattice(ix + 1, iy + 1, iz + 1, salt), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static double lattice(int x, int y, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** A built room or tunnel box (the facility, a mine level, a ledge niche), turned by yaw about its centre. */
    public record Box(int owner, double x, double y, double z, double hx, double hy, double hz, double yaw) implements Shape {
        public double[] bounds() {
            double c = Math.abs(Math.cos(yaw)), s = Math.abs(Math.sin(yaw));
            double ex = c * hx + s * hz, ez = s * hx + c * hz;
            return new double[]{x - ex, y - hy, z - ez, x + ex, y + hy, z + ez};
        }

        public boolean inside(double px, double py, double pz) {
            double dx = px - x, dz = pz - z, c = Math.cos(yaw), s = Math.sin(yaw);
            double lx = dx * c + dz * s, lz = -dx * s + dz * c;
            return Math.abs(lx) < hx && Math.abs(py - y) < hy && Math.abs(lz) < hz;
        }
    }

    /** Solid rock left standing inside a giant cave (subtracts from its owner's hollow). */
    public record Pillar(int owner, double x, double z, double r, double y0, double y1) implements Shape {
        public double[] bounds() { return new double[]{x - r, y0, z - r, x + r, y1, z + r}; }

        public boolean inside(double px, double py, double pz) {
            return py >= y0 && py <= y1 && (px - x) * (px - x) + (pz - z) * (pz - z) < r * r;
        }

        @Override public boolean solid() { return true; }
    }

    // ------------------------------------------------------------------ objects

    public enum CaveKind { SMALL_KARST, FISSURE, KARST_SYSTEM, FRACTURE_SYSTEM, CONNECTOR, GIANT, CANYON, SINKHOLE, BUILT }

    /**
     * A natural cave object. layer 2..4; waterY: below it the cave is flooded (13.9); host: the rock it is in;
     * shapes: indices into {@link #shapes}; places: its chambers and junctions in the route graph.
     */
    public static final class Cave {
        public final int id;
        public final CaveKind kind;
        public final int layer;
        public final double x, y, z;
        public final double waterY;
        public final RockType host;
        public final List<Integer> shapes = new ArrayList<>();
        public final List<Integer> places = new ArrayList<>();
        /** Systems: the karst unit or fault it follows; giant caves: a short label (G1..). */
        public String label = "";
        public double passageLength;

        public Cave(int id, CaveKind kind, int layer, double x, double y, double z, double waterY, RockType host) {
            this.id = id;
            this.kind = kind;
            this.layer = layer;
            this.x = x;
            this.y = y;
            this.z = z;
            this.waterY = waterY;
            this.host = host;
        }

        public boolean system() { return kind == CaveKind.KARST_SYSTEM || kind == CaveKind.FRACTURE_SYSTEM; }
    }

    /** A link between two caves in the connectivity graph (7.4): the passage that makes it, kind for the report. */
    public record CaveLink(int a, int b, String kind, int step) {}

    /** SPRING_MOUTH, KARST_WINDOW, CLIFF_MOUTH, WAVE_CUT: the states' own ways in (design 21.10). */
    public enum EntranceKind { COLLAPSE_PIT, COVER_SINKHOLE, GREAT_SINKHOLE, FISSURE, SHAFT, HILLSIDE_MOUTH, DRY_SPRING, MINE_ADIT, MINE_SHAFT, FACILITY,
        SPRING_MOUTH, KARST_WINDOW, CLIFF_MOUTH, WAVE_CUT }

    /** A way in from the surface (9.1). target: the cave it reaches (-1: a dead pit); place: its rim in the route graph. */
    public record Entrance(int id, EntranceKind kind, double x, double z, double surfaceY, double radius, double depth, int target, int place) {
        public boolean connected() { return target >= 0; }
    }

    public enum OreKind { COAL_SEAM, HYDROTHERMAL_VEIN, MVT_LEAD_ZINC, BANDED_IRON, NICKEL_COPPER, PEGMATITE, GREISEN_SKARN, BAUXITE, BRECCIA_PIPE }

    /** An ore district (10.2: the real mining districts the bodies gather in; mines go next to them). */
    public record District(int id, OreKind kind, double x, double z, double radius, int province) {}

    /** One ore body: an ellipsoid-ish volume (seams and blankets are flat, veins thin and tall, pipes vertical). */
    public record OreBody(int id, OreKind kind, int district, double x, double y, double z, double rx, double ry, double rz, double yaw, RockType host) {}

    /** An abandoned mine candidate (U7 builds it): adit or shaft, its levels' Y, its places. */
    public record Mine(int id, int district, EntranceKind kind, double x, double z, double surfaceY, double[] levels, int entrance, List<Integer> places) {}

    /** A part of the research facility (role names the design's list, 19.5), its box and its place. */
    public record FacilityPart(String role, Shape shape, int place, boolean surface) {}

    /**
     * The one national research facility candidate (19.5): its parts, the service road from the nearest C road to the
     * hidden entrance, the protection boxes (parts grown by the margin), and the rock it is cut in.
     */
    public record Facility(int id, double x, double z, double yaw, List<FacilityPart> parts, double[] serviceRoad,
                           double serviceGrade, List<Box> protect, RockType host, int link) {}

    public enum DocKind { RESEARCH_RECORD, ENGINEERING_LOG, ACCIDENT_REPORT, SURVEY_MAP, RADIO_MATERIAL, NOTE }

    /** Where a readable document can lie (19.6); host names the object, the text belongs to the narrative pass. */
    public record DocumentSite(int id, DocKind kind, double x, double y, double z, String host, int place) {}

    // ------------------------------------------------------------------ route graph

    /** What a step needs (19.2): light, rope length (m), anchors, a climbing kit (V3), a crawl, a dive (never planned through). */
    public record Need(boolean light, int rope, int anchors, boolean climb, boolean crawl, boolean dive) {
        public static final Need NONE = new Need(false, 0, 0, false, false, false);
        public static final Need LIGHT = new Need(true, 0, 0, false, false, false);

        public Need plus(Need o) {
            return new Need(light || o.light, Math.max(rope, o.rope), Math.max(anchors, o.anchors), climb || o.climb, crawl || o.crawl, dive || o.dive);
        }

        public String text() {
            List<String> s = new ArrayList<>();
            if (light) s.add("light");
            if (rope > 0) s.add("rope " + rope + " m");
            if (anchors > 0) s.add(anchors + " anchors");
            if (climb) s.add("climbing kit");
            if (crawl) s.add("crawl");
            if (dive) s.add("dive");
            return s.isEmpty() ? "nothing" : String.join(" + ", s);
        }
    }

    /** What a survivor carries: the kits the checks walk with (19.3). */
    public record Kit(String name, boolean light, int rope, int anchors, boolean climb) {
        public boolean meets(Need n) {
            return n != null && !n.dive && (!n.light || light) && n.rope <= rope && n.anchors <= anchors && (!n.climb || climb);
        }
    }

    /** The checks' kit ladder: nothing, a light, V2 rope work (30 / 60 m), V3 climbing. */
    public static final List<Kit> KITS = List.of(
            new Kit("none", false, 0, 0, false),
            new Kit("light", true, 0, 0, false),
            new Kit("rope30", true, 30, 4, false),
            new Kit("rope60", true, 60, 8, false),
            new Kit("rope120+climb", true, 120, 16, true));

    public enum PlaceKind { SURFACE, CHAMBER, JUNCTION, LEDGE, PITCH_TOP, PITCH_BOTTOM, FACILITY, MINE, CAMP }

    /** A standable spot (19.2): centre on the floor, half sizes, clear height above the floor. */
    public record Platform(double x, double y, double z, double hx, double hz, double headroom) {
        public double area() { return 4 * hx * hz; }
    }

    /** A place in the route graph. cave: the cave object (-1 surface / facility / mine); platform: where one stands. */
    public record Place(int id, PlaceKind kind, double x, double y, double z, int cave, Platform platform) {}

    public enum StepKind { WALK, STEP, SLOPE, CRAWL, ROPE, FIXED_ROPE, ROPE_LADDER, FIXED_LADDER, SUMP }

    /**
     * A step between two places. ab / ba: what each direction needs (a pitch needs rope down as up, so the checks find
     * a trap if they ever differ); alt: the V3 free-climb alternative (null when the rock or the height forbids it);
     * haul: how hard it is to carry a heavy load up it (0 flat .. 3 a long hauling pitch).
     */
    public record Step(int id, int a, int b, StepKind kind, double dy, double length, Need ab, Need ba, Need alt, int haul) {}

    /** A rope or ladder anchor on the wall (19.2): where, the rock it is in, whether that rock holds a bolt. */
    public record Anchor(double x, double y, double z, RockType rock) {
        public boolean good() { return rock != null && rock.anchor; }
    }

    public enum Fixture { NONE, FIXED_ROPE, ROPE_LADDER, FIXED_LADDER }

    /**
     * A vertical stage (19.2): its step(s) top to bottom (more than one when rebelay ledges split it), its height, the
     * top and bottom places, the rigging places (top, rebelay ledges) with the anchors at each, the shaft's axis and
     * radius, the fixture the world provides, the slope step that avoids it (-1 none), whether the way out needs rope or
     * climbing gear, and the heavy-load evacuation difficulty.
     */
    public record Pitch(int id, List<Integer> steps, double height, int top, int bottom, List<Integer> rigs, List<int[]> anchorsAt,
                        double ax, double az, double rh, Fixture fixture, int slopeAlt, boolean needsGearOut, int haul, int owner, boolean dark) {}

    /** One exit from a place to the surface (19.7). */
    public record Exit(int surface, Need need, int steps, double length, double climbUp, int pitches, int haul, boolean reliable, List<Integer> path) {}

    public enum Env { CAVE_CHAMBER, DEEP_CHAMBER, GIANT_CAVE, SINKHOLE_FLOOR, MINE_LEVEL, FACILITY_ROOM, SURVEY_CAMP }

    public enum Background { CAVER, SURVEYOR, HIKER, MINER, SCAVENGER, RESEARCHER, FACILITY_STAFF }

    /**
     * A survivor succession / special start candidate (19.7): data only (no takeover logic, kit, identity or story).
     * kit: the least the survivor must carry to walk out (the reliable exit's need); hazard 0 (safe) .. 4 (deadly);
     * checkpoints: points along the best exit that must be open after generation (re-checked against the real blocks).
     */
    public record Succession(int id, Env env, double x, double y, double z, int place, String host, Platform safe,
                             List<Exit> exits, Kit kit, EnumSet<Background> backgrounds, int hazard, boolean firstStart,
                             boolean succession, List<double[]> checkpoints) {}

    public enum DestinationKind { START_CAVE, GIANT_CAVE, CANYON }

    /** The start cave's English name (U5 sample, design 24; the user's choice 2026-10-22). */
    public static final String START_CAVE_NAME = "Bear Hollow Cave";

    /**
     * A cave as a destination (U5, design 8.4 and 24): its name, the entrance a kit walks in by and the room it leads
     * to, data only (no story; the map, documents and the wiki read it). kit: the least that walks in (KITS index);
     * length / drop: the route's walk and how far it goes down; route: {x, y, z} from the entrance rim to the room;
     * from the home: straight-line distance and bearing (degrees, 0 north, clockwise); the nearest road point and the
     * straight walk from it to the rim.
     */
    public record Destination(int id, DestinationKind kind, String name, int cave, int entrance, int room, int kit,
                              double length, double drop, List<double[]> route, double homeDistance, double homeBearing,
                              double roadX, double roadZ, double roadWalk) {}

    /**
     * U5b (design 24.5): the footpath to a destination's mouth from the nearest two-lane or county road. line: {x, z, y}
     * a metre apart from the trailhead on the road's shoulder to the mouth's rim (y the planned ground after the cuts);
     * length: its walk; grade: its steepest 10 m; fords: the streams it wades; road: the design it leaves ("town:" and
     * a town's name from a town's edge); the trailhead sign, the caution sign before the mouth and the marker posts as
     * road fixtures (block/TunnelFixtureBlock trail_sign / cave_sign / trail_marker; null: no room), each wholly in one
     * chunk, y the planned ground (CaveTrailPass stands them on the real one).
     */
    public record Trail(int destination, double[] line, double length, double grade, int fords, String road,
                        TunnelMesh.Placement sign, TunnelMesh.Placement caution, List<TunnelMesh.Placement> markers) {
        public int points() { return line.length / 3; }
    }

    /** The boulder forms the plan names (Rock R1a's data ids, docs/gameplay/rock_r1_stones_boulders_design.md 11.1). */
    public static final String[] ROCK_FORMS = {"erratic_round", "erratic_long", "erratic_big", "slab_flat", "slab_tilted", "talus_block",
            "talus_wedge", "split_pair", "tall_block", "small_round", "cluster_three", "cluster_two"};
    /** Their lengths, m (the same table). */
    public static final double[] ROCK_LENGTH = {1.6, 2.4, 3.0, 2.0, 1.7, 1.2, 1.4, 2.2, 1.0, 0.7, 0.9, 1.3};

    /** R3 (22.9): the rocks a boulder or loose stone looks like (boulder/BoulderRock ids, in its order). */
    public static final String[] ROCK_LOOKS = {"granite", "limestone", "sandstone", "shale"};

    /** The look a rock type's entrance rocks take (EntranceRocks#look), by name: RockTypes' boulder_look must agree. */
    public static String rockLook(RockType r) { return ROCK_LOOKS[EntranceRocks.look(r)]; }

    /**
     * U4b (22.7): a rock on an entrance cut, in the block column (x, z), on the planned ground y after the cut. form >= 0:
     * a boulder of {@link #ROCK_FORMS} at turn (its origin cell over the ground there); form -1: a patch of loose stones
     * (stones pebbles, a field stone, a flint). shape: the cut or talus it lies on; look: what rock it is of (R3,
     * {@link #ROCK_LOOKS}).
     */
    public record Rock(int x, double y, int z, int form, int turn, int stones, boolean cobble, boolean flint, int shape, int look) {
        public boolean boulder() { return form >= 0; }
    }

    // ------------------------------------------------------------------ the plan

    public final long seed;
    public final int minY;
    public final List<Shape> shapes = new ArrayList<>();
    /** Shapes that are entrance throats or built works: allowed through layer 1 and exempt from the cave layer rules. */
    public final java.util.BitSet throats = new java.util.BitSet();
    public final List<Cave> caves = new ArrayList<>();
    public final List<CaveLink> links = new ArrayList<>();
    public final List<Entrance> entrances = new ArrayList<>();
    public final List<District> districts = new ArrayList<>();
    public final List<OreBody> ores = new ArrayList<>();
    public final List<Mine> mines = new ArrayList<>();
    public Facility facility;
    /** The start dry cave (21.6): the system near the home that a light reaches from the surface, -1 none. */
    public int startCave = -1;
    public final List<DocumentSite> documents = new ArrayList<>();
    public final List<Place> places = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();
    public final List<Pitch> pitches = new ArrayList<>();
    public final List<Anchor> anchors = new ArrayList<>();
    public final List<Succession> successions = new ArrayList<>();
    public final List<Destination> destinations = new ArrayList<>();
    /** The entrance rocks (22.7), boulders first; by chunk in {@link #rocksIn}. */
    public final List<Rock> rocks = new ArrayList<>();
    private Map<Long, Rock[]> rockIndex = Map.of();
    /** The cave trails (U5b, 24.5); their segments by chunk {trail, first point} pairs and their fixtures by chunk. */
    public final List<Trail> trails = new ArrayList<>();
    private Map<Long, int[]> trailIndex = Map.of();
    private Map<Long, List<TunnelMesh.Placement>> trailParts = Map.of();
    /** Planning times, ms, by stage (the check's report). */
    public final Map<String, Long> times = new java.util.LinkedHashMap<>();

    /** Planner counters (rejections by reason and the like) for the check's report. */
    public final Map<String, Integer> notes = new java.util.TreeMap<>();

    public void note(String k) { notes.merge(k, 1, Integer::sum); }
    private Map<Long, int[]> index;
    private List<List<Integer>> adjacency;

    public UndergroundPlan(long seed, int minY) {
        this.seed = seed;
        this.minY = minY;
    }

    // ------------------------------------------------------------------ index and sampling

    static long cellKey(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    /** Builds the 512 m index (call after the last shape). */
    public void index() {
        Map<Long, List<Integer>> m = new HashMap<>();
        for (int i = 0; i < shapes.size(); i++) {
            double[] b = shapes.get(i).bounds();
            int x0 = (int) Math.floor(b[0] / INDEX_CELL), x1 = (int) Math.floor(b[3] / INDEX_CELL);
            int z0 = (int) Math.floor(b[2] / INDEX_CELL), z1 = (int) Math.floor(b[5] / INDEX_CELL);
            for (int cz = z0; cz <= z1; cz++) for (int cx = x0; cx <= x1; cx++) m.computeIfAbsent(cellKey(cx, cz), k -> new ArrayList<>()).add(i);
        }
        Map<Long, int[]> out = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> e : m.entrySet()) out.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        index = out;
    }

    /** Indexes the rocks by chunk, each chunk's in the plan's order (call after the last rock). */
    public void indexRocks() {
        Map<Long, List<Rock>> m = new HashMap<>();
        for (Rock r : rocks) m.computeIfAbsent(cellKey(r.x() >> 4, r.z() >> 4), k -> new ArrayList<>()).add(r);
        Map<Long, Rock[]> out = new HashMap<>();
        for (Map.Entry<Long, List<Rock>> e : m.entrySet()) out.put(e.getKey(), e.getValue().toArray(new Rock[0]));
        rockIndex = out;
    }

    private static final Rock[] NO_ROCKS = new Rock[0];

    /** Indexes the trails' segments (their bounds grown by reach) and their fixtures by chunk (call after the last trail). */
    public void indexTrails(double reach) {
        Map<Long, List<Integer>> m = new HashMap<>();
        Map<Long, List<TunnelMesh.Placement>> parts = new HashMap<>();
        for (int t = 0; t < trails.size(); t++) {
            Trail tr = trails.get(t);
            double[] l = tr.line();
            for (int i = 0; i + 5 < l.length; i += 3) {
                int x0 = (int) Math.floor((Math.min(l[i], l[i + 3]) - reach) / 16), x1 = (int) Math.floor((Math.max(l[i], l[i + 3]) + reach) / 16);
                int z0 = (int) Math.floor((Math.min(l[i + 1], l[i + 4]) - reach) / 16), z1 = (int) Math.floor((Math.max(l[i + 1], l[i + 4]) + reach) / 16);
                for (int cz = z0; cz <= z1; cz++) for (int cx = x0; cx <= x1; cx++) {
                    List<Integer> c = m.computeIfAbsent(cellKey(cx, cz), k -> new ArrayList<>());
                    c.add(t);
                    c.add(i / 3);
                }
            }
            List<TunnelMesh.Placement> all = new ArrayList<>(tr.markers());
            if (tr.sign() != null) all.add(0, tr.sign());
            if (tr.caution() != null) all.add(tr.caution());
            for (TunnelMesh.Placement p : all)
                parts.computeIfAbsent(cellKey((int) Math.floor(p.x()) >> 4, (int) Math.floor(p.z()) >> 4), k -> new ArrayList<>()).add(p);
        }
        Map<Long, int[]> out = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> e : m.entrySet()) out.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        trailIndex = out;
        trailParts = parts;
    }

    private static final int[] NO_SEGMENTS = new int[0];

    /** The trail segments near chunk (cx, cz) as {trail, first point} pairs (empty: none). */
    public int[] trailSegments(int cx, int cz) { return trailIndex.getOrDefault(cellKey(cx, cz), NO_SEGMENTS); }

    /** The trail fixtures standing in chunk (cx, cz). */
    public List<TunnelMesh.Placement> trailParts(int cx, int cz) { return trailParts.getOrDefault(cellKey(cx, cz), List.of()); }

    /**
     * The horizontal distance from (x, z) to the nearest trail's centreline and how far along that trail it lies, m
     * ({distance, along}); distance +inf when no trail segment is indexed near (only within the index's reach).
     */
    public double[] trailNear(double x, double z) {
        int[] segs = trailSegments((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        double best = Double.POSITIVE_INFINITY, along = 0;
        for (int k = 0; k + 1 < segs.length; k += 2) {
            double[] l = trails.get(segs[k]).line();
            int i = segs[k + 1] * 3;
            double ax = l[i], az = l[i + 1], dx = l[i + 3] - ax, dz = l[i + 4] - az, L2 = dx * dx + dz * dz;
            double f = L2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / L2));
            double d = Math.hypot(x - ax - f * dx, z - az - f * dz);
            if (d < best) { best = d; along = segs[k + 1] + f * Math.sqrt(L2); }
        }
        return new double[]{best, along};
    }

    /** The rocks whose column is in chunk (cx, cz). */
    public Rock[] rocksIn(int cx, int cz) { return rockIndex.getOrDefault(cellKey(cx, cz), NO_ROCKS); }

    /** The shape ids whose bounds overlap the index cell of (x, z) (empty array if none). */
    public int[] shapesNear(double x, double z) {
        int[] a = index.get(cellKey((int) Math.floor(x / INDEX_CELL), (int) Math.floor(z / INDEX_CELL)));
        return a == null ? new int[0] : a;
    }

    /** The cave hollow at a point (-1 = rock): any hollow shape there, unless its owner's pillar stands there. */
    public int caveAt(double x, double y, double z) {
        int i = shapeAt(x, y, z);
        return i < 0 ? -1 : shapes.get(i).owner();
    }

    /** The hollow shape at a point (-1 = rock), as {@link #caveAt}. */
    public int shapeAt(double x, double y, double z) { return shapeAmong(shapesNear(x, z), x, y, z); }

    /** The shape ids whose bounds overlap the column box [x0, x1] x [z0, z1] (U2: one chunk's list, built once per chunk). */
    public int[] shapesInBox(double x0, double z0, double x1, double z1) {
        java.util.TreeSet<Integer> out = new java.util.TreeSet<>();
        for (int cz = (int) Math.floor(z0 / INDEX_CELL); cz <= (int) Math.floor(z1 / INDEX_CELL); cz++)
            for (int cx = (int) Math.floor(x0 / INDEX_CELL); cx <= (int) Math.floor(x1 / INDEX_CELL); cx++) {
                int[] a = index.get(cellKey(cx, cz));
                if (a == null) continue;
                for (int i : a) {
                    double[] b = shapes.get(i).bounds();
                    if (b[3] >= x0 && b[0] <= x1 && b[5] >= z0 && b[2] <= z1) out.add(i);
                }
            }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** {@link #caveAt} over a given shape list (a chunk's, from {@link #shapesInBox}). */
    public int caveAmong(int[] ids, double x, double y, double z) {
        int i = shapeAmong(ids, x, y, z);
        return i < 0 ? -1 : shapes.get(i).owner();
    }

    /**
     * The hollow shape at a point among the ids (-1 = rock): the first that holds it, a stream trench only when no
     * other shape opens the point; -1 when the owner's pillar or the shape's own talus stands there.
     */
    public int shapeAmong(int[] ids, double x, double y, double z) {
        int found = -1, trench = -1;
        for (int i : ids) {
            Shape s = shapes.get(i);
            if (s.solid()) continue;
            double[] b = s.bounds();
            if (x < b[0] || y < b[1] || z < b[2] || x > b[3] || y > b[4] || z > b[5]) continue;
            if (!s.inside(x, y, z)) continue;
            if (s instanceof Capsule c && c.inTrench(x, y, z)) { if (trench < 0) trench = i; continue; }
            found = i;
            break;
        }
        if (found < 0) found = trench;
        if (found < 0) return -1;
        int owner = shapes.get(found).owner();
        for (int i : ids) {
            Shape s = shapes.get(i);
            if (s instanceof Pillar p && p.owner() == owner && p.inside(x, y, z)) return -1;
            if (s instanceof Talus t && t.host() == found && t.inside(x, y, z)) return -1;
        }
        return found;
    }

    /**
     * Whether a hollow point of the shape holds water: under its cave's water level, or in a stream trench (21.2); a
     * bowl by its own pond level, a porch never (22.2).
     */
    public boolean wetAt(int shape, double x, double y, double z) {
        Shape s = shapes.get(shape);
        if (s instanceof Bowl b) return y < b.water();
        if (s instanceof Notch) return false;
        return y < caves.get(s.owner()).waterY || s instanceof Capsule c && c.inTrench(x, y, z);
    }

    /** The ground after the cut over (x, z) (22.2): the planned ground, or an open cut's floor where that is lower. */
    public double groundAt(double x, double z, double planned) {
        double g = planned;
        for (int i : shapesNear(x, z)) if (shapes.get(i) instanceof Open o) g = Math.min(g, o.floorAt(x, z));
        return g;
    }

    public boolean airAt(double x, double y, double z) {
        int i = shapeAt(x, y, z);
        return i >= 0 && !wetAt(i, x, y, z);
    }

    public boolean waterAt(double x, double y, double z) {
        int i = shapeAt(x, y, z);
        return i >= 0 && wetAt(i, x, y, z);
    }

    public boolean openAt(double x, double y, double z) { return caveAt(x, y, z) >= 0; }

    // ------------------------------------------------------------------ graph helpers

    public int addPlace(PlaceKind kind, double x, double y, double z, int cave, Platform pf) {
        places.add(new Place(places.size(), kind, x, y, z, cave, pf));
        adjacency = null;
        return places.size() - 1;
    }

    public int addStep(int a, int b, StepKind kind, Need ab, Need ba, Need alt, int haul) {
        Place p = places.get(a), q = places.get(b);
        double len = Math.sqrt((p.x() - q.x()) * (p.x() - q.x()) + (p.y() - q.y()) * (p.y() - q.y()) + (p.z() - q.z()) * (p.z() - q.z()));
        steps.add(new Step(steps.size(), a, b, kind, q.y() - p.y(), len, ab, ba, alt, haul));
        adjacency = null;
        return steps.size() - 1;
    }

    /** After steps were rewritten in place (a ledge dropped for a free hang). */
    public void resetAdjacency() { adjacency = null; }

    /** Steps touching each place. */
    public List<List<Integer>> adjacency() {
        if (adjacency == null) {
            List<List<Integer>> a = new ArrayList<>();
            for (int i = 0; i < places.size(); i++) a.add(new ArrayList<>());
            for (Step s : steps) { a.get(s.a()).add(s.id()); a.get(s.b()).add(s.id()); }
            adjacency = a;
        }
        return adjacency;
    }

    /** The need to walk a step from place `from`, or with the kit's V3 alternative; null when the kit cannot. */
    public static Need needFrom(Step s, int from, Kit kit) {
        Need n = s.a() == from ? s.ab() : s.ba();
        if (kit.meets(n)) return n;
        if (s.alt() != null && kit.meets(s.alt())) return s.alt();
        return null;
    }
}
