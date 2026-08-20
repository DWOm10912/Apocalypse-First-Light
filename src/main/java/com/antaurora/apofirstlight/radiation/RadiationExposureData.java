package com.antaurora.apofirstlight.radiation;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/** Persistent cumulative radiation dose, reusable by any LivingEntity. */
public final class RadiationExposureData implements INBTSerializable<CompoundTag> {
    private static final String DOSE_KEY = "CumulativeDose";
    private double cumulativeDose;

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

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(DOSE_KEY, cumulativeDose);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        setDose(tag.getDouble(DOSE_KEY));
    }
}
