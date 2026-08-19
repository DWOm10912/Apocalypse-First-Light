package com.antaurora.apofirstlight.world.bunker;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class BunkerSavedData extends SavedData {
    public static final String ID = "apocalypse_firstlight_bunker";
    private boolean generated;
    private BlockPos origin = BlockPos.ZERO;
    private String rotation = "NONE";
    private int referenceSurfaceY;
    private int placementVersion;

    public static BunkerSavedData load(CompoundTag tag) {
        BunkerSavedData data = new BunkerSavedData();
        data.generated = tag.getBoolean("generated");
        data.origin = new BlockPos(tag.getInt("originX"), tag.getInt("originY"), tag.getInt("originZ"));
        data.rotation = tag.contains("rotation", 8) ? tag.getString("rotation") : "NONE";
        data.referenceSurfaceY = tag.getInt("referenceSurfaceY");
        data.placementVersion = tag.getInt("placementVersion");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("generated", generated);
        tag.putInt("originX", origin.getX());
        tag.putInt("originY", origin.getY());
        tag.putInt("originZ", origin.getZ());
        tag.putString("rotation", rotation);
        tag.putInt("referenceSurfaceY", referenceSurfaceY);
        tag.putInt("placementVersion", placementVersion);
        return tag;
    }

    public boolean isGenerated() { return generated; }
    public BlockPos getOrigin() { return origin; }
    public String getRotation() { return rotation; }
    public int getReferenceSurfaceY() { return referenceSurfaceY; }

    public void markGenerated(BlockPos origin, String rotation, int referenceSurfaceY, int placementVersion) {
        this.generated = true;
        this.origin = origin;
        this.rotation = rotation;
        this.referenceSurfaceY = referenceSurfaceY;
        this.placementVersion = placementVersion;
        setDirty();
    }
}
