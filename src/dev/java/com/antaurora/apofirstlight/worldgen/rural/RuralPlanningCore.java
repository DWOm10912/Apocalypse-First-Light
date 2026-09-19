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
        List<RuralStructurePool.Definition> available = mode == SelectionMode.LEGACY_NATURAL_V1
                ? RuralStructurePool.naturalDefinitions() : RuralStructurePool.definitions();
        for (RuralStructurePool.Definition definition : available) {
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
        List<RuralStructurePool.Definition> matching = RuralStructurePool.naturalDefinitions().stream()
                .filter(definition -> definition.role() == selectedRole
                        || selectedRole == RuralStructurePool.Role.FLEX && definition.weight() > 0)
                .toList();
        if (matching.isEmpty()) return null;
        return selectVariant(matching, seed, center, selectedRole, index);
    }

    /** Required farmhouse remains one role slot; its two appearances are chosen evenly. */
    public static RuralStructurePool.Definition selectRequiredFarmhouse(long seed, BlockPos center) {
        List<RuralStructurePool.Definition> matching = RuralStructurePool.naturalDefinitions().stream()
                .filter(definition -> definition.role() == RuralStructurePool.Role.FARMHOUSE).toList();
        return selectVariant(matching, seed, center, RuralStructurePool.Role.FARMHOUSE, 0);
    }

    private static RuralStructurePool.Definition selectVariant(List<RuralStructurePool.Definition> candidates,
                                                               long seed, BlockPos center,
                                                               RuralStructurePool.Role role, int slotIndex) {
        long key = mix64(seed ^ 0x9E3779B97F4A7C15L);
        key = mix64(key ^ ((long) center.getX() * 0xBF58476D1CE4E5B9L));
        key = mix64(key ^ ((long) center.getZ() * 0x94D049BB133111EBL));
        key = mix64(key ^ ((long) role.ordinal() * 0xD6E8FEB86659FD93L));
        key = mix64(key ^ ((long) slotIndex * 0xA0761D6478BD642FL));
        return candidates.get(Math.floorMod(key, candidates.size()));
    }

    /** SplitMix64 finalizer; mixes high X/Z bits into the low bit used by two-variant roles. */
    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    /** Only the two newly variant-bearing roles retry the other appearance for the same slot. */
    public static List<RuralStructurePool.Definition> naturalAlternatives(RuralStructurePool.Definition first) {
        if (first.role() != RuralStructurePool.Role.FARMHOUSE
                && first.role() != RuralStructurePool.Role.RESIDENTIAL) return List.of(first);
        List<RuralStructurePool.Definition> alternatives = new ArrayList<>();
        alternatives.add(first);
        RuralStructurePool.naturalDefinitions().stream()
                .filter(definition -> definition.role() == first.role() && !definition.equals(first))
                .forEach(alternatives::add);
        return List.copyOf(alternatives);
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
