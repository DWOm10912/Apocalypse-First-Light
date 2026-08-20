package com.antaurora.apofirstlight.radiation;

/** Surface-biome tendency applied only to the natural world/base radiation field. */
public enum BiomeRadiationProfile {
    UNKNOWN(0.0D, 1.0D),
    SAFE(0.0D, 0.075D),
    IRRADIATED(0.10D, 0.58D);

    private final double minimum;
    private final double maximum;

    BiomeRadiationProfile(double minimum, double maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public double constrain(double rawField) {
        if (this == UNKNOWN) return rawField;
        return minimum + Math.max(0.0D, Math.min(1.0D, rawField)) * (maximum - minimum);
    }

    public double minimum() { return minimum; }
    public double maximum() { return maximum; }
}
