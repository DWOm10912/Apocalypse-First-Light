package com.antaurora.apofirstlight.client.liquid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.LiquidRenderTypes;
import com.antaurora.apofirstlight.liquid.LiquidCell;
import com.antaurora.apofirstlight.liquid.LiquidSurfaceEdge;
import com.antaurora.apofirstlight.liquid.world.LiquidPackets;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Physical water V1 package 2 (docs/gameplay/water_v1_design.md 16.4, 16.6): the liquid's surfaces on the client and the
 * simplest drawing of them, before the full surface of package 6.
 * <ul>
 *   <li>Data: per chunk the server's wet cells (LiquidPackets.Surface); the level shown eases to the server's in about
 *   0.2 s (the server sends at most every 2 ticks), a still basin's at once.</li>
 *   <li>Surface: one quad a wet cell at its level, its corners the mean of the levels of the cells around the corner that
 *   the server says its water goes on into (LiquidCell#joinedSides: no wall or step between them), so the surface is
 *   continuous across cells and chunks and flat to a shore or a wall; where the ground is higher than the water the
 *   ground hides it. Its underside too, for a camera under water.</li>
 *   <li>Sides where the water ends against open air (a step down, the front of running water): from the surface down to
 *   the floor or the water beside it, only where the block beside it leaves the face open (16.10).</li>
 *   <li>Films (docs/gameplay/water_falling_v1_design.md 12, 13): water under {@link #SHOW_FROM} is no surface, only wet
 *   ground (one continuous darkening, staying a minute after it dried, {@link #drawDamp}); the surface fades in up to
 *   {@link #SHOW_FULL}. A few millimetres of water on grass sit among the blades and in the soil's crumbs: seen as wet
 *   ground, never as a sheet over the grass (in game 2026-10-24).</li>
 *   <li>Drawn after the translucent terrain. Without a shader pack by AFL's water program (WaterLook, afl_water: the
 *   water as a body over a copy of the frame, 16.10); with one (or in Fabulous) with the vanilla translucent type
 *   (block atlas, the block's light), so the pack's own translucent program draws it (Aureveil trial, 16.6), sprite
 *   {@code block/liquid/water_surface} (AFL's own, made by tools/water/surface_v0.py) tinted by the vertex colour. Never
 *   in a pack's shadow pass.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientLiquid {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SPRITE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/liquid/water_surface");
    /** Drawn within this distance of the camera (m). */
    private static final double RANGE = 160;
    /** Thinner water is not drawn (m): films on slopes the ground hides anyway. */
    private static final float MIN_DEPTH = 0.004F;
    /** Water this shallow is wet ground; the surface fades in from here to SHOW_FULL (m). */
    static final float SHOW_FROM = 0.008F, SHOW_FULL = 0.02F;
    /** Wet ground stays dark this long after its film is gone (ticks), fading out. */
    private static final int DAMP_TICKS = 1200;
    /** Two neighbours' water are one surface when their levels are this close (m) and their water overlaps. */
    private static final float JOIN = 0.6F;
    /** The colour of clear water over its ground, and how fast it thickens with depth. */
    private static final float R = 0.30F, G = 0.42F, B = 0.46F, ALPHA_MIN = 0.38F, ALPHA_MAX = 0.82F, ALPHA_PER_M = 0.55F;

    /** One wet cell. */
    static final class Cell {
        final int x, z, floor;
        final boolean boundary;
        /** LiquidCell#joinedSides: where the water goes on into the neighbour's (no wall or step between them). */
        final int sides;
        boolean still;
        float target, shown, before;
        /** The water's velocity east and south, m/s (WaterQuery). */
        float vx, vz;

        Cell(int x, int z, int floor, float level, boolean boundary, boolean still, int sides) {
            this.x = x;
            this.z = z;
            this.floor = floor;
            this.boundary = boundary;
            this.still = still;
            this.sides = sides;
            target = shown = before = level;
        }

        float floorM() { return floor / 16F; }
    }

    private static final Long2ObjectOpenHashMap<Cell[]> CHUNKS = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<Cell[]> COLUMNS = new Long2ObjectOpenHashMap<>();
    /** Wet ground by column: its floor (m) and the last game tick a film was on it. */
    private static final Long2ObjectOpenHashMap<double[]> DAMP = new Long2ObjectOpenHashMap<>();
    private static final BufferBuilder BUILDER = new BufferBuilder(1 << 16);
    private static VertexBuffer buffer;
    private static ClientLevel tracked;
    private static boolean failed;
    /** Diagnostics: last frame's quads and build + draw time (us). */
    public static int lastQuads;
    public static long lastMicros;
    /** Dev switch: false draws nothing (the data stays). */
    public static boolean enabled = true;

    private ClientLiquid() {}

    static {
        com.antaurora.apofirstlight.liquid.WaterQuery.setClientSource(ClientLiquid::layers);
    }

    /** WaterQuery's client side: the column's water as drawn (the level shown), held levels left to vanilla's water. */
    private static int layers(int x, int z, double[] out) {
        Cell[] at = COLUMNS.get(column(x, z));
        if (at == null) return 0;
        int n = 0;
        for (Cell c : at) {
            if (n == com.antaurora.apofirstlight.liquid.WaterQuery.MAX_LAYERS) break;
            if (c.boundary || c.shown - c.floorM() < com.antaurora.apofirstlight.liquid.WaterQuery.MIN_DEPTH) continue;
            int o = n++ * com.antaurora.apofirstlight.liquid.WaterQuery.STRIDE;
            out[o] = c.floorM();
            out[o + 1] = c.shown;
            out[o + 2] = c.vx;
            out[o + 3] = c.vz;
        }
        return n;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** The server's surfaces of one chunk (client main thread). */
    public static void receive(LiquidPackets.Surface p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != tracked) clear(mc.level);
        long key = ChunkPos.asLong(p.chunkX(), p.chunkZ());
        Cell[] old = CHUNKS.remove(key);
        if (old != null) for (Cell c : old) COLUMNS.remove(column(c.x, c.z));
        meshDirty(p.chunkX(), p.chunkZ());
        int n = p.column().length;
        if (n == 0) {
            Mesh m = MESHES.remove(key);
            if (m != null) m.close();
            return;
        }
        Cell[] cells = new Cell[n];
        for (int i = 0; i < n; i++) {
            int x = (p.chunkX() << 4) + ((p.column()[i] >> 4) & 15), z = (p.chunkZ() << 4) + (p.column()[i] & 15);
            boolean still = (p.flags()[i] & LiquidPackets.STILL) != 0, boundary = (p.flags()[i] & LiquidPackets.BOUNDARY) != 0;
            Cell c = new Cell(x, z, p.floor()[i], p.level()[i] / 4096F, boundary, still, p.flags()[i] >> LiquidPackets.SIDES_SHIFT & 15);
            c.vx = (float) (p.vx()[i] / LiquidPackets.Surface.VELOCITY);
            c.vz = (float) (p.vz()[i] / LiquidPackets.Surface.VELOCITY);
            // the same cell as before eases on from where it is shown
            if (old != null && !still) {
                for (Cell o : old) {
                    if (o.x == x && o.z == z && o.floor == c.floor) {
                        c.shown = c.before = o.shown;
                        break;
                    }
                }
            }
            cells[i] = c;
        }
        CHUNKS.put(key, cells);
        for (Cell c : cells) {
            long ck = column(c.x, c.z);
            Cell[] at = COLUMNS.get(ck);
            if (at == null) COLUMNS.put(ck, new Cell[]{c});
            else {
                Cell[] more = java.util.Arrays.copyOf(at, at.length + 1);
                more[at.length] = c;
                COLUMNS.put(ck, more);
            }
        }
    }

    private static void clear(ClientLevel level) {
        for (Mesh m : MESHES.values()) m.close();
        MESHES.clear();
        CHUNKS.clear();
        COLUMNS.clear();
        DAMP.clear();
        tracked = level;
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() == null || !event.getLevel().isClientSide()) return;
        Cell[] old = CHUNKS.remove(event.getChunk().getPos().toLong());
        if (old != null) for (Cell c : old) COLUMNS.remove(column(c.x, c.z));
        Mesh m = MESHES.remove(event.getChunk().getPos().toLong());
        if (m != null) m.close();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != tracked) clear(mc.level);
        if (mc.isPaused()) return;
        for (var e : CHUNKS.long2ObjectEntrySet()) {
            boolean moved = false;
            for (Cell c : e.getValue()) {
                c.before = c.shown;
                c.shown = c.still || c.boundary ? c.target : c.shown + (c.target - c.shown) * 0.5F;
                moved |= Math.abs(c.shown - c.before) > 1e-4F;
            }
            if (moved) {
                Mesh m = MESHES.get(e.getLongKey());
                if (m != null) m.dirty = true;
            }
        }
    }

    /** The cell of column (x, z) that is the same body of water as c, or null. */
    private static Cell joined(int x, int z, Cell c, float level) {
        Cell[] at = COLUMNS.get(column(x, z));
        if (at == null) return null;
        Cell best = null;
        float bestD = JOIN;
        for (Cell o : at) {
            float lo = level(o);
            float d = Math.abs(lo - level);
            // their water overlaps: each one's surface above the other's floor
            if (d < bestD && lo > c.floorM() && level > o.floorM()) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * The AFL water surface at (x, z) a point at height y is in (between a cell's floor and its surface, as shown now), or
     * NaN: where falling water and drops land (ClientFalls, LiquidJet.Surface).
     */
    public static double surfaceBelow(double x, double y, double z) {
        Cell[] at = COLUMNS.get(column(net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z)));
        if (at == null) return Double.NaN;
        for (Cell c : at) {
            if (c.shown - c.floorM() < SHOW_FULL && !c.boundary) continue;
            if (y <= c.shown && y > c.floorM() - 0.05) return c.shown;
        }
        return Double.NaN;
    }

    private static float partial;

    private static float level(Cell c) {
        return c.before + (c.shown - c.before) * partial;
    }

    /**
     * The surface at the corner (cx, cz) of c: the mean over the cells around it that c's water goes on into, the side
     * neighbours across c's joined sides and the diagonal one across a joined side of either. A corner with none (a shore,
     * a wall between two pools) stays at c's own level, so the surface is flat to its edge and the ground covers it. Across
     * a joined side with no water drawn near this level (it runs on as a film) the corner comes down to the ground there
     * (LiquidSurfaceEdge, falling water 21).
     */
    private static float corner(Cell c, float level, int cx, int cz) {
        int ox = cx == c.x ? c.x - 1 : c.x + 1, oz = cz == c.z ? c.z - 1 : c.z + 1;
        int sx = cx == c.x ? LiquidCell.WEST : LiquidCell.EAST, sz = cz == c.z ? LiquidCell.NORTH : LiquidCell.SOUTH;
        Cell a = (c.sides & sx) != 0 ? joined(ox, c.z, c, level) : null;
        Cell b = (c.sides & sz) != 0 ? joined(c.x, oz, c, level) : null;
        Cell d = a != null && (a.sides & sz) != 0 ? joined(ox, oz, a, level(a)) : null;
        if (d == null && b != null && (b.sides & sx) != 0) d = joined(ox, oz, b, level(b));
        float sum = level;
        int n = 1;
        if (a != null) { sum += level(a); n++; }
        if (b != null) { sum += level(b); n++; }
        if (d != null) { sum += level(d); n++; }
        float h = sum / n;
        // the water runs on into a side neighbour drawn nowhere near this level (a film running down the slope past the
        // crest): the corner comes down to the ground there, just inside that neighbour (falling water 21)
        float in = 0.02F;
        float zIn = cz == c.z ? cz + in : cz - in, xIn = cx == c.x ? cx + in : cx - in;
        if ((c.sides & sx) != 0 && a == null) h = LiquidSurfaceEdge.runoff(h, c.floorM(), groundAt(ox == c.x - 1 ? cx - in : cx + in, zIn, level));
        if ((c.sides & sz) != 0 && b == null) h = LiquidSurfaceEdge.runoff(h, c.floorM(), groundAt(xIn, oz == c.z - 1 ? cz - in : cz + in, level));
        return h;
    }

    private static final com.antaurora.apofirstlight.vehicle.physics.VehicleWorld.Ground GROUND = new com.antaurora.apofirstlight.vehicle.physics.VehicleWorld.Ground();
    private static net.minecraft.world.level.Level groundOf;
    private static com.antaurora.apofirstlight.vehicle.physics.LevelVehicleWorld groundWorld;

    /** The ground as drawn at (x, z), from half a metre above the water's level down to 2 m below it; NaN when none. */
    private static double groundAt(double x, double z, float level) {
        net.minecraft.world.level.Level l = tracked;
        if (l == null) return Double.NaN;
        if (l != groundOf) {
            groundOf = l;
            groundWorld = new com.antaurora.apofirstlight.vehicle.physics.LevelVehicleWorld(l, null);
        }
        if (!groundWorld.ground(x, z, level, level + 0.5, level - 2.0, GROUND) || !GROUND.found) return Double.NaN;
        return GROUND.height;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !enabled || failed || CHUNKS.isEmpty() && DAMP.isEmpty()) return;
        if (AflShaderCompat.activeShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != tracked) return;
        long t0 = System.nanoTime();
        try {
            draw(mc, level, event);
        } catch (RuntimeException e) {
            failed = true;
            LOGGER.error("AFL water: the surface is switched off after an error", e);
        }
        lastMicros = (System.nanoTime() - t0) / 1000;
    }

    /**
     * Without a shader pack (and not in Fabulous, whose translucent layer is a target of its own) AFL's own program draws
     * the water as a body (WaterLook, afl_water); otherwise the plain surface with the vanilla translucent type, which a
     * shader pack's own translucent program draws.
     */
    private static void draw(Minecraft mc, ClientLevel level, RenderLevelStageEvent event) {
        partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        ShaderInstance look = AflShaderCompat.shaderPackInUse() || Minecraft.useShaderTransparency() ? null : WaterLook.program();
        lastLook = look != null;
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(SPRITE);
        Out o = OUT;
        o.look = look != null;
        o.u0 = sprite.getU0();
        o.u1 = sprite.getU1();
        o.v0 = sprite.getV0();
        o.v1 = sprite.getV1();
        // the ripples' texture coordinates: the world position from an origin on a 1024 m grid (their tiles divide it)
        o.ox = Math.floor(cam.x / 1024) * 1024;
        o.oz = Math.floor(cam.z / 1024) * 1024;
        if (look != null) {
            drawCached(mc, level, event, look, cam, o);
            return;
        }
        o.cam = cam;
        BufferBuilder b = BUILDER;
        o.b = b;
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long now = level.getGameTime();
        int quads = 0;
        double range2 = RANGE * RANGE;
        for (Cell[] cells : CHUNKS.values()) {
            for (Cell c : cells) {
                double dx = c.x + 0.5 - cam.x, dz = c.z + 0.5 - cam.z;
                if (dx * dx + dz * dz > range2) continue;
                quads += emit(level, c, level(c), pos, now);
            }
        }
        lastQuads = quads;
        b.setQuadSorting(VertexSorting.byDistance(0, 0, 0));
        BufferBuilder.RenderedBuffer rendered = b.endOrDiscardIfEmpty();
        // the darkening is off with the rain's (ClientRainWet#enabled, user 2026-10-25)
        if (ClientRainWet.enabled) drawDamp(mc, level, event.getPoseStack(), cam, now);
        if (rendered == null) return;
        if (buffer == null) buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        Matrix4f view = event.getPoseStack().last().pose();
        RenderType type = RenderType.translucent();
        type.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            // positions are camera-relative: no chunk offset left over from the terrain
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0F, 0F, 0F);
            buffer.bind();
            buffer.upload(rendered);
            buffer.drawWithShader(view, event.getProjectionMatrix(), shader);
            VertexBuffer.unbind();
        } else rendered.release();
        type.clearRenderState();
    }

    /**
     * One cell's surface, its underside and its open sides into the frame's builder (OUT); returns the quads. A film (under
     * SHOW_FROM) draws nothing; while the darkening is on it is wet ground (drawDamp).
     */
    private static int emit(ClientLevel level, Cell c, float l, BlockPos.MutableBlockPos pos, long now) {
        Out o = OUT;
        float depth = l - c.floorM();
        if (ClientRainWet.enabled && depth > 0 && depth < SHOW_FULL) {
            long key = column(c.x, c.z);
            double[] d = DAMP.get(key);
            if (d == null) DAMP.put(key, d = new double[2]);
            d[0] = c.floorM();
            d[1] = now;
        }
        // without a shader pack only standing water is drawn: from SHOW_FULL deep, faded in from SHOW_FROM (falling water 19)
        if (depth < SHOW_FROM) return 0;
        float fade = smooth((depth - SHOW_FROM) / (SHOW_FULL - SHOW_FROM));
        float h00 = corner(c, l, c.x, c.z), h10 = corner(c, l, c.x + 1, c.z),
                h11 = corner(c, l, c.x + 1, c.z + 1), h01 = corner(c, l, c.x, c.z + 1);
        // the surface's block and the one above, brighter of each: a surface inside a slab is not dark
        int la = LevelRenderer.getLightColor(level, pos.set(c.x, (int) Math.floor(l), c.z)),
                lb = LevelRenderer.getLightColor(level, pos.move(0, 1, 0));
        o.light = Math.max(la & 0xFFFF, lb & 0xFFFF) | Math.max(la >>> 16, lb >>> 16) << 16;
        o.alpha = Math.min(ALPHA_MAX, ALPHA_MIN + depth * ALPHA_PER_M) * fade;
        o.depth = depth;
        o.fade = fade;
        int x0 = c.x, x1 = c.x + 1, z0 = c.z, z1 = c.z + 1;
        // the top, counter-clockwise seen from above, and its underside (seen from under water)
        o.vertex(x0, h00, z0, 0, 0, 0, 0, 1, 0);
        o.vertex(x0, h01, z1, 0, 0, 1, 0, 1, 0);
        o.vertex(x1, h11, z1, 0, 1, 1, 0, 1, 0);
        o.vertex(x1, h10, z0, 0, 1, 0, 0, 1, 0);
        o.vertex(x1, h10, z0, 0, 1, 0, 0, -1, 0);
        o.vertex(x1, h11, z1, 0, 1, 1, 0, -1, 0);
        o.vertex(x0, h01, z1, 0, 0, 1, 0, -1, 0);
        o.vertex(x0, h00, z0, 0, 0, 0, 0, -1, 0);
        int quads = 2;
        if (fade < 1) return quads;
        // the sides where the water ends against open air (a step down, the front of running water)
        if ((c.sides & LiquidCell.EAST) == 0) quads += side(level, c, l, x1, z1, h11, x1, z0, h10, 1, 0, pos);
        if ((c.sides & LiquidCell.WEST) == 0) quads += side(level, c, l, x0, z0, h00, x0, z1, h01, -1, 0, pos);
        if ((c.sides & LiquidCell.SOUTH) == 0) quads += side(level, c, l, x0, z1, h01, x1, z1, h11, 0, 1, pos);
        if ((c.sides & LiquidCell.NORTH) == 0) quads += side(level, c, l, x1, z0, h10, x0, z0, h00, 0, -1, pos);
        return quads;
    }

    /**
     * The water program's path (falling water 15, "走到被淹没的那边，简直是卡的要死" in game 2026-10-25): each chunk's
     * water is built once into a VertexBuffer of its own, positions from the chunk's corner, and only drawn each frame, the
     * program moving it by its ChunkOffset uniform. A chunk is rebuilt when its data comes or a neighbour's does (the
     * surface's corners lean on the neighbours), and once a tick while its levels ease toward the server's (still water
     * never). The levels are the tick's (no partial tick): water levels move slowly.
     */
    private static void drawCached(Minecraft mc, ClientLevel level, RenderLevelStageEvent event, ShaderInstance look, Vec3 cam, Out o) {
        if (o.ox != meshOx || o.oz != meshOz) {
            meshOx = o.ox;
            meshOz = o.oz;
            for (Mesh m : MESHES.values()) m.dirty = true;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long now = level.getGameTime();
        double reach = RANGE + 12, reach2 = reach * reach;
        int builds = 0;
        // built at the tick's levels: level(c) is then c.shown, for the neighbours too
        float framePartial = partial;
        partial = 1;
        for (var e : CHUNKS.long2ObjectEntrySet()) {
            long key = e.getLongKey();
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            double dx = (cx << 4) + 8 - cam.x, dz = (cz << 4) + 8 - cam.z;
            if (dx * dx + dz * dz > reach2) continue;
            Mesh m = MESHES.get(key);
            if (m == null) MESHES.put(key, m = new Mesh());
            if (!m.dirty || builds >= BUILDS_PER_FRAME) continue;
            m.dirty = false;
            builds++;
            o.cam = new Vec3(cx << 4, 0, cz << 4);
            BufferBuilder b = BUILDER;
            o.b = b;
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            int quads = 0;
            for (Cell c : e.getValue()) quads += emit(level, c, c.shown, pos, now);
            BufferBuilder.RenderedBuffer rendered = b.endOrDiscardIfEmpty();
            m.quads = quads;
            if (rendered == null) {
                m.close();
                continue;
            }
            if (m.buffer == null) m.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            m.buffer.bind();
            m.buffer.upload(rendered);
            VertexBuffer.unbind();
        }
        partial = framePartial;
        lastBuilds = builds;
        RenderTarget main = mc.getMainRenderTarget();
        if (!WaterLook.copyScene(main)) return;
        WaterLook.setUp(look, level, cam, partial, event.getProjectionMatrix(), main, o.ox, o.oz);
        com.mojang.blaze3d.shaders.Uniform offset = look.getUniform("ChunkOffset");
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        Matrix4f view = event.getPoseStack().last().pose();
        int quads = 0;
        for (var e : MESHES.long2ObjectEntrySet()) {
            Mesh m = e.getValue();
            if (m.buffer == null || m.quads == 0) continue;
            long key = e.getLongKey();
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            double dx = (cx << 4) + 8 - cam.x, dz = (cz << 4) + 8 - cam.z;
            if (dx * dx + dz * dz > reach2) continue;
            if (offset != null) offset.set((float) ((cx << 4) - cam.x), (float) -cam.y, (float) ((cz << 4) - cam.z));
            m.buffer.bind();
            m.buffer.drawWithShader(view, event.getProjectionMatrix(), look);
            quads += m.quads;
        }
        VertexBuffer.unbind();
        if (offset != null) offset.set(0F, 0F, 0F);
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        lastQuads = quads;
    }

    /** One chunk's water on the GPU (the water program's path). */
    private static final class Mesh {
        VertexBuffer buffer;
        int quads;
        boolean dirty = true;

        void close() {
            if (buffer != null) buffer.close();
            buffer = null;
            quads = 0;
        }
    }

    private static final Long2ObjectOpenHashMap<Mesh> MESHES = new Long2ObjectOpenHashMap<>();
    private static double meshOx = Double.NaN, meshOz = Double.NaN;
    /** Chunks rebuilt a frame at most. */
    private static final int BUILDS_PER_FRAME = 12;
    static int lastBuilds;

    /** The chunk and the four beside it rebuild (their surfaces' corners lean on each other). */
    private static void meshDirty(int cx, int cz) {
        for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Mesh m = MESHES.get(ChunkPos.asLong(cx + d[0], cz + d[1]));
            if (m != null) m.dirty = true;
        }
    }

    static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /**
     * Wet ground under films and where they were (falling water 13, after "这一地的圆形水膜也太丑了" in game 2026-10-25): one
     * continuous darkening, not a splat a cell. Each cell's wetness (1 under a film, fading over DAMP_TICKS after it went)
     * is a field: every wet cell and the dry ones beside it at the same ground are drawn as 2 x 2 quads whose vertices take
     * the field's value there (centre the cell's own, an edge's middle the mean of the two cells, a corner of the four), so
     * a wet patch is even inside and falls off over a metre round it, where block/liquid/wet_mask (4 m tiling blotches,
     * multiplied into the alpha) breaks the edge up. The quads lie on the drawn ground: a smoothed slope's top
     * (TerrainSmooth geometry) or the cell's floor. Black at up to WET_GRASS / WET_SOIL: wet grass darkens less than wet
     * soil.
     */
    private static void drawDamp(Minecraft mc, ClientLevel level, PoseStack pose, Vec3 cam, long now) {
        if (DAMP.isEmpty()) return;
        double range2 = RANGE * RANGE;
        WETNESS.clear();
        for (var it = DAMP.long2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
            var e = it.next();
            double[] d = e.getValue();
            long age = now - (long) d[1];
            if (age > DAMP_TICKS || age < 0) {
                it.remove();
                continue;
            }
            WETNESS.put(e.getLongKey(), 1 - smooth(age / (float) DAMP_TICKS));
        }
        if (WETNESS.isEmpty()) return;
        // the wet cells and the dry ones round them at the same ground
        it.unimi.dsi.fastutil.longs.LongOpenHashSet draw = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (long key : WETNESS.keySet()) {
            int x = (int) (key >> 32), z = (int) key;
            double dx = x + 0.5 - cam.x, dz = z + 0.5 - cam.z;
            if (dx * dx + dz * dz > range2) continue;
            for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) draw.add(column(x + i, z + k));
        }
        TextureAtlasSprite mask = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WET_MASK);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(LiquidRenderTypes.DECAL);
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float[] w = new float[9];
        double[] h = new double[9];
        for (long key : draw) {
            int x = (int) (key >> 32), z = (int) key;
            double[] own = DAMP.get(key);
            // a dry cell beside a wet one: its ground, if it is the same ground (no pit edge or wall between)
            int yBlock;
            double floor;
            if (own != null) {
                floor = own[0];
                yBlock = Math.floorDiv((int) Math.round(floor * 16) - 1, 16);
            } else {
                double near = nearFloor(x, z);
                if (Double.isNaN(near)) continue;
                yBlock = Math.floorDiv((int) Math.round(near * 16) - 1, 16);
                BlockState ground = level.getBlockState(pos.set(x, yBlock, z)), above = level.getBlockState(pos.set(x, yBlock + 1, z));
                if (ground.getCollisionShape(level, pos.set(x, yBlock, z)).isEmpty() || Block.isFaceFull(above.getCollisionShape(level, pos.set(x, yBlock + 1, z)), Direction.DOWN)) continue;
                floor = yBlock + 1;
            }
            // the field at the 3 x 3 vertices (0, 0.5, 1 across the cell)
            float c = wet(x, z), e = wet(x + 1, z), wv = wet(x - 1, z), s = wet(x, z + 1), n = wet(x, z - 1);
            float ne = wet(x + 1, z - 1), nw = wet(x - 1, z - 1), se = wet(x + 1, z + 1), sw = wet(x - 1, z + 1);
            w[0] = (c + wv + n + nw) / 4; w[1] = (c + n) / 2; w[2] = (c + e + n + ne) / 4;
            w[3] = (c + wv) / 2;          w[4] = c;           w[5] = (c + e) / 2;
            w[6] = (c + wv + s + sw) / 4; w[7] = (c + s) / 2; w[8] = (c + e + s + se) / 4;
            boolean any = false;
            for (float f : w) any |= f > 0.01F;
            if (!any) continue;
            // the drawn ground: a smoothed top's slope, else flat at the floor
            pos.set(x, yBlock, z);
            long g = -1;
            var chunk = level.getChunkAt(pos);
            if (TerrainSmooth.applies(level)) g = TerrainSmooth.geometry(level, chunk, x, yBlock, z);
            float[] corners = g >= 0 ? SmoothRules.corners(g) : null;
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    h[j * 3 + i] = corners != null ? yBlock + SmoothRules.surface(corners, i * 0.5, j * 0.5) : floor;
                }
            }
            BlockState top = level.getBlockState(pos);
            float strength = top.is(Blocks.GRASS_BLOCK) ? WET_GRASS : WET_SOIL;
            int light = LevelRenderer.getLightColor(level, pos.set(x, yBlock + 1, z));
            double lift = Math.max(0.003, Math.sqrt((x + 0.5 - cam.x) * (x + 0.5 - cam.x) + (z + 0.5 - cam.z) * (z + 0.5 - cam.z)) * 0.00012);
            float tu = (x & 3) / 4F, tv = (z & 3) / 4F;
            for (int j = 0; j < 2; j++) {
                for (int i = 0; i < 2; i++) {
                    // nw, sw, se, ne: the quad's two triangles split on its nw-se diagonal, as the smoothed top's are
                    int[] idx = {j * 3 + i, (j + 1) * 3 + i, (j + 1) * 3 + i + 1, j * 3 + i + 1};
                    int[][] at = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
                    for (int k = 0; k < 4; k++) {
                        double fx = at[k][0] * 0.5, fz = at[k][1] * 0.5;
                        float px = (float) (x + fx - cam.x), py = (float) (h[idx[k]] + lift - cam.y), pz = (float) (z + fz - cam.z);
                        out.vertex(m, px, py, pz).color(0, 0, 0, Math.round(255 * strength * w[idx[k]]))
                                .uv(mask.getU((tu + fx / 4) * 16), mask.getV((tv + fz / 4) * 16))
                                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, 0, 1, 0).endVertex();
                    }
                }
            }
        }
        buffers.endBatch(LiquidRenderTypes.DECAL);
    }

    private static final it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap WETNESS = new it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap();
    static final ResourceLocation WET_MASK = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/liquid/wet_mask");
    /** How dark wet ground gets at most (black over it): grass, and bare soil, sand, rock. */
    static final float WET_GRASS = 0.2F, WET_SOIL = 0.32F;

    private static float wet(int x, int z) {
        return WETNESS.get(column(x, z));
    }

    /** The floor of a wet neighbour of (x, z) (NaN: none): where a dry cell's ground would be the same. */
    private static double nearFloor(int x, int z) {
        for (int i = -1; i <= 1; i++) {
            for (int k = -1; k <= 1; k++) {
                double[] d = DAMP.get(column(x + i, z + k));
                if (d != null) return d[0];
            }
        }
        return Double.NaN;
    }

    /** Side faces sit this far inside the water's column, off the faces of the blocks beside it. */
    private static final float SIDE_INSET = 0.01F;

    /**
     * The side of c along the edge from (ax, az) to (bx, bz) (counter-clockwise seen from outside, its outward normal nx,
     * nz), from the surface (the corners' heights ha, hb) down to the floor, or to the water beside it if that stands
     * higher; block by block, only where the block beside it does not close the face (its collision shape covers it).
     */
    private static int side(ClientLevel level, Cell c, float l, int ax, int az, float ha, int bx, int bz, float hb, int nx, int nz,
                            BlockPos.MutableBlockPos pos) {
        int sx = c.x + nx, sz = c.z + nz;
        float bottom = c.floorM();
        Cell[] beside = COLUMNS.get(column(sx, sz));
        if (beside != null) {
            for (Cell o : beside) {
                float lo = level(o);
                if (o.floorM() < l && lo > bottom && lo < l) bottom = lo;
            }
        }
        float top = Math.max(ha, hb);
        if (top - bottom < MIN_DEPTH) return 0;
        Direction facing = Direction.fromDelta(-nx, 0, -nz);
        float ix = -nx * SIDE_INSET, iz = -nz * SIDE_INSET;
        Out o = OUT;
        int quads = 0;
        for (int y = (int) Math.floor(bottom); y < top; y++) {
            BlockState state = level.getBlockState(pos.set(sx, y, sz));
            // with a context: the two-argument form is the state's cached cube, not a smoothed top's slope (falling water 21)
            if (Block.isFaceFull(state.getCollisionShape(level, pos, net.minecraft.world.phys.shapes.CollisionContext.empty()), facing)) continue;
            float lo = Math.max(bottom, y), ta = Math.max(lo, Math.min(ha, y + 1)), tb = Math.max(lo, Math.min(hb, y + 1));
            if (ta - lo < 1e-3F && tb - lo < 1e-3F) continue;
            double axf = ax + ix, azf = az + iz, bxf = bx + ix, bzf = bz + iz;
            // along the edge for the plain texture's u; the ripples' coordinates are the world's
            o.side(axf, lo, azf, ha - lo, 0, nx, nz);
            o.side(bxf, lo, bzf, hb - lo, 1, nx, nz);
            o.side(bxf, tb, bzf, hb - tb, 1, nx, nz);
            o.side(axf, ta, azf, ha - ta, 0, nx, nz);
            quads++;
        }
        return quads;
    }

    /** The vertex writer of a frame: the plain surface's or the water program's attributes. */
    private static final class Out {
        BufferBuilder b;
        boolean look;
        float u0, u1, v0, v1, alpha, depth, fade = 1;
        int light;
        double ox, oz;
        Vec3 cam;

        /** A surface vertex at world (x, y, z); (u, v) on the plain texture; normal (nx, ny, nz). */
        void vertex(double x, float y, double z, float toSurface, float u, float v, int nx, int ny, int nz) {
            put(x, y, z, toSurface, look ? (float) (x - ox) : u0 + (u1 - u0) * u, look ? (float) (z - oz) : v0 + (v1 - v0) * v, nx, ny, nz);
        }

        /** A side vertex: toSurface the height up to the surface above it; u along the edge. */
        void side(double x, float y, double z, float toSurface, float u, int nx, int nz) {
            put(x, y, z, toSurface, look ? (float) (nx != 0 ? z - oz : x - ox) : u0 + (u1 - u0) * u,
                    look ? y : v0 + (v1 - v0) * Math.min(1, toSurface), nx, 0, nz);
        }

        private void put(double x, float y, double z, float toSurface, float u, float v, float nx, float ny, float nz) {
            float px = (float) (x - cam.x), py = (float) (y - cam.y), pz = (float) (z - cam.z);
            if (look) {
                // the program's own inputs: height up to the surface and depth, / 8 m
                b.vertex(px, py, pz).color(Math.min(1, toSurface / 8), Math.min(1, depth / 8), fade, 1F).uv(u, v).uv2(light)
                        .normal(nx, ny, nz).endVertex();
            } else {
                b.vertex(px, py, pz).color(R, G, B, alpha).uv(u, v).uv2(light).normal(nx, ny, nz).endVertex();
            }
        }
    }

    private static final Out OUT = new Out();
    /** Diagnostics: whether the last frame used the water program. */
    private static boolean lastLook;

    /** Dev readout ({@code /afl_render water status}). */
    public static String status() {
        int cells = 0;
        for (Cell[] c : CHUNKS.values()) cells += c.length;
        return String.format(Locale.ROOT, "%s; %d chunks, %d wet cells; last frame %d quads, %d chunks rebuilt, %d us, %s%s",
                enabled ? "on" : "off", CHUNKS.size(), cells, lastQuads, lastBuilds, lastMicros, lastLook ? "AFL water program (cached per chunk)" : "plain surface",
                failed ? "; FAILED (see log)" : "");
    }
}
