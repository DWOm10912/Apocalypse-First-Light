package com.antaurora.apofirstlight.client.map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.map.MapGround;
import com.antaurora.apofirstlight.map.MapRaster;
import com.antaurora.apofirstlight.map.MapTowns;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One record's map tiles as GPU textures (AFL Map V1, docs/ui/afl_map_v1_plan.md 8): a tile (MapRaster, 256 x 256 of a
 * zoom level) is drawn on a worker when first asked for or when a chunk under it changed, and uploaded on the render thread
 * a few per frame; an old picture stays on screen until its new one is up. At most {@link #KEEP} tiles are kept, the
 * least recently asked for freed first. A new road network (another world) drops them all.
 */
public final class MapTiles {
    static final int KEEP = 192, JOBS = 3, UPLOADS_PER_FRAME = 3;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "AFL map tiles");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static final class Tile {
        final int level, tx, tz;
        DynamicTexture texture;
        boolean dirty = true;
        int version;
        CompletableFuture<int[]> job;
        int jobVersion;
        long used;

        Tile(int level, int tx, int tz) { this.level = level; this.tx = tx; this.tz = tz; }
    }

    private final MapGround ground;
    private final Map<Long, Tile> tiles = new HashMap<>();
    private RoadWorks worksAt;
    private int jobs, uploads;
    private long frame;

    MapTiles(MapGround ground) { this.ground = ground; }

    static long key(int level, int tx, int tz) { return ((long) level << 60) | ((tx & 0x3FFFFFFFL) << 30) | (tz & 0x3FFFFFFFL); }

    /** Start of a frame's drawing: the upload budget renews. */
    public void frame() {
        frame++;
        uploads = 0;
        RoadWorks w = MapClient.works();
        if (w != worksAt) {
            worksAt = w;
            clear();
        }
    }

    /** The GL texture of a tile, or -1 when it has no picture yet (it is then on its way). Render thread. */
    public int texture(int level, int tx, int tz) {
        long k = key(level, tx, tz);
        Tile t = tiles.get(k);
        if (t == null) {
            if (tiles.size() >= KEEP) evict();
            t = new Tile(level, tx, tz);
            tiles.put(k, t);
        }
        t.used = frame;
        if (t.job != null && t.job.isDone() && uploads < UPLOADS_PER_FRAME) {
            int[] px = t.job.getNow(null);
            boolean current = t.jobVersion == t.version;
            t.job = null;
            jobs--;
            if (px != null) {
                upload(t, px);
                uploads++;
            }
            if (!current) t.dirty = true;
        }
        if (t.dirty && t.job == null && jobs < JOBS) {
            t.dirty = false;
            t.jobVersion = t.version;
            RoadWorks works = worksAt;
            MapTowns towns = MapClient.towns();
            final Tile tile = t;
            jobs++;
            t.job = CompletableFuture.supplyAsync(() -> {
                int[] px = new int[MapRaster.TILE * MapRaster.TILE];
                try {
                    MapRaster.tile(ground, works, towns, tile.level, tile.tx, tile.tz, px);
                } catch (RuntimeException e) {
                    ApocalypseFirstLight.LOGGER.warn("[AFL map] tile {} {} {} failed", tile.level, tile.tx, tile.tz, e);
                    return null;
                }
                return px;
            }, POOL);
        }
        return t.texture == null ? -1 : t.texture.getId();
    }

    private static void upload(Tile t, int[] argb) {
        if (t.texture == null) {
            t.texture = new DynamicTexture(MapRaster.TILE, MapRaster.TILE, false);
            t.texture.setFilter(true, false);
        }
        NativeImage img = t.texture.getPixels();
        if (img == null) return;
        for (int y = 0; y < MapRaster.TILE; y++)
            for (int x = 0; x < MapRaster.TILE; x++) {
                int c = argb[y * MapRaster.TILE + x];
                // NativeImage holds ABGR
                img.setPixelRGBA(x, y, (c & 0xFF00FF00) | (c >> 16 & 0xFF) | (c & 0xFF) << 16);
            }
        t.texture.upload();
        RenderSystem.bindTexture(t.texture.getId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    /** A chunk's samples changed: every tile it (or the hill shade and the known edge round it) reaches is drawn again. */
    void dirtyChunk(int cx, int cz) {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(() -> dirtyChunk(cx, cz)); return; }
        {
            for (int level = 0; level < MapRaster.LEVELS; level++) {
                double span = MapRaster.span(level);
                int tx0 = (int) Math.floor((cx * 16 - 24) / span), tx1 = (int) Math.floor((cx * 16 + 40) / span);
                int tz0 = (int) Math.floor((cz * 16 - 24) / span), tz1 = (int) Math.floor((cz * 16 + 40) / span);
                for (int tz = tz0; tz <= tz1; tz++)
                    for (int tx = tx0; tx <= tx1; tx++) {
                        Tile t = tiles.get(key(level, tx, tz));
                        if (t != null) { t.dirty = true; t.version++; }
                    }
            }
        }
    }

    private void evict() {
        List<Tile> all = new ArrayList<>(tiles.values());
        all.sort((a, b) -> Long.compare(a.used, b.used));
        for (int i = 0; i < all.size() / 4; i++) {
            Tile t = all.get(i);
            if (t.used >= frame - 1) break;
            free(t);
            tiles.remove(key(t.level, t.tx, t.tz));
        }
    }

    private void free(Tile t) {
        if (t.texture != null) t.texture.close();
        t.texture = null;
        if (t.job != null) { t.job.cancel(false); jobs--; t.job = null; }
    }

    void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(this::clear); return; }
        for (Tile t : tiles.values()) free(t);
        tiles.clear();
        jobs = 0;
    }
}
