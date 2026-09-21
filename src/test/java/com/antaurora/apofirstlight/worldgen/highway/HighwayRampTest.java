package com.antaurora.apofirstlight.worldgen.highway;

import java.util.*;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import static com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph.*;

/** Pure claim and local geometry preflight. No world/bootstrap/rendering. */
public final class HighwayRampTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        int zones=0, ports=0, slices=0;
        var factory=net.minecraft.resources.ResourceKey.class.getDeclaredMethod("create",net.minecraft.resources.ResourceLocation.class,net.minecraft.resources.ResourceLocation.class);
        factory.setAccessible(true);
        @SuppressWarnings("unchecked") var dimension=(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>) factory.invoke(null,
                new net.minecraft.resources.ResourceLocation("minecraft","dimension"),new net.minecraft.resources.ResourceLocation("minecraft","overworld"));
        for (long seed : new long[]{-4332662446239654818L,-645704099691625981L}) {
            var graph=build(seed);
            check(graph.getNationalTrunks().equals(buildTrunks(seed).getNationalTrunks()),"Phase 1 trunks unchanged");
            String before=graph.nodes().toString()+graph.edges()+graph.reservedZones()+graph.seaCrossings();
            for (var zone : graph.reservedZones()) {
                zones++;
                var seams=HighwayReservedSeams.forZone(graph,zone);
                for (var seam : seams) {
                    ports++; slices+=seam.length();
                    check(seam.length()>=1 && seam.length()<=7,"authorized length");
                    check(seam.bounds().width()*seam.bounds().depth()==65L*seam.length(),"existing construction width");
                    check(!seam.bounds().intersects(zone.bounds()),"core unchanged");
                    check(!seam.bounds().intersects(graph.getEdgeById(seam.edgeId()).orElseThrow().bounds(32)),"endpoint not duplicated");
                    var query=new BoundsXZ(seam.bounds().minX(),seam.bounds().minZ(),seam.bounds().minX()+1,seam.bounds().minZ()+1);
                    check(HighwaySpatialClaimProvider.query(graph,dimension,query).stream().anyMatch(c->c.boundsXZ().equals(seam.bounds())),"seam-only query returns full stable claim");
                }
                var module=HighwayRampGeometry.build(graph,zone);
                verify(module.ribbon());
                var flat=flatProfile(module.incoming().edge(),80);
                for(int delta:new int[]{-1,0,1}) {
                    var grade=new HighwayRampGrade(module,flat,80,80+delta);
                    check(grade.feasible(),"small positive/negative/same endpoint grade");
                    check(grade.at(0,module.incoming().x(),module.incoming().z())==80,"entry grade pinned");
                    check(grade.at(module.ribbon().length(),module.outgoing().x(),module.outgoing().z())==80+delta,"exit grade pinned");
                    for(var cell:module.ribbon().raster(module.ribbon().bounds(11.5),11.5))
                        if(module.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP && HighwayRampRenderer.parentLateral(module,cell.x(),cell.z())<=14)
                            check(grade.at(cell.sample().station(),cell.x(),cell.z())==80,"whole parent plane preserved");
                }
                check(!new HighwayRampGrade(module,flat,80,180).feasible(),"grade infeasible rejected");
                verifyDetails(module,flat);
                if(module.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP) {
                    long gore=module.ribbon().raster(module.ribbon().bounds(11.5),11.5).stream().filter(c->HighwayRampRenderer.gore(module,c.x(),c.z())).count();
                    check(gore>0,"wedge exists");
                    for(var c:module.ribbon().raster(module.ribbon().bounds(11.5),11.5))if(HighwayRampRenderer.gore(module,c.x(),c.z()))
                        check(HighwayRampRenderer.suppressFurniture(module,c.x(),c.z()),"gore suppresses competing dividers and median");
                }
                check(HighwayRampModules.forGraph(graph).query(zone.bounds()).stream().anyMatch(m->m.id().equals(module.id())),"spatial module query");
                for(var cell:module.ribbon().raster(module.ribbon().bounds(14.5),14.5))
                    check(module.owns(cell.x(),cell.z()),"all road/ROW cells in claims");
                check(zone.bounds().width()==65 && zone.bounds().depth()==65,"frozen core");
                System.out.println(seed+" "+module.id()+" length="+module.ribbon().length()+" cells="+module.ribbon().raster(module.ribbon().bounds(11.5),11.5).size());
            }
            check(before.equals(graph.nodes().toString()+graph.edges()+graph.reservedZones()+graph.seaCrossings()),"graph unchanged");
        }
        check(zones==9 && ports==14 && slices==83,"two-seed exact seam evidence");
        for(var orientation:List.of(Orientation.NORTH_SOUTH,Orientation.EAST_WEST))for(int sign:new int[]{-1,1}) {
            var graph=junctionFixture(orientation,sign);
            var m=HighwayRampGeometry.build(graph,graph.reservedZones().get(0));
            verify(m.ribbon());
            check(m.type()==HighwayRampGeometry.Type.BRANCH_JUNCTION_RAMP,"four directional junctions");
            check(new HighwayRampGrade(m,flatProfile(m.incoming().edge(),80),80,81).feasible(),"junction grade orientation equivalence");
        }
        // Every orientation, negative-coordinate translation, and endpoint grid remainder.
        for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1})for(boolean swap:new boolean[]{false,true})
            for(int a=33;a<=40;a++)for(int b=33;b<=40;b++) {
                List<HighwayGeometry.Point> points=new ArrayList<>();
                for(int[] p:new int[][]{{-a,0},{-16,0},{0,16},{0,b}})
                    points.add(new HighwayGeometry.Point(-17+(swap?p[1]*dz:p[0]*dx),-31+(swap?p[0]*dx:p[1]*dz)));
                verify(HighwayGeometry.localRamp(points));
            }
        System.out.println("HighwayRampTest PASS: "+checks+" checks; zones="+zones+" seams="+ports+" slices="+slices+"; no world");
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static void verifyDetails(HighwayRampGeometry.Module m,HighwayProfile parent)throws Exception {
        var plan=HighwayPlan.ribbon(m.ribbon(),0,m.ribbon().length());
        var grade=new HighwayRampGrade(m,parent,80,81);
        List<HighwayProfile.Sample> samples=new ArrayList<>();
        for(double s:new double[]{0,plan.length()}){var p=plan.sample(s);var t=plan.tangent(s);int y=grade.at(s,(int)p.x(),(int)p.z());
            samples.add(new HighwayProfile.Sample(s,p.x(),p.z(),t.x(),t.z(),60,y,HighwayTerrainMode.VIADUCT,HighwayTerrainMode.VIADUCT,false,60,60,60,60,0,23,0,0,0,y-60,60,60,false,false));}
        var resolution=new HighwayBridgeSpanResolver.Resolution(samples,List.of(new HighwayBridgeSpanResolver.Span(0,plan.length())),2,plan.length(),2,0,0);
        var ctor=HighwayProfile.class.getDeclaredConstructor(HighwayPlan.class,HighwayBridgeSpanResolver.Resolution.class,int.class,double.class,boolean.class,
                HighwayNodeConstraints.class,HighwayBranchGrade.class,Map.class,HighwayRampGrade.class);
        ctor.setAccessible(true);
        var profile=(HighwayProfile)ctor.newInstance(plan,resolution,0,0,false,HighwayNodeConstraints.NONE,null,Map.of(),grade);
        var corridor=HighwayCorridor.buildNatural(null,plan,profile);
        check(!corridor.geometryDeferred(),"real ramp ribbon corridor accepts local no-tunnel profile");
        check(corridor.cells().size()==m.ribbon().raster(m.ribbon().bounds(11.5),11.5).size(),"real corridor consumes full pavement");
        check(!profile.tunnelAllowed(0),"ramp tunnel disabled");
        Map<String,net.minecraft.core.BlockPos> features=new HashMap<>();
        for(var c:corridor.cells()) {
            features.put("road/"+c,new net.minecraft.core.BlockPos(c.x(),c.roadY(),c.z()));
            if(c.lateral()==0 && !HighwayRampRenderer.suppressFurniture(m,c.x(),c.z()))features.put("median/"+c,new net.minecraft.core.BlockPos(c.x(),c.roadY()+1,c.z()));
        }
        for(var p:corridor.roadMarkings())if(!HighwayRampRenderer.suppressFurniture(m,p.x(),p.z()))
            features.put("paint/"+p,new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()));
        for(long s=32;s<plan.length()-8;s+=32){var pose=HighwayPierGeometry.at(plan,s);
            for(var p:HighwayPierGeometry.rectangle(pose.x(),pose.z(),pose.tangent(),-9,9,-1,1)) {
                check(m.owns(p.x(),p.z()),"pier cap inside authorized claims");
                features.put("cap/"+s+"/"+p,new net.minecraft.core.BlockPos(p.x(),75,p.z()));
            }}
        Set<String> union=new HashSet<>();var b=m.ribbon().bounds(14.5);
        for(int cx=Math.floorDiv(b.minX(),16);cx<=Math.floorDiv(b.maxXExclusive()-1,16);cx++)
            for(int cz=Math.floorDiv(b.minZ(),16);cz<=Math.floorDiv(b.maxZExclusive()-1,16);cz++){
                var writer=new ChunkOwnedHighwayWriter(null,new net.minecraft.world.level.ChunkPos(cx,cz));
                for(var f:features.entrySet())if(writer.owns(f.getValue()))check(union.add(f.getKey()),"ChunkOwned details have one owner");
            }
        check(union.equals(features.keySet()),"ChunkOwned road/median/paint/pier union equals global details");
    }
    private static HighwayRouteGraph junctionFixture(Orientation orientation,int sign) {
        var base=buildTrunks(42);var parent=base.edge(orientation);
        int x=orientation==Orientation.NORTH_SOUTH?parent.fixedCoordinate():3000;
        int z=orientation==Orientation.NORTH_SOUTH?3000:parent.fixedCoordinate();
        int dx=orientation==Orientation.NORTH_SOUTH?sign:0,dz=orientation==Orientation.NORTH_SOUTH?0:sign;
        String route="strategic_branch/ramp_fixture";
        var a=new Node(route+"/mainland",NodeKind.MAINLAND_BRIDGEHEAD,x+dx*512,z+dz*512);
        var b=new Node(route+"/island",NodeKind.SATELLITE_BRIDGEHEAD,x+dx*1112,z+dz*1112);
        var source=com.antaurora.apofirstlight.worldgen.geography.MacroGeography.forSeed(42).crossingCandidates().get(0);
        var c=new SatelliteHighwayRouting.Connection("sea_crossing/ramp_fixture",1,source,route,
                new ParentAttachment(parent.routeId(),parent.id(),3000,parent.id()+"/junction/3000"),a,b,dx,dz,600,
                new OrthogonalHighwayPath(List.of(new HighwayGeometry.Point(x,z),new HighwayGeometry.Point(a.x(),a.z()))),
                new OrthogonalHighwayPath(List.of(new HighwayGeometry.Point(b.x(),b.z()),new HighwayGeometry.Point(b.x()+dx*256,b.z()+dz*256))));
        return publish(base,new SatelliteHighwayRouting.Result(List.of(c),List.of()));
    }
    private static HighwayProfile flatProfile(Edge edge,int y)throws Exception {
        var p=HighwayPlan.linear(new HighwayPlan.Point(edge.orientation()==Orientation.NORTH_SOUTH?edge.fixedCoordinate():edge.startStation(),
                edge.orientation()==Orientation.NORTH_SOUTH?edge.startStation():edge.fixedCoordinate()),
                new HighwayPlan.Point(edge.orientation()==Orientation.NORTH_SOUTH?edge.fixedCoordinate():edge.endStation(),
                edge.orientation()==Orientation.NORTH_SOUTH?edge.endStation():edge.fixedCoordinate()),23,edge.startStation());
        List<HighwayProfile.Sample> samples=new ArrayList<>();
        for(double s:new double[]{0,p.length()}){var a=p.sample(s);var t=p.tangent(s);
            samples.add(new HighwayProfile.Sample(s,a.x(),a.z(),t.x(),t.z(),y,y,HighwayTerrainMode.GROUND,HighwayTerrainMode.GROUND,false,y,y,y,y,0,23,0,0,0,0,y,y,false,false));}
        var ctor=HighwayProfile.class.getDeclaredConstructor(HighwayPlan.class,HighwayBridgeSpanResolver.Resolution.class,int.class,double.class,boolean.class,HighwayNodeConstraints.class);
        ctor.setAccessible(true);
        return ctor.newInstance(p,new HighwayBridgeSpanResolver.Resolution(samples,List.of(),0,0,0,0,0),0,0,false,HighwayNodeConstraints.NONE);
    }
    private static void verify(HighwayGeometry g) {
        var cells=g.raster(g.bounds(11.5),11.5);
        Set<Long> all=new HashSet<>(); for(var c:cells)all.add(key(c.x(),c.z()));
        Set<Long> seen=new HashSet<>(); ArrayDeque<Long> todo=new ArrayDeque<>();todo.add(all.iterator().next());
        while(!todo.isEmpty()){long k=todo.removeFirst();if(!seen.add(k))continue;int x=(int)(k>>32),z=(int)k;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){long n=key(x+d[0],z+d[1]);if(all.contains(n)&&!seen.contains(n))todo.add(n);}}
        check(seen.equals(all),"4-connected road");
        var exterior=g.bounds(11.5).expand(1);
        seen.clear();todo.add(key(exterior.minX(),exterior.minZ()));
        while(!todo.isEmpty()){long k=todo.removeFirst();if(!seen.add(k))continue;int x=(int)(k>>32),z=(int)k;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int nx=x+d[0],nz=z+d[1];long n=key(nx,nz);
                if(exterior.contains(nx,nz)&&!all.contains(n)&&!seen.contains(n))todo.add(n);}}
        check(seen.size()+all.size()==exterior.width()*exterior.depth(),"no enclosed road holes");
        Set<Long> median=new HashSet<>();for(var c:cells)if(g.inDetailBand(c.x(),c.z(),0))median.add(key(c.x(),c.z()));
        seen.clear();todo.add(median.iterator().next());
        while(!todo.isEmpty()){long k=todo.removeFirst();if(!seen.add(k))continue;int x=(int)(k>>32),z=(int)k;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){long n=key(x+d[0],z+d[1]);if(median.contains(n)&&!seen.contains(n))todo.add(n);}}
        check(seen.equals(median),"4-connected median");
        for(double station:new double[]{0,g.length()}){var p=g.point(station);var t=g.tangent(station);
            for(int l=-11;l<=11;l++)check(g.query((int)Math.round(p.x()-t.z()*l),(int)Math.round(p.z()+t.x()*l),11.5)!=null,"23-wide port");}
        // Chunk partition union is exact, including negative-coordinate corners.
        Set<Long> owned=new HashSet<>();var bounds=g.bounds(11.5);
        for(int cx=Math.floorDiv(bounds.minX(),16);cx<=Math.floorDiv(bounds.maxXExclusive()-1,16);cx++)
            for(int cz=Math.floorDiv(bounds.minZ(),16);cz<=Math.floorDiv(bounds.maxZExclusive()-1,16);cz++)
                for(var c:g.raster(new BoundsXZ(cx*16,cz*16,cx*16+16,cz*16+16),11.5))check(owned.add(key(c.x(),c.z())),"unique chunk owner");
        check(owned.equals(all),"chunk union equals global road");
    }
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
}
