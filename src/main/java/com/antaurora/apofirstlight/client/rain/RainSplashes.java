package com.antaurora.apofirstlight.client.rain;

import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Rain V1 splashes (docs/rendering/rain_v1.md), replacing vanilla's pixel rain particles: each client tick a number of
 * drops (with the rain's strength and the particle setting) land at random spots, as many per square metre everywhere
 * within RADIUS of the camera, on what the rain reaches first there (the drawn surface, a fluid's surface, a vehicle's
 * roof, glass or hood: RainTrucks, the body as drawn); each stays where it landed in the world, or on the truck it landed
 * on (going along with it).
 * <ul>
 * <li>on a hard surface: a crown (textures/effect/rain_splash cells 0-1, facing the camera, its base on the surface, a few
 * ticks) and, near the camera, a few droplets thrown up and out that fall back;</li>
 * <li>on water: a ripple ring spreading flat on the surface and one small droplet;</li>
 * <li>leaves (the rain is caught in the canopy), lava and fire: none (vanilla still steams on lava).</li>
 * </ul>
 * Drawn after the weather (over water) from their own builder, at once (#render), alpha blended, lit by the light in the
 * open over where they landed; the vanilla entity translucent program they use carries the dynamic lights (headlamps
 * light them). Capped.
 */
final class RainSplashes {
    /**
     * Splashes per square metre a tick in full rain (fancy, all particles), the same everywhere in the radius (a share
     * skewed to the camera made a dense patch that went along with the player); the radius (far ones fade out from FADE,
     * a few pixels by then); NEAR: within it the drawn surface is asked per spot and droplets fly, farther the column map's
     * height serves; the vertical reach (as vanilla's 10); the cap.
     */
    private static final double DENSITY = 0.26, RADIUS = 40, FADE = 30, NEAR = 12, REACH = 10;
    private static final int MAX = 9000;
    /** A crown's base over the drawn surface (m). */
    private static final double LIFT = 0.015;

    private static final class Splash {
        double x, y, z, vx, vy, vz, ox, oy, oz;
        int age, life;
        /** The truck it landed on: x, y, z are then forward, up, right in its frame (it goes along with the truck). */
        PickupTruckEntity on;
        /** Packed light where it landed, in the open over the surface (RainSplashes#land). */
        int light;
        byte kind;   // 0 crown, 1 ripple, 2 droplet
        byte cell;
        float size;
    }

    private final List<Splash> live = new ArrayList<>();
    /** The trucks near the camera this tick (land). */
    private List<RainTrucks.Frame> trucks = List.of();
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

    void clear() {
        live.clear();
    }

    boolean isEmpty() {
        return live.isEmpty();
    }

    /** Client tick: the live ones move and age, new ones land (rain = this tick's rain level). */
    void tick(ClientLevel level, RainColumns columns, Vec3 cam, float rain) {
        for (Splash s : live) {
            s.ox = s.x;
            s.oy = s.y;
            s.oz = s.z;
            s.age++;
            if (s.kind == 2) {
                s.vy -= 0.035;
                s.x += s.vx;
                s.y += s.vy;
                s.z += s.vz;
                s.vx *= 0.94;
                s.vz *= 0.94;
            }
        }
        live.removeIf(s -> s.age >= s.life || s.on != null && s.on.isRemoved());
        if (rain <= 0) return;
        trucks = RainTrucks.near(level, cam, RADIUS + 6, 8, 1.0F);
        ParticleStatus setting = Minecraft.getInstance().options.particles().get();
        float share = setting == ParticleStatus.MINIMAL ? 0.2F : setting == ParticleStatus.DECREASED ? 0.5F : 1.0F;
        if (!Minecraft.useFancyGraphics()) share *= 0.5F;
        int n = (int) Math.round(DENSITY * Math.PI * RADIUS * RADIUS * rain * rain * share);
        RandomSource r = level.random;
        landed[0] = landed[1] = landed[2] = landed[3] = landed[4] = 0;
        int camY = Mth.floor(cam.y);
        for (int i = 0; i < n && live.size() < MAX; i++) {
            // uniform over the disc: as many per square metre near the camera as far from it
            double d = RADIUS * Math.sqrt(r.nextDouble()), a = r.nextDouble() * Math.PI * 2;
            double x = cam.x + Math.cos(a) * d, z = cam.z + Math.sin(a) * d;
            int bx = Mth.floor(x), bz = Mth.floor(z);
            double top = columns.top(bx, bz);
            if (Double.isNaN(top) || top > camY + REACH || top < camY - REACH) continue;
            if (columns.crown(bx, bz)) continue;   // into a mesh tree's crown: no splash on its leaves or the ground under it
            land(level, r, x, z, d < NEAR ? Double.NaN : top);
        }
    }

    /** Splashes made the last tick: on the road, smoothed terrain, other blocks, water, vehicles (status). */
    final int[] landed = new int[5];

    /** columnTop: NaN near the camera (the drawn surface asked here), else the column map's height (far, cheap). */
    private void land(ClientLevel level, RandomSource r, double x, double z, double columnTop) {
        boolean near = Double.isNaN(columnTop);
        int bx = Mth.floor(x), bz = Mth.floor(z), h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        // the light in the open over the column (a splash on a slope or a road layer sits inside its block: sky light 0 there)
        spawnLight = LevelRenderer.getLightColor(level, at.set(bx, h, bz));
        BlockPos.MutableBlockPos p = at.set(bx, h - 1, bz);
        BlockState state = level.getBlockState(p);
        FluidState fluid = level.getFluidState(p);
        // the surface as drawn (RainGround): a splash on a block's collision top sat under the road mesh and the slopes
        double ground = near ? RainGround.surface(level, x, z, h) : columnTop;
        int kind = !near ? 2 : switch (RainGround.source()) { case ROAD -> 0; case SMOOTH -> 1; default -> 2; };
        // a truck's roof, glass, hood or bed over the ground here (and not under a cover): it lands there, on the truck
        if (landOnTruck(r, x, z, h, ground, near)) return;
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FIRE) || fluid.is(FluidTags.LAVA)) return;
        if (!fluid.isEmpty() && fluid.is(FluidTags.WATER)) {
            double surface = p.getY() + fluid.getHeight(level, p);
            if (Double.isNaN(ground) || surface >= ground) {
                landed[3]++;
                Splash s = add(1, x, surface + 0.012, z, 14 + r.nextInt(6));
                s.size = 0.16F + r.nextFloat() * 0.1F;
                if (near && r.nextFloat() < 0.5F) droplet(r, x, surface, z, 0.5);
                return;
            }
        }
        if (Double.isNaN(ground)) return;   // nothing to land on (no collision, no fluid)
        landed[kind]++;
        crown(r, x, ground, z, near);
    }

    /** Live splashes by kind (status): crowns, ripples, droplets. */
    int[] counts() {
        int[] c = new int[3];
        for (Splash s : live) c[s.kind]++;
        return c;
    }

    /** A drop falling at (x, z) onto the first truck part it meets from above the column top h, if any. */
    private boolean landOnTruck(RandomSource r, double x, double z, int h, double ground, boolean near) {
        if (trucks.isEmpty()) return false;
        double from = h + 3.0, best = Double.NaN;
        RainTrucks.Frame on = null;
        for (RainTrucks.Frame t : trucks) {
            double dx = x - t.o.x, dz = z - t.o.z;
            if (dx * dx + dz * dz > REACH_TRUCK * REACH_TRUCK) continue;
            double fall = RainTrucks.fall(t, x, from, z, 8.0);
            if (!Double.isNaN(fall) && !(fall >= best)) {
                best = fall;
                on = t;
            }
        }
        if (on == null) return false;
        double y = from - best;
        // under a cover (the column's top is over the truck) or below the ground drawn here: not reached
        if (y < h - 0.5 || !Double.isNaN(ground) && y <= ground) return false;
        landed[4]++;
        double[] p = on.local(x, y, z);
        crown(r, on.truck, p[0], p[2], p[1], near);
        return true;
    }

    /** A truck's parts are all within this of its origin (horizontally, m). */
    private static final double REACH_TRUCK = 3.6;

    private void crown(RandomSource r, double x, double y, double z, boolean near) {
        crown(r, null, x, y, z, near);
    }

    /** A crown and its droplets; on a truck, x, y, z are in its frame (forward, up, right). */
    private void crown(RandomSource r, PickupTruckEntity on, double x, double y, double z, boolean near) {
        // lifted a little off the surface: the quad lies near flat seen from above and must not dip into it
        Splash s = add(0, x, y + LIFT, z, 3 + r.nextInt(2));
        s.on = on;
        s.cell = (byte) r.nextInt(2);
        s.size = 0.055F + r.nextFloat() * 0.04F;
        int drops = near ? r.nextInt(3) + (r.nextFloat() < 0.5F ? 1 : 0) : 0;
        for (int k = 0; k < drops; k++) {
            Splash d = droplet(r, x, y, z, 1.0);
            if (d != null) d.on = on;
        }
    }

    private Splash droplet(RandomSource r, double x, double y, double z, double strength) {
        if (live.size() >= MAX) return null;
        Splash s = add(2, x, y + 0.01, z, 6 + r.nextInt(5));
        double a = r.nextDouble() * Math.PI * 2, out = (0.008 + r.nextDouble() * 0.02) * strength;
        s.vx = Math.cos(a) * out;
        s.vz = Math.sin(a) * out;
        s.vy = (0.03 + r.nextDouble() * 0.05) * strength;
        s.size = 0.008F + r.nextFloat() * 0.006F;
        return s;
    }

    /** The light new splashes take (set by land). */
    private int spawnLight = LightTexture.FULL_BRIGHT;

    private Splash add(int kind, double x, double y, double z, int life) {
        Splash s = new Splash();
        s.kind = (byte) kind;
        s.x = s.ox = x;
        s.y = s.oy = y;
        s.z = s.oz = z;
        s.life = life;
        s.light = spawnLight;
        live.add(s);
        return s;
    }

    /**
     * Drawn after the weather: positions camera-relative (the model-view is already turned to the camera, RainRenderer);
     * `view` is the level pose's normal matrix, so the up normal is turned the same way as the program's light directions
     * (an unturned normal shaded the splashes darker the more the camera looked down).
     */
    void render(ClientLevel level, Camera camera, float pt, Matrix3f view) {
        if (live.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        // its own builder, drawn right here: Oculus (even with no pack) swaps the shared buffer source for a fully buffered
        // one that draws only after the level, when the camera's turn is off the model-view again (user in game
        // 2026-10-17: splashes slid with the player, showed in the sky looking up, were hidden looking down)
        BufferBuilder out = builder;
        out.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        int drawn = 0;
        Matrix4f m = new PoseStack().last().pose();
        org.joml.Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        org.joml.Vector3f n = view.transform(new org.joml.Vector3f(0, 1, 0));
        nx = n.x();
        ny = n.y();
        nz = n.z();
        // the trucks as drawn this frame: a splash on one goes along with it; one left in the world where a truck now is
        // (it drove over or onto it) is not drawn inside the body
        List<RainTrucks.Frame> frames = RainTrucks.near(level, cam, RADIUS + 6, 8, pt);
        culled = 0;
        for (Splash s : live) {
            double x, y, z;
            if (s.on != null) {
                Vec3 w = s.on.framePoint(Mth.lerp(pt, s.ox, s.x), Mth.lerp(pt, s.oz, s.z), Mth.lerp(pt, s.oy, s.y), pt);
                x = w.x - cam.x;
                y = w.y - cam.y;
                z = w.z - cam.z;
            } else {
                double wx = Mth.lerp(pt, s.ox, s.x), wy = Mth.lerp(pt, s.oy, s.y), wz = Mth.lerp(pt, s.oz, s.z);
                if (insideTruck(frames, wx, wy, wz)) {
                    culled++;
                    continue;
                }
                x = wx - cam.x;
                y = wy - cam.y;
                z = wz - cam.z;
            }
            if (x * x + y * y + z * z > (RADIUS + 2) * (RADIUS + 2)) continue;
            float t = Mth.clamp((s.age + pt) / s.life, 0.0F, 1.0F);
            // far ones fade out towards the radius: no edge to the splashes
            float k = (float) Mth.clamp((RADIUS - Math.sqrt(x * x + z * z)) / (RADIUS - FADE), 0.0, 1.0), far = k * k * (3 - 2 * k);
            int light = s.light;
            switch (s.kind) {
                case 0 -> {   // facing the camera, its base on the surface: it rises up the screen (an upright one
                              // turned only about the vertical was edge-on, near invisible, looking down at it)
                    float grow = 0.65F + 0.55F * t, alpha = 0.75F * (1.0F - t * t) * far;
                    float hw = s.size * grow, h = s.size * 2.0F * grow;
                    float ax = left.x() * hw, ay = left.y() * hw, az = left.z() * hw;
                    float u0 = s.cell * 0.25F, u1 = u0 + 0.25F;
                    quad(out, m, (float) x + ax, (float) y + ay, (float) z + az, (float) x - ax, (float) y - ay, (float) z - az,
                            up.x() * h, Math.max(0.0F, up.y()) * h, up.z() * h, u0, u1, 0, 1, alpha, light);
                }
                case 1 -> {   // flat on the water, spreading
                    float radius = s.size * (0.15F + 0.85F * (1.0F - (1.0F - t) * (1.0F - t))), alpha = 0.55F * (1.0F - t) * far;
                    float fx = (float) x, fy = (float) y, fz = (float) z;
                    vertex(out, m, fx - radius, fy, fz - radius, 0.5F, 0.0F, alpha, light);
                    vertex(out, m, fx - radius, fy, fz + radius, 0.5F, 1.0F, alpha, light);
                    vertex(out, m, fx + radius, fy, fz + radius, 0.75F, 1.0F, alpha, light);
                    vertex(out, m, fx + radius, fy, fz - radius, 0.75F, 0.0F, alpha, light);
                }
                default -> {   // a droplet, facing the camera
                    float r = s.size, alpha = 0.8F * (1.0F - t * t * t) * far;
                    float lx = left.x() * r, ly = left.y() * r, lz = left.z() * r, ux = up.x() * r, uy = up.y() * r, uz = up.z() * r;
                    float fx = (float) x, fy = (float) y, fz = (float) z;
                    vertex(out, m, fx + lx - ux, fy + ly - uy, fz + lz - uz, 0.75F, 1.0F, alpha, light);
                    vertex(out, m, fx - lx - ux, fy - ly - uy, fz - lz - uz, 1.0F, 1.0F, alpha, light);
                    vertex(out, m, fx - lx + ux, fy - ly + uy, fz - lz + uz, 1.0F, 0.0F, alpha, light);
                    vertex(out, m, fx + lx + ux, fy + ly + uy, fz + lz + uz, 0.75F, 0.0F, alpha, light);
                }
            }
            drawn++;
        }
        if (drawn == 0) {
            out.end().release();
            return;
        }
        // now, under this stage's model-view (the camera's turn on it)
        RainRenderTypes.SPLASH.end(out, RenderSystem.getVertexSorting());
    }

    /** World splashes left out last frame because a truck's body is where they are (status). */
    int culled;

    private static boolean insideTruck(List<RainTrucks.Frame> frames, double x, double y, double z) {
        for (RainTrucks.Frame t : frames) {
            double dx = x - t.o.x, dz = z - t.o.z;
            if (dx * dx + dz * dz < REACH_TRUCK * REACH_TRUCK && t.inside(x, y, z)) return true;
        }
        return false;
    }

    /** The splashes' own vertex builder (render). */
    private final BufferBuilder builder = new BufferBuilder(64 * 1024);

    /** A quad from the base edge (x0..x1) up by (dx, dy, dz). */
    private static void quad(VertexConsumer out, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float dx, float dy, float dz, float u0, float u1, float v0, float v1, float alpha, int light) {
        vertex(out, m, x0, y0, z0, u0, v1, alpha, light);
        vertex(out, m, x1, y1, z1, u1, v1, alpha, light);
        vertex(out, m, x1 + dx, y1 + dy, z1 + dz, u1, v0, alpha, light);
        vertex(out, m, x0 + dx, y0 + dy, z0 + dz, u0, v0, alpha, light);
    }

    /** The world's up in view space this frame (render). */
    private static float nx, ny = 1, nz;

    /** m is the identity (positions are camera-relative already): the whole-vertex call, BufferBuilder's fast path for NEW_ENTITY. */
    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z, float u, float v, float alpha, int light) {
        out.vertex(x, y, z, 0.86F, 0.89F, 0.94F, alpha, u, v, OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
    }
}
