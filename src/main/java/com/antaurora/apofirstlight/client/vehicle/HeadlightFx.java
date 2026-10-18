package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.LiquidRenderTypes;
import com.antaurora.apofirstlight.client.light.DynamicLights;
import com.antaurora.apofirstlight.vehicle.HeadlightBeam;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.physics.LevelVehicleWorld;
import com.antaurora.apofirstlight.vehicle.physics.VehicleWorld;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The T15's lamps as light in the air and on the ground behind (docs/vehicles/first_pickup_v0_design.md 8.10), added
 * (LiquidRenderTypes.HEADLIGHT_GLOW: emissive, no depth writes, tested against the world so a wall or the truck's own body
 * cuts them), skipped in a shader pack's shadow pass: the beam cones in the fire effects' world pass stage
 * (AFTER_BLOCK_ENTITIES; shown by Sundial as FireFx is), the tail lamps' ground glow after the translucent blocks (with
 * Oculus that is after the deferred lighting, so it adds to the lit picture; earlier, a pack took it for the road's own
 * colour and lit it again by night: Aureveil A 2026-10-17, the pools came out dim). The lit lens itself is the body mesh at full light (PickupTruckRenderer lamps); there are no
 * flare sprites over the lamps (user 2026-10-17: the round flare looked out of place, the lit lens alone reads right).
 * <p>From the headlamps a faint beam cone runs {@link #LOW_LENGTH} / {@link #HIGH_LENGTH} m along each lit headlamp's aim
 * (HeadlightBeam), fading along its length and toward its silhouette, seen only in the dark and more in rain: the light it
 * puts on the world is HeadlightField's block light (white, so the red tail and brake lamps cannot be block light).
 * <p>The tail and brake lamps' red light on the ground behind (user 2026-10-17: the tail lamps lit nothing round them):
 * per lamp, a grid laid on the ground (LevelVehicleWorld#ground: road layer, smoothed terrain, blocks) over its pool's
 * ellipse, the glow texture stretched over it, its soft round pool in the texture's colour (alpha full): the strength is
 * the vertex alpha, {@link #TAIL_GROUND} with the tail lamps, {@link #BRAKE_GROUND} braking, times the dark, kept at
 * least {@link #ALPHA_MIN} (fainter: a darker red). Oculus's program for this render type (gbuffers_spidereyes) drops
 * fragments with alpha under 0.1: with the falloff in the alpha (vertex or texture) that cut the pools to hard polygons
 * (user 2026-10-17: dim, square, not smooth; Aureveil A found the cause). Without a shader pack it looks the same either
 * way (additive: colour x texture x alpha).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HeadlightFx {
    private static final double RANGE = 160;
    /** The glow texture's middle (textures/effect/headlight_flare: white there): the cones. */
    private static final float MID_U = 0.5F, MID_V = 0.5F;
    /** The least alpha drawn (a shader pack's alpha test cuts under 0.1); a fainter glow darkens its colour instead. */
    private static final float ALPHA_MIN = 0.11F;
    /** Halogen white, tail red (0-255). */
    private static final int[] WHITE = {255, 238, 210}, RED = {255, 36, 22};
    /** Beam cones: length and end radius (m); the lens radius; sides and rings; the strength in the dark (alpha 0-1). */
    private static final double LOW_LENGTH = 9, LOW_RADIUS = 2.0, HIGH_LENGTH = 14, HIGH_RADIUS = 1.6, LENS = 0.09;
    private static final int SIDES = 16, RINGS = 6;
    private static final float CONE_ALPHA = 0.07F, RAIN_BOOST = 1.6F;
    /** The ground glow's strength in the dark (alpha 0-1) with the tail lamps and braking. */
    private static final float TAIL_GROUND = 0.30F, BRAKE_GROUND = 0.75F;
    /**
     * The ground glow: the tail lamps (forward, right from the truck's origin); each lamp's pool's middle behind it and its
     * half sizes across and along (m); the vertices per side of each pool's grid; the lift off the ground (m).
     */
    private static final double TAIL_F = -2.30, TAIL_R = 0.77, POOL_BACK = 0.9, POOL_ACROSS = 1.25, POOL_ALONG = 2.1, GROUND_LIFT = 0.03;
    private static final int POOL_GRID = 9;

    /** A headlamp's lens (frame point: forward, right, up). */
    private record Lamp(double f, double r, double y) {}

    private static final Lamp[] LOW = {new Lamp(2.30, -0.746, 0.81), new Lamp(2.30, 0.746, 0.81)},
            HIGH = {new Lamp(2.30, -0.571, 0.81), new Lamp(2.30, 0.571, 0.81)};

    private static final VehicleWorld.Ground GROUND = new VehicleWorld.Ground();

    private HeadlightFx() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        boolean cones = event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
        boolean tails = event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        if (!cones && !tails || AflShaderCompat.activeShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Camera camera = event.getCamera();
        if (level == null) return;
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = null;
        LevelVehicleWorld world = null;
        PoseStack pose = event.getPoseStack();
        boolean perPixel = DynamicLights.perPixel();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof PickupTruckEntity truck) || truck.distanceToSqr(cam) > RANGE * RANGE) continue;
            boolean on = truck.headlights(), brake = truck.braking();
            if (!on && !brake) continue;
            float dark = dark(level, truck, pt);
            float cone = cones ? CONE_ALPHA * dark * dark * (1 + RAIN_BOOST * level.getRainLevel(pt)) : 0;
            // lamps drawn per pixel (no shader pack, or one with AFL_VEHICLE_LIGHTS): the tail lamps light the ground, no decal
            float ground = tails && !perPixel ? (brake ? BRAKE_GROUND : TAIL_GROUND) * dark : 0;
            if (cone <= 0.004F && ground <= 0.004F) continue;
            if (out == null) out = buffers.getBuffer(LiquidRenderTypes.HEADLIGHT_GLOW);
            Matrix4f matrix = pose.last().pose();
            Matrix3f normals = pose.last().normal();
            if (on && cone > 0.004F) {
                for (Lamp l : LOW) cone(out, matrix, normals, cam, truck, pt, l, HeadlightBeam.LOW, LOW_LENGTH, LOW_RADIUS, cone);
                if (truck.highBeam()) for (Lamp l : HIGH) cone(out, matrix, normals, cam, truck, pt, l, HeadlightBeam.HIGH, HIGH_LENGTH, HIGH_RADIUS, cone);
            }
            if (ground > 0.004F) {
                if (world == null) world = new LevelVehicleWorld(level, null);
                tailGlow(out, matrix, normals, cam, truck, pt, world, ground);
            }
        }
        if (out != null) buffers.endBatch(LiquidRenderTypes.HEADLIGHT_GLOW);
    }

    /** 0 in full daylight under the open sky, 1 at night or under cover. */
    private static float dark(ClientLevel level, PickupTruckEntity truck, float pt) {
        float day = Mth.clamp((level.getSkyDarken(pt) - 0.2F) / 0.8F, 0, 1);
        float sky = level.getBrightness(LightLayer.SKY, BlockPos.containing(truck.getX(), truck.getY() + 1.5, truck.getZ())) / 15.0F;
        return 1 - day * sky;
    }

    /**
     * The red glow on the ground behind the tail lamps: per lamp a grid over its pool's ellipse (from under the bumper
     * back), each vertex on the ground under it, the glow texture across the ellipse (its soft round pool is the shape);
     * a vertex with no ground within a metre up or two down is unlit (alpha 0).
     */
    private static void tailGlow(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3 cam, PickupTruckEntity truck, float pt,
                                 LevelVehicleWorld world, float strength) {
        int n = POOL_GRID;
        double[][] px = new double[n][n], py = new double[n][n], pz = new double[n][n];
        float[][] u = new float[n][n], v = new float[n][n];
        int[][] a = new int[n][n];
        double base = truck.getY();
        int alpha = Mth.clamp(Math.round(255 * Math.max(strength, ALPHA_MIN)), 0, 255);
        float shade = Math.min(1, strength / ALPHA_MIN);
        int[] red = shade >= 1 ? RED : new int[]{Math.round(RED[0] * shade), Math.round(RED[1] * shade), Math.round(RED[2] * shade)};
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < n; i++) {
                double b = -1 + 2.0 * i / (n - 1), f = TAIL_F - POOL_BACK - b * POOL_ALONG;
                for (int j = 0; j < n; j++) {
                    double sa = -1 + 2.0 * j / (n - 1), r = side * TAIL_R + sa * POOL_ACROSS;
                    Vec3 at = truck.framePoint(f, r, 0, pt);
                    boolean ground = world.ground(at.x, at.z, at.y, base + 1.0, base - 2.0, GROUND) && GROUND.found;
                    px[i][j] = at.x - cam.x;
                    py[i][j] = (ground ? GROUND.height : at.y) + GROUND_LIFT - cam.y;
                    pz[i][j] = at.z - cam.z;
                    u[i][j] = (float) (j / (n - 1.0));
                    v[i][j] = (float) (i / (n - 1.0));
                    a[i][j] = ground ? alpha : 0;
                }
            }
            for (int i = 0; i < n - 1; i++)
                for (int j = 0; j < n - 1; j++) {
                    if (a[i][j] + a[i][j + 1] + a[i + 1][j] + a[i + 1][j + 1] == 0) continue;
                    vertex(out, matrix, normals, px[i][j], py[i][j], pz[i][j], red, a[i][j], u[i][j], v[i][j]);
                    vertex(out, matrix, normals, px[i][j + 1], py[i][j + 1], pz[i][j + 1], red, a[i][j + 1], u[i][j + 1], v[i][j + 1]);
                    vertex(out, matrix, normals, px[i + 1][j + 1], py[i + 1][j + 1], pz[i + 1][j + 1], red, a[i + 1][j + 1], u[i + 1][j + 1], v[i + 1][j + 1]);
                    vertex(out, matrix, normals, px[i + 1][j], py[i + 1][j], pz[i + 1][j], red, a[i + 1][j], u[i + 1][j], v[i + 1][j]);
                }
        }
    }

    /**
     * One beam cone from a lamp along the pattern's aim (down by its aimDown, a little out to its own side): rings from
     * the lens to the length; a vertex's alpha falls with the square of the share of the length and with how edge-on the
     * cone's side is to the camera (a lit haze is thickest looking through it).
     */
    private static void cone(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3 cam, PickupTruckEntity truck, float pt, Lamp lamp,
                             HeadlightBeam.Pattern p, double length, double radius, float strength) {
        Vec3 o = truck.framePoint(lamp.f() + 0.04, lamp.r(), lamp.y(), pt);
        double down = Math.toRadians(p.aimDown() + 1.5), splay = Math.signum(lamp.r()) * Math.toRadians(2.0);
        Vec3 axis = truck.frameDirection(Math.cos(down) * Math.cos(splay), Math.cos(down) * Math.sin(splay), -Math.sin(down), pt).normalize();
        Vec3 side = axis.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(axis).normalize();
        double slope = (radius - LENS) / length;
        // ring vertices: position relative to the camera and alpha
        double[][] px = new double[RINGS + 1][SIDES + 1], py = new double[RINGS + 1][SIDES + 1], pz = new double[RINGS + 1][SIDES + 1];
        int[][] a = new int[RINGS + 1][SIDES + 1];
        for (int i = 0; i <= RINGS; i++) {
            double t = (double) i / RINGS, along = length * t * t * 0.7 + length * t * 0.3, rad = LENS + slope * along;
            double fade = Math.pow(1 - along / length, 2) * Math.min(1, along / 0.6);
            for (int j = 0; j <= SIDES; j++) {
                double ang = 2 * Math.PI * j / SIDES, cs = Math.cos(ang), sn = Math.sin(ang);
                double nx = side.x * cs + up.x * sn, ny = side.y * cs + up.y * sn, nz = side.z * cs + up.z * sn;
                double x = o.x + axis.x * along + nx * rad, y = o.y + axis.y * along + ny * rad, z = o.z + axis.z * along + nz * rad;
                // the side's normal leans back along the axis by the cone's slope
                double sx = nx - axis.x * slope, sy = ny - axis.y * slope, sz = nz - axis.z * slope, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
                double vx = cam.x - x, vy = cam.y - y, vz = cam.z - z, vl = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6;
                double face = Math.abs((sx * vx + sy * vy + sz * vz) / (sl * vl));
                px[i][j] = x - cam.x;
                py[i][j] = y - cam.y;
                pz[i][j] = z - cam.z;
                a[i][j] = Mth.clamp((int) Math.round(255 * strength * fade * Math.pow(face, 1.5)), 0, 255);
            }
        }
        for (int i = 0; i < RINGS; i++)
            for (int j = 0; j < SIDES; j++) {
                if (a[i][j] + a[i][j + 1] + a[i + 1][j] + a[i + 1][j + 1] == 0) continue;
                vertex(out, matrix, normals, px[i][j], py[i][j], pz[i][j], WHITE, a[i][j]);
                vertex(out, matrix, normals, px[i + 1][j], py[i + 1][j], pz[i + 1][j], WHITE, a[i + 1][j]);
                vertex(out, matrix, normals, px[i + 1][j + 1], py[i + 1][j + 1], pz[i + 1][j + 1], WHITE, a[i + 1][j + 1]);
                vertex(out, matrix, normals, px[i][j + 1], py[i][j + 1], pz[i][j + 1], WHITE, a[i][j + 1]);
            }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, double x, double y, double z, int[] c, int alpha) {
        vertex(out, matrix, normals, x, y, z, c, alpha, MID_U, MID_V);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, double x, double y, double z, int[] c, int alpha,
                               float u, float v) {
        out.vertex(matrix, (float) x, (float) y, (float) z).color(c[0], c[1], c[2], alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normals, 0, 1, 0).endVertex();
    }
}
