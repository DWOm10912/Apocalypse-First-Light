package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import java.util.*;
import java.util.function.DoubleFunction;

/** Real geometry/corridor consumers, controlled engineering fixtures. Does not bootstrap a world. */
public final class HighwayViaductTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        HighwayPhase1Snapshot.main(args);
        axis();
        var diagonal = geometry(15,15, 271,271);
        var out = geometry(15,15, 143,15, 271,143);
        var in = geometry(15,15, 143,143, 271,143);
        var both = geometry(15,15, 143,15, 271,143, 399,143);
        for (var geometry : List.of(diagonal,out,in,both)) {
            exercise(geometry, s -> HighwayTerrainMode.VIADUCT);
            exercise(geometry, s -> s >= 80 && s <= 208 ? HighwayTerrainMode.VIADUCT : HighwayTerrainMode.CUT);
            var plan = HighwayPlan.ribbon(geometry,0,geometry.length());
            var tunnel = HighwayCorridor.buildNatural(null,plan,profile(plan,s -> HighwayTerrainMode.TUNNEL));
            check(tunnel.geometryDeferred() && tunnel.cells().isEmpty() && tunnel.rowEnvelope().isEmpty(),"tunnel still deferred");
            piers(plan);
        }
        junction();
        replay(2,6544,-916);
        replay(3,-879,3376);
        System.out.println("HighwayViaductTest PASS assertions="+assertions+"; geometry/engineering fixtures only, no live terrain replay");
    }

    private static HighwayGeometry geometry(double... p) {
        List<HighwayGeometry.Point> points = new ArrayList<>();
        for(int i=0;i<p.length;i+=2) points.add(new HighwayGeometry.Point(p[i],p[i+1]));
        return new HighwayGeometry(points);
    }

    private static void axis() throws Exception {
        var plan = HighwayPlan.linear(new HighwayPlan.Point(0,0),new HighwayPlan.Point(128,0),23,0);
        var corridor = HighwayCorridor.buildNatural(null,plan,profile(plan,s -> HighwayTerrainMode.VIADUCT));
        check(corridor.cells().size()==129*23,"axis unchanged 23-wide footprint");
        var reference=profile(plan,s -> HighwayTerrainMode.VIADUCT);
        check(corridor.cells().stream().allMatch(c -> c.structuralBridge() && c.roadY()==reference.sampleAt(c.distance()).roadY()),"axis engineering Y");
        check(corridor.bridgeCells().stream().filter(c->c.role()==HighwayCorridor.Role.BRIDGE_EDGE).count()==129*2,"axis two rim columns");
        check(HighwayPierGeometry.SPACING==32,"existing pier spacing");
    }

    private static void exercise(HighwayGeometry geometry, DoubleFunction<HighwayTerrainMode> modes) throws Exception {
        var plan = HighwayPlan.ribbon(geometry,0,geometry.length());
        var profile = profile(plan,modes);
        var global = HighwayCorridor.buildNatural(null,plan,profile);
        check(!global.geometryDeferred(),"viaduct supported");
        check(global.cells().size()==geometry.raster(geometry.bounds(11.5),11.5).size(),"same surface footprint actual="+global.cells().size()+" expected="+geometry.raster(geometry.bounds(11.5),11.5).size());
        Set<String> expected = new HashSet<>(), owned = new HashSet<>();
        for(var c:global.cells()) {
            check(Math.abs(geometry.query(c.x(),c.z(),11.5).lateral())<=11.5+1e-8,"nominal perpendicular width");
            check(c.roadY()==profile.sampleAt(c.distance()).roadY(),"station deck Y");
            if(c.mode()==HighwayTerrainMode.VIADUCT) check(c.structuralBridge(),"concrete structure not ground fill");
            expected.add(key(c));
        }
        var box=geometry.bounds(14.5);
        // Independently rebuild each chunk's selected CORE+HALO profile (not a partition of one output).
        for(int cx=Math.floorDiv(box.minX(),16);cx<=Math.floorDiv(box.maxXExclusive()-1,16);cx++)
            for(int cz=Math.floorDiv(box.minZ(),16);cz<=Math.floorDiv(box.maxZExclusive()-1,16);cz++) {
                var bounds=new BoundsXZ(cx*16,cz*16,cx*16+16,cz*16+16);
                if(!geometry.intersects(bounds,14.5)) continue;
                long index=CorridorEngineeringSegment.segmentIndex((long)geometry.nearestStation(cx*16,cz*16));
                double from=Math.max(0,index*256-192),to=Math.min(geometry.length(),index*256+255+192);
                var localPlan=HighwayPlan.ribbon(geometry,from,to);
                var local=HighwayCorridor.buildNatural(null,localPlan,profile(localPlan,modes));
                for(var c:local.cells()) if(bounds.contains(c.x(),c.z())) owned.add(key(c));
            }
        check(owned.equals(expected),"chunk-owned union equals global deck incl transition/mixed boundary");
        Set<String> structural=new HashSet<>();
        for(var c:global.cells()) if(c.structuralBridge()) structural.add(c.x()+":"+c.z());
        check(global.cutColumns().stream().noneMatch(c->structural.contains(c.x()+":"+c.z())),"cut excludes bridge columns");
        check(global.bridgeCells().stream().anyMatch(c->c.role()==HighwayCorridor.Role.BRIDGE_EDGE),"parapet rim exists");
        check(!global.roadMarkings().isEmpty() && !global.centerline().isEmpty(),"markings and median retained");
    }

    private static void piers(HighwayPlan plan) {
        for(int s=32;s<plan.length()-8;s+=HighwayPierGeometry.SPACING) {
            var pose=HighwayPierGeometry.at(plan,s);
            var window=HighwayPlan.ribbon(plan.geometry(),Math.max(0,s-24),Math.min(plan.length(),s+24));
            check(pose.equals(HighwayPierGeometry.at(window,s)),"pier pose independent of window");
            var cap=HighwayPierGeometry.rectangle(pose.x(),pose.z(),pose.tangent(),-9,9,-1,1);
            check(!cap.isEmpty() && new HashSet<>(cap).size()==cap.size(),"pier finite unique pixels");
            Set<HighwayPierGeometry.Column> union=new HashSet<>();
            for(int cx=Math.floorDiv(pose.x()-12,16);cx<=Math.floorDiv(pose.x()+12,16);cx++)
                for(int cz=Math.floorDiv(pose.z()-12,16);cz<=Math.floorDiv(pose.z()+12,16);cz++) {
                    var b=new BoundsXZ(cx*16,cz*16,cx*16+16,cz*16+16);
                    for(var c:HighwayPierGeometry.rectangle(pose.x(),pose.z(),pose.tangent(),-9,9,-1,1))
                        if(b.contains(c.x(),c.z())) union.add(c);
                }
            check(union.equals(new HashSet<>(cap)),"pier at seam owned union");
        }
    }

    private static void junction() throws Exception {
        var graph=diagonalFixture(2);
        var edge=graph.getEdgeById("strategic_branch/viaduct_fixture_2/main").orElseThrow();
        var parent=graph.getEdgeById(edge.parentAttachment().orElseThrow().parentEdgeId()).orElseThrow();
        double station=edge.parentAttachment().orElseThrow().parentStation();
        var plan=parent.orientation()==HighwayRouteGraph.Orientation.NORTH_SOUTH
                ? HighwayPlan.linear(new HighwayPlan.Point(parent.fixedCoordinate(),station-192),new HighwayPlan.Point(parent.fixedCoordinate(),station+447),23,station-192)
                : HighwayPlan.linear(new HighwayPlan.Point(station-192,parent.fixedCoordinate()),new HighwayPlan.Point(station+447,parent.fixedCoordinate()),23,station-192);
        var parentProfile=profile(plan,s -> HighwayTerrainMode.VIADUCT);
        var grade=new HighwayBranchGrade(parent,parentProfile);
        var p=edge.geometry().point(0);
        check(grade.adjust(0,p.x(),p.z(),40)==grade.parentY(p.x(),p.z()),"junction exact parent deck");
        check(grade.parentViaduct(0,p.x(),p.z()),"parent structural inheritance");
        check(grade.adjust(256,p.x(),p.z(),40)==40,"blend ends at own grade");
        var branchPlan=HighwayPlan.ribbon(edge.geometry(),0,Math.min(256,edge.geometry().length()));
        var base=profile(branchPlan,s -> HighwayTerrainMode.VIADUCT);
        var ctor=HighwayProfile.class.getDeclaredConstructor(HighwayPlan.class,HighwayBridgeSpanResolver.Resolution.class,
                int.class,double.class,boolean.class,HighwayNodeConstraints.class,HighwayBranchGrade.class,Map.class);
        ctor.setAccessible(true);
        var attached=(HighwayProfile)ctor.newInstance(branchPlan,HighwayBridgeSpanResolver.resolve(base.samples()),
                0,0,false,HighwayNodeConstraints.NONE,grade,Map.of());
        var attachedCorridor=HighwayCorridor.buildNatural(null,branchPlan,attached);
        for(var cell:attachedCorridor.cells()) if(cell.distance()<=HighwayBranchGrade.HOLD_LENGTH)
            check(cell.roadY()==grade.parentY(cell.x(),cell.z()),"junction entire footprint inherits parent slope");
        int last=grade.adjust(0,p.x(),p.z(),40);
        for(int s=1;s<=256;s++) {
            int next=grade.adjust(s,p.x(),p.z(),40);
            check(Math.abs(next-last)<=1,"continuous grade no multi-block jumps");last=next;
        }
    }

    private static void replay(int island,int x,int z) throws Exception {
        var graph=diagonalFixture(island);
        var edge=graph.getEdgeById("strategic_branch/viaduct_fixture_"+island+"/main").orElseThrow();
        var bounds=new BoundsXZ(Math.floorDiv(x,16)*16,Math.floorDiv(z,16)*16,Math.floorDiv(x,16)*16+16,Math.floorDiv(z,16)*16+16);
        check(graph.query(bounds,20).contains(edge),"real seed branch queried");
        long index=CorridorEngineeringSegment.segmentIndex(edge.clampStation(edge.globalStation(bounds.minX(),bounds.minZ())));
        var plan=HighwayPlan.ribbon(edge.geometry(),Math.max(0,index*256-192),Math.min(edge.geometry().length(),index*256+447));
        // Controlled mixed profile probes the former BUILD_RIBBON failure; never claims live noise heights.
        var corridor=HighwayCorridor.buildNatural(null,plan,profile(plan,s->s<64?HighwayTerrainMode.VIADUCT:HighwayTerrainMode.GROUND));
        int owned=(int)corridor.cells().stream().filter(c->bounds.contains(c.x(),c.z())).count();
        var drop=HighwayLiveGenerationDiagnostic.classify(true,true,corridor.geometryDeferred(),corridor.cells().size(),owned,owned);
        check(!corridor.geometryDeferred() && owned>0 && drop==HighwayLiveGenerationDiagnostic.DropPoint.NONE,"real graph + mixed fixture replay");
        System.out.println("seed=-645704099691625981 historical_diagonal_fixture="+island+" CONTROLLED_PROFILE_REPLAY geometryDeferred="+corridor.geometryDeferred()
                +" cells="+corridor.cells().size()+" ownedCells="+owned+" FIRST_DROP_POINT="+drop);
    }

    private static HighwayRouteGraph diagonalFixture(int island) {
        var base=HighwayRouteGraph.buildTrunks(-645704099691625981L);
        var parent=base.edge(island==2?HighwayRouteGraph.Orientation.EAST_WEST:HighwayRouteGraph.Orientation.NORTH_SOUTH);
        int x=island==2?6544:parent.fixedCoordinate(),z=island==2?parent.fixedCoordinate():3376;
        return base.withStrategicBranch("viaduct_fixture_"+island,parent.routeId(),parent.id(),island==2?x:z,
                geometry(x,z,x+512,z+(island==2?-512:512)),"retained experimental diagonal regression");
    }

    private static int height(double station) { return 80+(int)Math.floor(station/128); }
    private static HighwayProfile profile(HighwayPlan plan,DoubleFunction<HighwayTerrainMode> modes) throws Exception {
        List<HighwayProfile.Sample> samples=new ArrayList<>();
        int count=Math.max(2,(int)Math.ceil(plan.length()/8)+1);
        for(int i=0;i<count;i++) {
            double s=Math.min(plan.length(),i*8), global=plan.globalStation(s);
            var p=plan.sample(s);var t=plan.tangent(s);var mode=modes.apply(global);int y=height(global);
            samples.add(new HighwayProfile.Sample(s,p.x(),p.z(),t.x(),t.z(),60,y,mode,mode,false,
                    60,60,60,60,0,23,0,0,0,20,60,60,false,false));
        }
        var ctor=HighwayProfile.class.getDeclaredConstructor(HighwayPlan.class,HighwayBridgeSpanResolver.Resolution.class,
                int.class,double.class,boolean.class,HighwayNodeConstraints.class);
        ctor.setAccessible(true);
        return ctor.newInstance(plan,HighwayBridgeSpanResolver.resolve(samples),0,0,false,HighwayNodeConstraints.NONE);
    }
    private static String key(HighwayCorridor.Cell c) { return c.x()+":"+c.z()+":"+c.roadY()+":"+c.structuralBridge(); }
    private static void check(boolean b,String message) { assertions++;if(!b)throw new AssertionError(message); }
}
