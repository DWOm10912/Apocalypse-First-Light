package com.antaurora.apofirstlight.dev;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.forge.internal.NBTConverter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SchemToVanillaStructureConverter {
    private SchemToVanillaStructureConverter() {
    }

    public static Result convert(MinecraftServer server, String inputName, String outputName, boolean overwrite)
            throws IOException {
        Path runDirectory = server.getFile(".").toPath().toAbsolutePath().normalize();
        Path projectRoot = runDirectory.getParent();
        if (projectRoot == null || !Files.isRegularFile(projectRoot.resolve("build.gradle"))) {
            throw new IOException("Unable to resolve AFL project root");
        }

        Path schematicRoot = runDirectory.resolve("config").resolve("worldedit").resolve("schematics").normalize();
        Path outputRoot = projectRoot.resolve("src/main/resources/data/apocalypse_firstlight/structures").normalize();
        String inputFileName = inputName.endsWith(".schem") ? inputName : inputName + ".schem";
        Path input = safeResolve(schematicRoot, inputFileName);
        Path output = safeResolve(outputRoot, outputName.endsWith(".nbt") ? outputName : outputName + ".nbt");

        if (!Files.isRegularFile(input)) {
            throw new IOException("Input schematic not found: " + input);
        }
        if (Files.exists(output) && !overwrite) {
            throw new IOException("Target already exists. Use --overwrite to replace it.");
        }
        Files.createDirectories(output.getParent());

        ClipboardFormat format = ClipboardFormats.findByFile(input.toFile());
        if (format == null) {
            throw new IOException("Unsupported or invalid schematic format: " + input.getFileName());
        }

        Result result;
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(input));
             ClipboardReader reader = format.getReader(stream)) {
            result = convertClipboard(reader.read(), input.getFileName().toString(), output.getFileName().toString());
        }

        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(result.root(), temporary.toFile());
            CompoundTag check = NbtIo.readCompressed(temporary.toFile());
            validate(check);
            try {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        return result;
    }

    private static Result convertClipboard(Clipboard clipboard, String inputName, String outputName) throws IOException {
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        BlockVector3 dimensions = clipboard.getDimensions();
        List<BlockVector3> positions = new ArrayList<>();
        for (BlockVector3 position : clipboard.getRegion()) {
            positions.add(position);
        }
        positions.sort(Comparator.comparingInt(BlockVector3::getY)
                .thenComparingInt(BlockVector3::getZ)
                .thenComparingInt(BlockVector3::getX));

        Map<BlockState, Integer> paletteIndices = new HashMap<>();
        List<BlockState> paletteStates = new ArrayList<>();
        ListTag blocks = new ListTag();
        int airCount = 0;
        int structureVoidCount = 0;
        int blockEntityCount = 0;

        for (BlockVector3 position : positions) {
            BaseBlock fullBlock = clipboard.getFullBlock(position);
            BlockState state;
            try {
                state = ForgeAdapter.adapt(fullBlock.toImmutableState());
            } catch (RuntimeException exception) {
                throw new IOException("Unable to adapt block at " + position + ": " + fullBlock, exception);
            }
            Integer paletteIndex = paletteIndices.get(state);
            if (paletteIndex == null) {
                paletteIndex = paletteStates.size();
                paletteIndices.put(state, paletteIndex);
                paletteStates.add(state);
            }

            CompoundTag block = new CompoundTag();
            block.put("pos", intList(position.getX() - min.getX(), position.getY() - min.getY(), position.getZ() - min.getZ()));
            block.putInt("state", paletteIndex);
            if (fullBlock.hasNbtData()) {
                CompoundTag nbt = NBTConverter.toNative(fullBlock.getNbtData());
                block.put("nbt", nbt);
                blockEntityCount++;
            }
            blocks.add(block);

            String id = state.getBlock().builtInRegistryHolder().key().location().toString();
            if (id.equals("minecraft:air")) airCount++;
            if (id.equals("minecraft:structure_void")) structureVoidCount++;
        }

        ListTag palette = new ListTag();
        for (BlockState state : paletteStates) {
            palette.add(NbtUtils.writeBlockState(state));
        }
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.put("size", intList(dimensions.getX(), dimensions.getY(), dimensions.getZ()));
        root.put("palette", palette);
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        int entityCount = clipboard.getEntities().size();
        return new Result(root, inputName, outputName, dimensions, positions.size(), paletteStates.size(), blockEntityCount,
                airCount, structureVoidCount, entityCount);
    }

    private static Path safeResolve(Path root, String value) throws IOException {
        if (value.isBlank() || value.indexOf('\0') >= 0) {
            throw new IOException("Invalid empty path");
        }
        Path candidate = root.resolve(value).normalize();
        if (!candidate.startsWith(root) || value.contains(":") || value.startsWith("/") || value.startsWith("\\")) {
            throw new IOException("Path must remain inside the permitted development directory");
        }
        return candidate;
    }

    private static void validate(CompoundTag root) throws IOException {
        if (!root.contains("DataVersion") || !root.contains("size") || !root.contains("palette")
                || !root.contains("blocks") || !root.contains("entities")) {
            throw new IOException("Generated structure NBT is missing required fields");
        }
        validateIntTripleList(root.get("size"), "size");
        ListTag blocks = root.getList("blocks", 10);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            validateIntTripleList(block.get("pos"), "blocks[" + index + "].pos");
        }
    }

    private static ListTag intList(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));
        list.add(IntTag.valueOf(y));
        list.add(IntTag.valueOf(z));
        return list;
    }

    private static void validateIntTripleList(net.minecraft.nbt.Tag tag, String path) throws IOException {
        if (!(tag instanceof ListTag list) || list.getElementType() != 3 || list.size() != 3) {
            throw new IOException(path + " must be a ListTag<IntTag> with exactly three elements");
        }
    }

    public record Result(CompoundTag root, String inputName, String outputName, BlockVector3 size, int blocks,
                         int uniqueStates, int blockEntities, int airBlocks, int structureVoidBlocks, int entities) {
        public String summary() {
            return "Converted " + inputName + " -> " + outputName + " | Size: " + size.getX() + " x " + size.getY()
                    + " x " + size.getZ() + " | Blocks: " + blocks + " | Unique states: " + uniqueStates
                    + " | BlockEntities: " + blockEntities + " | Air blocks: " + airBlocks
                    + " | Structure Void blocks: " + structureVoidBlocks + " | Entities skipped: " + entities;
        }
    }
}
