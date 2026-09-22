package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.lang.reflect.Proxy;
import java.util.*;

/** Actual production render into a bounded in-memory world; no client, chunks or world are generated. */
public final class SeaBridgeLandmarkTest {
    private static int checks;
    private static final SeaBridgeEngineering.FoundationSampler SOLID =
            (x,z,b) -> new HighwayTerrainSampler.PierFoundation(true,40,true);

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var boot=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        boot.setAccessible(true);boot.setBoolean(null,true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        var bind=SeaBridgeV1Test.class.getDeclaredMethod("bindPalette");bind.setAccessible(true);bind.invoke(null);
        var graph=HighwayRouteGraph.build(-4332662446239654818L);
        var geometry=SeaBridgeGeometry.of(graph.seaCrossings().get(0));
        var bridge=SeaBridgeEngineering.plan(null,geometry,70,78,SOLID);
        check(geometry.plan().length()==632,"representative live graph length");
        check(bridge.landmark().enabled(),"representative enabled with valid sampled foundations");
        check(bridge.landmark().span().pylonA()==188 && bridge.landmark().span().pylonB()==444,"frozen pylons");
        check(bridge.landmark().foundationStatus().equals("READY:132_COLUMNS"),"four complete footings");
        check(bridge.landmark().anchors().size()==144,"72 tower + 72 deck sockets");
        check(bridge.suppressedPierStations().size()==9 && bridge.retainedPierStations().size()==10,"representative suppression");
        validateStructure(bridge);
        var forward=render(bridge,false);
        check(forward.equals(render(bridge,true)),"chunk order independence of full production render");
        for(var p:bridge.landmark().placements())check(p.state().equals(forward.get(p.pos())),"no missing structure at chunk seam");

        int[] calls={0};
        var failed=SeaBridgeEngineering.plan(null,geometry,70,78,(x,z,b)->
                new HighwayTerrainSampler.PierFoundation(++calls[0]!=132,40,true));
        check(!failed.landmark().enabled() && failed.landmark().placements().isEmpty(),"last foundation failure is atomic");
        check(failed.landmark().fallbackReason().equals("PYLON_FOUNDATION_FAILED"),"foundation reason");
        check(failed.retainedPierStations().equals(geometry.pierStations()) && failed.suppressedPierStations().isEmpty(),"all V1 piers restored");
        check(failed.pierCount()==19,"all restored piers sampled");
        var baseline=render(failed,false);
        for(var cell:bridge.corridor().cells())for(int y=cell.roadY()-3;y<=cell.roadY()+6;y++) {
            var pos=new BlockPos(cell.x(),y,cell.z());
            check(Objects.equals(forward.get(pos),baseline.get(pos)),"road +/-11 identical including grade/furniture: "+pos);
        }
        for(long station:bridge.suppressedPierStations()) {
            var pos=LandmarkMainSpan.position(geometry.plan(),geometry.plan().localDistance(station),0,42);
            check(!forward.containsKey(pos) && baseline.containsKey(pos),"main span truly open below deck");
        }
        for(int[] axis:new int[][]{{0,1},{0,-1},{1,0},{-1,0}}) {
            var rotated=synthetic(geometry.crossing(),axis[0],axis[1],632);
            var b=SeaBridgeEngineering.plan(null,rotated,70,78,SOLID);
            check(b.landmark().enabled(),"cardinal enabled "+Arrays.toString(axis));
            validateStructure(b);
            var repeated=SeaBridgeEngineering.plan(null,rotated,70,78,SOLID);
            check(b.landmark().equals(repeated.landmark()),"deterministic full plan");
            var written=render(b,false);
            for(var block:b.landmark().placements())check(block.state().equals(written.get(block.pos())),"cardinal production placements");
        }
        var shortBridge=SeaBridgeEngineering.plan(null,synthetic(geometry.crossing(),0,1,400),70,78,SOLID);
        check(!shortBridge.landmark().enabled() && shortBridge.landmark().fallbackReason().equals("BRIDGE_TOO_SHORT"),"short bridge fallback");
        check(shortBridge.retainedPierStations().equals(shortBridge.geometry().pierStations()),"short keeps ordinary piers");
        var high=SeaBridgeEngineering.plan(null,geometry,260,260,SOLID);
        check(!high.landmark().enabled() && high.landmark().fallbackReason().equals("BUILD_HEIGHT"),"height guard");
        var clipped=new SeaBridgeGeometry(geometry.crossing(),geometry.plan(),
                new BoundsXZ(-2593,-7640,-2590,-7007),geometry.mainlandFirst());
        check(!SeaBridgeEngineering.plan(null,clipped,70,78,SOLID).landmark().enabled(),"envelope fail closed");
        var boundary=bridge.landmark().span();
        check(boundary.suppresses(geometry.plan(),(long)geometry.plan().globalStation(180)),"footing tangent intersection");
        check(!boundary.suppresses(geometry.plan(),(long)geometry.plan().globalStation(179)),"outside suppression boundary");
        System.out.println("SeaBridgeLandmarkTest PASS checks="+checks+" actual production plan/render, fixture grade=70/78 seabed=40; no world generated");
        System.out.println("REPRESENTATIVE "+bridge.landmarkDescription());
        for(int p:bridge.landmark().span().pylons())System.out.println("PYLON "+LandmarkMainSpan.position(geometry.plan(),p,0,bridge.profile().sampleAt(p).roadY()));
    }

