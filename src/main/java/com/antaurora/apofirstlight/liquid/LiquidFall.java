package com.antaurora.apofirstlight.liquid;

/**
 * Where liquid goes over an edge and falls (docs/gameplay/water_falling_v1_design.md 3.1): a face the flow crosses to a
 * cell whose surface stands well below the face's opening (LiquidEngine#FALL_MIN). The engine keeps one per such face
 * while the liquid runs, with its flow over the last second; the client draws drips, streams or a sheet from it.
 */
public final class LiquidFall {
    final LiquidFace face;
    LiquidCell src, dst;
    /** Millilitres over the edge this second so far, and the last full second's flow (L/s); the last tick any went. */
    long ml;
    double flow, sent = -1;
    long last;

    LiquidFall(LiquidFace face) {
        this.face = face;
    }

    /** The edge's west or north end (whole blocks). */
    public int x() {
        return face.south ? face.a.x() : face.a.x() + 1;
    }

    public int z() {
        return face.south ? face.a.z() + 1 : face.a.z();
    }

    /** The edge runs along x (a north-south fall), else along z. */
    public boolean alongX() {
        return face.south;
    }

    /** +1: the liquid falls toward +x / +z, -1 the other way. */
    public int sign() {
        return src == face.a ? 1 : -1;
    }

    /** The bottom of the opening the liquid goes over (m). */
    public double lip() {
        return face.sill();
    }

    /**
     * How thick the water is where it leaves the lip (m): over a free overfall the flow passes critical depth just behind
     * the brink, yc = (q^2 / g)^(1/3) for q the flow a metre of edge (m2/s), and leaves at about 0.715 yc (Rouse 1936).
     * Not the depth of the water behind: a thin film still leaves as a thin sheet (offline, in game 2026-10-24: the depth
     * behind gave a 4 m/s arch from a few millimetres of film).
     */
    public double depth() {
        return 0.715 * criticalDepth();
    }

    private double criticalDepth() {
        double q = flow / 1000 / Math.max(LiquidUnits.SLAB, width());
        return Math.cbrt(q * q / LiquidEngine.G);
    }

    /** Where it lands: the surface below (m). */
    public double bottom() {
        return dst.level;
    }

    /** The opening's width at the surface (m). */
    public double width() {
        return face.topWidth(Math.max(src.level, face.sill() + LiquidUnits.SLAB));
    }

    /** L/s over the last second. */
    public double flow() {
        return flow;
    }

    /** How fast it leaves the edge, m/s: the flow through the brink section, q / (0.715 yc), about 1.4 sqrt(g yc). */
    public double speed() {
        double yc = criticalDepth();
        if (yc <= 0) return 0.05;
        return Math.max(0.05, Math.min(4, flow / 1000 / Math.max(LiquidUnits.SLAB, width()) / (0.715 * yc)));
    }

    /** The chunk of the cell it falls from (engine chunk key). */
    public long chunk() {
        return src.column.chunk();
    }
}
