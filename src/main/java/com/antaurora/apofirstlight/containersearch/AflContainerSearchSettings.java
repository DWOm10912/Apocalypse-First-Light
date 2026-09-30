package com.antaurora.apofirstlight.containersearch;

/**
 * Per-asset search configuration. None of these values may depend on the container's contents.
 *
 * @param baseTicksPerSlot   search time for one slot before modifiers, 1..32767 ticks
 * @param durationJitter     relative, seed-derived jitter per slot, 0..0.5 (0.15 = +-15%)
 * @param noiseRadius        rummaging noise radius in blocks; 0 disables the noise hook
 * @param noiseIntervalTicks ticks between rummaging noises while searching; 0 disables the noise hook
 */
public record AflContainerSearchSettings(int baseTicksPerSlot, float durationJitter, float noiseRadius,
                                         int noiseIntervalTicks) {
    /** Framework default only; assets choose their own balance. */
    public static final AflContainerSearchSettings DEFAULT = new AflContainerSearchSettings(40, 0.15F, 0.0F, 0);

    public AflContainerSearchSettings {
        if (baseTicksPerSlot < 1 || baseTicksPerSlot > Short.MAX_VALUE) {
            throw new IllegalArgumentException("baseTicksPerSlot must be 1.." + Short.MAX_VALUE);
        }
        if (!(durationJitter >= 0.0F && durationJitter <= 0.5F)) {
            throw new IllegalArgumentException("durationJitter must be 0..0.5");
        }
        if (!(noiseRadius >= 0.0F && noiseRadius <= 256.0F)) {
            throw new IllegalArgumentException("noiseRadius must be 0..256");
        }
        if (noiseIntervalTicks < 0) {
            throw new IllegalArgumentException("noiseIntervalTicks cannot be negative");
        }
    }

    public boolean emitsNoise() {
        return noiseRadius > 0.0F && noiseIntervalTicks > 0;
    }
}