    private static void validateStructure(SeaBridgeEngineering b) {
        var plan=b.geometry().plan();var t=plan.tangent(0);var origin=plan.sample(0);
        Set<BlockPos> positions=new HashSet<>();
        for(var block:b.landmark().placements()) {
            var pos=block.pos();double dx=pos.getX()-origin.x(),dz=pos.getZ()-origin.z();
            double l=-dx*t.z()+dz*t.x(),s=dx*t.x()+dz*t.z();
            check(Math.abs(l)<=14 && b.geometry().bounds().contains(pos.getX(),pos.getZ()),"finite envelope");
            check(Math.abs(l)>11 || pos.getY()>=b.profile().sampleAt(s).roadY()+44,"road clearance");
            check(positions.add(pos),"deduplicated placements");
            check(!block.state().is(AflBlocks.STEEL_BLOCK.get()),"no visible steel marker blocks");
            if(block.state().is(AflBlocks.STEEL_BEAM.get()))check(block.state().getValue(RotatedPillarBlock.AXIS)
                    ==net.minecraft.core.Direction.Axis.Y,"crossbeam vertical beam axis");
        }
        var mapped=new HashMap<BlockPos,BlockState>();
        b.landmark().placements().forEach(p->mapped.put(p.pos(),p.state()));
        for(var a:b.landmark().anchors())check(!mapped.containsKey(a.socket()),"future anchor is data-only");
        // Identical local above-deck tower geometry, independent of differing longitudinal grades.
        for(int tower=0;tower<2;tower++) {
            int p=b.landmark().span().pylons().get(tower),d=b.landmark().baseYs().get(tower);
            for(int face:new int[]{-1,1}) {
                for(int l:new int[]{-11,-10,10,11})for(int h=48;h<=50;h++)
                    check(mapped.get(LandmarkMainSpan.position(plan,p+face*2,l,d+h)).is(HighwayPalette.REINFORCED_CONCRETE.getBlock()),
                            "crossbeam concrete end jamb");
                for(int l:new int[]{-9,-3,3,9})for(int h=48;h<=50;h++) {
                    var state=mapped.get(LandmarkMainSpan.position(plan,p+face*2,l,d+h));
                    check(state.is(AflBlocks.STEEL_BEAM.get()) && state.getValue(RotatedPillarBlock.AXIS)==net.minecraft.core.Direction.Axis.Y,
                            "vertical steel beam spans crossbeam gap");
                }
                for(int l:new int[]{-6,0,6})for(int h=48;h<=50;h++) {
                    var state=mapped.get(LandmarkMainSpan.position(plan,p+face*2,l,d+h));
                    check(state.is(AflBlocks.STEEL_BRACE.get()) && state.getValue(RotatedPillarBlock.AXIS)==net.minecraft.core.Direction.Axis.Y,
                            "vertical steel brace spans crossbeam gap");
                }
                for(int l:new int[]{-9,-6,-3,0,3,6,9}) {
                    check(mapped.get(LandmarkMainSpan.position(plan,p+face*2,l,d+47)).is(HighwayPalette.REINFORCED_CONCRETE.getBlock()),
                            "steel web touches lower concrete chord");
                    check(mapped.get(LandmarkMainSpan.position(plan,p+face*2,l,d+51)).is(HighwayPalette.REINFORCED_CONCRETE.getBlock()),
                            "steel web touches upper concrete chord");
                }
            }
            for(int ds=-4;ds<=4;ds++)for(int l=-14;l<=14;l++)for(int h=6;h<=64;h++) {
                var a=mapped.get(LandmarkMainSpan.position(plan,p+ds,l,d+h));
                var mirror=mapped.get(LandmarkMainSpan.position(plan,p+ds,-l,d+h));
                check(Objects.equals(a,mirror),"left/right symmetry");
                int q=b.landmark().span().pylons().get(1-tower),e=b.landmark().baseYs().get(1-tower);
                check(Objects.equals(a,mapped.get(LandmarkMainSpan.position(plan,q+ds,l,e+h))),"two tower symmetry");
            }
        }
    }

