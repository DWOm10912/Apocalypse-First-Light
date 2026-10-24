package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.client.TerrainSmoothClient;
import com.antaurora.apofirstlight.vegetation.MeshPlantBlock;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The near layer of the mesh plants (AFL vegetation, docs/gameplay/vegetation_v1_design.md 12; user 2026-10-23: "完全没有
 * 真实物理，还是最简单的碰到立刻就变形"): the blocks around anyone walking through the turf are taken out of the chunk mesh
 * and drawn here, every blade simulated.
 * <ul>
 * <li>Which blocks: those within {@value #REACH} m of a living thing's middle (and {@value #RANGE} m of the camera, at the
 * near level), at most {@value #MAX_BLOCKS}, nearest first. A block leaves when nobody has been within {@value #KEEP} m for
 * {@value #RELEASE_FRAMES} frames and its blades are still.</li>
 * <li>Hand-over: a block joining is hidden from the chunk mesh (PlantModel asks {@link #hides}) and its section rebuilt; a
 * block leaving is shown again and drawn here {@value #HANDOVER_SECONDS} s more. At rest a blade here is exactly the
 * chunk mesh's (the same PlantShape pose and writer, brightness and wind code), so the overlap does not show.</li>
 * <li>A blade is its rest pose's points (4 for the turf, 5-6 for tall leaves and stems): the foot fixed, each other point
 * drawn back to its rest place by a damped spring (a turf blade 3.6, 3.2, 2.8 Hz from the foot out; slower the longer the
 * blade, a 2 m cattail leaf about 1.7 Hz down to 1.1 at the tip; the points close in frequency so a blade swings as one
 * and its tip does not whip; a stem 1.8 times stiffer; damping ratio 0.45: a blade let go swings back once and settles;
 * one pressed down 2.5 times stiffer, damping ratio 0.9: it goes down and stays), its segments kept their length. Its rigid parts (spikes, flower heads, stem leaves) turn and move with it. Bodies are upright capsules from the feet to the hip (radius a third of the width): a
 * point inside is put back on the surface and carried a little along with the body, so blades part around legs, are
 * dragged and slide off, then spring back.</li>
 * <li>Footprints are the server's (MeshPlantBlock, VegetationFootfalls): when a block's state changes here its blades' rest
 * poses change, the folded ones get a downward shove and fall to their new place in a fifth of a second, and stay.</li>
 * <li>Drawing (13.10; measured 2026-10-24: writing every vertex every frame through the buffer builder was 70% of the render
 * thread without a pack, 90% with Aureveil): a block whose blades are all still keeps its vertices in its own vertex
 * buffers (block coordinates, built once, again when its light, the pack or the sprites change); the blocks that move go
 * into one pair of buffers built once a frame (camera-relative). Drawn after the solid terrain with the vanilla solid and
 * cutout types, the block's light and the biome's grass colour; without a pack by AFL's twins of the vanilla programs with
 * the plants' wind in the vertex stage (VegetationNearPrograms: the chunk mesh's wind code, so no wind on the CPU); with a
 * pack by the pack's programs for those types, in its shadow pass and its main pass, the block's place in the model-view
 * matrix. A pack that waves AFL's plants itself (VegetationShaderData, Aureveil's AFL_VEGETATION, 13.9) moves these with
 * the chunk mesh; any other pack leaves them still.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class VegetationDynamics {
    static final float REACH = 2.1F, KEEP = 2.8F, RANGE = 14, NEAR = 1.6F;
    static final int MAX_BLOCKS = 64, RELEASE_FRAMES = 10, MAX_SUBSTEPS = 6;
    /** A block leaving is drawn here this long more (s): its section's rebuild is off the render thread and lands a few frames later. */
    static final float HANDOVER_SECONDS = 1;
    private static final float SUBSTEP = 1 / 120F, ZETA = 0.45F;
    /** How much slower a blade's tip springs than its foot (0: all as one), and a stem's stiffness over a leaf's. */
    private static final float SPREAD = 0.35F, STEM_STIFF = 1.8F;
    /** How much of a body's motion a blade it touches is carried along by (a share of the body's speed). */
    private static final float DRAG = 0.2F;
    /** What lies or leans (a foot or a wheel pressed it): stiffer and nearly critically damped, down in a fifth of a second without bouncing (offline: 0.20 s to within 2 cm). */
    private static final float STILL_STIFF = 2.5F, STILL_ZETA = 0.9F;
    /** Bodies slower than this (m/s) stand still: the blades they hold go to sleep where they are (13.10). */
    private static final float CALM_SPEED = 0.05F;
    /** A folded blade's shove when the foot comes down (m/s, down). */
    private static final float SHOVE = 1.2F;
    /** Springs by frequency bins {@value #FREQ_STEP} Hz apart: this frame's per-substep factors (velocity kept, pull to rest). */
    private static final float FREQ_STEP = 0.05F;
    private static final int BINS = 400;
    private static final float[] KEEP_Z = new float[BINS], KEEP_STILL = new float[BINS], PULL = new float[BINS];

    /** Blocks drawn here and not by the chunk mesh (read by the chunk builders). */
    private static final Set<Long> HIDDEN = ConcurrentHashMap.newKeySet();
    private static final Long2ObjectOpenHashMap<Patch> PATCHES = new Long2ObjectOpenHashMap<>();
    private static final List<Body> BODIES = new ArrayList<>();
    private static final List<Body> NEAR_BODIES = new ArrayList<>();
    private static final Long2FloatOpenHashMap WANTED = new Long2FloatOpenHashMap();
    private static final Emitter EMITTER = new Emitter();
    /** The builders (solid, cutout) and this frame's buffers of the moving blocks (camera-relative). */
    private static final BufferBuilder[] BUILDERS = {new BufferBuilder(1 << 18), new BufferBuilder(1 << 18)};
    private static final VertexBuffer[] MOVING = new VertexBuffer[2];
    private static final int[] MOVING_COUNT = new int[2];
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static long lastNanos, frames, simNanos, drawNanos, joined, released, skipped, deferred, built = -1, passes, restBuilds;
    private static int simulated, movingBlocks, restBlocks, movingVertices;
    /** The atlas was stitched again (VegetationClient): every block's buffers are built afresh. */
    static volatile boolean spritesChanged;
    /** Dev switch (dev/VegetationPerfProbe): false leaves every block to the chunk mesh. */
    public static boolean enabled = true;
    /** Set (render thread) while a rebuild asked for is only for the plants (VegetationRebuildEmbeddiumMixin). */
    private static boolean deferring;

    private VegetationDynamics() {}

    /** Whether the chunk mesh leaves this block to the near layer (any thread). */
    static boolean hides(BlockPos pos) {
        return !HIDDEN.isEmpty() && HIDDEN.contains(pos.asLong());
    }

    /** Whether the section rebuild being asked for is only for the plants: built off the render thread (12.5). */
    public static boolean deferRebuilds() {
        return deferring;
    }

    /**
     * A block changed on the client (VegetationRebuildMixin): true when it needs no rebuild at all (a mesh plant's state in a
     * block drawn here: the chunk mesh leaves it out either way; its section is rebuilt when the block leaves); a mesh plant
     * elsewhere is rebuilt off the render thread. {@link #plantChangeDone} after.
     */
    public static boolean plantChanged(BlockPos pos, BlockState old, BlockState now) {
        if (!(old.getBlock() instanceof MeshPlantBlock) || !(now.getBlock() instanceof MeshPlantBlock)) return false;
        if (hides(pos)) {
            skipped++;
            return true;
        }
        deferring = true;
        deferred++;
        return false;
    }

    public static void plantChangeDone() {
        deferring = false;
    }

    /** A living thing this frame: feet (x, y, z), top, velocity (m/s), radius. */
    private record Body(double x, double y, double z, double top, float vx, float vz, float r) {}

    /** One blade here: its rest pose, its points now and a substep ago, its segments' lengths (block coordinates). */
    private static final class Strand {
        final PlantShape.Pose pose;
        final float[] rest, pos, prev, len;
        /** Each point's spring (a frequency bin): standing, and pressed down (stiffer). */
        final int[] bin, stillBin;
        /** The rigid parts it carries, posed with its rest pose. */
        final List<Carried> parts = new ArrayList<>(2);
        boolean asleep = true;
        /** Asleep where a body standing still holds it (not at rest): simulated again when the body moves or leaves. */
        boolean held;

        Strand(PlantShape.Pose pose, float[] rest) {
            int n = pose.points();
            this.pose = pose;
            this.rest = rest;
            this.pos = rest.clone();
            this.prev = rest.clone();
            this.len = new float[n - 1];
            this.bin = new int[n];
            this.stillBin = new int[n];
            for (int i = 0; i < len.length; i++) {
                int a = i * 3, b = a + 3;
                len[i] = (float) Math.sqrt(sq(rest[b] - rest[a]) + sq(rest[b + 1] - rest[a + 1]) + sq(rest[b + 2] - rest[a + 2]));
            }
            // slower along the blade and the longer it is (a turf blade 3.2 Hz a third up, a 2 m cattail leaf half that); stems stiffer
            float base = Mth.clamp(4.1F * (float) Math.sqrt(0.35F / Math.max(0.05F, pose.blade.len)), 1.2F, 6);
            if (pose.blade.sides > 0) base *= STEM_STIFF;
            for (int i = 0; i < n; i++) {
                float hz = base * (1 - SPREAD * pose.f[i]);
                bin[i] = Math.min(BINS - 1, (int) (hz / FREQ_STEP));
                stillBin[i] = Math.min(BINS - 1, (int) (hz * STILL_STIFF / FREQ_STEP));
            }
        }
    }

    /** A rigid part on a blade: where along it, and its quads in the blade's rest pose. */
    private record Carried(float at, List<PlantShape.Quad> quads) {}

    /** One block here. */
    private static final class Patch {
        final BlockPos pos;
        BlockState state;
        List<Strand> strands = new ArrayList<>();
        float r = 1, g = 1, b = 1;
        int species;
        /** Seconds left drawn here after leaving, or -1. */
        float handover = -1;
        int light, quiet;
        /** Its vertices while every blade is still (block coordinates; solid, cutout), valid for the light, the pack mode then. */
        final VertexBuffer[] rest = new VertexBuffer[2];
        final int[] restCount = new int[2];
        boolean restValid, restPack;
        int restLight;

        Patch(BlockPos pos) {
            this.pos = pos;
        }

        /** Its buffers freed (render thread). */
        void close() {
            for (int i = 0; i < 2; i++)
                if (rest[i] != null) {
                    rest[i].close();
                    rest[i] = null;
                }
            restValid = false;
        }
    }

    // ---------------------------------------------------------------------------------------------- frame

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.gameRenderer == null || VegetationClient.override >= 0 || !enabled) {
            releaseAll(mc);
            return;
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.05F, (now - lastNanos) / 1e9F);
        lastNanos = now;
        if (mc.isPaused()) dt = 0;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        bodies(mc, level, cam, event.renderTickTime);
        choose(mc, level, cam, dt);
        simulate(dt);
        frames++;
        simNanos += System.nanoTime() - now;
    }

    private static void bodies(Minecraft mc, ClientLevel level, Vec3 cam, float partial) {
        BODIES.clear();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(cam, cam).inflate(RANGE + REACH, 8, RANGE + REACH))) {
            if (e.isSpectator() || e.isPassenger() || !e.isAlive()) continue;
            Vec3 at = e.getPosition(partial);
            float r = Mth.clamp(e.getBbWidth() * 0.35F, 0.08F, 0.45F);
            BODIES.add(new Body(at.x, at.y, at.z, at.y + Math.min(e.getBbHeight(), 1.0F), (float) (e.getX() - e.xo) * 20, (float) (e.getZ() - e.zo) * 20, r));
        }
    }

    /** The blocks wanted near the bodies: new ones join, gone ones leave when still, changed ones take their new poses. */
    private static void choose(Minecraft mc, ClientLevel level, Vec3 cam, float dt) {
        Long2FloatOpenHashMap wanted = WANTED;
        wanted.clear();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Body body : BODIES) {
            if (sq(body.x - cam.x) + sq(body.z - cam.z) > sq(RANGE)) continue;
            int x0 = Mth.floor(body.x - REACH), x1 = Mth.floor(body.x + REACH), z0 = Mth.floor(body.z - REACH), z1 = Mth.floor(body.z + REACH);
            int y0 = Mth.floor(body.y) - 1, y1 = Mth.floor(body.y) + 1;
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    float d = (float) Math.sqrt(sq(x + 0.5 - body.x) + sq(z + 0.5 - body.z));
                    if (d > REACH) continue;
                    for (int y = y0; y <= y1; y++) {
                        BlockState s = level.getBlockState(p.set(x, y, z));
                        if (!(s.getBlock() instanceof MeshPlantBlock) || VegetationClient.stage(p) != 0) continue;
                        long k = p.asLong();
                        if (!wanted.containsKey(k) || wanted.get(k) > d) wanted.put(k, d);
                    }
                }
        }
        // at most MAX_BLOCKS, nearest a body first
        long[] keys = wanted.keySet().toLongArray();
        if (keys.length > MAX_BLOCKS) {
            Long[] boxed = new Long[keys.length];
            for (int i = 0; i < keys.length; i++) boxed[i] = keys[i];
            java.util.Arrays.sort(boxed, (a, b) -> Float.compare(wanted.get((long) a), wanted.get((long) b)));
            keys = new long[MAX_BLOCKS];
            for (int i = 0; i < MAX_BLOCKS; i++) keys[i] = boxed[i];
        }
        for (long k : keys) {
            Patch patch = PATCHES.get(k);
            if (patch == null) {
                patch = new Patch(BlockPos.of(k));
                PATCHES.put(k, patch);
                joined++;
            }
            if (patch.handover >= 0 || !HIDDEN.contains(k)) {
                patch.handover = -1;
                HIDDEN.add(k);
                dirty(mc, patch.pos);
            }
            patch.quiet = 0;
        }
        var it = PATCHES.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            Patch patch = e.getValue();
            BlockState s = level.getBlockState(patch.pos);
            if (!(s.getBlock() instanceof MeshPlantBlock)) {
                if (HIDDEN.remove(e.getLongKey())) dirty(mc, patch.pos);
                patch.close();
                it.remove();
                continue;
            }
            if (s != patch.state) repose(level, patch, s);
            patch.light = LevelRenderer.getLightColor(level, patch.pos);
            if (patch.handover >= 0) {
                if ((patch.handover -= dt) < 0) { patch.close(); it.remove(); released++; }
                continue;
            }
            if (patch.quiet > 0 && ++patch.quiet >= RELEASE_FRAMES && still(patch) && !near(patch, KEEP)) {
                HIDDEN.remove(e.getLongKey());
                dirty(mc, patch.pos);
                patch.handover = HANDOVER_SECONDS;
            } else if (patch.quiet == 0) patch.quiet = 1;   // counted from the next frame unless wanted again
        }
    }

    /** A block's blades for its (new) state: the same blades keep their points and spring to their new rest; folded ones are shoved down. */
    private static void repose(ClientLevel level, Patch patch, BlockState s) {
        boolean first = patch.state == null;
        patch.state = s;
        if (first) {
            int c = BiomeColors.getAverageGrassColor(level, patch.pos);
            patch.r = (c >> 16 & 255) / 255F;
            patch.g = (c >> 8 & 255) / 255F;
            patch.b = (c & 255) / 255F;
            patch.species = ((MeshPlantBlock) s.getBlock()).species.ordinal();
        }
        PlantShape.Press press = PlantModel.press(PlantModel.pose(s));
        float[] corners = TerrainSmoothClient.groundCorners(level, patch.pos.below());
        var old = new IdentityHashMap<PlantShape.Blade, Strand>();
        for (Strand st : patch.strands) old.put(st.pose.blade, st);
        var strands = new ArrayList<Strand>();
        float[] from = new float[6], to = new float[6], m = new float[9];
        for (var clump : PlantShape.clumps(patch.species, PlantModel.variant(patch.pos), 0)) {
            PlantShape.Pose[] poses = PlantShape.poses(clump, press);
            float dy = corners == null ? 0 : (float) SmoothRules.surface(corners, clump.x, clump.z) - 1;
            Strand[] made = new Strand[poses.length];
            for (int i = 0; i < poses.length; i++) {
                PlantShape.Pose pose = poses[i];
                if (pose == null) continue;
                float[] rest = pose.pts.clone();
                for (int k = 1; k < rest.length; k += 3) rest[k] += dy;
                Strand st = new Strand(pose, rest);
                Strand was = old.get(pose.blade);
                if (was != null && was.pos.length == st.pos.length) {
                    System.arraycopy(was.pos, 0, st.pos, 0, st.pos.length);
                    System.arraycopy(was.prev, 0, st.prev, 0, st.prev.length);
                    if (pose.sprite == PlantShape.STILL && was.pose.sprite == PlantShape.STANDING)
                        for (int k = 4; k < st.prev.length; k += 3) st.prev[k] = st.pos[k] + SHOVE * SUBSTEP;   // the foot comes down
                    st.asleep = false;
                }
                made[i] = st;
                strands.add(st);
            }
            for (var piece : clump.pieces) {
                Strand st = made[piece.blade()];
                if (st == null) continue;
                PlantShape.Pose grown = clump.grown[piece.blade()];
                PlantShape.frame(grown.pts, grown.f, piece.at(), from);
                PlantShape.frame(st.rest, st.pose.f, piece.at(), to);
                PlantShape.turn(from, to, m);
                var quads = new ArrayList<PlantShape.Quad>(piece.quads().size());
                for (var q : piece.quads()) quads.add(PlantShape.carried(piece, q, from, to, m, st.pose.sprite));
                st.parts.add(new Carried(piece.at(), quads));
            }
        }
        patch.strands = strands;
    }

    private static boolean still(Patch patch) {
        for (Strand st : patch.strands) if (!st.asleep) return false;
        return true;
    }

    private static boolean near(Patch patch, float reach) {
        for (Body body : BODIES)
            if (sq(patch.pos.getX() + 0.5 - body.x) + sq(patch.pos.getZ() + 0.5 - body.z) < sq(reach) && Math.abs(body.y - patch.pos.getY()) < 2) return true;
        return false;
    }

    /** The block's section rebuilt, off the render thread (12.5): a frame never waits for a hand-over. */
    private static void dirty(Minecraft mc, BlockPos pos) {
        // no level (logging out, quitting): Embeddium's sections are gone, and a new level rebuilds everything anyway
        if (mc.levelRenderer == null || mc.level == null) return;
        deferring = true;
        try {
            mc.levelRenderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        } finally {
            deferring = false;
        }
    }

    private static void releaseAll(Minecraft mc) {
        if (PATCHES.isEmpty() && HIDDEN.isEmpty()) return;
        for (Patch patch : PATCHES.values()) {
            if (HIDDEN.contains(patch.pos.asLong())) dirty(mc, patch.pos);
            patch.close();
        }
        PATCHES.clear();
        HIDDEN.clear();
        lastNanos = 0;
    }

    // ------------------------------------------------------------------------------------------- simulation

    private static void simulate(float dt) {
        simulated = 0;
        if (dt <= 0) return;
        int n = Math.min(MAX_SUBSTEPS, Math.max(1, (int) Math.ceil(dt / SUBSTEP)));
        float h = dt / n;
        for (int b = 0; b < BINS; b++) {
            double w = Math.PI * 2 * (b + 0.5) * FREQ_STEP;
            KEEP_Z[b] = (float) Math.exp(-2 * ZETA * w * h);
            KEEP_STILL[b] = (float) Math.exp(-2 * STILL_ZETA * w * h);
            PULL[b] = (float) (w * w * h * h);
        }
        List<Body> near = NEAR_BODIES;
        for (Patch patch : PATCHES.values()) {
            near.clear();
            for (Body body : BODIES)
                if (sq(patch.pos.getX() + 0.5 - body.x) + sq(patch.pos.getZ() + 0.5 - body.z) < sq(NEAR) && body.top > patch.pos.getY() - 1 && body.y < patch.pos.getY() + 3)
                    near.add(body);
            boolean calm = true;
            for (Body body : near) if (body.vx * body.vx + body.vz * body.vz > CALM_SPEED * CALM_SPEED) calm = false;
            for (Strand st : patch.strands) {
                // asleep: at rest with nobody near, or held by bodies standing still; a held one springs back once they leave
                if (st.asleep && (near.isEmpty() ? !st.held : calm)) continue;
                st.asleep = false;
                st.held = false;
                simulated++;
                boolean pressed = st.pose.sprite == PlantShape.STILL;
                for (int s = 0; s < n; s++) step(st, patch, near, pressed ? st.stillBin : st.bin, pressed ? KEEP_STILL : KEEP_Z, h);
                settle(st, near.isEmpty(), calm);
            }
        }
    }

    /** One substep of a blade: Verlet with springs to rest, segment lengths, the bodies, the ground. */
    private static void step(Strand st, Patch patch, List<Body> near, int[] bins, float[] keep, float h) {
        float[] pos = st.pos, prev = st.prev, rest = st.rest;
        int points = st.pose.points();
        for (int i = 1; i < points; i++) {
            float k = keep[bins[i]], pull = PULL[bins[i]];
            for (int a = 0; a < 3; a++) {
                int o = i * 3 + a;
                float v = (pos[o] - prev[o]) * k;
                prev[o] = pos[o];
                pos[o] += v + (rest[o] - pos[o]) * pull;
            }
        }
        lengths(st);
        if (!near.isEmpty()) {
            float ox = patch.pos.getX(), oy = patch.pos.getY(), oz = patch.pos.getZ();
            for (Body body : near)
                for (int i = 1; i < points; i++) {
                    int o = i * 3;
                    double wy = oy + pos[o + 1];
                    if (wy < body.y - 0.05 || wy > body.top) continue;
                    float rx = (float) (ox + pos[o] - body.x), rz = (float) (oz + pos[o + 2] - body.z);
                    float d = (float) Math.sqrt(rx * rx + rz * rz);
                    if (d >= body.r) continue;
                    if (d < 1e-4F) { rx = -body.vz; rz = body.vx; d = (float) Math.sqrt(rx * rx + rz * rz); if (d < 1e-4F) { rx = 1; rz = 0; d = 1; } }
                    // back to the surface, and carried a little with the body (the leg drags the blade along)
                    float out = (body.r - d) / d;
                    pos[o] += rx * out + body.vx * h * DRAG;
                    pos[o + 2] += rz * out + body.vz * h * DRAG;
                }
            lengths(st);
        }
        float ground = rest[1] + 0.003F;
        for (int i = 1; i < points; i++) if (pos[i * 3 + 1] < ground) pos[i * 3 + 1] = ground;
    }

    /** Each segment back to its length, from the foot out (the foot stays). */
    private static void lengths(Strand st) {
        float[] pos = st.pos;
        for (int i = 1; i < st.pose.points(); i++) {
            int a = (i - 1) * 3, b = i * 3;
            float dx = pos[b] - pos[a], dy = pos[b + 1] - pos[a + 1], dz = pos[b + 2] - pos[a + 2];
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1e-6F) continue;
            float k = st.len[i - 1] / d;
            pos[b] = pos[a] + dx * k;
            pos[b + 1] = pos[a + 1] + dy * k;
            pos[b + 2] = pos[a + 2] + dz * k;
        }
    }

    /**
     * A blade near its rest and barely moving, with nobody near, sleeps exactly at rest (as the chunk mesh draws it); one
     * barely moving among bodies that stand still sleeps where it is, held (13.10: standing in the turf no longer keeps
     * the blocks around rebuilt every frame).
     */
    private static void settle(Strand st, boolean alone, boolean calm) {
        if (!alone && !calm) return;
        float off = 0, speed = 0;
        for (int o = 3; o < st.pos.length; o++) {
            off = Math.max(off, Math.abs(st.pos[o] - st.rest[o]));
            speed = Math.max(speed, Math.abs(st.pos[o] - st.prev[o]));
        }
        // 6 mm off and 7 cm/s: well under what shows on a blade in the wind; a block still sooner costs nothing to draw (13.10)
        if (alone && off < 0.006F && speed < 0.0006F) {
            System.arraycopy(st.rest, 0, st.pos, 0, st.pos.length);
            System.arraycopy(st.rest, 0, st.prev, 0, st.prev.length);
            st.asleep = true;
        } else if (!alone && speed < 0.0003F) {
            System.arraycopy(st.pos, 0, st.prev, 0, st.prev.length);
            st.asleep = true;
            st.held = true;
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS || PATCHES.isEmpty()) return;
        long start = System.nanoTime();
        boolean pack = AflShaderCompat.shaderPackInUse();
        Vec3 cam = event.getCamera().getPosition();
        if (built != frames) {
            prepare(cam, pack);
            built = frames;
        }
        // a pack's shadow pass sees more than the camera: with a pack every block, without one only the visible
        Frustum frustum = pack ? null : event.getFrustum();
        Matrix4f view = event.getPoseStack().last().pose(), projection = event.getProjectionMatrix();
        for (int pass = 0; pass < 2; pass++) {
            RenderType type = pass == 0 ? RenderType.solid() : RenderType.cutoutMipped();
            type.setupRenderState();
            ShaderInstance own = pack ? null : VegetationNearPrograms.get(pass), shader = own != null ? own : RenderSystem.getShader();
            if (shader == null) {
                type.clearRenderState();
                continue;
            }
            if (own != null) {
                // the wind's uniforms on the program itself (they stay set through the draws)
                own.apply();
                VegetationWind.bind(own.getId());
                own.clear();
            }
            for (Patch patch : PATCHES.values()) {
                if (!patch.restValid || patch.restCount[pass] == 0) continue;
                if (frustum != null && !frustum.isVisible(new AABB(patch.pos).inflate(1.2, 0, 1.2).expandTowards(0, 1.6, 0))) continue;
                float ox = (float) (patch.pos.getX() - cam.x), oy = (float) (patch.pos.getY() - cam.y), oz = (float) (patch.pos.getZ() - cam.z);
                draw(patch.rest[pass], own, shader, view, projection, ox, oy, oz);
            }
            if (MOVING_COUNT[pass] > 0) draw(MOVING[pass], own, shader, view, projection, 0, 0, 0);
            if (own != null && own.CHUNK_OFFSET != null) own.CHUNK_OFFSET.set(0F, 0F, 0F);
            type.clearRenderState();
        }
        VertexBuffer.unbind();
        passes++;
        drawNanos += System.nanoTime() - start;
    }

    /** One buffer at (ox, oy, oz) from the camera: AFL's program takes it as its chunk offset, a pack's in the model-view matrix. */
    private static void draw(VertexBuffer buffer, ShaderInstance own, ShaderInstance shader, Matrix4f view, Matrix4f projection, float ox, float oy, float oz) {
        Matrix4f modelView = view;
        if (own != null) {
            if (own.CHUNK_OFFSET != null) own.CHUNK_OFFSET.set(ox, oy, oz);
        } else if (ox != 0 || oy != 0 || oz != 0) modelView = MODEL_VIEW.set(view).translate(ox, oy, oz);
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
    }

    /**
     * Once a frame, before the first pass: a block whose blades are all still gets its own buffers (built again when its
     * light, the pack or the sprites changed); the others go into this frame's moving buffers, camera-relative.
     */
    private static void prepare(Vec3 cam, boolean pack) {
        Emitter em = EMITTER;
        em.standing = PlantModel.sprite();
        em.still = PlantModel.stillSprite();
        boolean stale = spritesChanged;
        spritesChanged = false;
        movingBlocks = restBlocks = 0;
        boolean any = false;
        for (Patch patch : PATCHES.values()) {
            if (still(patch)) {
                restBlocks++;
                if (stale || !patch.restValid || patch.restLight != patch.light || patch.restPack != pack) buildRest(patch, pack);
            } else {
                patch.restValid = false;
                any = true;
            }
        }
        MOVING_COUNT[0] = MOVING_COUNT[1] = 0;
        movingVertices = 0;
        if (!any) return;
        em.begin();
        for (Patch patch : PATCHES.values()) {
            if (patch.restValid) continue;
            movingBlocks++;
            em.patch(patch, (float) (patch.pos.getX() - cam.x), (float) (patch.pos.getY() - cam.y), (float) (patch.pos.getZ() - cam.z));
            em.strands(patch);
        }
        for (int i = 0; i < 2; i++) {
            if (MOVING[i] == null) MOVING[i] = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            MOVING_COUNT[i] = em.end(i, MOVING[i]);
            movingVertices += MOVING_COUNT[i];
        }
    }

    /** A still block's own buffers, in block coordinates. */
    private static void buildRest(Patch patch, boolean pack) {
        Emitter em = EMITTER;
        em.begin();
        em.patch(patch, 0, 0, 0);
        em.strands(patch);
        for (int i = 0; i < 2; i++) {
            if (patch.rest[i] == null) patch.rest[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
            patch.restCount[i] = em.end(i, patch.rest[i]);
        }
        patch.restValid = true;
        patch.restLight = patch.light;
        patch.restPack = pack;
        restBuilds++;
    }

    /**
     * Writes the blades as the chunk mesh's PlantShape.Writer builds them, from their points now, and their rigid parts
     * carried along, into the builders (solid, cutout) at an origin; no wind here (the programs add it). The builders' fast
     * whole-vertex path without a pack.
     */
    private static final class Emitter implements PlantShape.Sink {
        final PlantShape.Writer writer = new PlantShape.Writer();
        final float[] from = new float[6], to = new float[6], m = new float[9], moved = new float[12];
        final int[] count = new int[2];
        float ox, oy, oz, r, g, b;
        int light;
        TextureAtlasSprite standing, still;

        /**
         * The builders' layout from begin on: {@link #BLOCK} vanilla's (32 bytes), {@link #TERRAIN} Oculus's with a pack (52:
         * vanilla's, then mc_Entity, mc_midTexCoord, at_tangent, at_midBlock), -1 another (each vertex through the builder).
         */
        int layout = -1;
        static final int BLOCK = 0, TERRAIN = 1;
        private static final int[] STRIDE = {32, 52};
        private static final List<String> TERRAIN_NAMES = List.of("Position", "Color", "UV0", "UV2", "Normal", "Padding",
                "mc_Entity", "mc_midTexCoord", "at_tangent", "at_midBlock", "Padding2");
        private static final Field FORMAT = formatField();
        /** Each list's vertices, written straight in the builders' layout and handed over whole at the end (13.10). */
        final long[] staging = new long[2];
        final int[] capacity = new int[2];
        final float[] face = new float[3], q = new float[12];

        private static Field formatField() {
            try {
                return net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(BufferBuilder.class, "f_85658_");
            } catch (RuntimeException e) {
                return null;
            }
        }

        /**
         * What the builder writes after begin: vanilla's block layout, or (a shader pack on) Oculus's terrain layout, whose
         * extended data Oculus fills in each element and vertex (beforeNext, fillExtendedData: about 0.1 us an element, 13.10).
         */
        private static int layoutOf(BufferBuilder builder) {
            if (FORMAT == null) return -1;
            try {
                VertexFormat f = (VertexFormat) FORMAT.get(builder);
                if (f == DefaultVertexFormat.BLOCK) return BLOCK;
                if (f != null && f.getVertexSize() == STRIDE[TERRAIN] && f.getElementAttributeNames().equals(TERRAIN_NAMES)) return TERRAIN;
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
            return -1;
        }

        void begin() {
            for (int i = 0; i < 2; i++) {
                BUILDERS[i].begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                count[i] = 0;
            }
            layout = layoutOf(BUILDERS[0]);
            if (layout >= 0 && checked[layout] == null) checked[layout] = check();
            if (layout >= 0 && !"ok".equals(checked[layout])) layout = -1;
        }

        /** Per layout, what the first check found (the status line): the direct bytes against the builder's own. */
        final String[] checked = new String[2];

        /**
         * One quad written both ways, byte for byte (but at_midBlock and vanilla's padding byte): the
         * direct layout trusted only when it matches what the builder (and Oculus in it) writes; else the builder's path.
         */
        private String check() {
            float[] p = {0.1F, 0.02F, 0.3F, 0.7F, 0.05F, 0.35F, 0.65F, 0.9F, 0.4F, 0.12F, 0.85F, 0.33F};
            float[] uv = {0.1F, 0.95F, 0.2F, 0.95F, 0.2F, 0.05F, 0.1F, 0.05F};
            float[] lit = {0.8F, 0.9F, 1F, 0.7F}, n = {0.2F, 0.3F, -0.9F, 0.1F, 0.2F, -0.95F, 0F, 0.1F, -1F, 0.3F, 0F, -0.9F};
            TextureAtlasSprite sp = standing;
            float tr = 0.6F, tg = 0.8F, tb = 0.4F;
            BufferBuilder ref = new BufferBuilder(256);
            ref.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (int i = 0; i < 4; i++)
                ref.vertex(p[i * 3] + ox, p[i * 3 + 1] + oy, p[i * 3 + 2] + oz, Math.min(1, lit[i] * tr), Math.min(1, lit[i] * tg), Math.min(1, lit[i] * tb), 1F,
                        sp.getU(PlantShape.inColumn(uv[i * 2], uv) * 16), sp.getV(uv[i * 2 + 1] * 16), OverlayTexture.NO_OVERLAY, light,
                        n[i * 3], n[i * 3 + 1], n[i * 3 + 2]);
            BufferBuilder.RenderedBuffer done = ref.end();
            ByteBuffer want = done.vertexBuffer();
            int stride = STRIDE[layout];
            write(0, p, uv, lit, n, tr, tg, tb, sp);
            String result = "ok";
            if (want.remaining() != 4 * stride) result = "size " + want.remaining() + " not " + 4 * stride;
            else for (int b = 0; b < 4 * stride; b++) {
                // at_midBlock: from the block's corner on purpose; vanilla's padding after the normal: never written there
                if (layout == TERRAIN ? b % stride >= 48 : b % stride == 31) continue;
                if (want.get(want.position() + b) != MemoryUtil.memGetByte(staging[0] + b)) {
                    result = "byte " + b + " differs";
                    break;
                }
            }
            done.release();
            if (!result.equals("ok")) {
                com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.warn("AFL vegetation: near layer direct vertices ({}) differ from the builder's: {}; the builder's path", layout == TERRAIN ? "Oculus terrain" : "vanilla block", result);
            }
            return result;
        }

        /** List i's vertices into the buffer; how many. */
        int end(int i, VertexBuffer into) {
            if (layout >= 0 && count[i] > 0) BUILDERS[i].putBulkData(MemoryUtil.memByteBuffer(staging[i], count[i] * STRIDE[layout]));
            BufferBuilder.RenderedBuffer done = BUILDERS[i].end();
            if (count[i] == 0) {
                done.release();
                return 0;
            }
            into.bind();
            into.upload(done);
            return count[i];
        }

        void patch(Patch patch, float ox, float oy, float oz) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            r = patch.r;
            g = patch.g;
            b = patch.b;
            light = patch.light;
        }

        void strands(Patch patch) {
            for (Strand st : patch.strands) {
                writer.pose(st.pose, st.pos, this);
                if (!st.parts.isEmpty()) parts(st);
            }
        }

        @Override
        public void quad(PlantShape.Pose pose, float[] p, float[] uv, float[] lit, float[] light, float[] n) {
            emit(p, uv, lit, n, pose.tone, pose.blade.tinted, pose.sprite, pose.solid());
        }

        void parts(Strand st) {
            for (Carried c : st.parts) {
                PlantShape.frame(st.rest, st.pose.f, c.at(), from);
                PlantShape.frame(st.pos, st.pose.f, c.at(), to);
                PlantShape.turn(from, to, m);
                for (var q : c.quads()) {
                    for (int i = 0; i < 4; i++) PlantShape.carry(q.p, i * 3, from, to, m, moved, i * 3);
                    emit(moved, q.uv, q.lit, q.n, q.tone, q.tinted, q.sprite, q.solid);
                }
            }
        }

        private void emit(float[] p, float[] uv, float[] lit, float[] n, float[] tone, boolean tinted, int sprite, boolean solid) {
            int list = solid ? 0 : 1;
            TextureAtlasSprite sp = sprite == PlantShape.STILL ? still : standing;
            float tr = tone[0], tg = tone[1], tb = tone[2];
            if (tinted) { tr *= r; tg *= g; tb *= b; }
            if (layout >= 0) {
                write(list, p, uv, lit, n, tr, tg, tb, sp);
                count[list] += 4;
                return;
            }
            BufferBuilder out = BUILDERS[list];
            for (int i = 0; i < 4; i++) {
                float l = lit[i];
                out.vertex(p[i * 3] + ox, p[i * 3 + 1] + oy, p[i * 3 + 2] + oz, Math.min(1, l * tr), Math.min(1, l * tg), Math.min(1, l * tb), 1F,
                        sp.getU(PlantShape.inColumn(uv[i * 2], uv) * 16), sp.getV(uv[i * 2 + 1] * 16), OverlayTexture.NO_OVERLAY, light,
                        n[i * 3], n[i * 3 + 1], n[i * 3 + 2]);
            }
            count[list] += 4;
        }

        /**
         * One quad straight into list's staging in the builders' layout: the bytes BufferBuilder#vertex writes (vanilla's
         * fast path), and with Oculus's layout what its BufferBuilder mixin adds (mc_Entity -1 -1 as outside a chunk, the
         * quad's middle texture coordinate, its face normal over the given one, the tangent of its first three corners as
         * NormalHelper#computeTangent, the middle of the block as ExtendedDataHelper#computeMidBlock but from the block's
         * own corner rather than the buffer's origin).
         */
        private void write(int list, float[] p, float[] uv, float[] lit, float[] n, float tr, float tg, float tb, TextureAtlasSprite sp) {
            int stride = STRIDE[layout], need = (count[list] + 4) * stride;
            if (need > capacity[list]) {
                int grow = Math.max(need, Math.max(1 << 16, capacity[list] * 2));
                staging[list] = MemoryUtil.nmemReallocChecked(staging[list], grow);
                capacity[list] = grow;
            }
            long a = staging[list] + (long) count[list] * stride;
            float u0 = 0, v0 = 0, u1 = 0, v1 = 0, u2 = 0, v2 = 0, mu = 0, mv = 0;
            for (int i = 0; i < 4; i++, a += stride) {
                float l = lit[i];
                float u = sp.getU(PlantShape.inColumn(uv[i * 2], uv) * 16), v = sp.getV(uv[i * 2 + 1] * 16);
                // the corners as written: Oculus takes the normal and tangent from them
                float x = q[i * 3] = p[i * 3] + ox, y = q[i * 3 + 1] = p[i * 3 + 1] + oy, z = q[i * 3 + 2] = p[i * 3 + 2] + oz;
                MemoryUtil.memPutFloat(a, x);
                MemoryUtil.memPutFloat(a + 4, y);
                MemoryUtil.memPutFloat(a + 8, z);
                MemoryUtil.memPutByte(a + 12, (byte) (int) (Math.min(1, l * tr) * 255F));
                MemoryUtil.memPutByte(a + 13, (byte) (int) (Math.min(1, l * tg) * 255F));
                MemoryUtil.memPutByte(a + 14, (byte) (int) (Math.min(1, l * tb) * 255F));
                MemoryUtil.memPutByte(a + 15, (byte) 255);
                MemoryUtil.memPutFloat(a + 16, u);
                MemoryUtil.memPutFloat(a + 20, v);
                MemoryUtil.memPutInt(a + 24, light);
                MemoryUtil.memPutByte(a + 28, normalByte(n[i * 3]));
                MemoryUtil.memPutByte(a + 29, normalByte(n[i * 3 + 1]));
                MemoryUtil.memPutByte(a + 30, normalByte(n[i * 3 + 2]));
                MemoryUtil.memPutByte(a + 31, (byte) 0);
                if (layout == TERRAIN) {
                    MemoryUtil.memPutShort(a + 32, (short) -1);
                    MemoryUtil.memPutShort(a + 34, (short) -1);
                    MemoryUtil.memPutInt(a + 48, midBlock(0.5F - p[i * 3], 0.5F - p[i * 3 + 1], 0.5F - p[i * 3 + 2]));
                }
                mu += u;
                mv += v;
                if (i == 0) { u0 = u; v0 = v; } else if (i == 1) { u1 = u; v1 = v; } else if (i == 2) { u2 = u; v2 = v; }
            }
            if (layout != TERRAIN) return;
            mu *= 0.25F;
            mv *= 0.25F;
            faceNormal(q, face);
            int normal = packNorm(face[0], face[1], face[2], 0);
            int tangent = tangent(face[0], face[1], face[2], q, u0, v0, u1, v1, u2, v2);
            a = staging[list] + (long) count[list] * stride;
            for (int i = 0; i < 4; i++, a += stride) {
                MemoryUtil.memPutInt(a + 28, normal);
                MemoryUtil.memPutFloat(a + 36, mu);
                MemoryUtil.memPutFloat(a + 40, mv);
                MemoryUtil.memPutInt(a + 44, tangent);
            }
        }

        private static byte normalByte(float f) {
            return (byte) ((int) (Mth.clamp(f, -1F, 1F) * 127F) & 255);
        }

        private static int packNorm(float x, float y, float z, float w) {
            return (int) (x * 127F) & 255 | ((int) (y * 127F) & 255) << 8 | ((int) (z * 127F) & 255) << 16 | ((int) (w * 127F) & 255) << 24;
        }

        private static int midBlock(float x, float y, float z) {
            return (int) (x * 64F) & 255 | ((int) (y * 64F) & 255) << 8 | ((int) (z * 64F) & 255) << 16;
        }

        /** Oculus's NormalHelper#computeFaceNormal: the cross of the quad's diagonals. */
        private static void faceNormal(float[] p, float[] out) {
            float dx0 = p[6] - p[0], dy0 = p[7] - p[1], dz0 = p[8] - p[2];
            float dx1 = p[9] - p[3], dy1 = p[10] - p[4], dz1 = p[11] - p[5];
            float x = dy0 * dz1 - dz0 * dy1, y = dz0 * dx1 - dx0 * dz1, z = dx0 * dy1 - dy0 * dx1;
            float l = (float) Math.sqrt(x * x + y * y + z * z);
            if (l != 0) { x /= l; y /= l; z /= l; }
            out[0] = x;
            out[1] = y;
            out[2] = z;
        }

        /** Oculus's NormalHelper#computeTangent from the first three corners. */
        private static int tangent(float nx, float ny, float nz, float[] p, float u0, float v0, float u1, float v1, float u2, float v2) {
            float e1x = p[3] - p[0], e1y = p[4] - p[1], e1z = p[5] - p[2];
            float e2x = p[6] - p[0], e2y = p[7] - p[1], e2z = p[8] - p[2];
            float du1 = u1 - u0, dv1 = v1 - v0, du2 = u2 - u0, dv2 = v2 - v0;
            float den = du1 * dv2 - du2 * dv1, f = den == 0 ? 1F : 1F / den;
            float tx = f * (dv2 * e1x - dv1 * e2x), ty = f * (dv2 * e1y - dv1 * e2y), tz = f * (dv2 * e1z - dv1 * e2z);
            float tc = rsqrt(tx * tx + ty * ty + tz * tz);
            tx *= tc;
            ty *= tc;
            tz *= tc;
            float bx = f * (-du2 * e1x + du1 * e2x), by = f * (-du2 * e1y + du1 * e2y), bz = f * (-du2 * e1z + du1 * e2z);
            float bc = rsqrt(bx * bx + by * by + bz * bz);
            bx *= bc;
            by *= bc;
            bz *= bc;
            float px = ty * nz - tz * ny, py = tz * nx - tx * nz, pz = tx * ny - ty * nx;
            return packNorm(tx, ty, tz, bx * px + by * py + bz * pz < 0 ? -1F : 1F);
        }

        private static float rsqrt(float v) {
            return v == 0 ? 1F : (float) (1.0 / Math.sqrt(v));
        }
    }

    // ------------------------------------------------------------------------------------------------- the rest

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        for (Patch patch : PATCHES.values()) patch.close();
        PATCHES.clear();
        HIDDEN.clear();
        BODIES.clear();
        lastNanos = 0;
    }

    static String status() {
        int strands = 0;
        for (Patch p : PATCHES.values()) strands += p.strands.size();
        return String.format(Locale.ROOT, "near layer: %s, %d blocks (%d hidden from the chunk mesh), %d blades, %d moving; joined %d, released %d;"
                        + " plant changes: %d not rebuilt (drawn here), %d rebuilt off the render thread; last frame %d still blocks (own buffers, %d built so far),"
                        + " %d moving blocks (%d vertices, written directly: vanilla layout %s, Oculus layout %s); %s; %.1f us sim, %.1f us build+draw a frame, %.2f passes a frame",
                AflShaderCompat.shaderPackInUse() ? "on (shader pack: shadow + main pass)" : "on", PATCHES.size(), HIDDEN.size(), strands, simulated, joined, released,
                skipped, deferred, restBlocks, restBuilds, movingBlocks, movingVertices,
                EMITTER.checked[Emitter.BLOCK] == null ? "not checked yet" : EMITTER.checked[Emitter.BLOCK],
                EMITTER.checked[Emitter.TERRAIN] == null ? "not checked yet" : EMITTER.checked[Emitter.TERRAIN],
                AflShaderCompat.shaderPackInUse() ? "the pack's programs" : VegetationNearPrograms.get(0) != null ? "AFL's programs with the wind" : "vanilla programs, no wind",
                frames == 0 ? 0 : simNanos / 1000.0 / frames, frames == 0 ? 0 : drawNanos / 1000.0 / frames, frames == 0 ? 0 : passes / (double) frames);
    }

    private static float sq(float f) {
        return f * f;
    }

    private static double sq(double d) {
        return d * d;
    }
}
