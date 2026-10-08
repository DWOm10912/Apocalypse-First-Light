package com.antaurora.apofirstlight.dev.highwaymesh;

import com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph;
import java.util.*;

/** Read-only adapter. Never connects separate edges across reserved turns/ramps or sea gaps. */
public final class RouteGraphMeshAdapter {
    public static RouteRoadGeometry.Plan select(HighwayRouteGraph graph,String edgeId,double start,double length,RoadSection from,RoadSection to) {
        if(graph.getNationalTrunks().size()!=2)throw new IllegalArgumentException("EXPECTED_TWO_NATIONAL_TRUNKS");
        var edge=graph.getEdgeById(edgeId).orElseThrow(()->new IllegalArgumentException("UNKNOWN_EDGE: use list"));
        List<RoadAlignment.Point> points;
        if(edge.geometry()!=null)points=edge.geometry().points().stream().map(p->new RoadAlignment.Point(p.x()+.5,p.z()+.5)).toList();
        else if(edge.orientation()==HighwayRouteGraph.Orientation.EAST_WEST)
            points=List.of(new RoadAlignment.Point(edge.startStation()+.5,edge.fixedCoordinate()+.5),new RoadAlignment.Point(edge.endStation()+.5,edge.fixedCoordinate()+.5));
        else points=List.of(new RoadAlignment.Point(edge.fixedCoordinate()+.5,edge.startStation()+.5),new RoadAlignment.Point(edge.fixedCoordinate()+.5,edge.endStation()+.5));
        List<RoadAlignment.Corridor> domains=new ArrayList<>();
        for(int i=1;i<points.size();i++)domains.add(new RoadAlignment.Corridor(points.get(i-1),points.get(i),HighwayRouteGraph.CONSTRUCTION_HALF_WIDTH));
        List<String> notes=new ArrayList<>(List.of("PREVIEW_ONLY_NO_WRITES_NO_COLLISION","PROTECTION_UNKNOWN_CONSTRUCTION_FORBIDDEN",
            "DESIGN_VERTICAL_PROFILE_NOT_LEGACY_OR_TERRAIN_HEIGHT","ENGINEERING_SURFACE_VIADUCT_TUNNEL_UNKNOWN",
            "SOURCE_NODE_KINDS="+edge.startNode().kind()+","+edge.endNode().kind(),"NO_CONNECTION_ACROSS_EDGE_RESERVED_GAPS"));
        edge.parentAttachment().ifPresent(p->notes.add("PARENT="+p));
        graph.seaCrossings().stream().filter(c->c.routeId().equals(edge.routeId())).forEach(c->notes.add("ROUTE_SEA_CROSSING="+c.id()+" (not rendered)"));
        notes.add("GRAPH_RESERVED_ZONES="+graph.reservedZones().size()+"; JUNCTION="+edge.junctionNodeId());
        // Geometry uses source block centres exactly once. Arc station remains separate from authority station.
        var plan=new RouteRoadGeometry.Plan(1,graph.seed(),HighwayRouteGraph.VERSION,edge.routeId(),edge.id(),edge.startNode().id(),edge.endNode().id(),
            edge.startStation(),points,domains,start,length,from,to,256,256,1024,.03,notes);
        var geometry=new RouteRoadGeometry(plan);
        if(geometry.alignment.maxCurvature>0)throw new IllegalArgumentException("RESTRICTED: actual curved edge requires verified protection evidence; no planning provider installed");
        double end=plan.start()+plan.length(),margin=Math.max(from.protection(),to.protection())/2;
        if(start<margin||end>geometry.alignment.length-margin)throw new IllegalArgumentException("RESTRICTED: sample must leave protection margin at finite edge endpoints");
        if(edge.geometry()!=null&&(edge.startStation()!=0||edge.endStation()+1<edge.geometry().length()))
            throw new IllegalArgumentException("RESTRICTED: partial polyline authority station mapping requires explicit remap");
        return plan;
    }
    private RouteGraphMeshAdapter(){}
}