    private static SeaBridgeGeometry synthetic(SatelliteHighwayRouting.Connection source,int dx,int dz,int length) {
        var a=new HighwayRouteGraph.Node("a",HighwayRouteGraph.NodeKind.MAINLAND_BRIDGEHEAD,-32,-640);
        var b=new HighwayRouteGraph.Node("b",HighwayRouteGraph.NodeKind.SATELLITE_BRIDGEHEAD,a.x()+dx*length,a.z()+dz*length);
        return SeaBridgeGeometry.of(new SatelliteHighwayRouting.Connection("fixture",1,source.source(),source.routeId(),source.parent(),
                a,b,dx,dz,length,source.mainlandGeometry(),source.islandGeometry()));
    }

    private static Map<BlockPos,BlockState> render(SeaBridgeEngineering bridge,boolean reverse) {
        Map<BlockPos,BlockState> states=new HashMap<>();final ChunkPos[] owner={null};
        var level=(WorldGenLevel)Proxy.newProxyInstance(SeaBridgeLandmarkTest.class.getClassLoader(),new Class[]{WorldGenLevel.class},
            (proxy,m,args)->switch(m.getName()) {
                case "getBlockState" -> states.getOrDefault(args[0],((BlockPos)args[0]).getY()<=40?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
                case "getFluidState" -> net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
                case "getBlockEntity" -> null;
                case "getHeight" -> 41;
                case "getMinBuildHeight" -> -64;
                case "getMaxBuildHeight" -> 320;
                case "ensureCanWrite" -> true;
                case "setBlock" -> {
                    BlockPos p=(BlockPos)args[0];
                    check((p.getX()>>4)==owner[0].x && (p.getZ()>>4)==owner[0].z,"actual chunk ownership");
                    check(bridge.geometry().bounds().contains(p.getX(),p.getZ()),"actual finite ownership");
                    states.put(p.immutable(),(BlockState)args[1]);yield true;
                }
                default -> throw new UnsupportedOperationException(m.getName());
            });
        List<ChunkPos> chunks=new ArrayList<>();var bounds=bridge.geometry().bounds();
        for(int x=bounds.minX()>>4;x<=(bounds.maxXExclusive()-1)>>4;x++)
            for(int z=bounds.minZ()>>4;z<=(bounds.maxZExclusive()-1)>>4;z++)chunks.add(new ChunkPos(x,z));
        if(reverse)Collections.reverse(chunks);
        for(var chunk:chunks) { owner[0]=chunk;bridge.render(level,new ChunkOwnedHighwayWriter(level,chunk)); }
        return states;
    }
    private static void check(boolean ok,String message) { checks++;if(!ok)throw new AssertionError(message); }
}
