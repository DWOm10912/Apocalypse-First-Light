package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;
import java.util.List;

/** One real graph, one fixture profile, one adjacent chunk pair. Never generates world chunks. */
public final class SeaBridgeV1Test {
    private static int checks;
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var boot=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        boot.setAccessible(true);boot.setBoolean(null,true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        bindPalette();
        var graph=HighwayRouteGraph.build(-4332662446239654818L);
        check(graph.seaCrossings().size()==1,"one real satellite crossing");
        var geometry=SeaBridgeGeometry.of(graph.seaCrossings().get(0));
        check(geometry.plan().length()>=geometry.crossing().span()
                && geometry.plan().length()-geometry.crossing().span()<=14,"bounded shore seam only");
        check(geometry.plan().length()>=300 && geometry.plan().length()<=800,"valid bank span");
        for(var node:List.of(geometry.crossing().mainland(),geometry.crossing().satellite())) {
            var edge=graph.edges().stream().filter(e->e.startNode().equals(node)||e.endNode().equals(node)).findFirst().orElseThrow();
            long station=edge.clampStation(edge.globalStation(node.x(),node.z()));
            check(station==geometry.plan().globalStation(0)
                    ||station==geometry.plan().globalStation(geometry.plan().length()),"abutment meets actual clipped road endpoint");
        }
        var bridge=SeaBridgeEngineering.plan(null,geometry,70,78,
                (x,z,bottom)->new HighwayTerrainSampler.PierFoundation(true,40,true));
        check(bridge.ready() && bridge.corridor().cells().size()>0,"deck cells");
        check(bridge.pierCount()>0,"piers");
        check(bridge.abutmentStatus(true).equals("READY") && bridge.abutmentStatus(false).equals("READY"),"two abutments");
        check(bridge.abutments().stream().allMatch(s->s.top()<=74),"abutments below pavement");
        check(bridge.profile().sampleAt(0).roadY()==70
                && bridge.profile().sampleAt(geometry.plan().length()).roadY()==78,"endpoint continuity");
        check(bridge.corridor().tunnelSections().isEmpty(),"no new tunnel system");
        System.out.println("Case A PASS: deckCells="+bridge.corridor().cells().size()+" piers="+bridge.pierCount()
                +" abutments=READY/READY; fixture grades=70/78 seabed=40 (not live terrain)");
        seam(bridge);
        System.out.println("Case C PASS: seed=-4332662446239654818 crossing="+geometry.crossing().id()
                +" mainland="+geometry.crossing().mainland()+" satellite="+geometry.crossing().satellite()
                +" axis="+geometry.crossing().dx()+","+geometry.crossing().dz()+" actualBankSpan="+geometry.crossing().span()
                +" deckAndAbutmentLength="+geometry.plan().length()
                +" plannedPierCount="+geometry.pierStations().size());
        System.out.println("SeaBridgeV1Test PASS checks="+checks+"; A/B/C only; no world/client/terrain generation");
    }

    private static void seam(SeaBridgeEngineering bridge) throws Exception {
        var plan=bridge.geometry().plan();boolean ns=plan.tangent(0).z()!=0;
        int boundary=(int)Math.ceil((plan.globalStation(0)+64)/16)*16;
        int fixed=(int)Math.round(ns?plan.sample(0).x():plan.sample(0).z());
        Map<BlockPos,BlockState> states=new java.util.HashMap<>();
        final ChunkPos[] owner={null};
        var level=(WorldGenLevel)java.lang.reflect.Proxy.newProxyInstance(SeaBridgeV1Test.class.getClassLoader(),
                new Class[]{WorldGenLevel.class},(p,m,a)->switch(m.getName()) {
                    case "getBlockState" -> states.getOrDefault(a[0],Blocks.AIR.defaultBlockState());
                    case "getBlockEntity" -> null;
                    case "getMinBuildHeight" -> -64;
                    case "getMaxBuildHeight" -> 320;
                    case "ensureCanWrite" -> true;
                    case "setBlock" -> {
                        BlockPos pos=(BlockPos)a[0];
                        if((pos.getX()>>4)!=owner[0].x || (pos.getZ()>>4)!=owner[0].z)
                            throw new AssertionError("cross-chunk write "+pos);
                        states.put(pos.immutable(),(BlockState)a[1]);yield true;
                    }
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        var placement=HighwayRenderer.class.getDeclaredMethod("runPlacementPasses",WorldGenLevel.class,
                HighwayBlockWriter.class,HighwayRenderStats.class,HighwayCorridor.class,HighwayProfile.class);
        placement.setAccessible(true);
        for(int axis: new int[]{boundary-1,boundary}) {
            owner[0]=new ChunkPos((ns?fixed+5:axis)>>4,(ns?axis:fixed+5)>>4);
            var writer=new FiniteRouteHighwayWriter(new ChunkOwnedHighwayWriter(level,owner[0]),bridge.geometry().bounds());
            placement.invoke(null,level,writer,new HighwayRenderStats(),bridge.corridor(),bridge.profile());
        }
        int before=bridge.profile().sampleAt(plan.localDistance(boundary-1)).roadY();
        int after=bridge.profile().sampleAt(plan.localDistance(boundary)).roadY();
        check(Math.abs(after-before)<=1,"adjacent grade");
        for(int axis: new int[]{boundary-1,boundary}) {
            int y=bridge.profile().sampleAt(plan.localDistance(axis)).roadY();
            var pos=new BlockPos(ns?fixed+5:axis,y,ns?axis:fixed+5);
            check(states.get(pos).is(HighwayPalette.ASPHALT.getBlock()),"final writer asphalt across seam");
        }
        System.out.println("Case B PASS: two chunk-owned placement passes, seam="+boundary+" grade="+before+"/"+after);
    }

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
    private static void check(boolean ok,String message) { checks++;if(!ok)throw new AssertionError(message); }
}
