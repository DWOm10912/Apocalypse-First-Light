package com.antaurora.apofirstlight.client.rain;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.weather.ClientWeather;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;

import java.util.List;
import java.util.Random;

/**
 * Rain V1 (docs/rendering/rain_v1.md): realistic rain without a shader pack, in place of vanilla's rain sheets and pixel
 * splash particles (mixin client.RainVanillaMixin; vanilla snow and the rain sounds stay).
 * <ul>
 * <li>Drops: one static buffer of {@link #DROPS} quads drawn twice by apocalypse_firstlight:afl_rain (afl_rain.vsh: each
 * quad a drop fixed in the world, animated on the GPU): a small dense box around the camera and a large sparse one.
 * The program reads {@link RainColumns} (cover, light, rain or not per column) and the nearest trucks' rain shells
 * ({@link RainTrucks}: boxes, the windshield and hood cut along the drawn body; no rain in the cab), and is lit by the dynamic lights (headlamps light the rain).</li>
 * <li>Splashes: {@link RainSplashes}, crowns, ripples on water and droplets, on the CPU.</li>
 * </ul>
 * Only without a shader pack (with one its own weather program draws vanilla's rain; Aureveil draws its own).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RainRenderer {
    /** Drops in the buffer; streak exposure (s); trucks the drops are kept out of (nearest), the shell parts each. */
    private static final int DROPS = 20000, TRUCKS = 2, TRUCK_BOXES = 16;
    private static final float EXPOSURE = 0.04F;
    private static final double TRUCK_RANGE = 40;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RainColumns COLUMNS = new RainColumns();
    private static final RainSplashes SPLASHES = new RainSplashes();

    /** A switch for testing (/afl_render rain on|off: off brings back vanilla rain). */
    public static volatile boolean enabled = true;
    /** A switch for testing (RainPerfProbe): the splashes alone. */
    public static volatile boolean splashes = true;

    private static VertexBuffer drops;
    private static ClientLevel tracked;
    private static boolean failed;
    /** Diagnostics: the last frame's draw time (us). */
    public static long lastMicros;
    /** Diagnostics: CPU time of the last frame's splash drawing and of the last tick's splash step (us). */
    public static long splashMicros, tickMicros;
    /** Diagnostics: GPU means of the drops and the splashes (GpuTimer). */
    public static final com.antaurora.apofirstlight.client.GpuTimer GPU_DROPS = new com.antaurora.apofirstlight.client.GpuTimer(),
            GPU_SPLASHES = new com.antaurora.apofirstlight.client.GpuTimer();
    private static Locations locations;
    /**
     * The wind's drift so far (blocks), wrapped at a multiple of both boxes (12, 56), and the time it was taken at: a change
     * of wind bends the streaks from then on and never moves the drops (regional weather V1, 2026-10-21).
     */
    private static final double DRIFT_WRAP = 168 * 60;
    private static double driftX, driftZ, driftSeconds = Double.NaN;

    private RainRenderer() {
    }

    /** AFL draws the rain this frame: switched on, the program loaded, no shader pack. */
    public static boolean active() {
        return enabled && !failed && RainPrograms.rain() != null && !AflShaderCompat.shaderPackInUse();
    }

    public static String status() {
        int[] live = SPLASHES.counts(), landed = SPLASHES.landed;
        return (active() ? "on" : "off") + "; " + lastMicros + " us last frame; splashes live: " + live[0] + " crowns, " + live[1]
                + " ripples, " + live[2] + " droplets, " + live[3] + " tyre drops, " + live[4] + " tyre mist; landed last tick: road " + landed[0] + ", smoothed terrain " + landed[1]
                + ", blocks " + landed[2] + ", water " + landed[3] + ", vehicles " + landed[4] + "; left out inside a truck last frame "
                + SPLASHES.culled + "; cpu splashes " + splashMicros + " us, splash tick " + tickMicros + " us; gpu drops " + GPU_DROPS.micros() + " us, splashes " + GPU_SPLASHES.micros() + " us";
    }

    /** A T15's tyre spray is AFL's to draw (client/vehicle/TruckSpray): AFL's rain on, no shader pack, splashes on. */
    public static boolean drawsSpray() {
        return active() && splashes;
    }

    /** A drop a tyre throws (RainSplashes#sprayDrop). */
    public static void sprayDrop(double x, double y, double z, double vx, double vy, double vz, float size, float drag, double floor, int life, int light) {
        SPLASHES.sprayDrop(x, y, z, vx, vy, vz, size, drag, floor, life, light);
    }

    /** A puff of a tyre's mist (RainSplashes#sprayMist). */
    public static void sprayMist(double x, double y, double z, double vx, double vy, double vz, float size, float alpha, float roll, double floor, int life, int light) {
        SPLASHES.sprayMist(x, y, z, vx, vy, vz, size, alpha, roll, floor, life, light);
    }

    /** The column map (also uploaded for a shader pack, RainShaderPack). */
    /** Whether it may snow near (x, z) (RainColumns#snowNear; RainVanillaMixin skips vanilla's precipitation otherwise). */
    public static boolean snowNear(int x, int z, int r) {
        return COLUMNS.snowNear(x, z, r);
    }

    static RainColumns columns() {
        return COLUMNS;
    }

    static void programChanged() {
        locations = null;
    }

    /** A block changed on the client (mixin client.DynamicLightBlockChangeMixin): its rain column again. */
    public static void blockChanged(ClientLevel level, BlockPos pos) {
        if (level == tracked && RenderSystem.isOnRenderThread()) COLUMNS.changed(pos);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != tracked) {
            SPLASHES.clear();
            tracked = level;
        }
        if (level == null || mc.isPaused()) return;
        // the column map also for a shader pack that draws the rain from it (RainShaderPack); the splashes only for AFL's own
        boolean own = active(), pack = !own && RainShaderPack.active();
        if (!own || !splashes) SPLASHES.clear();
        if (!own && !pack) return;
        float rain = level.getRainLevel(1.0F);
        if (rain <= 0 && SPLASHES.isEmpty()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        try {
            if (rain > 0) COLUMNS.update(level, cam.x, cam.z);
            long t0 = System.nanoTime();
            if (own && splashes) SPLASHES.tick(level, COLUMNS, cam, rain);
            tickMicros = (System.nanoTime() - t0) / 1000;
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !active()) return;
        long t0 = System.nanoTime();
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        try {
            float rain = level.getRainLevel(pt);
            if (rain > 0) {
                GPU_DROPS.begin();
                drawDrops(mc, level, cam, rain, pt);
                GPU_DROPS.end();
            }
            if (!SPLASHES.isEmpty()) {
                long s0 = System.nanoTime();
                GPU_SPLASHES.begin();
                SPLASHES.render(level, event.getCamera(), pt, event.getPoseStack().last().normal());
                GPU_SPLASHES.end();
                splashMicros = (System.nanoTime() - s0) / 1000;
            }
        } catch (RuntimeException e) {
            fail(e);
        }
        lastMicros = (System.nanoTime() - t0) / 1000;
    }

    /** Never take the game down: vanilla rain back for the session, logged once. */
    private static void fail(RuntimeException e) {
        failed = true;
        SPLASHES.clear();
        LOGGER.error("AFL rain: switched off after an error, vanilla rain is back", e);
    }

    private static void drawDrops(Minecraft mc, ClientLevel level, Vec3 cam, float rain, float pt) {
        ShaderInstance sh = RainPrograms.rain();
        if (drops == null) build();
        COLUMNS.upload();
        LightTexture light = mc.gameRenderer.lightTexture();
        light.turnOnLightLayer();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        sh.setSampler("Sampler0", COLUMNS.texture());
        sh.setSampler("Sampler2", RenderSystem.getShaderTexture(2));
        if (sh.MODEL_VIEW_MATRIX != null) sh.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        if (sh.PROJECTION_MATRIX != null) sh.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        if (sh.FOG_START != null) sh.FOG_START.set(RenderSystem.getShaderFogStart());
        if (sh.FOG_END != null) sh.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (sh.FOG_SHAPE != null) sh.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());

        float thunder = level.getThunderLevel(pt), density = rain * (0.65F + 0.35F * thunder);
        double seconds = ((level.getGameTime() % 24000) + pt) / 20.0;
        double wx, wz;
        if (ClientWeather.active()) {
            // the wind where the camera is (regional weather V1), a little slower near the ground, gusting
            double g = 1 + 0.25 * ClientWeather.gust() * (Math.sin(seconds / 7.7) + 0.5 * Math.sin(seconds / 3.1));
            wx = ClientWeather.windX() * 0.7 * g;
            wz = ClientWeather.windZ() * 0.7 * g;
        } else {
            double windAngle = 0.9 + 0.6 * Math.sin(seconds / 97.0);
            double windSpeed = (0.8 + 0.5 * Math.sin(seconds / 31.0) + 0.3 * Math.sin(seconds / 7.7)) * (1 + 1.6 * thunder);
            wx = Math.cos(windAngle) * windSpeed;
            wz = Math.sin(windAngle) * windSpeed;
        }
        double dt = seconds - driftSeconds;
        if (dt > 0 && dt < 1) {
            driftX = floorMod(driftX + wx * dt, DRIFT_WRAP);
            driftZ = floorMod(driftZ + wz * dt, DRIFT_WRAP);
        }
        driftSeconds = seconds;
        float pixel = 2.0F / (RenderSystem.getProjectionMatrix().m11() * Math.max(1, mc.getWindow().getHeight()));
        Trucks trucks = trucks(level, cam, pt);

        drops.bind();
        // near: small and dense, the drops right around the camera; far: large and sparse
        layer(sh, cam, 12, 12, 12, density * 0.2F, 0.55F, seconds, wx, wz, pixel, trucks);
        layer(sh, cam, 56, 36, 56, density, 0.45F, seconds, wx, wz, pixel, trucks);
        VertexBuffer.unbind();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        light.turnOffLightLayer();
    }

    private static void layer(ShaderInstance sh, Vec3 cam, double bx, double by, double bz, float count, float alpha, double seconds,
                              double wx, double wz, float pixel, Trucks trucks) {
        sh.apply();
        Locations l = locations;
        if (l == null || l.program != sh.getId()) locations = l = new Locations(sh.getId());
        GL20.glUniform3f(l.box, (float) bx, (float) by, (float) bz);
        // the camera modulo the box, worked out in double (world coordinates are too big for the GPU's floats)
        GL20.glUniform3f(l.offset, (float) floorMod(cam.x, bx), (float) floorMod(cam.y, by), (float) floorMod(cam.z, bz));
        GL20.glUniform3f(l.wind, (float) wx, 0, (float) wz);
        GL20.glUniform3f(l.drift, (float) driftX, 0, (float) driftZ);
        GL20.glUniform1f(l.time, (float) seconds);
        GL20.glUniform1f(l.count, count);
        GL20.glUniform1f(l.alpha, alpha);
        GL20.glUniform1f(l.pixel, pixel);
        GL20.glUniform1f(l.exposure, EXPOSURE);
        GL20.glUniform1f(l.camY, (float) cam.y);
        GL20.glUniform2f(l.colWrap, (float) floorMod(cam.x, RainColumns.SIZE), (float) floorMod(cam.z, RainColumns.SIZE));
        GL20.glUniform1i(l.truckCount, trucks.count);
        if (trucks.count > 0) {
            if (l.boxCount >= 0) GL20.glUniform1iv(l.boxCount, trucks.boxes);
            if (l.truckO >= 0) GL20.glUniform3fv(l.truckO, trucks.o);
            if (l.truckF >= 0) GL20.glUniform3fv(l.truckF, trucks.f);
            if (l.truckR >= 0) GL20.glUniform3fv(l.truckR, trucks.r);
            if (l.truckU >= 0) GL20.glUniform3fv(l.truckU, trucks.u);
            if (l.lo >= 0) GL20.glUniform3fv(l.lo, trucks.lo);
            if (l.hi >= 0) GL20.glUniform3fv(l.hi, trucks.hi);
            if (l.cut >= 0) GL20.glUniform4fv(l.cut, trucks.cut);
        }
        drops.draw();
        sh.clear();
    }

    private static double floorMod(double v, double m) {
        double r = v % m;
        return r < 0 ? r + m : r;
    }

    /** The static drop buffer: per quad a seed (Position, 0-1 each) and its corners (UV0). */
    private static void build() {
        BufferBuilder b = new BufferBuilder(DROPS * 4 * DefaultVertexFormat.POSITION_TEX.getVertexSize());
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Random r = new Random(0xA1F7_2026L);
        for (int i = 0; i < DROPS; i++) {
            float x = r.nextFloat(), y = r.nextFloat(), z = r.nextFloat();
            b.vertex(x, y, z).uv(0, 0).endVertex();
            b.vertex(x, y, z).uv(1, 0).endVertex();
            b.vertex(x, y, z).uv(1, 1).endVertex();
            b.vertex(x, y, z).uv(0, 1).endVertex();
        }
        drops = new VertexBuffer(VertexBuffer.Usage.STATIC);
        drops.bind();
        drops.upload(b.end());
        VertexBuffer.unbind();
    }

    /** The nearest trucks' frames (camera-relative) and shell parts (RainTrucks), for the drop program. */
    private static final class Trucks {
        int count;
        final int[] boxes = new int[TRUCKS];
        final float[] o = new float[TRUCKS * 3], f = new float[TRUCKS * 3], r = new float[TRUCKS * 3], u = new float[TRUCKS * 3],
                lo = new float[TRUCKS * TRUCK_BOXES * 3], hi = new float[TRUCKS * TRUCK_BOXES * 3], cut = new float[TRUCKS * TRUCK_BOXES * 4];
    }

    private static final Trucks TRUCK_DATA = new Trucks();

    private static Trucks trucks(ClientLevel level, Vec3 cam, float pt) {
        Trucks t = TRUCK_DATA;
        t.count = 0;
        for (RainTrucks.Frame frame : RainTrucks.near(level, cam, TRUCK_RANGE, TRUCKS, pt)) {
            int i = t.count++;
            put(t.o, i, frame.o.subtract(cam));
            put(t.f, i, frame.f);
            put(t.r, i, frame.r);
            put(t.u, i, frame.u);
            int n = 0;
            for (PickupTruckLayout.RainPart part : frame.parts) if (n < TRUCK_BOXES) part(t, i, n++, part);
            t.boxes[i] = n;
        }
        return t;
    }

    private static void put(float[] a, int i, Vec3 v) {
        a[i * 3] = (float) v.x;
        a[i * 3 + 1] = (float) v.y;
        a[i * 3 + 2] = (float) v.z;
    }

    private static void part(Trucks t, int truck, int n, PickupTruckLayout.RainPart part) {
        PickupTruckLayout.Box b = part.box();
        int k = (truck * TRUCK_BOXES + n) * 3, c = (truck * TRUCK_BOXES + n) * 4;
        t.lo[k] = (float) b.f0();
        t.lo[k + 1] = (float) b.r0();
        t.lo[k + 2] = (float) b.y0();
        t.hi[k] = (float) b.f1();
        t.hi[k + 1] = (float) b.r1();
        t.hi[k + 2] = (float) b.y1();
        t.cut[c] = (float) part.nf();
        t.cut[c + 1] = 0;
        t.cut[c + 2] = (float) part.ny();
        t.cut[c + 3] = (float) part.d();
    }

    /** The drop program's own uniforms (beside the ones its JSON declares). */
    private static final class Locations {
        final int program, box, offset, wind, drift, time, count, alpha, pixel, exposure, camY, colWrap, truckCount, boxCount,
                truckO, truckF, truckR, truckU, lo, hi, cut;

        Locations(int p) {
            program = p;
            box = GL20.glGetUniformLocation(p, "RainBox");
            offset = GL20.glGetUniformLocation(p, "RainOffset");
            wind = GL20.glGetUniformLocation(p, "RainWind");
            drift = GL20.glGetUniformLocation(p, "RainDrift");
            time = GL20.glGetUniformLocation(p, "RainTime");
            count = GL20.glGetUniformLocation(p, "RainCount");
            alpha = GL20.glGetUniformLocation(p, "RainAlpha");
            pixel = GL20.glGetUniformLocation(p, "PixelScale");
            exposure = GL20.glGetUniformLocation(p, "Exposure");
            camY = GL20.glGetUniformLocation(p, "CamY");
            colWrap = GL20.glGetUniformLocation(p, "ColWrap");
            truckCount = GL20.glGetUniformLocation(p, "TruckCount");
            boxCount = GL20.glGetUniformLocation(p, "TruckBoxCount");
            truckO = GL20.glGetUniformLocation(p, "TruckO");
            truckF = GL20.glGetUniformLocation(p, "TruckF");
            truckR = GL20.glGetUniformLocation(p, "TruckR");
            truckU = GL20.glGetUniformLocation(p, "TruckU");
            lo = GL20.glGetUniformLocation(p, "TruckLo");
            hi = GL20.glGetUniformLocation(p, "TruckHi");
            cut = GL20.glGetUniformLocation(p, "TruckCut");
        }
    }
}
