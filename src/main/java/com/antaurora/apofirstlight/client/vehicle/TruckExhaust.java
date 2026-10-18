package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FireFx;
import com.antaurora.apofirstlight.temperature.PlayerTemperature;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.physics.VehicleSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The T15's exhaust (2026-10-18, docs/vehicles/first_pickup_v0_design.md section 8.20): puffs of FireFx smoke blown out of
 * the tail pipe's open end (the model's exhaust_tip anchor, {@link #TIP}) along the pipe, slowing in the air, then rising
 * and drifting, left behind in the world as the truck drives on. What the engine shows, as an old turbo diesel does:
 * <pre>
 * the catch            a burst of dark grey for about a second
 * a cold engine        whitish grey, thick at first, thinning as the coolant warms from COLD_FROM to WARM_AT C
 * warm, idling         barely there; a little more with load (the pedal at speed)
 * floored low down     a short dark puff (the turbo not spooled yet: under LUG_RPM, the pedal pushed fast or held down)
 * cold air             white vapour all the time below VAPOUR_FROM C, more the colder (condensing water)
 * the engine off       nothing
 * </pre>
 * Every truck within {@link #RANGE} of the camera (the drivetrain readings are synced, as the engine sounds use them).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TruckExhaust {
    /** The outlet's centre (forward, right, up; m, the truck's frame) and the way the gas leaves (tools/pickup-t15/common.mjs EXHAUST_TIP). */
    static final double[] TIP = {-1.86, 0.70, 0.42}, OUT = {-0.6, 0.8, 0};
    private static final double RANGE = 64;
    /** Coolant (C): cold smoke up to COLD_FROM, gone by WARM_AT. */
    static final double COLD_FROM = 45, WARM_AT = 75;
    /** Below this rpm the turbo gives no boost: a pedal pushed down there smokes. */
    static final double LUG_RPM = 1700;
    /** Outdoor air (C) below which the exhaust's water shows as vapour, and where it is at its thickest. */
    static final double VAPOUR_FROM = 6, VAPOUR_FULL = -15;
    /** Ticks the catch smokes. */
    private static final int CATCH_TICKS = 24;

    private static final Map<PickupTruckEntity, State> STATES = new WeakHashMap<>();

    private TruckExhaust() {}

    private static final class State {
        boolean running;
        int sinceCatch = 1000, airAge = 1000;
        float pedal, lug;
        double air = 15, carry;
        Vec3 last;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Entity camera = mc.getCameraEntity();
        if (level == null || camera == null || mc.isPaused()) return;
        for (Entity e : level.entitiesForRendering())
            if (e instanceof PickupTruckEntity truck && truck.distanceToSqr(camera) < RANGE * RANGE) step(level, truck, STATES.computeIfAbsent(truck, t -> new State()));
    }

    private static void step(ClientLevel level, PickupTruckEntity truck, State s) {
        boolean run = truck.engineRunning();
        if (run && !s.running) s.sinceCatch = 0;
        s.running = run;
        s.sinceCatch++;
        Vec3 tip = truck.point(TIP[0], TIP[1], TIP[2]);
        Vec3 from = s.last != null && s.last.distanceToSqr(tip) < 9 ? s.last : tip;
        s.last = tip;
        if (++s.airAge >= 20) {   // once a second: the outdoor air there
            s.airAge = 0;
            s.air = PlayerTemperature.outdoorAir(level, BlockPos.containing(tip), tip.y);
        }
        float pedal = truck.throttlePedal();
        double rpm = truck.tachometer(1.0F), coolant = truck.coolantGauge(1.0F);
        // the turbo lag: a pedal pushed down fast (or held down) under LUG_RPM smokes until the boost comes, about a second
        float push = pedal - s.pedal;
        s.pedal = pedal;
        float low = (float) Mth.clamp((LUG_RPM - rpm) / 600, 0, 1);
        if (run && (push > 0.15F || pedal > 0.8F) && low > 0) s.lug = Math.max(s.lug, Math.min(1, pedal) * low);
        s.lug *= 0.93F;
        if (!run) return;

        double flow = rpm / VehicleSpec.T15.idleRpm * (0.35 + 0.65 * pedal);   // gas out, idle unloaded = 0.35
        float cold = (float) Mth.clamp((WARM_AT - coolant) / (WARM_AT - COLD_FROM), 0, 1);
        float vapour = (float) Mth.clamp((VAPOUR_FROM - s.air) / (VAPOUR_FROM - VAPOUR_FULL), 0, 1);
        float start = s.sinceCatch < CATCH_TICKS ? 1 - (float) s.sinceCatch / CATCH_TICKS : 0;
        // what this tick's gas looks like: its darkness (shade, 0 black .. 1 white) and how thick
        float dark = Math.max(start * 0.8F, s.lug), white = Math.max(cold * 0.8F, vapour);
        float alpha = 0.035F + 0.04F * pedal + 0.30F * cold * cold + 0.22F * vapour + 0.45F * dark;
        float shade = Mth.clamp(0.42F + 0.4F * white - 0.32F * dark, 0.08F, 0.88F);
        int life = Math.round(28 + 22 * white + 20 * dark);
        // puffs along the pipe's path this tick (none left out at speed), more with more gas
        s.carry += Math.min(3.0, 0.45 + 0.35 * flow + 2.0 * dark);
        Vec3 out = truck.frameDirection(OUT[0], OUT[1], OUT[2], 1.0F);
        double v = 0.05 + 0.025 * flow, carX = (truck.getX() - truck.xo) * 0.25, carZ = (truck.getZ() - truck.zo) * 0.25;
        RandomSource r = level.random;
        int n = (int) s.carry;
        s.carry -= n;
        for (int i = 0; i < n; i++) {
            double t = (i + r.nextDouble()) / n;
            Vec3 p = from.lerp(tip, t);
            FireFx.blown(level, p.x, p.y, p.z, out.x * v + carX + (r.nextDouble() - 0.5) * 0.01, 0.004 + (r.nextDouble() - 0.5) * 0.01,
                    out.z * v + carZ + (r.nextDouble() - 0.5) * 0.01, 0.035F + 0.02F * (float) Math.min(flow, 3) + 0.04F * dark, shade, Math.min(0.85F, alpha), life);
        }
    }
}
