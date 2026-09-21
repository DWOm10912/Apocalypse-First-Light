package com.antaurora.apofirstlight.worldgen.highway;

/** Small pure-logic baseline; no world or Minecraft bootstrap. */
public final class HighwayPhase1Snapshot {
    public static void main(String[] args) {
        HighwayRouteGraph graph = HighwayRouteGraph.build(42L);
        System.out.println(graph.intersection());
        for (HighwayRouteGraph.Edge edge : graph.getNationalTrunks().stream().flatMap(r -> r.edges().stream()).toList()) {
            System.out.println(edge.routeId() + " " + edge.id() + " " + edge.startNode()
                    + " " + edge.endNode() + " " + edge.bounds(32));
        }
    }
}
