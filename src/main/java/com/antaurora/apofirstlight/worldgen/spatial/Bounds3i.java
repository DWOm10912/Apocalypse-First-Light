package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.Objects;

/**
 * Integer half-open bounds [min, max), allowing empty axes and arbitrary integer coordinates.
 * Dimensions use long so full integer-domain spans cannot wrap.
 * Intersection preserves max-of-minima as its anchor and clamps each disjoint axis to zero.
 * Empty bounds at different anchors remain distinct values, but contain/intersect no points.
 */
public record Bounds3i(int minX, int minY, int minZ, int maxXExclusive, int maxYExclusive, int maxZExclusive) {
    public Bounds3i {
        if (minX > maxXExclusive || minY > maxYExclusive || minZ > maxZExclusive) throw new IllegalArgumentException("Reversed bounds");
    }
    public long width() { return (long) maxXExclusive - minX; }
    public long height() { return (long) maxYExclusive - minY; }
    public long depth() { return (long) maxZExclusive - minZ; }
    public boolean isEmpty() { return minX == maxXExclusive || minY == maxYExclusive || minZ == maxZExclusive; }
    public boolean contains(int x, int y, int z) {
        return x >= minX && x < maxXExclusive && y >= minY && y < maxYExclusive && z >= minZ && z < maxZExclusive;
    }
    public boolean intersects(Bounds3i other) {
        Objects.requireNonNull(other, "other");
        return !isEmpty() && !other.isEmpty() && minX < other.maxXExclusive && other.minX < maxXExclusive && minY < other.maxYExclusive && other.minY < maxYExclusive && minZ < other.maxZExclusive && other.minZ < maxZExclusive;
    }
    public Bounds3i intersection(Bounds3i other) {
        Objects.requireNonNull(other, "other");
        int x = Math.max(minX, other.minX);
        int y = Math.max(minY, other.minY);
        int z = Math.max(minZ, other.minZ);
        return new Bounds3i(x, y, z, Math.max(x, Math.min(maxXExclusive, other.maxXExclusive)), Math.max(y, Math.min(maxYExclusive, other.maxYExclusive)), Math.max(z, Math.min(maxZExclusive, other.maxZExclusive)));
    }
    public BoundsXZ xz() { return new BoundsXZ(minX, minZ, maxXExclusive, maxZExclusive); }
    public YRange yRange() { return new YRange(minY, maxYExclusive); }
}
