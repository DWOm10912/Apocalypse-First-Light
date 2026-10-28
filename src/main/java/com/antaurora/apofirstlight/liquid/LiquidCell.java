package com.antaurora.apofirstlight.liquid;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.liquid.LiquidUnits.ML_PER_GROUND_UNIT;
import static com.antaurora.apofirstlight.liquid.LiquidUnits.SLABS;

/**
 * One cell of the liquid framework (docs/gameplay/water_v1_design.md 5.2, 5.3): one layer of a 1 m column, the run of
 * slabs between solid ground below and above it that has open room in every slab. It holds whole millilitres of one
 * liquid; its level comes from its fill curve, the open area of each slab added up (GroundView#openArea).
 * <p>
 * A layer that reaches the top of the engine's window is open to the sky ({@link #openTop}); its slabs stop a few metres
 * above the column's last solid (LiquidColumn#SKY_KEEP) and past them it is 1 m2 a metre. A capped layer can be filled
 * past its room only by a geometry change (ground pushed into it); then its head stands above the ceiling by the excess
 * spread over 1 m2, which drives the excess out through its faces (a pressure stand-in, 5.3).
 */
public final class LiquidCell {
    final LiquidColumn column;
    /** Absolute slabs: the lowest open slab, and one past the highest. */
    final int floorSlab, ceilSlab;
    /** Open area of each slab, 1/256 m2, all above 0. */
    final int[] area;
    /** prefix[k]: ground units (1/4096 m3) of room in the slabs below floorSlab + k. */
    final long[] prefix;
    final boolean openTop;

    Liquid liquid;
    long volume;
    /** The surface (or the floor while dry), metres; boundary cells: their fixed level. */
    double level;
    boolean boundary;
    /**
     * A river's water (a boundary; the river plan's steady flow field, R1): held at the river's surface, moving at the
     * field's velocity (m/s, east and south), and only taking water in (LiquidEngine#setRiver).
     */
    boolean river;
    float riverVx, riverVz;
    int quiet;
    boolean sleeping;
    LiquidBasin basin, lastBasin;
    final List<LiquidFace> faces = new ArrayList<>(4);
    /** The faces this cell owns (it is their west / north side), in the engine's face order (rebuilt with it). */
    final List<LiquidFace> owned = new ArrayList<>(2);
    /** The last tick water (or a boundary) taking part in the flow was in this cell or next to it. */
    long near = -1;
    /** Step scratch, mL: what flows out and in this step, and the room at its start. */
    double out, in, space;
    double lastLevel;
    /** The volume at the last quiet check, mL (a sliver's level jumps with every millilitre: ROCK_ML). */
    long lastVolume;
    /**
     * The film the floor holds (LiquidSoak): mL that do not run off, and what a film soaks in and dries a tick (mL, the
     * floor's open area times the rates; infiltration first). Set when the column is built.
     */
    long storage;
    double soakPerTick, dryPerTick, soakCarry;

    LiquidCell(LiquidColumn column, int floorSlab, int[] area, boolean openTop) {
        this.column = column;
        this.floorSlab = floorSlab;
        this.ceilSlab = floorSlab + area.length;
        this.area = area;
        this.openTop = openTop;
        prefix = new long[area.length + 1];
        for (int k = 0; k < area.length; k++) prefix[k + 1] = prefix[k] + area[k];
        level = floor();
        lastLevel = level;
    }

    public int x() { return column.x; }
    public int z() { return column.z; }
    public long volume() { return volume; }
    public double level() { return level; }
    public Liquid liquid() { return liquid; }
    public boolean sleeping() { return sleeping; }
    public boolean boundary() { return boundary; }
    public boolean river() { return river; }
    public double floor() { return floorSlab / (double) SLABS; }
    public double ceiling() { return ceilSlab / (double) SLABS; }
    public int floorSlab() { return floorSlab; }
    public boolean openTop() { return openTop; }
    public LiquidBasin basin() { return basin; }
    public long storage() { return storage; }

    /** {@link #joinedSides()} bits. */
    public static final int EAST = 1, SOUTH = 2, WEST = 4, NORTH = 8;

    /**
     * The sides where this cell is one body of water with its neighbour: both wet and the opening below both surfaces
     * (the surface's corners join only across these, so a wall or a step between two pools is not smoothed over).
     */
    public int joinedSides() {
        if (volume <= 0 && !boundary) return 0;
        int m = 0;
        for (LiquidFace f : faces) {
            LiquidCell o = f.other(this);
            if (o.volume <= 0 && !o.boundary) continue;
            if (f.crossSection(Math.min(level, o.level)) <= 0) continue;
            m |= f.a == this ? (f.south ? SOUTH : EAST) : (f.south ? NORTH : WEST);
        }
        return m;
    }

