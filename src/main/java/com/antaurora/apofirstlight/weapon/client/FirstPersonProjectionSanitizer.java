package com.antaurora.apofirstlight.weapon.client;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Coordinate resolving only: never install this matrix in render state. */
public final class FirstPersonProjectionSanitizer {
    private FirstPersonProjectionSanitizer() {}

    public static Matrix4f sanitize(Matrix4fc hand, Matrix4fc world) {
        // JOML m[column][row]: clip Z coefficients (column-major indices 10, 14).
        // Preserve hand X/Y/FOV and clip W, including m23 (perspective -1).
        return new Matrix4f(hand).m22(world.m22()).m32(world.m32());
    }
}
