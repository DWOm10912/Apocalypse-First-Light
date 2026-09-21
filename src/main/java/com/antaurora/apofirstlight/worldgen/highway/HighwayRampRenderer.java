package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.block.RoadMarkingBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local composition over the existing renderer. Parent pavement/median/markings stay authoritative. */
final class HighwayRampRenderer {
    private HighwayRampRenderer() {}
    record LocalMarking(int x,int y,int z,HighwayCorridor.RoadMarkingType type,int connections,int rises) {}
    private record MarkingKey(int x,int z) {}
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
    static boolean suppressFurniture(HighwayRampGeometry.Module m,int x,int z) {
        return m.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP
                && (parentLateral(m,x,z)<=14 || gore(m,x,z));
    }
    static boolean junctionPavement(HighwayRampGeometry.Module m,int x,int z) {
        return m.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP
                && parentLateral(m,x,z)>9 && (parentLateral(m,x,z)<=14 || gore(m,x,z)||m.ribbon().outerEdge(x,z))
                && roadInterior(m,x,z);
    }
    private static boolean roadInterior(HighwayRampGeometry.Module m,int x,int z) {
        for(int[] d:DIRECTIONS)if(!inside(m,x+d[0],z+d[1],11.5,true))return false;
        return true;
    }
    private static final int[][] DIRECTIONS={{0,-1},{1,0},{0,1},{-1,0}};
    /** Continuation is queried only one cell past a port, so port faces are never end caps. */
    private static HighwayGeometry.Sample paintSample(HighwayRampGeometry.Module m,int x,int z) {
        var s=m.ribbon().query(x,z,14.5);if(s!=null)return s;
        for(boolean start:new boolean[]{true,false}) {
            double station=start?0:m.ribbon().length();var p=m.ribbon().point(station);var t=m.ribbon().tangent(station);
            double along=(x-p.x())*t.x()+(z-p.z())*t.z();
            double lateral=-(x-p.x())*t.z()+(z-p.z())*t.x();
            if((start?along<0:along>0)&&Math.abs(along)<=1.01&&Math.abs(lateral)<=14.5)
                return new HighwayGeometry.Sample(station+along,lateral,t.x(),t.z());
        }
        return null;
    }
    private static boolean inside(HighwayRampGeometry.Module m,int x,int z,double width,boolean parent) {
        if(parent&&m.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP&&parentLateral(m,x,z)<=width)return true;
        var s=paintSample(m,x,z);return s!=null&&Math.abs(s.lateral())<=width+1e-8;
    }
    private static int boundary(HighwayRampGeometry.Module m,int x,int z,double width,boolean parent) {
        if(!inside(m,x,z,width,parent))return 0;
        int mask=0;for(int i=0;i<4;i++)if(!inside(m,x+DIRECTIONS[i][0],z+DIRECTIONS[i][1],width,parent))mask|=1<<i;
        if(mask!=0)return 16+mask;
        // Concave staircase vertices need a 2px corner join, not an extra full white loop.
        int corners=0;for(int i=0;i<4;i++) {
            int j=(i+1)%4;
            if(!inside(m,x+DIRECTIONS[i][0]+DIRECTIONS[j][0],z+DIRECTIONS[i][1]+DIRECTIONS[j][1],width,parent))corners|=1<<i;
        }
        return corners==0?0:32+corners;
    }
    static BlockState localSurfaceState(HighwayRampGeometry.Module m,int x,int z,BlockState requested) {
        return requested.is(HighwayPalette.REINFORCED_CONCRETE.getBlock()) && junctionPavement(m,x,z)
                ? HighwayPalette.ASPHALT : requested;
    }
    static void render(WorldGenLevel level,HighwayRampEngineering e,HighwayBlockWriter target) {
        if(!e.ready())return;
        HighwayRenderer.renderNatural(level,e.profile(),e.corridor(),placementWriter(e,target));
        finishSurfaceAndMarkings(level,e,target);
    }
    static HighwayBlockWriter placementWriter(HighwayRampEngineering e,HighwayBlockWriter target) {
        var m=e.module();boolean junction=m.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP;
        var bounds=m.ribbon().bounds(14.5);
        return new HighwayBlockWriter(){
            public boolean owns(BlockPos p){return target.owns(p)&&bounds.contains(p.getX(),p.getZ())&&m.owns(p.getX(),p.getZ())
                    &&(!junction||parentLateral(m,p.getX(),p.getZ())>11);}
            public boolean set(BlockPos p,BlockState state){
                if(!owns(p))return false;
                var s=m.ribbon().query(p.getX(),p.getZ(),14.5);
                if(s==null)return false;
                int y=e.grade().at(s.station(),p.getX(),p.getZ());
                // Ramp modules own their paint. Generic ribbon paint is replaced below with
                // rotation-neutral masks and explicit axial port continuation.
                if(isMarking(state))return false;
                // Outer-edge concrete is structural except where its ribbon edge crosses the
                // Junction merge/gore driving surface at roadY.
                if(junction && p.getY()==y)state=localSurfaceState(m,p.getX(),p.getZ(),state);
                // The shared merge is pavement, not a second median/parapet or crossing paint grid.
                if(!state.isAir() && p.getY()>y && suppressFurniture(m,p.getX(),p.getZ()))return false;
                return target.set(p,state);
            }
            public boolean mayAffectHorizontal(int x,int z,int radius){return target.mayAffectHorizontal(x,z,radius);}
        };
    }

