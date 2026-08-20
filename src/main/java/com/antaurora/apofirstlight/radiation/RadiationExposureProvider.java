package com.antaurora.apofirstlight.radiation;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

public final class RadiationExposureProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static final Capability<RadiationExposureData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    private final RadiationExposureData data = new RadiationExposureData();
    private final LazyOptional<RadiationExposureData> optional = LazyOptional.of(() -> data);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return capability == CAPABILITY ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() { return data.serializeNBT(); }

    @Override
    public void deserializeNBT(CompoundTag nbt) { data.deserializeNBT(nbt); }
}
