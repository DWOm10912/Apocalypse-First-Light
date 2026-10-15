package com.antaurora.apofirstlight.client.road;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCells;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The generated roads on the client (C3.2; docs/worldgen/federal_highways_c3.md, 10.5): the built routes arrive as JSON
 * at login (RoadNetworkS2CPacket), become the same RoadDesigns the server's chunk pass uses, and each loaded chunk the
 * road reaches gets its pavement mesh (RoadMesh: the R4 sample's asphalt, edge and lane lines, safety edges, clipped to
 * the chunk) built on a worker thread, a bounded number per tick, and dropped when the chunk unloads. Drawn after the
 * solid terrain with the terrain solid program and the block atlas (RoadRenderTypes), lit per vertex from the world's
 * light, in the main pass only (never in a shader pack's shadow pass).
 * <p>
 * Where the collision blocks of a paved cell are gone (dug out, blown up), the mesh leaves that cell open and lines the
 * hole with asphalt walls (RoadCells): each finished mesh is compared with the world once, the chunks around the player
 * every tick and the others a few per tick.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class RoadClient {
    static final ResourceLocation ASPHALT = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/asphalt"),
            WHITE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/paint_white"),
            YELLOW = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/paint_yellow");
    /** Floats per vertex: x, y, z (chunk-relative, absolute y), u, v (atlas), nx, ny, nz. */
    static final int STRIDE = 8;
    /** Mesh builds started per tick (each about 0.3 ms of worker time per road). */
    static final int BUILDS_PER_TICK = 4;

    private static List<RoadDesign> roads = List.of();
    private static RoadWorks index;
    private static final Map<Long, ChunkMesh> MESHES = new HashMap<>();
    private static final Map<Long, CompletableFuture<ChunkMesh>> PENDING = new HashMap<>();

    /** One chunk's pavement: asphalt and marking vertices (four per triangle: a, b, c, c), and its height range. */
    record ChunkMesh(int cx, int cz, float[] asphalt, float[] marking, float minY, float maxY, Map<Integer, RoadCells.Cell> cells, BitSet dug) {}

    /** Meshes compared with the world per tick besides the 3 x 3 chunks around the player. */
    static final int CHECKS_PER_TICK = 8;
    private static int checkCursor;

    private RoadClient() {}

    public static void receive(String json) {
        clear();
        if (json == null || json.isEmpty()) { roads = List.of(); index = null; return; }
        try {
            Set<String> ids = new HashSet<>();
            for (var r : RoadNetworkJson.routes(json)) ids.add(r.id());
            roads = RoadNetworkJson.designs(json, ids);
            index = new RoadWorks(roads, RoadWorks.Water.NONE);
        } catch (RuntimeException e) {
            ApocalypseFirstLight.LOGGER.error("[AFL roads] cannot read the road network from the server; no pavement is drawn", e);
            roads = List.of();
            index = null;
        }
    }

    private static boolean active(ClientLevel level) { return index != null && level != null && level.dimension() == Level.OVERWORLD; }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    private static float[] sprite(ResourceLocation id) {
        TextureAtlasSprite s = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
        if (s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation()))
            throw new IllegalStateException("missing road texture " + id);
        return new float[]{s.getU0(), s.getU1(), s.getV0(), s.getV1()};
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!active(level) || mc.player == null) { if (!MESHES.isEmpty() || !PENDING.isEmpty()) clear(); return; }
        // finished builds
        for (var it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!e.getValue().isDone()) continue;
            it.remove();
            try {
                ChunkMesh m = e.getValue().join();
                if (level.getChunkSource().hasChunk(m.cx(), m.cz())) {
                    MESHES.put(e.getKey(), m);
                    recheck(level, e.getKey(), m);
                }
            } catch (RuntimeException ex) {
                ApocalypseFirstLight.LOGGER.error("[AFL roads] pavement mesh failed for a chunk", ex);
            }
        }
        // unloaded chunks
        MESHES.values().removeIf(m -> !level.getChunkSource().hasChunk(m.cx(), m.cz()));
        // dug-out pavement: the chunks around the player every tick, the others round robin
        int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z, r = mc.options.getEffectiveRenderDistance() + 1;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            long k = key(pcx + dx, pcz + dz);
            ChunkMesh m = MESHES.get(k);
            if (m != null) recheck(level, k, m);
        }
        if (!MESHES.isEmpty()) {
            List<Long> keys = new ArrayList<>(MESHES.keySet());
            for (int i = 0; i < Math.min(CHECKS_PER_TICK, keys.size()); i++) {
                long k = keys.get(Math.floorMod(checkCursor++, keys.size()));
                recheck(level, k, MESHES.get(k));
            }
        }
        // new chunks, nearest first is not needed: a bounded number per tick in scan order
        int started = 0;
        Map<String, float[]> uv = null;
        for (int dz = -r; dz <= r && started < BUILDS_PER_TICK; dz++) for (int dx = -r; dx <= r && started < BUILDS_PER_TICK; dx++) {
            int cx = pcx + dx, cz = pcz + dz;
            long k = key(cx, cz);
            if (MESHES.containsKey(k) || PENDING.containsKey(k) || !index.touches(cx, cz) || !level.getChunkSource().hasChunk(cx, cz)) continue;
            if (uv == null) uv = Map.of("asphalt", sprite(ASPHALT), "white", sprite(WHITE), "yellow", sprite(YELLOW));
            Map<String, float[]> atlas = uv;
            List<RoadDesign> rs = roads;
            RoadWorks works = index;
            PENDING.put(k, CompletableFuture.supplyAsync(() -> build(rs, works, cx, cz, atlas, new BitSet())));
            started++;
        }
    }

    /** Compare a mesh's paved cells with the world; rebuild it when the set of dug cells changed. */
    private static void recheck(ClientLevel level, long k, ChunkMesh m) {
        if (m == null || PENDING.containsKey(k)) return;
        BitSet dug = dug(level, m);
        if (dug.equals(m.dug())) return;
        Map<String, float[]> atlas = Map.of("asphalt", sprite(ASPHALT), "white", sprite(WHITE), "yellow", sprite(YELLOW));
        List<RoadDesign> rs = roads;
        RoadWorks works = index;
        PENDING.put(k, CompletableFuture.supplyAsync(() -> build(rs, works, m.cx(), m.cz(), atlas, dug)));
    }

    /**
     * The paved cells (chunk and ring) whose collision blocks are not the ones the chunk pass put there; empty when no
     * cell of the chunk itself is dug (the ring only shapes the walls of a hole in this chunk).
     */
    static BitSet dug(ClientLevel level, ChunkMesh m) {
        BitSet out = new BitSet();
        if (m.cells() == null || m.cells().isEmpty()) return out;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean inner = false;
        for (RoadCells.Cell c : m.cells().values()) {
            int x = m.cx() * 16 + RoadCells.lx(c.index()), z = m.cz() * 16 + RoadCells.lz(c.index());
            if (!surface(level.getBlockState(p.set(x, c.y(), z)), c.layers())
                    || (c.upper() > 0 && !surface(level.getBlockState(p.set(x, c.y() + 1, z)), c.upper()))) {
                out.set(c.index());
                inner |= RoadCells.inner(c.index());
            }
        }
        return inner ? out : new BitSet();
    }

    private static boolean surface(BlockState s, int layers) {
        return s.is(AflBlocks.ROAD_SURFACE.get()) && s.getValue(RoadSurfaceBlock.LAYERS) == layers;
    }

    static ChunkMesh build(List<RoadDesign> rs, RoadWorks works, int cx, int cz, Map<String, float[]> uv, BitSet dug) {
        List<RoadMesh.Triangle> tris = new ArrayList<>();
        for (RoadDesign d : rs) tris.addAll(RoadMesh.chunk(d, cx, cz));
        Map<Integer, RoadCells.Cell> cells = tris.isEmpty() ? Map.of() : RoadCells.of(rs, works, cx, cz);
        if (!dug.isEmpty()) {
            boolean[] removed = new boolean[256];
            for (int i = dug.nextSetBit(0); i >= 0; i = dug.nextSetBit(i + 1))
                if (RoadCells.inner(i)) removed[RoadCells.lx(i) | RoadCells.lz(i) << 4] = true;
            tris = RoadMesh.without(tris, removed);
            tris.addAll(RoadCells.walls(rs, cells, dug, cx, cz));
        }
        int na = 0, nm = 0;
        for (RoadMesh.Triangle t : tris) if (t.material() == RoadMesh.Material.ASPHALT) na++; else nm++;
        float[] a = new float[na * 4 * STRIDE], m = new float[nm * 4 * STRIDE];
        int ia = 0, im = 0;
        float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
        for (RoadMesh.Triangle t : tris) {
            boolean asphalt = t.material() == RoadMesh.Material.ASPHALT;
            float[] r = uv.get(switch (t.material()) { case ASPHALT -> "asphalt"; case WHITE -> "white"; case YELLOW -> "yellow"; });
            float[] out = asphalt ? a : m;
            int i = asphalt ? ia : im;
            for (RoadMesh.Vertex v : new RoadMesh.Vertex[]{t.a(), t.b(), t.c(), t.c()}) {
                out[i++] = (float) v.x();
                out[i++] = (float) v.y();
                out[i++] = (float) v.z();
                out[i++] = r[0] + (r[1] - r[0]) * (float) v.u();
                out[i++] = r[2] + (r[3] - r[2]) * (float) v.v();
                out[i++] = (float) t.nx();
                out[i++] = (float) t.ny();
                out[i++] = (float) t.nz();
                lo = Math.min(lo, (float) v.y());
                hi = Math.max(hi, (float) v.y());
            }
            if (asphalt) ia = i; else im = i;
        }
        // an empty mesh marks a chunk the index reaches but the pavement misses (never rebuilt while loaded)
        return tris.isEmpty() ? new ChunkMesh(cx, cz, a, m, 0, 0, cells, dug) : new ChunkMesh(cx, cz, a, m, lo, hi, cells, dug);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS || MESHES.isEmpty()) return;
        // Oculus draws its shadow map through renderChunkLayer(solid), whose Forge hook fires this stage with the shadow
        // camera: the pavement lies on the ground and casts no shadow worth having; draw it in the main pass only
        if (com.antaurora.apofirstlight.weapon.client.AflShaderCompat.activeShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc.level)) return;
        Vec3 camera = event.getCamera().getPosition();
        List<ChunkMesh> visible = new ArrayList<>();
        for (ChunkMesh m : MESHES.values()) {
            if (m.asphalt().length + m.marking().length == 0) continue;
            AABB box = new AABB(m.cx() * 16, m.minY() - 0.5, m.cz() * 16, m.cx() * 16 + 16, m.maxY() + 0.5, m.cz() * 16 + 16);
            if (event.getFrustum().isVisible(box)) visible.add(m);
        }
        if (visible.isEmpty()) return;
        var buffers = mc.renderBuffers().bufferSource();
        Map<Long, Integer> lights = new HashMap<>();
        PoseStack pose = event.getPoseStack();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (boolean asphalt : new boolean[]{true, false}) {
            RenderType type = asphalt ? RoadRenderTypes.ASPHALT : RoadRenderTypes.MARKING;
            VertexConsumer out = buffers.getBuffer(type);
            for (ChunkMesh m : visible) {
                float[] v = asphalt ? m.asphalt() : m.marking();
                if (v.length == 0) continue;
                pose.pushPose();
                pose.translate(m.cx() * 16 - camera.x, -camera.y, m.cz() * 16 - camera.z);
                Matrix4f mat = pose.last().pose();
                Matrix3f nor = pose.last().normal();
                for (int i = 0; i < v.length; i += STRIDE) {
                    int bx = m.cx() * 16 + (int) Math.floor(Math.min(15.999f, Math.max(0, v[i]))), by = (int) Math.floor(v[i + 1] + 0.05f),
                            bz = m.cz() * 16 + (int) Math.floor(Math.min(15.999f, Math.max(0, v[i + 2])));
                    long lk = BlockPos.asLong(bx, by, bz);
                    Integer light = lights.get(lk);
                    if (light == null) {
                        light = LevelRenderer.getLightColor(mc.level, p.set(bx, by, bz));
                        lights.put(lk, light);
                    }
                    out.vertex(mat, v[i], v[i + 1], v[i + 2]).color(1f, 1f, 1f, 1f).uv(v[i + 3], v[i + 4]).uv2(light)
                            .normal(nor, v[i + 5], v[i + 6], v[i + 7]).endVertex();
                }
                pose.popPose();
            }
            buffers.endBatch(type);
        }
    }

    /** Built and drawn pavement chunks, for /afl roads. */
    public static String status() {
        return index == null ? "no roads from the server" : roads.size() + " route(s), " + MESHES.size() + " chunk meshes, " + PENDING.size() + " building";
    }

    private static void clear() {
        MESHES.clear();
        PENDING.clear();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); roads = List.of(); index = null; }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent
        public static void register(RegisterClientReloadListenersEvent event) {
            // the atlas UVs are baked into the meshes: rebuild them after a resource reload
            event.registerReloadListener(new SimplePreparableReloadListener<Boolean>() {
                @Override protected Boolean prepare(ResourceManager manager, ProfilerFiller profiler) { return true; }
                @Override protected void apply(Boolean ignored, ResourceManager manager, ProfilerFiller profiler) { clear(); }
            });
        }
    }
}
