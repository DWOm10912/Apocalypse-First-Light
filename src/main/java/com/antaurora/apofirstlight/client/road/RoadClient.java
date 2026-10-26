package com.antaurora.apofirstlight.client.road;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.road.BridgeMesh;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCells;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.road.JunctionMesh;
import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import com.antaurora.vextris.client.GpuGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
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
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The generated roads on the client (C3.2; docs/worldgen/federal_highways_c3.md, 10.5): the built routes arrive as JSON
 * at login (RoadNetworkS2CPacket), become the same RoadDesigns the server's chunk pass uses, and each loaded chunk the
 * road reaches gets its pavement mesh (RoadMesh: asphalt, edge, lane and centre lines per cross-section, safety edges,
 * clipped to the chunk) built on a worker thread, a bounded number per tick, and dropped when the chunk unloads. Drawn
 * after the solid terrain with the terrain solid program and the block atlas (RoadRenderTypes), lit per vertex from the
 * world's light, in the main pass only (never in a shader pack's shadow pass).
 * <p>
 * Where the collision blocks of a paved cell are gone (dug out, blown up), the mesh leaves that cell open and lines the
 * hole with asphalt walls (RoadCells): each finished mesh is compared with the world once, the chunks around the player
 * every tick and the others a few per tick. Where strikes broke the asphalt (road Mesh-ification P1, RoadDamage), the
 * gone 12.5 cm sub-cells are cut out, faced and floored (RoadDamageMesh); a new copy of a chunk's damage rebuilds it and
 * its four neighbours.
 * <p>
 * Bridges (docs 10.11) are concrete meshes too (BridgeMesh, drawn with the asphalt): the world holds only their
 * invisible collision (road_structure). A chunk near a span is built twice: first the decks and parapets, then, once its
 * structure cells were scanned on the main thread (the foot of every column, which cells are broken), the abutment and
 * wing walls and the repairs of broken cells; the same comparison rebuilds it when a structure cell changes. Faces are
 * shaded by direction like blocks (not with a shader pack, which shades by the normal itself) and lit from the block in
 * front of them.
 * <p>
 * Tunnels (Tunnel V1, docs/worldgen/federal_tunnels_v1.md) are drawn the same way: the lining, walkways, drains and
 * portals (TunnelMesh, with the bridges). Their equipment and handrails are fixture blocks a player can take down, drawn
 * by TunnelFixtureClient (V1.1). Nothing in a tunnel gives light: its faces take the world's light like any other.
 * <p>
 * Drawing (docs 10.5): each chunk's solid vertices (pavement, bridges, lining) and its marking vertices are
 * uploaded once into a Vextris GpuGeometry (0.1.1-alpha.4) with a per-vertex light stream; a frame draws one matrix per
 * chunk and render type inside one GpuGeometry.Batch, and the light refresh uploads only the light stream. A geometry is
 * rebuilt when Vextris marks it stale (resource reload, shader pack on or off, pipeline rebuild) and freed when its chunk
 * unloads or is rebuilt (dug, broken structure). -Dafl.roadGpu=false submits every vertex each frame instead (the path
 * before 0.1.1-alpha.4, for comparison).
 * <p>
 * A chunk coming into view costs render-thread time once: its first light lookup and its geometry upload. Both are spread
 * over frames (FIRST_BUDGET_NS per frame, nearest chunks first, at least one step per frame), and a chunk's geometry is
 * uploaded in pieces of at most PIECE vertices; a chunk is drawn once all its pieces are up. Flying fast through new
 * country, a tunnel chunk took up to 24 ms in one frame before this (2026-10-16).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class RoadClient {
    static final ResourceLocation ASPHALT = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/asphalt"),
            WHITE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/paint_white"),
            YELLOW = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/paint_yellow"),
            CONCRETE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/reinforced_concrete"),
            TUNNEL_PANEL = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/tunnel/panel"),
            TUNNEL_ARCH = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/tunnel/arch"),
            STEEL = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/tunnel/steel"),
            DRAIN = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/tunnel/drain"),
            GRAVEL = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/gravel");
    /** Floats per vertex: x, y, z (chunk-relative, absolute y), u, v (atlas), nx, ny, nz. */
    static final int STRIDE = 8;
    /** Ticks a mesh's per-vertex light is kept: near the player (two chunks) and farther away. */
    static final int LIGHT_NEAR = 4, LIGHT_FAR = 40;
    /** Mesh builds started per tick (each about 0.3 ms of worker time per road). */
    static final int BUILDS_PER_TICK = 4;
    /** False (-Dafl.roadGpu=false): every vertex submitted each frame, no GPU geometry. */
    static final boolean GPU = !"false".equals(System.getProperty("afl.roadGpu"));
    /**
     * Render-thread time of the road client since the dev frame log last read it (ns; src/dev C2FrameLog): light lookups,
     * geometry builds, light uploads, draws, client tick; and the chunks whose light was taken and the geometries built.
     */
    public static final long[] NANOS = new long[5];
    public static final int LIGHTS = 0, BUILD = 1, UPLOAD = 2, DRAW = 3, TICK = 4;
    public static final int[] COUNTS = new int[2];
    /** Render-thread time per frame for chunks coming into view (first light lookups, geometry pieces); at least one step. */
    static final long FIRST_BUDGET_NS = 2_000_000L;
    /** Vertices per uploaded geometry piece (a multiple of 4: whole quads). */
    static final int PIECE = 4096;

    private static List<RoadDesign> roads = List.of();
    private static RoadWorks index;
    private static final Map<Long, ChunkMesh> MESHES = new HashMap<>();
    private static final Map<Long, CompletableFuture<ChunkMesh>> PENDING = new HashMap<>();

    /**
     * One chunk's pavement, bridges and tunnels: solid (asphalt, concrete, a tunnel's lining) and marking vertices (four
     * per triangle: a, b, c, c), its box (chunk-relative), the paved cells and the dug ones, the expected bridge structure
     * cells and the found ones (null: not scanned yet), the per-vertex light kept for a few ticks and the GPU copies. The
     * tunnels' equipment is not here: its parts are fixture blocks drawn by TunnelFixtureClient (V1.1).
     */
    record ChunkMesh(int cx, int cz, float[] asphalt, float[] marking, float[] box, Map<Integer, RoadCells.Cell> cells, BitSet dug,
                     Map<Integer, BridgeMesh.Expect> bridge, Map<Integer, BridgeMesh.Found> found, Light light) {}

    /**
     * A mesh's per-vertex light (solid, marking) and the game time it was taken; its GPU geometry (render thread only):
     * pieces of the solid stream and the marking stream (ranges: stream 0 or 1, first and end vertex), built in order, the
     * shade they were written with, whether the light taken last was uploaded. TunnelFixtureClient: its parts and the
     * guide signs' legends.
     */
    static final class Light {
        final int[][] values = new int[2][];
        long at = Long.MIN_VALUE;
        final List<GpuGeometry> pieces = new ArrayList<>();
        int[][] ranges;
        boolean shaded, uploaded;

        void close() {
            for (GpuGeometry g : pieces) if (g != null) g.close();
            pieces.clear();
            ranges = null;
            uploaded = false;
        }

        boolean complete() { return ranges != null && pieces.size() == ranges.length; }

        /** Some piece is stale (Vextris epoch) or was written with the other shade. */
        boolean stale(boolean shade) {
            if (ranges == null) return false;
            if (shaded != shade) return true;
            for (GpuGeometry g : pieces) if (g != null && !g.isCurrent()) return true;
            return false;
        }
    }

    /** Meshes compared with the world per tick besides the 3 x 3 chunks around the player. */
    static final int CHECKS_PER_TICK = 8;
    private static int checkCursor;

    private RoadClient() {}

    static {
        // road_structure hits on what is drawn (meshhit/RoadStructureHits): a chunk's solid vertices once its structure cells
        // were scanned (before that the walls are missing: the blocks keep their boxes)
        com.antaurora.apofirstlight.meshhit.RoadStructureHits.client((cx, cz) -> {
            ChunkMesh m = MESHES.get(key(cx, cz));
            return m == null || m.found() == null ? null : new com.antaurora.apofirstlight.meshhit.RoadStructureHits.Drawn(m.asphalt(), STRIDE, 4);
        });
    }

    public static void receive(String json) {
        clear();
        home = null;
        if (json == null || json.isEmpty()) { roads = List.of(); index = null; return; }
        try {
            roads = RoadNetworkJson.designs(json);
            home = com.antaurora.apofirstlight.worldgen.network.road.RoadCounty.read(json);
            // with the forest home's driveway (county_road_g3 section 6): its gravel mesh and its surface for the vehicles
            index = new RoadWorks(roads, RoadWorks.Water.NONE, new com.antaurora.apofirstlight.worldgen.network.town.TownWorks(
                    com.antaurora.apofirstlight.worldgen.network.town.TownPlan.read(json)),
                    com.antaurora.apofirstlight.worldgen.network.road.Driveway.of(home));
            // road_structure's collision follows the bridge mesh (a dedicated server's client has only this copy)
            com.antaurora.apofirstlight.block.RoadStructureBlock.works(index);
        } catch (RuntimeException e) {
            ApocalypseFirstLight.LOGGER.error("[AFL roads] cannot read the road network from the server; no pavement is drawn", e);
            roads = List.of();
            index = null;
        }
    }

    /** The road network's works on this client (the map draws from it; AFL Map V1), or null. */
    public static RoadWorks works() { return index; }

    /** The forest home of the network (County road G3; the map draws its buildings), or null. */
    public static com.antaurora.apofirstlight.worldgen.network.road.RoadCounty.Home home() { return home; }

    private static com.antaurora.apofirstlight.worldgen.network.road.RoadCounty.Home home;

    private static boolean active(ClientLevel level) { return index != null && level != null && level.dimension() == Level.OVERWORLD; }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    static float[] sprite(ResourceLocation id) {
        TextureAtlasSprite s = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
        if (s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation()))
            throw new IllegalStateException("missing road texture " + id);
        return new float[]{s.getU0(), s.getU1(), s.getV0(), s.getV1()};
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long t0 = System.nanoTime();
        try {
            tickRoads();
        } finally {
            NANOS[TICK] += System.nanoTime() - t0;
        }
    }

    private static void tickRoads() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!active(level) || mc.player == null) { if (!MESHES.isEmpty() || !PENDING.isEmpty()) clear(); return; }
        // finished builds; compared with the world after the loop (a recheck may start a new build in PENDING)
        List<Long> finished = new ArrayList<>();
        for (var it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!e.getValue().isDone()) continue;
            it.remove();
            try {
                ChunkMesh m = e.getValue().join();
                if (level.getChunkSource().hasChunk(m.cx(), m.cz())) {
                    ChunkMesh old = MESHES.put(e.getKey(), m);
                    if (old != null) old.light().close();
                    finished.add(e.getKey());
                }
            } catch (RuntimeException ex) {
                ApocalypseFirstLight.LOGGER.error("[AFL roads] pavement mesh failed for a chunk", ex);
            }
        }
        for (long k : finished) recheck(level, k, MESHES.get(k));
        // unloaded chunks
        MESHES.values().removeIf(m -> {
            if (level.getChunkSource().hasChunk(m.cx(), m.cz())) return false;
            m.light().close();
            return true;
        });
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
        // broken asphalt changed (RoadDamage): the chunk and its neighbours (their faces read its sub-cells), once no build
        // that may have read the old copy is still running
        if (com.antaurora.apofirstlight.road.RoadDamage.clientChanged == null) com.antaurora.apofirstlight.road.RoadDamage.clientChanged = RoadClient::damaged;
        for (var it = DAMAGED.iterator(); it.hasNext(); ) {
            long k = it.next();
            if (PENDING.containsKey(k)) continue;
            it.remove();
            ChunkMesh m = MESHES.get(k);
            if (m == null) continue;
            Map<String, float[]> atlas = atlas();
            List<RoadDesign> rs = roads;
            RoadWorks works = index;
            PENDING.put(k, CompletableFuture.supplyAsync(() -> build(rs, works, m.cx(), m.cz(), atlas, m.dug(), m.bridge(), m.found())));
        }
        // new chunks, nearest first is not needed: a bounded number per tick in scan order
        int started = 0;
        Map<String, float[]> uv = null;
        for (int dz = -r; dz <= r && started < BUILDS_PER_TICK; dz++) for (int dx = -r; dx <= r && started < BUILDS_PER_TICK; dx++) {
            int cx = pcx + dx, cz = pcz + dz;
            long k = key(cx, cz);
            if (MESHES.containsKey(k) || PENDING.containsKey(k) || !index.touches(cx, cz) || !level.getChunkSource().hasChunk(cx, cz)) continue;
            if (uv == null) uv = atlas();
            Map<String, float[]> atlas = uv;
            List<RoadDesign> rs = roads;
            RoadWorks works = index;
            PENDING.put(k, CompletableFuture.supplyAsync(() -> build(rs, works, cx, cz, atlas, new BitSet(), null, null)));
            started++;
        }
    }

    /** Chunks whose broken asphalt or a neighbour's changed (client main thread). */
    private static final Set<Long> DAMAGED = new HashSet<>();

    private static void damaged(int cx, int cz) {
        DAMAGED.add(key(cx, cz));
        DAMAGED.add(key(cx + 1, cz));
        DAMAGED.add(key(cx - 1, cz));
        DAMAGED.add(key(cx, cz + 1));
        DAMAGED.add(key(cx, cz - 1));
    }

    private static Map<String, float[]> atlas() {
        return Map.of("asphalt", sprite(ASPHALT), "white", sprite(WHITE), "yellow", sprite(YELLOW), "concrete", sprite(CONCRETE),
                "panel", sprite(TUNNEL_PANEL), "arch", sprite(TUNNEL_ARCH), "steel", sprite(STEEL), "drain", sprite(DRAIN), "equipment", sprite(TunnelParts.SHEET),
                "gravel", sprite(GRAVEL));
    }

    /** Compare a mesh's paved and structure cells with the world; rebuild it when the dug or the found cells changed. */
    private static void recheck(ClientLevel level, long k, ChunkMesh m) {
        if (m == null || PENDING.containsKey(k)) return;
        BitSet dug = dug(level, m);
        Map<Integer, BridgeMesh.Found> found = scan(level, m);
        if (dug.equals(m.dug()) && Objects.equals(found, m.found())) return;
        Map<String, float[]> atlas = atlas();
        List<RoadDesign> rs = roads;
        RoadWorks works = index;
        PENDING.put(k, CompletableFuture.supplyAsync(() -> build(rs, works, m.cx(), m.cz(), atlas, dug, m.bridge(), found)));
    }

    /**
     * The bridge structure cells (chunk and ring) as they are in the world: from the expected top down at most 3 blocks
     * to the first structure block (road_surface or road_structure), then down to the lowest one of the run.
     */
    static Map<Integer, BridgeMesh.Found> scan(ClientLevel level, ChunkMesh m) {
        return RoadStructureBlock.scan(level, m.cx(), m.cz(), m.bridge());
    }

    /**
     * The paved cells (chunk and ring) whose collision blocks are not the ones the chunk pass put there, and at
     * RoadCells.OPEN + index the cell-less neighbours of the chunk's standing cells that lie open at the cell's collision
     * block (the turf beside it gone: through the invisible block the sky showed under the mesh, 2026-10-18); empty when
     * no cell of the chunk itself is dug and none of its cells has an open side (the ring only shapes the walls), unless
     * there is broken asphalt (RoadDamage): then a dug ring cell in a loaded chunk counts too, its broken neighbours in this
     * chunk draw no face toward it (road Mesh-ification P2, RoadDamageMesh#intact).
     */
    static BitSet dug(ClientLevel level, ChunkMesh m) {
        BitSet out = new BitSet();
        if (m.cells() == null || m.cells().isEmpty()) return out;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean inner = false, ring = false, damaged = !com.antaurora.apofirstlight.road.RoadDamage.CLIENT.isEmpty();
        for (RoadCells.Cell c : m.cells().values()) {
            int x = m.cx() * 16 + RoadCells.lx(c.index()), z = m.cz() * 16 + RoadCells.lz(c.index());
            if (!surface(level.getBlockState(p.set(x, c.y(), z)), c.layers())
                    || (c.upper() > 0 && !surface(level.getBlockState(p.set(x, c.y() + 1, z)), c.upper()))) {
                out.set(c.index());
                if (RoadCells.inner(c.index())) inner = true;
                else ring |= damaged && level.getChunkSource().hasChunk(x >> 4, z >> 4);
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (RoadCells.Cell c : m.cells().values()) {
            if (!RoadCells.inner(c.index()) || out.get(c.index())) continue;
            int lx = RoadCells.lx(c.index()), lz = RoadCells.lz(c.index());
            for (int[] d : dirs) {
                int n = RoadCells.index(lx + d[0], lz + d[1]);
                if (m.cells().containsKey(n)) continue;
                int x = m.cx() * 16 + lx + d[0], z = m.cz() * 16 + lz + d[1];
                // a neighbour chunk not loaded yet reads as air: no wall until it is there
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                if (open(level.getBlockState(p.set(x, c.y(), z)))) {
                    out.set(RoadCells.OPEN + n);
                    inner = true;
                }
            }
        }
        return inner || ring ? out : new BitSet();
    }

    /** A neighbour block that leaves a collision block's side open: air, a plant, a fluid. */
    private static boolean open(BlockState s) {
        return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty();
    }

    private static boolean surface(BlockState s, int layers) {
        return s.is(AflBlocks.ROAD_SURFACE.get()) && s.getValue(RoadSurfaceBlock.LAYERS) == layers;
    }

    /**
     * Build a chunk's mesh: pavement, bridges (expect null: computed here), the dug paved cells cut out and lined, the
     * broken structure cells cut out and repaired (found null: not scanned yet; no walls, no repairs).
     */
    static ChunkMesh build(List<RoadDesign> rs, RoadWorks works, int cx, int cz, Map<String, float[]> uv, BitSet dug,
                           Map<Integer, BridgeMesh.Expect> expect, Map<Integer, BridgeMesh.Found> found) {
        // the roads and the T junction pads (C3.3b)
        List<RoadMesh.Triangle> tris = JunctionMesh.pavement(works, rs, cx, cz);
        Map<Integer, RoadCells.Cell> cells = tris.isEmpty() ? Map.of() : RoadCells.of(rs, works, cx, cz);
        if (expect == null) expect = BridgeMesh.expected(works, cx, cz);
        // no structure cells: nothing to scan (an empty scan then equals it, so the mesh is not rebuilt for it)
        if (expect.isEmpty()) found = Map.of();
        // broken asphalt (road Mesh-ification P1): the client's copies, replaced whole, never written (any thread); a dug cell's
        // walls toward a broken one leave its asphalt's faces to RoadDamageMesh (P2)
        boolean damaged = !com.antaurora.apofirstlight.road.RoadDamage.CLIENT.isEmpty();
        java.util.function.BiFunction<Integer, Integer, com.antaurora.apofirstlight.road.RoadDamageChunk> copies =
                (x, z) -> com.antaurora.apofirstlight.road.RoadDamage.CLIENT.get(net.minecraft.world.level.ChunkPos.asLong(x, z));
        java.util.function.IntPredicate broken = !damaged ? null : n -> {
            var c = copies.apply(Math.floorDiv(cx * 16 + RoadCells.lx(n), 16), Math.floorDiv(cz * 16 + RoadCells.lz(n), 16));
            return c != null && c.get(cx * 16 + RoadCells.lx(n), cz * 16 + RoadCells.lz(n)) != null;
        };
        tris = BridgeMesh.finish(tris, rs, works, cx, cz, cells, dug, expect, found, broken);
        if (damaged)
            tris = com.antaurora.apofirstlight.worldgen.network.road.RoadDamageMesh.apply(tris,
                    com.antaurora.apofirstlight.worldgen.network.road.RoadDamageMesh.of(works, copies, dug, cx, cz), cx, cz);
        int na = 0, nm = 0;
        for (RoadMesh.Triangle t : tris) if (t.material().solid()) na++; else nm++;
        float[] a = new float[na * 4 * STRIDE], m = new float[nm * 4 * STRIDE];
        int ia = 0, im = 0;
        float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
        for (RoadMesh.Triangle t : tris) {
            boolean asphalt = t.material().solid();
            float[] r = uv.get(switch (t.material()) {
                case ASPHALT -> "asphalt"; case WHITE -> "white"; case YELLOW -> "yellow"; case CONCRETE -> "concrete";
                case TUNNEL_PANEL -> "panel"; case TUNNEL_ARCH -> "arch"; case STEEL -> "steel"; case DRAIN -> "drain"; case PART -> "equipment";
                case GRAVEL -> "gravel";
            });
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
        // an empty mesh marks a chunk the index reaches but the road misses (never rebuilt while loaded)
        float[] box = tris.isEmpty() ? new float[]{0, 0, 0, 16, 0, 16} : new float[]{0, lo, 0, 16, hi, 16};
        return new ChunkMesh(cx, cz, a, m, box, cells, dug, expect, found, new Light());
    }

    /**
     * A block that holds the light it stands in (no light of its own to give a face in front of it). road_structure is not
     * one: it takes one level of light, so its own cell carries the light of its surroundings, and stepping over it took
     * a culvert's walls and a pier's foot under water or a deck the sky light of the open air above (group D).
     */
    private static boolean occludes(ClientLevel level, BlockPos p) {
        return level.getBlockState(p).isSolidRender(level, p);
    }

    /** Vanilla's directional block shade (up 1, down 0.5, north / south 0.8, east / west 0.6), blended by the normal. */
    static float shade(float nx, float ny, float nz) {
        return nx * nx * 0.6f + nz * nz * 0.8f + ny * ny * (ny > 0 ? 1.0f : 0.5f);
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
            float[] b = m.box();
            AABB box = new AABB(m.cx() * 16 + b[0], b[1] - 0.5, m.cz() * 16 + b[2], m.cx() * 16 + b[3], b[4] + 0.5, m.cz() * 16 + b[5]);
            if (event.getFrustum().isVisible(box)) visible.add(m);
        }
        if (visible.isEmpty()) return;
        // per-vertex light, taken again every LIGHT_NEAR ticks near the player and LIGHT_FAR ticks farther away (a tunnel
        // holds tens of thousands of vertices; looking each one up every frame cost more than drawing it)
        Long2IntOpenHashMap lights = new Long2IntOpenHashMap();
        lights.defaultReturnValue(Integer.MIN_VALUE);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long now = mc.level.getGameTime();
        int pcx = Mth.floor(camera.x) >> 4, pcz = Mth.floor(camera.z) >> 4;
        // nearest first: chunks coming into view get their first light and their geometry in this order
        visible.sort(Comparator.comparingInt(m -> (m.cx() - pcx) * (m.cx() - pcx) + (m.cz() - pcz) * (m.cz() - pcz)));
        long t0 = System.nanoTime(), deadline = t0 + FIRST_BUDGET_NS;
        boolean first = false;
        for (ChunkMesh m : visible) {
            Light l = m.light();
            // a chunk a truck's headlamps light (HeadlightField, design doc 8.10) counts as near: the beam moves with the truck
            boolean near = Math.abs(m.cx() - pcx) <= 2 && Math.abs(m.cz() - pcz) <= 2
                    || com.antaurora.apofirstlight.client.vehicle.HeadlightField.litChunk(m.cx(), m.cz(), now);
            if (l.values[0] == null) {
                // a chunk coming into view: within the frame budget (one at least)
                if (first && System.nanoTime() > deadline) continue;
                first = true;
            } else if (now >= l.at && now - l.at < (near ? LIGHT_NEAR : LIGHT_FAR + ((m.cx() * 7 + m.cz() * 13) & 15))) continue;
            l.values[0] = lights(mc.level, m.cx(), m.cz(), m.asphalt(), lights, p);
            l.values[1] = lights(mc.level, m.cx(), m.cz(), m.marking(), lights, p);
            l.at = now;
            l.uploaded = false;
            COUNTS[0]++;
        }
        NANOS[LIGHTS] += System.nanoTime() - t0;
        PoseStack pose = event.getPoseStack();
        // a shader pack shades by the normal itself
        boolean shaded = !com.antaurora.apofirstlight.weapon.client.AflShaderCompat.shaderPackInUse();
        if (GPU) drawCached(visible, pose, camera, shaded, deadline);
        else drawSubmitted(visible, pose, camera, shaded, mc.renderBuffers().bufferSource());
    }

    /**
     * One draw per chunk, piece and render type from the chunk's GPU geometry, all in one batch; built or rebuilt here, a
     * piece at a time within the frame budget (one piece at least), nearest chunks first (visible is sorted).
     */
    private static void drawCached(List<ChunkMesh> visible, PoseStack pose, Vec3 camera, boolean shaded, long deadline) {
        boolean built = false;
        try (var batch = GpuGeometry.Batch.begin()) {
            for (ChunkMesh m : visible) {
                Light l = m.light();
                if (l.values[0] == null) continue;
                long t0 = System.nanoTime();
                if (l.stale(shaded)) l.close();
                if (l.ranges == null) {
                    l.ranges = ranges(m);
                    l.shaded = shaded;
                }
                while (!l.complete() && (!built || System.nanoTime() < deadline)) {
                    int[] r = l.ranges[l.pieces.size()];
                    l.pieces.add(GpuGeometry.build(r[0] == 0 ? RoadRenderTypes.ASPHALT : RoadRenderTypes.MARKING, new GpuGeometry.Options().lightStream(),
                            out -> write(out, m, r, shaded)));
                    built = true;
                    COUNTS[1]++;
                }
                long t1 = System.nanoTime();
                NANOS[BUILD] += t1 - t0;
                if (!l.complete()) continue;
                if (!l.uploaded) {
                    for (int i = 0; i < l.ranges.length; i++) if (l.pieces.get(i) != null) l.pieces.get(i).setLights(slice(l, l.ranges[i]));
                    l.uploaded = true;
                }
                long t2 = System.nanoTime();
                NANOS[UPLOAD] += t2 - t1;
                pose.pushPose();
                pose.translate(m.cx() * 16 - camera.x, -camera.y, m.cz() * 16 - camera.z);
                Matrix4f mat = pose.last().pose();
                for (int i = 0; i < l.ranges.length; i++) {
                    GpuGeometry g = l.pieces.get(i);
                    if (g != null) g.draw(l.ranges[i][0] == 0 ? RoadRenderTypes.ASPHALT : RoadRenderTypes.MARKING, mat, 1f, 1f, 1f, 1f, 0, 0);
                }
                pose.popPose();
                NANOS[DRAW] += System.nanoTime() - t2;
            }
        }
    }

    /** The pieces of a chunk's geometry: solid stream and marking stream, PIECE vertices at most. */
    private static int[][] ranges(ChunkMesh m) {
        List<int[]> out = new ArrayList<>();
        int solid = m.asphalt().length / STRIDE, marking = m.marking().length / STRIDE;
        for (int a = 0; a < solid; a += PIECE) out.add(new int[]{0, a, Math.min(solid, a + PIECE)});
        for (int a = 0; a < marking; a += PIECE) out.add(new int[]{1, a, Math.min(marking, a + PIECE)});
        return out.toArray(new int[0][]);
    }

    /**
     * Vertices [r[1], r[2]) of stream r[0] in the BLOCK format, chunk-relative, shaded as drawSubmitted does; light 0 (the
     * light stream holds it).
     */
    private static void write(VertexConsumer out, ChunkMesh m, int[] r, boolean shaded) {
        write(out, r[0] == 1 ? m.marking() : m.asphalt(), r[1], r[2], shaded);
    }

    /** Vertices [from, to) of v (STRIDE floats each) in the BLOCK format, light 0 (the light stream holds it). */
    static void write(VertexConsumer out, float[] v, int from, int to, boolean shaded) {
        for (int j = from; j < to; j++) {
            int i = j * STRIDE;
            float sh = shaded ? shade(v[i + 5], v[i + 6], v[i + 7]) : 1f;
            out.vertex(v[i], v[i + 1], v[i + 2]).color(sh, sh, sh, 1f).uv(v[i + 3], v[i + 4]).uv2(0)
                    .normal(v[i + 5], v[i + 6], v[i + 7]).endVertex();
        }
    }

    /** The light of vertices [r[1], r[2]) of stream r[0]. */
    static int[] slice(Light l, int[] r) {
        int[] out = new int[r[2] - r[1]];
        System.arraycopy(l.values[r[0]], r[1], out, 0, out.length);
        return out;
    }

    /** Every vertex submitted each frame (-Dafl.roadGpu=false). */
    private static void drawSubmitted(List<ChunkMesh> visible, PoseStack pose, Vec3 camera, boolean shaded, MultiBufferSource.BufferSource buffers) {
        for (int pass = 0; pass < 2; pass++) {
            RenderType type = pass == 0 ? RoadRenderTypes.ASPHALT : RoadRenderTypes.MARKING;
            VertexConsumer out = buffers.getBuffer(type);
            for (ChunkMesh m : visible) {
                {
                    int k = pass;
                    float[] v = k == 0 ? m.asphalt() : m.marking();
                    int[] light = m.light().values[k];
                    if (v.length == 0 || light == null) continue;
                    pose.pushPose();
                    pose.translate(m.cx() * 16 - camera.x, -camera.y, m.cz() * 16 - camera.z);
                    Matrix4f mat = pose.last().pose();
                    Matrix3f nor = pose.last().normal();
                    for (int i = 0, j = 0; i < v.length; i += STRIDE, j++) {
                        float sh = shaded ? shade(v[i + 5], v[i + 6], v[i + 7]) : 1f;
                        out.vertex(mat, v[i], v[i + 1], v[i + 2]).color(sh, sh, sh, 1f).uv(v[i + 3], v[i + 4]).uv2(light[j])
                                .normal(nor, v[i + 5], v[i + 6], v[i + 7]).endVertex();
                    }
                    pose.popPose();
                }
            }
            buffers.endBatch(type);
        }
    }

    /**
     * The light of each vertex: of the block in front of its face (half a block along the normal; a wall's own block is
     * dark), looked up once per block per frame.
     */
    static int[] lights(ClientLevel level, int cx, int cz, float[] v, Long2IntOpenHashMap cache, BlockPos.MutableBlockPos p) {
        int[] out = new int[v.length / STRIDE];
        for (int i = 0, j = 0; i < v.length; i += STRIDE, j++)
            out[j] = light(level, cx * 16 + (int) Math.floor(v[i] + v[i + 5] * 0.5f), (int) Math.floor(v[i + 1] + v[i + 6] * 0.5f),
                    cz * 16 + (int) Math.floor(v[i + 2] + v[i + 7] * 0.5f), cache, p);
        return out;
    }

    /**
     * As lights, but the vertices of each range {from, to} with a point {x, y, z} (chunk-relative, y absolute) take that
     * point's block's light (TunnelFixtureClient: a guide sign's face and legend lit as one).
     */
    static int[] lights(ClientLevel level, int cx, int cz, float[] v, List<int[]> ranges, List<double[]> points, Long2IntOpenHashMap cache,
                        BlockPos.MutableBlockPos p) {
        int[] out = lights(level, cx, cz, v, cache, p);
        for (int k = 0; k < ranges.size(); k++) {
            double[] q = points.get(k);
            int light = light(level, cx * 16 + (int) Math.floor(q[0]), (int) Math.floor(q[1]), cz * 16 + (int) Math.floor(q[2]), cache, p);
            for (int j = ranges.get(k)[0]; j < Math.min(out.length, ranges.get(k)[1]); j++) out[j] = light;
        }
        return out;
    }

    /** The light of block (bx, by, bz), looked up once per frame (cache). */
    private static int light(ClientLevel level, int bx, int by, int bz, Long2IntOpenHashMap cache, BlockPos.MutableBlockPos p) {
        long lk = BlockPos.asLong(bx, by, bz);
        int light = cache.get(lk);
        if (light == Integer.MIN_VALUE) {
            // that block may itself hold the light (a parapet's own partial top block, the ground a wall's foot is
            // buried in): its light is 0, which would smear black across the face; take the first open block above
            p.set(bx, by, bz);
            for (int up = 0; up < 4 && occludes(level, p); up++) p.move(0, 1, 0);
            light = LevelRenderer.getLightColor(level, p);
            cache.put(lk, light);
        }
        return light;
    }

    /** Built and drawn pavement chunks and the bridge chunks among them, for /afl roads and the dev harness. */
    public static String status() {
        if (index == null) return "no roads from the server";
        int bridge = 0, scanned = 0, broken = 0, absent = 0, dug = 0;
        for (ChunkMesh m : MESHES.values()) {
            if (m.dug() != null) for (int i = m.dug().nextSetBit(0); i >= 0; i = m.dug().nextSetBit(i + 1)) if (RoadCells.inner(i)) dug++;
            if (m.bridge() == null || m.bridge().isEmpty()) continue;
            bridge++;
            if (m.found() == null) continue;
            scanned++;
            for (BridgeMesh.Expect e : m.bridge().values()) {
                if (!RoadCells.inner(e.index())) continue;
                BridgeMesh.State st = BridgeMesh.state(e, m.found().get(e.index()));
                if (st == BridgeMesh.State.BROKEN) broken++;
                else if (st == BridgeMesh.State.ABSENT) absent++;
            }
        }
        return roads.size() + " route(s), " + MESHES.size() + " chunk meshes, " + PENDING.size() + " building, dug paved cells " + dug + "; bridge chunks " + bridge
                + " (scanned " + scanned + ", broken cells " + broken + ", absent wing cells " + absent + ")";
    }

    private static void clear() {
        for (ChunkMesh m : MESHES.values()) m.light().close();
        MESHES.clear();
        PENDING.clear();
        DAMAGED.clear();
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
