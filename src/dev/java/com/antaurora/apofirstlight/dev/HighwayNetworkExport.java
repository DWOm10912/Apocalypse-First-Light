package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.LandmassRole;
import com.antaurora.apofirstlight.worldgen.highway.HighwayGeometry;
import com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph;
import com.antaurora.apofirstlight.worldgen.highway.SatelliteHighwayRouting;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure overlay and text serialization of the already planned graph. */
public final class HighwayNetworkExport {
    private static final Color TRUNK = new Color(249, 248, 244);
    private static final Color BRANCH = new Color(248, 126, 65);
    private static final Color RESERVED = new Color(77, 238, 248);
    private static final Color NODE = new Color(255, 52, 162);
    private HighwayNetworkExport() {}

    public static void overlay(BufferedImage image, HighwayRouteGraph graph, MacroGeography macro,
                               int radius, int step) {
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (HighwayRouteGraph.Edge edge : sortedEdges(graph)) {
                g.setColor(edge.routeType() == HighwayRouteGraph.RouteType.NATIONAL_TRUNK ? TRUNK : BRANCH);
                g.setStroke(new BasicStroke(edge.routeType() == HighwayRouteGraph.RouteType.NATIONAL_TRUNK ? 3f : 2f));
                List<HighwayGeometry.Point> points = points(edge);
                for (int i = 1; i < points.size(); i++) {
                    HighwayGeometry.Point a = points.get(i - 1), b = points.get(i);
                    g.drawLine(pixel(a.x(), radius, step), pixel(a.z(), radius, step),
                            pixel(b.x(), radius, step), pixel(b.z(), radius, step));
                }
            }
            g.setColor(RESERVED);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                    10f, new float[]{6f, 5f}, 0f));
            for (SatelliteHighwayRouting.Connection crossing : sortedCrossings(graph)) {
                g.drawLine(pixel(crossing.mainland().x(), radius, step), pixel(crossing.mainland().z(), radius, step),
                        pixel(crossing.satellite().x(), radius, step), pixel(crossing.satellite().z(), radius, step));
            }
            g.setStroke(new BasicStroke(1f));
            for(var zone:graph.reservedZones()) {
                var b=zone.bounds();g.setColor(NODE);
                int x=pixel(b.minX(),radius,step),z=pixel(b.minZ(),radius,step);
                g.drawRect(x,z,Math.max(2,pixel(b.maxXExclusive(),radius,step)-x),Math.max(2,pixel(b.maxZExclusive(),radius,step)-z));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            for (HighwayRouteGraph.Node node : sortedNodes(graph)) {
                int x = pixel(node.x(), radius, step), z = pixel(node.z(), radius, step);
                g.setColor(NODE);
                g.fillOval(x - 3, z - 3, 7, 7);
                g.setColor(Color.WHITE);
                g.drawString(label(node.kind()), x + 5, z - 5);
            }
            for (MacroGeography.Island island : macro.islands()) {
                if (island.role() != LandmassRole.SATELLITE_ISLAND || connected(graph, island.id())) continue;
                int x = pixel(island.x(), radius, step), z = pixel(island.z(), radius, step);
                g.setColor(new Color(255, 72, 72));
                g.drawLine(x - 5, z - 5, x + 5, z + 5);
                g.drawLine(x - 5, z + 5, x + 5, z - 5);
            }
            int x = Math.max(0, image.getWidth() - 246);
            g.setColor(new Color(0, 0, 0, 220)); g.fillRect(x, 4, 242, 124);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            String[] legend = {"HIGHWAY NETWORK V2", "National Trunk", "Strategic Branch",
                    "Highway Node / Bridgehead", "Planned Sea Crossing (no deck)", "Red X: unconnected satellite", "Square: TURN / ramp reserved"};
            Color[] colors = {Color.WHITE, TRUNK, BRANCH, NODE, RESERVED, new Color(255, 72, 72), NODE};
            for (int i = 0; i < legend.length; i++) {
                g.setColor(colors[i]); g.fillRect(x + 8, 12 + i * 16, 12, 3);
                g.setColor(Color.WHITE); g.drawString(legend[i], x + 28, 17 + i * 16);
            }
        } finally { g.dispose(); }
    }

    public static String report(HighwayRouteGraph graph, MacroGeography macro) {
        StringBuilder out = new StringBuilder("\nHIGHWAY NETWORK EXPORT V2\n");
        long satelliteCount = macro.islands().stream().filter(i -> i.role() == LandmassRole.SATELLITE_ISLAND).count();
        out.append("seed = ").append(graph.seed()).append("\n")
                .append("National Trunks = ").append(graph.getNationalTrunks().size()).append("\n")
                .append("Strategic Branches = ").append(graph.getStrategicBranches().size()).append("\n")
                .append("Highway Nodes = ").append(graph.nodes().size()).append("\n")
                .append("Satellite Connections = ").append(graph.seaCrossings().size()).append("\n")
                .append("Sea Crossing Reservations = ").append(graph.seaCrossings().size()).append("\n")
                .append("Connected Satellites = ").append(graph.seaCrossings().size()).append("\n")
                .append("Unconnected Satellites = ").append(satelliteCount - graph.seaCrossings().size()).append("\n")
                .append("Legend: trunk=white; branch=orange; node=magenta; planned sea crossing=cyan dashed; unconnected=red X\n")
                .append("Sea crossing lines are reservations only; no physical bridge is claimed.\n");
        out.append("\nROUTES\n");
        for (HighwayRouteGraph.Route route : graph.routes().stream().sorted(Comparator.comparing(HighwayRouteGraph.Route::routeId)).toList()) {
            out.append("routeId = ").append(route.routeId()).append("\nrouteType = ").append(route.routeType())
                    .append("\npurpose = ").append(route.purpose()).append("\nedgeIds = ")
                    .append(route.edges().stream().map(HighwayRouteGraph.Edge::id).sorted().toList()).append("\n")
                    .append("parentAttachment = ").append(route.parentAttachment().orElse(null)).append("\n\n");
        }
        out.append("EDGES\n");
        for (HighwayRouteGraph.Edge edge : sortedEdges(graph)) {
            List<HighwayGeometry.Point> line = points(edge);
            out.append("edgeId = ").append(edge.id()).append("\nrouteId = ").append(edge.routeId())
                    .append("\nstartNodeId = ").append(edge.startNode().id()).append("\nendNodeId = ").append(edge.endNode().id())
                    .append("\ngeometryType = ").append(edge.geometry() == null ? "AXIAL" : "POLYLINE")
                    .append("\nsegmentKinds = ").append(edge.geometry() == null ? "AXIAL_STRAIGHT"
                            : edge.geometry().kinds().stream().map(Enum::name).sorted().toList())
                    .append("\nstartXZ = ").append(edge.startNode().x()).append(", ").append(edge.startNode().z())
                    .append("\nendXZ = ").append(edge.endNode().x()).append(", ").append(edge.endNode().z())
                    .append("\nbounds32 = ").append(edge.bounds(32))
                    .append("\nstationRange = ").append(edge.startStation()).append(".. ").append(edge.endStation())
                    .append("\nlength = ").append(edge.geometry() == null ? edge.endStation() - edge.startStation() : edge.geometry().length())
                    .append("\ncontrolPointsXZ = ").append(line)
                    .append("\nparentAttachment = ").append(edge.parentAttachment().orElse(null)).append("\n\n");
        }
        Map<String, String> connectedEdges = new TreeMap<>();
        for (HighwayRouteGraph.Edge edge : graph.edges()) {
            connectedEdges.merge(edge.startNode().id(), edge.id(), (a, b) -> a + "," + b);
            connectedEdges.merge(edge.endNode().id(), edge.id(), (a, b) -> a + "," + b);
        }
        out.append("NODES\n");
        for (HighwayRouteGraph.Node node : sortedNodes(graph)) {
            out.append("nodeId = ").append(node.id()).append("\nnodeType = ").append(node.kind())
                    .append("\nx = ").append(node.x()).append("\nz = ").append(node.z())
                    .append("\nconnectedEdges = ").append(connectedEdges.getOrDefault(node.id(), "none"));
            for (SatelliteHighwayRouting.Connection c : sortedCrossings(graph))
                if (c.mainland().id().equals(node.id()) || c.satellite().id().equals(node.id())
                        || c.parent().junctionNodeId().equals(node.id()))
                    out.append(c.parent().junctionNodeId().equals(node.id())
                            ? "," + c.parent().parentEdgeId() : "")
                            .append("\nsatelliteIslandId = ").append(c.islandId())
                            .append("\nconnectionId = ").append(c.id())
                            .append("\nparentRouteId = ").append(c.parent().parentRouteId())
                            .append("\nparentStation = ").append(c.parent().parentStation());
            out.append("\n\n");
        }
        out.append("TURN NODES AND INFRASTRUCTURE RESERVED ZONES\n");
        for(var turn:graph.turns())out.append("turn = ").append(turn).append("\n");
        for(var zone:graph.reservedZones())out.append("reservedZone = ").append(zone).append("\nreservedType = ").append(zone.kind()).append("\n");
        out.append("SEA CROSSING RESERVATIONS (PLANNED ONLY)\n");
        for (SatelliteHighwayRouting.Connection c : sortedCrossings(graph))
            out.append("crossingId = ").append(c.id()).append("\nsatelliteIslandId = ").append(c.islandId())
                    .append("\nowningRouteId = ").append(c.routeId()).append("\nmainlandBridgehead = ")
                    .append(c.mainland().id()).append(" @ ").append(c.mainland().x()).append(", ").append(c.mainland().z())
                    .append("\nsatelliteBridgehead = ").append(c.satellite().id()).append(" @ ")
                    .append(c.satellite().x()).append(", ").append(c.satellite().z())
                    .append("\nactualBankSpan = ").append(c.span()).append("\nbridgeAxis = ").append(c.dx()).append(", ").append(c.dz())
                    .append("\nsourceWaterbodyId = ").append(c.source().waterbodyId())
                    .append("\nsourceCandidate = ").append(c.source()).append("\n\n");
        out.append("CONNECTED SATELLITES\n");
        for (SatelliteHighwayRouting.Connection c : sortedCrossings(graph))
            out.append("satelliteIslandId = ").append(c.islandId()).append("\nstatus = CONNECTED\nconnectionId = ")
                    .append(c.id()).append("\nrouteId = ").append(c.routeId())
                    .append("\nselectedCrossing = ").append(c.source().waterbodyId())
                    .append("\nsourceCandidate = ").append(c.source())
                    .append("\nmainlandDisplacement = ").append(c.mainlandDisplacement())
                    .append("\nsatelliteDisplacement = ").append(c.satelliteDisplacement())
                    .append("\ncombinedDisplacement = ").append(c.combinedDisplacement())
                    .append("\nactualBankSpan = ").append(c.span()).append("\nbridgeAxis = ").append(c.dx()).append(", ").append(c.dz())
                    .append("\nbridgeApproachEngineeringStatus = UNKNOWN")
                    .append("\nparentTrunk = ").append(c.parent().parentRouteId())
                    .append("\nselectedParentTrunk = ").append(c.parent().parentRouteId())
                    .append("\nmainlandRouteLength = ").append(c.mainlandRouteLength())
                    .append("\nturnCount = ").append(c.turnCount())
                    .append("\nextraDistance = ").append(c.extraDistance())
                    .append("\nnetworkCost = ").append(c.networkCost())
                    .append("\nbranchJunction = ").append(c.parent().junctionNodeId()).append(" @ station ")
                    .append(c.parent().parentStation())
                    .append("\nmainlandBridgeheadXZ = ").append(c.mainland().x()).append(", ").append(c.mainland().z())
                    .append("\nsatelliteBridgeheadXZ = ").append(c.satellite().x()).append(", ").append(c.satellite().z())
                    .append("\nmainlandBranchLength = ").append(c.mainlandGeometry().length())
                    .append("\nislandHighwayLength = ").append(c.islandGeometry().length())
                    .append("\nseaSpan = ").append(c.span()).append("\n\n");
        out.append("UNCONNECTED SATELLITES\n");
        for (MacroGeography.Island island : macro.islands().stream().filter(i -> i.role() == LandmassRole.SATELLITE_ISLAND)
                .sorted(Comparator.comparingInt(MacroGeography.Island::id)).toList()) {
            if (connected(graph, island.id())) continue;
            List<MacroGeography.CrossingCandidate> qualified = macro.crossingCandidates().stream()
                    .filter(c -> c.fromLandmassId() == 0 && c.toLandmassId() == island.id()
                            && c.waterSpan() >= 300 && c.waterSpan() <= 600).toList();
            String diagnostic = graph.routingDiagnostics().stream().filter(d -> d.startsWith("satellite=" + island.id() + ":"))
                    .findFirst().orElse("No per-island routing diagnostic available");
            String reason = qualified.isEmpty() ? "NO_QUALIFIED_CROSSING"
                    : diagnostic.contains("firstFailureReason=") ? diagnostic.split("firstFailureReason=",2)[1].split(" ",2)[0] : "ROUTING_STATUS_UNKNOWN";
            out.append("satelliteIslandId = ").append(island.id()).append("\nstatus = UNCONNECTED")
                    .append("\nfailureReason = ").append(reason).append("\nrouterDiagnostic = ")
                    .append(diagnostic).append("\n\n");
        }
        return out.toString();
    }

    private static List<HighwayRouteGraph.Edge> sortedEdges(HighwayRouteGraph graph) {
        return graph.edges().stream().sorted(Comparator.comparing(HighwayRouteGraph.Edge::id)).toList();
    }
    private static List<HighwayRouteGraph.Node> sortedNodes(HighwayRouteGraph graph) {
        return graph.nodes().stream().sorted(Comparator.comparing(HighwayRouteGraph.Node::id)).toList();
    }
    private static List<SatelliteHighwayRouting.Connection> sortedCrossings(HighwayRouteGraph graph) {
        return graph.seaCrossings().stream().sorted(Comparator.comparing(SatelliteHighwayRouting.Connection::id)).toList();
    }
    private static boolean connected(HighwayRouteGraph graph, int islandId) {
        return graph.seaCrossings().stream().anyMatch(c -> c.islandId() == islandId);
    }
    private static List<HighwayGeometry.Point> points(HighwayRouteGraph.Edge edge) {
        if (edge.geometry() != null) return edge.geometry().points();
        return edge.orientation()==HighwayRouteGraph.Orientation.NORTH_SOUTH
                ?List.of(new HighwayGeometry.Point(edge.fixedCoordinate(),edge.startStation()),new HighwayGeometry.Point(edge.fixedCoordinate(),edge.endStation()))
                :List.of(new HighwayGeometry.Point(edge.startStation(),edge.fixedCoordinate()),new HighwayGeometry.Point(edge.endStation(),edge.fixedCoordinate()));
    }
    private static int pixel(double coordinate, int radius, int step) {
        return (int) Math.round((coordinate + radius) / step);
    }
    private static String label(HighwayRouteGraph.NodeKind kind) {
        return switch (kind) {
            case INTERSECTION -> "INT";
            case BRANCH_JUNCTION -> "JCT";
            case MAINLAND_BRIDGEHEAD -> "MBH";
            case SATELLITE_BRIDGEHEAD -> "SBH";
            case TERMINUS -> "END";
            case TURN -> "TURN";
        };
    }
}
