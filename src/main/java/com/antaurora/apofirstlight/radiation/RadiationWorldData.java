package com.antaurora.apofirstlight.radiation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class RadiationWorldData extends SavedData {
    private static final String DATA_ID = "apocalypse_firstlight_radiation";
    private long safeChunkX;
    private long safeChunkZ;
    private boolean safeAnchorInitialized;

    public static RadiationWorldData load(CompoundTag tag) {
        RadiationWorldData data = new RadiationWorldData();
        data.safeChunkX = tag.getLong("SafeChunkX");
        data.safeChunkZ = tag.getLong("SafeChunkZ");
        data.safeAnchorInitialized = tag.getBoolean("SafeAnchorInitialized");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("SafeChunkX", safeChunkX);
        tag.putLong("SafeChunkZ", safeChunkZ);
        tag.putBoolean("SafeAnchorInitialized", safeAnchorInitialized);
        return tag;
    }

    public static RadiationWorldData get(net.minecraft.server.level.ServerLevel level) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            throw new IllegalArgumentException("RadiationWorldData is Overworld-only");
        }
        RadiationWorldData data = level.getDataStorage().computeIfAbsent(
                RadiationWorldData::load, RadiationWorldData::new, DATA_ID);
        if (!data.safeAnchorInitialized) {
            net.minecraft.core.BlockPos spawn = level.getSharedSpawnPos();
            data.safeChunkX = spawn.getX() >> 4;
            data.safeChunkZ = spawn.getZ() >> 4;
            data.safeAnchorInitialized = true;
            data.setDirty();
        }
        return data;
    }

    public void setSpawnSafeChunk(long chunkX, long chunkZ) {
        if (safeChunkX != chunkX || safeChunkZ != chunkZ || !safeAnchorInitialized) {
            safeChunkX = chunkX;
            safeChunkZ = chunkZ;
            safeAnchorInitialized = true;
            setDirty();
        }
    }

    public long safeChunkX() { return safeChunkX; }
    public long safeChunkZ() { return safeChunkZ; }
    public boolean safeAnchorInitialized() { return safeAnchorInitialized; }
}
