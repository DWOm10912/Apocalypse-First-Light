package com.antaurora.apofirstlight.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class RadiationWorldData extends SavedData {
    private static final String DATA_ID = "apocalypse_firstlight_radiation";
    private long safeChunkX;
    private long safeChunkZ;
    private int safeAnchorX;
    private int safeAnchorZ;
    private boolean safeAnchorInitialized;
    private String anchorSource = "WORLD_SPAWN";
    private int anchorVersion;

    public static RadiationWorldData load(CompoundTag tag) {
        RadiationWorldData data = new RadiationWorldData();
        data.safeChunkX = tag.getLong("SafeChunkX");
        data.safeChunkZ = tag.getLong("SafeChunkZ");
        data.safeAnchorX = tag.contains("SafeAnchorX", 3) ? tag.getInt("SafeAnchorX") : (int) (data.safeChunkX * 16L + 8L);
        data.safeAnchorZ = tag.contains("SafeAnchorZ", 3) ? tag.getInt("SafeAnchorZ") : (int) (data.safeChunkZ * 16L + 8L);
        data.safeAnchorInitialized = tag.getBoolean("SafeAnchorInitialized");
        data.anchorSource = tag.contains("AnchorSource", 8) ? tag.getString("AnchorSource") : "WORLD_SPAWN";
        data.anchorVersion = tag.getInt("AnchorVersion");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("SafeChunkX", safeChunkX);
        tag.putLong("SafeChunkZ", safeChunkZ);
        tag.putInt("SafeAnchorX", safeAnchorX);
        tag.putInt("SafeAnchorZ", safeAnchorZ);
        tag.putBoolean("SafeAnchorInitialized", safeAnchorInitialized);
        tag.putString("AnchorSource", anchorSource);
        tag.putInt("AnchorVersion", anchorVersion);
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
            data.safeAnchorX = spawn.getX();
            data.safeAnchorZ = spawn.getZ();
            data.safeChunkX = spawn.getX() >> 4;
            data.safeChunkZ = spawn.getZ() >> 4;
            data.safeAnchorInitialized = true;
            data.anchorSource = "WORLD_SPAWN";
            data.anchorVersion = 0;
            data.setDirty();
        }
        return data;
    }

    public void setBunkerAnchor(BlockPos anchor) {
        long chunkX = anchor.getX() >> 4;
        long chunkZ = anchor.getZ() >> 4;
        if (safeAnchorX != anchor.getX() || safeAnchorZ != anchor.getZ()
                || !safeAnchorInitialized || !"BUNKER".equals(anchorSource) || anchorVersion != 1) {
            safeAnchorX = anchor.getX();
            safeAnchorZ = anchor.getZ();
            safeChunkX = chunkX;
            safeChunkZ = chunkZ;
            safeAnchorInitialized = true;
            anchorSource = "BUNKER";
            anchorVersion = 1;
            setDirty();
        }
    }

    public int safeAnchorX() { return safeAnchorX; }
    public int safeAnchorZ() { return safeAnchorZ; }
    public long safeChunkX() { return safeChunkX; }
    public long safeChunkZ() { return safeChunkZ; }
    public boolean safeAnchorInitialized() { return safeAnchorInitialized; }
    public String anchorSource() { return anchorSource; }
    public int anchorVersion() { return anchorVersion; }
}
