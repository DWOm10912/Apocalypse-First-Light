package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.Objects;

import static com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.*;

/** Immutable route authority. Phase 2A adds opt-in branches; build(seed) remains Phase 1. */
public final class HighwayRouteGraph {
    public static final int VERSION = 1;
    public static final int FOOTPRINT_HALF_WIDTH = 20;
    public static final int CONSTRUCTION_HALF_WIDTH = 32;
    private static final int COAST_APPROACH = 32;
    private static final int STATION_GRID = 8;
    private static final Map<Long, HighwayRouteGraph> CACHE = new LinkedHashMap<>(16, .75f, true);

    private final long seed;
    private final Node intersection;
    private final List<Node> nodes;
    private final List<Edge> edges;
    private final List<Route> routes;

    private HighwayRouteGraph(long seed, Node intersection, List<Node> nodes, List<Edge> edges) {
        this.seed = seed;
        this.intersection = intersection;
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.routes = this.edges.stream().map(edge -> new Route(edge.routeId(), edge.routeType(),
                edge.purpose(), List.of(edge), edge.parentAttachment())).toList();
    }

    public static synchronized HighwayRouteGraph forSeed(long seed) {
        HighwayRouteGraph graph = CACHE.get(seed);
        if (graph == null) {
            graph = build(seed);
            CACHE.put(seed, graph);
            if (CACHE.size() > 16) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return graph;
    }

    /** Pure reconstruction: no chunks, blocks, biome queries or mutable planner state. */
    public static HighwayRouteGraph build(long seed) {
        MacroGeography geography = MacroGeography.forSeed(seed);
        long hash = mix(seed ^ 0x41464c5f48573231L);
        int x = (640 + (int) Math.floorMod(hash, 384L)) * ((hash & 1L) == 0 ? 1 : -1);
        int z = (640 + (int) Math.floorMod(mix(hash), 384L)) * ((hash & 2L) == 0 ? 1 : -1);
        if (!coreFits(geography, x, z)) {
            boolean found = false;
            for (int[] candidate : new int[][] {{768, 768}, {-768, 768}, {-768, -768}, {768, -768}}) {
                if (coreFits(geography, candidate[0], candidate[1])) {
                    x = candidate[0]; z = candidate[1]; found = true; break;
                }
            }
            if (!found) throw new IllegalStateException("No mainland Highway V2 core for seed " + seed);
        }
        Node junction = new Node("national_intersection", NodeKind.INTERSECTION, x, z);
        Edge a = trunk(geography, junction, RouteRole.NATIONAL_TRUNK_A, Orientation.EAST_WEST);
        Edge b = trunk(geography, junction, RouteRole.NATIONAL_TRUNK_B, Orientation.NORTH_SOUTH);
        return new HighwayRouteGraph(seed, junction,
                List.of(a.startNode(), a.endNode(), b.startNode(), b.endNode(), junction), List.of(a, b));
    }

    private static boolean coreFits(MacroGeography geography, int x, int z) {
        // Both infinite axis lines miss spawn, including their construction envelopes.
        int reserve = MacroGeography.STARTUP_MAINLAND_RESERVE + CONSTRUCTION_HALF_WIDTH;
        if (Math.abs(x) <= reserve || Math.abs(z) <= reserve || Math.hypot(x, z) > 1600) return false;
        for (int dx = -96; dx <= 96; dx += 32) {
            for (int dz = -96; dz <= 96; dz += 32) {
                MacroGeographySample sample = geography.sample(x + dx, z + dz);
                if (!stableMainland(sample) || sample.coastDistance() < 256) return false;
            }
        }
        return true;
    }

    private static Edge trunk(MacroGeography geography, Node junction, RouteRole role, Orientation orientation) {
        int fixed = orientation == Orientation.NORTH_SOUTH ? junction.x() : junction.z();
        int station = orientation == Orientation.NORTH_SOUTH ? junction.z() : junction.x();
        int start = terminus(geography, orientation, fixed, station, -1);
        int end = terminus(geography, orientation, fixed, station, 1);
        String routeId = role.name().toLowerCase(java.util.Locale.ROOT);
        Node from = point(routeId + "/start", orientation, fixed, start);
        Node to = point(routeId + "/end", orientation, fixed, end);
        return new Edge(routeId, routeId + "/main", role, from, to, orientation, fixed,
                start, end, junction.id());
    }

    private static int terminus(MacroGeography geography, Orientation orientation, int fixed,
                                int junction, int direction) {
        int last = junction;
        int limit = geography.outerOceanRadius();
        // Validate the complete 65-block reservation at every station, not sparse coast probes.
        // First macro coast (including an inland-sea shore) terminates Phase 1. Local water
        // inside macro LAND remains an engineering decision and can still become a viaduct.
        for (int station = junction; Math.abs(station) <= limit; station += direction) {
            boolean valid = true;
            for (int lateral = -CONSTRUCTION_HALF_WIDTH; lateral <= CONSTRUCTION_HALF_WIDTH; lateral++) {
                int x = orientation == Orientation.NORTH_SOUTH ? fixed + lateral : station;
                int z = orientation == Orientation.NORTH_SOUTH ? station : fixed + lateral;
                if (!stableMainland(geography.sample(x, z))) { valid = false; break; }
            }
            if (!valid) break;
            last = station;
        }
        int endpoint = last - direction * COAST_APPROACH;
        // Keep the existing global 8-block profile sampling phase, including clipped halos.
        return direction < 0 ? -Math.floorDiv(-endpoint, STATION_GRID) * STATION_GRID
                : Math.floorDiv(endpoint, STATION_GRID) * STATION_GRID;
    }

    private static boolean stableMainland(MacroGeographySample sample) {
        return sample.nationId() == NationId.MAIN_NATION && sample.landmassRole() == LandmassRole.MAINLAND
                && sample.surfaceClass() == SurfaceClass.LAND;
    }

    private static Node point(String id, Orientation orientation, int fixed, int station) {
        return new Node(id, NodeKind.TERMINUS, orientation == Orientation.NORTH_SOUTH ? fixed : station,
                orientation == Orientation.NORTH_SOUTH ? station : fixed);
    }

    public List<Edge> query(BoundsXZ area, int halfWidth) {
        if (area.isEmpty()) return List.of();
        return edges.stream().filter(edge -> edge.bounds(halfWidth).intersects(area)).toList();
    }

    public Edge edge(Orientation orientation) {
        return edges.stream().filter(edge -> edge.routeType() == RouteType.NATIONAL_TRUNK
                && edge.orientation() == orientation).findFirst().orElseThrow();
    }

    public List<Route> routes() { return routes; }
    public List<Route> getNationalTrunks() { return routesOfType(RouteType.NATIONAL_TRUNK); }
    public List<Route> getStrategicBranches() { return routesOfType(RouteType.STRATEGIC_BRANCH); }
    private List<Route> routesOfType(RouteType type) {
        return routes.stream().filter(route -> route.routeType() == type).toList();
    }
    public Optional<Route> getRouteById(String id) {
        return routes.stream().filter(route -> route.routeId().equals(id)).findFirst();
    }
    public Optional<Edge> getEdgeById(String id) {
        return edges.stream().filter(edge -> edge.id().equals(id)).findFirst();
    }

    /** Integer block projection onto finite national trunks. Stable edge ID breaks distance ties. */
    public AttachmentCandidate findParentCandidate(int x, int z) {
        return getNationalTrunks().stream().flatMap(route -> route.edges().stream())
                .map(edge -> {
                    int station = (int) edge.clampStation(edge.globalStation(x, z));
                    Node point = point("projection", edge.orientation(), edge.fixedCoordinate(), station);
                    return new AttachmentCandidate(edge.routeId(), edge.id(), station,
                            point.x(), point.z(), edge.distanceTo(x, z));
                }).min(Comparator.comparingDouble(AttachmentCandidate::distance)
                        .thenComparing(AttachmentCandidate::parentEdgeId)).orElseThrow();
    }

    /**
     * Build-time copy operation, never mutates the seed cache. Caller supplies a stable semantic key,
     * not an insertion index. V1 supports one perpendicular axis-aligned edge, not diagonal routing.
     * Junction is an exact interior attachment (integer station), not a proximity match or ramp.
     */
    public HighwayRouteGraph withStrategicBranch(String key, String parentRouteId, String parentEdgeId,
                                                 int station, int targetX, int targetZ, String purpose) {
        if (key == null || !key.matches("[a-z0-9][a-z0-9_-]*"))
            throw new IllegalArgumentException("Branch requires a stable lowercase semantic key");
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("Missing purpose");
        String routeId = "strategic_branch/" + key;
        if (getRouteById(routeId).isPresent()) throw new IllegalArgumentException("Duplicate route " + routeId);
        Edge parent = getEdgeById(parentEdgeId).orElseThrow(() -> new IllegalArgumentException("Unknown parent edge"));
        if (!parent.routeId().equals(parentRouteId)) throw new IllegalArgumentException("Parent route mismatch");
        if (station < parent.startStation() || station > parent.endStation())
            throw new IllegalArgumentException("Attachment outside parent edge");
        Node projected = point(parent.id() + "/junction/" + station,
                parent.orientation(), parent.fixedCoordinate(), station);
        Node junction = new Node(projected.id(), NodeKind.BRANCH_JUNCTION, projected.x(), projected.z());
        if (station == parent.startStation()) junction = parent.startNode();
        else if (station == parent.endStation()) junction = parent.endNode();
        else if (projected.x() == intersection.x() && projected.z() == intersection.z()) junction = intersection;
        if (Math.abs((long) targetX) > 29_999_000 || Math.abs((long) targetZ) > 29_999_000)
            throw new IllegalArgumentException("Target outside supported world coordinates");
        Orientation orientation = parent.orientation() == Orientation.EAST_WEST
                ? Orientation.NORTH_SOUTH : Orientation.EAST_WEST;
        int fixed = orientation == Orientation.NORTH_SOUTH ? junction.x() : junction.z();
        int start = orientation == Orientation.NORTH_SOUTH ? junction.z() : junction.x();
        int end = orientation == Orientation.NORTH_SOUTH ? targetZ : targetX;
        if ((orientation == Orientation.NORTH_SOUTH ? targetX : targetZ) != fixed || start == end)
            throw new IllegalArgumentException("V1 branch must be nonzero and perpendicular axis-aligned");
        Node target = new Node(routeId + "/end", NodeKind.TERMINUS, targetX, targetZ);
        ParentAttachment attachment = new ParentAttachment(parentRouteId, parentEdgeId, station, junction.id());
        Edge branch = new Edge(routeId, routeId + "/main", RouteRole.STRATEGIC_BRANCH,
                start < end ? junction : target, start < end ? target : junction, orientation, fixed,
                Math.min(start, end), Math.max(start, end), junction.id(), purpose, Optional.of(attachment));
        List<Edge> extendedEdges = new ArrayList<>(edges);
        extendedEdges.add(branch);
        extendedEdges.sort(Comparator.comparing(Edge::id));
        Map<String, Node> extendedNodes = new java.util.TreeMap<>();
        for (Node node : nodes) extendedNodes.put(node.id(), node);
        extendedNodes.put(junction.id(), junction);
        extendedNodes.put(target.id(), target);
        return new HighwayRouteGraph(seed, intersection, new ArrayList<>(extendedNodes.values()), extendedEdges);
    }

    public long seed() { return seed; }
    public Node intersection() { return intersection; }
    public List<Node> nodes() { return nodes; }
    public List<Edge> edges() { return edges; }

    static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public enum RouteType { NATIONAL_TRUNK, STRATEGIC_BRANCH, CONNECTOR }
    public enum RouteRole { NATIONAL_TRUNK_A, NATIONAL_TRUNK_B, STRATEGIC_BRANCH }
    public enum Orientation { EAST_WEST, NORTH_SOUTH }
    public enum NodeKind { TERMINUS, INTERSECTION, BRANCH_JUNCTION }
    public record Node(String id, NodeKind kind, int x, int z) {}
    public record ParentAttachment(String parentRouteId, String parentEdgeId, int parentStation,
                                   String junctionNodeId) {}
    public record AttachmentCandidate(String parentRouteId, String parentEdgeId, int parentStation,
                                      int x, int z, double distance) {}
    /** Phase 2A routes each have one finite edge; purpose never selects a renderer. */
    public record Route(String routeId, RouteType routeType, String purpose, List<Edge> edges,
                        Optional<ParentAttachment> parentAttachment) {
        public Route { edges = List.copyOf(edges); }
    }

    /** A finite route edge; junctionNodeId identifies the interior grade-separated crossing.
     * It does not imply a navigable turn or ramp. Stations are inclusive world-axis coordinates. */
    public record Edge(String routeId, String id, RouteRole role, Node startNode, Node endNode,
                       Orientation orientation, int fixedCoordinate, int startStation, int endStation,
                       String junctionNodeId, String purpose, Optional<ParentAttachment> parentAttachment) {
        public Edge(String routeId, String id, RouteRole role, Node startNode, Node endNode,
                    Orientation orientation, int fixedCoordinate, int startStation, int endStation,
                    String junctionNodeId) {
            this(routeId, id, role, startNode, endNode, orientation, fixedCoordinate,
                    startStation, endStation, junctionNodeId, "national", Optional.empty());
        }
        public Edge {
            if (startStation >= endStation) throw new IllegalArgumentException("Empty highway edge");
            Objects.requireNonNull(parentAttachment);
            if ((role == RouteRole.STRATEGIC_BRANCH) != parentAttachment.isPresent())
                throw new IllegalArgumentException("Only branches require parent attachment");
        }

        public RouteType routeType() {
            return role == RouteRole.STRATEGIC_BRANCH ? RouteType.STRATEGIC_BRANCH : RouteType.NATIONAL_TRUNK;
        }

        public long globalStation(int worldX, int worldZ) {
            return orientation == Orientation.NORTH_SOUTH ? worldZ : worldX;
        }

        public long clampStation(long station) {
            return Math.max(startStation, Math.min(endStation, station));
        }

        public BoundsXZ bounds(int halfWidth) {
            if (halfWidth < 0) throw new IllegalArgumentException("Negative highway envelope");
            return orientation == Orientation.NORTH_SOUTH
                    ? new BoundsXZ(fixedCoordinate - halfWidth, startStation, fixedCoordinate + halfWidth + 1, endStation + 1)
                    : new BoundsXZ(startStation, fixedCoordinate - halfWidth, endStation + 1, fixedCoordinate + halfWidth + 1);
        }

        public double distanceTo(double x, double z) {
            double station = orientation == Orientation.NORTH_SOUTH ? z : x;
            double lateral = (orientation == Orientation.NORTH_SOUTH ? x : z) - fixedCoordinate;
            return Math.hypot(lateral, station - Math.max(startStation, Math.min(endStation, station)));
        }
    }
}
