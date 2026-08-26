package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The fixed V1.1 rural building pool. Roles keep large agricultural lots from losing to generic slots. */
public final class RuralStructurePool {
    public static final Definition FARMHOUSE = new Definition(
            new ResourceLocation("apocalypse_firstlight", "rural_farmhouse_01"), 0, 1, Direction.SOUTH,
            Role.FARMHOUSE, 0);
    public static final Definition BARN = new Definition(
            new ResourceLocation("apocalypse_firstlight", "rural_barn_large_01"), 0, 1, Direction.SOUTH,
            Role.AGRICULTURAL_LARGE, 0);

    private static final List<Definition> DEFINITIONS = List.of(
            FARMHOUSE,
            BARN,
            new Definition(new ResourceLocation("apocalypse_firstlight", "rural_house_small_01"), 100, 3, Direction.SOUTH,
                    Role.RESIDENTIAL, 1),
            new Definition(new ResourceLocation("apocalypse_firstlight", "rural_storage_small_01"), 60, 1, Direction.SOUTH,
                    Role.AGRICULTURAL_UTILITY, 0),
            new Definition(new ResourceLocation("apocalypse_firstlight", "rural_grain_silo_01"), 50, 1, Direction.SOUTH,
                    Role.AGRICULTURAL_UTILITY, 1),
            new Definition(new ResourceLocation("apocalypse_firstlight", "rural_water_tower_01"), 25, 1, Direction.SOUTH,
                    Role.LANDMARK, 0));

    private RuralStructurePool() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Definition definition(ResourceLocation id) {
        return DEFINITIONS.stream().filter(definition -> definition.id().equals(id)).findFirst().orElse(null);
    }

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
