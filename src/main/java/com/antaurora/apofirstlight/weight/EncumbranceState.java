package com.antaurora.apofirstlight.weight;

/**
 * Derived, non-persistent. carriedMassGrams is the physical mass; loadGrams weighs it by where and what is carried
 * (worn armor counts less, bulky furniture more; PlayerMassSources). The ratio, tier and penalties follow the load.
 * Multipliers are 1 and sprint is free while penalties are disabled (Creative / Spectator).
 */
public record EncumbranceState(long carriedMassGrams, long loadGrams, long comfortCapacityGrams, double encumbranceRatio,
                               double severity, Tier tier, boolean penaltiesEnabled, double speedMultiplier,
                               double jumpMultiplier, boolean sprintBlocked, long dataRevision, String quality) {
    /** Up to the onset (the comfort), then the onset → severe range in thirds: 30 kg comfort = 30 / 40 / 50 / 60 kg. */
    public enum Tier { LIGHT, BURDENED, HEAVY, HAULING, EXTREME }

    /** wasSprintBlocked: the previous state's, for the resume threshold below the block threshold. */
    public static EncumbranceState calculate(MassResult mass, long loadGrams, ItemMassData.Snapshot data, boolean enabled,
                                             boolean wasSprintBlocked) {
        var p = data.policy();
        var penalties = data.penalties();
        double ratio = loadGrams / (double)p.comfortGrams();
        double severity = Math.max(0, Math.min(1, (ratio - p.onset()) / (p.severe() - p.onset())));
        Tier tier = ratio <= p.onset() ? Tier.LIGHT : severity < 1 / 3.0 ? Tier.BURDENED
                : severity < 2 / 3.0 ? Tier.HEAVY : severity < 1 ? Tier.HAULING : Tier.EXTREME;
        boolean sprintBlocked = enabled
                && ratio >= (wasSprintBlocked ? penalties.sprintResumeRatio() : penalties.sprintBlockRatio());
        return new EncumbranceState(mass.grams(), loadGrams, p.comfortGrams(), ratio, severity, tier, enabled,
                enabled ? penalties.speed(ratio) : 1.0, enabled ? penalties.jump(ratio) : 1.0, sprintBlocked,
                data.revision(), mass.quality());
    }
}
