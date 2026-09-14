package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Compatibility identity view of the ordered bundled Legacy V1 recipe and WG-03 metadata. */
public final class RuralStructurePool {
    private static final RuralStructureCatalog CATALOG = RuralStructureCatalog.loadBundled();
    public static final Definition FARMHOUSE = CATALOG.legacyDefinitions().get(0);
    public static final Definition BARN = CATALOG.legacyDefinitions().get(1);

    private RuralStructurePool() {
    }

    public static List<Definition> definitions() {
        return CATALOG.legacyDefinitions();
    }

    public static Definition definition(ResourceLocation id) {
        return CATALOG.legacyDefinition(id);
    }

    public static RuralStructureCatalog catalog() { return CATALOG; }

    public enum Role {
        RESIDENTIAL,
        FARMHOUSE,
        AGRICULTURAL_LARGE,
        AGRICULTURAL_UTILITY,
        LANDMARK,
        FLEX
    }

    public record Definition(ResourceLocation id, int weight, int maxCount, Direction frontDirection,
                             Role role, int groundAnchorOffsetY) {
    }
}
