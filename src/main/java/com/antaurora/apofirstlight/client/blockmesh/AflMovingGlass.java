package com.antaurora.apofirstlight.client.blockmesh;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FastVertices;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The glass the animated mesh renderer draws itself (a door or lid in motion, or not yet back in the chunk) while a shader
 * pack is on (2026-10-08). Drawn by the block entity renderer, glass goes through Oculus' block entity program; Sundial has
 * no gbuffers_block_translucent, so it fell back to the opaque gbuffers_block and showed a dusk-coloured glow instead of the
 * glass the chunk draws (user video, A1 storefront door at night). Entity glass has the same problem (2026-10-16, the T15:
 * Sundial Lite has no translucent entity program either), so entity renderers queue here as well; the T15 queues here
 * without a shader pack too (its entity glass, flushed before its riders, hid them behind shut windows). Oculus maps the terrain translucent shaders to
 * gbuffers_water in every phase but entities and block entities, so this glass is queued during the block entity pass, at
 * the renderer's view-space pose, and drawn after the translucent terrain as {@link RenderType#translucent()} with the block
 * atlas sprite: the same program, PBR maps and blending as the chunk glass and the Storefront Glazing.
 * <p>
 * Queued straight as the translucent type's vertex bytes (2026-10-18): each part's faces are read once into a model-frame
 * copy ({@link #faces}), and a frame only turns them by the pose. It used to bake every face into a new BakedQuad each
 * frame (AflStaticMeshModel.emit) and read them back, about 1 ms a frame for the T15's glass (docs/rendering/rain_v1.md
 * section 3.5). The vertices are the ones emit made: the same corner order (mirrored poses too), UVs, rounded normals.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AflMovingGlass {
    /** The queued glass: RenderType.translucent()'s format (BLOCK: position, colour, UV, light, normal; 32 bytes a vertex). */
    private static final FastVertices QUEUED = new FastVertices(1 << 16);
    private static final int VERTEX = 32;
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(1 << 16));
    private static final BufferBuilder BUILDER = new BufferBuilder(1 << 16);
    private static final float UV_SLACK = 1e-3F;

    /**
     * A part's faces in its model frame: per face {size, back, nx, ny, nz} then four corners {x, y, z, u, v} (corner k is
     * the face's corner min(k, size - 1), as emit reads them). {@link #BAD} when a UV lies outside the texture.
     */
    private static final Map<MeshPart, float[]> FACES = new WeakHashMap<>();
    private static final float[] BAD = new float[0];
    private static final int FACE = 25;

    private AflMovingGlass() {}

    private static float[] faces(MeshPart part) {
        float[] f = FACES.get(part);
        if (f != null) return f;
        f = new float[part.faceCount() * FACE];
        boolean back = !part.closed();
        for (int face = 0; face < part.faceCount(); face++) {
            int start = part.faceStart(face), size = part.faceSize(face), o = face * FACE;
            f[o] = size;
            f[o + 1] = back ? 1 : 0;
            f[o + 2] = part.value(start, 5);
            f[o + 3] = part.value(start, 6);
            f[o + 4] = part.value(start, 7);
            for (int corner = 0; corner < 4; corner++) {
                int index = start + Math.min(corner, size - 1), c = o + 5 + corner * 5;
                float tu = part.value(index, 3), tv = part.value(index, 4);
                if (tu < -UV_SLACK || tu > 1 + UV_SLACK || tv < -UV_SLACK || tv > 1 + UV_SLACK) {
                    FACES.put(part, BAD);
                    return BAD;
                }
                f[c] = part.value(index, 0);
                f[c + 1] = part.value(index, 1);
                f[c + 2] = part.value(index, 2);
                f[c + 3] = Math.max(0, Math.min(1, tu));
                f[c + 4] = Math.max(0, Math.min(1, tv));
            }
        }
        FACES.put(part, f);
        return f;
    }

    /** Which stored corner each drawn corner is: as is, or (a mirrored pose) the winding turned (emit's order). */
    private static final int[] AS_IS = {0, 1, 2, 3}, MIRROR_QUAD = {0, 3, 2, 1}, MIRROR_TRIANGLE = {0, 2, 1, 1};
    private static final Vector3f P = new Vector3f(), N = new Vector3f();
    private static final Matrix3f NORMALS = new Matrix3f();
    private static final float[] X = new float[4], Y = new float[4], Z = new float[4], U = new float[4], V = new float[4];

    /**
     * Queues one part's glass at pose (view space, the renderer's current pose); false when it cannot be drawn this way (a
     * UV outside the texture; nothing is queued then). Also used by entity renderers (PickupTruckRenderer: the T15's
     * glass), whose pose is view space too.
     */
    public static boolean queue(List<MeshPart> parts, Matrix4f pose, TextureAtlasSprite sprite, int light) {
        if (parts.isEmpty()) return true;
        float determinant = pose.determinant3x3();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-12F) return true;   // zero scale: nothing to draw
        boolean mirrored = determinant < 0;
        Matrix3f normals = NORMALS.set(pose).invert().transpose();
        float u0 = sprite.getU0(), du = sprite.getU1() - u0, v0 = sprite.getV0(), dv = sprite.getV1() - v0;
        int start = QUEUED.bytes();
        for (MeshPart part : parts) {
            float[] f = faces(part);
            if (f == BAD) {
                QUEUED.truncate(start);
                return false;
            }
            for (int o = 0; o < f.length; o += FACE) {
                int size = (int) f[o];
                normals.transform(N.set(f[o + 2], f[o + 3], f[o + 4]));
                float length = N.length();
                if (!Float.isFinite(length) || length < 1e-12F) continue;
                N.div(length);
                int[] order = !mirrored ? AS_IS : size == 4 ? MIRROR_QUAD : MIRROR_TRIANGLE;
                for (int corner = 0; corner < 4; corner++) {
                    int c = o + 5 + order[corner] * 5;
                    pose.transformPosition(P.set(f[c], f[c + 1], f[c + 2]));
                    X[corner] = P.x;
                    Y[corner] = P.y;
                    Z[corner] = P.z;
                    U[corner] = u0 + du * f[c + 3];
                    V[corner] = v0 + dv * f[c + 4];
                }
                put(AS_IS, N.x, N.y, N.z, light);
                if (f[o + 1] != 0) put(MIRROR_QUAD, -N.x, -N.y, -N.z, light);
            }
        }
        return true;
    }

    /** One quad of the corners in X..V (in this order), its normal rounded as a baked quad's (a signed byte each). */
    private static void put(int[] corners, float nx, float ny, float nz, int light) {
        int normal = FastVertices.normal((byte) (nx * 127) / 127.0F, (byte) (ny * 127) / 127.0F, (byte) (nz * 127) / 127.0F);
        long p = QUEUED.take(4 * VERTEX);
        for (int i = 0; i < 4; i++, p += VERTEX) {
            int c = corners[i];
            FastVertices.f(p, X[c]);
            FastVertices.f(p + 4, Y[c]);
            FastVertices.f(p + 8, Z[c]);
            FastVertices.i(p + 12, 0xFFFFFFFF);
            FastVertices.f(p + 16, U[c]);
            FastVertices.f(p + 20, V[c]);
            FastVertices.i(p + 24, light);
            FastVertices.i(p + 28, normal);
        }
    }

    /** A frame whose stage never came (no world drawn) leaves nothing behind. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) QUEUED.reset();
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || QUEUED.bytes() == 0) return;
        RenderType type = RenderType.translucent();
        // positions are view space already (the renderer's pose); the normal is the baked one (view space too)
        if (!AflShaderCompat.shaderPackInUse()) {
            // no shader pack: the queued bytes into an own builder in one copy, drawn (sorted) as the buffer source would.
            // The source's builder is no help here: Embeddium hands out its wrapper (SodiumBufferBuilder), which takes
            // vertex by vertex (2026-10-18, rain_v1.md section 3.5). With a pack Oculus widens the format, so it stays below.
            BUILDER.begin(type.mode(), type.format());
            QUEUED.into(BUILDER);
            type.end(BUILDER, RenderSystem.getVertexSorting());
            return;
        }
        VertexConsumer out = BUFFERS.getBuffer(type);
        if (out instanceof BufferBuilder b) QUEUED.into(b);
        else {
            for (int o = 0; o < QUEUED.bytes(); o += VERTEX) {
                long p = QUEUED.address(o);
                int normal = MemoryUtil.memGetInt(p + 28);
                out.vertex(MemoryUtil.memGetFloat(p), MemoryUtil.memGetFloat(p + 4), MemoryUtil.memGetFloat(p + 8),
                        1.0F, 1.0F, 1.0F, 1.0F, MemoryUtil.memGetFloat(p + 16), MemoryUtil.memGetFloat(p + 20),
                        OverlayTexture.NO_OVERLAY, MemoryUtil.memGetInt(p + 24),
                        (byte) normal / 127.0F, (byte) (normal >> 8) / 127.0F, (byte) (normal >> 16) / 127.0F);
            }
            QUEUED.reset();
        }
        BUFFERS.endBatch(type);
    }
}
