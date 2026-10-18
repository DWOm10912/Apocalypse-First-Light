package com.antaurora.apofirstlight.worldgen.network.town;

import java.util.List;

/**
 * The towns that get a street framework (Town Framework V1, docs/worldgen/federal_towns_v1.md): streets, blocks, lots
 * and zoning, no buildings. V1 lays out Linden (valley_town) only; the other fifteen towns keep their bare network node.
 */
public final class TownSites {
    private TownSites() {}

    /** Town ids (FederalTemplate.CitySite) with a framework, in build order. */
    public static final List<String> FRAMEWORK = List.of("valley_town");

    /** The corridor of a town's own roads (its exit crossroad): town_ + its id. */
    public static String corridor(String town) { return "town_" + town; }

    /** The town a road of a town corridor belongs to, or null. */
    public static String townOf(String roadId) {
        if (roadId == null || !roadId.startsWith("town_")) return null;
        int c = roadId.indexOf(':');
        return c < 0 ? null : roadId.substring("town_".length(), c);
    }
}
