package com.antaurora.apofirstlight.client.camera;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Camera director V1 (docs/dev/camera_director_v1_plan.md 2.2): where a shot puts the camera this frame. Angles in degrees
 * as Minecraft's (yaw 0 = south, pitch + = down); {@code fov} NaN keeps the field of view the game computed (the director
 * eases the field of view on its own, in ComputeFov, since the game's is not known when the camera is placed).
 */
public record CameraPose(Vec3 position, float yaw, float pitch, float roll, double fov) {
    public CameraPose(Vec3 position, float yaw, float pitch) {
        this(position, yaw, pitch, 0F, Double.NaN);
    }

    /**
     * From {@code from} toward {@code to} by {@code w} (0..1): position and roll linear, yaw the short way round, pitch
     * linear, as the views the director takes over always eased (Mth.rotLerp + Mth.lerp, plan 4 risk 2). The field of view
     * is {@code to}'s.
     */
    public static CameraPose blend(CameraPose from, CameraPose to, float w) {
        if (w <= 0F) return new CameraPose(from.position, from.yaw, from.pitch, from.roll, to.fov);
        if (w >= 1F) return to;
        return new CameraPose(from.position.lerp(to.position, w), Mth.rotLerp(w, from.yaw, to.yaw), Mth.lerp(w, from.pitch, to.pitch),
                Mth.lerp(w, from.roll, to.roll), to.fov);
    }
}
