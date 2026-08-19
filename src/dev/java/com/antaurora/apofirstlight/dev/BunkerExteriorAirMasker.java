package com.antaurora.apofirstlight.dev;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BunkerExteriorAirMasker {
    private static final String AIR = "minecraft:air";
    private static final String STRUCTURE_VOID = "minecraft:structure_void";
    private static final int SPAWN_X = 5;
    private static final int SPAWN_Y = 1;
    private static final int SPAWN_Z = 9;

    private BunkerExteriorAirMasker() {
    }

    public static Result mask(MinecraftServer server, String inputName, String outputName, boolean overwrite)
            throws IOException {
        Path runDirectory = server.getFile(".").toPath().toAbsolutePath().normalize();
        Path projectRoot = runDirectory.getParent();
        if (projectRoot == null || !Files.isRegularFile(projectRoot.resolve("build.gradle"))) {
            throw new IOException("Unable to resolve AFL project root");
        }

        Path root = projectRoot.resolve("src/main/resources/data/apocalypse_firstlight/structures").normalize();
        Path input = safeResolve(root, inputName.endsWith(".nbt") ? inputName : inputName + ".nbt");
        Path output = safeResolve(root, outputName.endsWith(".nbt") ? outputName : outputName + ".nbt");
        if (!Files.isRegularFile(input)) {
            throw new IOException("Input structure not found: " + input);
        }
        if (Files.exists(output) && !overwrite) {
            throw new IOException("Target already exists. Use --overwrite to replace it.");
        }

        CompoundTag structure = NbtIo.readCompressed(input.toFile());
        Parsed parsed = parse(structure);
        long spawnKey = key(SPAWN_X, SPAWN_Y, SPAWN_Z, parsed.sizeX, parsed.sizeZ);
        String spawnState = parsed.stateName(spawnKey);
        if (!AIR.equals(spawnState)) {
            throw new IOException("Spawn anchor (5,1,9) must be minecraft:air, found " + spawnState);
        }

        Set<Long> exterior = floodFill(parsed);
        if (exterior.contains(spawnKey)) {
            throw new IOException("INTERIOR LEAK DETECTED: Spawn anchor is connected to exterior air. "
                    + "Bunker shell/entrance is open; exterior masking aborted.");
        }

        int totalAir = parsed.airCount;
        int exteriorCount = exterior.size();
        int interiorCount = totalAir - exteriorCount;
        if (interiorCount <= 0) {
            throw new IOException("Safety check failed: no interior air remains");
        }

        ListTag filteredBlocks = new ListTag();
        int removedExteriorAir = 0;
        for (int index = 0; index < parsed.blocks.size(); index++) {
            CompoundTag block = parsed.blocks.getCompound(index);
            ListTag pos = block.getList("pos", 3);
            long position = key(pos.getInt(0), pos.getInt(1), pos.getInt(2), parsed.sizeX, parsed.sizeZ);
            if (exterior.contains(position)) {
                if (block.contains("nbt", 10)) {
                    throw new IOException("Exterior AIR entry contains block entity NBT at "
                            + pos.getInt(0) + "," + pos.getInt(1) + "," + pos.getInt(2));
                }
                if (!AIR.equals(parsed.stateName(position))) {
                    throw new IOException("Exterior set contains a non-AIR block at " + position);
                }
                removedExteriorAir++;
                continue;
            }
            filteredBlocks.add(block.copy());
        }
        if (removedExteriorAir != exteriorCount) {
            throw new IOException("Exterior positions without removable block entries: "
                    + (exteriorCount - removedExteriorAir));
        }
        if (!filteredBlocksContainsAir(filteredBlocks, parsed, spawnKey)) {
            throw new IOException("Spawn anchor block entry was removed or is no longer minecraft:air");
        }
        structure.put("blocks", filteredBlocks);

        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(structure, temporary.toFile());
            CompoundTag check = NbtIo.readCompressed(temporary.toFile());
            validateWritten(check, parsed, totalAir, parsed.originalStructureVoidCount,
                    parsed.blocks.size(), removedExteriorAir);
            try {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }

        return new Result(input.getFileName().toString(), output.getFileName().toString(), parsed.sizeX, parsed.sizeY,
                parsed.sizeZ, parsed.blocks.size(), filteredBlocks.size(), parsed.palette.size(), totalAir,
                exteriorCount, interiorCount, removedExteriorAir,
                parsed.originalStructureVoidCount, parsed.originalStructureVoidCount,
                parsed.blocksWithNbt, parsed.entitiesCount, structure.getInt("DataVersion"));
    }

    private static Parsed parse(CompoundTag structure) throws IOException {
        if (!structure.contains("DataVersion", 3) || !structure.contains("size", 9)
                || !structure.contains("palette", 9) || !structure.contains("blocks", 9)
                || !structure.contains("entities", 9)) {
            throw new IOException("Structure NBT is missing DataVersion, size, palette, blocks, or entities");
        }
        ListTag size = structure.getList("size", 3);
        if (size.size() != 3) {
            throw new IOException("size must be a ListTag<IntTag> with exactly three elements");
        }
        int sx = size.getInt(0), sy = size.getInt(1), sz = size.getInt(2);
        if (sx <= 0 || sy <= 0 || sz <= 0) {
            throw new IOException("size dimensions must be positive");
        }

        ListTag palette = structure.getList("palette", 10);
        if (palette.isEmpty()) {
            throw new IOException("palette is empty");
        }
        ListTag blocks = structure.getList("blocks", 10);
        Map<Long, CompoundTag> byPosition = new HashMap<>();
        int air = 0;
        int voids = 0;
        int blockEntities = 0;
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            ListTag pos = block.getList("pos", 3);
            if (pos.size() != 3) {
                throw new IOException("blocks[" + index + "].pos must be a ListTag<IntTag> with exactly three elements");
            }
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) {
                throw new IOException("blocks[" + index + "].pos is outside size bounds");
            }
            if (!block.contains("state", 3)) {
                throw new IOException("blocks[" + index + "] is missing integer state");
            }
            long key = key(x, y, z, sx, sz);
            if (byPosition.put(key, block) != null) {
                throw new IOException("Duplicate block position: " + x + "," + y + "," + z);
            }
            String stateName = palette.getCompound(block.getInt("state")).getString("Name");
            if (AIR.equals(stateName)) air++;
            if (STRUCTURE_VOID.equals(stateName)) voids++;
            if (block.contains("nbt", 10)) blockEntities++;
        }
        int entities = structure.getList("entities", 10).size();
        return new Parsed(structure, palette, blocks, byPosition, sx, sy, sz, air, voids, blockEntities, entities);
    }

    private static Set<Long> floodFill(Parsed parsed) throws IOException {
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> exterior = new HashSet<>();
        for (int index = 0; index < parsed.blocks.size(); index++) {
            CompoundTag block = parsed.blocks.getCompound(index);
            ListTag pos = block.getList("pos", 3);
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            if ((x == 0 || x == parsed.sizeX - 1 || y == 0 || y == parsed.sizeY - 1
                    || z == 0 || z == parsed.sizeZ - 1)
                    && AIR.equals(parsed.stateName(key(x, y, z, parsed.sizeX, parsed.sizeZ)))) {
                long position = key(x, y, z, parsed.sizeX, parsed.sizeZ);
                if (exterior.add(position)) queue.add(position);
            }
        }
        int[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            long current = queue.remove();
            int x = x(current, parsed.sizeX, parsed.sizeZ);
            int y = y(current, parsed.sizeX, parsed.sizeZ);
            int z = z(current, parsed.sizeX, parsed.sizeZ);
            for (int[] direction : directions) {
                int nx = x + direction[0], ny = y + direction[1], nz = z + direction[2];
                if (nx < 0 || nx >= parsed.sizeX || ny < 0 || ny >= parsed.sizeY || nz < 0 || nz >= parsed.sizeZ) continue;
                long next = key(nx, ny, nz, parsed.sizeX, parsed.sizeZ);
                if (AIR.equals(parsed.stateName(next)) && exterior.add(next)) queue.add(next);
            }
        }
        return exterior;
    }

    private static boolean filteredBlocksContainsAir(ListTag blocks, Parsed parsed, long spawnKey) throws IOException {
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            ListTag pos = block.getList("pos", 3);
            long position = key(pos.getInt(0), pos.getInt(1), pos.getInt(2), parsed.sizeX, parsed.sizeZ);
            if (position == spawnKey) {
                return AIR.equals(parsed.stateName(position));
            }
        }
        return false;
    }

    private static void validateWritten(CompoundTag written, Parsed parsed, int originalAir,
                                        int originalVoid, int originalBlocks, int removed) throws IOException {
        Parsed check = parse(written);
        if (check.sizeX != parsed.sizeX || check.sizeY != parsed.sizeY || check.sizeZ != parsed.sizeZ
                || check.blocks.size() != originalBlocks - removed
                || check.entitiesCount != parsed.entitiesCount
                || check.originalStructureVoidCount != originalVoid
                || check.airCount != originalAir - removed
                || written.getInt("DataVersion") != parsed.structure.getInt("DataVersion")) {
            throw new IOException("Written structure validation failed");
        }
    }

    private static Path safeResolve(Path root, String value) throws IOException {
        if (value.isBlank() || value.indexOf('\0') >= 0 || value.contains(":")
                || value.startsWith("/") || value.startsWith("\\")) {
            throw new IOException("Invalid structure path");
        }
        Path candidate = root.resolve(value).normalize();
        if (!candidate.startsWith(root)) throw new IOException("Structure path must remain inside the structure directory");
        return candidate;
    }

    private static long key(int x, int y, int z, int sizeX, int sizeZ) {
        return x + (long) sizeX * (z + (long) sizeZ * y);
    }

    private static int x(long key, int sizeX, int sizeZ) {
        return (int) (key % sizeX);
    }

    private static int y(long key, int sizeX, int sizeZ) {
        return (int) (key / ((long) sizeX * sizeZ));
    }

    private static int z(long key, int sizeX, int sizeZ) {
        return (int) ((key / sizeX) % sizeZ);
    }

    private static final class Parsed {
        private final CompoundTag structure;
        private final ListTag palette;
        private final ListTag blocks;
        private final Map<Long, CompoundTag> blockByPosition;
        private final int sizeX, sizeY, sizeZ, airCount, originalStructureVoidCount, blocksWithNbt, entitiesCount;

        private Parsed(CompoundTag structure, ListTag palette, ListTag blocks, Map<Long, CompoundTag> blockByPosition,
                       int sizeX, int sizeY, int sizeZ, int airCount, int structureVoidCount,
                       int blocksWithNbt, int entitiesCount) {
            this.structure = structure;
            this.palette = palette;
            this.blocks = blocks;
            this.blockByPosition = blockByPosition;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.airCount = airCount;
            this.originalStructureVoidCount = structureVoidCount;
            this.blocksWithNbt = blocksWithNbt;
            this.entitiesCount = entitiesCount;
        }

        private String stateName(long position) throws IOException {
            CompoundTag block = blockByPosition.get(position);
            if (block == null) throw new IOException("Missing block entry at " + position);
            int state = block.getInt("state");
            if (state < 0 || state >= palette.size()) throw new IOException("Invalid palette index: " + state);
            String name = palette.getCompound(state).getString("Name");
            if (name.isBlank()) throw new IOException("Palette entry " + state + " has no block Name");
            return name;
        }

    }

    public record Result(String input, String output, int sizeX, int sizeY, int sizeZ, int originalBlocks,
                         int finalBlocks, int palette, int totalAir, int exteriorAir, int interiorAir,
                         int removedExteriorAir, int originalStructureVoid, int finalStructureVoid,
                         int blockEntities, int entities, int dataVersion) {
        public String summary() {
            return "Exterior Air Mask complete | Input: " + input + " | Output: " + output
                    + " | Size: " + sizeX + " x " + sizeY + " x " + sizeZ
                    + " | Total AIR: " + totalAir + " | Exterior AIR detected: " + exteriorAir
                    + " | Exterior AIR entries removed: " + removedExteriorAir
                    + " | Interior AIR preserved: " + interiorAir + " | Existing Structure Void: "
                    + originalStructureVoid + " | Final Structure Void: " + finalStructureVoid
                    + " | Spawn anchor (5,1,9): SAFE";
        }
    }
}
