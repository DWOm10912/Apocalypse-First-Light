package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.Objects;

/**
 * Integer half-open bounds [min, max), allowing empty axes and arbitrary integer coordinates.
 * Dimensions use long so full integer-domain spans cannot wrap.
 * Intersection preserves max-of-minima as its anchor and clamps each disjoint axis to zero.
 * Empty bounds at different anchors remain distinct values, but contain/intersect no points.
 */
public record BoundsXZ(int minX, int minZ, int maxXExclusive, int maxZExclusive) {
    public BoundsXZ {
        if (minX > maxXExclusive || minZ > maxZExclusive) throw new IllegalArgumentException("Reversed bounds");
    }
    public long width() { return (long) maxXExclusive - minX; }
    public long depth() { return (long) maxZExclusive - minZ; }
    public boolean isEmpty() { return minX == maxXExclusive || minZ == maxZExclusive; }
    public boolean contains(int x, int z) {
        return x >= minX && x < maxXExclusive && z >= minZ && z < maxZExclusive;
    }
    public boolean intersects(BoundsXZ other) {
        Objects.requireNonNull(other, "other");
        return !isEmpty() && !other.isEmpty() && minX < other.maxXExclusive && other.minX < maxXExclusive && minZ < other.maxZExclusive && other.minZ < maxZExclusive;
    }
    public BoundsXZ intersection(BoundsXZ other) {
        Objects.requireNonNull(other, "other");
        int x = Math.max(minX, other.minX);
        int z = Math.max(minZ, other.minZ);
        return new BoundsXZ(x, z, Math.max(x, Math.min(maxXExclusive, other.maxXExclusive)), Math.max(z, Math.min(maxZExclusive, other.maxZExclusive)));
    }
    /** Expands each axis, including empty axes. Throws on negative margin or integer overflow. */
    public BoundsXZ expand(int margin) {
        if (margin < 0) throw new IllegalArgumentException("Negative margin");
        return new BoundsXZ(Math.subtractExact(minX, margin), Math.subtractExact(minZ, margin),
                Math.addExact(maxXExclusive, margin), Math.addExact(maxZExclusive, margin));
    }
}
