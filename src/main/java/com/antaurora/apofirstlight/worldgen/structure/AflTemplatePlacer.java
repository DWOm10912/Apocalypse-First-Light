package com.antaurora.apofirstlight.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Places an AFL building template the way AFL buildings are placed (Building Wiring V1 phase 6,
 * docs/gameplay/building_wiring_v1_phase6_draft.md 5): with {@link AflBlockEntityProcessor}, so plug and panel offsets and
 * the panels' packed wiring turn with the building. The dev command {@code /afl dev template} uses it; the forest start's
 * placement (M4 P3) is to use the same call. Server thread only.
 * <p>
 * The whole template volume is written, its air included, so it belongs on clear ground.
 */
public final class AflTemplatePlacer {
    /** Where /afl_author export writes (BuildingAuthoringCommands). */
    public static final String EXPORTS = "afl_authoring_exports";

    private AflTemplatePlacer() {}

    /** An export's file: GAMEDIR/afl_authoring_exports/&lt;name&gt;.nbt (a building ID: lower case, digits, underscore). */
    public static Optional<Path> exportFile(String name) {
        if (!name.matches("[a-z][a-z0-9_]{0,63}")) return Optional.empty();
        Path p = FMLPaths.GAMEDIR.get().resolve(EXPORTS).resolve(name + ".nbt");
        return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    /**
     * A template by name: an export of that building ID when there is one (a bare name, no namespace), else the structure
     * manager's resource template.
     */
    public static Optional<StructureTemplate> load(ServerLevel level, ResourceLocation name, boolean bare) throws IOException {
        if (bare) {
            Optional<Path> file = exportFile(name.getPath());
            if (file.isPresent()) {
                StructureTemplate t = new StructureTemplate();
                try (InputStream in = Files.newInputStream(file.get())) {
                    t.load(level.holderLookup(Registries.BLOCK), NbtIo.readCompressed(in));
                }
                return Optional.of(t);
            }
        }
        return level.getStructureManager().get(name);
    }

    /** The placement settings: entities never (exports have none), the AFL processor unless raw. */
    public static StructurePlaceSettings settings(Rotation rotation, Mirror mirror, boolean raw) {
        StructurePlaceSettings s = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror).setIgnoreEntities(true);
        if (!raw) s.addProcessor(AflBlockEntityProcessor.INSTANCE);
        return s;
    }

    /**
     * Places the template with its origin corner at {@code at}, turned and mirrored about it (vanilla's order: mirror, then
     * rotate). {@code raw} leaves out the AFL processor, as vanilla /place template does (the dev check of a panel turning
     * its wiring by itself). The chunks it covers are loaded first. Blocks are set as /place template sets them (no
     * neighbour updates while placing).
     */
    public static boolean place(ServerLevel level, StructureTemplate template, BlockPos at, Rotation rotation, Mirror mirror, boolean raw) {
        StructurePlaceSettings s = settings(rotation, mirror, raw);
        BoundingBox box = template.getBoundingBox(s, at);
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) level.getChunk(x, z);
        return template.placeInWorld(level, at, at, s, level.getRandom(), Block.UPDATE_CLIENTS);
    }
}
