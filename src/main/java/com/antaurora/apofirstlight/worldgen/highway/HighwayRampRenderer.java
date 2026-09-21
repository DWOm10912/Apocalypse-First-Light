package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.block.RoadMarkingBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Local composition over the existing renderer. Parent pavement/median/markings stay authoritative. */
final class HighwayRampRenderer {
    private HighwayRampRenderer() {}
    static double parentLateral(HighwayRampGeometry.Module m,int x,int z) {
        var p=m.incoming().edge();
        return Math.abs(p.orientation()==HighwayRouteGraph.Orientation.NORTH_SOUTH?x-p.fixedCoordinate():z-p.fixedCoordinate());
    }
    static boolean gore(HighwayRampGeometry.Module m,int x,int z) {
        if(m.type()!=HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP)return false;
        var s=m.ribbon().query(x,z,11.5);if(s==null)return false;
        double d=parentLateral(m,x,z);
        var a=m.ribbon().tangent(0);var b=m.ribbon().tangent(m.ribbon().length());
        double side=-Math.signum(a.x()*b.z()-a.z()*b.x());
        return d>11 && d<23 && s.lateral()*side>(d-11)*.75;
    }
    private static boolean goreEdge(HighwayRampGeometry.Module m,int x,int z) {
        return gore(m,x,z) && (!gore(m,x-1,z)||!gore(m,x+1,z)||!gore(m,x,z-1)||!gore(m,x,z+1));
    }
    static boolean suppressFurniture(HighwayRampGeometry.Module m,int x,int z) {
        return m.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP
                && (parentLateral(m,x,z)<=14 || gore(m,x,z));
    }
    static void render(WorldGenLevel level,HighwayRampEngineering e,HighwayBlockWriter target) {
        if(!e.ready())return;
        var m=e.module();boolean junction=m.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP;
        var bounds=m.ribbon().bounds(14.5);
        HighwayBlockWriter writer=new HighwayBlockWriter(){
            public boolean owns(BlockPos p){return target.owns(p)&&bounds.contains(p.getX(),p.getZ())&&m.owns(p.getX(),p.getZ())
                    &&(!junction||parentLateral(m,p.getX(),p.getZ())>11);}
            public boolean set(BlockPos p,BlockState state){
                if(!owns(p))return false;
                var s=m.ribbon().query(p.getX(),p.getZ(),14.5);
                if(s==null)return false;
                int y=e.grade().at(s.station(),p.getX(),p.getZ());
                // The shared merge is pavement, not a second median/parapet or crossing paint grid.
                if(!state.isAir() && p.getY()>y && suppressFurniture(m,p.getX(),p.getZ()))return false;
                return target.set(p,state);
            }
            public boolean mayAffectHorizontal(int x,int z,int radius){return target.mayAffectHorizontal(x,z,radius);}
        };
        HighwayRenderer.renderNatural(level,e.profile(),e.corridor(),writer);
        if(!junction)return;
        for(var c:e.corridor().cells()) {
            if(!target.owns(new BlockPos(c.x(),c.roadY(),c.z())))continue;
            double d=parentLateral(m,c.x(),c.z());
            // Remove only the parent's branch-side parapet at the actual opening; keep median and lane paint.
            if(d>=11 && d<=12 && !m.ribbon().outerEdge(c.x(),c.z())) {
                BlockPos p=new BlockPos(c.x(),c.roadY()+1,c.z());
                if(level.getBlockEntity(p)==null && level.getBlockState(p).is(HighwayPalette.REINFORCED_CONCRETE_SLAB.getBlock()))
                    target.set(p,Blocks.AIR.defaultBlockState());
            }
            if(!goreEdge(m,c.x(),c.z()))continue;
            int mask=0,rises=0;int[][] directions={{0,-1},{1,0},{0,1},{-1,0}};
            for(int i=0;i<4;i++) {
                int x=c.x()+directions[i][0],z=c.z()+directions[i][1];
                if(!goreEdge(m,x,z))continue;
                mask|=1<<i;
                var next=m.ribbon().query(x,z,11.5);
                if(next!=null && e.grade().at(next.station(),x,z)==c.roadY()+1)rises|=1<<i;
            }
            target.set(new BlockPos(c.x(),c.roadY()+1,c.z()),AflBlocks.EDGE_LANE_WHITE.get().defaultBlockState()
                    .setValue(RoadMarkingBlock.FACING,Direction.NORTH).setValue(RoadMarkingBlock.CONNECTIONS,mask==0?16:mask)
                    .setValue(RoadMarkingBlock.RISES,rises));
        }
    }
}
