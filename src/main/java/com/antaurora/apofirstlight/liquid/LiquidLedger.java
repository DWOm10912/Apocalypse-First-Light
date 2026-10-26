package com.antaurora.apofirstlight.liquid;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * One chunk's liquid account, mL (docs/gameplay/water_v1_design.md 5.10). The chunk's cells plus its limbo always equal
 * what came in less what went out, to the millilitre: {@link #net()}. Limbo is liquid a geometry change left without room
 * anywhere near (a sealed space filled solid); kept, never dropped.
 */
public final class LiquidLedger {
    long rain, added, removed, boundaryIn, boundaryOut, crossIn, crossOut, infiltration, evaporation, limbo;
    /** Natural rain (and the surface water it left) handed over by the chunk's SurfaceWater: package 3a, save version 2. */
    long fromSurface;
    /** Falling into the chunk, not landed yet (LiquidEngine#fall; not saved: a save lands it first). */
    long inFlight;
    /** Scratch while a basin is spread to one level: this chunk's net change, mL (not saved). */
    long spread;
    /** Some liquid of the chunk moved since LiquidEngine#takeChanged (not saved). */
    boolean changed;

    public long net() {
        return rain + fromSurface + added - removed + boundaryIn - boundaryOut + crossIn - crossOut - infiltration - evaporation;
    }

    public long rain() { return rain; }
    public long fromSurface() { return fromSurface; }
    public long added() { return added; }
    public long removed() { return removed; }
    public long boundaryIn() { return boundaryIn; }
    public long boundaryOut() { return boundaryOut; }
    public long crossIn() { return crossIn; }
    public long crossOut() { return crossOut; }
    public long limbo() { return limbo; }
    public long infiltration() { return infiltration; }
    public long evaporation() { return evaporation; }
    public long inFlight() { return inFlight; }

    void write(DataOutput out) throws IOException {
        for (long v : new long[]{rain, added, removed, boundaryIn, boundaryOut, crossIn, crossOut, infiltration, evaporation, limbo, fromSurface})
            out.writeLong(v);
    }

    /** A save's account; version 1 had no fromSurface. */
    static LiquidLedger read(DataInput in, int version) throws IOException {
        LiquidLedger l = new LiquidLedger();
        l.rain = in.readLong();
        l.added = in.readLong();
        l.removed = in.readLong();
        l.boundaryIn = in.readLong();
        l.boundaryOut = in.readLong();
        l.crossIn = in.readLong();
        l.crossOut = in.readLong();
        l.infiltration = in.readLong();
        l.evaporation = in.readLong();
        l.limbo = in.readLong();
        if (version >= 2) l.fromSurface = in.readLong();
        return l;
    }
}
