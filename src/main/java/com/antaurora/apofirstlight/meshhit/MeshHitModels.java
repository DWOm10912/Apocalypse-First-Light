package com.antaurora.apofirstlight.meshhit;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
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
 * blockstate JSON, or {@link MeshHitProvider}) to Forge OBJ models has one, unless listed in {@link #EXCLUDED}; so does
 * every AFL animated mesh block (its block entity an AflAnimatedMeshHost, AnimatedMeshHits). Every cell of a
 * {@link MeshHitMultiCell} block has its master's whole model. Models load on first use and stay (they come from the jar,
 * not a resource pack); both sides build the same.
 */
public final class MeshHitModels {
    /** Blocks (registry paths) that keep their collision shape for rays though they have OBJ models. */
    private static final Set<String> EXCLUDED = Set.of();

    private static final Map<ResourceLocation, Optional<MeshHitModel>> MODELS = new ConcurrentHashMap<>();
    private static final Map<BlockState, Optional<Shape>> BY_STATE = new ConcurrentHashMap<>();
    private static final Map<List<ResourceLocation>, Optional<Shape>> BY_PIECES = new ConcurrentHashMap<>();
    private static final Map<List<MeshHitAssembled.Piece>, Optional<Shape>> ASSEMBLED = new ConcurrentHashMap<>();

    private MeshHitModels() {
    }

    /** A model placed in its block: turned about the block's centre as the blockstate turns it (x first, then y). */
    record Placed(MeshHitModel model, Quaternionf toWorld, Quaternionf toModel) {}

    /** The models drawing one block state; their origin is the block's cell, or {@link #moved} for another cell of it. */
    public static final class Shape {
        final List<Placed> parts;
        /** The models' cell less the asked cell (MeshHitMultiCell: the master's offset). */
        final int dx, dy, dz;

        Shape(List<Placed> parts) {
            this(parts, 0, 0, 0);
        }

        private Shape(List<Placed> parts, int dx, int dy, int dz) {
            this.parts = List.copyOf(parts);
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        /** The same models, seen from the cell {@code (dx, dy, dz)} away from their own (back from the master). */
        Shape moved(int dx, int dy, int dz) {
            return new Shape(parts, dx, dy, dz);
        }

        public int triangles() {
            int n = 0;
            for (Placed p : parts) n += p.model.triangles();
            return n;
        }

        /** Each feature edge of every part, in block-local coordinates (0..1 for the asked cell). */
        public void forEachEdge(EdgeConsumer out) {
            Vector3f a = new Vector3f(), b = new Vector3f();
            for (Placed p : parts) {
                float[] e = p.model.edges();
                for (int i = 0; i + 5 < e.length; i += 6) {
                    a.set(e[i] - 0.5F, e[i + 1] - 0.5F, e[i + 2] - 0.5F);
                    b.set(e[i + 3] - 0.5F, e[i + 4] - 0.5F, e[i + 5] - 0.5F);
                    p.toWorld.transform(a);
                    p.toWorld.transform(b);
                    out.accept(a.x + 0.5F + dx, a.y + 0.5F + dy, a.z + 0.5F + dz, b.x + 0.5F + dx, b.y + 0.5F + dy, b.z + 0.5F + dz);
                }
            }
        }

        /** The nearest surface along {@code from -> to} (world), or null: the segment misses every part. Its cell is {@code pos}. */
        @Nullable
        public MeshBlockHitResult clip(Vec3 from, Vec3 to, BlockPos pos) {
            double best = 1.0;
            Vec3 normal = null;
            for (Placed p : parts) {
                Vector3f o = new Vector3f((float) (from.x - pos.getX() - dx - 0.5), (float) (from.y - pos.getY() - dy - 0.5), (float) (from.z - pos.getZ() - dz - 0.5));
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

    /** One feature edge (client/MeshHitOutline). */
    @FunctionalInterface
    public interface EdgeConsumer {
        void accept(float x0, float y0, float z0, float x1, float y1, float z1);
    }

    /**
     * The hit mesh of the block at {@code pos}, or null (not this mod's, no OBJ models, excluded, an animated part still
     * moving).
     */
    @Nullable
    public static Shape shape(BlockGetter level, BlockPos pos, BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !ApocalypseFirstLight.MOD_ID.equals(id.getNamespace()) || EXCLUDED.contains(id.getPath())) return null;
        if (state.getBlock() instanceof MeshHitMultiCell multi) {
            BlockPos master = multi.meshHitMaster(state, pos);
            if (!master.equals(pos)) {
                BlockState drawn = level.getBlockState(master);
                if (drawn.getBlock() != state.getBlock()) return null;   // a broken structure: the boxes
                Shape whole = own(level, master, drawn);
                return whole == null ? null : whole.moved(master.getX() - pos.getX(), master.getY() - pos.getY(), master.getZ() - pos.getZ());
            }
        }
        return own(level, pos, state);
    }

    /**
     * {@code hit} (from {@code pos}'s shape) given to the cell of the same block it landed in: a multi-cell block's model
     * reaches over its other cells, and a bullet hole or the outline belongs to the cell it is in. Unchanged when that
     * cell is another block (a part reaching out of the structure).
     */
    public static MeshBlockHitResult inCell(BlockGetter level, MeshBlockHitResult hit, BlockState state) {
        if (!(state.getBlock() instanceof MeshHitMultiCell)) return hit;
        Vec3 inside = hit.getLocation().subtract(hit.normal().scale(1.0E-4));   // just under the surface
        BlockPos cell = BlockPos.containing(inside);
        if (cell.equals(hit.getBlockPos()) || level.getBlockState(cell).getBlock() != state.getBlock()) return hit;
        return new MeshBlockHitResult(hit.getLocation(), hit.normal(), cell);
    }

    @Nullable
    private static Shape own(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity() && level.getBlockEntity(pos) instanceof AflAnimatedMeshHost host) return AnimatedMeshHits.shape(host);
        if (state.getBlock() instanceof MeshHitAssembled assembled)
            return ASSEMBLED.computeIfAbsent(List.copyOf(assembled.meshHitPieces(level, pos, state)), MeshHitModels::merged).orElse(null);
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

    /** The pieces turned about the block's centre (quarter turns, as BlockModelRotation y) and merged into one model. */
    private static Optional<Shape> merged(List<MeshHitAssembled.Piece> pieces) {
        List<Float> out = new ArrayList<>();
        for (MeshHitAssembled.Piece piece : pieces) {
            MeshHitModel model = model(piece.model());
            if (model == null) continue;
            float[] t = model.tri();
            int turns = Math.floorMod(piece.y() / 90, 4);
            for (int i = 0; i + 2 < t.length; i += 3) {
                float x = t[i], z = t[i + 2];
                for (int k = 0; k < turns; k++) { float w = x; x = 1.0F - z; z = w; }   // north to east: (x, z) to (1 - z, x)
                out.add(x);
                out.add(t[i + 1]);
                out.add(z);
            }
        }
        if (out.isEmpty()) return Optional.empty();
        float[] tri = new float[out.size()];
        for (int i = 0; i < tri.length; i++) tri[i] = out.get(i);
        return Optional.of(new Shape(List.of(new Placed(MeshHitModel.of(tri), new Quaternionf(), new Quaternionf()))));
    }

    @Nullable
    static MeshHitModel model(ResourceLocation id) {
        return MODELS.computeIfAbsent(id, k -> Optional.ofNullable(MeshHitModel.load(k))).orElse(null);
    }
}
