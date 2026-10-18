package com.antaurora.apofirstlight.client.road;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import com.antaurora.vextris.client.GpuGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws the tunnel fixtures (block/TunnelFixtureBlock; Tunnel parts removable V1.1, 2026-10-16) the way RoadClient draws
 * the roads: per chunk, every part whose first standing cell lies in the chunk (a part's cells may lie in two chunks;
 * each part is drawn once) baked into one vertex array (TunnelParts), uploaded as Vextris GpuGeometry pieces with a
 * per-vertex light stream, one batch per frame; never a block entity renderer (one draw per part, and Embeddium never
 * calls one for a block entity made after its section was built). The interchange guide signs' legends (C3.3b group 2)
 * are a second stream drawn as markings (RoadRenderTypes.MARKING), lit with the sign's face from one point.
 * <p>
 * A chunk is looked at again when a fixture cell in it or next to it loads, changes or goes (TunnelFixtureBlockEntity),
 * and round robin besides (a chunk loaded before its neighbour drew a part whose first cell was in the neighbour);
 * a chunk whose set of parts is unchanged keeps its geometry. Light is taken again every few ticks like the roads'.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class TunnelFixtureClient {
    /** Chunks looked at round robin per tick besides the changed ones. */
    static final int SCANS_PER_TICK = 16;
    /** Render-thread time per frame for chunks coming into view (first light, geometry pieces); at least one step. */
    static final long FIRST_BUDGET_NS = 1_000_000L;

    /**
     * One chunk's parts: their vertices and the guide signs' legends (TunnelParts.Baked: drawn as markings), the ranges lit
     * as one point, box (chunk-relative), the set's signature, light and GPU pieces.
     */
    record Mesh(int cx, int cz, float[] verts, float[] overlay, List<TunnelParts.Anchor> anchors, float[] box, long signature, int parts,
                RoadClient.Light light) {}

    /** A part's identity: its kind, variant, origin and axes (the cells of one part all carry it). */
    record Id(TunnelFixtureBlock.Kind kind, String bone, double x, double y, double z, double ex0, double ex1, double ex2, double ez0, double ez1, double ez2) {
        static Id of(TunnelMesh.Placement p, TunnelFixtureBlock.Kind k) {
            return new Id(k, p.bone(), p.x(), p.y(), p.z(), p.ex()[0], p.ex()[1], p.ex()[2], p.ez()[0], p.ez()[1], p.ez()[2]);
        }
    }

    private static final Map<Long, Mesh> MESHES = new HashMap<>();
    private static final Map<Long, CompletableFuture<Mesh>> PENDING = new HashMap<>();
    private static final Map<Long, Long> WANTED = new HashMap<>();
    private static final Set<Long> CHANGED = ConcurrentHashMap.newKeySet();
    private static int cursor;

    private TunnelFixtureClient() {}

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    /** A fixture cell at p loaded, changed or went: its chunk and the neighbours are looked at next tick. */
    public static void changed(BlockPos p) {
        int cx = p.getX() >> 4, cz = p.getZ() >> 4;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) CHANGED.add(key(cx + dx, cz + dz));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) { if (!MESHES.isEmpty() || !PENDING.isEmpty()) clear(); return; }
        for (var it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!e.getValue().isDone()) continue;
            it.remove();
            try {
                Mesh m = e.getValue().join();
                if (!level.getChunkSource().hasChunk(m.cx(), m.cz()) || WANTED.getOrDefault(e.getKey(), 0L) != m.signature()) continue;
                Mesh old = MESHES.put(e.getKey(), m);
                if (old != null) old.light().close();
            } catch (RuntimeException ex) {
                ApocalypseFirstLight.LOGGER.error("[AFL roads] tunnel fixture mesh failed for a chunk", ex);
            }
        }
        MESHES.values().removeIf(m -> {
            if (level.getChunkSource().hasChunk(m.cx(), m.cz())) return false;
            m.light().close();
            WANTED.remove(key(m.cx(), m.cz()));
            return true;
        });
        Map<String, float[]> uv = null;
        List<Long> todo = new ArrayList<>(CHANGED);
        CHANGED.removeAll(todo);
        int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z, r = mc.options.getEffectiveRenderDistance() + 1, side = 2 * r + 1;
        for (int i = 0; i < SCANS_PER_TICK; i++) {
            int c = Math.floorMod(cursor++, side * side);
            todo.add(key(pcx - r + c % side, pcz - r + c / side));
        }
        for (long k : todo) {
            int cx = (int) (k >> 32), cz = (int) k;
            if (!level.getChunkSource().hasChunk(cx, cz)) continue;
            List<TunnelMesh.Placement> parts = owned(level, level.getChunk(cx, cz));
            long sig = signature(parts);
            Mesh m = MESHES.get(k);
            if (WANTED.getOrDefault(k, 0L) == sig) continue;
            WANTED.put(k, sig);
            if (parts.isEmpty()) {
                if (m != null) { m.light().close(); MESHES.remove(k); }
                PENDING.remove(k);
                continue;
            }
            if (uv == null) uv = Map.of("equipment", RoadClient.sprite(TunnelParts.SHEET), "steel", RoadClient.sprite(RoadClient.STEEL),
                    "guide", RoadClient.sprite(GuideSignParts.SHEET));
            float[] eq = uv.get("equipment"), steel = uv.get("steel"), guide = uv.get("guide");
            PENDING.put(k, CompletableFuture.supplyAsync(() -> bake(parts, cx, cz, sig, eq, steel, guide)));
        }
    }

    /** The parts drawn by chunk c: those its fixture cells hold whose first standing cell (Part#cells order) is in c. */
    static List<TunnelMesh.Placement> owned(ClientLevel level, LevelChunk c) {
        Map<Id, TunnelMesh.Placement> out = new LinkedHashMap<>();
        // one part may be stored as near copies (Entry#same): the first one met stands for all
        List<TunnelFixtureBlockEntity.Entry> seen = new ArrayList<>();
        for (BlockEntity be : c.getBlockEntities().values()) {
            if (!(be instanceof TunnelFixtureBlockEntity f)) continue;
            for (TunnelFixtureBlockEntity.Entry e : f.entries()) {
                if (seen.stream().anyMatch(e::same)) continue;
                seen.add(e);
                Id id = Id.of(e.placement, e.kind);
                for (long cell : e.part().cells()) {
                    BlockPos q = BlockPos.of(cell);
                    if (!level.getChunkSource().hasChunk(q.getX() >> 4, q.getZ() >> 4)) continue;
                    if (!(level.getBlockEntity(q) instanceof TunnelFixtureBlockEntity o) || !o.holds(e)) continue;
                    if (q.getX() >> 4 == c.getPos().x && q.getZ() >> 4 == c.getPos().z) out.put(id, e.placement);
                    break;
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private static long signature(List<TunnelMesh.Placement> parts) {
        if (parts.isEmpty()) return 0;
        long h = 0x9E3779B97F4A7C15L;
        List<Integer> hs = new ArrayList<>();
        for (TunnelMesh.Placement p : parts) hs.add(Id.of(p, TunnelFixtureBlock.Kind.of(p.mesh())).hashCode());
        hs.sort(Comparator.naturalOrder());
        for (int v : hs) h = (h ^ v) * 0x100000001B3L;
        return h == 0 ? 1 : h;
    }

    private static Mesh bake(List<TunnelMesh.Placement> parts, int cx, int cz, long sig, float[] eq, float[] steel, float[] guide) {
        TunnelParts.Baked baked = TunnelParts.bake(parts, cx, cz, eq, steel, guide);
        float[] v = baked.solid();
        float x0 = Float.POSITIVE_INFINITY, y0 = Float.POSITIVE_INFINITY, z0 = Float.POSITIVE_INFINITY,
                x1 = Float.NEGATIVE_INFINITY, y1 = Float.NEGATIVE_INFINITY, z1 = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < v.length; i += RoadClient.STRIDE) {
            x0 = Math.min(x0, v[i]); x1 = Math.max(x1, v[i]);
            y0 = Math.min(y0, v[i + 1]); y1 = Math.max(y1, v[i + 1]);
            z0 = Math.min(z0, v[i + 2]); z1 = Math.max(z1, v[i + 2]);
        }
        float[] box = v.length == 0 ? new float[]{0, 0, 0, 16, 0, 16} : new float[]{x0, y0, z0, x1, y1, z1};
        return new Mesh(cx, cz, v, baked.overlay(), baked.anchors(), box, sig, parts.size(), new RoadClient.Light());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS || MESHES.isEmpty()) return;
        // like the roads: not in a shader pack's shadow pass (Tunnel V1 drew the parts with the pavement, without shadows)
        if (com.antaurora.apofirstlight.weapon.client.AflShaderCompat.activeShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        List<Mesh> visible = new ArrayList<>();
        for (Mesh m : MESHES.values()) {
            if (m.verts().length == 0) continue;
            float[] b = m.box();
            AABB box = new AABB(m.cx() * 16 + b[0], b[1] - 0.5, m.cz() * 16 + b[2], m.cx() * 16 + b[3], b[4] + 0.5, m.cz() * 16 + b[5]);
            if (event.getFrustum().isVisible(box)) visible.add(m);
        }
        if (visible.isEmpty()) return;
        Long2IntOpenHashMap lights = new Long2IntOpenHashMap();
        lights.defaultReturnValue(Integer.MIN_VALUE);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long now = mc.level.getGameTime();
        int pcx = Mth.floor(camera.x) >> 4, pcz = Mth.floor(camera.z) >> 4;
        visible.sort(Comparator.comparingInt(m -> (m.cx() - pcx) * (m.cx() - pcx) + (m.cz() - pcz) * (m.cz() - pcz)));
        long deadline = System.nanoTime() + FIRST_BUDGET_NS;
        boolean first = false;
        for (Mesh m : visible) {
            RoadClient.Light l = m.light();
            boolean near = Math.abs(m.cx() - pcx) <= 2 && Math.abs(m.cz() - pcz) <= 2;
            if (l.values[0] == null) {
                if (first && System.nanoTime() > deadline) continue;
                first = true;
            } else if (now >= l.at && now - l.at < (near ? RoadClient.LIGHT_NEAR : RoadClient.LIGHT_FAR + ((m.cx() * 7 + m.cz() * 13) & 15))) continue;
            l.values[0] = lights(mc.level, m, 0, m.verts(), lights, p);
            l.values[1] = lights(mc.level, m, 1, m.overlay(), lights, p);
            l.at = now;
            l.uploaded = false;
        }
        PoseStack pose = event.getPoseStack();
        boolean shaded = !com.antaurora.apofirstlight.weapon.client.AflShaderCompat.shaderPackInUse();
        if (RoadClient.GPU) drawCached(visible, pose, camera, shaded, deadline);
        else drawSubmitted(visible, pose, camera, shaded, mc.renderBuffers().bufferSource());
    }

    /** A stream's per-vertex light: each vertex its own block's, a guide sign's face and legend their anchor's. */
    private static int[] lights(ClientLevel level, Mesh m, int stream, float[] v, Long2IntOpenHashMap cache, BlockPos.MutableBlockPos p) {
        List<int[]> ranges = new ArrayList<>();
        List<double[]> points = new ArrayList<>();
        for (TunnelParts.Anchor a : m.anchors()) {
            if (a.stream() != stream) continue;
            ranges.add(new int[]{a.from(), a.to()});
            points.add(new double[]{a.x(), a.y(), a.z()});
        }
        return ranges.isEmpty() ? RoadClient.lights(level, m.cx(), m.cz(), v, cache, p) : RoadClient.lights(level, m.cx(), m.cz(), v, ranges, points, cache, p);
    }

    private static void drawCached(List<Mesh> visible, PoseStack pose, Vec3 camera, boolean shaded, long deadline) {
        boolean built = false;
        try (var batch = GpuGeometry.Batch.begin()) {
            for (Mesh m : visible) {
                RoadClient.Light l = m.light();
                if (l.values[0] == null) continue;
                if (l.stale(shaded)) l.close();
                if (l.ranges == null) {
                    List<int[]> rs = new ArrayList<>();
                    for (int stream = 0; stream < 2; stream++) {
                        int n = (stream == 0 ? m.verts() : m.overlay()).length / RoadClient.STRIDE;
                        for (int a = 0; a < n; a += RoadClient.PIECE) rs.add(new int[]{stream, a, Math.min(n, a + RoadClient.PIECE)});
                    }
                    l.ranges = rs.toArray(new int[0][]);
                    l.shaded = shaded;
                }
                while (!l.complete() && (!built || System.nanoTime() < deadline)) {
                    int[] r = l.ranges[l.pieces.size()];
                    l.pieces.add(GpuGeometry.build(type(r[0]), new GpuGeometry.Options().lightStream(),
                            out -> RoadClient.write(out, r[0] == 0 ? m.verts() : m.overlay(), r[1], r[2], shaded)));
                    built = true;
                }
                if (!l.complete()) continue;
                if (!l.uploaded) {
                    for (int i = 0; i < l.ranges.length; i++) if (l.pieces.get(i) != null) l.pieces.get(i).setLights(RoadClient.slice(l, l.ranges[i]));
                    l.uploaded = true;
                }
                pose.pushPose();
                pose.translate(m.cx() * 16 - camera.x, -camera.y, m.cz() * 16 - camera.z);
                Matrix4f mat = pose.last().pose();
                for (int i = 0; i < l.ranges.length; i++) {
                    GpuGeometry g = l.pieces.get(i);
                    if (g != null) g.draw(type(l.ranges[i][0]), mat, 1f, 1f, 1f, 1f, 0, 0);
                }
                pose.popPose();
            }
        }
    }

    /** The solid stream's render type, the legends' (the markings': their polygon offset over the face). */
    private static net.minecraft.client.renderer.RenderType type(int stream) { return stream == 0 ? RoadRenderTypes.ASPHALT : RoadRenderTypes.MARKING; }

    /** Every vertex submitted each frame (-Dafl.roadGpu=false, as the roads). */
    private static void drawSubmitted(List<Mesh> visible, PoseStack pose, Vec3 camera, boolean shaded, MultiBufferSource.BufferSource buffers) {
        for (int stream = 0; stream < 2; stream++) {
            VertexConsumer out = buffers.getBuffer(type(stream));
            for (Mesh m : visible) {
                float[] v = stream == 0 ? m.verts() : m.overlay();
                int[] light = m.light().values[stream];
                if (v.length == 0 || light == null) continue;
                pose.pushPose();
                pose.translate(m.cx() * 16 - camera.x, -camera.y, m.cz() * 16 - camera.z);
                Matrix4f mat = pose.last().pose();
                Matrix3f nor = pose.last().normal();
                for (int i = 0, j = 0; i < v.length; i += RoadClient.STRIDE, j++) {
                    float sh = shaded ? RoadClient.shade(v[i + 5], v[i + 6], v[i + 7]) : 1f;
                    out.vertex(mat, v[i], v[i + 1], v[i + 2]).color(sh, sh, sh, 1f).uv(v[i + 3], v[i + 4]).uv2(light[j])
                            .normal(nor, v[i + 5], v[i + 6], v[i + 7]).endVertex();
                }
                pose.popPose();
            }
            buffers.endBatch(type(stream));
        }
    }

    /** Chunks with drawn fixtures, their parts and faces, those building, for the dev harness. */
    public static String status() {
        int parts = 0, faces = 0, uploaded = 0;
        for (Mesh m : MESHES.values()) {
            parts += m.parts();
            faces += (m.verts().length + m.overlay().length) / RoadClient.STRIDE / 4;
            if (m.light().complete()) uploaded++;
        }
        return MESHES.size() + " fixture chunk(s), " + parts + " parts, " + faces + " faces, " + uploaded + " on the GPU, " + PENDING.size() + " building";
    }

    private static void clear() {
        for (Mesh m : MESHES.values()) m.light().close();
        MESHES.clear();
        PENDING.clear();
        WANTED.clear();
        CHANGED.clear();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
