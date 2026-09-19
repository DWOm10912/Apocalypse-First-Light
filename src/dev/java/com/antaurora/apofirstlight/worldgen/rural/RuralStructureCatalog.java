package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.worldgen.structure.StructureDefinition;
import com.antaurora.apofirstlight.worldgen.structure.StructureDefinitionLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/** Once-per-classloader bundled resource adapter; never parses metadata in candidate/chunk loops. */
public final class RuralStructureCatalog {
    private static final String ROOT = "/data/apocalypse_firstlight/afl_worldgen/";
    /** Existing variants join natural selection without changing the frozen six-entry dev recipe. */
    private static final List<ResourceLocation> NATURAL_VARIANTS = List.of(
            new ResourceLocation("apocalypse_firstlight", "rural_farmhouse_02"),
            new ResourceLocation("apocalypse_firstlight", "rural_house_small_02"));
    private final RuralRecipe recipe;
    private final List<RuralStructurePool.Definition> legacy;
    private final List<RuralStructurePool.Definition> natural;
    private final Map<ResourceLocation, RuralStructurePool.Definition> byNbt;
    private final Map<ResourceLocation, StructureDefinition> metadata;

    private RuralStructureCatalog(RuralRecipe recipe, List<RuralStructurePool.Definition> legacy,
                                  List<RuralStructurePool.Definition> natural,
                                  Map<ResourceLocation, StructureDefinition> metadata) {
        this.recipe = recipe;
        this.legacy = List.copyOf(legacy);
        this.natural = List.copyOf(natural);
        this.metadata = Map.copyOf(metadata);
        var map = new LinkedHashMap<ResourceLocation, RuralStructurePool.Definition>();
        for (var definition : natural) map.put(definition.id(), definition);
        this.byNbt = Map.copyOf(map);
    }

    public static RuralStructureCatalog loadBundled() {
        RuralRecipe recipe = RuralRecipe.parse(read(ROOT + "rural/legacy_natural_v1.json"));
        if (recipe.entries().size() != 6 || recipe.entries().get(0).role() != RuralStructurePool.Role.FARMHOUSE
                || recipe.entries().get(1).role() != RuralStructurePool.Role.AGRICULTURAL_LARGE)
            throw new IllegalStateException("Legacy V1 requires its frozen six-entry pool and required-role order");
        var legacy = new ArrayList<RuralStructurePool.Definition>();
        var metadata = new LinkedHashMap<ResourceLocation, StructureDefinition>();
        for (RuralRecipe.Entry entry : recipe.entries()) {
            ResourceLocation id = entry.asset();
            StructureDefinition asset = loadMetadata(id);
            String expectedCategory = "rural/" + entry.role().name().toLowerCase(Locale.ROOT);
            if (!asset.category().equals(new ResourceLocation("apocalypse_firstlight", expectedCategory)))
                throw new IllegalStateException("Legacy Rural category/role mismatch: " + id);
            legacy.add(new RuralStructurePool.Definition(asset.structureNbt(), entry.declaredWeight(),
                    entry.declaredMaxCount(), asset.front(), entry.role(), asset.groundAnchorOffsetY()));
            metadata.put(id, asset);
        }
        var natural = new ArrayList<>(legacy);
        for (ResourceLocation id : NATURAL_VARIANTS) {
            StructureDefinition asset = loadMetadata(id);
            if (metadata.putIfAbsent(id, asset) != null)
                throw new IllegalStateException("Natural Rural variant is already in legacy recipe: " + id);
            RuralStructurePool.Role role = switch (asset.category().getPath()) {
                case "rural/farmhouse" -> RuralStructurePool.Role.FARMHOUSE;
                case "rural/residential" -> RuralStructurePool.Role.RESIDENTIAL;
                default -> throw new IllegalStateException("Unsupported Rural variant category: " + id);
            };
            RuralStructurePool.Definition counterpart = legacy.stream()
                    .filter(definition -> definition.role() == role).findFirst().orElseThrow();
            natural.add(new RuralStructurePool.Definition(asset.structureNbt(), counterpart.weight(),
                    counterpart.maxCount(), asset.front(), role, asset.groundAnchorOffsetY()));
        }
        return new RuralStructureCatalog(recipe, legacy, natural, metadata);
    }

    private static StructureDefinition loadMetadata(ResourceLocation id) {
        var parsed = StructureDefinitionLoader.parse(id, read(ROOT + "structures/" + id.getPath() + ".json"));
        if (parsed.definition().isEmpty())
            throw new IllegalStateException("Invalid Rural metadata " + id + ": " + parsed.validation().issues());
        StructureDefinition asset = parsed.definition().orElseThrow();
        if (!asset.structureNbt().equals(id) || asset.allowedRotations().size() != Rotation.values().length)
            throw new IllegalStateException("Rural asset NBT/rotations mismatch: " + id);
        return asset;
    }

    private static String read(String resource) {
        try (InputStream input = RuralStructureCatalog.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing bundled Rural resource " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Rural resource " + resource, exception);
        }
    }

    public RuralRecipe recipe() { return recipe; }
    public List<RuralStructurePool.Definition> legacyDefinitions() { return legacy; }
    public List<RuralStructurePool.Definition> naturalDefinitions() { return natural; }
    public RuralStructurePool.Definition legacyDefinition(ResourceLocation id) { return byNbt.get(id); }
    public StructureDefinition metadata(ResourceLocation id) { return metadata.get(id); }
}
