package com.antaurora.apofirstlight.worldgen.network.contract;

/**
 * A space someone holds (contract section 5). Priority, high to low: the start home (hard) > a placed lot (hard) > road
 * surface and formation (hard) > a road corridor's soft zone (siting only, may give way in planning) > natural features
 * (trees do not grow in a claim: ForestPlan.exclude). Conflicts are settled in planning only, never per chunk.
 *
 * @param minY lowest block Y the claim covers (Integer.MIN_VALUE: unbounded)
 * @param maxY highest block Y the claim covers (Integer.MAX_VALUE: unbounded)
 * @param buffer metres the claim reaches beyond its shape
 */
public record SpatialClaim(String id, String owner, Kind kind, Shape shape, int minY, int maxY, double buffer, int priority, boolean hard) {
    /** TERRAIN_KEEP: no one else may change the ground here (round the start home). */
    public enum Kind { BUILDING, YARD, PRIVATE_ROAD, LOT, ROAD_SURFACE, ROAD_CORRIDOR, TERRAIN_KEEP }

    /** The priorities of section 5 (higher wins). */
    public static final int START_HOME = 100, PLACED_LOT = 80, ROAD = 60, CORRIDOR_SOFT = 40, NATURAL = 0;

    /** Signed distance to the claim including its buffer (contract Shape convention). */
    public double distance(double x, double z) { return shape.distance(x, z) - buffer; }
}
