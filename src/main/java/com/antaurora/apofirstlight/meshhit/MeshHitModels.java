package com.antaurora.apofirstlight.meshhit;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hit meshes by block state (docs/rendering/mesh_hit_runtime_v1.md): every block of this mod whose state resolves (its
 * blockstate JSON, or {@link MeshHitProvider}) to Forge OBJ models has one, unless listed in {@link #EXCLUDED}. Models
 * load on first use and stay (they come from the jar, not a resource pack); both sides build the same.
 */
public final class MeshHitModels {
    /** Blocks (registry paths) that keep their collision shape for rays though they have OBJ models. */
    private static final Set<String> EXCLUDED = Set.of();

    private static final Map<ResourceLocation, Optional<MeshHitModel>> MODELS = new ConcurrentHashMap<>();
    private static final Map<BlockState, Optional<Shape>> BY_STATE = new ConcurrentHashMap<>();
    private static final Map<List<ResourceLocation>, Optional<Shape>> BY_PIECES = new ConcurrentHashMap<>();

    private MeshHitModels() {
    }

    /** A model placed in its block: turned about the block's centre as the blockstate turns it (x first, then y). */
    record Placed(MeshHitModel model, Quaternionf toWorld, Quaternionf toModel) {}

    /** The models drawing one block state. */
    public static final class Shape {
        final List<Placed> parts;

        Shape(List<Placed> parts) {
            this.parts = List.copyOf(parts);
        }

        public int triangles() {
            int n = 0;
            for (Placed p : parts) n += p.model.triangles();
            return n;
        }

        /** The nearest surface along {@code from -> to} (world), or null: the segment misses every part. */
        @Nullable
        public MeshBlockHitResult clip(Vec3 from, Vec3 to, BlockPos pos) {
            double best = 1.0;
            Vec3 normal = null;
            for (Placed p : parts) {
                Vector3f o = new Vector3f((float) (from.x - pos.getX() - 0.5), (float) (from.y - pos.getY() - 0.5), (float) (from.z - pos.getZ() - 0.5));
                Vector3f d = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
                p.toModel.transform(o);
                p.toModel.transform(d);
                double[] hit = p.model.intersect(o.x + 0.5, o.y + 0.5, o.z + 0.5, d.x, d.y, d.z, best);
                if (hit == null || hit[0] >= best) continue;
                best = hit[0];
                Vector3f n = new Vector3f((float) hit[1], (float) hit[2], (float) hit[3]);
                p.toWorld.transform(n);
                normal = new Vec3(n.x, n.y, n.z).normalize();
            }
            if (normal == null) return null;
            return new MeshBlockHitResult(from.add(to.subtract(from).scale(best)), normal, pos.immutable());
        }
    }

    /** The hit mesh of the block at {@code pos}, or null (not this mod's, no OBJ models, excluded). */
    @Nullable
    public static Shape shape(BlockGetter level, BlockPos pos, BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !ApocalypseFirstLight.MOD_ID.equals(id.getNamespace()) || EXCLUDED.contains(id.getPath())) return null;
        if (state.getBlock() instanceof MeshHitProvider provider) {
            List<ResourceLocation> pieces = provider.meshHitModels(level, pos, state);
            return BY_PIECES.computeIfAbsent(List.copyOf(pieces), MeshHitModels::ofPieces).orElse(null);
        }
        return BY_STATE.computeIfAbsent(state, MeshHitModels::ofState).orElse(null);
    }

    private static Optional<Shape> ofState(BlockState state) {
        List<Placed> parts = new ArrayList<>();
        for (BlockstateMeshResolver.Part part : BlockstateMeshResolver.parts(state)) {
            MeshHitModel model = model(part.model());
            if (model == null) continue;
            // as vanilla's BlockModelRotation: rotateYXZ(-y, -x, 0) about the block's centre
            Quaternionf toWorld = new Quaternionf().rotateYXZ((float) Math.toRadians(-part.y()), (float) Math.toRadians(-part.x()), 0.0F);
            parts.add(new Placed(model, toWorld, new Quaternionf(toWorld).conjugate()));
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of(new Shape(parts));
    }

    private static Optional<Shape> ofPieces(List<ResourceLocation> pieces) {
        List<Placed> parts = new ArrayList<>();
        for (ResourceLocation piece : pieces) {
            MeshHitModel model = model(piece);
            if (model != null) parts.add(new Placed(model, new Quaternionf(), new Quaternionf()));
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of(new Shape(parts));
    }

    @Nullable
    static MeshHitModel model(ResourceLocation id) {
        return MODELS.computeIfAbsent(id, k -> Optional.ofNullable(MeshHitModel.load(k))).orElse(null);
    }
}
