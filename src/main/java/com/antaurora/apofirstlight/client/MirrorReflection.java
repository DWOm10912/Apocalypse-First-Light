package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.WallMirrorBlock;
import com.antaurora.apofirstlight.blockentity.WallMirrorBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirror Reflection V1 (docs/rendering/mirror_reflection_v1.md): a real reflection for the nearest visible wall mirror.
 * <p>
 * Each frame, right after the sky (before any terrain), it picks the mirror nearest the camera that faces it, is in view
 * and within {@link #RANGE}. The camera is reflected through the glass plane, and the room in front of the mirror is drawn
 * from there into an off-screen texture, with an off-axis projection whose near plane is the glass rectangle itself: the
 * texture is exactly the glass's view, so it maps onto the glass quad with plain 0..1 UVs, and the wall behind the mirror
 * falls in front of the near plane and is clipped. After the block entities ({@code AFTER_BLOCK_ENTITIES}) the same class
 * draws it on the glass, 1 mm in front of it and behind the frame's face. That is our own world pass, not a block entity
 * renderer: Embeddium only calls the renderers of block entities it listed when the chunk section was built, and a mirror
 * placed before it had a block entity gets one later (on its first {@code getBlockEntity}), unlisted until the section is
 * rebuilt (2026-10-08: no reflection without shaders, where nothing rebuilt the sections).
 * <p>
 * The loaded mirrors come from {@link WallMirrorBlockEntity} (placed and loaded block entities) and from a scan of every
 * chunk the client loads (mirrors without a block entity yet).
 * <ul>
 * <li>What is drawn: the blocks (and fluids) in a box in front of the mirror, meshed into our own vertex buffers and
 * re-meshed over several frames when a block, its light or a block entity's model data there changes, the
 * block entities in the box and every entity in it, the local player included. Other mirrors show their plain glass (no
 * recursion); sky, clouds, weather and particles are left out: the background is the fog colour.</li>
 * <li>Shaders: the off-screen pass binds its own framebuffer, so Oculus uses the vanilla programs for it (it only replaces
 * them while the main target is bound). With a shader pack the reflection is drawn "albedo" (white lightmap) and the glass
 * quad goes through the pack as an ordinary textured surface (entity solid, the mirror's light), so the pack lights, fogs and
 * tone-maps it like the room; without one it is drawn with vanilla lighting and shown unlit (text type, full light).
 * {@code /afl_render mirror} can force either.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MirrorReflection {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/mirror_reflection");

    // The visible glass (tools/build-restroom-fixtures-v2.mjs MIRROR: inside the 16 mm frame), NORTH frame, block units:
    // x 0.5 +- HALF_WIDTH, y BOTTOM..TOP, its face GLASS from the block centre toward the wall.
    static final double HALF_WIDTH = 0.2125, BOTTOM = 0.017, TOP = 0.746, GLASS = 0.485;
    /** The reflection quad sits this far in front of the glass (the frame face is 5 mm in front of it). */
    static final double DRAW_LIFT = 0.001;
    static final double RANGE = 12.0, MIN_DISTANCE = 0.05;
    /** The meshed box, in blocks: in front of the mirror, to each side, below and above its cell. */
    static final int FRONT = 12, SIDE = 10, DOWN = 4, UP = 6;
    static final float FAR = 64.0F;
    static final long CHECK_NANOS = 100_000_000L, BUILD_NANOS = 1_500_000L;
    static final int[] HEIGHTS = {256, 384, 512, 768, 1024, 1536};

    /** One frame's reflection: the glass quad (world, lifted), its UVs, and whether it was drawn "albedo". */
    public record Frame(BlockPos pos, Vec3 normal, Vec3[] corners, float[] u, float[] v, boolean albedo) {}

    private record Candidate(BlockPos pos, Direction facing, Vec3 normal, double depth, double distance) {}

    private static @Nullable Frame current;
    private static boolean rendering, failed;
    private static volatile @Nullable Thread meshingThread;
    private static @Nullable TextureTarget target;
    private static int targetHeight;
    private static @Nullable RegionMesh mesh;
    private static @Nullable DynamicTexture white;
    private static boolean textureRegistered;
    private static final Map<RenderType, BufferBuilder> BUILDERS = new HashMap<>();
    private static @Nullable MultiBufferSource.BufferSource entityBuffers;

    private MirrorReflection() {}

    /** The reflection drawn this frame for the mirror at {@code pos}, or null. */
    public static @Nullable Frame frameFor(BlockPos pos) {
        Frame f = current;
        return f != null && f.pos.equals(pos) ? f : null;
    }

    /** True while the off-screen pass draws. */
    public static boolean rendering() { return rendering; }

    /** True on the thread meshing the reflection box: chunk-model side effects (mesh confirmation) must not happen. */
    public static boolean meshing() { return meshingThread == Thread.currentThread(); }

    /** Mirrors in a chunk the client loads, block entity or not (a section's palette tells whether to look). */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) return;
        Block mirror = AflBlocks.WALL_MIRROR.get();
        LevelChunkSection[] sections = chunk.getSections();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.is(mirror))) continue;
            int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                if (section.getBlockState(x, y, z).is(mirror)) WallMirrorBlockEntity.clientMirrors().add(new BlockPos(x0 + x, y0 + y, z0 + z));
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        var at = event.getChunk().getPos();
        WallMirrorBlockEntity.clientMirrors().removeIf(p -> (p.getX() >> 4) == at.x && (p.getZ() >> 4) == at.z);
    }

    /** This frame's reflection onto its mirror's glass (not in the shader shadow pass). */
    private static void drawGlass(RenderLevelStageEvent event) {
        Frame f = current;
        var mc = Minecraft.getInstance();
        if (f == null || mc.level == null || AflShaderCompat.activeShadowPass()) return;
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        RenderType type = f.albedo ? RenderType.entitySolid(TEXTURE) : RenderType.text(TEXTURE);
        VertexConsumer out = buffers.getBuffer(type);
        int light = f.albedo ? LevelRenderer.getLightColor(mc.level, f.pos) : LightTexture.FULL_BRIGHT;
        var last = event.getPoseStack().last();
        for (int i = 0; i < 4; i++) {
            Vec3 p = f.corners[i];
            out.vertex(last.pose(), (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z))
                    .color(255, 255, 255, 255).uv(f.u[i], f.v[i]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(last.normal(), (float) f.normal.x, (float) f.normal.y, (float) f.normal.z).endVertex();
        }
        buffers.endBatch(type);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        WallMirrorBlockEntity.clearClientMirrors();
        current = null;
        pending = null;
        if (mesh != null) { mesh.close(); mesh = null; }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) { drawGlass(event); return; }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        current = null;
        AflRenderDev.Mirror mode = AflRenderDev.mirror();
        if (mode == AflRenderDev.Mirror.OFF || failed) return;
        var mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || WallMirrorBlockEntity.clientMirrors().isEmpty()) return;
        Vec3 eye = event.getCamera().getPosition();
        Candidate best = pick(level, eye, event.getFrustum());
        if (best == null) return;
        boolean albedo = mode == AflRenderDev.Mirror.ALBEDO || mode == AflRenderDev.Mirror.AUTO && AflShaderCompat.shaderPackInUse();
        long start = AflRenderProfiler.begin();
        try {
            current = render(mc, level, best, eye, event.getPartialTick(), albedo);
        } catch (RuntimeException e) {
            failed = true;
            ApocalypseFirstLight.LOGGER.error("[AFL MIRROR] reflection pass failed, mirrors show plain glass until restart", e);
        } finally {
            AflRenderProfiler.end("mirror.reflection", start);
        }
    }

    private static @Nullable Candidate pick(ClientLevel level, Vec3 eye, @Nullable Frustum frustum) {
        Candidate best = null;
        for (BlockPos pos : WallMirrorBlockEntity.clientMirrors()) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(AflBlocks.WALL_MIRROR.get())) { WallMirrorBlockEntity.clientMirrors().remove(pos); continue; }
            Direction facing = state.getValue(WallMirrorBlock.FACING);
            Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 centre = glassCentre(pos, facing, 0);
            double depth = eye.subtract(centre).dot(normal), distance = eye.distanceTo(centre);
            if (depth < MIN_DISTANCE || distance > RANGE) continue;
            if (frustum != null && !frustum.isVisible(new AABB(pos))) continue;
            if (best == null || distance < best.distance) best = new Candidate(pos, facing, normal, depth, distance);
        }
        return best;
    }

    static Vec3 glassCentre(BlockPos pos, Direction facing, double lift) {
        Vec3 n = Vec3.atLowerCornerOf(facing.getNormal());
        return new Vec3(pos.getX() + 0.5, pos.getY() + (BOTTOM + TOP) / 2, pos.getZ() + 0.5).add(n.scale(lift - GLASS));
    }

    /** Glass corners, in the order that faces +normal: bottom +side, bottom -side, top -side, top +side. */
    static Vec3[] glassCorners(BlockPos pos, Direction facing, double lift) {
        Vec3 n = Vec3.atLowerCornerOf(facing.getNormal()), t = Vec3.atLowerCornerOf(facing.getClockWise().getNormal());
        Vec3 base = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5).add(n.scale(lift - GLASS));
        return new Vec3[]{
                base.add(t.scale(HALF_WIDTH)).add(0, BOTTOM, 0), base.add(t.scale(-HALF_WIDTH)).add(0, BOTTOM, 0),
                base.add(t.scale(-HALF_WIDTH)).add(0, TOP, 0), base.add(t.scale(HALF_WIDTH)).add(0, TOP, 0)};
    }

    private static @Nullable Frame render(Minecraft mc, ClientLevel level, Candidate c, Vec3 eye, float partial, boolean albedo) {
        Vec3 n = c.normal;
        Vec3 mirrored = eye.subtract(n.scale(2 * c.depth));   // the camera reflected through the glass plane
        // looking out of the wall, through the glass, into the room
        Matrix4f view = new Matrix4f().setLookAt(0, 0, 0, (float) n.x, (float) n.y, (float) n.z, 0, 1, 0);
        Vec3[] glass = glassCorners(c.pos, c.facing, 0);
        Vector3f[] q = new Vector3f[4];
        float left = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = Float.MAX_VALUE, top = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            Vec3 d = glass[i].subtract(mirrored);
            q[i] = view.transformPosition(new Vector3f((float) d.x, (float) d.y, (float) d.z));
            left = Math.min(left, q[i].x); right = Math.max(right, q[i].x);
            bottom = Math.min(bottom, q[i].y); top = Math.max(top, q[i].y);
        }
        float near = (float) c.depth;   // every corner lies on the near plane
        Matrix4f projection = new Matrix4f().setFrustum(left, right, bottom, top, near, FAR);
        float[] u = new float[4], v = new float[4];
        for (int i = 0; i < 4; i++) {
            u[i] = (q[i].x - left) / (right - left);
            v[i] = (q[i].y - bottom) / (top - bottom);   // a framebuffer's row 0 is the bottom of its image
        }

        RegionMesh region = mesh(level, c.pos, c.facing, mirrored);
        if (region == null) return null;   // the first mesh is still being built: plain glass meanwhile
        TextureTarget out = target(mc, c.distance);

        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        out.setClearColor(fogColor[0], fogColor[1], fogColor[2], 1.0F);
        RenderSystem.depthMask(true);   // a clear honours the write masks
        RenderSystem.colorMask(true, true, true, true);
        out.clear(Minecraft.ON_OSX);
        out.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(view);
        RenderSystem.applyModelViewMatrix();
        // fade out at the box's far end, measured from the mirrored camera (the light's real path length)
        RenderSystem.setShaderFogStart((float) c.depth + FRONT - 3);
        RenderSystem.setShaderFogEnd((float) c.depth + FRONT + 1);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        rendering = true;
        try {
            for (RenderType layer : RenderType.chunkBufferLayers())
                if (layer != RenderType.translucent()) region.draw(layer, view, projection, mirrored, albedo);
            if (level.effects().constantAmbientLight()) Lighting.setupNetherLevel(view); else Lighting.setupLevel(view);
            drawDynamic(mc, level, region, mirrored, partial, albedo);
            region.draw(RenderType.translucent(), view, projection, mirrored, albedo);
        } finally {
            rendering = false;
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
        registerTexture(mc);
        Vec3[] corners = glassCorners(c.pos, c.facing, DRAW_LIFT);
        return new Frame(c.pos, n, corners, u, v, albedo);
    }

    /** Block entities and entities in the box, through our own immediate buffers (never the main, shader-batched ones). */
    private static void drawDynamic(Minecraft mc, ClientLevel level, RegionMesh region, Vec3 eye, float partial, boolean albedo) {
        if (entityBuffers == null) entityBuffers = MultiBufferSource.immediate(new BufferBuilder(1 << 18));
        MultiBufferSource.BufferSource buffers = entityBuffers;
        MultiBufferSource source = albedo ? type -> new FullBright(buffers.getBuffer(type)) : buffers;
        PoseStack pose = new PoseStack();
        var blockEntities = mc.getBlockEntityRenderDispatcher();
        for (BlockEntity be : region.blockEntities) {
            if (be.isRemoved()) continue;
            BlockPos p = be.getBlockPos();
            pose.pushPose();
            pose.translate(p.getX() - eye.x, p.getY() - eye.y, p.getZ() - eye.z);
            blockEntities.render(be, partial, pose, source);
            pose.popPose();
        }
        buffers.endBatch();
        var entities = mc.getEntityRenderDispatcher();
        entities.setRenderShadow(false);
        try {
            for (Entity e : level.getEntities((Entity) null, region.box, e -> true)) {
                double x = Mth.lerp(partial, e.xOld, e.getX()), y = Mth.lerp(partial, e.yOld, e.getY()), z = Mth.lerp(partial, e.zOld, e.getZ());
                float yaw = Mth.lerp(partial, e.yRotO, e.getYRot());
                entities.render(e, x - eye.x, y - eye.y, z - eye.z, yaw, partial, pose, source, entities.getPackedLightCoords(e, partial));
            }
        } finally {
            entities.setRenderShadow(mc.options.entityShadows().get());
        }
        buffers.endBatch();
    }

    // ---------------- off-screen target ----------------

    private static TextureTarget target(Minecraft mc, double distance) {
        double fov = Math.toRadians(mc.options.fov().get());
        double px = mc.getWindow().getHeight() * (TOP - BOTTOM) / (2 * Math.max(distance, 0.1) * Math.tan(fov / 2));
        int want = HEIGHTS[HEIGHTS.length - 1];
        for (int h : HEIGHTS) if (h >= px) { want = h; break; }
        // grow at once, shrink only when well below the current size
        int height = target == null || want > targetHeight || want * 2 <= targetHeight ? want : targetHeight;
        int width = Math.max(16, Math.round(height * (float) (2 * HALF_WIDTH / (TOP - BOTTOM))));
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
        targetHeight = height;
        return target;
    }

    private static void registerTexture(Minecraft mc) {
        if (textureRegistered) return;
        textureRegistered = true;
        mc.getTextureManager().register(TEXTURE, new ReflectionTexture());
    }

    /** The texture manager's handle on the off-screen colour buffer (the target owns the GL texture). */
    private static final class ReflectionTexture extends AbstractTexture {
        @Override public void load(ResourceManager manager) {}
        @Override public int getId() { return target == null ? 0 : target.getColorTextureId(); }
        @Override public void releaseId() {}
        @Override public void close() {}
        /** Always linear: the texture is about screen resolution but seen at any angle. */
        @Override public void setFilter(boolean blur, boolean mipmap) {
            this.bind();
            com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 10241, 9729);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 10240, 9729);
        }
    }

    private static int white() {
        if (white == null) {
            NativeImage image = new NativeImage(16, 16, false);
            image.fillRect(0, 0, 16, 16, 0xFFFFFFFF);
            white = new DynamicTexture(image);
        }
        return white.getId();
    }

    // ---------------- the meshed box ----------------

    private static final int BOX_D = FRONT + 1, BOX_W = 2 * SIDE + 1, BOX_H = DOWN + UP + 1, CELLS = BOX_D * BOX_W * BOX_H;
    private static @Nullable Build pending;

    /**
     * The box's mesh for this mirror, or null while its first mesh is still being built. A (re)build runs over several
     * frames, at most {@link #BUILD_NANOS} each, and the old mesh is drawn until the new one is complete (a full box took
     * 45-75 ms in one go in the A1 restroom, 2026-10-08).
     */
    private static @Nullable RegionMesh mesh(ClientLevel level, BlockPos pos, Direction facing, Vec3 eye) {
        long now = System.nanoTime();
        RegionMesh m = mesh;
        if (m == null || !m.origin.equals(pos) || m.facing != facing) {
            if (m != null) m.close();
            m = mesh = new RegionMesh(pos, facing);
            pending = null;
        }
        if (pending == null && (!m.ready || now - m.checked >= CHECK_NANOS)) {
            m.checked = now;
            long hash = scan(level, m);
            if (!m.ready || hash != m.hash) pending = new Build(m, hash);
        }
        if (pending != null && step(level, pending)) {
            finish(pending, eye);
            pending = null;
        }
        return m.ready ? m : null;
    }

    /** Cell {@code index} of the box: depth (along facing) slowest, then side, then height; the mirror's own cell included. */
    private static void cell(RegionMesh m, int index, BlockPos.MutableBlockPos p) {
        int i = index / (BOX_W * BOX_H), j = index / BOX_H % BOX_W - SIDE, k = index % BOX_H - DOWN;
        Direction t = m.facing.getClockWise();
        p.set(m.origin.getX() + m.facing.getStepX() * i + t.getStepX() * j, m.origin.getY() + k,
                m.origin.getZ() + m.facing.getStepZ() * i + t.getStepZ() * j);
    }

    /** What the mesh depends on: every block state, its block and sky light, and each block entity cell's model data object. */
    private static long scan(ClientLevel level, RegionMesh m) {
        long h = 0xcbf29ce484222325L;
        var models = level.getModelDataManager();
        var p = new BlockPos.MutableBlockPos();
        for (int index = 0; index < CELLS; index++) {
            cell(m, index, p);
            BlockState state = level.getBlockState(p);
            h = (h ^ Block.getId(state)) * 0x100000001b3L;
            h = (h ^ (level.getBrightness(LightLayer.BLOCK, p) << 4 | level.getBrightness(LightLayer.SKY, p))) * 0x100000001b3L;
            if (state.hasBlockEntity() && models != null) h = (h ^ System.identityHashCode(models.getAt(p))) * 0x100000001b3L;
        }
        return h;
    }

    /** A (re)build in progress: the next cell, the layers begun, the block entities found. */
    private static final class Build {
        final RegionMesh mesh;
        final long hash;
        int next;
        final List<RenderType> begun = new ArrayList<>();
        final List<BlockEntity> blockEntities = new ArrayList<>();
        final RandomSource random = RandomSource.create();
        final PoseStack pose = new PoseStack();
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        Build(RegionMesh mesh, long hash) { this.mesh = mesh; this.hash = hash; }
    }

    /** Meshes cells for up to {@link #BUILD_NANOS}; true once every cell is done. */
    private static boolean step(ClientLevel level, Build b) {
        long start = System.nanoTime();
        meshingThread = Thread.currentThread();
        try {
            while (b.next < CELLS) {
                cell(b.mesh, b.next++, b.p);
                if (!b.p.equals(b.mesh.origin)) meshCell(level, b, b.p);
                if ((b.next & 7) == 0 && System.nanoTime() - start > BUILD_NANOS) return false;
            }
            return true;
        } finally {
            meshingThread = null;
        }
    }

    private static void meshCell(ClientLevel level, Build b, BlockPos.MutableBlockPos p) {
        var mc = Minecraft.getInstance();
        var blocks = mc.getBlockRenderer();
        BlockPos origin = b.mesh.origin;
        BlockState state = level.getBlockState(p);
        if (state.isAir()) return;
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            // the liquid renderer writes section-local positions
            var builder = begin(ItemBlockRenderTypes.getRenderLayer(fluid), b.begun);
            blocks.renderLiquid(p, level, new Offset(builder, (p.getX() & ~15) - origin.getX(),
                    (p.getY() & ~15) - origin.getY(), (p.getZ() & ~15) - origin.getZ()), state, fluid);
        }
        if (state.getRenderShape() == RenderShape.MODEL) {
            var models = level.getModelDataManager();
            var model = blocks.getBlockModel(state);
            ModelData data = model.getModelData(level, p, state, Objects.requireNonNullElse(models == null ? null : models.getAt(p), ModelData.EMPTY));
            b.random.setSeed(state.getSeed(p));
            for (RenderType layer : model.getRenderTypes(state, b.random, data)) {
                var builder = begin(layer, b.begun);
                b.pose.pushPose();
                b.pose.translate(p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ());
                blocks.renderBatched(state, p, level, b.pose, builder, true, b.random, data, layer);
                b.pose.popPose();
            }
        }
        if (state.hasBlockEntity()) {
            BlockEntity be = level.getBlockEntity(p);
            if (be != null && !(be instanceof WallMirrorBlockEntity) && mc.getBlockEntityRenderDispatcher().getRenderer(be) != null) b.blockEntities.add(be);
        }
    }

    /** Uploads a finished build into its mesh (translucent quads sorted for the current mirrored camera). */
    private static void finish(Build b, Vec3 eye) {
        RegionMesh m = b.mesh;
        for (RenderType layer : RenderType.chunkBufferLayers()) {
            BufferBuilder builder = b.begun.contains(layer) ? BUILDERS.get(layer) : null;
            BufferBuilder.RenderedBuffer rendered = null;
            if (builder != null) {
                if (layer == RenderType.translucent())
                    builder.setQuadSorting(VertexSorting.byDistance((float) (eye.x - m.origin.getX()), (float) (eye.y - m.origin.getY()), (float) (eye.z - m.origin.getZ())));
                rendered = builder.endOrDiscardIfEmpty();
            }
            if (rendered == null) {
                VertexBuffer old = m.buffers.remove(layer);
                if (old != null) old.close();
                continue;
            }
            VertexBuffer buffer = m.buffers.computeIfAbsent(layer, k -> new VertexBuffer(VertexBuffer.Usage.STATIC));
            buffer.bind();
            buffer.upload(rendered);
            VertexBuffer.unbind();
        }
        m.blockEntities.clear();
        m.blockEntities.addAll(b.blockEntities);
        m.hash = b.hash;
        m.ready = true;
    }

    private static BufferBuilder begin(RenderType layer, List<RenderType> begun) {
        BufferBuilder builder = BUILDERS.computeIfAbsent(layer, k -> new BufferBuilder(k.bufferSize()));
        if (!begun.contains(layer)) {
            // left over from an abandoned build (the region changed mid-build): end it and drop what it held. discard() alone
            // only rewinds the data and leaves the builder "building", so the next begin threw "Already building!" and the
            // reflection pass shut itself off until restart (user 2026-10-08: mirrors plain in every pack)
            if (builder.building()) {
                BufferBuilder.RenderedBuffer stale = builder.endOrDiscardIfEmpty();
                if (stale != null) stale.release();
            }
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            begun.add(layer);
        }
        return builder;
    }

    private static final class RegionMesh {
        final BlockPos origin;
        final Direction facing;
        final AABB box;
        final Map<RenderType, VertexBuffer> buffers = new HashMap<>();
        final List<BlockEntity> blockEntities = new ArrayList<>();
        long hash, checked;
        boolean ready;

        RegionMesh(BlockPos origin, Direction facing) {
            this.origin = origin.immutable();
            this.facing = facing;
            Direction t = facing.getClockWise();
            BlockPos a = origin.relative(t, -SIDE).below(DOWN), b = origin.relative(facing, FRONT).relative(t, SIDE).above(UP);
            this.box = new AABB(a).minmax(new AABB(b));
        }

        void draw(RenderType layer, Matrix4f view, Matrix4f projection, Vec3 eye, boolean albedo) {
            VertexBuffer buffer = buffers.get(layer);
            if (buffer == null) return;
            layer.setupRenderState();
            if (albedo) RenderSystem.setShaderTexture(2, white());
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                if (shader.CHUNK_OFFSET != null)
                    shader.CHUNK_OFFSET.set((float) (origin.getX() - eye.x), (float) (origin.getY() - eye.y), (float) (origin.getZ() - eye.z));
                buffer.bind();
                buffer.drawWithShader(view, projection, shader);
                VertexBuffer.unbind();
                if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            }
            layer.clearRenderState();
        }

        void close() {
            buffers.values().forEach(VertexBuffer::close);
            buffers.clear();
        }
    }

    /** Adds a fixed offset to every position (the liquid renderer's section-local coordinates). */
    private static final class Offset extends VertexConsumerWrapper {
        private final double dx, dy, dz;
        Offset(VertexConsumer parent, double dx, double dy, double dz) { super(parent); this.dx = dx; this.dy = dy; this.dz = dz; }
        @Override public VertexConsumer vertex(double x, double y, double z) { parent.vertex(x + dx, y + dy, z + dz); return this; }
    }

    /** "Albedo" entities: full lightmap, so the shader pack lights the reflection once, on the glass. */
    private static final class FullBright extends VertexConsumerWrapper {
        FullBright(VertexConsumer parent) { super(parent); }
        @Override public VertexConsumer uv2(int u, int v) {
            int full = LightTexture.FULL_BRIGHT;
            parent.uv2(full & 0xFFFF, full >> 16 & 0xFFFF);
            return this;
        }
    }
}
