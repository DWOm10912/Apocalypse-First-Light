package com.antaurora.apofirstlight.infected.vision;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class InfectedVisionState {
    ServerPlayer candidate;
    long lastScanTime;
    float detectionProgress;
    boolean wasVisible;
    int loggedTier = -1;
    ServerPlayer confirmedPlayer;
    Vec3 lastVisiblePosition;
    long lastVisibleGameTime;
}
