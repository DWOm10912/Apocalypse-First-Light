package com.antaurora.apofirstlight.worldgen.roads;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.antaurora.apofirstlight.worldgen.structure.StructureSocket;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Vec3i;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Explicit diagnostic schema, including 2D rectangles; no reflective serialization of Minecraft objects. */
public final class RoadPlanJson {
    private RoadPlanJson() {}

    public static String write(RoadPlan plan) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 2);
        root.addProperty("spec_version", plan.specVersion());
        root.addProperty("plan_id", plan.planId());
        root.addProperty("candidate_id", plan.candidateId());
        root.addProperty("layout", plan.layout().name());
        root.addProperty("status", plan.status().name());
        root.addProperty("construction_authorized", false);
        root.addProperty("coordinates", "World block X/Z; all rectangles half-open; Y is integer walking plane G");
        root.addProperty("height_contract", "asphalt surfaceH16 = 16 * G - 3; fractional construction not implemented");
        root.addProperty("node_ownership", "Node footprints take precedence over incident edge corridors; marking stops at footprint");
        root.addProperty("total_width_semantics", "Complete right of way = asphalt + 2 * (curb reservation + utility band + sidewalk)");
        root.addProperty("side_band_width_units", "Per side, in blocks; cross-section lists ordered negative then positive X/Z side");
        root.addProperty("access_geometry", "Reservation only; sidewalk contact at G, vehicle asphalt contact at S; full crossing height profile remains V1-B");
        JsonObject crossSections = new JsonObject();
        for (RoadType type : RoadType.values()) {
            JsonObject section = new JsonObject(); widths(section,type); crossSections.add(type.name(),section);
        }
        root.add("cross_section_types", crossSections);
        root.addProperty("connected", plan.connected());
        root.addProperty("attempts", plan.attempts());
        root.addProperty("terrain_samples", plan.terrainSamples());
        root.add("candidate_bounds", bounds(plan.candidateBounds()));
        JsonObject counts = new JsonObject();
        counts.addProperty("nodes", plan.nodes().size());
        counts.addProperty("edges", plan.edges().size());
        counts.addProperty("lots", plan.lots().size());
        Map<String, Integer> roadCounts = new TreeMap<>(), uses = new TreeMap<>(), kinds = new TreeMap<>();
        for (RoadType type : RoadType.values()) roadCounts.put(type.name(), 0);
        for (var edge : plan.edges()) roadCounts.merge(edge.type().name(), 1, Integer::sum);
        for (var node : plan.nodes()) kinds.merge(node.kind().name(), 1, Integer::sum);
        for (var lot : plan.lots()) uses.merge(lot.use(), 1, Integer::sum);
        counts.add("road_types", counts(roadCounts));
        counts.add("node_kinds", counts(kinds));
        counts.add("building_uses", counts(uses));
        root.add("counts", counts);
        JsonArray diagnostics = new JsonArray();
        plan.diagnostics().forEach(diagnostics::add);
        root.add("diagnostics", diagnostics);
        JsonArray nodes = new JsonArray();
        for (var node : plan.nodes()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", node.id());
            json.addProperty("x", node.x());
            json.addProperty("z", node.z());
            json.addProperty("G", node.groundY());
            json.addProperty("surfaceH16", node.surfaceH16());
            json.addProperty("kind", node.kind().name());
            json.add("footprint", boundsList(node.footprint()));
            RoadJunction junction = node.junction();
            json.addProperty("arm_extent", junction.armExtent());
            json.addProperty("marking_stop_boundary", "At each connected arm's edge_join; no markings through the node");
            JsonObject regions = new JsonObject();
            regions.add("asphalt", boundsList(junction.asphalt()));
            regions.add("curbs", boundsList(junction.curbs()));
            regions.add("utilities", boundsList(junction.utilities()));
            regions.add("sidewalks", boundsList(junction.sidewalks()));
            json.add("regions", regions);
            JsonArray arms = new JsonArray();
            for (var arm : node.arms()) {
                JsonObject a = new JsonObject();
                a.addProperty("direction", arm.direction().name());
                a.addProperty("road_type", arm.type().name());
                a.addProperty("edge_id", arm.edgeId());
                widths(a,arm.type());
                a.addProperty("edge_join_x", node.x()+arm.direction().getStepX()*junction.armExtent());
                a.addProperty("edge_join_z", node.z()+arm.direction().getStepZ()*junction.armExtent());
                arms.add(a);
            }
            json.add("arms", arms);
            nodes.add(json);
        }
        root.add("nodes", nodes);
        JsonArray edges = new JsonArray();
        for (var edge : plan.edges()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", edge.id());
            json.addProperty("from", edge.from());
            json.addProperty("to", edge.to());
            json.addProperty("road_type", edge.type().name());
            json.addProperty("x1", edge.x1()); json.addProperty("z1", edge.z1());
            json.addProperty("x2", edge.x2()); json.addProperty("z2", edge.z2());
            json.addProperty("length", edge.length());
            widths(json,edge.type());
            json.addProperty("G", edge.groundY());
            json.addProperty("surfaceH16", edge.surfaceH16());
            json.add("corridor", bounds(edge.corridor()));
            RoadCrossSection section = edge.crossSection();
            JsonObject bands = new JsonObject();
            bands.add("asphalt", bounds(section.asphalt()));
            bands.add("curbs", boundsList(section.curbs()));
            bands.add("utilities", boundsList(section.utilities()));
            bands.add("sidewalks", boundsList(section.sidewalks()));
            json.add("cross_section", bands);
            edges.add(json);
        }
        root.add("edges", edges);
        JsonArray lots = new JsonArray();
        for (var lot : plan.lots()) lots.add(lot(lot));
        root.add("lots", lots);
        JsonArray claims = new JsonArray();
        for (var claim : plan.claims()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", claim.id());
            json.addProperty("owner", claim.owner().toString());
            json.addProperty("dimension", claim.dimension().location().toString());
            json.addProperty("generation_version", claim.generationVersion());
            json.addProperty("type", claim.type().name());
            json.addProperty("strength", claim.strength().name());
            json.addProperty("priority", claim.priority());
            json.addProperty("exclusion_margin", claim.exclusionMargin());
            json.add("bounds", bounds(claim.boundsXZ()));
            claims.add(json);
        }
        root.add("claims", claims);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }

    private static JsonObject lot(RoadLotPlanner.Lot lot) {
        JsonObject json = new JsonObject();
        json.addProperty("id", lot.id());
        json.addProperty("use", lot.use());
        json.addProperty("variant_id", lot.variantId());
        json.addProperty("generation_version", lot.generationVersion());
        json.addProperty("road_id", lot.roadId());
        json.addProperty("G", lot.groundY());
        json.addProperty("surfaceH16", lot.surfaceH16());
        json.add("full_bounds", bounds(lot.fullBounds()));
        json.add("parking", boundsList(lot.parking()));
        json.add("service", boundsList(lot.service()));
        JsonObject setbacks = new JsonObject();
        setbacks.addProperty("front", lot.setbacks().front());
        setbacks.addProperty("left", lot.setbacks().left());
        setbacks.addProperty("right", lot.setbacks().right());
        setbacks.addProperty("rear", lot.setbacks().rear());
        json.add("local_setbacks", setbacks);
        var body = lot.building();
        JsonObject building = new JsonObject();
        building.addProperty("placeholder_only", true);
        building.addProperty("actual_nbt_bound", false);
        building.add("allowed_bounds", bounds(body.bounds()));
        building.add("size", vector(body.size()));
        building.addProperty("ground_anchor_offset_y", body.groundAnchorOffsetY());
        building.add("placement_origin", vector(body.placementOrigin()));
        building.add("lot_local_offset", vector(body.lotLocalOffset()));
        building.addProperty("asset_local_front", body.assetLocalFront().name());
        building.addProperty("world_front", body.worldFront().name());
        building.addProperty("rotation", body.rotation().name());
        building.add("main_socket", socket(body.mainSocket()));
        building.add("main_socket_world", vector(body.mainSocketWorld()));
        json.add("building", building);
        JsonArray connectors = new JsonArray();
        for (var connector : lot.connectors()) {
            JsonObject c = new JsonObject();
            c.addProperty("name", connector.name());
            c.addProperty("type", connector.type().name());
            c.addProperty("road_id", connector.roadId());
            c.addProperty("facing", connector.facing().name());
            c.addProperty("width", connector.width());
            c.add("entry_strip", bounds(connector.entryStrip()));
            c.add("internal_paths", boundsList(connector.internalPaths()));
            c.add("road_access", bounds(connector.roadAccess()));
            c.add("sidewalk_contact", bounds(connector.sidewalkContact()));
            c.add("asphalt_contact", connector.asphaltContact().map(RoadPlanJson::bounds).orElse(null));
            c.addProperty("access_geometry", "RESERVATION_ONLY");
            c.addProperty("road_surfaceH16", connector.roadSurfaceH16());
            c.addProperty("lot_surfaceH16", connector.lotSurfaceH16());
            JsonArray levels = new JsonArray();
            connector.curbTransitionH16().forEach(levels::add);
            c.add("curb_transitionH16", levels);
            connectors.add(c);
        }
        json.add("connectors", connectors);
        return json;
    }

    private static JsonObject socket(StructureSocket socket) {
        JsonObject result = new JsonObject();
        result.addProperty("name", socket.name());
        result.add("local_position", vector(socket.localPosition()));
        result.addProperty("facing", socket.facing().name());
        result.addProperty("type", socket.type().name());
        return result;
    }
    private static void widths(JsonObject target,RoadType type) {
        target.addProperty("asphalt_width",type.asphaltWidth());
        target.addProperty("right_of_way_width",type.rightOfWayWidth());
        target.addProperty("total_width",type.rightOfWayWidth());
        target.addProperty("curb_reservation",type.curbReservation());
        target.addProperty("utility_band",type.utilityBand());
        target.addProperty("sidewalk_width",type.sidewalkWidth());
    }
    private static JsonObject bounds(BoundsXZ bounds) {
        JsonObject result = new JsonObject();
        result.addProperty("min_x", bounds.minX()); result.addProperty("min_z", bounds.minZ());
        result.addProperty("max_x_exclusive", bounds.maxXExclusive());
        result.addProperty("max_z_exclusive", bounds.maxZExclusive());
        return result;
    }
    private static JsonArray boundsList(List<BoundsXZ> bounds) {
        JsonArray result = new JsonArray();
        bounds.forEach(b -> result.add(bounds(b)));
        return result;
    }
    private static JsonArray vector(Vec3i v) {
        JsonArray result = new JsonArray(); result.add(v.getX()); result.add(v.getY()); result.add(v.getZ());
        return result;
    }
    private static JsonObject counts(Map<String, Integer> values) {
        JsonObject result = new JsonObject(); values.forEach(result::addProperty); return result;
    }
}
