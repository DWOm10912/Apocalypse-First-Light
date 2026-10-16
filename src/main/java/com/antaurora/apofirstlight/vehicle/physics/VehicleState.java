package com.antaurora.apofirstlight.vehicle.physics;

/**
 * One vehicle's physics state as sent between the driver's client, the server and the clients that watch it
 * (vehicle/VehiclePackets): the model origin (world), the orientation (model frame to world, x y z w), velocity and
 * angular velocity (world), the steer angle, each wheel's travel and turn rate, the sleep flag and the drivetrain
 * (package 2: engine, gear, clutch, transfer case, ignition, coolant, fuel flow).
 */
public record VehicleState(double x, double y, double z, float qx, float qy, float qz, float qw, float vx, float vy, float vz,
                           float wx, float wy, float wz, float steer, float[] travel, float[] spinRate, boolean asleep, Drivetrain.State drive) {
    /** Every number finite and in range (a packet from a client is checked before it is used). */
    public boolean finite() {
        if (travel.length != 4 || spinRate.length != 4 || drive == null || !drive.valid()) return false;
        double sum = x + y + z + qx + qy + qz + qw + vx + vy + vz + wx + wy + wz + steer;
        for (int i = 0; i < 4; i++) sum += travel[i] + spinRate[i];
        return Double.isFinite(sum);
    }

    public double speed() {
        return Math.sqrt((double) vx * vx + (double) vy * vy + (double) vz * vz);
    }
}
