package com.antaurora.apofirstlight.liquid;

import com.antaurora.apofirstlight.liquid.ground.GroundMaterialV0;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Natural rain on one chunk's ground (docs/gameplay/water_rain_v1_design.md 4.1, package 3a): a few numbers a column, no
 * liquid cells. Each settle takes one column over the ticks since the last one, in order:
 * <ol>
 *   <li>rain (mm a game hour over 1 m2, LiquidUnits);</li>
 *   <li>a canopy over the column catches it first: {@link #CANOPY_GAPS} falls through the gaps, the rest fills the leaves
 *   up to their capacity (broadleaf 1 mm, needles 2 mm), the overflow drips through; wet leaves dry at
 *   {@link #CANOPY_EVAPORATION_MM_H} once the rain stops;</li>
 *   <li>what reaches the ground: onto water standing there (the engine's: it goes in, LiquidEngine#fromSurface), onto a
 *   natural water body (written as gone to it), or onto ground, where it stands as the column's surface water;</li>
 *   <li>the surface water soaks in at Horton's capacity (fast on dry ground, falling to the soil's steady rate as it takes
 *   water in, recovering as the soil drains, LiquidSoak), and dries once the rain stops;</li>
 *   <li>up to the ground's depression storage it stays; above it is runoff, which package 3b's catchment routing carries
 *   off. Until then (3a) it stays on its column and soaks in and dries there.</li>
 * </ol>
 * Everything is whole millilitres and the account balances to one: rain = canopy evaporation + infiltration + evaporation
 * + to water + to water bodies + what the leaves and the ground hold now ({@link #verify}). A column's rain short of a
 * millilitre waits in its carry (not saved). How dark the ground looks follows apart from the water: dark while rain
 * reaches it or water stands, fading over LiquidSoak#darkHours after.
 */
public final class SurfaceWater {
    public static final int COLUMNS = 256;
    /** What a column's rain lands on this settle. */
    public static final int GROUND = 0, WATER = 1, BODY = 2, NOTHING = 3;
    /** Share of the rain falling straight through a canopy's gaps. */
    public static final double CANOPY_GAPS = 0.25;
    /** A wet canopy's evaporation, mm a game hour (interception loss studies: 0.1-0.5 mm/h). */
    public static final double CANOPY_EVAPORATION_MM_H = 0.3;
    /** Below this (mm a game hour, RegionalWeather.WET) it is not raining: leaves and ground dry. */
    public static final double RAINING_MM_H = 0.3;
    private static final int SAVE_VERSION = 1;

    /** On the leaves, mL. */
    final int[] canopy = new int[COLUMNS];
    /** On the ground, mL (storage, and above it runoff). */
    final int[] surface = new int[COLUMNS];
    /** Horton's F: water taken in above the steady rate, mm (the capacity is f0 - k F, not below fc). */
    final float[] soaked = new float[COLUMNS];
    /** How dark the ground looks, 0..1. */
    final float[] dark = new float[COLUMNS];
    /** Rain short of a millilitre. */
    final float[] carry = new float[COLUMNS];

    /** The account, mL. */
    long rain, canopyEvaporation, infiltration, evaporation, toWater, toBodies;

    /** The last settle: rain handed to water standing on the column, mL (sink WATER). */
    public long handed;
    /** The last settle: rain reached the column's ground (it stays dark however fast it soaks in). */
    public boolean reached;

    /**
     * Settles column i (x + 16 z in the chunk) over {@code ticks} of rain at {@code mmH} (mm a game hour). {@code canopyMm}
     * is the leaves' capacity above it (0: none), {@code sink} what the rain lands on, {@code kind} the ground's material
     * (sink GROUND). Rain onto water is left in {@link #handed} for the caller to give to it.
     */
    public void settle(int i, double mmH, int ticks, double canopyMm, int sink, GroundMaterialV0.Kind kind) {
        handed = 0;
        reached = false;
        if (ticks <= 0) return;
        double hours = ticks / (double) LiquidUnits.TICKS_PER_GAME_HOUR;
        boolean raining = mmH >= RAINING_MM_H;
        long fall = 0;
        if (mmH > 0 && sink != NOTHING) {
            double ml = carry[i] + mmH * ticks * LiquidUnits.ML_PER_TICK_PER_MM_H;
            fall = (long) ml;
            carry[i] = (float) (ml - fall);
            rain += fall;
        }
        // the canopy
        long through = fall;
        if (canopyMm > 0) {
            long cap = Math.round(canopyMm * 1000);
            long toLeaves = fall - (long) (fall * CANOPY_GAPS);
            long take = Math.max(0, Math.min(toLeaves, cap - canopy[i]));
            canopy[i] += (int) take;
            through = fall - take;
        }
        if (canopy[i] > 0) {
            // leaves lost overhead (a tree felled) shed what they held; wet ones dry once the rain stops
            if (canopyMm <= 0) {
                through += canopy[i];
                canopy[i] = 0;
            } else if (!raining) {
                long e = Math.min(canopy[i], Math.round(CANOPY_EVAPORATION_MM_H * hours * 1000));
                canopy[i] -= (int) e;
                canopyEvaporation += e;
            }
        }
        // the ground
        if (sink == WATER || sink == BODY) {
            long all = through + surface[i];
            surface[i] = 0;
            dark[i] = 0;
            if (sink == WATER) {
                handed = all;
                toWater += all;
            } else toBodies += all;
            return;
        }
        if (sink == NOTHING) {
            // no ground known: what was there stays as it was
            surface[i] += (int) through;
            return;
        }
        long w = surface[i] + through;
        double fc = LiquidSoak.infiltrationMmH(kind), f0 = LiquidSoak.initialInfiltrationMmH(kind);
        double capacityMmH = Math.max(fc, f0 - LiquidSoak.HORTON_K * soaked[i]);
        long in = Math.min(w, Math.round(capacityMmH * hours * 1000));
        w -= in;
        infiltration += in;
        // F grows by what went in above the steady rate and falls as the soil drains at it
        soaked[i] = (float) Math.max(0, soaked[i] + in / 1000.0 - fc * hours);
        if (!raining && w > 0) {
            long e = Math.min(w, Math.round(LiquidSoak.EVAPORATION_MM_H * hours * 1000));
            w -= e;
            evaporation += e;
        }
        surface[i] = (int) w;
        // rain reaching it darkens the ground even when it all soaks in at once
        reached = through > 0;
        if (w > 0 || reached) dark[i] = 1;
        else if (dark[i] > 0) {
            double h = LiquidSoak.darkHours(kind);
            dark[i] = h <= 0 ? 0 : (float) Math.max(0, dark[i] - hours / h);
        }
    }

    /** The water standing on column i took none of the last settle's {@link #handed} after all: it stays on the ground. */
    public void keep(int i) {
        surface[i] += (int) handed;
        toWater -= handed;
        handed = 0;
    }

    public int canopy(int i) { return canopy[i]; }
    public int surface(int i) { return surface[i]; }
    public float soaked(int i) { return soaked[i]; }
    public float dark(int i) { return dark[i]; }
    public long rain() { return rain; }
    public long canopyEvaporation() { return canopyEvaporation; }
    public long infiltration() { return infiltration; }
    public long evaporation() { return evaporation; }
    public long toWater() { return toWater; }
    public long toBodies() { return toBodies; }

    /** Nothing on any column and the soil recovered: a settle without rain would change nothing. */
    public boolean idle() {
        for (int i = 0; i < COLUMNS; i++) if (canopy[i] != 0 || surface[i] != 0 || dark[i] != 0 || soaked[i] != 0) return false;
        return true;
    }

    /** What the leaves and the ground hold now, mL. */
    public long held() {
        long n = 0;
        for (int i = 0; i < COLUMNS; i++) n += canopy[i] + surface[i];
        return n;
    }

    /** Runoff on column i: its surface water above the ground's depression storage (mL; package 3b carries it off). */
    public static long runoff(int surfaceMl, GroundMaterialV0.Kind kind) {
        return Math.max(0, surfaceMl - Math.round(LiquidSoak.storageMm(kind) * 1000));
    }

    /** The account's gap: 0 when every millilitre is where it says (rain = out + held). */
    public long verify() {
        return rain - canopyEvaporation - infiltration - evaporation - toWater - toBodies - held();
    }

    /** Anything worth a save: water held, ground still dark, or an account. */
    public boolean worthSaving() {
        if (rain != 0) return true;
        for (int i = 0; i < COLUMNS; i++) if (canopy[i] != 0 || surface[i] != 0 || soaked[i] != 0 || dark[i] != 0) return true;
        return false;
    }

    public byte[] save() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(SAVE_VERSION);
            for (long v : new long[]{rain, canopyEvaporation, infiltration, evaporation, toWater, toBodies}) out.writeLong(v);
            int n = 0;
            for (int i = 0; i < COLUMNS; i++) if (canopy[i] != 0 || surface[i] != 0 || soaked[i] != 0 || dark[i] != 0) n++;
            out.writeShort(n);
            for (int i = 0; i < COLUMNS; i++) {
                if (canopy[i] == 0 && surface[i] == 0 && soaked[i] == 0 && dark[i] == 0) continue;
                out.writeByte(i);
                out.writeInt(canopy[i]);
                out.writeInt(surface[i]);
                out.writeFloat(soaked[i]);
                out.writeFloat(dark[i]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static SurfaceWater load(byte[] data) {
        SurfaceWater s = new SurfaceWater();
        if (data == null) return s;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readInt();
            if (version != SAVE_VERSION) throw new IOException("surface water save version " + version);
            s.rain = in.readLong();
            s.canopyEvaporation = in.readLong();
            s.infiltration = in.readLong();
            s.evaporation = in.readLong();
            s.toWater = in.readLong();
            s.toBodies = in.readLong();
            int n = in.readShort();
            for (int k = 0; k < n; k++) {
                int i = in.readByte() & 255;
                s.canopy[i] = in.readInt();
                s.surface[i] = in.readInt();
                s.soaked[i] = in.readFloat();
                s.dark[i] = in.readFloat();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return s;
    }
}
