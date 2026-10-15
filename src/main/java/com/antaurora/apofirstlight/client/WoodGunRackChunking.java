package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.CabinMeshModel;
import com.antaurora.apofirstlight.cabin.WoodFurniture;
import com.antaurora.apofirstlight.cabin.WoodGunRackBlockEntity;
import com.antaurora.apofirstlight.client.blockmesh.AflStaticMeshModel;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.vextris.client.VextrisMeshes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Wood Furniture V1 (docs/models/wood_furniture_v1.md, "性能"): the chunk draws what hangs on a gun rack.
 * <p>
 * A rack's guns rest until its contents change, but a mesh gun is 11,000 to 14,000 vertices, and the renderer re-submitting
 * five of them every frame (twice with a shader pack, for its shadow) cost 7 + 7 ms a frame under Sundial (2026-10-13). So
 * when the contents change, each slot is drawn once into a recorder ({@link WoodGunRackRenderer#slot}, the renderer's own
 * drawing), and what it gets is handed to the rack's cabin chunk model through the block entity's model data
 * ({@link CabinMeshModel#EXTRA}): block-space quads in the cutout layer, flat lit from the cell, texture coordinates moved
 * into the block atlas (the gun textures are already there: the vanilla block atlas takes every textures/item png).
 * <ul>
 * <li>Into the chunk: draws whose render type is an entity cutout or solid type of the block atlas or of a texture that is a
 * block atlas sprite, quad by quad as long as the quad stays within {@link #REACH} of the cell and its texture
 * coordinates within the texture. Faces of a closed mesh part as they are; all others also get their back side (the
 * renderer draws without face culling). Light baked in by the drawing (a sight's full bright reticle) is kept.</li>
 * <li>Kept by the renderer (replayed from the recording each frame): everything else, e.g. translucent or glint layers
 * (these not in a shader pack's shadow pass).</li>
 * <li>Shading as the static mesh chunk path ({@link AflStaticMeshModel#diffuse}): with a shader pack plain, the pack lights
 * it; without one the renderer's entity light in the vertex colour.</li>
 * <li>Confirmation: the chunk model reports each copy it meshes; the renderer leaves a slot to the chunk only when the copy
 * the chunk has had {@link #CONFIRM_FRAMES} frames to show holds the same stack at the same facing, so a gun is never
 * missing (a gun taken down may stay up to a few frames).</li>
 * </ul>
 * Recorded on the client thread when the chunk asks for the model data or the renderer sees the contents changed, never
 * in a shadow pass (a mesh skips its translucent parts there). Off with the development switch {@code static_mesh off}
 * ({@link AflRenderDev}): the renderer then draws everything, as before.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WoodGunRackChunking {
    private static final int CONFIRM_FRAMES = 4;
    /** How far chunk geometry may reach outside its cell (AflStaticMeshModel.REACH: the chunk section frustum margin). */
    private static final float REACH = 1.1F, UV_SLACK = 1e-3F;
    private static final Pattern TEXTURE = Pattern.compile("texture\\[Optional\\[([^\\]]+)\\]");
    private static final Map<WoodGunRackBlockEntity, Host> HOSTS = Collections.synchronizedMap(new WeakHashMap<>());
    /** Per render type: the texture whose sprite its UVs address (the block atlas itself for atlas types), or null (renderer). */
    private static final Map<RenderType, Kind> KINDS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile long kindsGeneration = Long.MIN_VALUE;
    private static volatile long frame;

    private WoodGunRackChunking() {}

    private static final class Host {
        Copy requested;
        volatile ModelData data;
        volatile Shown newest, previous;
        long seq;
    }

    private record Shown(Copy copy, long frame) {}

    /** How a render type's draws go: into the chunk (with the sprite its texture is, or atlas UVs) or to the renderer. */
    private record Kind(boolean chunk, boolean atlasUvs, @Nullable TextureAtlasSprite sprite, boolean shadow) {}

    private static final Kind RENDERER = new Kind(false, false, null, false);

    // ------------------------------------------------------------------------------------------------ the copy

    /** The renderer's leftovers of one draw type in one slot: block-space vertices {x, y, z, u, v, nx, ny, nz}, colours, lights. */
    private record Leftover(RenderType type, boolean shadow, float[] vertices, int[] colors, int[] lights) {}

    /** One recording of a rack's contents: what the chunk draws and what the renderer keeps, per slot. */
    static final class Copy implements CabinMeshModel.Extra {
        /** Its host, not the block entity: the host map is weak in the block entity, and model data keeps copies alive. */
        private final Host host;
        final long seq, generation;
        final Direction facing;
        final ItemStack[] stacks;
        final Leftover[][] leftovers;
        /** Per quad: DefaultVertexFormat.BLOCK data with the drawn colour (ABGR) and its sprite. */
        private final int[][] quads;
        private final TextureAtlasSprite[] sprites;
        @SuppressWarnings("unchecked")
        private final List<BakedQuad>[] baked = new List[3];

        Copy(Host host, long seq, long generation, Direction facing, ItemStack[] stacks, Leftover[][] leftovers,
             List<int[]> quads, List<TextureAtlasSprite> sprites) {
            this.host = host;
            this.seq = seq;
            this.generation = generation;
            this.facing = facing;
            this.stacks = stacks;
            this.leftovers = leftovers;
            this.quads = quads.toArray(int[][]::new);
            this.sprites = sprites.toArray(TextureAtlasSprite[]::new);
        }

        @Override
        public List<BakedQuad> meshCutout() {
            if (!AflRenderDev.staticMesh() || generation != VextrisMeshes.generation()) return List.of();
            if (!MirrorReflection.meshing()) confirm(this);   // an empty copy too: the chunk no longer shows the old guns
            int shading = shading();
            List<BakedQuad> list = baked[shading];
            if (list == null) baked[shading] = list = bake(shading);
            return list;
        }

        private List<BakedQuad> bake(int shading) {
            var out = new ArrayList<BakedQuad>(quads.length);
            for (int q = 0; q < quads.length; q++) {
                int[] data = quads[q].clone();
                float fx = 0, fy = 0, fz = 0;
                for (int i = 0; i < 4; i++) {
                    int o = i * 8, n = data[o + 7];
                    float nx = (byte) n / 127.0F, ny = (byte) (n >> 8) / 127.0F, nz = (byte) (n >> 16) / 127.0F;
                    fx += nx; fy += ny; fz += nz;
                    if (shading == 0) continue;
                    float light = AflStaticMeshModel.diffuse(nx, ny, nz, shading);
                    int c = data[o + 3];
                    int r = Math.round((c & 0xFF) * light), g = Math.round((c >> 8 & 0xFF) * light), b = Math.round((c >> 16 & 0xFF) * light);
                    data[o + 3] = c & 0xFF000000 | b << 16 | g << 8 | r;
                }
                // direction: the flat lighter's light face; shade only with a shader pack (else the entity light is in the
                // colour); no ambient occlusion (smooth light clamps corners into the cell, wrong for geometry outside it)
                out.add(new BakedQuad(data, -1, Direction.getNearest(fx, fy, fz), sprites[q], shading == 0, false));
            }
            return List.copyOf(out);
        }

        /** The chunk draws slot k for this stack at this facing (the renderer replays only its leftovers). */
        boolean has(int k, ItemStack stack, Direction facing) {
            return this.facing == facing && generation == VextrisMeshes.generation() && sameStack(stacks[k], stack);
        }

        /** Slot k's leftovers at the renderer's pose (the block's origin); drawn light where the drawing left it unset. */
        void replay(int k, PoseStack pose, MultiBufferSource buffers, int light, boolean shadowPass) {
            WoodGunRackChunking.replay(leftovers[k], pose, buffers, light, shadowPass);
        }
    }

    /** Recorded draws at the pose; drawn light where the drawing left it unset; in a shadow pass only those that cast one. */
    private static void replay(Leftover[] parts, PoseStack pose, MultiBufferSource buffers, int light, boolean shadowPass) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (Leftover l : parts) {
            if (shadowPass && !l.shadow()) continue;
            VertexConsumer out = buffers.getBuffer(l.type());
            float[] v = l.vertices();
            for (int i = 0, c = 0; i < v.length; i += 8, c++) {
                float x = v[i], y = v[i + 1], z = v[i + 2], nx = v[i + 5], ny = v[i + 6], nz = v[i + 7];
                int color = l.colors()[c];
                out.vertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                        m.m02() * x + m.m12() * y + m.m22() * z + m.m32(),
                        (color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, (color >>> 24) / 255.0F,
                        v[i + 3], v[i + 4], OverlayTexture.NO_OVERLAY, l.lights()[c] == 0 ? light : l.lights()[c],
                        n.m00() * nx + n.m10() * ny + n.m20() * nz, n.m01() * nx + n.m11() * ny + n.m21() * nz,
                        n.m02() * nx + n.m12() * ny + n.m22() * nz);
            }
        }
    }

    /**
     * A drawing recorded once and replayed at any pose (the ground guns, {@link GroundItemRenderer}): a mesh gun's bind pose
     * re-walked every frame costs its whole bone tree; the replay only moves the recorded vertices. Draws of opaque and
     * cutout entity types cast a shadow in a shader pack's shadow pass, the rest (translucent, glint) do not.
     */
    static final class Replay {
        private final Leftover[] parts;
        final long generation;

        private Replay(Leftover[] parts, long generation) {
            this.parts = parts;
            this.generation = generation;
        }

        void draw(PoseStack pose, MultiBufferSource buffers, int light, boolean shadowPass) {
            replay(parts, pose, buffers, light, shadowPass);
        }

        /** The recorded geometry's bounds {minX, minY, minZ, maxX, maxY, maxZ}, or null when nothing was drawn. */
        @Nullable float[] bounds() {
            float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            boolean any = false;
            for (Leftover l : parts) {
                float[] v = l.vertices();
                for (int i = 0; i < v.length; i += 8) {
                    if (!Float.isFinite(v[i]) || !Float.isFinite(v[i + 1]) || !Float.isFinite(v[i + 2])) continue;
                    any = true;
                    for (int a = 0; a < 3; a++) {
                        b[a] = Math.min(b[a], v[i + a]);
                        b[a + 3] = Math.max(b[a + 3], v[i + a]);
                    }
                }
            }
            return any ? b : null;
        }
    }

    /** Records a drawing (at an identity pose, light 0: a vertex's light afterwards was set by the drawing itself). */
    static Replay record(java.util.function.BiConsumer<PoseStack, MultiBufferSource> drawing) {
        var recorder = new Recorder();
        drawing.accept(new PoseStack(), recorder);
        var parts = new ArrayList<Leftover>();
        for (var e : recorder.streams.entrySet()) {
            Stream st = e.getValue();
            int count = st.count - st.count % 4;
            if (count == 0) continue;
            Kind kind = kind(e.getKey());
            parts.add(new Leftover(e.getKey(), kind.chunk() || kind.shadow(), java.util.Arrays.copyOf(st.v, count * 8),
                    java.util.Arrays.copyOf(st.color, count), java.util.Arrays.copyOf(st.light, count)));
        }
        return new Replay(parts.toArray(Leftover[]::new), VextrisMeshes.generation());
    }

    // ------------------------------------------------------------------------------------------ bookkeeping

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frame++;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) HOSTS.clear();
    }

    private static Host host(WoodGunRackBlockEntity rack) {
        return HOSTS.computeIfAbsent(rack, k -> new Host());
    }

    /** The rack's model data (WoodGunRackBlockEntity#getModelData): records new contents on the client thread, any thread reads. */
    public static ModelData modelData(WoodGunRackBlockEntity rack) {
        if (rack.getLevel() == null || !rack.getLevel().isClientSide) return ModelData.EMPTY;
        Host h = host(rack);
        if (RenderSystem.isOnRenderThread() && !AflShaderCompat.activeShadowPass()) refresh(rack, h);
        ModelData data = h.data;
        return data == null ? ModelData.EMPTY : data;
    }

    /**
     * For the renderer, each pass: the copy the chunk shows, or null. In the main pass it also records new contents and
     * asks for a rebuild of the section.
     */
    static @Nullable Copy shown(WoodGunRackBlockEntity rack, boolean shadowPass) {
        if (!AflRenderDev.staticMesh()) return null;
        Host h = host(rack);
        if (!shadowPass && refresh(rack, h)) {
            rack.requestModelDataUpdate();
            BlockPos pos = rack.getBlockPos();
            var renderer = Minecraft.getInstance().levelRenderer;
            if (renderer != null) renderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        }
        Shown s = h.newest;
        if (s != null && frame < s.frame() + CONFIRM_FRAMES) s = h.previous;
        return s == null ? null : s.copy();
    }

    /** Called by the chunk model when it meshes a copy (any thread). */
    private static void confirm(Copy copy) {
        Host h = copy.host;
        synchronized (h) {
            Shown n = h.newest;
            if (n != null && n.copy().seq >= copy.seq) return;
            if (n != null && frame >= n.frame() + CONFIRM_FRAMES) h.previous = n;
            h.newest = new Shown(copy, frame);
        }
    }

    /** Records the contents when they differ from the last recording; true when it did. */
    private static boolean refresh(WoodGunRackBlockEntity rack, Host h) {
        Direction facing = rack.getBlockState().getValue(WoodFurniture.GunRack.FACING);
        long generation = VextrisMeshes.generation();
        Copy old = h.requested;
        if (old != null && old.facing == facing && old.generation == generation && same(old, rack)) return false;
        Copy copy = record(rack, h, ++h.seq, generation, facing);
        h.requested = copy;
        h.data = ModelData.builder().with(CabinMeshModel.EXTRA, copy).build();
        return true;
    }

    private static boolean same(Copy copy, WoodGunRackBlockEntity rack) {
        for (int k = 0; k < WoodGunRackBlockEntity.SLOTS; k++) if (!sameStack(copy.stacks[k], rack.item(k))) return false;
        return true;
    }

    /** Same item, count and tag (what the drawing reads); capabilities left out, a copy's may not compare equal. */
    private static boolean sameStack(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() == b.isEmpty();
        return a.getItem() == b.getItem() && a.getCount() == b.getCount() && java.util.Objects.equals(a.getTag(), b.getTag());
    }

    /** 0: a shader pack lights it; 1: overworld entity lights; 2: nether entity lights (AflMeshChunking#shading). */
    private static int shading() {
        if (AflShaderCompat.shaderPackInUse()) return 0;
        var level = Minecraft.getInstance().level;
        return level != null && level.effects().constantAmbientLight() ? 2 : 1;
    }

    // ------------------------------------------------------------------------------------------- recording

    private static Copy record(WoodGunRackBlockEntity rack, Host h, long seq, long generation, Direction facing) {
        var stacks = new ItemStack[WoodGunRackBlockEntity.SLOTS];
        var leftovers = new Leftover[WoodGunRackBlockEntity.SLOTS][];
        var quads = new ArrayList<int[]>();
        var sprites = new ArrayList<TextureAtlasSprite>();
        for (int k = 0; k < stacks.length; k++) {
            ItemStack s = rack.item(k);
            stacks[k] = s.copy();
            var kept = new ArrayList<Leftover>();
            if (!s.isEmpty()) {
                var recorder = new Recorder();
                // light 0: whatever light a vertex carries afterwards was set by the drawing itself (full bright parts)
                WoodGunRackRenderer.slot(k, s, facing, new PoseStack(), recorder, 0, rack.getBlockPos().hashCode() + k);
                for (var e : recorder.streams.entrySet()) split(e.getKey(), e.getValue(), quads, sprites, kept);
            }
            leftovers[k] = kept.toArray(Leftover[]::new);
        }
        return new Copy(h, seq, generation, facing, stacks, leftovers, quads, sprites);
    }

    private static Kind kind(RenderType type) {
        long generation = VextrisMeshes.generation();
        if (generation != kindsGeneration) { KINDS.clear(); kindsGeneration = generation; }
        return KINDS.computeIfAbsent(type, WoodGunRackChunking::classify);
    }

    private static Kind classify(RenderType type) {
        var m = TEXTURE.matcher(type.toString());
        if (!m.find()) return RENDERER;
        ResourceLocation texture = ResourceLocation.tryParse(m.group(1));
        // only the memoized entity types of exactly this texture: an opaque or cutout look the chunk's cutout layer keeps
        if (texture == null || (type != RenderType.entityCutoutNoCull(texture) && type != RenderType.entityCutout(texture)
                && type != RenderType.entitySolid(texture))) {
            return new Kind(false, false, null, false);
        }
        if (texture.equals(TextureAtlas.LOCATION_BLOCKS)) return new Kind(true, true, null, true);
        TextureAtlasSprite sprite = AflStaticMeshModel.atlasSprite(texture);
        if (sprite == null) {
            if (WARNED.add(texture.toString()))
                ApocalypseFirstLight.LOGGER.warn("[AFL gun rack] texture {} is not in the block atlas; drawn by the block entity renderer", texture);
            return new Kind(false, false, null, true);
        }
        return new Kind(true, false, sprite, true);
    }

    /** One recorded draw type into chunk quads and renderer leftovers. */
    private static void split(RenderType type, Stream s, List<int[]> quads, List<TextureAtlasSprite> sprites, List<Leftover> kept) {
        Kind kind = kind(type);
        var leftV = new ArrayList<float[]>();
        var leftC = new ArrayList<int[]>();
        var leftL = new ArrayList<int[]>();
        TextureAtlasSprite missing = null;
        for (int q = 0; q + 4 <= s.count; q += 4) {
            boolean chunk = kind.chunk() && fits(s, q, kind);
            if (!chunk) {
                leftV.add(java.util.Arrays.copyOfRange(s.v, q * 8, q * 8 + 32));
                leftC.add(java.util.Arrays.copyOfRange(s.color, q, q + 4));
                leftL.add(java.util.Arrays.copyOfRange(s.light, q, q + 4));
                continue;
            }
            TextureAtlasSprite sprite = kind.sprite();
            if (sprite == null) sprite = s.sprite[q];
            if (sprite == null) {
                if (missing == null) missing = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(MissingTextureAtlasSprite.getLocation());
                sprite = missing;
            }
            quads.add(quad(s, q, kind, false));
            sprites.add(sprite);
            if (!s.closed[q]) {
                quads.add(quad(s, q, kind, true));
                sprites.add(sprite);
            }
        }
        if (leftV.isEmpty()) return;
        int n = leftV.size();
        float[] v = new float[n * 32];
        int[] c = new int[n * 4], l = new int[n * 4];
        for (int i = 0; i < n; i++) {
            System.arraycopy(leftV.get(i), 0, v, i * 32, 32);
            System.arraycopy(leftC.get(i), 0, c, i * 4, 4);
            System.arraycopy(leftL.get(i), 0, l, i * 4, 4);
        }
        kept.add(new Leftover(type, kind.shadow(), v, c, l));
    }

    /** The quad starting at vertex q stays near the cell and inside its texture. */
    private static boolean fits(Stream s, int q, Kind kind) {
        for (int i = q; i < q + 4; i++) {
            int o = i * 8;
            for (int a = 0; a < 3; a++) {
                float p = s.v[o + a];
                if (!Float.isFinite(p) || p < -REACH || p > 1 + REACH) return false;
            }
            if (kind.atlasUvs()) continue;
            float u = s.v[o + 3], v = s.v[o + 4];
            if (u < -UV_SLACK || u > 1 + UV_SLACK || v < -UV_SLACK || v > 1 + UV_SLACK) return false;
        }
        return true;
    }

    /**
     * DefaultVertexFormat.BLOCK data of the quad at vertex q, its corners in the order that faces where its normal points
     * (the chunk culls back faces; the renderer never did, so a drawing's winding was never checked); its back side:
     * the other order and the reversed normal.
     */
    private static int[] quad(Stream s, int q, Kind kind, boolean back) {
        int[] data = new int[32];
        TextureAtlasSprite sprite = kind.sprite();
        boolean reverse = back != againstNormal(s, q);
        for (int i = 0; i < 4; i++) {
            int src = q + (reverse ? (4 - i) & 3 : i), o = src * 8, d = i * 8;
            float u = s.v[o + 3], v = s.v[o + 4];
            if (sprite != null) {
                u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * Math.max(0, Math.min(1, u));
                v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * Math.max(0, Math.min(1, v));
            }
            float sign = back ? -1 : 1;
            data[d] = Float.floatToRawIntBits(s.v[o]);
            data[d + 1] = Float.floatToRawIntBits(s.v[o + 1]);
            data[d + 2] = Float.floatToRawIntBits(s.v[o + 2]);
            int c = s.color[src];   // ARGB -> ABGR
            data[d + 3] = c & 0xFF000000 | (c & 0xFF) << 16 | c & 0xFF00 | c >> 16 & 0xFF;
            data[d + 4] = Float.floatToRawIntBits(u);
            data[d + 5] = Float.floatToRawIntBits(v);
            data[d + 6] = s.light[src];   // 0: the chunk's light; else the drawing's own (kept where brighter)
            data[d + 7] = (((byte) (sign * s.v[o + 5] * 127)) & 0xFF) | (((byte) (sign * s.v[o + 6] * 127)) & 0xFF) << 8
                    | (((byte) (sign * s.v[o + 7] * 127)) & 0xFF) << 16;
        }
        return data;
    }

    /** The quad's corners at vertex q wind clockwise seen from where its first normal points. */
    private static boolean againstNormal(Stream s, int q) {
        float[] v = s.v;
        int a = q * 8;
        for (int t = 1; t <= 2; t++) {   // ABC, else ACD (a degenerate first triangle)
            int b = (q + t) * 8, c = (q + t + 1) * 8;
            float ux = v[b] - v[a], uy = v[b + 1] - v[a + 1], uz = v[b + 2] - v[a + 2];
            float wx = v[c] - v[a], wy = v[c + 1] - v[a + 1], wz = v[c + 2] - v[a + 2];
            float gx = uy * wz - uz * wy, gy = uz * wx - ux * wz, gz = ux * wy - uy * wx;
            if (gx * gx + gy * gy + gz * gz < 1e-14F) continue;
            return gx * v[a + 5] + gy * v[a + 6] + gz * v[a + 7] < 0;
        }
        return false;
    }

    /** A buffer source that keeps what is drawn into it, per render type, in drawing order. */
    private static final class Recorder implements MultiBufferSource, MaintenanceGunRendering.ClosedFaces {
        final Map<RenderType, Stream> streams = new LinkedHashMap<>();
        boolean closed;

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return streams.computeIfAbsent(type, t -> new Stream(this));
        }

        @Override
        public void closedFaces(boolean closed) { this.closed = closed; }
    }

    /** The vertices of one render type (quads, 4 per face): {x, y, z, u, v, nx, ny, nz}, ARGB colour, packed light. */
    private static final class Stream implements VertexConsumer {
        private final Recorder owner;
        float[] v = new float[8 * 1024];
        int[] color = new int[1024], light = new int[1024];
        boolean[] closed = new boolean[1024];
        TextureAtlasSprite[] sprite = new TextureAtlasSprite[1024];
        int count;
        private @Nullable TextureAtlasSprite bulkSprite;
        private float x, y, z, u, tv, nx, ny = 1, nz;
        private int argb = -1, packedLight;
        private boolean defaulted;
        private int defaultArgb;

        Stream(Recorder owner) { this.owner = owner; }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = (float) x; this.y = (float) y; this.z = (float) z;
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            argb = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.u = u; this.tv = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) { return this; }

        @Override
        public VertexConsumer uv2(int u, int v) {
            packedLight = u & 0xFFFF | (v & 0xFFFF) << 16;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            nx = x; ny = y; nz = z;
            return this;
        }

        @Override
        public void endVertex() {
            if (count == color.length) {
                int size = count * 2;
                v = java.util.Arrays.copyOf(v, size * 8);
                color = java.util.Arrays.copyOf(color, size);
                light = java.util.Arrays.copyOf(light, size);
                closed = java.util.Arrays.copyOf(closed, size);
                sprite = java.util.Arrays.copyOf(sprite, size);
            }
            int o = count * 8;
            v[o] = x; v[o + 1] = y; v[o + 2] = z; v[o + 3] = u; v[o + 4] = tv; v[o + 5] = nx; v[o + 6] = ny; v[o + 7] = nz;
            color[count] = defaulted ? defaultArgb : argb;
            light[count] = packedLight;
            closed[count] = owner.closed;
            sprite[count] = bulkSprite;
            count++;
            argb = -1;
            packedLight = 0;
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            defaulted = true;
            defaultArgb = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
        }

        @Override
        public void unsetDefaultColor() { defaulted = false; }

        /** Every bulk quad path ends here: remember its sprite for the atlas-UV draws. */
        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, float a,
                                int[] lights, int overlay, boolean readExistingColor) {
            bulkSprite = quad.getSprite();
            VertexConsumer.super.putBulkData(pose, quad, brightness, r, g, b, a, lights, overlay, readExistingColor);
            bulkSprite = null;
        }
    }
}