    /** Final local ownership also consumes parent writes made before the Ramp renderer. */
    static void finishSurfaceAndMarkings(WorldGenLevel level,HighwayRampEngineering e,HighwayBlockWriter target) {
        var m=e.module();boolean junction=m.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP;
        for(var c:e.corridor().cells()) {
            BlockPos surface=new BlockPos(c.x(),c.roadY(),c.z());
            if(!m.owns(c.x(),c.z())||!target.owns(surface)||junction&&parentLateral(m,c.x(),c.z())<9)continue;
            boolean opening=junctionPavement(m,c.x(),c.z());
            if(opening&&level.getBlockEntity(surface)==null
                    &&level.getBlockState(surface).is(HighwayPalette.REINFORCED_CONCRETE.getBlock()))
                target.set(surface,HighwayPalette.ASPHALT);
            for(int dy=1;dy<=2;dy++) {
                BlockPos p=surface.above(dy);var state=level.getBlockState(p);
                if(level.getBlockEntity(p)==null&&(isMarking(state)||opening&&
                        (state.is(HighwayPalette.REINFORCED_CONCRETE.getBlock())||state.is(HighwayPalette.REINFORCED_CONCRETE_SLAB.getBlock()))))
                    target.set(p,Blocks.AIR.defaultBlockState());
            }
        }
        placeLocalMarkings(e,target);
    }

