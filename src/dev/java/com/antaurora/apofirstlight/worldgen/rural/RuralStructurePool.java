package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Asset-independent input types retained for the inactive frontage and terrain algorithms.
 * There is no bundled pool, recipe, static resource load, or natural-generation entry point.
 * Future callers must supply their own definitions explicitly.
 */
public final class RuralStructurePool {
    private RuralStructurePool() {
    }

    public enum Role {
        RESIDENTIAL,
        FARMHOUSE,
        AGRICULTURAL_LARGE,
        AGRICULTURAL_UTILITY,
        LANDMARK,
        FLEX
    }

    public record Definition(ResourceLocation id, Direction frontDirection,
                             Role role, int groundAnchorOffsetY) {
    }
}
