package com.antaurora.apofirstlight.client.liquid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.LiquidRenderTypes;
import com.antaurora.apofirstlight.liquid.LiquidCell;
import com.antaurora.apofirstlight.liquid.LiquidSurfaceMesh;
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
 *   <li>Surface (docs/gameplay/water_mesh_parcels_v1_design.md A2): one continuous mesh, 3 x 3 vertices a wet cell, cut on
 *   the smoothed tops' diagonal; its vertices from LiquidSurfaceMesh (the same number from every chunk that shares one):
 *   still water level and flat to a shore or a wall, where the ground higher than the water hides it; running water a
 *   sheet on the ground, bending into the pool it runs into, faded out at its dry edges, its flow and white water in the
 *   vertices. Still water's underside too, for a camera under water.</li>
 *   <li>Sides where the water ends against open air (a step down, the front of running water): from the surface down to
 *   the floor or the water beside it, only where the block beside it leaves the face open (16.10).</li>
 *   <li>Films (docs/gameplay/water_falling_v1_design.md 12, 13): running ones are drawn on the ground however thin (A1,
 *   the user 2026-10-26); the film the floor holds is not sent. Still water under {@link #SHOW_FROM} is no surface, only
 *   wet ground (one continuous darkening, staying a minute after it dried, {@link #drawDamp}); the surface fades in up to
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
    static final ResourceLocation SPRITE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/liquid/water_surface");
    /** Drawn within this distance of the camera (m). */
    private static final double RANGE = 160;
    /** Thinner water is not drawn (m): films on slopes the ground hides anyway. */
    private static final float MIN_DEPTH = 0.004F;
    /** Water this shallow is wet ground; the surface fades in from here to SHOW_FULL (m). */
    static final float SHOW_FROM = 0.008F, SHOW_FULL = 0.02F;
    /** Wet ground stays dark this long after its film is gone (ticks), fading out. */
    private static final int DAMP_TICKS = 1200;
    /** The colour of clear water over its ground, and how fast it thickens with depth; a running film's (plain surface). */
    private static final float R = 0.30F, G = 0.42F, B = 0.46F, ALPHA_MIN = 0.38F, ALPHA_MAX = 0.82F, ALPHA_PER_M = 0.55F, ALPHA_FILM = 0.22F;

    /** One wet cell; its velocity is WaterQuery's too. */
    static final class Cell extends LiquidSurfaceMesh.Cell {
        float target, shown, before;
        /** A river's water (LiquidPackets#RIVER): held like a natural body, but AFL's water to swim in. */
        boolean river;

        Cell(int x, int z, int floor, float level, boolean boundary, boolean still, boolean running, int sides) {
            super(x, z, floor, sides, boundary, still, running);
            target = shown = before = level;
        }

        float floorM() { return floor(); }

        @Override
        public float level() {
            return ClientLiquid.level(this);
        }
    }

    /** The surface's view of the cells and the drawn ground. */
    private static final LiquidSurfaceMesh.Source SOURCE = new LiquidSurfaceMesh.Source() {
        @Override
        public LiquidSurfaceMesh.Cell[] column(int x, int z) {
            return COLUMNS.get(ClientLiquid.column(x, z));
        }

        @Override
        public double ground(double x, double z, float level) {
            // a corner's ground is asked by each of the cells round it, and frame after frame while the water moves: kept a
            // tick, by the level to the quarter metre (searched from 0.75 m above it)
            float q = (float) (Math.floor(level * 4) / 4);
            return GROUND_CACHE.computeIfAbsent(new GroundKey(x, z, q), k -> groundAt(k.x(), k.z(), k.level() + 0.25F));
        }
    };

    private record GroundKey(double x, double z, float level) {}

    private static final java.util.HashMap<GroundKey, Double> GROUND_CACHE = new java.util.HashMap<>();

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

    /** WaterQuery's client side: the column's water as drawn (the level shown), held levels left to vanilla's water (a river's are AFL's). */
    private static int layers(int x, int z, double[] out) {
        Cell[] at = COLUMNS.get(column(x, z));
        if (at == null) return 0;
        int n = 0;
        for (Cell c : at) {
            if (n == com.antaurora.apofirstlight.liquid.WaterQuery.MAX_LAYERS) break;
            if ((c.boundary && !c.river) || c.shown - c.floorM() < com.antaurora.apofirstlight.liquid.WaterQuery.MIN_DEPTH) continue;
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
            classifyAround(p.chunkX(), p.chunkZ());
            return;
        }
        Cell[] cells = new Cell[n];
        for (int i = 0; i < n; i++) {
            int x = (p.chunkX() << 4) + ((p.column()[i] >> 4) & 15), z = (p.chunkZ() << 4) + (p.column()[i] & 15);
            boolean still = (p.flags()[i] & LiquidPackets.STILL) != 0, boundary = (p.flags()[i] & LiquidPackets.BOUNDARY) != 0,
                    running = (p.flags()[i] & LiquidPackets.RUNNING) != 0;
            Cell c = new Cell(x, z, p.floor()[i], p.level()[i] / 4096F, boundary, still, running, p.flags()[i] >> LiquidPackets.SIDES_SHIFT & 15);
            c.river = (p.flags()[i] & LiquidPackets.RIVER) != 0;
            c.vx = (float) (p.vx()[i] / LiquidPackets.Surface.VELOCITY);
            c.vz = (float) (p.vz()[i] / LiquidPackets.Surface.VELOCITY);
            c.film = p.film()[i] / 1000F;
            // the same cell as before eases on from where it is shown
            if (old != null && !still) {
                for (Cell o : old) {
                    if (o.x == x && o.z == z && o.floorSlab == c.floorSlab) {
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
        classifyAround(p.chunkX(), p.chunkZ());
    }

    /** Which cells lie on the ground (LiquidSurfaceMesh#classify), in the chunk and the four beside it: they read each other. */
    private static void classifyAround(int cx, int cz) {
        for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Cell[] cells = CHUNKS.get(ChunkPos.asLong(cx + d[0], cz + d[1]));
            if (cells != null) for (Cell c : cells) LiquidSurfaceMesh.classify(SOURCE, c);
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
        GROUND_CACHE.clear();
        for (Mesh m : MESHES.values()) m.moving = false;
        for (var e : CHUNKS.long2ObjectEntrySet()) {
            boolean moved = false;
            for (Cell c : e.getValue()) {
                c.before = c.shown;
                c.shown = c.still || c.boundary ? c.target : c.shown + (c.target - c.shown) * 0.5F;
                moved |= Math.abs(c.shown - c.before) > 1e-4F;
            }
            // moving water is rebuilt every frame at the frame's levels (not a step a tick: "水面一卡一卡", M1 in game
            // 2026-10-26), with the four chunks beside it, whose corners lean on its levels
            if (moved) {
                long k = e.getLongKey();
                for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    Mesh m = MESHES.get(ChunkPos.asLong(ChunkPos.getX(k) + d[0], ChunkPos.getZ(k) + d[1]));
                    if (m != null) m.moving = true;
                }
            }
        }
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

    static float level(Cell c) {
        return c.before + (c.shown - c.before) * partial;
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
        o.pack = look == null && WaterShaderData.packDraws();
        o.u0 = sprite.getU0();
        o.u1 = sprite.getU1();
        o.v0 = sprite.getV0();
        o.v1 = sprite.getV1();
        // the ripples' texture coordinates: the world position from an origin on a 1024 m grid (their tiles divide it)
        o.ox = Math.floor(cam.x / 1024) * 1024;
        o.oz = Math.floor(cam.z / 1024) * 1024;
        // the water program's path, and a shader pack drawing the water as its own (AFL_WATER: its output is opaque, so
        // nothing to sort; rebuilt every frame over 160 m of a wide river it cost most of the frame, in game 2026-10-28)
        if (look != null || o.pack) {
            if (ClientRainWet.enabled && o.pack) {
                dampAll(cam, level.getGameTime());
                drawDamp(mc, level, event.getPoseStack(), cam, level.getGameTime());
            }
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

    /** A cell of shallow still water marks its ground wet this frame (drawDamp; only while the darkening is on). */
    private static void damp(Cell c, float depth, boolean drape, long now) {
        if (depth <= 0 || depth >= SHOW_FULL || drape) return;
        long key = column(c.x, c.z);
        double[] d = DAMP.get(key);
        if (d == null) DAMP.put(key, d = new double[2]);
        d[0] = c.floorM();
        d[1] = now;
    }

    /** The wet marks kept fresh without building the surface (the cached paths build a chunk only when it changes). */
    private static void dampAll(Vec3 cam, long now) {
        double range2 = RANGE * RANGE;
        for (Cell[] cells : CHUNKS.values())
            for (Cell c : cells) {
                double dx = c.x + 0.5 - cam.x, dz = c.z + 0.5 - cam.z;
                if (dx * dx + dz * dz > range2) continue;
                damp(c, level(c) - c.floorM(), c.drapes(), now);
            }
    }

    /**
     * One cell's surface, its underside and its open sides into the frame's builder (OUT); returns the quads. Still water
     * under SHOW_FROM draws nothing (while the darkening is on it is wet ground, drawDamp); running water however thin
     * lies on the ground, without an underside or sides (A2).
     */
    private static int emit(ClientLevel level, Cell c, float l, BlockPos.MutableBlockPos pos, long now) {
        Out o = OUT;
        float depth = l - c.floorM();
        boolean drape = c.drapes();
        if (ClientRainWet.enabled) damp(c, depth, drape, now);
        // still water from SHOW_FULL deep, faded in from SHOW_FROM (falling water 19); running water always (A1)
        if (!drape && depth < SHOW_FROM) return 0;
        float fade = drape ? 1 : smooth((depth - SHOW_FROM) / (SHOW_FULL - SHOW_FROM));
        LiquidSurfaceMesh.Vertex[] v = VERTS;
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) LiquidSurfaceMesh.vertex(SOURCE, c, i, j, MEMBERS, v[j * 3 + i]);
        // the surface's block and the one above, brighter of each: a surface inside a slab (or a film on a slope) is not dark
        int la = LevelRenderer.getLightColor(level, pos.set(c.x, (int) Math.floor(l), c.z)),
                lb = LevelRenderer.getLightColor(level, pos.move(0, 1, 0));
        o.light = Math.max(la & 0xFFFF, lb & 0xFFFF) | Math.max(la >>> 16, lb >>> 16) << 16;
        o.alpha = drape ? ALPHA_FILM : Math.min(ALPHA_MAX, ALPHA_MIN + depth * ALPHA_PER_M) * fade;
        o.depth = depth;
        o.fade = fade;
        o.clear = c.river ? 1 : 0;
        o.kind = c.river ? WaterShaderData.RIVER : WaterShaderData.STILL;
        int quads = 0;
        // the top in 2 x 2 quads, counter-clockwise seen from above (nw, sw, se, ne: split on the nw-se diagonal, as the
        // smoothed tops are), and still water's underside (seen from under water)
        for (int qj = 0; qj < 2; qj++) {
            for (int qi = 0; qi < 2; qi++) {
                int[] at = {qj * 3 + qi, (qj + 1) * 3 + qi, (qj + 1) * 3 + qi + 1, qj * 3 + qi + 1};
                int[][] ij = {{qi, qj}, {qi, qj + 1}, {qi + 1, qj + 1}, {qi + 1, qj}};
                o.normal(v[at[0]].height, v[at[1]].height, v[at[2]].height, v[at[3]].height, 0.5F);
                for (int k = 0; k < 4; k++) o.top(c.x + ij[k][0] * 0.5, c.z + ij[k][1] * 0.5, ij[k][0] * 0.5F, ij[k][1] * 0.5F, v[at[k]], true);
                quads++;
                if (drape) continue;
                for (int k = 3; k >= 0; k--) o.top(c.x + ij[k][0] * 0.5, c.z + ij[k][1] * 0.5, ij[k][0] * 0.5F, ij[k][1] * 0.5F, v[at[k]], false);
                quads++;
            }
        }
        if (fade < 1 || drape) return quads;
        // a river's edge runs on flat over the bank beside it, the bank's sloped ground cutting it: the shore is where
        // the slope meets the water, not a block's edge with a side under it (R1 in game 2026-10-26: "为什么没有用mesh做岸边")
        if (c.river) return quads + apron(c, l, v);
        // the sides where the water ends against open air (a step down, the front of running water), in two halves along
        // the edge's middle vertex
        int x0 = c.x, x1 = c.x + 1, z0 = c.z, z1 = c.z + 1;
        if (LiquidSurfaceMesh.across(SOURCE, c, LiquidCell.EAST) == null) {
            quads += side(level, c, l, x1, z1, v[8].height, x1, z0 + 0.5, v[5].height, 1, 0, pos);
            quads += side(level, c, l, x1, z0 + 0.5, v[5].height, x1, z0, v[2].height, 1, 0, pos);
        }
        if (LiquidSurfaceMesh.across(SOURCE, c, LiquidCell.WEST) == null) {
            quads += side(level, c, l, x0, z0, v[0].height, x0, z0 + 0.5, v[3].height, -1, 0, pos);
            quads += side(level, c, l, x0, z0 + 0.5, v[3].height, x0, z1, v[6].height, -1, 0, pos);
        }
        if (LiquidSurfaceMesh.across(SOURCE, c, LiquidCell.SOUTH) == null) {
            quads += side(level, c, l, x0, z1, v[6].height, x0 + 0.5, z1, v[7].height, 0, 1, pos);
            quads += side(level, c, l, x0 + 0.5, z1, v[7].height, x1, z1, v[8].height, 0, 1, pos);
        }
        if (LiquidSurfaceMesh.across(SOURCE, c, LiquidCell.NORTH) == null) {
            quads += side(level, c, l, x1, z0, v[2].height, x0 + 0.5, z0, v[1].height, 0, -1, pos);
            quads += side(level, c, l, x0 + 0.5, z0, v[1].height, x0, z0, v[0].height, 0, -1, pos);
        }
        return quads;
    }

    private static final LiquidSurfaceMesh.Vertex APRON = new LiquidSurfaceMesh.Vertex();

    /**
     * A river cell's surface carried on flat over each column beside it that holds no water of its own (and the corner
     * column between two such sides), at the heights of the cell's edge there (v, its 3 x 3 vertices), top and underside;
     * the ground drawn above the water hides it. Returns the quads.
     */
    private static int apron(Cell c, float l, LiquidSurfaceMesh.Vertex[] v) {
        APRON.fade = 1;
        APRON.vx = c.vx;
        APRON.vz = c.vz;
        APRON.foam = 0;
        APRON.draped = false;
        boolean e = LiquidSurfaceMesh.across(SOURCE, c, LiquidCell.EAST) == null && dry(c.x + 1, c.z, l),
                w = LiquidSurfaceMesh.across(SOURCE, c, LiquidCell.WEST) == null && dry(c.x - 1, c.z, l),
                s = LiquidSurfaceMesh.across(SOURCE, c, LiquidCell.SOUTH) == null && dry(c.x, c.z + 1, l),
                n = LiquidSurfaceMesh.across(SOURCE, c, LiquidCell.NORTH) == null && dry(c.x, c.z - 1, l);
        int quads = 0;
        float nw = v[0].height, ne = v[2].height, sw = v[6].height, se = v[8].height;
        // corners in the order (0, 0), (0, 1), (1, 1), (1, 0) of the column
        if (e) quads += flat(c.x + 1, c.z, ne, se, se, ne);
        if (w) quads += flat(c.x - 1, c.z, nw, sw, sw, nw);
        if (s) quads += flat(c.x, c.z + 1, sw, sw, se, se);
        if (n) quads += flat(c.x, c.z - 1, nw, nw, ne, ne);
        if (e && s && dry(c.x + 1, c.z + 1, l)) quads += flat(c.x + 1, c.z + 1, se, se, se, se);
        if (e && n && dry(c.x + 1, c.z - 1, l)) quads += flat(c.x + 1, c.z - 1, ne, ne, ne, ne);
        if (w && s && dry(c.x - 1, c.z + 1, l)) quads += flat(c.x - 1, c.z + 1, sw, sw, sw, sw);
        if (w && n && dry(c.x - 1, c.z - 1, l)) quads += flat(c.x - 1, c.z - 1, nw, nw, nw, nw);
        return quads;
    }

    /** No water shown in column (x, z) about level l. */
    private static boolean dry(int x, int z, float l) {
        Cell[] at = COLUMNS.get(column(x, z));
        if (at == null) return true;
        for (Cell o : at) if (o.floorM() < l + 0.5F && level(o) > l - 0.5F && level(o) - o.floorM() >= SHOW_FROM) return false;
        return true;
    }

    /** Column (x, z) as one quad, its corners (0, 0), (0, 1), (1, 1), (1, 0) at the heights given, top and underside. */
    private static int flat(int x, int z, float h00, float h01, float h11, float h10) {
        Out o = OUT;
        int[][] ij = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        float[] h = {h00, h01, h11, h10};
        o.normal(h00, h01, h11, h10, 1);
        for (int k = 0; k < 4; k++) {
            APRON.height = h[k];
            o.top(x + ij[k][0], z + ij[k][1], ij[k][0], ij[k][1], APRON, true);
        }
        for (int k = 3; k >= 0; k--) {
            APRON.height = h[k];
            o.top(x + ij[k][0], z + ij[k][1], ij[k][0], ij[k][1], APRON, false);
        }
        return 2;
    }

    private static final LiquidSurfaceMesh.Vertex[] VERTS = new LiquidSurfaceMesh.Vertex[9];
    private static final LiquidSurfaceMesh.Cell[] MEMBERS = new LiquidSurfaceMesh.Cell[4];

    static {
        for (int i = 0; i < VERTS.length; i++) VERTS[i] = new LiquidSurfaceMesh.Vertex();
    }

    /**
     * The water program's path (falling water 15, "走到被淹没的那边，简直是卡的要死" in game 2026-10-25): each chunk's
     * water is built once into a VertexBuffer of its own, positions from the chunk's corner, and only drawn each frame, the
     * program moving it by its ChunkOffset uniform. A chunk is rebuilt when its data comes or a neighbour's does (the
     * surface's corners lean on the neighbours), and every frame while its levels (or a neighbour's) ease toward the
     * server's, at the frame's levels (a step a tick showed as jerks, M1 in game 2026-10-26); still water never.
     */
    private static void drawCached(Minecraft mc, ClientLevel level, RenderLevelStageEvent event, ShaderInstance look, Vec3 cam, Out o) {
        // the vertices differ by path (the water program's attributes, the pack's): built again on a switch
        int mode = look != null ? 0 : 1;
        if (o.ox != meshOx || o.oz != meshOz || mode != meshMode) {
            meshOx = o.ox;
            meshOz = o.oz;
            meshMode = mode;
            for (Mesh m : MESHES.values()) m.dirty = true;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long now = level.getGameTime();
        double reach = RANGE + 12, reach2 = reach * reach;
        int builds = 0;
        // built at the frame's levels (level(c) between the tick's), the neighbours' too: a moving chunk every frame
        for (var e : CHUNKS.long2ObjectEntrySet()) {
            long key = e.getLongKey();
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            double dx = (cx << 4) + 8 - cam.x, dz = (cz << 4) + 8 - cam.z;
            if (dx * dx + dz * dz > reach2) continue;
            Mesh m = MESHES.get(key);
            if (m == null) MESHES.put(key, m = new Mesh());
            if (!m.dirty && !m.moving || builds >= BUILDS_PER_FRAME) continue;
            m.dirty = false;
            builds++;
            o.cam = new Vec3(cx << 4, 0, cz << 4);
            BufferBuilder b = BUILDER;
            o.b = b;
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            int quads = 0;
            for (Cell c : e.getValue()) quads += emit(level, c, level(c), pos, now);
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
        lastBuilds = builds;
        Matrix4f view = event.getPoseStack().last().pose();
        if (look == null) {
            drawPack(event, cam, view, reach2);
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (!WaterLook.copyScene(main)) return;
        WaterLook.setUp(look, level, cam, partial, event.getProjectionMatrix(), main, o.ox, o.oz);
        com.mojang.blaze3d.shaders.Uniform offset = look.getUniform("ChunkOffset");
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
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

    /**
     * The cached chunks with the shader pack's own translucent program (AFL_WATER): moved by its ChunkOffset as the
     * terrain's chunks are.
     */
    private static void drawPack(RenderLevelStageEvent event, Vec3 cam, Matrix4f view, double reach2) {
        RenderType type = RenderType.translucent();
        type.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        int quads = 0;
        if (shader != null) {
            com.mojang.blaze3d.shaders.Uniform offset = shader.CHUNK_OFFSET;
            for (var e : MESHES.long2ObjectEntrySet()) {
                Mesh m = e.getValue();
                if (m.buffer == null || m.quads == 0) continue;
                long key = e.getLongKey();
                int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
                double dx = (cx << 4) + 8 - cam.x, dz = (cz << 4) + 8 - cam.z;
                if (dx * dx + dz * dz > reach2) continue;
                if (offset != null) offset.set((float) ((cx << 4) - cam.x), (float) -cam.y, (float) ((cz << 4) - cam.z));
                m.buffer.bind();
                m.buffer.drawWithShader(view, event.getProjectionMatrix(), shader);
                quads += m.quads;
            }
            VertexBuffer.unbind();
            if (offset != null) offset.set(0F, 0F, 0F);
        }
        type.clearRenderState();
        lastQuads = quads;
    }

    /** One chunk's water on the GPU (the water program's path, the shader pack's). */
    private static final class Mesh {
        VertexBuffer buffer;
        int quads;
        boolean dirty = true;
        /** Its water, or a neighbour's, moves this tick: rebuilt every frame. */
        boolean moving;

        void close() {
            if (buffer != null) buffer.close();
            buffer = null;
            quads = 0;
        }
    }

    private static final Long2ObjectOpenHashMap<Mesh> MESHES = new Long2ObjectOpenHashMap<>();
    private static double meshOx = Double.NaN, meshOz = Double.NaN;
    /** The path the meshes were built for: 0 the water program, 1 the shader pack's (AFL_WATER). */
    private static int meshMode = -1;
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
            float[] corners = g >= 0 ? TerrainSmooth.top(level, chunk, x, yBlock, z, g) : null;   // R2b: the smooth top
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
                    // nw, sw, se, ne: the quad's two triangles split on the diagonal the smoothed top folds along there
                    // (starting at sw for sw-ne, SmoothRules#swNe: a fan's creases, a plain top split sw-ne)
                    int[] idx = {j * 3 + i, (j + 1) * 3 + i, (j + 1) * 3 + i + 1, j * 3 + i + 1};
                    int[][] at = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
                    int first = corners != null && SmoothRules.swNe(corners, i * 0.5, j * 0.5, (i + 1) * 0.5, (j + 1) * 0.5) ? 1 : 0;
                    for (int r = 0; r < 4; r++) {
                        int k = (r + first) & 3;
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
    private static int side(ClientLevel level, Cell c, float l, double ax, double az, float ha, double bx, double bz, float hb, int nx, int nz,
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
        /** A river's clear water (1) or rain water standing on earth (0): Color.r on the water program's top. */
        float clear;
        /**
         * A shader pack draws the water as its own (WaterShaderData, AFL_WATER): the pack's attributes instead of the plain
         * surface's; kind the cell's kind of water (WaterShaderData.STILL, RIVER); the top quad's own normal (normal()).
         */
        boolean pack;
        int kind;
        float qnx, qny = 1, qnz;
        int light;
        double ox, oz;
        Vec3 cam;

        /**
         * A surface vertex at world (x, z) from the mesh's vertex: (u, v) across the cell for the plain texture; up its top,
         * else its underside. The water program's top carries its flow in the normal as (vx / 4, 1, vz / 4), white water
         * in Color.g, how far it is shown in Color.b, and a film 0 in Color.a (afl_water.vsh).
         */
        void top(double x, double z, float u, float v, LiquidSurfaceMesh.Vertex m, boolean up) {
            float px = (float) (x - cam.x), py = (float) (m.height - cam.y), pz = (float) (z - cam.z);
            if (look) {
                float fx = up ? Math.max(-1, Math.min(1, m.vx / 4)) : 0, fz = up ? Math.max(-1, Math.min(1, m.vz / 4)) : 0;
                b.vertex(px, py, pz).color(clear, up ? m.foam : 0F, fade * m.fade, m.draped ? 0F : 1F).uv((float) (x - ox), (float) (z - oz)).uv2(light)
                        .normal(fx, up ? 1 : -1, fz).endVertex();
            } else if (pack) {
                // the pack's water: flow (m/s) and white water on the top, how far it is shown, the kind in u, the
                // face's own normal (down under it)
                float fx = up ? Math.max(-1, Math.min(1, m.vx / WaterShaderData.FLOW_RANGE)) : 0, fz = up ? Math.max(-1, Math.min(1, m.vz / WaterShaderData.FLOW_RANGE)) : 0;
                float sign = up ? 1 : -1;
                b.vertex(px, py, pz).color(0.5F + fx / 2, 0.5F + fz / 2, up ? m.foam : 0F, fade * m.fade).uv(packU(u), packV(v)).uv2(light)
                        .normal(qnx * sign, qny * sign, qnz * sign).endVertex();
            } else {
                b.vertex(px, py, pz).color(R, G, B, alpha * m.fade).uv(u0 + (u1 - u0) * u, v0 + (v1 - v0) * v).uv2(light)
                        .normal(0, up ? 1 : -1, 0).endVertex();
            }
        }

        /**
         * The normal of a top quad (size m on a side) from its corners' heights, counter-clockwise from above as emit()
         * and flat() walk them: nw (a), sw (b), se (c), ne (d); up.
         */
        void normal(float a, float b, float c, float d, float size) {
            // the diagonals: nw -> se (+size, +size) and sw -> ne (+size, -size), crossed upward
            float dx1 = size, dy1 = c - a, dz1 = size, dx2 = size, dy2 = d - b, dz2 = -size;
            float nx = dy1 * dz2 - dz1 * dy2, ny = dz1 * dx2 - dx1 * dz2, nz = dx1 * dy2 - dy1 * dx2;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (ny < 0) len = -len;
            if (Math.abs(len) < 1e-6F) {
                qnx = qnz = 0;
                qny = 1;
            } else {
                qnx = nx / len;
                qny = ny / len;
                qnz = nz / len;
            }
        }

        /**
         * The kind's texture coordinates inside the surface's sprite (WaterShaderData): the middle half of the kind's slot
         * spread over the face (u, v 0..1 across it), so the face's tangent stays defined and every point of it reads
         * the kind as floor(share of the sprite x KINDS).
         */
        float packU(float u) {
            return u0 + (u1 - u0) * (kind + 0.25F + 0.5F * u) / WaterShaderData.KINDS;
        }

        float packV(float v) {
            return v0 + (v1 - v0) * (0.25F + 0.5F * v);
        }

        /** A side vertex: toSurface the height up to the surface above it; u along the edge. */
        void side(double x, float y, double z, float toSurface, float u, int nx, int nz) {
            put(x, y, z, toSurface, look ? (float) (nx != 0 ? z - oz : x - ox) : pack ? packU(u) : u0 + (u1 - u0) * u,
                    look ? y : pack ? packV(Math.min(1, toSurface)) : v0 + (v1 - v0) * Math.min(1, toSurface), nx, 0, nz);
        }

        private void put(double x, float y, double z, float toSurface, float u, float v, float nx, float ny, float nz) {
            float px = (float) (x - cam.x), py = (float) (y - cam.y), pz = (float) (z - cam.z);
            if (look) {
                // the program's own inputs: height up to the surface / 8 m; no white water on a side
                b.vertex(px, py, pz).color(Math.min(1, toSurface / 8), 0F, fade, 1F).uv(u, v).uv2(light)
                        .normal(nx, ny, nz).endVertex();
            } else if (pack) {
                // a side: still, no white water
                b.vertex(px, py, pz).color(0.5F, 0.5F, 0F, fade).uv(u, v).uv2(light).normal(nx, ny, nz).endVertex();
            } else {
                b.vertex(px, py, pz).color(R, G, B, alpha).uv(u, v).uv2(light).normal(nx, ny, nz).endVertex();
            }
        }
    }

    private static final Out OUT = new Out();
    /** Diagnostics: whether the last frame used the water program. */
    private static boolean lastLook;

    /** Metres of AFL water over a point in it (its column's surface above, its floor below), else 0 (WaterShaderData's eye). */
    static float waterAbove(double x, double y, double z) {
        Cell[] cells = COLUMNS.get(column((int) Math.floor(x), (int) Math.floor(z)));
        if (cells == null) return 0;
        for (Cell c : cells) {
            float l = level(c);
            if (y >= c.floorM() && y < l) return (float) (l - y);
        }
        return 0;
    }

    /** Dev readout ({@code /afl_render water status}). */
    public static String status() {
        int cells = 0;
        for (Cell[] c : CHUNKS.values()) cells += c.length;
        return String.format(Locale.ROOT, "%s; %d chunks, %d wet cells; last frame %d quads, %d chunks rebuilt, %d us, %s%s",
                enabled ? "on" : "off", CHUNKS.size(), cells, lastQuads, lastBuilds, lastMicros, lastLook ? "AFL water program (cached per chunk)" : OUT.pack ? "the shader pack's water (AFL_WATER, cached per chunk)" : "plain surface",
                failed ? "; FAILED (see log)" : "") + "; " + WaterShaderData.status();
    }
}
