package com.antaurora.apofirstlight.worldgen.structure;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import com.antaurora.apofirstlight.worldgen.spatial.Bounds3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.*;
import static com.antaurora.apofirstlight.worldgen.structure.StructureValidationIssue.*;

/** Read-only standard compressed Structure NBT inspection. No registry lookup or template placement. */
public final class StructureNbtReader {
    private StructureNbtReader() {}
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final Set<String> FORBIDDEN = Set.of("minecraft:command_block", "minecraft:chain_command_block",
            "minecraft:repeating_command_block", "minecraft:structure_block", "minecraft:jigsaw",
            "minecraft:structure_void", "minecraft:barrier", "minecraft:light");

    /** nonAir includes every alternative palette, conservatively detecting blocked entrance cells. */
    public record Info(Bounds3i localBounds, long blockCount, long explicitAirCount, long blockEntityCount,
                       long multiblockCount, Set<BlockPos> nonAir) {
        public Info {
            java.util.Objects.requireNonNull(localBounds);
            if (localBounds.minX()!=0 || localBounds.minY()!=0 || localBounds.minZ()!=0 || localBounds.isEmpty())
                throw new IllegalArgumentException("Expected positive [0,size) bounds");
            if (blockCount<0 || explicitAirCount<0 || blockEntityCount<0 || multiblockCount<0)
                throw new IllegalArgumentException("Negative count");
            nonAir = nonAir.stream().map(BlockPos::immutable).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        public Vec3i size() { return new Vec3i(localBounds.maxXExclusive(), localBounds.maxYExclusive(), localBounds.maxZExclusive()); }
    }
    public record Inspection(Optional<Info> info, StructureValidationResult validation) {
        public Inspection {
            java.util.Objects.requireNonNull(info); java.util.Objects.requireNonNull(validation);
            if (info.isEmpty() && validation.valid()) throw new IllegalArgumentException("Missing NBT cannot be valid");
        }
    }
    /** Caller supplies a finite NBT allocation quota. The input and decompressor are always closed. */
    public static Inspection read(Path path, long maxBytes) {
        java.util.Objects.requireNonNull(path);
        if (maxBytes <= 0) throw new IllegalArgumentException("Positive NBT byte quota required");
        try (var raw = Files.newInputStream(path);
             var input = new DataInputStream(new GZIPInputStream(raw))) {
            return inspect(NbtIo.read(input, new NbtAccounter(maxBytes)));
        } catch (NoSuchFileException error) {
            return failure(Code.MISSING_NBT, path.toString(), error.toString());
        } catch (IOException | RuntimeException error) {
            return failure(Code.INVALID_NBT_DATA, path.toString(), error.toString());
        }
    }
    private static Inspection failure(Code code, String path, String message) {
        return new Inspection(Optional.empty(), new StructureValidationResult(List.of(error(code, path, message))));
    }
    public static Inspection inspect(CompoundTag root) {
        try {
            int[] size = triple(root, "size", Code.INVALID_NBT_SIZE);
            if (size[0]<=0 || size[1]<=0 || size[2]<=0) throw new Invalid(Code.INVALID_NBT_SIZE, "size", "All dimensions must be positive");
            var bounds = new Bounds3i(0,0,0,size[0],size[1],size[2]);
            var issues = new ArrayList<StructureValidationIssue>();
            List<ListTag> palettes = new ArrayList<>();
            if (root.contains("palettes")) {
                var alternatives = list(root, "palettes", Tag.TAG_LIST);
                for (Tag alternative : alternatives) {
                    var palette = (ListTag) alternative;
                    if (!palette.isEmpty() && palette.getElementType()!=Tag.TAG_COMPOUND)
                        throw new Invalid(Code.INVALID_NBT_DATA, "palettes", "Expected compound palette entries");
                    palettes.add(palette);
                }
            } else palettes.add(list(root, "palette", Tag.TAG_COMPOUND));
            if (palettes.isEmpty()) throw new Invalid(Code.INVALID_NBT_DATA, "palettes", "No palette");
            var blocks = list(root, "blocks", Tag.TAG_COMPOUND);
            if (blocks.size()>1_000_000) throw new Invalid(Code.INVALID_NBT_DATA, "blocks", "WG-03 inspection limit is 1000000 cells");
            Set<BlockPos> positions = new HashSet<>(), nonAir = new HashSet<>();
            long air=0, be=0, multi=0;
            boolean forbidden=false;
            for (int i=0; i<blocks.size(); i++) {
                CompoundTag block = blocks.getCompound(i);
                int[] pos = triple(block, "pos", Code.INVALID_NBT_DATA);
                var p = new BlockPos(pos[0],pos[1],pos[2]);
                if (!bounds.contains(p.getX(),p.getY(),p.getZ()) || !positions.add(p))
                    throw new Invalid(Code.INVALID_NBT_DATA, "blocks["+i+"].pos", "Out of size or duplicate coordinate");
                if (!block.contains("state",Tag.TAG_INT)) throw new Invalid(Code.INVALID_NBT_DATA, "state", "Expected integer palette index");
                int state=block.getInt("state");
                boolean allAir=true, isMulti=false;
                for (ListTag palette : palettes) {
                    if (state<0 || state>=palette.size()) throw new Invalid(Code.INVALID_NBT_DATA, "state", "Palette index outside palette");
                    CompoundTag entry=palette.getCompound(state);
                    if (!entry.contains("Name",Tag.TAG_STRING)) throw new Invalid(Code.INVALID_NBT_DATA, "palette.Name", "Missing block name");
                    String name=entry.getString("Name");
                    if (net.minecraft.resources.ResourceLocation.tryParse(name)==null)
                        throw new Invalid(Code.INVALID_NBT_DATA, "palette.Name", "Invalid block identifier");
                    allAir &= AIR.contains(name);
                    forbidden |= FORBIDDEN.contains(name);
                    // Conservative known pair-state markers, not a completeness or survival proof.
                    CompoundTag properties=entry.getCompound("Properties");
                    isMulti |= properties.contains("half") || properties.contains("part");
                }
                if (allAir) air++; else nonAir.add(p);
                if (isMulti) multi++;
                if (block.contains("nbt")) {
                    if (!block.contains("nbt",Tag.TAG_COMPOUND)) throw new Invalid(Code.INVALID_NBT_DATA, "block.nbt", "Expected compound");
                    be++;
                }
            }
            if (be>0) issues.add(warning(Code.BE_REQUIRES_GAME_QA,"blocks.nbt","Block entity records="+be+"; contents/rotation not game-tested"));
            if (multi>0) issues.add(warning(Code.MULTIBLOCK_REQUIRES_GAME_QA,"palette.Properties","Pair/half state cells="+multi+"; completeness not game-tested"));
            if (forbidden) issues.add(error(Code.FORBIDDEN_NBT_CONTENT,"palette","Control/debug/structure-void blocks are not supported"));
            if (root.contains("entities") && !list(root,"entities",Tag.TAG_COMPOUND).isEmpty())
                issues.add(error(Code.FORBIDDEN_NBT_CONTENT,"entities","V1 asset contract does not import entities"));
            return new Inspection(Optional.of(new Info(bounds,blocks.size(),air,be,multi,nonAir)),new StructureValidationResult(issues));
        } catch (Invalid invalid) {
            return new Inspection(Optional.empty(),new StructureValidationResult(List.of(invalid.issue())));
        }
    }
    private static ListTag list(CompoundTag root, String key, int type) {
        if (!(root.get(key) instanceof ListTag value) || (!value.isEmpty() && value.getElementType()!=type))
            throw new Invalid(Code.INVALID_NBT_DATA,key,"Wrong or missing list type");
        return value;
    }
    private static int[] triple(CompoundTag root,String key,Code code) {
        if (!(root.get(key) instanceof ListTag value) || value.size()!=3 || value.getElementType()!=Tag.TAG_INT)
            throw new Invalid(code,key,"Expected exactly three NBT integers");
        return new int[]{value.getInt(0),value.getInt(1),value.getInt(2)};
    }
}
