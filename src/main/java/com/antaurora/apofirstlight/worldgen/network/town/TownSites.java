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

    /**
     * A town's landmark (2026-10-20, user: the story hangs on landmark buildings, maybe one per town; ordinary houses are
     * laid out freely): a whole block reserved for it in every world, at its place where: "main" (by Main St, nearest the
     * town square) or "edge" (the town's far end). Its building is not designed yet (a white box).
     */
    public record Landmark(String id, String where) {}

    /** A town's landmarks, in the order their blocks are taken. */
    public static List<Landmark> landmarks(String town) {
        return "valley_town".equals(town) ? List.of(new Landmark("linden_landmark", "main")) : List.of();
    }

    /** The corridor of a town's own roads (its exit crossroad): town_ + its id. */
    public static String corridor(String town) { return "town_" + town; }

    /** The town a road of a town corridor belongs to, or null. */
    public static String townOf(String roadId) {
        if (roadId == null || !roadId.startsWith("town_")) return null;
        int c = roadId.indexOf(':');
        return c < 0 ? null : roadId.substring("town_".length(), c);
    }
}
