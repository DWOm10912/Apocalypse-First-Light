package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.authoring.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** One authored draft upgrade, not a runtime building generator. No export/validation/pool hooks. */
public final class OfficeMidrise01DetailBuilder {
    private final Map<BlockPos,BlockState> plan=new LinkedHashMap<>(OfficeMidrise01DraftBuilder.massing());
    private OfficeMidrise01DetailBuilder() {}
    static Map<BlockPos,BlockState> detailed() {
        var b=new OfficeMidrise01DetailBuilder();
        b.exterior();b.groundFloor();b.officeFloors();b.elevator();b.stairCore();b.roofEquipment();b.lighting();
        return b.plan;
    }
    public static int upgrade(ServerLevel level,BuildingAuthoringSession s) {
        if(!BuildingAuthoringConfig.ENABLED.get())throw new IllegalArgumentException("Authoring disabled");
        var m=s.metadata;
        if(!m.id().equals(OfficeMidrise01DraftBuilder.ID)||m.width()!=28||m.depth()!=34||m.height()!=40||m.surfaceOffset()!=1)
            throw new IllegalArgumentException("Resume office_midrise_01 at its original MIN corner, size 28 34 40 1");
        BuildingAuthoringService.accessible(level,s);
        if(!level.getEntities((net.minecraft.world.entity.Entity)null,s.bounds(),e->true).isEmpty())
            throw new IllegalArgumentException("Move players/entities outside the plot before upgrade");
        var before=OfficeMidrise01DraftBuilder.massing();var after=detailed();var changes=new LinkedHashMap<BlockPos,BlockState>();
        // Check unchanged shell landmarks too: reject an empty/wrongly selected plot.
        for(var p:List.of(new BlockPos(0,0,0),new BlockPos(27,0,33),new BlockPos(13,26,16),new BlockPos(27,31,33)))
            if(!level.getBlockState(s.origin.offset(p)).equals(before.get(p)))throw new IllegalArgumentException("Massing landmark mismatch at "+s.origin.offset(p).toShortString());
        for(var entry:after.entrySet()) {
            var local=entry.getKey();var old=before.getOrDefault(local,Blocks.AIR.defaultBlockState());var next=entry.getValue();
            if(old.equals(next))continue;
            var pos=s.origin.offset(local);var current=level.getBlockState(pos);
            if(current.equals(next))continue;
            if(!current.equals(old)||level.getBlockEntity(pos)!=null)
                throw new IllegalArgumentException("User edit overlaps upgrade at "+pos.toShortString()+"; no blocks changed. Preserve/move that edit first.");
            changes.put(pos,next);
        }
        // Preflight is complete before any write. Edits outside this delta are preserved.
        changes.forEach((pos,state)->level.setBlock(pos,state,2));
        s.metadata=new BuildingMetadata(m.id(),28,34,40,1,BuildingMetadata.Category.MIDRISE_OFFICE,
                Set.of(BuildingMetadata.Zone.CORE,BuildingMetadata.Zone.MIXED),true,true);
        s.changed();return changes.size();
    }
    private void set(int x,int y,int z,BlockState state) {
        if(x<0||x>27||y<0||y>39||z<0||z>33)throw new IllegalStateException("Detail outside plot");
        plan.put(new BlockPos(x,y,z),state);
    }
    private void box(int x0,int y0,int z0,int x1,int y1,int z1,Block b) {
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)set(x,y,z,b.defaultBlockState());
    }
    private void stair(int x,int y,int z,Direction facing) {set(x,y,z,Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,facing));}
    private void door(int x,int y,int z,Direction facing,DoorHingeSide hinge,Block door) {
        var state=door.defaultBlockState().setValue(DoorBlock.FACING,facing).setValue(DoorBlock.HINGE,hinge);
        set(x,y,z,state);set(x,y+1,z,state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
    }
    private void exterior() {
        // Architectural dark mullions; light bands and approved brick piers stay in place.
        for(int f:new int[]{6,11,16,21,26}) {
            for(int x:new int[]{4,13,23})box(x,f+1,33,x,f+3,33,Blocks.GRAY_CONCRETE);
            for(int z:new int[]{6,17,27}) {
                box(0,f+1,z,0,f+3,z,Blocks.GRAY_CONCRETE);box(27,f+1,z,27,f+3,z,Blocks.GRAY_CONCRETE);
            }
        }
        for(int x=8;x<=19;x++)set(x,5,33,Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));
        // Planters are recessed within the frontage; no blocks extend beyond capture bounds.
        for(int x:new int[]{2,22}) {
            box(x,2,31,x+3,2,32,Blocks.STONE_BRICKS);
            box(x,3,31,x+3,3,31,Blocks.OAK_LEAVES);
            for(int a=x;a<=x+3;a++)set(a,3,31,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
        }
        // Four-wide primary opening retained; internal glazed vestibule with open circulation.
        box(10,2,31,11,4,31,Blocks.BLACK_STAINED_GLASS);box(16,2,31,17,4,31,Blocks.BLACK_STAINED_GLASS);
        box(22,2,0,23,4,0,Blocks.AIR);
        door(22,2,0,Direction.NORTH,DoorHingeSide.LEFT,Blocks.DARK_OAK_DOOR);
        door(23,2,0,Direction.NORTH,DoorHingeSide.RIGHT,Blocks.DARK_OAK_DOOR);
        // Recessed rear maintenance ladder and supported landings, industrial detail inside footprint.
        box(25,2,0,25,32,1,Blocks.AIR);
        box(25,1,2,25,34,2,Blocks.POLISHED_BLACKSTONE);
        for(int y=2;y<=33;y++)set(25,y,1,Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH));
        for(int y:new int[]{6,11,16,21,26}) {
            box(24,y,0,24,y,1,Blocks.POLISHED_ANDESITE);
            box(24,y+1,0,24,y+1,0,Blocks.IRON_BARS);
        }
    }
    private void groundFloor() {
        box(9,1,2,11,1,29,Blocks.POLISHED_ANDESITE);
        // Reception beside the lobby, never across its route to the main corridor.
        box(19,2,25,23,2,25,Blocks.DARK_OAK_PLANKS);
        for(int x=19;x<=23;x++)set(x,3,25,Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        chair(21,1,24,Direction.SOUTH);set(22,3,25,Blocks.BLACK_CARPET.defaultBlockState());
        for(int z:new int[]{26,28})chair(5,1,z,Direction.EAST);
        box(3,2,24,3,2,28,Blocks.SPRUCE_PLANKS);
        // Rear-east utility room; decorative storage only, no containers or machines.
        box(18,2,2,18,4,12,Blocks.WHITE_CONCRETE);box(18,2,12,26,4,12,Blocks.WHITE_CONCRETE);
        box(21,2,12,22,4,12,Blocks.AIR);
        for(int z:new int[]{3,6,9}) {
            box(25,2,z,26,3,z+1,Blocks.GRAY_CONCRETE);
            for(int x=25;x<=26;x++)set(x,4,z,Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        }
        // Security/work room on the west, with two-wide corridor access.
        box(8,2,14,8,4,23,Blocks.WHITE_CONCRETE);box(1,2,23,8,4,23,Blocks.WHITE_CONCRETE);
        box(8,2,17,8,4,18,Blocks.AIR);desk(3,1,17);desk(3,1,20);
    }
    private void officeFloors() {
        for(int floor=1;floor<=5;floor++) {
            int f=1+floor*5;
            box(9,f,2,11,f,30,Blocks.POLISHED_ANDESITE);
            box(8,f+1,14,8,f+3,31,Blocks.WHITE_CONCRETE);
            box(12,f+1,10,12,f+3,31,Blocks.WHITE_CONCRETE);
            box(1,f+1,22,8,f+3,22,Blocks.WHITE_CONCRETE);
            int split=floor%2==0?19:20;
            box(12,f+1,split,26,f+3,split,Blocks.WHITE_CONCRETE);
            for(int z:new int[]{17,26}) {
                box(8,f+1,z,8,f+3,z+1,Blocks.AIR);
                box(12,f+1,z,12,f+3,z+1,Blocks.AIR);
            }
            // Glazed upper partition panels; they read as offices rather than solid cells.
            box(8,f+2,20,8,f+3,21,Blocks.GLASS);
            box(12,f+2,22,12,f+3,24,Blocks.GLASS);
            desk(3,f,16);desk(3,f,26);
            // Rear-east meeting room vs front open-office arrangements alternate per floor.
            if(floor%2==0) {meeting(17,f,14);desk(16,f,25);desk(22,f,28);}
            else {desk(16,f,13);desk(22,f,16);meeting(17,f,26);}
            // A third enclosed office in the rear, leaving core circulation x9..11 intact.
            box(18,f+1,2,18,f+3,9,Blocks.WHITE_CONCRETE);
            box(18,f+1,10,26,f+3,10,Blocks.WHITE_CONCRETE);
            box(21,f+1,10,22,f+3,10,Blocks.AIR);desk(21,f,5);
        }
    }
    private void chair(int x,int f,int z,Direction facing) {
        set(x,f+1,z,Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,facing));
    }
    private void desk(int x,int f,int z) {
        for(int a=x;a<x+3;a++)set(a,f+1,z,Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));
        set(x+1,f+2,z,Blocks.BLACK_CARPET.defaultBlockState());chair(x+1,f,z+1,Direction.NORTH);
    }
    private void meeting(int x,int f,int z) {
        for(int a=x;a<=x+4;a++)for(int b=z;b<=z+1;b++)set(a,f+1,b,Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));
        for(int a=x;a<=x+4;a+=2) {chair(a,f,z-1,Direction.SOUTH);chair(a,f,z+2,Direction.NORTH);}
    }
    private void elevator() {
        // Enclosed decorative shaft. Doors are visual only; no elevator gameplay or exposed drop.
        box(13,2,3,17,30,7,Blocks.GRAY_CONCRETE);
        for(int f:new int[]{1,6,11,16,21,26}) {
            box(13,f+1,8,17,f+3,8,Blocks.POLISHED_BLACKSTONE);
            door(14,f+1,8,Direction.SOUTH,DoorHingeSide.LEFT,Blocks.IRON_DOOR);
            door(15,f+1,8,Direction.SOUTH,DoorHingeSide.RIGHT,Blocks.IRON_DOOR);
            set(16,f+2,9,Blocks.STONE_BUTTON.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE,AttachFace.WALL)
                    .setValue(HorizontalDirectionalBlock.FACING,Direction.SOUTH));
        }
    }
    private void stairCore() {
        // Replace only the coarse roof headhouse volume with a hollow, navigable core.
        box(2,32,2,8,37,13,Blocks.AIR);
        box(2,2,2,2,36,13,Blocks.LIGHT_GRAY_CONCRETE);box(8,2,2,8,36,13,Blocks.LIGHT_GRAY_CONCRETE);
        box(2,2,2,8,36,2,Blocks.LIGHT_GRAY_CONCRETE);box(2,2,13,8,36,13,Blocks.LIGHT_GRAY_CONCRETE);
        box(2,37,2,8,37,13,Blocks.SMOOTH_STONE);
        box(3,32,3,7,36,12,Blocks.AIR);
        // Center divider separates two 2-wide alternating stair runs. Two-deep end landings connect them.
        box(5,2,5,5,36,9,Blocks.WHITE_CONCRETE);
        for(int y:new int[]{6,11,16,21,26,31}) {
            box(3,y,5,4,y,9,Blocks.AIR);box(6,y,5,7,y,9,Blocks.AIR);
        }
        for(int i=0;i<6;i++) {
            int f=1+5*i;boolean south=i%2==0;int x0=south?3:6;
            for(int k=0;k<5;k++) {
                int z=south?5+k:9-k;
                for(int x=x0;x<x0+2;x++) {
                    box(x,f+1,z,x,f+k,z,Blocks.POLISHED_ANDESITE);
                    stair(x,f+k+1,z,south?Direction.SOUTH:Direction.NORTH);
                }
            }
            // Openings to corridor on both end landings; no one-wide bottleneck.
            box(8,f+1,3,8,f+3,4,Blocks.AIR);box(8,f+1,11,8,f+3,12,Blocks.AIR);
            // Guard the unused well edge at each level without narrowing the walking stair lane.
            int unused=south?6:3;
            for(int x=unused;x<unused+2;x++)set(x,f+1,south?9:5,Blocks.IRON_BARS.defaultBlockState()
                    .setValue(IronBarsBlock.EAST,true).setValue(IronBarsBlock.WEST,true));
        }
        // Arrival on roof is at Y32; one step up to gravel surface Y33.
        box(8,32,3,8,35,4,Blocks.AIR);
        stair(8,32,3,Direction.EAST);stair(8,32,4,Direction.EAST);
        box(3,34,13,6,35,13,Blocks.BLACK_STAINED_GLASS);
    }
    private void roofEquipment() {
        // Hollow decorative metal housings, fan grilles, small vents. No functional BE blocks.
        box(17,34,7,22,34,11,Blocks.AIR);
        box(16,35,6,23,35,12,Blocks.LIGHT_GRAY_CONCRETE);
        for(int x:new int[]{18,21})for(int z=8;z<=10;z++)
            set(x,36,z,Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF,Half.BOTTOM));
        box(16,34,6,23,34,6,Blocks.POLISHED_BLACKSTONE);
        box(5,34,19,10,34,24,Blocks.LIGHT_GRAY_CONCRETE);
        for(int x=6;x<=9;x++)set(x,35,21,Blocks.IRON_TRAPDOOR.defaultBlockState());
        box(5,33,19,10,33,19,Blocks.POLISHED_BLACKSTONE);
        for(int x:new int[]{13,16,19}) {
            box(x,33,26,x+1,34,27,Blocks.POLISHED_ANDESITE);
            set(x,35,26,Blocks.IRON_TRAPDOOR.defaultBlockState());
        }
        // Minimal age detail, no holes or structural damage.
        set(0,32,6,Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
        set(27,32,29,Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
    }
    private void lighting() {
        for(int f:new int[]{1,6,11,16,21,26}) {
            for(int x:new int[]{4,10,16,23})for(int z:new int[]{7,16,26,30}) {
                if((x<=8&&z<=13)||(x>=13&&x<=17&&z<=8))continue;
                set(x,f+4,z,Blocks.SEA_LANTERN.defaultBlockState());
            }
            set(2,f+3,7,Blocks.SEA_LANTERN.defaultBlockState());
        }
        set(5,36,4,Blocks.SEA_LANTERN.defaultBlockState());
    }
}
