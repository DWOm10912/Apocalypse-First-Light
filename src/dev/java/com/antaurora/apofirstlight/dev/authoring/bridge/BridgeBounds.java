package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Inclusive bounds; no chunk loading or coordinate normalization of invalid requests. */
record BridgeBounds(BlockPos min,BlockPos max) {
    static final int LIMIT=250_000;
    BridgeBounds {if(min.getX()>max.getX()||min.getY()>max.getY()||min.getZ()>max.getZ()||Math.abs((long)min.getX())>30_000_000||Math.abs((long)max.getX())>30_000_000||Math.abs((long)min.getZ())>30_000_000||Math.abs((long)max.getZ())>30_000_000||min.getY()<-2048||max.getY()>2048)throw new IllegalArgumentException("INVALID_BOUNDS");}
    int width(){return max.getX()-min.getX()+1;}
    int height(){return max.getY()-min.getY()+1;}
    int depth(){return max.getZ()-min.getZ()+1;}
    long volume(){return (long)width()*height()*depth();}
    boolean contains(BlockPos p){return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
    void inside(BridgeBounds outer){if(!outer.contains(min)||!outer.contains(max))throw new IllegalArgumentException("OUTSIDE_AUTHORING_SCOPE");}
    void check(ServerLevel level){
        if(volume()<1||volume()>LIMIT)throw new IllegalArgumentException("MAX_EDIT_LIMIT: split into <=250000 block regions");
        if(min.getY()<level.getMinBuildHeight()||max.getY()>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(min)||!level.getWorldBorder().isWithinBounds(max))throw new IllegalArgumentException("WORLD_BOUNDS");
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)if(!level.hasChunk(x,z))throw new IllegalArgumentException("CHUNK_NOT_LOADED");
    }
    AABB aabb(){return new AABB(min,max.offset(1,1,1));}
    JsonObject json(){return BridgeJson.object("min",BridgeJson.xyz(min),"max",BridgeJson.xyz(max),"width",width(),"height",height(),"depth",depth(),"volume",volume());}
}
