package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.light.DynamicLights;
import com.antaurora.apofirstlight.client.light.LightCone;
import com.antaurora.apofirstlight.vehicle.HeadlightBeam;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The T15's lamps as per-pixel dynamic lights (client/light/DynamicLights, docs/vehicles/first_pickup_v0_design.md 8.15):
 * each frame, at the partial tick's pose, the low beams (HeadlightBeam.LOW), the high beams with them (HIGH), the reverse
 * lamps backward in reverse (REVERSE) and the tail lamps' red glow behind ({@link #TAIL}, brighter braking). The lamps at
 * each end share one shadow origin (the middle of the bumper at lamp height). Without a shader pack this replaces the
 * block light of client/vehicle/HeadlightField and the tail lamps' ground decal of HeadlightFx, and so with a shader pack
 * that draws them itself (option AFL_VEHICLE_LIGHTS: Aureveil); with any other pack those stay.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TruckLights {
    /** Trucks this close to the camera light (blocks), at most this many (nearest first; 8 lights each). */
    private static final double RANGE = 128;
    private static final int MAX_TRUCKS = 3;
    /**
     * The lamps (forward, right, up from the truck's origin, just outside the lenses; HeadlightField's): the low and high
     * beam lenses of each headlamp, the reverse lamps and the tail lamps at the back; the shadow origins at each end.
     */
    private static final double LAMP_F = 2.32, LAMP_Y = 0.81, LOW_R = 0.746, HIGH_R = 0.571, REVERSE_F = -2.36, REVERSE_R = 0.77,
            REVERSE_Y = 0.696, TAIL_F = -2.34, TAIL_R = 0.77, TAIL_Y = 0.86, FRONT_F = 2.36, REAR_F = -2.40;
    /** The tail lamps' glow on the ground and the truck behind: wide, short, aimed down (near light: lit, braking). */
    private static final LightCone TAIL = new LightCone(20, 70, 40, 15, 4.5, 3.0, 1.0, 0, 0.6, 0.3, 0.2),
            BRAKE = new LightCone(20, 70, 40, 15, 5.5, 7.0, 2.4, 0, 0.6, 0.3, 0.2);
    private static final LightCone LOW = LightCone.of(HeadlightBeam.LOW), HIGH = LightCone.of(HeadlightBeam.HIGH),
            REVERSE = LightCone.of(HeadlightBeam.REVERSE);
    /** Halogen headlamps (warm white), the reverse lamps (white), the tail lamps (red). */
    private static final float[] HEAD = {1.0F, 0.93F, 0.82F}, WHITE = {1.0F, 0.97F, 0.92F}, RED = {1.0F, 0.10F, 0.06F};

    private TruckLights() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        DynamicLights.register(TruckLights::collect);
        event.enqueueWork(DynamicLights::registerTextures);
    }

    private static void collect(DynamicLights.Lights out, ClientLevel level, Vec3 camera, float pt) {
        List<PickupTruckEntity> lit = new ArrayList<>();
        for (Entity e : level.entitiesForRendering())
            if (e instanceof PickupTruckEntity t && (t.headlights() || t.reversing() || t.braking()) && t.distanceToSqr(camera) < RANGE * RANGE)
                lit.add(t);
        lit.sort(Comparator.comparingDouble(t -> t.distanceToSqr(camera)));
        for (int i = 0; i < Math.min(lit.size(), MAX_TRUCKS); i++) truck(out, lit.get(i), pt);
    }

    private static void truck(DynamicLights.Lights out, PickupTruckEntity t, float pt) {
        Vec3 fwd = t.frameDirection(1, 0, 0, pt).normalize(), up = t.frameDirection(0, 0, 1, pt).normalize(), back = fwd.scale(-1);
        boolean head = t.headlights(), high = head && t.highBeam(), reverse = t.reversing(), brake = t.braking();
        if (head) {
            Vec3 o = t.framePoint(FRONT_F, 0, LAMP_Y, pt);
            int front = out.occluder(o.x, o.y, o.z);
            for (int side = -1; side <= 1; side += 2) {
                spot(out, t, LOW, LAMP_F, side * LOW_R, LAMP_Y, fwd, up, HEAD, front, pt);
                if (high) spot(out, t, HIGH, LAMP_F, side * HIGH_R, LAMP_Y, fwd, up, HEAD, front, pt);
            }
        }
        if (head || reverse || brake) {
            Vec3 o = t.framePoint(REAR_F, 0, REVERSE_Y, pt);
            int rear = out.occluder(o.x, o.y, o.z);
            for (int side = -1; side <= 1; side += 2) {
                if (reverse) spot(out, t, REVERSE, REVERSE_F, side * REVERSE_R, REVERSE_Y, back, up, WHITE, rear, pt);
                if (head || brake) spot(out, t, brake ? BRAKE : TAIL, TAIL_F, side * TAIL_R, TAIL_Y, back, up, RED, rear, pt);
            }
        }
    }

    private static void spot(DynamicLights.Lights out, PickupTruckEntity t, LightCone cone, double f, double r, double y, Vec3 fwd, Vec3 up,
                             float[] colour, int occluder, float pt) {
        Vec3 o = t.framePoint(f, r, y, pt);
        out.spot(cone, o.x, o.y, o.z, fwd, up, colour[0], colour[1], colour[2], occluder);
    }
}
