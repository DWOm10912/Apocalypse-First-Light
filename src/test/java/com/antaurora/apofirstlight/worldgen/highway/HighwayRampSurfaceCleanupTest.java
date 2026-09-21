package com.antaurora.apofirstlight.worldgen.highway;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Two-case in-memory replay of real placement writers and final block shapes. No world/chunks. */
public final class HighwayRampSurfaceCleanupTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        // This harness needs block factories only. Full Forge Bootstrap initializes transformed
        // network events, which a plain JavaExec intentionally does not load.
        var boot=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        boot.setAccessible(true);boot.setBoolean(null,true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        bindPalette();
        var graph=HighwayRouteGraph.build(-4332662446239654818L);
        var junction=module(graph,-961,-6504);
        var turn=module(graph,-2592,-6504);
        check(junction.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP,"Case A module type");
        check(turn.type()==HighwayRampGeometry.Type.TURN_RAMP,"Case B module type");
        caseA(engineering(junction));
        caseB(engineering(turn));
        System.out.println("HighwayRampSurfaceCleanupTest PASS: "+checks+" checks; Case A junction + Case B W->N TURN; no world");
    }

    private static void caseA(HighwayRampEngineering e) {
        boolean gore=false,structuralEdge=false;
        for(var c:e.corridor().cells()) {
            if(HighwayRampRenderer.gore(e.module(),c.x(),c.z())) {
                gore=true;
            } else if(!HighwayRampRenderer.junctionPavement(e.module(),c.x(),c.z())) {
                structuralEdge=true;
            }
        }
        check(gore,"Case A has gore cells");check(structuralEdge,"Case A retains structural edge cells");
        verifyMarkings(e,"Case A");
        replay(e,true);
    }

    private static void caseB(HighwayRampEngineering e) {
        var a=e.module().ribbon().tangent(0);var b=e.module().ribbon().tangent(e.module().ribbon().length());
        check(a.x()<-.99 && Math.abs(a.z())<.01,"Case B incoming W");
        check(b.z()<-.99 && Math.abs(b.x())<.01,"Case B outgoing N");
        var marks=verifyMarkings(e,"Case B");int start=0,end=0;
        for(var mark:marks) {
            var s=e.module().ribbon().query(mark.x(),mark.z(),HighwayGeometry.ROAD_HALF_WIDTH);
            if(mark.type()==HighwayCorridor.RoadMarkingType.WHITE_LANE_DIVIDER)continue;
            if(s.station()<.75){start++;check(mark.connections()>16,"entry uses legacy-aligned boundary model");}
            if(s.station()>e.module().ribbon().length()-.75){end++;check(mark.connections()>16,"exit uses legacy-aligned boundary model");}
        }
        check(start>0 && end>0,"Case B both ports own continuation markings");
        replay(e,false);
    }

    // Bind only actual AFL palette suppliers in this isolated JVM; no mod/client/world is launched.
    @SuppressWarnings("unchecked")
    private static void bindPalette() throws Exception {
        var deferred=com.antaurora.apofirstlight.registry.AflBlocks.BLOCKS;
        var field=net.minecraftforge.registries.DeferredRegister.class.getDeclaredField("entries");field.setAccessible(true);
        var entries=(Map<net.minecraftforge.registries.RegistryObject<?>,java.util.function.Supplier<?>>)field.get(deferred);
        var value=net.minecraftforge.registries.RegistryObject.class.getDeclaredField("value");value.setAccessible(true);
        for(String name:List.of("reinforced_concrete","reinforced_concrete_slab","asphalt","edge_lane_white","edge_lane_yellow",
                "white_lane_divider","edge_lane_white_step_connector","edge_lane_yellow_step_connector","white_lane_divider_step_connector")) {
            var entry=entries.entrySet().stream().filter(v->v.getKey().getId().getPath().equals(name)).findFirst().orElseThrow();
            value.set(entry.getKey(),entry.getValue().get());
        }
    }

    private static void replay(HighwayRampEngineering e,boolean junction) {
        try {
            Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState> states=new java.util.HashMap<>();
            var level=(net.minecraft.world.level.WorldGenLevel)java.lang.reflect.Proxy.newProxyInstance(
                    HighwayRampSurfaceCleanupTest.class.getClassLoader(),new Class[]{net.minecraft.world.level.WorldGenLevel.class},(p,m,a)->switch(m.getName()) {
                        case "getBlockState" -> states.getOrDefault(a[0],net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        case "getBlockEntity" -> null;
                        case "getMinBuildHeight" -> -64;
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
            HighwayBlockWriter target=new HighwayBlockWriter(){
                public boolean owns(net.minecraft.core.BlockPos p){return e.module().owns(p.getX(),p.getZ());}
                public boolean set(net.minecraft.core.BlockPos p,net.minecraft.world.level.block.state.BlockState s){if(!owns(p))return false;states.put(p.immutable(),s);return true;}
                public boolean mayAffectHorizontal(int x,int z,int r){return true;}
            };
            var placement=HighwayRenderer.class.getDeclaredMethod("runPlacementPasses",net.minecraft.world.level.WorldGenLevel.class,
                    HighwayBlockWriter.class,HighwayRenderStats.class,HighwayCorridor.class,HighwayProfile.class);placement.setAccessible(true);
            if(junction){var parent=e.grade().parentProfile();placement.invoke(null,level,target,new HighwayRenderStats(),HighwayCorridor.buildNatural(null,parent.plan(),parent),parent);}
            placement.invoke(null,level,HighwayRampRenderer.placementWriter(e,target),new HighwayRenderStats(),e.corridor(),e.profile());
            Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState> before=new java.util.HashMap<>(states);
            HighwayRampRenderer.finishSurfaceAndMarkings(level,e,target);
            int converted=0;
            for(var c:e.corridor().cells())if(HighwayRampRenderer.junctionPavement(e.module(),c.x(),c.z())) {
                var p=new net.minecraft.core.BlockPos(c.x(),c.roadY(),c.z());
                check(states.get(p).is(HighwayPalette.ASPHALT.getBlock()),"FINAL drivable state must be asphalt at "+p);
                if(before.get(p).is(HighwayPalette.REINFORCED_CONCRETE.getBlock()))converted++;
            }
            if(junction)check(converted>0,"replay must expose parent concrete missed by old ramp writer");
            for(var mark:HighwayRampRenderer.localMarkings(e)) {
                var p=new net.minecraft.core.BlockPos(mark.x(),mark.y(),mark.z());
                var state=states.get(p);
                check(state.getValue(com.antaurora.apofirstlight.block.RoadMarkingBlock.CONNECTIONS)==mark.connections(),"FINAL producer state "+p);
                var sample=e.module().ribbon().query(mark.x(),mark.z(),HighwayGeometry.ROAD_HALF_WIDTH);
                if(mark.type()!=HighwayCorridor.RoadMarkingType.WHITE_LANE_DIVIDER&&sample!=null
                        &&(sample.station()<.01||sample.station()>e.module().ribbon().length()-.01)) {
                    double side=Math.signum(sample.lateral());
                    var facing=net.minecraft.core.Direction.fromDelta((int)Math.round(-sample.tangentZ()*side),0,(int)Math.round(sample.tangentX()*side));
                    var straight=state.setValue(com.antaurora.apofirstlight.block.RoadMarkingBlock.CONNECTIONS,0)
                            .setValue(com.antaurora.apofirstlight.block.RoadMarkingBlock.FACING,facing);
                    check(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(state.getShape(level,p),straight.getShape(level,p),
                            net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"actual port shape equals straight edge, including lateral pixel offset "+p);
                }
            }
            if(!junction)for(var entry:before.entrySet())if(!entry.getValue().isAir()
                    &&!(entry.getValue().getBlock() instanceof com.antaurora.apofirstlight.block.RoadMarkingBlock)
                    &&!(entry.getValue().getBlock() instanceof com.antaurora.apofirstlight.block.RoadMarkingStepConnectorBlock))
                check(entry.getValue().equals(states.get(entry.getKey())),"TURN final road/structure unchanged "+entry.getKey());
            for(var type:List.of(HighwayCorridor.RoadMarkingType.WHITE_EDGE,HighwayCorridor.RoadMarkingType.YELLOW_EDGE)) {
                var pixels=new HashSet<Long>();
                for(var mark:HighwayRampRenderer.localMarkings(e))if(mark.type()==type) {
                    var p=new net.minecraft.core.BlockPos(mark.x(),mark.y(),mark.z());
                    states.get(p).getShape(level,p).forAllBoxes((x0,y0,z0,x1,y1,z1)->{
                        for(int x=(int)Math.round(x0*16);x<(int)Math.round(x1*16);x++)
                            for(int z=(int)Math.round(z0*16);z<(int)Math.round(z1*16);z++)pixels.add(pixel(mark.x()*16+x,mark.z()*16+z));
                    });
                }
                if(junction&&type==HighwayCorridor.RoadMarkingType.WHITE_EDGE)for(var entry:states.entrySet())
                    if(entry.getValue().is(com.antaurora.apofirstlight.registry.AflBlocks.EDGE_LANE_WHITE.get())) {
                        var p=entry.getKey();entry.getValue().getShape(level,p).forAllBoxes((x0,y0,z0,x1,y1,z1)->{
                            for(int x=(int)Math.round(x0*16);x<(int)Math.round(x1*16);x++)
                                for(int z=(int)Math.round(z0*16);z<(int)Math.round(z1*16);z++)pixels.add(pixel(p.getX()*16+x,p.getZ()*16+z));
                        });
                    }
                int components=0;
                while(!pixels.isEmpty()) {
                    components++;var queue=new java.util.ArrayDeque<Long>();long first=pixels.iterator().next();pixels.remove(first);queue.add(first);
                    while(!queue.isEmpty()){long key=queue.removeFirst();int x=(int)(key>>32),z=(int)key;
                        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){long next=pixel(x+d[0],z+d[1]);if(pixels.remove(next))queue.add(next);}}
                }
                System.out.println((junction?"JUNCTION":"TURN")+" "+type+" final shape components="+components);
                if(!junction)check(components==2,"TURN has exactly two continuous "+type+" lines at 1/16-block resolution");
                else check(components==(type==HighwayCorridor.RoadMarkingType.WHITE_EDGE?3:2),
                        "Junction final line components include both joins to parent, with no isolated white loop");
            }
            System.out.println((junction?"JUNCTION":"TURN")+" actual placement replay: parent concrete corrected="+converted+", final markings="+HighwayRampRenderer.localMarkings(e).size());
        }catch(Exception failure){throw new RuntimeException(failure);}
    }
    private static long pixel(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}

    private static List<HighwayRampRenderer.LocalMarking> verifyMarkings(HighwayRampEngineering e,String label) {
        var marks=HighwayRampRenderer.localMarkings(e);check(!marks.isEmpty(),label+" markings exist");
        var positions=new HashSet<String>();
        for(var m:marks){check(m.connections()!=0,label+" no isolated marking");
            check(positions.add(m.x()+","+m.y()+","+m.z()),label+" no duplicate marking position");}
        return marks;
    }

    private static HighwayRampEngineering engineering(HighwayRampGeometry.Module module) throws Exception {
        var parent=flatProfile(module.incoming().edge(),80,module.incoming().station()-40,module.incoming().station()+80);
        var grade=new HighwayRampGrade(module,parent,80,80);
        var plan=HighwayPlan.ribbon(module.ribbon(),0,module.ribbon().length());
        List<HighwayProfile.Sample> samples=new ArrayList<>();
        for(double s:new double[]{0,plan.length()}){var p=plan.sample(s);var t=plan.tangent(s);
            samples.add(new HighwayProfile.Sample(s,p.x(),p.z(),t.x(),t.z(),80,80,HighwayTerrainMode.GROUND,
                    HighwayTerrainMode.GROUND,false,80,80,80,80,0,23,0,0,0,0,80,80,false,false));}
        var resolution=new HighwayBridgeSpanResolver.Resolution(samples,List.of(),0,0,0,0,0);
        var ctor=HighwayProfile.class.getDeclaredConstructor(HighwayPlan.class,HighwayBridgeSpanResolver.Resolution.class,
                int.class,double.class,boolean.class,HighwayNodeConstraints.class,HighwayBranchGrade.class,Map.class,HighwayRampGrade.class);
        ctor.setAccessible(true);
        var profile=(HighwayProfile)ctor.newInstance(plan,resolution,0,0,false,HighwayNodeConstraints.NONE,null,Map.of(),grade);
        var corridor=HighwayCorridor.buildNatural(null,plan,profile);
        return new HighwayRampEngineering(module,grade,profile,corridor,"READY");
    }

    private static HighwayProfile flatProfile(HighwayRouteGraph.Edge edge,int y,int start,int end)throws Exception {
        var p=HighwayPlan.linear(new HighwayPlan.Point(edge.orientation()==HighwayRouteGraph.Orientation.NORTH_SOUTH?edge.fixedCoordinate():start,
                edge.orientation()==HighwayRouteGraph.Orientation.NORTH_SOUTH?start:edge.fixedCoordinate()),
                new HighwayPlan.Point(edge.orientation()==HighwayRouteGraph.Orientation.NORTH_SOUTH?edge.fixedCoordinate():end,
                        edge.orientation()==HighwayRouteGraph.Orientation.NORTH_SOUTH?end:edge.fixedCoordinate()),23,start);
        List<HighwayProfile.Sample> samples=new ArrayList<>();
        for(double s:new double[]{0,p.length()}){var a=p.sample(s);var t=p.tangent(s);
            samples.add(new HighwayProfile.Sample(s,a.x(),a.z(),t.x(),t.z(),y,y,HighwayTerrainMode.GROUND,
                    HighwayTerrainMode.GROUND,false,y,y,y,y,0,23,0,0,0,0,y,y,false,false));}
        var ctor=HighwayProfile.class.getDeclaredConstructor(HighwayPlan.class,HighwayBridgeSpanResolver.Resolution.class,
                int.class,double.class,boolean.class,HighwayNodeConstraints.class);
        ctor.setAccessible(true);
        return ctor.newInstance(p,new HighwayBridgeSpanResolver.Resolution(samples,List.of(),0,0,0,0,0),0,0,false,HighwayNodeConstraints.NONE);
    }

    private static HighwayRampGeometry.Module module(HighwayRouteGraph graph,int x,int z) {
        var zone=graph.reservedZones().stream().filter(r->r.node().x()==x&&r.node().z()==z).findFirst().orElseThrow();
        return HighwayRampGeometry.build(graph,zone);
    }
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
}
