package com.antaurora.apofirstlight.worldgen.highway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure, random-access primary-highway layout derived only from the world seed. */
public final class PrimaryHighwayNetwork {
    public static final int BASE_SPACING = 2200;
    public static final int POSITION_JITTER = 300;
    public static final int MINIMUM_SPACING = BASE_SPACING - POSITION_JITTER;
    public static final int FOOTPRINT_HALF_WIDTH = 20;

    private static final long NS_SALT = 0x4e535f5052494d41L;
    private static final long EW_SALT = 0x45575f5052494d41L;

    private final long seed;
    private final int nsOrigin;
    private final int ewOrigin;

    public PrimaryHighwayNetwork(long seed) {
        this.seed = seed;
        this.nsOrigin = bounded(mix(seed ^ NS_SALT), BASE_SPACING) - BASE_SPACING / 2;
        this.ewOrigin = bounded(mix(seed ^ EW_SALT), BASE_SPACING) - BASE_SPACING / 2;
    }

    public long seed() {
        return seed;
    }

    public Corridor corridor(Orientation orientation, int index) {
        int origin = orientation == Orientation.PRIMARY_NORTH_SOUTH ? nsOrigin : ewOrigin;
        long salt = orientation == Orientation.PRIMARY_NORTH_SOUTH ? NS_SALT : EW_SALT;
        int pairIndex = Math.floorDiv(index, 2);
        int intervalJitter = bounded(mix(seed ^ salt ^ ((long) pairIndex * 0x9e3779b97f4a7c15L)),
                POSITION_JITTER * 2 + 1) - POSITION_JITTER;
        // Each deterministic pair uses +jitter then -jitter. This is a random-access
        // interval sequence: monotonic, 1900..2500 spacing, and exactly 2200 average.
        int pairOffset = Math.floorMod(index, 2) == 1 ? intervalJitter : 0;
        long coordinate = (long) origin + (long) index * BASE_SPACING + pairOffset;
        if (coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Highway corridor index is outside the Minecraft world coordinate range: " + index);
        }
        return new Corridor(orientation, index, (int) coordinate);
    }

    public Corridor nearest(Orientation orientation, int coordinate) {
        int origin = orientation == Orientation.PRIMARY_NORTH_SOUTH ? nsOrigin : ewOrigin;
        int estimate = (int) Math.round((coordinate - (double) origin) / BASE_SPACING);
        Corridor nearest = null;
        long best = Long.MAX_VALUE;
        for (int index = estimate - 2; index <= estimate + 2; index++) {
            Corridor candidate = corridor(orientation, index);
            long distance = Math.abs((long) coordinate - candidate.fixedCoordinate());
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    public List<Corridor> nearby(Orientation orientation, int minCoordinate, int maxCoordinate,
                                 int radius) {
        int expandedMin = minCoordinate - radius;
        int expandedMax = maxCoordinate + radius;
        Corridor nearMin = nearest(orientation, expandedMin);
        Corridor nearMax = nearest(orientation, expandedMax);
        int minIndex = Math.min(nearMin.index(), nearMax.index()) - 2;
        int maxIndex = Math.max(nearMin.index(), nearMax.index()) + 2;
        List<Corridor> result = new ArrayList<>();
        for (int index = minIndex; index <= maxIndex; index++) {
            Corridor corridor = corridor(orientation, index);
            if (corridor.fixedCoordinate() >= expandedMin && corridor.fixedCoordinate() <= expandedMax) {
                result.add(corridor);
            }
        }
        result.sort(Comparator.comparingInt(Corridor::index));
        return List.copyOf(result);
    }

    public InterstateInterchangeNode node(Corridor ns, Corridor ew, HighwayTerrainSampler terrain) {
        if (ns.orientation() != Orientation.PRIMARY_NORTH_SOUTH
                || ew.orientation() != Orientation.PRIMARY_EAST_WEST) {
            throw new IllegalArgumentException("Interchange nodes require one N/S and one E/W corridor");
        }
        int x = ns.fixedCoordinate();
        int z = ew.fixedCoordinate();
        int baseNs = terrain.globalRoadY(ns, z);
        int baseEw = terrain.globalRoadY(ew, x);
        int nsRaise = Math.max(0, baseEw + InterstateInterchangeNode.REQUIRED_SURFACE_SEPARATION - baseNs);
        int ewRaise = Math.max(0, baseNs + InterstateInterchangeNode.REQUIRED_SURFACE_SEPARATION - baseEw);
        Orientation upper;
        if (nsRaise < ewRaise) upper = Orientation.PRIMARY_NORTH_SOUTH;
        else if (ewRaise < nsRaise) upper = Orientation.PRIMARY_EAST_WEST;
        else upper = (mix(seed ^ ((long) ns.index() << 32) ^ (ew.index() & 0xffffffffL)) & 1L) == 0L
                ? Orientation.PRIMARY_NORTH_SOUTH : Orientation.PRIMARY_EAST_WEST;
        int lowerRoadY = upper == Orientation.PRIMARY_NORTH_SOUTH ? baseEw : baseNs;
        int upperBaseY = upper == Orientation.PRIMARY_NORTH_SOUTH ? baseNs : baseEw;
        int upperRoadY = Math.max(upperBaseY,
                lowerRoadY + InterstateInterchangeNode.REQUIRED_SURFACE_SEPARATION);
        return new InterstateInterchangeNode(ns, ew, x, z, upper, baseNs, baseEw,
                lowerRoadY, upperRoadY);
    }

    static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static int bounded(long value, int bound) {
        return (int) Math.floorMod(value, (long) bound);
    }

    public enum Orientation {
        PRIMARY_NORTH_SOUTH("NS"),
        PRIMARY_EAST_WEST("EW");

        private final String idPrefix;

        Orientation(String idPrefix) {
            this.idPrefix = idPrefix;
        }
    }

    public record Corridor(Orientation orientation, int index, int fixedCoordinate) {
        public String id() {
            return orientation.idPrefix + "_" + index;
        }

        public long globalStation(int worldX, int worldZ) {
            return orientation == Orientation.PRIMARY_NORTH_SOUTH ? worldZ : worldX;
        }
    }
}
