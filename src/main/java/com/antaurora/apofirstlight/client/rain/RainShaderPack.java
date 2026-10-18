package com.antaurora.apofirstlight.client.rain;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ShaderPackOption;
import com.antaurora.apofirstlight.client.blockmesh.AflStaticMeshModel;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * The rain for a shader pack that draws it itself (docs/rendering/rain_v1.md section 6; Aureveil from alpha.49): the pack
 * switches it on with its boolean option {@code AFL_RAIN}; then AFL leaves out vanilla's rain sheets and rain splash
 * particles (mixin client.RainVanillaMixin; snow, lava smoke and the rain sounds stay) and keeps these textures up every
 * frame before the terrain (AFTER_SKY), all positions relative to Camera#getPosition() at the frame's partial tick:
 * <ul>
 * <li>{@code apocalypse_firstlight:dynamic/afl_rain_columns}: the rain's column map, 128 x 129 RGBA8
 * ({@link RainColumns#uploadPack}).</li>
 * <li>{@code apocalypse_firstlight:dynamic/afl_rain_trucks}: RGBA32F 85 x 3. Row 0 the header: texel 0 (trucks 0-2, layout
 * 1, 0, 4243: the pack's check), texel 1 the T15 glass sprite's UV range in the block atlas (u0, v0, u1, v1). Rows 1-2 the
 * nearest trucks within 40 m, nearest first: texel 0 the origin (w 1), 1-3 forward, right, up (unit); 4-51 the 16 rain
 * shell boxes, 3 texels each (min.f, min.r, min.u, nf), (max.f, max.r, max.u, nu), (d, 1 when used, 0, 0), in the
 * truck's (forward, right, up), what stays of a box being nf * f + nu * u <= d (no cut: nf = nu = 0, d = 1e9; as
 * afl_rain.vsh afl_truckClip, which also lowers every box's floor by 0.05 m); 52-75 the four panes of glass
 * ({@link TruckGlass#PANES}: windshield, rear, left, right), 6 texels each: origin (u = s = 0, w 1), U, S, Nout (unit,
 * world; a door window turned with its door), the glass range (u0, s0, u1, s1, m) and its rectangle in
 * dynamic/afl_glass_water (x0, y0, x1, y1, 0..1: (u0, s0) at (x0, y0), (u1, s1) at (x1, y1); zeros where that pane is dry
 * or its truck keeps no water, beyond 24 m); 76 the truck's velocity (m/s, world) and the road's wetness 0..1; 77-84 the
 * four tyres' water (FL, FR, RL, RR; 2026-10-18, layout 1), 2 texels each: the contact point and the spray 0..1, the
 * direction the water goes (world unit) and the tread speed (m/s), all 0 for a tyre throwing none
 * (client/vehicle/TruckSpray#packWheel).</li>
 * <li>{@code apocalypse_firstlight:dynamic/afl_glass_water}: the glass water of the two trucks' panes, RGBA8
 * {@link GlassAtlas#W} x {@link GlassAtlas#H} with mip levels, baked by {@link WindshieldRenderer#packFrame}.</li>
 * </ul>
 * With the option off, another pack or none: nothing is stopped and nothing is updated (the pack does not read them).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RainShaderPack {
    static final String OPTION = "AFL_RAIN";
    static final ResourceLocation COLUMNS_ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/afl_rain_columns"),
            TRUCKS_ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/afl_rain_trucks"),
            GLASS_ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/afl_glass_water");
    static final int TRUCK_W = 85, TRUCK_H = 3, TRUCKS = 2, BOXES = 16, PANE_AT = 52, PANE_TEXELS = 6, WHEEL_AT = 76;
    /** The header's texel 0 .y: the trucks texture's layout (1: the wheel spray texels 76-84 are there; 2026-10-18). */
    private static final float LAYOUT = 1;
    private static final float[] WHEEL = new float[8];
    private static final double RANGE = 40;
    private static final float CHECK = 4243.0F;
    private static final ResourceLocation GLASS = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/pickup_t15_glass.png");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DataTexture COLUMNS_TEX = new DataTexture(), TRUCKS_TEX = new DataTexture(), GLASS_TEX = new DataTexture();
    private static final FloatBuffer DATA = MemoryUtil.memAllocFloat(TRUCK_W * TRUCK_H * 4);
    private static int frame;
    private static boolean failed;

    private RainShaderPack() {
    }

    /** A texture whose contents AFL writes itself (nothing to load from resources). */
    private static final class DataTexture extends AbstractTexture {
        int allocated = -1;

        @Override
        public void load(ResourceManager manager) {
        }
    }

    /** The shader pack in use draws the rain from AFL's textures (its option AFL_RAIN is on). */
    public static boolean active() {
        return !failed && AflShaderCompat.shaderPackInUse() && ShaderPackOption.on(OPTION, "the pack draws the rain; off: vanilla rain");
    }

    /** Client setup (render thread): the textures registered before the shader pack first binds them. */
    public static void register() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        register(textures, COLUMNS_ID, COLUMNS_TEX);
        register(textures, TRUCKS_ID, TRUCKS_TEX);
        register(textures, GLASS_ID, GLASS_TEX);
    }

    /**
     * Registered, and its GL name bound once right away: a name only generated is no texture yet for direct state access
     * (Oculus binds with DSA; it logged GL_INVALID_OPERATION on the first frame, Aureveil A 2026-10-17).
     */
    private static void register(TextureManager textures, ResourceLocation id, DataTexture texture) {
        if (textures.getTexture(id, null) == texture) return;
        textures.register(id, texture);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(texture.getId());
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(0);
    }

    /**
     * Dev check (RainPerfProbe; reads the GPU back, a wait: never per frame): the glass map's mean cover (B, 0-255) and
     * normal (R, G) over a 16 x 16 block in the middle of slot 0's windshield rectangle, or null without DSA / a map.
     */
    public static int[] debugGlassSample() {
        int id = GLASS_TEX.getId();
        if (!org.lwjgl.opengl.GL.getCapabilities().OpenGL45 || WindshieldRenderer.BAKED.length == 0) return null;
        int[] r = GlassAtlas.TEXELS[0][0];
        int n = 16, x = r[0] + r[2] / 2 - n / 2, y = r[1] + r[3] / 2 - n / 2;
        java.nio.ByteBuffer b = MemoryUtil.memAlloc(n * n * 4);
        try {
            org.lwjgl.opengl.GL45C.glGetTextureSubImage(id, 0, x, y, 0, n, n, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, b);
            long[] sum = new long[3];
            for (int i = 0; i < n * n; i++)
                for (int k = 0; k < 3; k++) sum[k] += b.get(i * 4 + k) & 0xFF;
            return new int[]{(int) (sum[0] / (n * n)), (int) (sum[1] / (n * n)), (int) (sum[2] / (n * n)), WindshieldRenderer.BAKED[0][0] ? 1 : 0};
        } finally {
            MemoryUtil.memFree(b);
        }
    }

    /** The trucks (nearest first) the glass map was baked for this frame: the trucks texture's rows. */
    private static List<RainTrucks.Frame> slots = List.of();

    /**
     * The start of the frame, before the world pass: the glass water stepped and baked into the glass map
     * (WindshieldRenderer.packFrame). In the world pass Oculus throws away what a non-Iris ShaderInstance draws, so the
     * bake drew nothing there (Aureveil A, 2026-10-17). The camera is last frame's (only the nearest trucks are chosen
     * by it); the trucks' places are this frame's (same partial tick as the world pass).
     */
    @SubscribeEvent
    public static void renderTick(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !active()) {
            slots = List.of();
            return;
        }
        try {
            register();
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            float pt = event.renderTickTime;
            slots = RainTrucks.near(level, cam, RANGE, TRUCKS, pt);
            WindshieldRenderer.packFrame(mc, level, cam, pt, slots, GLASS_TEX.getId());
        } catch (RuntimeException e) {
            failed = true;
            slots = List.of();
            LOGGER.error("AFL rain for the shader pack: switched off after an error (vanilla rain is back)", e);
        }
    }

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || AflShaderCompat.activeShadowPass() || !active()) return;
        try {
            register();
            Vec3 cam = event.getCamera().getPosition();
            frame++;
            RainRenderer.columns().uploadPack(COLUMNS_TEX.getId(), Mth.floor(cam.x), Mth.floor(cam.z), frame);
            // the trucks baked at the start of the frame (renderTick), placed for this camera
            uploadTrucks(cam, event.getPartialTick(), slots, WindshieldRenderer.BAKED);
        } catch (RuntimeException e) {
            failed = true;
            LOGGER.error("AFL rain for the shader pack: switched off after an error (vanilla rain is back)", e);
        }
    }

    private static final Quaternionf Q = new Quaternionf(), QP = new Quaternionf();
    private static final Vector3f V = new Vector3f();
    private static final float[] P = new float[3];

    private static void uploadTrucks(Vec3 cam, float pt, List<RainTrucks.Frame> near, boolean[][] baked) {
        FloatBuffer d = DATA;
        MemoryUtil.memSet(MemoryUtil.memAddress(d), 0, (long) d.capacity() * Float.BYTES);
        d.put(0, near.size()).put(1, LAYOUT).put(3, CHECK);
        TextureAtlasSprite glass = AflStaticMeshModel.atlasSprite(GLASS);
        if (glass != null) d.put(4, glass.getU0()).put(5, glass.getV0()).put(6, glass.getU1()).put(7, glass.getV1());
        for (int t = 0; t < near.size(); t++) {
            RainTrucks.Frame f = near.get(t);
            int row = (1 + t) * TRUCK_W;
            texel(d, row, (float) (f.o.x - cam.x), (float) (f.o.y - cam.y), (float) (f.o.z - cam.z), 1);
            texel(d, row + 1, (float) f.f.x, (float) f.f.y, (float) f.f.z, 0);
            texel(d, row + 2, (float) f.r.x, (float) f.r.y, (float) f.r.z, 0);
            texel(d, row + 3, (float) f.u.x, (float) f.u.y, (float) f.u.z, 0);
            int n = 0;
            for (PickupTruckLayout.RainPart part : f.parts) {
                if (n >= BOXES) break;
                PickupTruckLayout.Box b = part.box();
                boolean cut = part.nf() != 0 || part.ny() != 0;
                int at = row + 4 + n * 3;
                texel(d, at, (float) b.f0(), (float) b.r0(), (float) b.y0(), (float) part.nf());
                texel(d, at + 1, (float) b.f1(), (float) b.r1(), (float) b.y1(), (float) part.ny());
                texel(d, at + 2, cut ? (float) part.d() : 1e9F, 1, 0, 0);
                n++;
            }
            panes(d, row, f.truck, cam, pt, t, baked[t]);
            // the tyres' water (client/vehicle/TruckSpray): the truck's motion and the road's wetness, then per wheel
            PickupTruckEntity tr = f.truck;
            texel(d, row + WHEEL_AT, (float) ((tr.getX() - tr.xo) * 20), (float) ((tr.getY() - tr.yo) * 20), (float) ((tr.getZ() - tr.zo) * 20),
                    com.antaurora.apofirstlight.client.vehicle.TruckSpray.wet(tr));
            for (int w = 0; w < 4; w++) {
                com.antaurora.apofirstlight.client.vehicle.TruckSpray.packWheel(tr, w, pt, cam, WHEEL);
                texel(d, row + WHEEL_AT + 1 + w * 2, WHEEL[0], WHEEL[1], WHEEL[2], WHEEL[3]);
                texel(d, row + WHEEL_AT + 2 + w * 2, WHEEL[4], WHEEL[5], WHEEL[6], WHEEL[7]);
            }
        }
        int id = TRUCKS_TEX.getId();
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        d.position(0).limit(d.capacity());
        // row 1 + t truck t (texels 0-3 frame, 4-51 boxes, 52-75 panes, 76-84 tyre water): a bad value logged with its texel
        com.antaurora.apofirstlight.client.GlTex.checkFinite("afl_rain_trucks", d, TRUCK_W);
        if (TRUCKS_TEX.allocated != id) {
            // once: its storage made on a binding (the only glGet here); every frame after that without one (GlTex)
            TRUCKS_TEX.allocated = id;
            int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, TRUCK_W, TRUCK_H, 0, GL11.GL_RGBA, GL11.GL_FLOAT, d);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
        } else {
            com.antaurora.apofirstlight.client.GlTex.subImage(id, 0, 0, TRUCK_W, TRUCK_H, GL11.GL_RGBA, GL11.GL_FLOAT, d);
        }
    }

    /** A truck's four panes into its row (texels 52-75): as WindshieldRenderer places them, a door window turned with its door. */
    private static void panes(FloatBuffer d, int row, PickupTruckEntity truck, Vec3 cam, float pt, int slot, boolean[] baked) {
        truck.orientation(pt, Q);
        Vec3 pos = truck.getPosition(pt);
        float ox = (float) (pos.x - cam.x), oy = (float) (pos.y - cam.y), oz = (float) (pos.z - cam.z);
        for (int i = 0; i < TruckGlass.PANES.length; i++) {
            TruckGlass.Pane pane = TruckGlass.PANES[i];
            float px = 0, py = 0, pz = 0, deg = 0;
            if (pane.hinge != TruckGlass.Hinge.NONE) {
                boolean left = pane.hinge == TruckGlass.Hinge.DOOR_L;
                px = left ? -TruckGlass.HINGE_X : TruckGlass.HINGE_X;
                py = TruckGlass.HINGE_Y;
                pz = TruckGlass.HINGE_Z;
                deg = left ? -truck.doorAngle(false, pt) : truck.doorAngle(true, pt);
            }
            QP.set(Q);
            if (deg != 0) QP.rotateY((float) Math.toRadians(deg));
            int at = row + PANE_AT + i * PANE_TEXELS;
            // origin: the truck's turn to the hinge, the door's turn from it
            pane.model(0, 0, 0, P, 0);
            Q.transform(V.set(px, py, pz));
            float bx = ox + V.x, by = oy + V.y, bz = oz + V.z;
            QP.transform(V.set(P[0] - px, P[1] - py, P[2] - pz));
            texel(d, at, bx + V.x, by + V.y, bz + V.z, 1);
            axis(d, at + 1, pane.axisU);
            axis(d, at + 2, pane.axisS);
            axis(d, at + 3, pane.axisN);
            texel(d, at + 4, (float) GlassAtlas.u0(pane), (float) GlassAtlas.s0(pane), (float) GlassAtlas.u1(pane), (float) GlassAtlas.s1(pane));
            if (baked[i]) {
                float[] r = GlassAtlas.RECT[slot][i];
                texel(d, at + 5, r[0], r[1], r[2], r[3]);
            }
        }
    }

    private static void axis(FloatBuffer d, int at, double[] a) {
        QP.transform(V.set((float) a[0], (float) a[1], (float) a[2]));
        texel(d, at, V.x, V.y, V.z, 0);
    }

    private static void texel(FloatBuffer d, int at, float x, float y, float z, float w) {
        d.put(at * 4, x).put(at * 4 + 1, y).put(at * 4 + 2, z).put(at * 4 + 3, w);
    }
}
