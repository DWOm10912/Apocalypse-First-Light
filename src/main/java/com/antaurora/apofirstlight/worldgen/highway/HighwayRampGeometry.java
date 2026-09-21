package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import java.util.List;
import static com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph.*;

/** Pure finite module geometry, separate from immutable route authority. No world or writes. */
public final class HighwayRampGeometry {
    private HighwayRampGeometry() {}
    public enum Type { BRANCH_JUNCTION_RAMP, TURN_RAMP }
    public record Port(Edge edge, int x, int z, int station) {}
    public record Module(String id, Type type, ReservedZone zone, Port incoming, Port outgoing,
                         HighwayGeometry ribbon, List<HighwayReservedSeams.Seam> seams) {
        public Module { seams=List.copyOf(seams); }
        public BoundsXZ queryBounds() {
            var b=ribbon.bounds(14.5);var c=zone.bounds();
            int x0=Math.min(b.minX(),c.minX()),z0=Math.min(b.minZ(),c.minZ());
            int x1=Math.max(b.maxXExclusive(),c.maxXExclusive()),z1=Math.max(b.maxZExclusive(),c.maxZExclusive());
            for(var s:seams){x0=Math.min(x0,s.bounds().minX());z0=Math.min(z0,s.bounds().minZ());
                x1=Math.max(x1,s.bounds().maxXExclusive());z1=Math.max(z1,s.bounds().maxZExclusive());}
            return new BoundsXZ(x0,z0,x1,z1);
        }
        public boolean owns(int x, int z) {
            return zone.bounds().contains(x,z) || seams.stream().anyMatch(s -> s.bounds().contains(x,z))
                    || incoming.edge().bounds(CONSTRUCTION_HALF_WIDTH).contains(x,z)
                    || outgoing.edge().bounds(CONSTRUCTION_HALF_WIDTH).contains(x,z);
        }
        public String description() {
            var a=ribbon.tangent(0);var b=ribbon.tangent(ribbon.length());
            return "moduleId="+id+" moduleType="+type+" nodeId="+zone.node().id()
                    +" incomingDirection="+direction(a)+" outgoingDirection="+direction(b)
                    +" bounds="+zone.bounds()+" seamBounds="+seams.stream().map(HighwayReservedSeams.Seam::bounds).toList()
                    +" geometryStatus="+(type==Type.TURN_RAMP?"TURN_RAMP_V1":"JUNCTION_RAMP_V1")
                    +" gradeStatus=RUNTIME_CHECK_REQUIRED";
        }
        private static String direction(HighwayGeometry.Point p){return p.x()>0?"E":p.x()<0?"W":p.z()>0?"S":"N";}
    }

    public static Module build(HighwayRouteGraph graph, ReservedZone zone) {
        var incident = graph.edges().stream().filter(e -> e.routeId().equals(zone.routeId())
                && (e.startNode().equals(zone.node()) || e.endNode().equals(zone.node()))).toList();
        Edge out = incident.stream().filter(e -> e.startNode().equals(zone.node())).findFirst().orElseThrow();
        Port exit = endpoint(out, zone.node());
        Port entry;
        Type type;
        if (zone.node().kind() == NodeKind.TURN) {
            type = Type.TURN_RAMP;
            entry = endpoint(incident.stream().filter(e -> e.endNode().equals(zone.node())).findFirst().orElseThrow(), zone.node());
        } else {
            type = Type.BRANCH_JUNCTION_RAMP;
            var attachment = out.parentAttachment().orElseThrow();
            Edge parent = graph.getEdgeById(attachment.parentEdgeId()).orElseThrow();
            // Deterministic positive-parent-station fork; parent itself is never clipped or rotated.
            int station = attachment.parentStation() - 32;
            if (station < parent.startStation()) throw new IllegalArgumentException("Junction entry outside parent");
            entry = parent.orientation() == Orientation.NORTH_SOUTH
                    ? new Port(parent, parent.fixedCoordinate(), station, station)
                    : new Port(parent, station, parent.fixedCoordinate(), station);
        }
        int cx = zone.node().x(), cz = zone.node().z();
        int ix = Integer.signum(entry.x()-cx), iz = Integer.signum(entry.z()-cz);
        int ox = Integer.signum(exit.x()-cx), oz = Integer.signum(exit.z()-cz);
        if (ix*ox + iz*oz != 0) throw new IllegalArgumentException("Only perpendicular ramp ports supported");
        var geometry = HighwayGeometry.localRamp(List.of(new HighwayGeometry.Point(entry.x(),entry.z()),
                new HighwayGeometry.Point(cx+ix*16,cz+iz*16),
                new HighwayGeometry.Point(cx+ox*16,cz+oz*16),
                new HighwayGeometry.Point(exit.x(),exit.z())));
        Module module = new Module("ramp/" + (type == Type.TURN_RAMP ? "turn/" : "junction/") + zone.node().id(),
                type, zone, entry, exit, geometry, HighwayReservedSeams.forZone(graph,zone));
        // Pavement and ROW must fit without clipping. The larger claim envelope is not a road footprint.
        for (var cell : geometry.raster(geometry.bounds(14.5),14.5))
            if (!module.owns(cell.x(),cell.z())) throw new IllegalArgumentException("Ramp footprint exceeds authorized claims");
        return module;
    }

    static Port endpoint(Edge edge, Node node) {
        boolean ns = edge.orientation() == Orientation.NORTH_SOUTH;
        int axis = ns ? node.z() : node.x();
        int station = Math.abs((long)edge.startStation()-axis) < Math.abs((long)edge.endStation()-axis)
                ? edge.startStation() : edge.endStation();
        return new Port(edge, ns ? edge.fixedCoordinate() : station,
                ns ? station : edge.fixedCoordinate(), station);
    }
}
