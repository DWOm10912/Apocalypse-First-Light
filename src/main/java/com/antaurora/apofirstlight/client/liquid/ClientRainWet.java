package com.antaurora.apofirstlight.client.liquid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.LiquidRenderTypes;
import com.antaurora.apofirstlight.liquid.world.LiquidPackets;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Ground wet from natural rain (docs/gameplay/water_rain_v1_design.md 4.1, package 3a): the server's LiquidRain says per
 * chunk which columns are dark and how fast they pale (LiquidPackets.Wet); this draws them the way ClientLiquid draws the
 * ground under a film (falling water 13): one continuous darkening, each column's value a field whose vertices are the
 * means of the columns around, block/liquid/wet_mask breaking it up, grass darkening less than soil. Under a roof or
 * anything else the rain stops on, nothing (the server never wets it).
 * <ul>
 *   <li>Rain wets the whole open ground, so each chunk's quads live on the GPU (a VertexBuffer, chunk-relative) and a
 *   frame only draws them (the first build wrote 116k vertices a frame, 9 ms, in game 2026-10-25): rebuilt when the
 *   chunk's data comes or a neighbour's does, and every {@link #PALE_EVERY} ticks while it pales. A column whose field is
 *   even is one quad, on a smoothed slope too (its corners); only an uneven one is 2 x 2.</li>
 *   <li>Drawn with the decal type's own state and shader (a shader pack's entity program under Oculus), within
 *   {@link #RANGE}, fading out over the last {@link #FADE} m by the colour modulator.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientRainWet {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final double RANGE = 64, FADE = 16;
    private static final short DRY = Short.MIN_VALUE;
    /** Chunks rebuilt a frame at most, and how often a paling chunk is (ticks). */
    private static final int BUILDS_PER_FRAME = 2, PALE_EVERY = 100;
    private static final com.mojang.blaze3d.vertex.BufferBuilder BUILDER = new com.mojang.blaze3d.vertex.BufferBuilder(1 << 16);

    static final class Wet {
        final int cx, cz;
        final short[] y = new short[256];
        final float[] dark = new float[256];
        final int[] fade = new int[256];
        long received;
        boolean paling, dirty = true;
        /** The chunk's quads on the GPU (null: none). */
        com.mojang.blaze3d.vertex.VertexBuffer buffer;
        int vertices;
        long built;

        void close() {
            if (buffer != null) buffer.close();
            buffer = null;
            vertices = 0;
        }

        Wet(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            java.util.Arrays.fill(y, DRY);
        }

        float value(int i, long now) {
            if (y[i] == DRY) return 0;
            if (fade[i] == 0) return dark[i];
            return Math.max(0, dark[i] - (now - received) / (float) fade[i]);
        }
    }

    private static final Long2ObjectOpenHashMap<Wet> CHUNKS = new Long2ObjectOpenHashMap<>();
    private static ClientLevel tracked;
    private static TextureAtlasSprite builtWith;
    private static boolean failed;
    /**
     * Off (user 2026-10-25, third look: "如果只能这么丑，干脆别做暗面了呗，效果交给光影"): no darkening is drawn, neither
     * the rain's nor a film's (ClientLiquid#drawDamp); the data still comes, for a shader pack's wet ground later.
     * /afl_render wet on draws both again (dev).
     */
    public static boolean enabled = false;
    static int lastVertices, lastBuilds;
    static long lastMicros;

    private ClientRainWet() {}

    public static void receive(LiquidPackets.Wet p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != tracked) clear(mc.level);
        long key = ChunkPos.asLong(p.chunkX(), p.chunkZ());
        if (p.column().length == 0) {
            Wet gone = CHUNKS.remove(key);
            if (gone != null) {
                gone.close();
                neighboursDirty(p.chunkX(), p.chunkZ());
            }
            return;
        }
        Wet w = CHUNKS.get(key);
        if (w == null) CHUNKS.put(key, w = new Wet(p.chunkX(), p.chunkZ()));
        java.util.Arrays.fill(w.y, DRY);
        w.paling = false;
        for (int k = 0; k < p.column().length; k++) {
            int i = p.column()[k] & 255;
            w.y[i] = p.y()[k];
            w.dark[i] = (p.dark()[k] & 255) / 255F;
            w.fade[i] = p.fade()[k];
            w.paling |= p.fade()[k] > 0;
        }
        w.received = mc.level == null ? 0 : mc.level.getGameTime();
        w.dirty = true;
        neighboursDirty(p.chunkX(), p.chunkZ());
    }

    private static void neighboursDirty(int cx, int cz) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Wet n = CHUNKS.get(ChunkPos.asLong(cx + d[0], cz + d[1]));
            if (n != null) n.dirty = true;
        }
    }

    private static void clear(ClientLevel level) {
        for (Wet w : CHUNKS.values()) w.close();
        CHUNKS.clear();
        tracked = level;
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() == null || !event.getLevel().isClientSide()) return;
        Wet gone = CHUNKS.remove(event.getChunk().getPos().toLong());
        if (gone != null) gone.close();
    }

    /** Column (x, z)'s darkness now, 0 where it is dry or not known. */
    private static float at(int x, int z, long now) {
        Wet w = CHUNKS.get(ChunkPos.asLong(x >> 4, z >> 4));
        return w == null ? 0 : w.value((x & 15) + 16 * (z & 15), now);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !enabled || failed || CHUNKS.isEmpty()) return;
        if (AflShaderCompat.activeShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != tracked) return;
        long t0 = System.nanoTime();
        try {
            draw(mc, level, event.getPoseStack(), event.getProjectionMatrix(), event.getCamera().getPosition());
        } catch (RuntimeException e) {
            failed = true;
            LOGGER.error("AFL water: rain-wet ground is switched off after an error", e);
        }
        lastMicros = (System.nanoTime() - t0) / 1000;
    }

    private static void draw(Minecraft mc, ClientLevel level, PoseStack pose, Matrix4f projection, Vec3 cam) {
        long now = level.getGameTime();
        TextureAtlasSprite mask = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ClientLiquid.WET_MASK);
        if (mask != builtWith) {
            builtWith = mask;
            for (Wet w : CHUNKS.values()) w.dirty = true;
        }
        int builds = 0, vertices = 0;
        double reach = RANGE + 12;
        // rebuilds first, nearest the camera first among those due
        Wet[] due = new Wet[BUILDS_PER_FRAME];
        double[] dueD = new double[BUILDS_PER_FRAME];
        java.util.Arrays.fill(dueD, Double.MAX_VALUE);
        for (Wet w : CHUNKS.values()) {
            if (!(w.dirty || w.paling && now - w.built >= PALE_EVERY)) continue;
            double dx = (w.cx << 4) + 8 - cam.x, dz = (w.cz << 4) + 8 - cam.z, d = dx * dx + dz * dz;
            if (d > reach * reach) continue;
            for (int k = 0; k < BUILDS_PER_FRAME; k++) {
                if (d < dueD[k]) {
                    for (int m = BUILDS_PER_FRAME - 1; m > k; m--) {
                        due[m] = due[m - 1];
                        dueD[m] = dueD[m - 1];
                    }
                    due[k] = w;
                    dueD[k] = d;
                    break;
                }
            }
        }
        for (Wet w : due) {
            if (w == null) continue;
            build(level, w, now, mask);
            builds++;
        }
        RenderType type = LiquidRenderTypes.DECAL;
        type.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0F, 0F, 0F);
            Matrix4f view = new Matrix4f();
            for (Wet w : CHUNKS.values()) {
                if (w.buffer == null || w.vertices == 0) continue;
                double dx = (w.cx << 4) + 8 - cam.x, dz = (w.cz << 4) + 8 - cam.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > reach) continue;
                float fade = (float) Math.min(1, Math.max(0, (reach - d) / FADE));
                if (fade <= 0) continue;
                view.set(pose.last().pose()).translate((float) ((w.cx << 4) - cam.x), (float) -cam.y, (float) ((w.cz << 4) - cam.z));
                RenderSystem.setShaderColor(1, 1, 1, fade);
                w.buffer.bind();
                w.buffer.drawWithShader(view, projection, shader);
                vertices += w.vertices;
            }
            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        type.clearRenderState();
        lastVertices = vertices;
        lastBuilds = builds;
    }

    /**
     * The chunk's quads onto the GPU: each column not dry as one quad where the field is even over it (on a smoothed
     * slope, through its corners: the quad's two triangles split on the top's diagonal, SmoothRules#swNe; a fan top always in
     * quarters), else 2 x 2 quads
     * with the field's values at their corners, on the ground the rain stops on (a smoothed top, a full block top).
     */
    private static void build(ClientLevel level, Wet w, long now, TextureAtlasSprite mask) {
        w.dirty = false;
        w.built = now;
        w.paling = false;
        LevelChunk chunk = level.getChunkSource().getChunk(w.cx, w.cz, false);
        if (chunk == null) {
            w.close();
            return;
        }
        com.mojang.blaze3d.vertex.BufferBuilder b = BUILDER;
        b.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY);
        int n = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float[] f = new float[9];
        double[] h = new double[9];
        boolean smooth = TerrainSmooth.applies(level);
        for (int i = 0; i < 256; i++) {
            if (w.y[i] == DRY) continue;
            int lx = i & 15, lz = i >> 4, x = (w.cx << 4) + lx, z = (w.cz << 4) + lz, yb = w.y[i];
            float c = w.value(i, now);
            if (c <= 0.01F) continue;
            if (w.fade[i] > 0) w.paling = true;
            float e = at(x + 1, z, now), wv = at(x - 1, z, now), s = at(x, z + 1, now), nn = at(x, z - 1, now);
            float ne = at(x + 1, z - 1, now), nw = at(x - 1, z - 1, now), se = at(x + 1, z + 1, now), sw = at(x - 1, z + 1, now);
            f[0] = (c + wv + nn + nw) / 4; f[1] = (c + nn) / 2; f[2] = (c + e + nn + ne) / 4;
            f[3] = (c + wv) / 2;           f[4] = c;            f[5] = (c + e) / 2;
            f[6] = (c + wv + s + sw) / 4;  f[7] = (c + s) / 2;  f[8] = (c + e + s + se) / 4;
            BlockState top = chunk.getBlockState(pos.set(x, yb, z));
            long g = smooth ? TerrainSmooth.geometry(level, chunk, x, yb, z) : -1;
            float[] corners = g >= 0 ? TerrainSmooth.top(level, chunk, x, yb, z, g) : null;   // R2b: the smooth top
            // only a smoothed slope's top or a block whose whole top face is flat at its top: stairs, roofs over steps, fences
            // and walls are not ground, a flat quad over their steps showed as dark stripes (in game 2026-10-25)
            if (corners == null && !Block.isFaceFull(top.getCollisionShape(level, pos), Direction.UP)) continue;
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) h[j * 3 + k] = corners != null ? yb + SmoothRules.surface(corners, k * 0.5, j * 0.5) : yb + 1;
            }
            float strength = 255 * (top.is(Blocks.GRASS_BLOCK) ? ClientLiquid.WET_GRASS : ClientLiquid.WET_SOIL);
            int light = LevelRenderer.getLightColor(level, pos.set(x, yb + 1, z));
            float tu = (x & 3) / 4F, tv = (z & 3) / 4F;
            boolean even = true;
            for (float v : f) even &= Math.abs(v - c) < 0.02F;
            // a fan top (dig R1) takes the four quarters below, which follow its creases
            if (even && (corners == null || corners.length < 9)) {
                // nw, sw, se, ne: the corners of the 3 x 3 (0, 6, 8, 2); from sw when the top splits sw-ne (SmoothRules#swNe)
                int[] idx = {0, 6, 8, 2};
                double[][] at = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
                int first = corners != null && SmoothRules.swNe(corners) ? 1 : 0;
                for (int r = 0; r < 4; r++) {
                    int q = (r + first) & 3;
                    put(b, lx + at[q][0], h[idx[q]], lz + at[q][1], mask, tu + at[q][0] / 4, tv + at[q][1] / 4, strength * c, light);
                }
                n += 4;
                continue;
            }
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    int[] idx = {j * 3 + k, (j + 1) * 3 + k, (j + 1) * 3 + k + 1, j * 3 + k + 1};
                    int[][] at = {{k, j}, {k, j + 1}, {k + 1, j + 1}, {k + 1, j}};
                    int first = corners != null && SmoothRules.swNe(corners, k * 0.5, j * 0.5, (k + 1) * 0.5, (j + 1) * 0.5) ? 1 : 0;
                    for (int r = 0; r < 4; r++) {
                        int q = (r + first) & 3;
                        double fx = at[q][0] * 0.5, fz = at[q][1] * 0.5;
                        put(b, lx + fx, h[idx[q]], lz + fz, mask, tu + fx / 4, tv + fz / 4, strength * f[idx[q]], light);
                    }
                    n += 4;
                }
            }
        }
        com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer rendered = b.endOrDiscardIfEmpty();
        if (rendered == null) {
            w.close();
            return;
        }
        if (w.buffer == null) w.buffer = new com.mojang.blaze3d.vertex.VertexBuffer(com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
        w.buffer.bind();
        w.buffer.upload(rendered);
        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        w.vertices = n;
    }

    /**
     * A vertex, a few millimetres over the ground so it does not fight it. Black: only its alpha darkens what is under it,
     * so the shader's lighting and the normal make no difference.
     */
    private static void put(com.mojang.blaze3d.vertex.BufferBuilder b, double x, double y, double z, TextureAtlasSprite mask, double u, double v, float alpha, int light) {
        b.vertex(x, y + 0.005, z).color(0, 0, 0, Math.round(alpha)).uv(mask.getU(u * 16), mask.getV(v * 16))
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
    }

    public static String status() {
        int columns = 0;
        for (Wet w : CHUNKS.values()) for (short y : w.y) if (y != DRY) columns++;
        return String.format(Locale.ROOT, "%s; %d chunks, %d wet columns; last frame %d vertices, %d chunks rebuilt, %d us%s",
                enabled ? "on" : "off", CHUNKS.size(), columns, lastVertices, lastBuilds, lastMicros, failed ? "; FAILED (see log)" : "");
    }
}
