package com.antaurora.apofirstlight.noise.movement;

final class MovementNoiseState {
    boolean initialized;
    double previousX;
    double previousZ;
    boolean wasOnGround;
    double accumulatedHorizontalDistance;
    float maxAirborneFallDistance;
}
