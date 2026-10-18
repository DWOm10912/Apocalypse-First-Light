package com.antaurora.apofirstlight.client.light;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dynamic Lights V1 (docs/rendering/dynamic_lights_v1.md): moving lights worked out per pixel in the world programs, from
 * where the lamps are this frame (the partial tick's pose), instead of as block light. Block light steps by whole blocks,
 * is shaded per vertex (lit triangles on the slopes) and every change rebuilds the chunk meshes it touches, which Embeddium
 * does synchronously for the sections near the camera (user 2026-10-17: headlamps 一卡一卡, 毛刺三角形).
 * <p>Each frame (the level pass, from AFTER_SKY to AFTER_LEVEL; the hand and the GUI get none) the {@link Provider}s add
 * their lights ({@link Lights#spot}: a {@link LightCone} at a pose, a colour, an occluder: one shadow origin that lights at
 * one end of a vehicle share), the block grid around the camera follows it ({@link OccupancyGrid}: the shadow rays' world)
 * and every patched program ({@link DynamicLightShaders}) gets the lights when it is bound: vanilla programs at
 * ShaderInstance#apply, Embeddium's terrain program at its state setup (mixins client.DynamicLightShaderApplyMixin,
 * client.DynamicLightEmbeddiumStateMixin).
 * <p>AFL's programs draw them only without a shader pack ({@link #active}). A shader pack that draws them itself (option
 * AFL_VEHICLE_LIGHTS on: Aureveil) gets the same lights and block grid as two textures it binds ({@link ShaderPackLights},
 * {@link #packActive}); with any other pack the lamps fall back to block light (client/vehicle/HeadlightField).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DynamicLights {
    public static final int MAX_LIGHTS = 24, MAX_OCCLUDERS = 8;
    private static final int LIGHT_FLOATS = 24;

    /** Adds this frame's lights (render thread, the level pass; positions in the world, interpolated by the partial tick). */
    public interface Provider {
        void collect(Lights out, ClientLevel level, Vec3 camera, float partialTick);
    }

    /** One frame's lights as the providers add them. */
    public static final class Lights {
        private final List<float[]> spots = new ArrayList<>();
        private final List<int[]> order = new ArrayList<>();
        private final float[] occluders = new float[MAX_OCCLUDERS * 4];
        private int occluderCount;
        private Vec3 camera = Vec3.ZERO;

        /** A shadow origin (world), the index to give its lights; past the limit, the last one again. */
        public int occluder(double x, double y, double z) {
            int i = Math.min(occluderCount, MAX_OCCLUDERS - 1);
            if (occluderCount < MAX_OCCLUDERS) occluderCount++;
            occluders[i * 4] = (float) (x - camera.x);
            occluders[i * 4 + 1] = (float) (y - camera.y);
            occluders[i * 4 + 2] = (float) (z - camera.z);
            return i;
        }

        /**
         * A spot light at (x, y, z) (world) facing {@code fwd} with {@code up} its up (both unit, at right angles), in the
         * colour (r, g, b: 1 = the cone's levels as they are), its shadow from {@code occluder}. Past the limit, dropped.
         */
        public void spot(LightCone c, double x, double y, double z, Vec3 fwd, Vec3 up, float r, float g, float b, int occluder) {
            if (spots.size() >= MAX_LIGHTS || occluder < 0) return;
            float[] f = new float[LIGHT_FLOATS];
            f[0] = (float) (x - camera.x);
            f[1] = (float) (y - camera.y);
            f[2] = (float) (z - camera.z);
            f[3] = (float) c.reach();
            f[4] = (float) fwd.x;
            f[5] = (float) fwd.y;
            f[6] = (float) fwd.z;
            f[7] = (float) c.near();
            f[8] = (float) up.x;
            f[9] = (float) up.y;
            f[10] = (float) up.z;
            f[11] = (float) c.far();
            f[12] = (float) Math.toRadians(c.aimDown());
            f[13] = (float) Math.toRadians(c.across());
            f[14] = (float) Math.toRadians(c.below());
            f[15] = (float) Math.toRadians(c.above());
            f[16] = r;
            f[17] = g;
            f[18] = b;
            f[19] = (float) c.rightReach();
            f[20] = occluder;
            f[21] = (float) c.nearDistance();
            f[22] = (float) c.back();
            f[23] = (float) c.belowLoss();
            order.add(new int[]{occluder, spots.size()});
            spots.add(f);
        }

        private void reset(Vec3 camera) {
            spots.clear();
            order.clear();
            occluderCount = 0;
            this.camera = camera;
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final Lights LIGHTS = new Lights();
    private static final OccupancyGrid GRID = new OccupancyGrid();
    private static final FloatBuffer LIGHT_DATA = MemoryUtil.memAllocFloat(MAX_LIGHTS * LIGHT_FLOATS),
            OCCLUDER_DATA = MemoryUtil.memAllocFloat(MAX_OCCLUDERS * 4), MATRIX = MemoryUtil.memAllocFloat(16);
    private static final Matrix4f CLIP_TO_WORLD = new Matrix4f();

    /** A switch for testing (the fallback block light comes back when off). */
    public static volatile boolean enabled = true;

    /** This frame's state: the lights (0 outside the level pass), the camera's block and its fraction, the screen, gamma. */
    private static int count;
    private static boolean inLevel;
    private static int stamp;
    private static int camBX, camBY, camBZ;
    private static float camFX, camFY, camFZ, screenW = 1, screenH = 1, gamma;
    /** Each program's uniform locations (by GL program id; cleared when programs are made again). */
    private static final Int2ObjectOpenHashMap<Locations> LOCATIONS = new Int2ObjectOpenHashMap<>();
    /** Diagnostics: the last frame's lights and collect time (us). */
    public static int lastLights;
    public static long lastMicros;

    private DynamicLights() {
    }

    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    /** The lights are drawn per pixel now: no shader pack, the terrain program patched, switched on. */
    public static boolean active() {
        return enabled && DynamicLightShaders.chunkReady() && !AflShaderCompat.shaderPackInUse();
    }

    /** A shader pack draws the lights from AFL's textures (its option AFL_VEHICLE_LIGHTS is on). */
    public static boolean packActive() {
        return enabled && AflShaderCompat.shaderPackInUse() && ShaderPackLights.wanted();
    }

    /** The lamps are drawn per pixel (by AFL's programs or the shader pack): no block light, no tail lamp ground decal. */
    public static boolean perPixel() {
        return active() || packActive();
    }

    /** Client setup (render thread): the shader pack's textures registered before it first binds them. */
    public static void registerTextures() {
        ShaderPackLights.register();
    }

    /** The lights are drawn in this frame's level pass (render thread): the programs that carry them are in use. */
    public static boolean drawing() {
        return inLevel;
    }

    public static String status() {
        return (active() ? "on" : packActive() ? "on (shader pack)" : "off") + "; " + DynamicLightShaders.status() + "; " + lastLights + " lights (" + lastMicros
                + " us); grid sections built " + GRID.built + " (" + GRID.lastMicros + " us last frame)";
    }

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) begin(event);
        else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            inLevel = false;
            stamp++;
        }
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel && event.getLevel() == Minecraft.getInstance().level)
            GRID.chunkLoaded(event.getChunk().getPos().x, event.getChunk().getPos().z);
    }

    /** A block changed on the client (mixin client.DynamicLightBlockChangeMixin). */
    public static void blockChanged(ClientLevel level, BlockPos pos) {
        if (level == Minecraft.getInstance().level && RenderSystem.isOnRenderThread()) GRID.changed(pos);
    }

    /** Programs were made again (a resource reload): their uniform locations are looked up afresh. */
    static void programsChanged() {
        LOCATIONS.clear();
    }

    private static void begin(RenderLevelStageEvent event) {
        inLevel = false;
        stamp++;
        count = 0;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        boolean own = level != null && active(), pack = level != null && !own && packActive();
        if (!own && !pack) {
            // the pack's header back to no lights (switched off, another pack, left the world)
            if (AflShaderCompat.shaderPackInUse()) ShaderPackLights.upload(LIGHT_DATA, 0, LIGHTS.occluders, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            return;
        }
        long t0 = System.nanoTime();
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        LIGHTS.reset(cam);
        try {
            for (Provider p : PROVIDERS) p.collect(LIGHTS, level, cam, pt);
            // kept up with the camera with or without lights, so shadows are there the moment a lamp comes on
            GRID.update(level, cam.x, cam.y, cam.z, pack);
        } catch (RuntimeException e) {
            // never take the frame down: off for the session (the lamps go back to block light), logged once
            enabled = false;
            LOGGER.error("AFL dynamic lights: switched off after an error", e);
            return;
        }
        // lights sharing an occluder next to each other: one shadow ray per pixel for them
        LIGHTS.order.sort(Comparator.comparingInt(o -> o[0]));
        LIGHT_DATA.clear();
        for (int[] o : LIGHTS.order) LIGHT_DATA.put(LIGHTS.spots.get(o[1]));
        LIGHT_DATA.flip();
        OCCLUDER_DATA.clear();
        OCCLUDER_DATA.put(LIGHTS.occluders);
        OCCLUDER_DATA.flip();
        count = LIGHTS.spots.size();
        lastLights = count;
        int bx = Mth.floor(cam.x), by = Mth.floor(cam.y), bz = Mth.floor(cam.z);
        camBX = bx;
        camBY = by;
        camBZ = bz;
        camFX = (float) (cam.x - bx);
        camFY = (float) (cam.y - by);
        camFZ = (float) (cam.z - bz);
        if (pack) {
            // the shader pack reads them from its textures (the hand pass too); AFL's programs are not in use
            ShaderPackLights.upload(LIGHT_DATA, count, LIGHTS.occluders, LIGHTS.occluderCount, GRID.placed(),
                    GRID.minBlockX() - bx, GRID.minBlockY() - by, GRID.minBlockZ() - bz, bx, by, bz, camFX, camFY, camFZ);
            lastMicros = (System.nanoTime() - t0) / 1000;
            return;
        }
        // clip = P * modelView * levelPose * (world - camera): the level pose is the camera's turn (entities are posed on
        // the CPU with it, the terrain programs get it as their model-view)
        CLIP_TO_WORLD.set(event.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(event.getPoseStack().last().pose()).invert();
        RenderTarget target = mc.getMainRenderTarget();
        screenW = target.width;
        screenH = target.height;
        gamma = mc.options.gamma().get().floatValue();
        lastMicros = (System.nanoTime() - t0) / 1000;
        inLevel = true;
    }

    /**
     * The bound program's lights (its GL id): the uniforms once per frame state (lights on, then off after the level pass),
     * the block grid on its texture unit whenever lights are on. Programs without the light code are left alone.
     */
    public static void bind(int program) {
        if (program == 0) return;
        Locations l = LOCATIONS.get(program);
        if (l == null) {
            l = new Locations(program);
            LOCATIONS.put(program, l);
        }
        if (l.count < 0) return;
        int c = inLevel ? count : 0;
        if (l.stamp != stamp) {
            l.stamp = stamp;
            GL20.glUniform1i(l.count, c);
            if (c > 0) {
                LIGHT_DATA.position(0).limit(c * LIGHT_FLOATS);
                if (l.lights >= 0) GL20.glUniform4fv(l.lights, LIGHT_DATA);
                LIGHT_DATA.limit(LIGHT_DATA.capacity());
                OCCLUDER_DATA.position(0);
                if (l.occluders >= 0) GL20.glUniform4fv(l.occluders, OCCLUDER_DATA);
                if (l.camBlock >= 0) GL20.glUniform3i(l.camBlock, camBX, camBY, camBZ);
                if (l.camFrac >= 0) GL20.glUniform3f(l.camFrac, camFX, camFY, camFZ);
                if (l.occMin >= 0) GL20.glUniform3i(l.occMin, GRID.minBlockX(), GRID.minBlockY(), GRID.minBlockZ());
                if (l.screen >= 0) GL20.glUniform2f(l.screen, screenW, screenH);
                if (l.gamma >= 0) GL20.glUniform1f(l.gamma, gamma);
                if (l.sampler >= 0) GL20.glUniform1i(l.sampler, OccupancyGrid.UNIT - GL13.GL_TEXTURE0);
            }
        }
        // the matrix whenever in the level pass, lights or not (the road program's fog distance comes from it)
        if (inLevel && l.clip >= 0) {
            MATRIX.clear();
            CLIP_TO_WORLD.get(MATRIX);
            GL20.glUniformMatrix4fv(l.clip, false, MATRIX);
        }
        if (c > 0) {
            int prev = GlStateManager._getActiveTexture();
            GL13.glActiveTexture(OccupancyGrid.UNIT);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, GRID.texture());
            GL13.glActiveTexture(prev);
        }
    }

    /** A program's light uniforms (-1: not in it; count -1: the program has no light code). */
    private static final class Locations {
        final int count, lights, occluders, sampler, camBlock, camFrac, occMin, clip, screen, gamma;
        int stamp = Integer.MIN_VALUE;

        Locations(int program) {
            count = GL20.glGetUniformLocation(program, "afl_LightCount");
            lights = GL20.glGetUniformLocation(program, "afl_Lights");
            occluders = GL20.glGetUniformLocation(program, "afl_Occluders");
            sampler = GL20.glGetUniformLocation(program, "afl_OccTex");
            camBlock = GL20.glGetUniformLocation(program, "afl_CamBlock");
            camFrac = GL20.glGetUniformLocation(program, "afl_CamFrac");
            occMin = GL20.glGetUniformLocation(program, "afl_OccMin");
            clip = GL20.glGetUniformLocation(program, "afl_ClipToWorld");
            screen = GL20.glGetUniformLocation(program, "afl_ScreenSize");
            gamma = GL20.glGetUniformLocation(program, "afl_Gamma");
        }
    }
}
