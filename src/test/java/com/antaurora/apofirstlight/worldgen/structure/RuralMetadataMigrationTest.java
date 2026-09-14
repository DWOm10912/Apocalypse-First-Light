package com.antaurora.apofirstlight.worldgen.structure;

import com.antaurora.apofirstlight.worldgen.rural.RuralRecipe;
import com.antaurora.apofirstlight.worldgen.rural.RuralStructureCatalog;
import com.antaurora.apofirstlight.worldgen.rural.RuralStructurePool;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/** WG-07 bundled-resource contract; still not a graphical or real-noise world QA. */
public final class RuralMetadataMigrationTest {
    private static final String ROOT = "/data/apocalypse_firstlight/";
    private static final List<String> ASSETS = List.of("rural_barn_large_01", "rural_farmhouse_01",
            "rural_farmhouse_02", "rural_grain_silo_01", "rural_house_small_01",
            "rural_house_small_02", "rural_storage_small_01", "rural_water_tower_01");
    private static final Map<String, Integer> ANCHORS = Map.of("rural_barn_large_01", 0,
            "rural_farmhouse_01", 0, "rural_farmhouse_02", 0, "rural_grain_silo_01", 1,
            "rural_house_small_01", 1, "rural_house_small_02", 0,
            "rural_storage_small_01", 0, "rural_water_tower_01", 0);
    private static final Map<String, BlockPos> NEW_SOCKETS = Map.of(
            "rural_farmhouse_02", new BlockPos(9, 0, 16),
            "rural_house_small_02", new BlockPos(8, 1, 10));

    private RuralMetadataMigrationTest() { }

    public static void main(String[] args) throws Exception {
        RuralStructureCatalog catalog = RuralStructurePool.catalog();
        RuralRecipe recipe = catalog.recipe();
        require(recipe.entries().size() == 6, "Legacy recipe must have six entries");
        require(catalog.legacyDefinitions().size() == 6, "Legacy adapter must have six definitions");
        require(catalog.legacyDefinitions().get(0) == RuralStructurePool.FARMHOUSE
                && catalog.legacyDefinitions().get(1) == RuralStructurePool.BARN, "Required legacy identities changed");
        require(recipe.entries().stream().noneMatch(e -> e.asset().getPath().endsWith("_02")),
                "New variant entered Natural recipe");
        int validated = 0;
        for (String name : ASSETS) {
            ResourceLocation id = new ResourceLocation("apocalypse_firstlight", name);
            byte[] bytes = read(ROOT + "structures/" + name + ".nbt");
            StructureNbtReader.Inspection nbt = StructureNbtReader.inspect(NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes)));
            require(nbt.validation().valid() && nbt.info().isPresent(), name + " NBT invalid: " + nbt.validation().issues());
            String json = new String(read(ROOT + "afl_worldgen/structures/" + name + ".json"), StandardCharsets.UTF_8);
            var loaded = StructureDefinitionLoader.validate(id, json, nbt);
            require(loaded.definition().isPresent(), name + " metadata invalid: " + loaded.validation().issues());
            StructureDefinition asset = loaded.definition().orElseThrow();
            require(asset.equals(catalog.metadata(id)), name + " catalog metadata differs from packaged metadata");
            require(asset.front() == Direction.SOUTH && asset.groundAnchorOffsetY() == ANCHORS.get(name),
                    name + " Legacy front/anchor changed");
            require(asset.allowedRotations().containsAll(List.of(Rotation.values())), name + " rotations incomplete");
            require(asset.assetRevision().equals("sha256:" + sha256(bytes)), name + " revision does not match NBT");
            RuralStructurePool.Definition adapter = catalog.legacyDefinition(id);
            if (NEW_SOCKETS.containsKey(name)) {
                require(adapter == null, name + " entered legacy adapter");
                require(asset.sockets().size() == 1, name + " must have exactly one primary socket");
                StructureSocket socket = asset.sockets().get(0);
                require(socket.name().equals("main") && socket.localPosition().equals(NEW_SOCKETS.get(name))
                        && socket.facing() == Direction.SOUTH && socket.type() == StructureSocketType.PEDESTRIAN,
                        name + " differs from user-confirmed socket");
                for (Rotation rotation : Rotation.values()) verifyRotatedSocket(name, nbt.info().orElseThrow(), socket, rotation);
            } else {
                require(adapter != null && adapter.frontDirection() == asset.front()
                        && adapter.groundAnchorOffsetY() == asset.groundAnchorOffsetY(), name + " adapter mismatch");
            }
            System.out.println("WG07_ASSET|" + name + "|NBT=" + nbt.info().orElseThrow().size()
                    + "|front=" + asset.front() + "|anchor=" + asset.groundAnchorOffsetY()
                    + "|rotations=" + asset.allowedRotations().size() + "|sockets=" + asset.sockets()
                    + "|BE=" + nbt.info().orElseThrow().blockEntityCount()
                    + "|status=VALID_WITH_WARNINGS");
            validated++;
        }
        require(validated == 8, "Expected eight publishable metadata assets");
        System.out.println("PASS WG-07.1 metadata validated=" + validated + "/8 four_rotation_new_variants=8 recipe=6");
    }

    private static void verifyRotatedSocket(String name, StructureNbtReader.Info nbt,
                                            StructureSocket socket, Rotation rotation) {
        BlockPos rotated = StructureTransform.local(socket.localPosition(), rotation);
        Direction facing = StructureTransform.facing(socket.facing(), rotation);
        var bounds = StructureTransform.bounds(nbt.size(), rotation, BlockPos.ZERO);
        require(bounds.contains(rotated.getX(), rotated.getY(), rotated.getZ()),
                name + " socket outside rotated bounds for " + rotation);
        boolean outward = switch (facing) {
            case NORTH -> rotated.getZ() == bounds.minZ();
            case SOUTH -> rotated.getZ() == bounds.maxZExclusive() - 1;
            case WEST -> rotated.getX() == bounds.minX();
            case EAST -> rotated.getX() == bounds.maxXExclusive() - 1;
            default -> false;
        };
        require(outward, name + " rotated socket does not face outside for " + rotation);
        require(StructureTransform.facing(Direction.SOUTH, rotation) == facing,
                name + " rotated front/socket facing mismatch for " + rotation);
    }

    private static byte[] read(String path) throws Exception {
        try (InputStream input = RuralMetadataMigrationTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing resource " + path);
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
