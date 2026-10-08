package com.antaurora.apofirstlight.dev.highwaymesh;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** One explicit sandbox per dimension; ledger contains only the exact test block states we own. */
public final class DevHighwayMeshData extends SavedData {
    public static final String ID="afl_dev_highway_mesh_m1a";
    public boolean active;
    public BlockPos origin=BlockPos.ZERO;
    public String assetVersion="",phase="EMPTY";
    public UUID instance=new UUID(0,0);
    public final Map<BlockPos,Integer> owned=new LinkedHashMap<>();
    public static DevHighwayMeshData load(CompoundTag tag){
        var data=new DevHighwayMeshData();if(tag.getInt("schema")!=1)throw new IllegalArgumentException("Unsupported highway demo saved-data schema; refusing to modify it");
        data.active=tag.getBoolean("active");data.origin=BlockPos.of(tag.getLong("origin"));data.assetVersion=tag.getString("asset_version");data.phase=tag.getString("phase");
        if(tag.hasUUID("instance"))data.instance=tag.getUUID("instance");
        var list=tag.getList("owned",Tag.TAG_COMPOUND);if(list.size()>16000)throw new IllegalArgumentException("Demo ledger exceeds budget");
        for(var entry:list){var row=(CompoundTag)entry;int layers=row.getInt("layers");if(layers<1||layers>32||!row.contains("pos",Tag.TAG_LONG))throw new IllegalArgumentException("Invalid collision layer/position");if(data.owned.put(BlockPos.of(row.getLong("pos")),layers)!=null)throw new IllegalArgumentException("Duplicate collision ownership");}
        if(data.active&&(!tag.hasUUID("instance")||!data.assetVersion.matches("[a-f0-9]{64}")||(data.origin.getX()&15)!=0||(data.origin.getZ()&15)!=0||!Set.of("PLACING","COMPLETE","PARTIAL_USE_REMOVE","PARTIAL_REMOVE").contains(data.phase)))throw new IllegalArgumentException("Invalid active scene descriptor");
        if(!data.active&&!data.owned.isEmpty()||data.active&&data.phase.equals("COMPLETE")&&data.owned.isEmpty())throw new IllegalArgumentException("Inconsistent scene ownership");
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        tag.putInt("schema",1);tag.putBoolean("active",active);tag.putLong("origin",origin.asLong());tag.putString("asset_version",assetVersion);tag.putString("phase",phase);tag.putUUID("instance",instance);
        ListTag list=new ListTag();owned.forEach((pos,layers)->{CompoundTag row=new CompoundTag();row.putLong("pos",pos.asLong());row.putInt("layers",layers);list.add(row);});tag.put("owned",list);return tag;
    }
    public void begin(BlockPos origin,String version){this.origin=origin.immutable();this.assetVersion=version;this.instance=UUID.randomUUID();active=true;phase="PLACING";owned.clear();setDirty();}
    public void clear(){active=false;owned.clear();phase="EMPTY";setDirty();}
}