    static java.util.List<LocalMarking> localMarkings(HighwayRampEngineering e) {
        var m=e.module();Map<MarkingKey,LocalMarking> selected=new LinkedHashMap<>();
        java.util.List<LocalMarking> edges=new java.util.ArrayList<>();
        for(var c:e.corridor().cells()) {
            if(m.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP&&parentLateral(m,c.x(),c.z())<9)continue;
            int white=boundary(m,c.x(),c.z(),10.5,true);
            if(white!=0)edges.add(new LocalMarking(c.x(),c.roadY()+1,c.z(),HighwayCorridor.RoadMarkingType.WHITE_EDGE,white,0));
            int yellow=suppressFurniture(m,c.x(),c.z())?0:boundary(m,c.x(),c.z(),2.5,false);
            if(yellow!=0)edges.add(new LocalMarking(c.x(),c.roadY()+1,c.z(),HighwayCorridor.RoadMarkingType.YELLOW_EDGE,yellow,0));
            var detail=m.ribbon().rampMarking(c.x(),c.z());
            if(detail==null || Math.abs(detail.band())!=6 || suppressFurniture(m,c.x(),c.z())
                    ||m.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP&&parentLateral(m,c.x(),c.z())<24)continue;
            var type=markingType(detail.band());
            if(type!=null)selected.put(new MarkingKey(c.x(),c.z()),new LocalMarking(c.x(),c.roadY()+1,c.z(),type,0,0));
        }
        java.util.List<LocalMarking> result=new java.util.ArrayList<>(edges);
        int[][] directions={{0,-1},{1,0},{0,1},{-1,0}};
        for(var mark:selected.values()) {
            int mask=0,rises=0,actual=0;
            for(int i=0;i<4;i++) {
                var next=selected.get(new MarkingKey(mark.x()+directions[i][0],mark.z()+directions[i][1]));
                if(next==null || next.type()!=mark.type() || Math.abs(next.y()-mark.y())>1)continue;
                mask|=1<<i;actual++;
                if(next.y()==mark.y()+1)rises|=1<<i;
            }
            var sample=m.ribbon().query(mark.x(),mark.z(),HighwayGeometry.ROAD_HALF_WIDTH);
            if(sample!=null && sample.station()<.75)mask|=directionBit(opposite(cardinal(m.ribbon().tangent(0))));
            if(sample!=null && sample.station()>m.ribbon().length()-.75)
                mask|=directionBit(cardinal(m.ribbon().tangent(m.ribbon().length())));
            if(actual>0)result.add(new LocalMarking(mark.x(),mark.y(),mark.z(),mark.type(),mask,rises));
        }
        return java.util.List.copyOf(result);
    }

    private static void placeLocalMarkings(HighwayRampEngineering e,HighwayBlockWriter target) {
        for(var mark:localMarkings(e)) {
            BlockState state=switch(mark.type()) {
                case WHITE_EDGE -> AflBlocks.EDGE_LANE_WHITE.get().defaultBlockState();
                case YELLOW_EDGE -> AflBlocks.EDGE_LANE_YELLOW.get().defaultBlockState();
                case WHITE_LANE_DIVIDER -> AflBlocks.WHITE_LANE_DIVIDER.get().defaultBlockState();
            };
            BlockPos p=new BlockPos(mark.x(),mark.y(),mark.z());
            if(e.module().owns(mark.x(),mark.z())&&target.owns(p))target.set(p,state.setValue(RoadMarkingBlock.FACING,Direction.NORTH)
                    .setValue(RoadMarkingBlock.CONNECTIONS,mark.connections()).setValue(RoadMarkingBlock.RISES,mark.rises()));
        }
    }
    private static HighwayCorridor.RoadMarkingType markingType(int band) {
        return switch(band){case -10,10->HighwayCorridor.RoadMarkingType.WHITE_EDGE;
            case -2,2->HighwayCorridor.RoadMarkingType.YELLOW_EDGE;
            case -6,6->HighwayCorridor.RoadMarkingType.WHITE_LANE_DIVIDER;default->null;};
    }
    private static Direction cardinal(HighwayGeometry.Point p) {
        return Math.abs(p.x())>Math.abs(p.z())?(p.x()>0?Direction.EAST:Direction.WEST)
                :(p.z()>0?Direction.SOUTH:Direction.NORTH);
    }
    private static Direction opposite(Direction d){return d.getOpposite();}
    private static int directionBit(Direction d){return switch(d){case NORTH->1;case EAST->2;case SOUTH->4;case WEST->8;default->0;};}
    private static boolean isMarking(BlockState s) {
        return s.is(AflBlocks.EDGE_LANE_WHITE.get())||s.is(AflBlocks.EDGE_LANE_YELLOW.get())
                ||s.is(AflBlocks.WHITE_LANE_DIVIDER.get())||s.is(AflBlocks.EDGE_LANE_WHITE_STEP_CONNECTOR.get())
                ||s.is(AflBlocks.EDGE_LANE_YELLOW_STEP_CONNECTOR.get())||s.is(AflBlocks.WHITE_LANE_DIVIDER_STEP_CONNECTOR.get());
    }
}
