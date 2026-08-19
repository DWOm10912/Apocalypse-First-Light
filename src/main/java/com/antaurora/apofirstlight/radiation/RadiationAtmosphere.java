package com.antaurora.apofirstlight.radiation;

public final class RadiationAtmosphere {
    private RadiationAtmosphere() {
    }

    public static float getIntensity(double finalRadiation) {
        if (finalRadiation <= 0.0) return 0.0F;
        double normalized = Math.min(1.0, finalRadiation / 10.0);
        return (float) (normalized * normalized * (3.0 - 2.0 * normalized));
    }
}
