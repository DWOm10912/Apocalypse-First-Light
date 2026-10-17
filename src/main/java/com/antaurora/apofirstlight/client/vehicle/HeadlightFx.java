package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.LiquidRenderTypes;
import com.antaurora.apofirstlight.vehicle.HeadlightBeam;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The T15's lamps as light in the air (docs/vehicles/first_pickup_v0_design.md 8.10): a soft flare over each lit lamp and,
 * from the headlamps, a faint beam cone, both added (LiquidRenderTypes.HEADLIGHT_GLOW: emissive, no depth writes, tested against the
 * world so a wall cuts them), in the fire effects' world pass stage (AFTER_BLOCK_ENTITIES; shown by Sundial as FireFx is),
 * skipped in a shader pack's shadow pass. The lit lens itself is the body mesh at full light (PickupTruckRenderer lamps).
 * <p>Flares face the camera, strongest looking into the lamp (headlamps forward, tail lamps back), weaker by day (the
 * sky light at the truck times the daylight). The beam cone runs {@link #LOW_LENGTH} / {@link #HIGH_LENGTH} m along each
 * lit headlamp's aim (HeadlightBeam), fading along its length and toward its silhouette, seen only in the dark and more in
 * rain: the light it puts on the world is HeadlightField's block light.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HeadlightFx {
    private static final double RANGE = 160;
    /** The flare sprite's middle (textures/effect/headlight_flare: white, full alpha there) for the cone. */
    private static final float MID_U = 0.5F, MID_V = 0.5F;
    /** Halogen white, amber, red (0-255). */
    private static final int[] WHITE = {255, 238, 210}, AMBER = {255, 150, 40}, RED = {255, 30, 20};
    /** Beam cones: length and end radius (m); the lens radius; sides and rings; the strength in the dark (alpha 0-1). */
    private static final double LOW_LENGTH = 9, LOW_RADIUS = 2.0, HIGH_LENGTH = 14, HIGH_RADIUS = 1.6, LENS = 0.09;
    private static final int SIDES = 16, RINGS = 6;
    private static final float CONE_ALPHA = 0.07F, RAIN_BOOST = 1.6F;
    /** A flare's least size per metre away, so a far lamp keeps a core a few pixels wide. */
    private static final double FAR_SIZE = 0.012;
    /** The flares' strength without a shader pack (the lens glows by itself there; user 2026-10-16: the flare alone looked cheap). */
    private static final float VANILLA_FLARE = 0.5F;

    /** A lamp: frame point (forward, right, up), facing forward (+1) or back (-1), flare half-size (m; the hot core is a tenth of it), colour. */
    private record Flare(double f, double r, double y, int facing, float size, int[] colour) {}

    private static final Flare[] LOW = {new Flare(2.30, -0.746, 0.81, 1, 0.75F, WHITE), new Flare(2.30, 0.746, 0.81, 1, 0.75F, WHITE)},
            HIGH = {new Flare(2.30, -0.571, 0.81, 1, 0.7F, WHITE), new Flare(2.30, 0.571, 0.81, 1, 0.7F, WHITE)},
            PARK = {new Flare(2.24, -0.658, 0.68, 1, 0.22F, AMBER), new Flare(2.24, 0.658, 0.68, 1, 0.22F, AMBER)},
            TAIL = {new Flare(-2.30, -0.77, 0.83, -1, 0.42F, RED), new Flare(-2.30, 0.77, 0.83, -1, 0.42F, RED)},
            REVERSE = {new Flare(-2.30, -0.77, 0.696, -1, 0.38F, WHITE), new Flare(-2.30, 0.77, 0.696, -1, 0.38F, WHITE)},
            PLATE = {new Flare(-2.36, 0, 0.54, -1, 0.14F, WHITE)};

    private HeadlightFx() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || AflShaderCompat.activeShadowPass()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Camera camera = event.getCamera();
        if (level == null) return;
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = null;
        PoseStack pose = event.getPoseStack();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof PickupTruckEntity truck) || truck.distanceToSqr(cam) > RANGE * RANGE) continue;
            boolean on = truck.headlights(), brake = truck.braking(), reverse = truck.reversing();
            if (!on && !brake && !reverse) continue;
            if (out == null) out = buffers.getBuffer(LiquidRenderTypes.HEADLIGHT_GLOW);
            float dark = dark(level, truck, pt);
            Matrix4f matrix = pose.last().pose();
            Matrix3f normals = pose.last().normal();
            Vec3 fwd = truck.frameDirection(1, 0, 0, pt);
            // without a shader pack the lit lens itself carries the light (PickupTruckRenderer#lampGlow): the flare only a soft halo round it
            float flare = (0.35F + 0.65F * dark) * (AflShaderCompat.shaderPackInUse() ? 1 : VANILLA_FLARE);
            if (on) {
                flares(out, matrix, normals, camera, truck, pt, fwd, LOW, flare);
                flares(out, matrix, normals, camera, truck, pt, fwd, PARK, flare * 0.8F);
                flares(out, matrix, normals, camera, truck, pt, fwd, PLATE, flare * 0.5F);
                if (truck.highBeam()) flares(out, matrix, normals, camera, truck, pt, fwd, HIGH, flare);
            }
            if (on || brake) flares(out, matrix, normals, camera, truck, pt, fwd, TAIL, flare * (brake ? 1.0F : 0.5F));
            if (reverse) flares(out, matrix, normals, camera, truck, pt, fwd, REVERSE, flare * 0.8F);
            float cone = CONE_ALPHA * dark * dark * (1 + RAIN_BOOST * level.getRainLevel(pt));
            if (on && cone > 0.004F) {
                for (Flare l : LOW) cone(out, matrix, normals, cam, truck, pt, l, HeadlightBeam.LOW, LOW_LENGTH, LOW_RADIUS, cone);
                if (truck.highBeam()) for (Flare l : HIGH) cone(out, matrix, normals, cam, truck, pt, l, HeadlightBeam.HIGH, HIGH_LENGTH, HIGH_RADIUS, cone);
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

    private static void flares(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Camera camera, PickupTruckEntity truck, float pt, Vec3 fwd,
                               Flare[] lamps, float strength) {
        Vec3 cam = camera.getPosition();
        Quaternionf view = camera.rotation();
        Vector3f v = new Vector3f();
        for (Flare l : lamps) {
            // just in front of the lens, so the bodywork round it does not cut the flare
            Vec3 at = truck.framePoint(l.f() + 0.10 * l.facing(), l.r(), l.y(), pt);
            Vec3 to = cam.subtract(at);
            double d = to.length();
            if (d < 0.3) continue;
            double facing = (fwd.x * to.x + fwd.y * to.y + fwd.z * to.z) / d * l.facing();
            float s = strength * (float) (0.12 + 0.88 * Math.pow(Math.max(0, facing), 2));
            int alpha = Mth.clamp(Math.round(s * 255), 0, 255);
            if (alpha < 3) continue;
            // far off a lamp stays a glowing point a few pixels wide (the flare's hot core is a tenth of its size)
            float size = Math.max(l.size() * (0.6F + 0.4F * (float) Math.max(0, facing)), (float) (d * FAR_SIZE));
            double x = at.x - cam.x, y = at.y - cam.y, z = at.z - cam.z;
            int[] c = l.colour();
            corner(out, matrix, normals, view, v, -1, -1, size, x, y, z, 0, 1, c, alpha);
            corner(out, matrix, normals, view, v, -1, 1, size, x, y, z, 0, 0, c, alpha);
            corner(out, matrix, normals, view, v, 1, 1, size, x, y, z, 1, 0, c, alpha);
            corner(out, matrix, normals, view, v, 1, -1, size, x, y, z, 1, 1, c, alpha);
        }
    }

    private static void corner(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Quaternionf view, Vector3f v, float cx, float cy, float size,
                               double x, double y, double z, float u, float uv, int[] c, int alpha) {
        v.set(cx, cy, 0.0F).rotate(view).mul(size);
        out.vertex(matrix, (float) (x + v.x()), (float) (y + v.y()), (float) (z + v.z())).color(c[0], c[1], c[2], alpha).uv(u, uv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normals, 0, 1, 0).endVertex();
    }

    /**
     * One beam cone from a lamp along the pattern's aim (down by its aimDown, a little out to its own side): rings from
     * the lens to the length; a vertex's alpha falls with the square of the share of the length and with how edge-on the
     * cone's side is to the camera (a lit haze is thickest looking through it).
     */
    private static void cone(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3 cam, PickupTruckEntity truck, float pt, Flare lamp,
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
                vertex(out, matrix, normals, px[i][j], py[i][j], pz[i][j], a[i][j]);
                vertex(out, matrix, normals, px[i + 1][j], py[i + 1][j], pz[i + 1][j], a[i + 1][j]);
                vertex(out, matrix, normals, px[i + 1][j + 1], py[i + 1][j + 1], pz[i + 1][j + 1], a[i + 1][j + 1]);
                vertex(out, matrix, normals, px[i][j + 1], py[i][j + 1], pz[i][j + 1], a[i][j + 1]);
            }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, double x, double y, double z, int alpha) {
        out.vertex(matrix, (float) x, (float) y, (float) z).color(WHITE[0], WHITE[1], WHITE[2], alpha).uv(MID_U, MID_V)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normals, 0, 1, 0).endVertex();
    }
}
