package com.antaurora.apofirstlight.client.light;

import com.antaurora.apofirstlight.vehicle.HeadlightBeam;

/**
 * A dynamic light's pattern (shaders/include/afl_dynamic_light.glsl#afl_pattern, the shape of vehicle/HeadlightBeam's
 * Pattern): aim below the lamp's forward, half spread across, the span below and above the aim (deg); reach (m); the light
 * near the lamp and at 0.7 of the reach (block light levels); the extra reach on the right edge (share); full light this
 * close (m); the spread across measured from this far behind the lamp (m, a reflector is not a point); how much light is
 * lost toward the bottom of the span (share).
 */
public record LightCone(double aimDown, double across, double below, double above, double reach, double near, double far,
                        double rightReach, double nearDistance, double back, double belowLoss) {
    public LightCone {
        // the log falloff runs from the near distance to 0.7 of the reach
        nearDistance = Math.max(0.05, Math.min(nearDistance, reach * 0.7 * 0.9));
    }

    /**
     * A bare flame or flash: the same light all round, falling only with the distance. Give it as two spots, forward down
     * and up. The pattern has no round mode (its formula is shared with the shader pack's copy), so the cone is set so
     * nothing but the distance counts: the aim 90 deg toward the up axis puts every direction below the aim with no loss,
     * and the spread across measured from 10 km behind is next to nothing. Two half spheres of 89.5 deg instead (user
     * 2026-10-26: "奇怪的光源焦点") fell off as 1 - share^2 across and above: dark seams at their join and along the
     * diagonals, a star round the point nearest the flame.
     */
    public static LightCone round(double reach, double near, double far, double nearDistance) {
        return new LightCone(-90, 89.5, 89.5, 89.5, reach, near, far, 0, nearDistance, 1e4, 0);
    }

    /** A headlamp pattern of HeadlightBeam with its constants (NEAR, BACK, BELOW_LOSS). */
    public static LightCone of(HeadlightBeam.Pattern p) {
        return new LightCone(p.aimDown(), p.across(), p.below(), p.above(), p.reach(), p.near(), p.far(), p.rightReach(),
                HeadlightBeam.NEAR, HeadlightBeam.BACK, HeadlightBeam.BELOW_LOSS);
    }
}
