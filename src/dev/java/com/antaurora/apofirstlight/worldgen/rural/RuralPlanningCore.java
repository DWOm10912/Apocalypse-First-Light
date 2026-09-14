package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Shared legacy catalog/selection policy. Terrain, lifecycle and writes stay with each adapter. */
public final class RuralPlanningCore {
    public enum SelectionMode { LEGACY_NATURAL_V1, LEGACY_DEV_V1 }

    private RuralPlanningCore() { }

    public static Catalog catalog(SelectionMode mode,
                                  Function<ResourceLocation, Optional<StructureTemplate>> source) {
        Map<RuralStructurePool.Definition, StructureTemplate> templates = new LinkedHashMap<>();
        RuralStructurePool.Definition firstMissing = null;
        for (RuralStructurePool.Definition definition : RuralStructurePool.definitions()) {
            Optional<StructureTemplate> template = source.apply(definition.id());
            if (template.isPresent()) {
                templates.put(definition, template.get());
            } else if (firstMissing == null) {
                firstMissing = definition;
                // Existing dev command aborts on its first missing template; Natural skips it
                // and only fails if that definition is actually required by this candidate.
                if (mode == SelectionMode.LEGACY_DEV_V1) break;
            }
        }
        return new Catalog(templates, firstMissing);
    }

    /** No weighted draw or maxCount; legacy FLEX still filters definitions with weight > 0. */
    public static RuralStructurePool.Definition selectNatural(RuralStructurePool.Role role,
                                                               long seed, BlockPos center, int index) {
        RuralStructurePool.Role selectedRole = role == RuralStructurePool.Role.FARMHOUSE
                ? RuralStructurePool.Role.RESIDENTIAL : role;
        if (selectedRole == RuralStructurePool.Role.AGRICULTURAL_LARGE) return null;
        List<RuralStructurePool.Definition> matching = RuralStructurePool.definitions().stream()
                .filter(definition -> definition.role() == selectedRole
                        || selectedRole == RuralStructurePool.Role.FLEX && definition.weight() > 0)
                .toList();
        if (matching.isEmpty()) return null;
        return matching.get((int) Math.floorMod(seed ^ center.asLong() ^ index, matching.size()));
    }

    /** Command-only maxCount filtering, kept separate from Natural V1. */
    public static List<RuralStructurePool.Definition> availableDev(RuralStructurePool.Role role,
                                                                     Map<RuralStructurePool.Definition, Integer> counts) {
        List<RuralStructurePool.Definition> result = new ArrayList<>();
        List<RuralStructurePool.Definition> definitions = RuralStructurePool.definitions();
        switch (role) {
            case RESIDENTIAL -> addIfAvailable(result, definitions.get(2), counts);
            case AGRICULTURAL_UTILITY -> {
                addIfAvailable(result, definitions.get(3), counts);
                addIfAvailable(result, definitions.get(4), counts);
            }
            case LANDMARK -> addIfAvailable(result, definitions.get(5), counts);
            case FLEX -> {
                addIfAvailable(result, definitions.get(2), counts);
                addIfAvailable(result, definitions.get(3), counts);
                addIfAvailable(result, definitions.get(4), counts);
                addIfAvailable(result, definitions.get(5), counts);
            }
            default -> { }
        }
        return result;
    }

    private static void addIfAvailable(List<RuralStructurePool.Definition> result,
                                       RuralStructurePool.Definition definition,
                                       Map<RuralStructurePool.Definition, Integer> counts) {
        if (counts.getOrDefault(definition, 0) < definition.maxCount()) result.add(definition);
    }

    /** Command-only weighted draw; call count and order are unchanged. */
    public static RuralStructurePool.Definition selectDev(List<RuralStructurePool.Definition> definitions,
                                                           RandomSource random) {
        int total = definitions.stream().mapToInt(RuralStructurePool.Definition::weight).sum();
        if (total <= 0) return definitions.get(0);
        int value = random.nextInt(total);
        for (RuralStructurePool.Definition definition : definitions) {
            value -= definition.weight();
            if (value < 0) return definition;
        }
        return definitions.get(definitions.size() - 1);
    }

    public record Catalog(Map<RuralStructurePool.Definition, StructureTemplate> templates,
                          RuralStructurePool.Definition firstMissing) {
        public Catalog {
            templates = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(templates));
        }
    }
}
