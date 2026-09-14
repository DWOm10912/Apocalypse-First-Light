package com.antaurora.apofirstlight.worldgen.structure;

import java.util.Objects;
import com.antaurora.apofirstlight.worldgen.spatial.Bounds3i;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Explicit inclusive Vanilla / exclusive shared boundary. Overflow and empty conversion are rejected. */
public final class VanillaBoundsAdapter {
    private VanillaBoundsAdapter() {}
    public static Bounds3i fromVanilla(BoundingBox box) {
        Objects.requireNonNull(box);
        return new Bounds3i(box.minX(), box.minY(), box.minZ(), Math.addExact(box.maxX(), 1),
                Math.addExact(box.maxY(), 1), Math.addExact(box.maxZ(), 1));
    }
    public static BoundingBox toVanilla(Bounds3i box) {
        Objects.requireNonNull(box);
        if (box.isEmpty()) throw new IllegalArgumentException("Empty shared bounds have no Vanilla box");
        return new BoundingBox(box.minX(), box.minY(), box.minZ(), Math.subtractExact(box.maxXExclusive(), 1),
                Math.subtractExact(box.maxYExclusive(), 1), Math.subtractExact(box.maxZExclusive(), 1));
    }
}
