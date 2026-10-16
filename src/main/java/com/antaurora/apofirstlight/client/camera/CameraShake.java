package com.antaurora.apofirstlight.client.camera;

import net.minecraft.client.Camera;

/**
 * Camera director V1 (docs/dev/camera_director_v1_plan.md 2.5): the shake layer's sum for one frame, degrees as the view's
 * (pitch + = down). Every source adds to it; the director puts the sum on the view once (ViewportEvent.ComputeCameraAngles),
 * after the shots have placed the camera. A source keeps its own conditions (first person, its item, its machine).
 */
public final class CameraShake {
    public double pitch, yaw, roll;

    /** One shake source: adds what it shakes this frame to {@code out} (or nothing). Called every frame, in order. */
    @FunctionalInterface
    public interface Source {
        void shake(Camera camera, float partialTick, CameraShake out);
    }

    public void add(double pitch, double yaw, double roll) {
        this.pitch += pitch;
        this.yaw += yaw;
        this.roll += roll;
    }

    void reset() {
        pitch = yaw = roll = 0;
    }
}
