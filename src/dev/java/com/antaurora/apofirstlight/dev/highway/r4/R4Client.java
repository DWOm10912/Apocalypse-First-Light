package com.antaurora.apofirstlight.dev.highway.r4;

import com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry;
import com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.Tile;
import com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.Triangle;
import com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.Vertex;
import com.antaurora.apofirstlight.dev.highwaymesh.RoadMeshAsset;
import com.antaurora.apofirstlight.dev.highwaymesh.WorldChunkMeshRenderer;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.mesh.MeshPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * The R4 pavement on the client (docs/worldgen/highway_r4_sample_v1.md, C/K): rebuilt from the recipe on a worker thread,
 * cut into 16 m tiles (the shared M1 kernel: ChunkMeshGeometry / RoadMeshAsset, Vextris MeshLoader), baked for loaded
 * chunks only (two tiles per tick), drawn after the solid terrain with the terrain solid program and the mipmapped block
 * atlas (asphalt) and the offset marking type (lines). One scene; no per-tile resource ids.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class R4Client {
    static final ResourceLocation ASPHALT = new ResourceLocation("afl_highway_demo", "block/r4_asphalt"),
            WHITE = new ResourceLocation("afl_highway_demo", "block/r4_paint_white"), YELLOW = new ResourceLocation("afl_highway_demo", "block/r4_paint_yellow");
    private static R4Network.Snapshot scene;
    private static RoadMeshAsset asset;
    private static int originX, originZ;
    private static CompletableFuture<Built> building;
    private static String buildingVersion;
    private static final Map<Tile, RoadMeshAsset.BakedTile> CACHE = new TreeMap<>();
    private static boolean failed, reportPending;
    private static long bakeNanos, buildNanos;
    private static int bakedTotal, evictedTotal, drawnTiles, submittedVertices;

    private record Built(RoadMeshAsset asset, int originX, int originZ, long nanos) {}

    private R4Client() {}

    public static void receive(R4Network.Snapshot next) {
        if (FMLEnvironment.production) return;
        boolean same = scene != null && scene.instance().equals(next.instance()) && scene.version().equals(next.version())
                && scene.dimension().equals(next.dimension()) && scene.preview() == next.preview();
        if (!same) clearAssets();
        scene = next.enabled() ? next : null;
        reportPending = next.report();
        if (scene == null) { clearAssets(); if (next.report()) message("No segment shown."); }
    }

    private static boolean active() {
        var level = Minecraft.getInstance().level;
        return scene != null && level != null && level.dimension().location().equals(scene.dimension());
    }

    private static boolean loaded(Tile t) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getChunkSource().hasChunk((originX >> 4) + t.x(), (originZ >> 4) + t.z());
    }

    private static float[] sprite(ResourceLocation id) {
        TextureAtlasSprite s = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
        if (s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation()))
            throw new IllegalStateException("Missing dev texture " + id + "; launch the updated development run");
        return new float[]{s.getU0(), s.getU1(), s.getV0(), s.getV1()};
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FMLEnvironment.production) return;
        if (!active() || failed) { if (building != null && building.isDone()) { building = null; buildingVersion = null; } return; }
        try {
            if (asset == null) {
                if (building != null && building.isDone()) {
                    Built b = building.join();
                    if (scene.version().equals(buildingVersion)) { asset = b.asset; originX = b.originX; originZ = b.originZ; buildNanos = b.nanos; }
                    building = null;
                    buildingVersion = null;
                }
                if (asset == null && building == null) {
                    var requested = scene;
                    Map<String, float[]> uv = Map.of("asphalt", sprite(ASPHALT), "white", sprite(WHITE), "yellow", sprite(YELLOW));
                    buildingVersion = requested.version();
                    building = CompletableFuture.supplyAsync(() -> build(requested, uv));
                }
                if (asset == null) return;
            }
            int before = CACHE.size();
            CACHE.keySet().removeIf(t -> !loaded(t));
            evictedTotal += before - CACHE.size();
            int remaining = 2;   // bounded work per tick, never inside the render callback
            for (var t : asset.tiles.keySet()) if (loaded(t) && !CACHE.containsKey(t)) {
                long begin = System.nanoTime();
                CACHE.put(t, asset.bake(t));
                bakeNanos += System.nanoTime() - begin;
                bakedTotal++;
                if (--remaining == 0) break;
            }
            boolean ready = asset.tiles.keySet().stream().filter(R4Client::loaded).allMatch(CACHE::containsKey);
            if (reportPending && ready) { reportPending = false; report(); }
        } catch (Exception e) {
            failed = true;
            CACHE.clear();
            message("FAILED: " + e.getMessage() + "; no pavement drawn. Saved blocks are unchanged.");
        }
    }

    private static Built build(R4Network.Snapshot s, Map<String, float[]> uv) {
        long begin = System.nanoTime();
        var recipe = R4Recipe.decode(s.recipe());
        if (!recipe.version().equals(s.version())) throw new IllegalStateException("R4_RECIPE_DIGEST_MISMATCH");
        R4Design design = recipe.design();
        int[] b = R4Earthworks.bounds(design);
        int ox = Math.floorDiv(b[0], 16) * 16, oz = Math.floorDiv(b[1], 16) * 16;
        List<Triangle> source = R4Mesh.triangles(design, ox, oz), mapped = new ArrayList<>(source.size());
        for (var t : source) {
            float[] r = uv.get(t.material());
            mapped.add(new Triangle(t.material(), map(t.a(), r), map(t.b(), r), map(t.c(), r), t.sourceNormal()));
        }
        List<double[]> centres = new ArrayList<>();
        for (double st = 0; st <= design.length + 1e-9; st += 2) {
            double[] c = design.centre(st);
            centres.add(new double[]{st, c[0] - ox, design.datum(st), c[1] - oz});
        }
        var asset = RoadMeshAsset.generated(s.version(), mapped, centres, design.length, R4Section.EDGE * 2 + 1);
        return new Built(asset, ox, oz, System.nanoTime() - begin);
    }

    private static Vertex map(Vertex v, float[] r) {
        return new Vertex(v.x(), v.y(), v.z(), r[0] + (r[1] - r[0]) * v.u(), r[2] + (r[3] - r[2]) * v.v());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS || FMLEnvironment.production || !active() || failed || asset == null) return;
        // Oculus draws its shadow map through LevelRenderer.renderChunkLayer(solid), whose Forge hook fires this same stage
        // with the shadow camera's matrices: drawing there put a skewed second road into the Sundial frame (2026-10-10).
        // The pavement lies on the ground and casts no shadow worth having; draw it in the main pass only.
        if (com.antaurora.apofirstlight.weapon.client.AflShaderCompat.activeShadowPass()) return;
        var mc = Minecraft.getInstance();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var metrics = new MeshRenderer.Metrics();
        Map<Long, Integer> lights = new HashMap<>();
        drawnTiles = 0;
        boolean preview = scene.preview();
        List<Map.Entry<Tile, RoadMeshAsset.BakedTile>> visible = new ArrayList<>();
        for (var e : CACHE.entrySet()) {
            if (!loaded(e.getKey())) continue;
            var b = e.getValue();
            AABB box = new AABB(originX + e.getKey().x() * 16, b.minY() - 0.1, originZ + e.getKey().z() * 16,
                    originX + e.getKey().x() * 16 + 16, b.maxY() + 0.1, originZ + e.getKey().z() * 16 + 16);
            if (event.getFrustum().isVisible(box)) visible.add(e);
        }
        // one pass per render type: the shared buffer flushes whenever the type changes
        for (boolean asphalt : new boolean[]{true, false}) {
            RenderType type = asphalt ? R4RenderTypes.ASPHALT : R4RenderTypes.MARKING;
            var out = buffers.getBuffer(type);
            for (var e : visible) {
                var b = e.getValue();
                Vec3 origin = new Vec3(originX + e.getKey().x() * 16, 0, originZ + e.getKey().z() * 16);
                int offset = 0;
                for (MeshPart part : b.model().parts("road")) {
                    int faces = part.faceCount();
                    var normals = b.sourceNormals().subList(offset, offset + faces);
                    offset += faces;
                    if (part.name().equals("asphalt") == asphalt)
                        WorldChunkMeshRenderer.render(List.of(part), origin, camera, event.getPoseStack(), out, mc.level, lights, metrics, preview, normals);
                }
            }
            buffers.endBatch(type);
        }
        drawnTiles = visible.size();
        submittedVertices = metrics.vertices;
    }

    private static void report() {
        long floats = CACHE.values().stream().mapToLong(t -> (long) t.corners() * 8 * 4).sum();
        message(String.format(Locale.ROOT, "%s %s tiles=%d cached=%d drawn(last frame)=%d triangles=%d submitted vertices(last frame)=%d; corner floats=%d bytes; "
                        + "mesh build=%.1f ms tile bake cumulative=%.1f ms baked=%d evicted=%d (CPU numbers, not GPU draw calls or total heap)",
                scene.preview() ? "PREVIEW (no blocks)" : "BUILT", scene.version().substring(0, 12), asset.tiles.size(), CACHE.size(), drawnTiles,
                asset.triangles.size(), submittedVertices, floats, buildNanos / 1e6, bakeNanos / 1e6, bakedTotal, evictedTotal));
    }

    private static void message(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal("Highway R4 client: " + text), false);
    }

    private static void clearAssets() {
        CACHE.clear();
        asset = null;
        failed = false;
        bakeNanos = buildNanos = 0;
        bakedTotal = evictedTotal = drawnTiles = submittedVertices = 0;
    }

    private static void clear() {
        scene = null;
        reportPending = false;
        clearAssets();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel().isClientSide()) clear(); }

    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent
        public static void register(RegisterClientReloadListenersEvent event) {
            if (FMLEnvironment.production) return;
            event.registerReloadListener(new SimplePreparableReloadListener<Boolean>() {
                @Override protected Boolean prepare(ResourceManager manager, ProfilerFiller profiler) { return true; }
                @Override protected void apply(Boolean ignored, ResourceManager manager, ProfilerFiller profiler) { clearAssets(); reportPending = scene != null; }
            });
        }
    }
}
