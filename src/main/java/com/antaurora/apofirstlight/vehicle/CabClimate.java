package com.antaurora.apofirstlight.vehicle;

import net.minecraft.world.entity.Entity;

/**
 * The T15 cab's air (2026-10-18, docs/vehicles/first_pickup_v0_design.md section 8.19): the cab is a shelter of its own
 * (no rain on the riders of the two cab seats, its own air temperature), and the heater panel has a switch the driver
 * steps OFF - HEAT - COOL ({@link PickupTruckEntity#climateMode}; VehicleDriving.CLIMATE). Server side, once a second per
 * truck ({@link #target}, {@link #step}); PlayerTemperature takes the cab air in place of the world's for a cab rider.
 * <pre>
 * passive = lerp(HOLD, outdoor, sheltered) + SOLAR x sun          doors shut (sun: the sun's height, clear sky, open above)
 *         = outdoor                                               a door open
 * HEAT:    up to HEAT_SET, at most HEAT_RISE x warm over passive  warm: the coolant from WARM_FROM to WARM_FULL C; key on
 * COOL:    down to COOL_SET, at most COOL_DROP under passive      the compressor on the running engine (AC_TORQUE)
 * cab     += (target - cab)(1 - e^(-dt / tau))                    tau TAU_SHUT, TAU_FAN with the blower, TAU_OPEN a door open
 * </pre>
 * Heat is the engine's own (the coolant through the heater core): no fuel of its own, nothing until the engine is warm.
 * The air conditioning loads the engine ({@link #AC_TORQUE} on the crankshaft through the compressor's belt), so it
 * burns fuel through the engine model like any load. With a door open the heater and the air conditioning reach a
 * quarter as far.
 */
public final class CabClimate {
    public static final int OFF = 0, HEAT = 1, COOL = 2, MODES = 3;
    /** How much of the day's mean a shut cab keeps (0 the night's air, 1 a house's), and a shut cab in full sun (C). */
    static final double HOLD = 0.3, SOLAR = 10;
    /** The heater: where it stops (C), its most over the passive cab, the coolant it starts and is full at (C). */
    static final double HEAT_SET = 22, HEAT_RISE = 35, WARM_FROM = 35, WARM_FULL = 75;
    /** The air conditioning: where it stops (C), its most under the passive cab. */
    static final double COOL_SET = 21, COOL_DROP = 22;
    /** A door open: the share of the heater's / air conditioning's reach left. */
    static final double OPEN_REACH = 0.25;
    /** Time constants (s): shut, shut with the blower on, a door open. */
    static final double TAU_SHUT = 300, TAU_FAN = 100, TAU_OPEN = 20;
    /** The compressor's load on the engine while it runs (Nm; about 1 kW at idle, 3 kW at 2500 rpm). */
    public static final double AC_TORQUE = 12;

    private CabClimate() {
    }

    /**
     * The air the cab moves to (C): outdoor and sheltered as PlayerTemperature has them there, sun 0..1 (the sun's
     * height on a clear sky, open above), the doors shut, the mode, the key on (the blower's power), the engine running
     * (the compressor's), the coolant (C).
     */
    static double target(double outdoor, double sheltered, double sun, boolean shut, int mode, boolean power, boolean running, double coolant) {
        double passive = shut ? outdoor + (sheltered - outdoor) * HOLD + SOLAR * sun : outdoor;
        double reach = shut ? 1 : OPEN_REACH;
        if (mode == HEAT && power) {
            double warm = clamp((coolant - WARM_FROM) / (WARM_FULL - WARM_FROM));
            if (passive < HEAT_SET) return Math.min(HEAT_SET, passive + HEAT_RISE * warm * reach);
        } else if (mode == COOL && running) {
            if (passive > COOL_SET) return Math.max(COOL_SET, passive - COOL_DROP * reach);
        }
        return passive;
    }

    /** The cab's air after dt seconds towards target; NaN (not known yet) starts at it. */
    static double step(double cab, double target, double dt, boolean shut, boolean blower) {
        if (Double.isNaN(cab)) return target;
        double tau = !shut ? TAU_OPEN : blower ? TAU_FAN : TAU_SHUT;
        return cab + (target - cab) * (1 - Math.exp(-dt / tau));
    }

    /** The truck whose cab this entity sits in (the driver's or the passenger's seat), else null. */
    public static PickupTruckEntity cabOf(Entity rider) {
        if (!(rider.getVehicle() instanceof PickupTruckEntity truck)) return null;
        int seat = truck.seatOf(rider);
        return seat >= 0 && PickupTruckLayout.SEATS[seat].cab() ? truck : null;
    }

    private static double clamp(double x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }
}
