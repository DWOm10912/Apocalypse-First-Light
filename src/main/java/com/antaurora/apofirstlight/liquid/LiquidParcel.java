package com.antaurora.apofirstlight.liquid;

/**
 * A parcel of liquid in the air (docs/gameplay/water_mesh_parcels_v1_design.md B1): real millilitres with a position and
 * a velocity, flown by the engine under gravity until it comes down onto water or the ground, and only then added there.
 * While it flies its liquid is on its chunk's ledger as in the air (LiquidLedger#inFlight). Metres, seconds.
 */
public final class LiquidParcel {
    double x, y, z, vx, vy, vz;
    /** Its liquid, mL; 0 once landed. */
    long ml;
    /** The chunk (engine key) whose account holds it while it flies. */
    final long owner;
    /** The entity it came from (a pour), or -1. */
    final int source;
    final Liquid liquid;
    /** Seconds in the air. */
    double age;
    /** It met someone and now only drops to their feet. */
    boolean hit;
    /** The fall it went over, or null: where it lands is where that fall is drawn to (LiquidFall#bottom). */
    LiquidFall fall;
    /** Air drag, a = -drag |v| v (1/m); 0 for falls and pours, which the clients draw as plain parabolas. */
    double drag;

    LiquidParcel(double x, double y, double z, double vx, double vy, double vz, long ml, long owner, int source, Liquid liquid) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.ml = ml;
        this.owner = owner;
        this.source = source;
        this.liquid = liquid;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double vx() { return vx; }
    public double vy() { return vy; }
    public double vz() { return vz; }
    public long ml() { return ml; }
    public int source() { return source; }
    public double age() { return age; }
    public boolean hit() { return hit; }
}
