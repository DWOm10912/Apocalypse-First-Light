package com.antaurora.apofirstlight.worldgen.highway;

import java.util.List;

/** Invokes the real corridor consumer with synthetic profiles; no Minecraft bootstrap/world. */
public final class HighwayGeometryIntegrationTest {
    public static void main(String[] args) throws Exception {
        HighwayGeometryTest.main(args);
        var geometry = new HighwayGeometry(List.of(new HighwayGeometry.Point(1000,-859),
                new HighwayGeometry.Point(1064,-859), new HighwayGeometry.Point(1128,-923),
                new HighwayGeometry.Point(1192,-923)));
        var base = HighwayRouteGraph.build(42);
        require(base.query(new com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ(
                base.intersection().x(), base.intersection().z(),
                base.intersection().x() + 1, base.intersection().z() + 1),
                HighwayRouteGraph.FOOTPRINT_HALF_WIDTH).stream()
                .filter(e -> e.routeType() == HighwayRouteGraph.RouteType.NATIONAL_TRUNK).count() == 2,
                "national trunk lookup");
        var graph = base.withStrategicBranch("geometry", "national_trunk_a", "national_trunk_a/main",
                1000, geometry, "synthetic");
        var edge = graph.getEdgeById("strategic_branch/geometry/main").orElseThrow();
        require(edge.geometry() == geometry && graph.getNationalTrunks().equals(base.getNationalTrunks()), "graph binding");
        require(graph.query(geometry.bounds(0),32).contains(edge), "finite graph query");
        var plan = HighwayPlan.ribbon(geometry,0,geometry.length());
        var corridor = HighwayCorridor.buildNatural(null,plan,profile(plan,HighwayTerrainMode.GROUND,8));
        require(!corridor.geometryDeferred(), "supported surface");
        require(corridor.cells().size() == geometry.raster(geometry.bounds(11.5),11.5).size(), "consumer footprint");
        require(!corridor.roadMarkings().isEmpty() && corridor.roadMarkings().stream().anyMatch(m -> m.rises()!=0), "sloped marking risers");
        require(corridor.rowEnvelope().stream().allMatch(c -> geometry.query(c.x(),c.z(),14.5)!=null), "grading only expanded ribbon");
        var viaduct = HighwayCorridor.buildNatural(null,plan,profile(plan,HighwayTerrainMode.VIADUCT,0));
        require(!viaduct.geometryDeferred() && viaduct.cells().stream().allMatch(HighwayCorridor.Cell::structuralBridge),
                "viaduct ribbon supported");
        for (var mode : List.of(HighwayTerrainMode.TUNNEL)) {
            var deferred = HighwayCorridor.buildNatural(null,plan,profile(plan,mode,0));
            require(deferred.geometryDeferred() && deferred.cells().isEmpty() && deferred.rowEnvelope().isEmpty()
                    && deferred.coreRoadColumns().isEmpty() && deferred.cutColumns().isEmpty()
                    && deferred.tunnelBorePositions().isEmpty(), "unsupported structural mode emits nothing");
        }
        require(HighwayLiveGenerationDiagnostic.classify(true, true, false,
                corridor.cells().size(), 1, 1) == HighwayLiveGenerationDiagnostic.DropPoint.NONE,
                "surface branch preflight");
        require(HighwayLiveGenerationDiagnostic.classify(true, true, true,
                0, 0, 1) == HighwayLiveGenerationDiagnostic.DropPoint.BUILD_RIBBON,
                "unsupported ribbon first drop");
        require(HighwayLiveGenerationDiagnostic.classify(true, false, false,
                0, 0, 0) == HighwayLiveGenerationDiagnostic.DropPoint.NO_GRAPH_EDGE,
                "no-edge first drop");
        require(HighwayLiveGenerationDiagnostic.classify(false, true, false,
                1, 1, 1) == HighwayLiveGenerationDiagnostic.DropPoint.FEATURE_NOT_ELIGIBLE,
                "feature-ineligible first drop");
        require(HighwayLiveGenerationDiagnostic.classify(true, true, false,
                100, 0, 1) == HighwayLiveGenerationDiagnostic.DropPoint.SEGMENT_SELECTION,
                "selected segment has no owned ribbon cells");
        System.out.println("HighwayGeometryIntegrationTest PASS: graph, corridor, grading, sloped markings, viaduct supported / tunnel no-write; no world");
    }
    private static HighwayProfile profile(HighwayPlan plan, HighwayTerrainMode mode, int rise) throws Exception {
        var samples = List.of(sample(plan,0,80,mode), sample(plan,plan.length(),80+rise,mode));
        var resolution = new HighwayBridgeSpanResolver.Resolution(samples,List.of(),0,0,0,0,0);
        var constructor = HighwayProfile.class.getDeclaredConstructor(HighwayPlan.class,
                HighwayBridgeSpanResolver.Resolution.class,int.class,double.class,boolean.class,HighwayNodeConstraints.class);
        constructor.setAccessible(true);
        return constructor.newInstance(plan,resolution,0,0,false,HighwayNodeConstraints.NONE);
    }
    private static HighwayProfile.Sample sample(HighwayPlan p,double s,int y,HighwayTerrainMode mode) {
        var point=p.sample(s); var tangent=p.tangent(s);
        return new HighwayProfile.Sample(s,point.x(),point.z(),tangent.x(),tangent.z(),y,y,mode,mode,
                false,y,y,y,y,0,23,0,0,0,0,y,y,false,false);
    }
    private static void require(boolean value,String name) { if(!value) throw new AssertionError(name); }
}
