package com.antaurora.apofirstlight.weight;

/** Derived, non-persistent output only. No movement, stamina or weapon modifier is applied in V1. */
public record EncumbranceState(long carriedMassGrams, long comfortCapacityGrams, double encumbranceRatio,
                               double severity, Tier tier, boolean penaltiesEnabled, long dataRevision, String quality) {
    public enum Tier { LIGHT, APPROACHING_COMFORT, HEAVY, SEVERE }
    public static EncumbranceState calculate(MassResult mass, ItemMassData.Snapshot data, boolean enabled) {
        var p = data.policy();
        double ratio = mass.grams() / (double)p.comfortGrams();
        double severity = Math.max(0, Math.min(1, (ratio - p.onset()) / (p.severe() - p.onset())));
        Tier tier = ratio < p.onset() ? Tier.LIGHT : ratio < 1 ? Tier.APPROACHING_COMFORT
                : ratio < p.severe() ? Tier.HEAVY : Tier.SEVERE;
        return new EncumbranceState(mass.grams(), p.comfortGrams(), ratio, severity, tier, enabled, data.revision(), mass.quality());
    }
}
