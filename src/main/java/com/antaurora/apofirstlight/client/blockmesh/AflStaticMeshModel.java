package com.antaurora.apofirstlight.client.blockmesh;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile.Part;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile.Transform;
import com.antaurora.apofirstlight.blockmesh.AflMeshChunkData;
import com.antaurora.apofirstlight.client.AflRenderDev;
import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.antaurora.apofirstlight.client.mesh.AflMeshModel;
import com.antaurora.apofirstlight.client.mesh.AflMeshPart;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The chunk model of AFL animated mesh blocks (docs/dev/render_performance_v1.md, step 2): the resting parts that
 * {@link AflMeshChunking} hands it through the block entity's model data, as ordinary chunk quads. Model JSON:
 * {@code {"loader": "apocalypse_firstlight:static_mesh", "textures": {"particle": ...}}}; the block returns
 * {@code RenderShape.MODEL}. The profile, facing and parts come with the model data (only the cell holding the block
 * entity has any), so one loader serves every asset and every cell.
 * <p>
 * The quads match the block entity renderer: the same transform (block centre, facing, profile origin and scale, then
 * each part's pivot, rest pose and settled animation pose), the mesh UVs mapped into the profile texture's block atlas
 * sprite ({@code ns:textures/block/x.png} is sprite {@code ns:block/x}), a back face for every part that is not a closed
 * solid (the renderer draws without face culling), flat lighting (no ambient occlusion, like the renderer), cutout layer
 * (no mipmaps without shaders, like the renderer's texture). Shading: with a shader pack, white and the chunk's own
 * shading (the pack lights both paths); without one, the renderer's entity diffuse lighting baked into the vertex colour
 * so the parts match those the renderer still draws.
 * <p>
 * A part is left to the renderer when its UVs leave the texture (an atlas sprite cannot repeat) or it reaches more than
 * {@link #REACH} block outside its cell (Embeddium frustum-tests a chunk section with a 1.125 block margin); the chunk
 * reports the parts it really drew ({@link AflMeshChunking#confirm}). Built once per variant and resource generation and
 * cached; read on the chunk builder threads, it only touches immutable snapshots, never the level or a block entity.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AflStaticMeshModel {
    public static final String LOADER = "static_mesh";
    private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(RenderType.cutout());
    /** UVs may sit this far outside 0..1 (exporter rounding); further out the face would need a repeating texture */
    private static final float UV_SLACK = 1e-3F;
    /** how far chunk geometry may reach outside its cell */
    static final float REACH = 1.1F;
    private static final int CACHE_LIMIT = 512;
    private static final Vector3f L0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize(), L1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize(),
            NETHER_L1 = new Vector3f(-0.2F, -1.0F, 0.7F).normalize();

    private record Built(List<BakedQuad> quads, long lo, long hi) {}
    private static final Built NOTHING = new Built(List.of(), 0, 0);
    private static final Map<AflMeshChunking.Variant, Built> BUILT = new ConcurrentHashMap<>();
    private static volatile long builtGeneration = Long.MIN_VALUE;
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private AflStaticMeshModel() {}

    @SubscribeEvent
    public static void registerLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(LOADER, Loader.INSTANCE);
        AflMeshChunkData.install(AflMeshChunking::modelData);
    }

    /** The block's current model is this model. */
    public static boolean serves(BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state) instanceof Baked;
    }

    private enum Loader implements IGeometryLoader<Geometry> {
        INSTANCE;

        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            return new Geometry();
        }
    }

    private record Geometry() implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                               ModelState state, ItemOverrides overrides, ResourceLocation location) {
            if (!context.hasMaterial("particle")) throw new JsonParseException(location + ": static_mesh needs the texture \"particle\"");
            return new Baked(sprites.apply(context.getMaterial("particle")));
        }
    }

    private static final class Baked implements IDynamicBakedModel {
        private final TextureAtlasSprite particle;

        Baked(TextureAtlasSprite particle) {
            this.particle = particle;
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random,
                                                 @NotNull ModelData data, @Nullable RenderType renderType) {
            if (side != null || !AflRenderDev.staticMesh()) return List.of();
            if (renderType != null && renderType != RenderType.cutout()) return List.of();
            AflMeshChunking.Snapshot snapshot = data.get(AflMeshChunking.SNAPSHOT);
            if (snapshot == null || snapshot.variant().empty()) return List.of();
            Built built = built(snapshot.variant());
            AflMeshChunking.confirm(snapshot, built.lo(), built.hi());
            return built.quads();
        }

        @Override
        public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random, @NotNull ModelData data) {
            return LAYERS;
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public @NotNull TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    private static Built built(AflMeshChunking.Variant variant) {
        long generation = AflMeshCache.snapshot().generation();
        if (generation != builtGeneration || BUILT.size() > CACHE_LIMIT) { BUILT.clear(); builtGeneration = generation; }
        return BUILT.computeIfAbsent(variant, AflStaticMeshModel::build);
    }

    /** The variant's parts in block space, exactly as AflAnimatedBlockMeshRenderer places them at rest. */
    private static Built build(AflMeshChunking.Variant v) {
        AflBlockMeshProfile profile = v.profile();
        AflMeshModel mesh = AflMeshCache.snapshot().get(profile.geometry());
        if (mesh == null) return NOTHING;
        ResourceLocation texture = profile.texture();
        String path = texture.getPath();
        if (!path.startsWith("textures/") || !path.endsWith(".png")) return NOTHING;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(new ResourceLocation(texture.getNamespace(), path.substring(9, path.length() - 4)));
        if (sprite == null || sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
            warn(profile + ":texture", "{}: texture {} is not in the block atlas; drawn by the block entity renderer", profile.geometry(), texture);
            return NOTHING;
        }
        var layout = AflMeshChunking.layout(profile, mesh);
        var root = new Matrix4f().translate(0.5F, 0, 0.5F);
        if (profile.horizontalFacing()) root.rotateY((float) Math.toRadians(AflBlockMeshProfile.facingDegrees(v.facing())));
        root.translate((float) profile.origin().x, (float) profile.origin().y, (float) profile.origin().z);
        root.scale((float) profile.scale().x, (float) profile.scale().y, (float) profile.scale().z);
        int n = layout.parts.length;
        Matrix4f[] poses = new Matrix4f[n];
        var out = new ArrayList<BakedQuad>();
        long lo = 0, hi = 0;
        for (int i = 0; i < n; i++) {
            Part part = layout.parts[i];
            int parent = layout.parent[i];
            Matrix4f base = parent < 0 ? root : poses[parent];
            Vec3 parentPivot = parent < 0 ? Vec3.ZERO : layout.parts[parent].pivot();
            int c = layout.channel[i];
            double t = c >= 0 && (v.ones() >>> c & 1) != 0 ? 1 : 0;
            poses[i] = pose(base, part, parentPivot, t);
            if (!v.has(i)) continue;
            int start = out.size();
            String problem = emit(mesh.parts(part.bone(), AflMeshPart.Layer.CUTOUT), poses[i], sprite, v.shading(), out);
            if (problem != null) {
                out.subList(start, out.size()).clear();
                warn(profile + ":" + part.bone() + ":" + v.facing(), "{} facing {}: part {} {}; drawn by the block entity renderer",
                        profile.geometry(), v.facing(), part.bone(), problem);
                continue;
            }
            if (i < 64) lo |= 1L << i; else hi |= 1L << (i - 64);
        }
        return new Built(List.copyOf(out), lo, hi);
    }

    private static void warn(String key, String message, Object... args) {
        if (WARNED.add(key)) ApocalypseFirstLight.LOGGER.warn("[AFL static mesh] " + message, args);
    }

    /** AflAnimatedBlockMeshRenderer#drawPart's transform with the channel at t (0 or 1). */
    private static Matrix4f pose(Matrix4f parent, Part part, Vec3 parentPivot, double t) {
        Transform rest = part.rest();
        Transform target = part.motion() == null ? Transform.IDENTITY : part.motion().target();
        var m = new Matrix4f(parent).translate(
                (float) (part.pivot().x - parentPivot.x + rest.translation().x + target.translation().x * t),
                (float) (part.pivot().y - parentPivot.y + rest.translation().y + target.translation().y * t),
                (float) (part.pivot().z - parentPivot.z + rest.translation().z + target.translation().z * t));
        // the renderer's rotate(): Z, then Y, then X
        double rx = rest.rotation().x + target.rotation().x * t, ry = rest.rotation().y + target.rotation().y * t,
                rz = rest.rotation().z + target.rotation().z * t;
        if (rz != 0) m.rotateZ((float) Math.toRadians(rz));
        if (ry != 0) m.rotateY((float) Math.toRadians(ry));
        if (rx != 0) m.rotateX((float) Math.toRadians(rx));
        m.scale((float) (rest.scale().x * (1 + (target.scale().x - 1) * t)), (float) (rest.scale().y * (1 + (target.scale().y - 1) * t)),
                (float) (rest.scale().z * (1 + (target.scale().z - 1) * t)));
        return m;
    }

    /**
     * The faces of one part at pose m, as AflMeshRenderer#renderPartsAtCurrentPose writes them: ABCD for quads, ABCC for
     * triangles, the boundary reversed under a mirroring pose, the first corner's normal for the whole face. Faces of an
     * open part also get their back side (reversed boundary, reversed normal). Vertex layout: DefaultVertexFormat.BLOCK.
     * Null, or why the part cannot be baked.
     */
    private static String emit(List<AflMeshPart> parts, Matrix4f m, TextureAtlasSprite sprite, int shading, List<BakedQuad> out) {
        if (parts.isEmpty()) return null;
        float determinant = m.determinant3x3();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-12F) return null;   // zero scale: nothing to draw
        boolean mirrored = determinant < 0;
        var normalMatrix = new Matrix3f(m).invert().transpose();
        float u0 = sprite.getU0(), du = sprite.getU1() - u0, v0 = sprite.getV0(), dv = sprite.getV1() - v0;
        var position = new Vector3f();
        var normal = new Vector3f();
        int[] order = new int[4];
        float[] x = new float[4], y = new float[4], z = new float[4], u = new float[4], v = new float[4];
        for (var part : parts) {
            boolean back = !part.closed();
            for (int face = 0; face < part.faceCount(); face++) {
                int start = part.faceStart(face), size = part.faceSize(face);
                normal.set(part.value(start, 5), part.value(start, 6), part.value(start, 7));
                normalMatrix.transform(normal);
                float length = normal.length();
                if (!Float.isFinite(length) || length < 1e-12F) continue;
                normal.div(length);
                for (int corner = 0; corner < 4; corner++) {
                    int k = Math.min(corner, size - 1);
                    order[corner] = start + (mirrored && k > 0 ? size - k : k);
                }
                for (int corner = 0; corner < 4; corner++) {
                    int index = order[corner];
                    float tu = part.value(index, 3), tv = part.value(index, 4);
                    if (tu < -UV_SLACK || tu > 1 + UV_SLACK || tv < -UV_SLACK || tv > 1 + UV_SLACK) return "has UVs outside its texture";
                    position.set(part.value(index, 0), part.value(index, 1), part.value(index, 2));
                    m.transformPosition(position);
                    if (outside(position.x) || outside(position.y) || outside(position.z))
                        return "reaches more than " + REACH + " block beyond its cell";
                    x[corner] = position.x; y[corner] = position.y; z[corner] = position.z;
                    u[corner] = u0 + du * Math.max(0, Math.min(1, tu));
                    v[corner] = v0 + dv * Math.max(0, Math.min(1, tv));
                }
                out.add(quad(new int[]{0, 1, 2, 3}, x, y, z, u, v, normal.x, normal.y, normal.z, sprite, shading));
                if (back) out.add(quad(new int[]{0, 3, 2, 1}, x, y, z, u, v, -normal.x, -normal.y, -normal.z, sprite, shading));
            }
        }
        return null;
    }

    private static boolean outside(float c) {
        return !Float.isFinite(c) || c < -REACH || c > 1 + REACH;
    }

    /** The entity shader's diffuse light for a world-space normal (Lighting.setupLevel / setupNetherLevel, minecraft_mix_light). */
    private static int color(float nx, float ny, float nz, int shading) {
        if (shading == 0) return -1;
        Vector3f second = shading == 2 ? NETHER_L1 : L1;
        float light = Math.min(1.0F, (Math.max(0.0F, L0.x * nx + L0.y * ny + L0.z * nz)
                + Math.max(0.0F, second.x * nx + second.y * ny + second.z * nz)) * 0.6F + 0.4F);
        int c = Math.round(light * 255.0F);
        return 0xFF000000 | c << 16 | c << 8 | c;
    }

    private static BakedQuad quad(int[] corners, float[] x, float[] y, float[] z, float[] u, float[] v,
                                  float nx, float ny, float nz, TextureAtlasSprite sprite, int shading) {
        int[] data = new int[32];
        int packedNormal = (((byte) (nx * 127)) & 0xFF) | ((((byte) (ny * 127)) & 0xFF) << 8) | ((((byte) (nz * 127)) & 0xFF) << 16);
        int color = color(nx, ny, nz, shading);
        for (int i = 0; i < 4; i++) {
            int c = corners[i], o = i * 8;
            data[o] = Float.floatToRawIntBits(x[c]);
            data[o + 1] = Float.floatToRawIntBits(y[c]);
            data[o + 2] = Float.floatToRawIntBits(z[c]);
            data[o + 3] = color;                               // ABGR
            data[o + 4] = Float.floatToRawIntBits(u[c]);
            data[o + 5] = Float.floatToRawIntBits(v[c]);
            data[o + 6] = 0;                                   // lightmap: the chunk renderer's
            data[o + 7] = packedNormal;
        }
        // direction: the flat lighter's light face (a face on the cell's boundary plane takes the neighbour's light);
        // shade only with a shader pack (without one the entity diffuse light is in the colour);
        // no ambient occlusion: smooth lighting clamps corners into the cell, wrong for geometry outside it
        return new BakedQuad(data, -1, Direction.getNearest(nx, ny, nz), sprite, shading == 0, false);
    }
}
