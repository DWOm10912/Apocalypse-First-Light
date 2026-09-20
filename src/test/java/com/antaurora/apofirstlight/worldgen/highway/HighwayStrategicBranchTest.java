package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.antaurora.apofirstlight.worldgen.spatial.YRange;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import static com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph.*;

/** Pure main-method contracts, like the existing terrain/spatial contract tests; no world. */
public final class HighwayStrategicBranchTest {
    private static int checks;
    private static final ResourceKey<Level> OVERWORLD = dimension("overworld");
    private static final ResourceKey<Level> NETHER = dimension("the_nether");
    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> dimension(String name) {
        try {
            var factory = ResourceKey.class.getDeclaredMethod("create", ResourceLocation.class, ResourceLocation.class);
            factory.setAccessible(true);
            return (ResourceKey<Level>) factory.invoke(null, new ResourceLocation("minecraft", "dimension"),
                    new ResourceLocation("minecraft", name));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    public static void main(String[] args) {
        HighwayRouteGraph base = HighwayRouteGraph.build(42);
        check(base.getNationalTrunks().size() == 2 && base.getStrategicBranches().isEmpty(), "two trunks only");
        check(base.intersection().equals(new Node("national_intersection", NodeKind.INTERSECTION, 926, -859)), "Phase 1 intersection fixture");
        Edge a = base.getEdgeById("national_trunk_a/main").orElseThrow();
        Edge b = base.getEdgeById("national_trunk_b/main").orElseThrow();
        check(a.routeId().equals("national_trunk_a") && a.startStation() == -6664
                && a.endStation() == 7168 && a.fixedCoordinate() == -859, "Phase 1 trunk A fixture");
        check(b.routeId().equals("national_trunk_b") && b.startStation() == -6944
                && b.endStation() == 6416 && b.fixedCoordinate() == 926, "Phase 1 trunk B fixture");
        check(a.bounds(32).equals(new BoundsXZ(-6664, -891, 7169, -826)), "Phase 1 A claim bounds");
        check(b.bounds(32).equals(new BoundsXZ(894, -6944, 959, 6417)), "Phase 1 B claim bounds");
        check(base.edges().equals(HighwayRouteGraph.build(42).edges()), "reconstruction determinism");
        HighwayRouteGraph extended = branch(base, "example", 1000, -1359);
        Edge branch = extended.getEdgeById("strategic_branch/example/main").orElseThrow();
        var attachment = branch.parentAttachment().orElseThrow();
        check(branch.routeType() == RouteType.STRATEGIC_BRANCH, "branch type");
        check(attachment.parentRouteId().equals(a.routeId()) && attachment.parentEdgeId().equals(a.id())
                && attachment.parentStation() == 1000, "exact parent station");
        check(extended.nodes().stream().anyMatch(n -> n.id().equals(attachment.junctionNodeId())
                && n.x() == 1000 && n.z() == -859), "explicit junction");
        check(branch.startStation() == -1359 && branch.endStation() == -859, "negative-direction finite interval");
        check(branch.endNode().id().equals(attachment.junctionNodeId()), "semantic start survives station sorting");
        BoundsXZ area = new BoundsXZ(995, -1205, 1005, -1195);
        check(extended.query(area, 32).equals(java.util.List.of(branch)), "shared bounds query");
        var claims = HighwaySpatialClaimProvider.query(extended, OVERWORLD, area);
        check(claims.size() == 1 && claims.get(0).boundsXZ().equals(branch.bounds(32))
                && claims.get(0).yRange().orElseThrow().equals(new YRange(-64, 320))
                && claims.get(0).priority() == 70 && claims.get(0).exclusionMargin() == 0, "real claim consumer");
        check(HighwaySpatialClaimProvider.query(extended, NETHER, area).isEmpty(), "dimension exclusion");
        check(extended.query(new BoundsXZ(999, -1400, 1001, -1390), 32).isEmpty(), "no infinite extension");
        BoundsXZ all = new BoundsXZ(-10000, -10000, 10000, 10000);
        check(HighwaySpatialClaimProvider.query(extended, OVERWORLD, all).containsAll(
                HighwaySpatialClaimProvider.query(base, OVERWORLD, all)), "trunk claim identities unchanged");
        check(extended.getNationalTrunks().equals(base.getNationalTrunks()), "unchanged parent routes");
        check(base.edges().size() == 2 && HighwayRouteGraph.forSeed(42).edges().size() == 2, "cache not mutated");
        var first = branch(branch(base, "one", 1000, -1359), "two", 1200, -359);
        var second = branch(branch(base, "two", 1200, -359), "one", 1000, -1359);
        check(first.routes().equals(second.routes()) && first.nodes().equals(second.nodes()), "insertion order independent");
        check(branch(base, "example", 1000, -1359).edges().equals(extended.edges()), "stable branch identity");
        check(base.findParentCandidate(-10000, -859).parentStation() == -6664, "finite projection clamp");
        var nested = extended.withStrategicBranch("nested", branch.routeId(), branch.id(), -1200, 1500, -1200, "generic");
        check(nested.getStrategicBranches().size() == 2, "branch parent supported");
        reject(() -> branch(extended, "example", 1000, -1359), "duplicate ID");
        reject(() -> base.withStrategicBranch("bad", "wrong", a.id(), 1000, 1000, -1359, "generic"), "wrong parent");
        reject(() -> base.withStrategicBranch("bad", a.routeId(), "missing", 1000, 1000, -1359, "generic"), "missing edge");
        reject(() -> branch(base, "bad", a.endStation() + 1, -1359), "outside parent");
        reject(() -> branch(base, "bad", 1000, -859), "zero length");
        reject(() -> base.withStrategicBranch("bad", a.routeId(), a.id(), 1000, 1001, -1359, "generic"), "diagonal rejected");
        try { extended.edges().clear(); throw new AssertionError("mutable edges"); }
        catch (UnsupportedOperationException expected) { checks++; }
        System.out.println("HighwayStrategicBranchTest PASS: " + checks + " checks; no world created");
    }
    private static HighwayRouteGraph branch(HighwayRouteGraph graph, String key, int x, int z) {
        return graph.withStrategicBranch(key, "national_trunk_a", "national_trunk_a/main", x, x, z, "generic");
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
    private static void reject(Runnable action, String name) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(name);
    }
}
