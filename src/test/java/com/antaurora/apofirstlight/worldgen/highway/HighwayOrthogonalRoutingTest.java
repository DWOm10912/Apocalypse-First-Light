package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.dev.HighwayNetworkExport;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import java.util.*;
import static com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph.*;

/** Planning/claim/consumer contracts. No renderer, screenshot, world, or chunk creation. */
public final class HighwayOrthogonalRoutingTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var a=new HighwayGeometry.Point(0,0);
        check(OrthogonalHighwayPath.candidates(a,new HighwayGeometry.Point(512,0),1,0).stream().anyMatch(p->p.size()==2),"straight");
        check(OrthogonalHighwayPath.candidates(a,new HighwayGeometry.Point(512,512),1,0).stream().anyMatch(p->p.size()==3),"L");
        check(OrthogonalHighwayPath.candidates(a,new HighwayGeometry.Point(512,512),1,0).stream().anyMatch(p->p.size()==4),"dogleg");
        for(int sign:new int[]{-1,1})for(var path:OrthogonalHighwayPath.candidates(a,new HighwayGeometry.Point(sign*512,512),sign,0)) {
            var chain=new OrthogonalHighwayPath(path);
            var boxes=chain.edgeBounds(true,32);
            for(var box:boxes)for(var zone:chain.reservations(true))check(!box.intersects(zone),"axis writes cannot enter reservation (incl negative direction)");
            for(int i=2;i<path.size()-1;i++)check(OrthogonalHighwayPath.distance(path.get(i-1),path.get(i))>=128,"turn spacing");
            var last=boxes.get(boxes.size()-1);
            check(Math.max(last.width(),last.depth())>=65,"minimum physical bridge approach");
        }
        var macro=MacroGeography.forSeed(42);var base=buildTrunks(42);var parent=base.edge(Orientation.EAST_WEST);
        int s=3000,z=parent.fixedCoordinate();
        var p=List.of(new HighwayGeometry.Point(s,z),new HighwayGeometry.Point(s,z+512),
                new HighwayGeometry.Point(s+512,z+512),new HighwayGeometry.Point(s+512,z+1024));
        var island=new OrthogonalHighwayPath(List.of(new HighwayGeometry.Point(s+512,z+1624),new HighwayGeometry.Point(s+512,z+1880)));
        var route="strategic_branch/orthogonal_fixture";
        var attachment=new ParentAttachment(parent.routeId(),parent.id(),s,parent.id()+"/junction/"+s);
        var connection=new SatelliteHighwayRouting.Connection("sea_crossing/orthogonal_fixture",1,macro.crossingCandidates().get(0),route,attachment,
                new Node(route+"/mainland_bridgehead",NodeKind.MAINLAND_BRIDGEHEAD,s+512,z+1024),
                new Node(route+"/satellite_bridgehead",NodeKind.SATELLITE_BRIDGEHEAD,s+512,z+1624),0,1,600,
                new OrthogonalHighwayPath(p),island);
        var graph=publish(base,new SatelliteHighwayRouting.Result(List.of(connection),List.of()));
        check(graph.getNationalTrunks().equals(base.getNationalTrunks()),"trunks exact");
        check(graph.turns().size()==2,"explicit TURN count");
        check(graph.reservedZones().size()==3,"TURN + junction ramp zones");
        for(var turn:graph.turns()) {
            check(!turn.incomingDirection().equals(turn.outgoingDirection()),"direction change explicit");
            check(graph.getEdgeById(turn.incomingEdge()).isPresent()&&graph.getEdgeById(turn.outgoingEdge()).isPresent(),"edge references");
            check(HighwayLiveGenerationDiagnostic.reservationAt(graph,turn.node().x(),turn.node().z()).startsWith("PLANNED TURN / NO ROAD MODULE YET"),"diagnose reserved turn");
        }
        var dimFactory=net.minecraft.resources.ResourceKey.class.getDeclaredMethod("create",net.minecraft.resources.ResourceLocation.class,net.minecraft.resources.ResourceLocation.class);
        dimFactory.setAccessible(true);
        @SuppressWarnings("unchecked") var overworld=(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>)dimFactory.invoke(null,
                new net.minecraft.resources.ResourceLocation("minecraft","dimension"),new net.minecraft.resources.ResourceLocation("minecraft","overworld"));
        for(var zone:graph.reservedZones()) {
            check(zone.bounds().width()==65 && zone.bounds().depth()==65,"64 nominal centered zone inclusive blocks");
            check(HighwaySpatialClaimProvider.query(graph,overworld,zone.bounds()).stream().anyMatch(c->c.boundsXZ().equals(zone.bounds())),"reserved zone hard claim");
            for(var edge:graph.getStrategicBranches().get(0).edges())check(!edge.bounds(32).intersects(zone.bounds()),"full construction envelope clear");
        }
        check(SatelliteHighwayRouting.conflicts(connection.mainlandGeometry(),island,parent,base,List.of(connection)),"occupied route rejected");
        check(!SatelliteHighwayRouting.conflicts(connection.mainlandGeometry(),island,parent,base,List.of()),"unoccupied route viable");
        String export=HighwayNetworkExport.report(graph,macro);
        check(export.contains("nodeType = TURN")&&export.contains("reservedZone =")&&export.contains("incomingEdge="),"export TURN authority");
        check(!export.contains("geometryType = POLYLINE"),"no exported long diagonal");
        for(var edge:graph.getStrategicBranches().get(0).edges()) {
            check(edge.geometry()==null,"axis consumer selection");
            var start=edge.orientation()==Orientation.NORTH_SOUTH?new HighwayPlan.Point(edge.fixedCoordinate(),edge.startStation()):new HighwayPlan.Point(edge.startStation(),edge.fixedCoordinate());
            var end=edge.orientation()==Orientation.NORTH_SOUTH?new HighwayPlan.Point(edge.fixedCoordinate(),edge.endStation()):new HighwayPlan.Point(edge.endStation(),edge.fixedCoordinate());
            var plan=HighwayPlan.linear(start,end,23,edge.startStation());
            for(var mode:List.of(HighwayTerrainMode.VIADUCT,HighwayTerrainMode.TUNNEL,HighwayTerrainMode.GROUND)) {
                var samples=new ArrayList<HighwayProfile.Sample>();
                for(double distance:new double[]{0,plan.length()}) {
                    var pos=plan.sample(distance);var t=plan.tangent(distance);
                    samples.add(new HighwayProfile.Sample(distance,pos.x(),pos.z(),t.x(),t.z(),60,80,mode,mode,false,60,60,60,60,0,23,0,0,0,20,60,60,false,false));
                }
                var ctor=HighwayProfile.class.getDeclaredConstructor(HighwayPlan.class,HighwayBridgeSpanResolver.Resolution.class,int.class,double.class,boolean.class,HighwayNodeConstraints.class);
                ctor.setAccessible(true);
                var profile=(HighwayProfile)ctor.newInstance(plan,HighwayBridgeSpanResolver.resolve(samples),0,0,false,HighwayNodeConstraints.NONE);
                var corridor=HighwayCorridor.buildNatural(null,plan,profile,edge.bounds(32));
                check(!corridor.geometryDeferred()&&!corridor.cells().isEmpty(),"Axis surface/viaduct/tunnel not geometry-deferred");
            }
        }
        System.out.println("HighwayOrthogonalRoutingTest PASS: "+checks+" checks; no world");
    }
    private static void check(boolean ok,String why) {checks++;if(!ok)throw new AssertionError(why);}
}
