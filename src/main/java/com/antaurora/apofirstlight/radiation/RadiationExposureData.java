package com.antaurora.apofirstlight.radiation;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/** Persistent cumulative radiation dose, reusable by any LivingEntity. */
public final class RadiationExposureData implements INBTSerializable<CompoundTag> {
    private static final String DOSE_KEY = "CumulativeDose";
    private static final String RESIDUAL_RATE_KEY = "ResidualRadiationRate";
    private double cumulativeDose;
    private double residualRadiationRate;

    public double getDose() { return cumulativeDose; }

    public boolean setDose(double dose) {
        if (!Double.isFinite(dose) || dose < 0.0) return false;
        cumulativeDose = dose;
        return true;
    }

    public boolean addDose(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) return false;
        if (amount > Double.MAX_VALUE - cumulativeDose) return false;
        cumulativeDose += amount;
        return true;
    }

    public void resetDose() { cumulativeDose = 0.0; }

    public double getResidualRadiationRate() { return residualRadiationRate; }

    public boolean setResidualRadiationRate(double rate) {
        if (!Double.isFinite(rate) || rate < 0.0) return false;
        residualRadiationRate = rate;
        return true;
    }

    public void resetResidualRadiation() { residualRadiationRate = 0.0; }

    public void approachResidualToward(double targetRate, double factor) {
        if (!Double.isFinite(targetRate) || targetRate < 0.0 || !Double.isFinite(factor) || factor <= 0.0) return;
        residualRadiationRate += (targetRate - residualRadiationRate) * Math.min(1.0, factor);
    }

    public void decayResidual(double multiplier, double zeroThreshold) {
        if (!Double.isFinite(multiplier) || !Double.isFinite(zeroThreshold)) return;
        residualRadiationRate *= Math.max(0.0, Math.min(1.0, multiplier));
        if (residualRadiationRate < Math.max(0.0, zeroThreshold)) residualRadiationRate = 0.0;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(DOSE_KEY, cumulativeDose);
        tag.putDouble(RESIDUAL_RATE_KEY, residualRadiationRate);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        setDose(tag.getDouble(DOSE_KEY));
        setResidualRadiationRate(tag.getDouble(RESIDUAL_RATE_KEY));
    }
}
