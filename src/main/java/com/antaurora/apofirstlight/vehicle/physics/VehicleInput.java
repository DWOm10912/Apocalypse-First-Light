package com.antaurora.apofirstlight.vehicle.physics;

/**
 * The driver's controls for one tick (design doc section 4, package 2): throttle and brake (0..1), steer (-1 left .. 1
 * right), the handbrake; a gear change asked this tick (+1 up, -1 down, 0 none), the 2H / 4H lever pulled this tick, the
 * ignition key held; whether the tank has fuel and whether anyone sits at the wheel at all.
 */
public record VehicleInput(float throttle, float brake, float steer, boolean handbrake, int shift, boolean transfer, boolean ignition,
                           boolean fuel, boolean driver) {
    /** Nobody at the wheel: the handbrake is on, the engine stops (the key goes with the driver). */
    public static final VehicleInput PARKED = new VehicleInput(0, 0, 0, true, 0, false, false, false, false);

    /** Pedals and keys only (no gear change, lever or key; fuel in the tank): the offline check's driving. */
    public VehicleInput(float throttle, float brake, float steer, boolean handbrake) {
        this(throttle, brake, steer, handbrake, 0, false, false, true, true);
    }

    /** Nothing pressed (the truck may hold still and, parked, sleep). */
    public boolean idle() {
        return throttle == 0 && brake == 0 && steer == 0 && shift == 0 && !transfer && !ignition;
    }
}
