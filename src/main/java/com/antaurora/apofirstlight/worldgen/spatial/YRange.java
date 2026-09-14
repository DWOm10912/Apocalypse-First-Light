package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.Objects;

/**
 * Integer half-open bounds [min, max), allowing empty axes and arbitrary integer coordinates.
 * Dimensions use long so full integer-domain spans cannot wrap.
 * Intersection preserves max-of-minima as its anchor and clamps each disjoint axis to zero.
 * Empty bounds at different anchors remain distinct values, but contain/intersect no points.
 */
public record YRange(int minY, int maxYExclusive) {
    public YRange {
        if (minY > maxYExclusive) throw new IllegalArgumentException("Reversed bounds");
    }
    public long height() { return (long) maxYExclusive - minY; }
    public boolean isEmpty() { return minY == maxYExclusive; }
    public boolean contains(int y) {
        return y >= minY && y < maxYExclusive;
    }
    public boolean intersects(YRange other) {
        Objects.requireNonNull(other, "other");
        return !isEmpty() && !other.isEmpty() && minY < other.maxYExclusive && other.minY < maxYExclusive;
    }
}
