package com.antaurora.apofirstlight.authoring;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.UUID;

/** Session reservation only. No automatic terrain edits, buildings, or chunk tickets. */
public final class BuildingAuthoringSession {
    public enum State { EMPTY, DRAFT, VALIDATED, EXPORTED }
    public final UUID owner;
    public final ResourceKey<Level> dimension;
    public final BlockPos origin;
    public BuildingMetadata metadata;
    public State state=State.EMPTY;
    public long boundsUntil;
    public String approvedDigest;
    public String clearToken;
    public long clearUntil;
    public BuildingAuthoringSession(UUID owner, ResourceKey<Level> dimension, BlockPos origin, BuildingMetadata metadata) {
        this.owner=owner;this.dimension=dimension;this.origin=origin.immutable();this.metadata=metadata;
    }
    public BlockPos max() {return origin.offset(metadata.width()-1,metadata.height()-1,metadata.depth()-1);}
    public AABB bounds() {return new AABB(origin, max().offset(1,1,1));}
    public void changed() {state=State.DRAFT;approvedDigest=null;clearToken=null;}
}
