package com.antaurora.apofirstlight.client.rain;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.GpuTimer;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.WiperSystem;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The water on the T15s' glass (docs/vehicles/t15_windshield_rain_design.md), without a shader pack: each truck within
 * {@link #RANGE} keeps a {@link GlassWater} per pane ({@link TruckGlass}: the windshield with its wipers, the rear window,
 * the door windows, which turn with their doors), stepped every frame (the wipers as the truck draws them, WiperSystem).
 * Drawn after the weather, over a copy of the frame (colour with its mip chain, and depth), with the water textures
 * simulated offline (textures/effect/windshield_beads, windshield_drops; tools/blender/windshield_water):
 * <ul>
 * <li>the film (afl_windshield_film): a mesh on the glass; the fine beads (a tile, built up as the film map / Outside
 * say), each a lens, far ones as their mean view; the streak a blade just left smears lights along the stroke;</li>
 * <li>the drops (afl_windshield_drop): a quad each on the glass, drawn with a simulated shape (resting, going over to a
 * running one as it slides); slide trails and the water riding on a blade as wet lines.</li>
 * </ul>
 * Both test the copied depth themselves with a small tolerance (the water lies on the glass, which writes depth), so the
 * steering wheel and the pillars hide them and the glass does not. With a shader pack AFL draws nothing (the wipers still
 * move: they are the truck's own model); one that draws the rain itself (RainShaderPack, AFL_RAIN) gets the water kept
 * and baked as cover and normal into dynamic/afl_glass_water at the start of the frame ({@link #packFrame}, GlassAtlas,
 * afl_glass_bake_film / afl_glass_bake_drop; docs/rendering/rain_v1.md section 6).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WindshieldRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Trucks with water kept (m from the camera, at most), drops drawn within DROP_RANGE (beyond they are under a pixel). */
    private static final double RANGE = 24, DROP_RANGE = 10;
    private static final int TRUCKS = 3;

    /** A switch for testing (/afl_render windshield on|off). */
    public static volatile boolean enabled = true;
    private static volatile ShaderInstance film, drop, bakeFilm, bakeDrop;
    private static boolean failed, packFailed;
    private static final Map<PickupTruckEntity, Glass> GLASS = new WeakHashMap<>();
    private static final SceneCopy COPY = new SceneCopy();
    private static final WaterTextures TEXTURES = new WaterTextures();
    private static final BufferBuilder BUILDER = new BufferBuilder(1 << 18);
    private static long lastNanos;
    private static ClientLevel tracked;
    /** Diagnostics: the last frame's time (us), drops drawn. */
    public static long lastMicros;
    public static int lastDrops;
    /** Diagnostics: the last frame's CPU time stepping the water and drawing it (us); GPU means (GpuTimer). */
    public static long simMicros, drawMicros;
    public static final GpuTimer GPU_COPY = new GpuTimer(), GPU_FILM = new GpuTimer(), GPU_DROPS = new GpuTimer(), GPU_BAKE = new GpuTimer();
    /** Diagnostics, with the shader pack: CPU time of the last bake into the glass map (us). */
    public static long bakeMicros;

    private WindshieldRenderer() {
    }

    /** One truck's water, a share per pane (TruckGlass.PANES), and the windshield's film map texture. */
    private static final class Glass {
        final GlassWater[] panes = new GlassWater[TruckGlass.PANES.length];
        final DynamicTexture texture = new DynamicTexture(GlassWater.FW, GlassWater.FH, false);
        long seen;

        Glass(int id) {
            for (int i = 0; i < panes.length; i++) panes[i] = new GlassWater(TruckGlass.PANES[i], 0x57A7E5L * 31 + id * 7L + i);
            texture.setFilter(true, false);
        }

        boolean any() {
            for (GlassWater w : panes) if (w.any) return true;
            return false;
        }

        void upload() {
            GlassWater water = panes[0];
            if (!water.filmDirty) return;
            water.filmDirty = false;
            NativeImage img = texture.getPixels();
            if (img == null) return;
            for (int y = 0; y < GlassWater.FH; y++)
                for (int x = 0; x < GlassWater.FW; x++) {
                    int c = y * GlassWater.FW + x;
                    int w = (int) (Math.min(1, water.wet[c]) * 255), st = (int) (Math.min(1, water.streak[c]) * 255);
                    img.setPixelRGBA(x, y, 0xFF000000 | st << 8 | w);   // ABGR: red = wet, green = streak
                }
            texture.upload();
        }
    }

    public static boolean active() {
        return enabled && !failed && film != null && drop != null && !AflShaderCompat.shaderPackInUse();
    }

    /** The shader pack draws the water from AFL's glass map (RainShaderPack): the water is kept and baked for it. */
    static boolean packActive() {
        return enabled && !packFailed && bakeFilm != null && bakeDrop != null && RainShaderPack.active();
    }

    public static String status() {
        int n = 0, drops = 0;
        synchronized (GLASS) {
            for (Glass g : GLASS.values()) {
                n++;
                for (GlassWater w : g.panes) drops += w.count;
            }
        }
        return (active() ? "on" : packActive() ? "baked for the shader pack" : "off") + "; " + lastMicros + " us last frame; " + n + " trucks kept (" + TruckGlass.PANES.length + " panes each), " + drops + " drops, "
                + lastDrops + " drawn; cpu sim " + simMicros + " us, draw " + drawMicros + " us; gpu copy " + GPU_COPY.micros() + " us, film "
                + GPU_FILM.micros() + " us, drops " + GPU_DROPS.micros() + " us; bake cpu " + bakeMicros + " us, gpu " + GPU_BAKE.micros() + " us";
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Programs {
        private Programs() {
        }

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            TEXTURES.stale = true;   // resources (re)loaded: the water textures too, on the next frame
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_windshield_film"),
                        DefaultVertexFormat.POSITION_TEX), loaded -> film = loaded);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_windshield_drop"),
                        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), loaded -> drop = loaded);
            } catch (Exception e) {
                film = drop = null;
                LOGGER.warn("AFL windshield water: no programs ({}); the glass stays dry", e.toString());
            }
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_glass_bake_film"),
                        DefaultVertexFormat.POSITION_TEX), loaded -> bakeFilm = loaded);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_glass_bake_drop"),
                        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), loaded -> bakeDrop = loaded);
            } catch (Exception e) {
                bakeFilm = bakeDrop = null;
                LOGGER.warn("AFL glass water for the shader pack: no programs ({}); its glass map stays dry", e.toString());
            }
        }
    }

    /** After the rain (RainRenderer, same stage): the water refracts the drops falling behind it too. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (level != tracked) {
            clear();
            tracked = level;
        }
        if (level == null || !active()) {
            // with the shader pack drawing the water (packFrame keeps it), it stays
            if (!GLASS.isEmpty() && (level == null || !packActive())) clear();
            return;
        }
        if (mc.isPaused()) dt = 0;
        long t0 = System.nanoTime();
        try {
            frame(mc, level, event.getCamera().getPosition(), event.getPartialTick(), dt, now);
        } catch (RuntimeException e) {
            failed = true;
            clear();
            LOGGER.error("AFL windshield water: switched off after an error", e);
        }
        lastMicros = (System.nanoTime() - t0) / 1000;
    }

    /** Per slot of GlassAtlas and pane: whether packFrame baked water there this frame (RainShaderPack writes t5 for those). */
    static final boolean[][] BAKED = new boolean[GlassAtlas.SLOTS][TruckGlass.PANES.length];
    private static int bakeFbo = -1, bakeFor = -1;
    private static boolean atlasClear;

    /**
     * With the shader pack drawing the water (RainShaderPack.renderTick, the start of the frame, before the world pass):
     * the water stepped as without one, and
     * the panes of the trucks in slots (the trucks texture's rows, nearest first) baked into the glass map (atlas, its GL
     * id; GlassAtlas) with its mip levels. Returns BAKED.
     */
    static boolean[][] packFrame(Minecraft mc, ClientLevel level, Vec3 cam, float pt, List<RainTrucks.Frame> slots, int atlas) {
        for (boolean[] b : BAKED) java.util.Arrays.fill(b, false);
        if (!packActive()) return BAKED;
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (level != tracked) {
            clear();
            tracked = level;
        }
        if (mc.isPaused()) dt = 0;
        long t0 = System.nanoTime();
        try {
            simulate(level, cam, pt, dt, now);
            long t1 = System.nanoTime();
            simMicros = (t1 - t0) / 1000;
            GPU_BAKE.begin();
            bake(mc, slots, pt, atlas);
            GPU_BAKE.end();
            bakeMicros = (System.nanoTime() - t1) / 1000;
        } catch (RuntimeException e) {
            packFailed = true;
            clear();
            for (boolean[] b : BAKED) java.util.Arrays.fill(b, false);
            LOGGER.error("AFL glass water for the shader pack: switched off after an error", e);
        }
        lastMicros = (System.nanoTime() - t0) / 1000;
        return BAKED;
    }

    private static void bake(Minecraft mc, List<RainTrucks.Frame> slots, float pt, int atlas) {
        lastDrops = 0;
        boolean any = false;
        for (int t = 0; t < slots.size() && t < GlassAtlas.SLOTS; t++) {
            Glass g;
            synchronized (GLASS) {
                g = GLASS.get(slots.get(t).truck);
            }
            if (g != null) for (int i = 0; i < g.panes.length; i++) any |= BAKED[t][i] = g.panes[i].any;
        }
        boolean fresh = bakeFor != atlas;
        if (!any && atlasClear && !fresh) return;
        // at the start of the frame (RainShaderPack.renderTick), outside the world pass: Oculus locks colour and depth writes
        // of every non-Iris ShaderInstance while it renders the world (MixinShaderInstance -> DepthColorStorage), so a bake
        // there drew nothing (Aureveil A, 2026-10-17). No glGet to save the state either (each one makes the game wait for the
        // driver thread): the state at that point is known (Minecraft#runTick: main target bound, cull on; the last frame's
        // blitToScreen left depth test and blend off, depth and colour masks on) and is put back as that
        try {
            if (fresh) {
                allocateAtlas(atlas);
                bakeFor = atlas;
            }
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, bakeFbo);
            RenderSystem.colorMask(true, true, true, true);
            GlStateManager._viewport(0, 0, GlassAtlas.W, GlassAtlas.H);
            // cleared to dry (normal 0.5, 0.5; cover 0) with an explicit value: the clear colour state is left alone
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, DRY);
            atlasClear = !any;
            if (any) {
                TEXTURES.ensure(mc);
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                RenderSystem.enableBlend();
                for (int t = 0; t < slots.size() && t < GlassAtlas.SLOTS; t++) {
                    Glass g;
                    synchronized (GLASS) {
                        g = GLASS.get(slots.get(t).truck);
                    }
                    if (g == null) continue;
                    g.upload();
                    for (int i = 0; i < g.panes.length; i++) {
                        if (!BAKED[t][i]) continue;
                        int[] r = GlassAtlas.TEXELS[t][i];
                        GlStateManager._viewport(r[0], r[1], r[2], r[3]);
                        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
                        bakeFilm(g, g.panes[i]);
                        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
                        drawDrops(g.panes[i], true);
                    }
                }
            }
            GlStateManager._bindTexture(atlas);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._bindTexture(0);
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }
    }

    private static final float[] DRY = {0.5F, 0.5F, 0, 0};

    /** The glass map's storage (RGBA8, GlassAtlas.LEVELS mip levels below the base, linear) and the framebuffer onto level 0. */
    private static void allocateAtlas(int atlas) {
        GlStateManager._bindTexture(atlas);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, GlassAtlas.LEVELS);
        for (int l = 0; l <= GlassAtlas.LEVELS; l++)
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, l, GL11.GL_RGBA8, GlassAtlas.W >> l, GlassAtlas.H >> l, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GlStateManager._bindTexture(0);
        if (bakeFbo < 0) bakeFbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, bakeFbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, atlas, 0);
        int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("glass map: framebuffer status " + status);
    }

    /** The glass layer of one pane into its rectangle (the viewport): a quad over its glass range. */
    private static void bakeFilm(Glass g, GlassWater water) {
        TruckGlass.Pane pane = water.pane;
        ShaderInstance sh = bakeFilm;
        film(sh, g, water);
        RenderSystem.setShader(() -> sh);
        float u0 = (float) GlassAtlas.u0(pane), u1 = (float) GlassAtlas.u1(pane), s0 = (float) GlassAtlas.s0(pane), s1 = (float) GlassAtlas.s1(pane);
        box(sh, pane);
        BufferBuilder b = BUILDER;
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(u0, s0, 0).uv(u0, s0).endVertex();
        b.vertex(u1, s0, 0).uv(u1, s0).endVertex();
        b.vertex(u1, s1, 0).uv(u1, s1).endVertex();
        b.vertex(u0, s1, 0).uv(u0, s1).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    /** The baking programs' Box (the pane's glass range: what the viewport spans) and its outline. */
    private static void box(ShaderInstance sh, TruckGlass.Pane pane) {
        sh.safeGetUniform("Box").set((float) GlassAtlas.u0(pane), (float) GlassAtlas.s0(pane), (float) GlassAtlas.u1(pane), (float) GlassAtlas.s1(pane));
        sh.safeGetUniform("Outline").set((float) pane.aL, (float) pane.bL, (float) pane.aR, (float) pane.bR);
        sh.safeGetUniform("Span").set((float) pane.s0, (float) pane.s1, (float) pane.corner);
    }

    private static void clear() {
        synchronized (GLASS) {
            for (Glass g : GLASS.values()) g.texture.close();
            GLASS.clear();
        }
    }

    private record Shown(PickupTruckEntity truck, Glass glass, double distance) {}

    private static void frame(Minecraft mc, ClientLevel level, Vec3 cam, float pt, double dt, long now) {
        long t0 = System.nanoTime();
        List<Shown> shown = simulate(level, cam, pt, dt, now);
        long t1 = System.nanoTime();
        simMicros = (t1 - t0) / 1000;
        lastDrops = 0;
        if (shown.isEmpty()) return;
        TEXTURES.ensure(mc);
        RenderTarget main = mc.getMainRenderTarget();
        GPU_COPY.begin();
        boolean copied = COPY.copy(main);
        GPU_COPY.end();
        if (!copied) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // the sky's light on the water: its colour and the fog's (the rain's grey), dimmed at night and under heavy cloud
        float sky = level.getSkyDarken(pt), k = 0.25F + 0.75F * sky;
        Vec3 skyColor = level.getSkyColor(cam, pt);
        float[] fog = RenderSystem.getShaderFogColor();
        SKY_LIGHT.set(((float) skyColor.x + fog[0]) * 0.5F * k, ((float) skyColor.y + fog[1]) * 0.5F * k, ((float) skyColor.z + fog[2]) * 0.5F * k);
        // the glass layers of every pane, then the drops (two passes, so each is timed on its own; the panes do not overlap
        // in a way the order would show: one behind another is hidden by the copied depth)
        for (int pass = 0; pass < 2; pass++) {
            GpuTimer timer = pass == 0 ? GPU_FILM : GPU_DROPS;
            timer.begin();
            for (Shown s : shown) {
                if (pass == 0) s.glass.upload();
                else if (s.distance >= DROP_RANGE) continue;
                Quaternionf q = s.truck.orientation(pt, new Quaternionf());
                Vec3 pos = s.truck.getPosition(pt);
                float ox = (float) (pos.x - cam.x), oy = (float) (pos.y - cam.y), oz = (float) (pos.z - cam.z);
                float doorL = s.truck.doorAngle(false, pt), doorR = s.truck.doorAngle(true, pt);
                for (GlassWater water : s.glass.panes) {
                    if (!water.any) continue;
                    TruckGlass.Pane pane = water.pane;
                    place(pane, q, ox, oy, oz, pane.hinge == TruckGlass.Hinge.DOOR_L ? -doorL : pane.hinge == TruckGlass.Hinge.DOOR_R ? doorR : 0);
                    // the pane's axes in the world, and which side of it the camera is on (the cab's or the world's)
                    axis(pane.axisU, AU);
                    axis(pane.axisS, AS);
                    axis(pane.axisN, AN);
                    pane.model(pane.uMid(), pane.sMid(), 0, P, 0);
                    toCamera(P, V);
                    inside = V.x * AN.x + V.y * AN.y + V.z * AN.z > 0;
                    if (pass == 0) drawFilm(s.glass, water);
                    else drawDrops(water, false);
                }
            }
            timer.end();
        }
        drawMicros = (System.nanoTime() - t1) / 1000;
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** The water on the trucks within RANGE stepped (dt s), gone trucks' dropped; those with water, nearest first. */
    private static List<Shown> simulate(ClientLevel level, Vec3 cam, float pt, double dt, long now) {
        List<Shown> shown = new ArrayList<>();
        float rainLevel = level.getRainLevel(pt);
        for (RainTrucks.Frame f : RainTrucks.near(level, cam, RANGE, TRUCKS, pt)) {
            PickupTruckEntity truck = f.truck;
            Glass g;
            synchronized (GLASS) {
                g = GLASS.computeIfAbsent(truck, t -> new Glass(t.getId()));
            }
            g.seen = now;
            // rain on the glass: raining here and open to the sky above the windshield's middle
            Vec3 mid = truck.framePoint(0.70, 0, 1.75, pt);
            double rain = rainLevel > 0 && level.isRainingAt(BlockPos.containing(mid.x, mid.y + 0.5, mid.z)) ? rainLevel : 0;
            double forward = (truck.getX() - truck.xo) * f.f.x + (truck.getY() - truck.yo) * f.f.y + (truck.getZ() - truck.zo) * f.f.z;
            WiperSystem w = truck.wipers(pt);
            for (GlassWater water : g.panes) water.update(dt, rain, forward * 20, w.left(), w.right());
            if (g.any()) shown.add(new Shown(truck, g, Math.sqrt(truck.distanceToSqr(cam))));
        }
        // trucks gone or out of range: their water goes
        synchronized (GLASS) {
            for (Iterator<Map.Entry<PickupTruckEntity, Glass>> it = GLASS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<PickupTruckEntity, Glass> e = it.next();
                if (e.getKey().isRemoved() || now - e.getValue().seen > 5_000_000_000L) {
                    e.getValue().texture.close();
                    it.remove();
                }
            }
        }
        return shown;
    }

    private static final Vector3f SKY_LIGHT = new Vector3f(), AN = new Vector3f();
    private static boolean inside;

    private static void common(ShaderInstance sh) {
        sh.setSampler("Scene", COPY.color);
        sh.setSampler("SceneDepth", COPY.depth);
        sh.safeGetUniform("SkyLight").set(SKY_LIGHT.x, SKY_LIGHT.y, SKY_LIGHT.z);
        // the cab as the water shows it where its light cannot get out (dim, about as bright as the sky makes it)
        sh.safeGetUniform("Interior").set(SKY_LIGHT.x * 0.18F, SKY_LIGHT.y * 0.18F, SKY_LIGHT.z * 0.18F);
        sh.safeGetUniform("Inside").set(inside ? 1.0F : 0.0F);
        sh.safeGetUniform("GlassAcross").set(AU.x, AU.y, AU.z);
        sh.safeGetUniform("GlassUp").set(AS.x, AS.y, AS.z);
        sh.safeGetUniform("GlassOut").set(AN.x, AN.y, AN.z);
        RenderSystem.setShader(() -> sh);
    }

    /** Where the pane being drawn lies: the truck's turn times its door's (QP), about the hinge (PIVOT), from BASE. */
    private static final Quaternionf QP = new Quaternionf();
    private static final Vector3f BASE = new Vector3f(), PIVOT = new Vector3f(), T = new Vector3f();

    /** Sets up the pane's place: the truck (q, at o from the camera) and, for a door window, its door turned deg about Y. */
    private static void place(TruckGlass.Pane pane, Quaternionf q, float ox, float oy, float oz, float deg) {
        if (pane.hinge == TruckGlass.Hinge.NONE) PIVOT.set(0, 0, 0);
        else PIVOT.set(pane.hinge == TruckGlass.Hinge.DOOR_L ? -TruckGlass.HINGE_X : TruckGlass.HINGE_X, TruckGlass.HINGE_Y, TruckGlass.HINGE_Z);
        q.transform(BASE.set(PIVOT)).add(ox, oy, oz);
        QP.set(q);
        if (deg != 0) QP.rotateY((float) Math.toRadians(deg));
    }

    /** A model-frame point of the pane being drawn, camera-relative. */
    private static void toCamera(float[] p, Vector3f out) {
        QP.transform(out.set(p[0] - PIVOT.x, p[1] - PIVOT.y, p[2] - PIVOT.z)).add(BASE);
    }

    private static void axis(double[] a, Vector3f out) {
        QP.transform(out.set((float) a[0], (float) a[1], (float) a[2]));
    }

    private static void drawFilm(Glass g, GlassWater water) {
        TruckGlass.Pane pane = water.pane;
        ShaderInstance sh = film;
        common(sh);
        film(sh, g, water);
        BufferBuilder b = BUILDER;
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float[] mesh = filmMesh(pane);
        Vector3f v = FILM_V;
        for (int k = 0; k < mesh.length; k += 5) {
            QP.transform(v.set(mesh[k] - PIVOT.x, mesh[k + 1] - PIVOT.y, mesh[k + 2] - PIVOT.z)).add(BASE);
            b.vertex(v.x, v.y, v.z).uv(mesh[k + 3], mesh[k + 4]).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
    }

    /** Each pane's film mesh in the truck's model frame (x, y, z, u, s a corner, four a cell): the outline never changes. */
    private static final Map<TruckGlass.Pane, float[]> FILM_MESH = new java.util.IdentityHashMap<>();
    private static final Vector3f FILM_V = new Vector3f();

    /**
     * A mesh over the outline's box, cells about 4 cm (the windshield's curve needs them); cells wholly off the glass (the
     * rounded corners, the slanted sides) left out. Worked out once a pane (each cell asks the outline four times; per
     * frame that was most of the glass water's CPU time, 2026-10-17).
     */
    private static float[] filmMesh(TruckGlass.Pane pane) {
        float[] mesh = FILM_MESH.get(pane);
        if (mesh != null) return mesh;
        double u0 = pane.uMin - 0.01, u1 = pane.uMax + 0.01, s0 = pane.s0 - 0.004, s1 = pane.s1 + 0.004;
        int nu = Math.max(4, (int) Math.ceil((u1 - u0) / 0.04)), ns = Math.max(4, (int) Math.ceil((s1 - s0) / 0.036));
        float[] all = new float[nu * ns * 4 * 5];
        int n = 0;
        for (int j = 0; j < ns; j++)
            for (int i = 0; i < nu; i++) {
                double ua = u0 + (u1 - u0) * i / nu, ub = u0 + (u1 - u0) * (i + 1) / nu;
                double sa = s0 + (s1 - s0) * j / ns, sb = s0 + (s1 - s0) * (j + 1) / ns;
                if (Math.max(Math.max(pane.inside(ua, sa), pane.inside(ub, sa)), Math.max(pane.inside(ub, sb), pane.inside(ua, sb))) < -0.03) continue;
                n = filmCorner(all, n, pane, ua, sa);
                n = filmCorner(all, n, pane, ub, sa);
                n = filmCorner(all, n, pane, ub, sb);
                n = filmCorner(all, n, pane, ua, sb);
            }
        mesh = java.util.Arrays.copyOf(all, n);
        FILM_MESH.put(pane, mesh);
        return mesh;
    }

    private static int filmCorner(float[] mesh, int n, TruckGlass.Pane pane, double u, double s) {
        pane.model(u, s, WindshieldGlass.OFF * 0.6, mesh, n);
        mesh[n + 3] = (float) u;
        mesh[n + 4] = (float) s;
        return n + 5;
    }

    /** The glass layer's inputs (the film and bead maps, wetness, outline, the wipers' sweeps), drawn or baked. */
    private static void film(ShaderInstance sh, Glass g, GlassWater water) {
        TruckGlass.Pane pane = water.pane;
        sh.setSampler("Film", g.texture.getId());
        sh.setSampler("Beads", TEXTURES.beads);
        sh.safeGetUniform("Outside").set(water.outside);
        sh.safeGetUniform("Outline").set((float) pane.aL, (float) pane.bL, (float) pane.aR, (float) pane.bR);
        sh.safeGetUniform("Span").set((float) pane.s0, (float) pane.s1, (float) pane.corner);
        if (pane.wipers.length == 2) {
            WindshieldGlass.Wiper l = pane.wipers[0], r = pane.wipers[1];
            sh.safeGetUniform("Pivots").set((float) l.pu(), (float) l.ps(), (float) r.pu(), (float) r.ps());
            sh.safeGetUniform("Blades").set((float) (l.arm() - l.len() / 2), (float) (l.arm() + l.len() / 2),
                    (float) (r.arm() - r.len() / 2), (float) (r.arm() + r.len() / 2));
            sh.safeGetUniform("Sweeps").set((float) Math.toRadians(l.sweep()), (float) Math.toRadians(r.sweep()), (float) l.rs(), (float) r.rs());
        } else {
            // no blades: no sweep holds a point (the beads everywhere build up as Outside says)
            sh.safeGetUniform("Pivots").set(0F, 0F, 0F, 0F);
            sh.safeGetUniform("Blades").set(0F, 0F, 0F, 0F);
            sh.safeGetUniform("Sweeps").set(0F, 0F, 0F, 0F);
        }
    }

    private static final float[] P = new float[3];
    private static final Vector3f V = new Vector3f(), AU = new Vector3f(), AS = new Vector3f();
    /** The drop shapes' cells are R_EQ cell half widths round (tools/blender/windshield_water/gen_textures.py). */
    private static final float R_EQ = 0.5F;
    /** A wet line's cover reaches this share of its quad's half width; its segments fade over their ends (afl_windshield_drop). */
    private static final float LINE_WIDTH = 0.78F, LINE_LENGTH = 1.45F;
    private static final int LINE = 255;

    /** Quads go out in glass coordinates (u, s, 0: baking into the glass map) instead of camera-relative. */
    private static boolean flat;
    private static final Vector3f FLAT_U = new Vector3f(1, 0, 0), FLAT_S = new Vector3f(0, 1, 0);

    /** The drops, slide trails and blade loads of one pane: drawn over the frame, or (bake) into its glass map rectangle. */
    private static void drawDrops(GlassWater w, boolean bake) {
        TruckGlass.Pane pane = w.pane;
        ShaderInstance sh = bake ? bakeDrop : drop;
        flat = bake;
        if (bake) {
            box(sh, pane);
            RenderSystem.setShader(() -> sh);
        } else {
            common(sh);
        }
        sh.setSampler("Drops", TEXTURES.drops);
        BufferBuilder b = BUILDER;
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        int n = 0;
        // slide trails first, under the drops: thin wet lines (a flat, round-edged film), fading over about eight seconds
        for (int t = 0; t < GlassWater.TRAILS; t++) {
            int k = t * 6;
            float age = w.trail[k + 5], width = w.trail[k + 4];
            if (width <= 0 || age > 8) continue;
            float du = w.trail[k + 2] - w.trail[k], ds = w.trail[k + 3] - w.trail[k + 1], len = (float) Math.hypot(du, ds);
            if (len < 1e-5F) continue;
            quad(b, pane, (w.trail[k] + w.trail[k + 2]) * 0.5, (w.trail[k + 1] + w.trail[k + 3]) * 0.5, du / len, ds / len,
                    len / LINE_LENGTH, width * 0.5F / LINE_WIDTH, 0, LINE, 0.16F, 1 - age / 8);
            n++;
        }
        // the drops: a resting shape (tilted a little off straight down the glass), going over to a running one (its head
        // the way it runs) as it slides
        for (int i = 0; i < w.count; i++) {
            float k = w.runK[i], half = w.r[i] / R_EQ;
            float ru = (float) Math.sin(w.tilt[i]), rs = (float) Math.cos(w.tilt[i]);
            float du = ru + (w.wu[i] - ru) * k, ds = rs + (w.ws[i] - rs) * k, len = (float) Math.hypot(du, ds);
            if (len < 1e-4F) {
                du = w.wu[i];
                ds = w.ws[i];
                len = 1;
            }
            quad(b, pane, w.u[i], w.s[i], du / len, ds / len, half, half, w.rest[i] & 0xFF, w.running[i] & 0xFF, k, 1);
            n++;
        }
        // what the blades push: a wet line riding on each blade's leading edge
        double[] at = new double[2];
        for (int k = 0; k < pane.wipers.length; k++) {
            WindshieldGlass.Wiper wp = pane.wipers[k];
            int dir = w.travel[k];
            if (dir == 0 || Double.isNaN(w.turn[k])) continue;
            float seg = (float) (wp.len() / GlassWater.BINS);
            for (int bin = 0; bin < GlassWater.BINS; bin++) {
                float v = w.load[k][bin];
                if (v <= 0) continue;
                float r = (float) Math.min(0.0028, Math.cbrt(v));
                double t = (bin + 0.5) / GlassWater.BINS - 0.5;
                wp.blade(t, w.turn[k], at);
                double rho = Math.hypot(at[0] - wp.pu(), at[1] - wp.ps());
                wp.blade(t, w.turn[k] + dir * (0.006 + r) / rho, at);
                double a = w.turn[k], bu = Math.cos(a), bs = Math.sin(a);
                quad(b, pane, at[0], at[1], (float) bu, (float) bs, seg / LINE_LENGTH * 1.2F, r * 0.8F / LINE_WIDTH, 0, LINE, 0.9F, 1);
                n++;
            }
        }
        lastDrops += n;
        if (n > 0) BufferUploader.drawWithShader(b.end());
        // released even when empty: an unreleased batch keeps the shared builder from ever rewinding, so it grew every
        // frame until it hit 2 GB and the game went out of memory (user, 2026-10-17)
        else b.end().release();
    }

    /**
     * One water quad: centre (u, s), its axis (du, ds) in glass coordinates, half sizes along / across it; shapes a and b
     * (cells, or b = LINE: a wet line), the blend between them (a line: its edge slope over LINE_WIDTH), its opacity.
     */
    private static void quad(BufferBuilder b, TruckGlass.Pane pane, double u, double s, float du, float ds,
                             float along, float across, int a, int bShape, float blend, float alpha) {
        float cx, cy, cz;
        Vector3f gu, gs;
        if (flat) {
            cx = (float) u;
            cy = (float) s;
            cz = 0;
            gu = FLAT_U;
            gs = FLAT_S;
        } else {
            pane.model(u, s, WindshieldGlass.OFF, P, 0);
            toCamera(P, V);
            cx = V.x;
            cy = V.y;
            cz = V.z;
            gu = AU;
            gs = AS;
        }
        // along = du * U + ds * S, across = the perpendicular (ds, -du) turned the same way
        float ax = (gu.x * du + gs.x * ds) * along, ay = (gu.y * du + gs.y * ds) * along, az = (gu.z * du + gs.z * ds) * along;
        float px = (gu.x * ds - gs.x * du) * across, py = (gu.y * ds - gs.y * du) * across, pz = (gu.z * ds - gs.z * du) * across;
        int bl = Math.round(Math.max(0, Math.min(1, blend)) * 255), al = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        int au = Math.round(du * 16383), as = Math.round(ds * 16383);
        // local: x across (+ toward the truck's right when upright), y along (+ up the glass when upright)
        b.vertex(cx - ax - px, cy - ay - py, cz - az - pz).color(a, bShape, bl, al).uv(-1, -1).uv2(au, as).endVertex();
        b.vertex(cx - ax + px, cy - ay + py, cz - az + pz).color(a, bShape, bl, al).uv(1, -1).uv2(au, as).endVertex();
        b.vertex(cx + ax + px, cy + ay + py, cz + az + pz).color(a, bShape, bl, al).uv(1, 1).uv2(au, as).endVertex();
        b.vertex(cx + ax - px, cy + ay - py, cz + az - pz).color(a, bShape, bl, al).uv(-1, 1).uv2(au, as).endVertex();
    }

    /** The water textures, simulated offline: mipmapped by the GPU (every channel averages right), loaded on each resource reload. */
    private static final class WaterTextures {
        int beads = -1, drops = -1;
        volatile boolean stale = true;

        void ensure(Minecraft mc) {
            if (!stale) return;
            stale = false;
            if (beads >= 0) TextureUtil.releaseTextureId(beads);
            if (drops >= 0) TextureUtil.releaseTextureId(drops);
            // the bead tile repeats and is seen at a slant: anisotropic, so a far field keeps its grain
            beads = load(mc, "windshield_beads", GL11.GL_REPEAT, 10, true);
            // the drop cells: a few levels only (the cells must not bleed into each other)
            drops = load(mc, "windshield_drops", GL12.GL_CLAMP_TO_EDGE, 4, false);
        }

        private static int load(Minecraft mc, String name, int wrap, int maxLevel, boolean anisotropic) {
            ResourceLocation id = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/effect/" + name + ".png");
            try (InputStream in = mc.getResourceManager().open(id); NativeImage img = NativeImage.read(in)) {
                int levels = Math.min(maxLevel, 31 - Integer.numberOfLeadingZeros(Math.min(img.getWidth(), img.getHeight())));
                int t = TextureUtil.generateTextureId();
                TextureUtil.prepareImage(t, levels, img.getWidth(), img.getHeight());
                img.upload(0, 0, 0, false);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
                if (anisotropic && GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                    float max = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                    GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(16F, max));
                }
                GlStateManager._bindTexture(0);
                return t;
            } catch (IOException e) {
                throw new UncheckedIOException("windshield water texture " + id, e);
            }
        }
    }

    /**
     * The frame copied: colour (with mipmaps, for the blur and the lamp glow) and depth (the water's own occlusion test),
     * blitted from the main target. The depth texture takes the main one's own format (a blit needs them alike).
     */
    private static final class SceneCopy {
        int fbo = -1, color = -1, depth = -1, width, height, depthFormat;

        boolean copy(RenderTarget main) {
            int w = main.width, h = main.height;
            if (w <= 0 || h <= 0) return false;
            int format = depthFormat(main);
            if (fbo < 0 || w != width || h != height || format != depthFormat) create(w, h, format);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            GlStateManager._bindTexture(color);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._bindTexture(0);
            main.bindWrite(false);
            return true;
        }

        private static int depthFormat(RenderTarget main) {
            GlStateManager._bindTexture(main.getDepthTextureId());
            int f = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_INTERNAL_FORMAT);
            GlStateManager._bindTexture(0);
            return f;
        }

        private void create(int w, int h, int format) {
            if (fbo >= 0) {
                GlStateManager._glDeleteFramebuffers(fbo);
                GlStateManager._deleteTexture(color);
                GlStateManager._deleteTexture(depth);
            }
            width = w;
            height = h;
            depthFormat = format;
            color = GlStateManager._genTexture();
            GlStateManager._bindTexture(color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 6);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            depth = GlStateManager._genTexture();
            GlStateManager._bindTexture(depth);
            boolean stencil = format == GL30.GL_DEPTH24_STENCIL8 || format == GL30.GL_DEPTH32F_STENCIL8;
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, w, h, 0, stencil ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT,
                    stencil ? (format == GL30.GL_DEPTH32F_STENCIL8 ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV : GL30.GL_UNSIGNED_INT_24_8) : GL11.GL_FLOAT,
                    (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            GlStateManager._bindTexture(0);
            fbo = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, stencil ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, depth, 0);
            int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("windshield scene copy: framebuffer status " + status);
        }
    }
}
