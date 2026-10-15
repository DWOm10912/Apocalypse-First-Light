package com.antaurora.apofirstlight.meshhit;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.CabinPieces;
import com.antaurora.apofirstlight.cabin.WoodFurniture;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hit meshes of the blocks drawn from cabin pieces (CabinPieces: the Cabin Exterior V1 blocks, the wood furniture, the box
 * stove; docs/rendering/mesh_hit_runtime_v1.md "Cabin pieces"): the pieces the block draws for its state and neighbour
 * flags, read from the jar's cabin_mesh JSON, turned and merged into one model, so the crosshair picks the drawn surface and
 * the selection outline is the mesh's own edges (a roof's slope, the round logs) instead of the collision boxes (user
 * 2026-10-13: the outline must be the mesh). Both sides build the same.
 * <p>Not the gun rack: its guns are drawn by its block entity and are no part of the pieces, so a ray at a gun would pass
 * through to the wall behind and the slot under the crosshair could not be taken; it keeps its box.
 */
final class CabinMeshHits {
    private record Key(BlockState state, int flags) {}

    /** Per piece: its triangles (9 floats each), drawn for the north side. */
    private static final Map<String, Optional<float[]>> PIECES = new ConcurrentHashMap<>();
    private static final Map<Key, Optional<MeshHitModels.Shape>> LOOKS = new ConcurrentHashMap<>();

    private CabinMeshHits() {
    }

    static boolean covers(BlockState state) {
        return state.getBlock() instanceof CabinPieces && !(state.getBlock() instanceof WoodFurniture.GunRack);
    }

    @Nullable
    static MeshHitModels.Shape shape(BlockGetter level, BlockPos pos, BlockState state) {
        CabinPieces block = (CabinPieces) state.getBlock();
        Key key = new Key(state, block.cabinFlags(state, level, pos));
        return LOOKS.computeIfAbsent(key, k -> build(block, k)).orElse(null);
    }

    private static Optional<MeshHitModels.Shape> build(CabinPieces block, Key key) {
        List<float[]> parts = new ArrayList<>();
        List<Integer> turns = new ArrayList<>();
        List<Float> lifts = new ArrayList<>();
        block.cabinPieces(key.state(), key.flags(), (name, turn, dy) -> {
            float[] tri = piece(name);
            if (tri == null) return;
            parts.add(tri);
            turns.add(Math.floorMod(turn, 4));
            lifts.add(dy);
        });
        int total = 0;
        for (float[] t : parts) total += t.length;
        if (total == 0) return Optional.empty();
        float[] out = new float[total];
        int at = 0;
        for (int i = 0; i < parts.size(); i++) {
            float[] t = parts.get(i);
            int r = turns.get(i);
            float dy = lifts.get(i);
            for (int j = 0; j + 2 < t.length; j += 3) {
                float x = t[j], z = t[j + 2];
                for (int k = 0; k < r; k++) { float w = x; x = 1.0F - z; z = w; }   // a clockwise quarter: (x, z) to (1 - z, x)
                out[at++] = x;
                out[at++] = t[j + 1] + dy;
                out[at++] = z;
            }
        }
        MeshHitModel model = MeshHitModel.of(out);
        return model == null ? Optional.empty() : Optional.of(new MeshHitModels.Shape(List.of(new MeshHitModels.Placed(model, new Quaternionf(), new Quaternionf()))));
    }

    /** A piece's triangles: each quad {@code [tex, (x, y, z, u, v, nx, ny, nz) x 4]} split in two, degenerate ones dropped. */
    @Nullable
    private static float[] piece(String name) {
        return PIECES.computeIfAbsent(name, k -> Optional.ofNullable(read(k))).orElse(null);
    }

    @Nullable
    private static float[] read(String name) {
        String path = "/assets/" + ApocalypseFirstLight.MOD_ID + "/cabin_mesh/" + name + ".json";
        try (InputStream in = CabinMeshHits.class.getResourceAsStream(path)) {
            if (in == null) return null;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray quads = root.getAsJsonArray("quads");
            float[] out = new float[quads.size() * 18];
            int at = 0;
            float[][] p = new float[4][3];
            for (int i = 0; i < quads.size(); i++) {
                JsonArray q = quads.get(i).getAsJsonArray();
                for (int k = 0; k < 4; k++) for (int c = 0; c < 3; c++) p[k][c] = q.get(1 + k * 8 + c).getAsFloat();
                for (int[] t : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                    float[] a = p[t[0]], b = p[t[1]], c = p[t[2]];
                    double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
                    double cx = uy * wz - uz * wy, cy = uz * wx - ux * wz, cz = ux * wy - uy * wx;
                    if (cx * cx + cy * cy + cz * cz < 1e-14) continue;
                    for (float[] v : new float[][]{a, b, c}) for (float f : v) out[at++] = f;
                }
            }
            return java.util.Arrays.copyOf(out, at);
        } catch (Exception e) {
            ApocalypseFirstLight.LOGGER.error("[AFL MESH HIT] could not read cabin piece {}", name, e);
            return null;
        }
    }
}
