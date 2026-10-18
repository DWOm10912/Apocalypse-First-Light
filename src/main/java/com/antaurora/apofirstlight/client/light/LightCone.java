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

    /** A headlamp pattern of HeadlightBeam with its constants (NEAR, BACK, BELOW_LOSS). */
    public static LightCone of(HeadlightBeam.Pattern p) {
        return new LightCone(p.aimDown(), p.across(), p.below(), p.above(), p.reach(), p.near(), p.far(), p.rightReach(),
                HeadlightBeam.NEAR, HeadlightBeam.BACK, HeadlightBeam.BELOW_LOSS);
    }
}