    /** Room in the layer, mL (geometry, not conserved). */
    public double capacity() {
        return prefix[area.length] * ML_PER_GROUND_UNIT;
    }

    /** Liquid this layer holds below height m, mL, when filled to m. */
    public double volumeBelow(double m) {
        double s = m * SLABS - floorSlab;
        if (s <= 0) return 0;
        int n = area.length;
        if (s >= n) return (prefix[n] + (openTop ? (s - n) * LiquidUnits.AREA : 0)) * ML_PER_GROUND_UNIT;
        int k = (int) s;
        return (prefix[k] + area[k] * (s - k)) * ML_PER_GROUND_UNIT;
    }

    /** The surface of {@code ml} in this layer, metres: the inverse of the fill curve; past the room, the stand-in head. */
    public double levelOf(long ml) {
        if (ml <= 0) return floor();
        double t = ml / ML_PER_GROUND_UNIT;
        int n = area.length;
        if (t >= prefix[n]) return ceilSlab / (double) SLABS + (t - prefix[n]) / 4096.0;
        int lo = 0, hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (prefix[mid] <= t) lo = mid;
            else hi = mid - 1;
        }
        return (floorSlab + lo + (t - prefix[lo]) / area[lo]) / SLABS;
    }

    /**
     * The water's mean velocity through this cell, m/s, into out: [0] east, [1] south (water_v1_design.md 5.8, package 4).
     * Each face's flow over the wet area it passes through, averaged over the faces on each axis, so water coming in from
     * the west and leaving east reads as one current. A sleeping cell's faces carry nothing: still water reads 0.
     */
    public void velocity(double[] out) {
        if (river) {
            out[0] = riverVx;
            out[1] = riverVz;
            return;
        }
        double x = 0, z = 0;
        int nx = 0, nz = 0;
        for (LiquidFace f : faces) {
            if (f.removed) continue;
            double area = f.crossSection(Math.max(f.a.level, f.b.level));
            if (area <= 1e-4) continue;
            double u = f.q / area;
            if (f.south) {
                z += u;
                nz++;
            } else {
                x += u;
                nx++;
            }
        }
        out[0] = nx == 0 ? 0 : x / nx;
        out[1] = nz == 0 ? 0 : z / nz;
    }

    /** A face carrying less than this (m3/s, 0.1 mL/s) carries nothing (running()). */
    static final double RUNNING_Q = 1e-7;

    /**
     * Running water (docs/gameplay/water_mesh_parcels_v1_design.md A1): awake, more water than the floor holds back as a
     * film (storage), and flowing through a face. The film rain leaves on the ground is not running: the floor holds it.
     */
    public boolean running() {
        if (sleeping || boundary || volume <= storage) return false;
        for (LiquidFace f : faces) if (!f.removed && Math.abs(f.q) > RUNNING_Q) return true;
        return false;
    }

    /** Is height m inside this layer (floor inclusive, ceiling exclusive; open to the sky: anything above the floor)? */
    public boolean contains(double m) {
        return m >= floor() && (openTop || m < ceiling());
    }

    boolean awake() {
        return !sleeping && !boundary;
    }

    /** The room left before the ceiling, mL; open layers: unbounded. */
    double room() {
        return openTop ? Double.MAX_VALUE : Math.max(0, capacity() - volume);
    }

    void refreshLevel() {
        if (boundary) return;
        // the surface rarely leaves its slab between steps: try it and the next ones before searching
        double t = volume / ML_PER_GROUND_UNIT;
        int n = area.length;
        if (volume > 0 && t < prefix[n]) {
            for (int k = Math.max(0, hint - 1); k <= Math.min(n - 1, hint + 1); k++) {
                if (prefix[k] <= t && t < prefix[k + 1]) {
                    hint = k;
                    level = (floorSlab + k + (t - prefix[k]) / area[k]) / SLABS;
                    return;
                }
            }
        }
        level = levelOf(volume);
        hint = Math.max(0, Math.min(n - 1, (int) (level * SLABS) - floorSlab));
    }

    /** The slab the surface was in last time, for {@link #refreshLevel()}. */
    private int hint;
}
