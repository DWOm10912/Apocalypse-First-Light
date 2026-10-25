package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.blockmesh.AflStaticMeshModel;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mesh items lying on the ground are drawn by the chunk (docs/dev/render_performance_v1.md, "地上的物品交给区块"), as the
 * guns on a gun rack are ({@link WoodGunRackChunking}): a mesh gun is 11,000 to 14,000 vertices and a pile of rounds draws
 * its cartridge mesh up to five times, and the item renderer re-submitting them every frame, twice with a shader pack, cost
 * frames under Sundial (user 2026-10-19: a dropped P9, a few piles of 9 mm rounds at 26 FPS).
 * <ul>
 * <li>An item entity is no block, so there is no chunk model to hand the quads to: they are added to the section's mesh
 * through Embeddium's ChunkMeshEvent ({@link GroundItemEmbeddium}). Without Embeddium (and so without a shader pack) the
 * renderer draws the recording as before.</li>
 * <li>Which items: guns and every item drawn by a custom renderer (AFL mesh items, magazines, sights, mesh block items),
 * recorded once per stack ({@link GroundItemRenderer}, shared by every entity with the same stack) and split into what the
 * chunk takes and what the renderer keeps ({@link WoodGunRackChunking.Split}). Flat sprites and baked block models stay
 * with the renderer: they are a few quads.</li>
 * <li>When: as soon as it lies on the ground, {@link #REST_TICKS} ticks without moving more than {@link #STEP} a tick
 * (the slide after landing), placed at the entity's position and rest turn ({@link GroundItemRenderer#restYaw}) in the
 * section it lies in, and the section is rebuilt. Moved more than {@link #DRIFT} from there, in the air, picked up, gone,
 * re-recorded or with the development switch {@code static_mesh off}: taken out again and the section rebuilt; the renderer
 * draws it again at once (the old mesh may show it for a few frames more).</li>
 * <li>The renderer leaves the chunk part to the chunk only {@link #CONFIRM_FRAMES} frames after a section mesh with it was
 * built, so the item is never missing. It still draws what the chunk does not take (translucent, glint).</li>
 * <li>Light: the entity's own (packed light of the cell it is in, as the item renderer reads it), where the drawing did not
 * set one itself; shading as the gun rack's chunk path (with a shader pack plain, else the entity light in the colour).</li>
 * </ul>
 * Main thread only, but for the copies of a section (the mesh event's builder runs on a chunk build thread).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GroundItemChunking {
    static final int REST_TICKS = 1, CONFIRM_FRAMES = 4;
    /**
     * Blocks a tick an item may still slide and count as lying still; how far from where the chunk shows it it may get. A
     * thrown item slows by its block's friction after landing (x 0.59 a tick on most blocks): at 0.03 a tick it slides about
     * 0.04 more, within DRIFT, so it goes in about 0.2 s after landing and stays (2026-10-19: 0.01 / 0.02 took 0.5 s and a
     * server position correction took it out again once).
     */
    private static final double STEP = 0.03, DRIFT = 0.06;
    private static final boolean EMBEDDIUM = ModList.get().isLoaded("embeddium");

    /** One item in a section's mesh: its quads, moved by the recording's offset, turned, then placed in the section. */
    static final class Copy {
        final WoodGunRackChunking.Split split;
        final long section;
        final float tx, ty, tz, ox, oy, oz, cos, sin;
        final BlockPos lightAt;
        /** The frame a section mesh with this copy was built, or -1. */
        volatile long meshed = -1;

        Copy(WoodGunRackChunking.Split split, long section, float tx, float ty, float tz, float ox, float oy, float oz,
             float yaw, BlockPos lightAt) {
            this.split = split;
            this.section = section;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.ox = ox; this.oy = oy; this.oz = oz;
            this.cos = (float) Math.cos(yaw); this.sin = (float) Math.sin(yaw);
            this.lightAt = lightAt;
        }
    }

    private static int resplits, chunkFrames;

    private static final class Host {
        WoodGunRackChunking.Split split;
        float tx, ty, tz;
        double x = Double.NaN, y, z;
        int still;
        @Nullable Copy copy;
        double copyX, copyY, copyZ;
    }

    private static final Map<ItemEntity, Host> HOSTS = new WeakHashMap<>();
    private static final Map<Long, List<Copy>> SECTIONS = new ConcurrentHashMap<>();
    private static volatile long frame;
    private static boolean registered;
    /** Development switch (the frame time probe): off, nothing goes to the chunk and what is there comes out. */
    public static volatile boolean enabled = true;
    /** Counts for {@link #status} (development): copies put in / taken out, section meshes with copies, quads written. */
    private static int ins, outs;
    static volatile int meshEvents, emitted;

    private GroundItemChunking() {}

    /**
     * The renderer saw this item lying in the level (main pass), recorded as split and drawn moved by (tx, ty, tz) in its
     * rest-turned frame.
     */
    static void seen(ItemEntity entity, WoodGunRackChunking.Split split, float tx, float ty, float tz) {
        if (!EMBEDDIUM) return;
        if (!registered) { GroundItemEmbeddium.register(); registered = true; }
        Host h = HOSTS.computeIfAbsent(entity, e -> new Host());
        if (h.split != split || h.tx != tx || h.ty != ty || h.tz != tz) {
            if (h.split != null) resplits++;
            out(h);
            h.split = split;
            h.tx = tx; h.ty = ty; h.tz = tz;
            h.still = 0;
        }
    }

    /**
     * Ground items go to the chunk here (Embeddium, the switches on). Then the renderer draws none in a shader pack's shadow
     * pass: the chunk casts the shadow of what it holds, and one still in the air or sliding casts none until it is in
     * (2026-10-19: a P9 drawn by the renderer cost about 2.8 ms a draw, drawn twice in the shadow pass).
     */
    static boolean available() {
        return EMBEDDIUM && enabled && AflRenderDev.staticMesh();
    }

    /** The chunk shows this recording of the item (the renderer then draws only {@link WoodGunRackChunking.Split#rest}). */
    static boolean chunkDraws(ItemEntity entity, WoodGunRackChunking.Split split) {
        if (!EMBEDDIUM) return false;
        Host h = HOSTS.get(entity);
        Copy c = h == null ? null : h.copy;
        boolean drawn = c != null && c.split == split && c.meshed >= 0 && frame >= c.meshed + CONFIRM_FRAMES;
        if (drawn) chunkFrames++;
        return drawn;
    }

    /**
     * Development (the frame time probe, /afl dev): items seen, in the chunk (of them in a built mesh), and since the last
     * call copies put in / taken out, re-recordings seen, frames the chunk drew one, section meshes with copies, quads.
     */
    public static String status() {
        int copies = 0, meshed = 0;
        for (Host h : HOSTS.values()) {
            if (h.copy == null) continue;
            copies++;
            if (h.copy.meshed >= 0) meshed++;
        }
        String s = String.format("embeddium %s, on %s, hosts %d, in chunk %d (meshed %d), in %d / out %d, resplit %d, chunk-drawn frames %d, mesh events %d, quads %d",
                EMBEDDIUM, enabled, HOSTS.size(), copies, meshed, ins, outs, resplits, chunkFrames, meshEvents, emitted);
        ins = outs = resplits = chunkFrames = 0;
        meshEvents = emitted = 0;
        return s;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frame++;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || HOSTS.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        boolean on = AflRenderDev.staticMesh() && enabled;
        for (Iterator<Map.Entry<ItemEntity, Host>> it = HOSTS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ItemEntity entity = e.getKey();
            Host h = e.getValue();
            if (entity.isRemoved() || entity.level() != level) {
                out(h);
                it.remove();
                continue;
            }
            double dx = entity.getX() - h.x, dy = entity.getY() - h.y, dz = entity.getZ() - h.z;
            boolean step = !(dx * dx + dy * dy + dz * dz <= STEP * STEP);   // NaN (first tick) counts as a step
            h.x = entity.getX(); h.y = entity.getY(); h.z = entity.getZ();
            if (h.copy != null) {
                double cx = h.x - h.copyX, cy = h.y - h.copyY, cz = h.z - h.copyZ;
                if (!on || !entity.onGround() || cx * cx + cy * cy + cz * cz > DRIFT * DRIFT) out(h);
            }
            if (step || !entity.onGround() || !on) {
                h.still = 0;
                continue;
            }
            if (++h.still >= REST_TICKS && h.copy == null) in(entity, h);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        HOSTS.clear();
        SECTIONS.clear();
    }

    private static void in(ItemEntity entity, Host h) {
        BlockPos at = entity.blockPosition();
        long section = SectionPos.asLong(at);
        Copy c = new Copy(h.split, section, h.tx, h.ty, h.tz,
                (float) (entity.getX() - SectionPos.sectionToBlockCoord(SectionPos.x(section))),
                (float) (entity.getY() - SectionPos.sectionToBlockCoord(SectionPos.y(section))),
                (float) (entity.getZ() - SectionPos.sectionToBlockCoord(SectionPos.z(section))),
                GroundItemRenderer.restYaw(entity), at.immutable());
        h.copy = c;
        ins++;
        h.copyX = entity.getX(); h.copyY = entity.getY(); h.copyZ = entity.getZ();
        SECTIONS.computeIfAbsent(section, k -> new CopyOnWriteArrayList<>()).add(c);
        dirty(section);
    }

    private static void out(Host h) {
        Copy c = h.copy;
        if (c == null) return;
        h.copy = null;
        outs++;
        List<Copy> list = SECTIONS.get(c.section);
        if (list != null) {
            list.remove(c);
            if (list.isEmpty()) SECTIONS.remove(c.section, list);
        }
        dirty(c.section);
    }

    private static void dirty(long section) {
        var renderer = Minecraft.getInstance().levelRenderer;
        if (renderer != null) renderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
    }

    /** The copies in a section, for its mesh (main thread, when the section is about to be built). */
    static List<Copy> inSection(long section) {
        List<Copy> list = SECTIONS.get(section);
        return list == null || list.isEmpty() ? List.of() : new ArrayList<>(list);
    }

    /** Writes the copies into a section's cutout mesh, section-relative (a chunk build thread). */
    static void emit(List<Copy> copies, int shading, BlockAndTintGetter view, VertexConsumer out) {
        long now = frame;
        for (Copy c : copies) {
            // in a carved or worn rock block, or a pocket a carving seals off, the hollow's light (user 2026-10-24: drops black)
            int light = com.antaurora.apofirstlight.terrainsmooth.client.RockCarveMesh.lightAt(view, c.lightAt, LevelRenderer.getLightColor(view, c.lightAt));
            for (int[] d : c.split.quads) {
                for (int i = 0; i < 4; i++) {
                    int o = i * 8;
                    float x = Float.intBitsToFloat(d[o]) + c.tx, y = Float.intBitsToFloat(d[o + 1]) + c.ty,
                            z = Float.intBitsToFloat(d[o + 2]) + c.tz;
                    int n = d[o + 7];
                    float nx = (byte) n / 127.0F, ny = (byte) (n >> 8) / 127.0F, nz = (byte) (n >> 16) / 127.0F;
                    // the rest turn about +y (Axis.YP.rotation), then the offset in the section
                    float wx = c.cos * x + c.sin * z, wz = -c.sin * x + c.cos * z;
                    float wnx = c.cos * nx + c.sin * nz, wnz = -c.sin * nx + c.cos * nz;
                    int abgr = d[o + 3];
                    float shade = shading == 0 ? 1 : AflStaticMeshModel.diffuse(wnx, ny, wnz, shading);
                    int own = d[o + 6];
                    int packed = own == 0 ? light : Math.max(own & 0xFFFF, light & 0xFFFF) | Math.max(own >>> 16, light >>> 16) << 16;
                    out.vertex(c.ox + wx, c.oy + y, c.oz + wz)
                            .color(Math.round((abgr & 0xFF) * shade), Math.round((abgr >> 8 & 0xFF) * shade),
                                    Math.round((abgr >> 16 & 0xFF) * shade), abgr >>> 24)
                            .uv(Float.intBitsToFloat(d[o + 4]), Float.intBitsToFloat(d[o + 5]))
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(packed)
                            .normal(wnx, ny, wnz)
                            .endVertex();
                }
            }
            c.meshed = now;
            emitted += c.split.quads.length;
        }
    }
}
