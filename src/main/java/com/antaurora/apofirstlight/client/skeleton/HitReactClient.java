package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.HealthPackets;
import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The flinch of injury knockdown V1 (docs/gameplay/injury_knockdown_v1_plan.md 2; health/Knockdown sends each hit that came
 * from somewhere): the body sways away from where the hit came from, a damped swing e^(-ζωt)·sin(ω_d·t) (ω {@link #OMEGA},
 * ζ {@link #ZETA}: the most at about 0.09 s, gone within 0.5 s), {@link #BASE}° + {@link #PER_STRENGTH}° × the share of
 * health lost, hits adding up to at most {@link #MAX}°. Front: back; behind: forward; right: to the left. The body only
 * (HumanoidPoser.Input#flinchPitch / flinchRoll, the pelvis, chest and head taking their shares); the camera never.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HitReactClient {
    private static final float OMEGA = 14F, ZETA = 0.5F, BASE = 6F, PER_STRENGTH = 30F, MAX = 25F, LASTS = 0.6F;
    private static final float DAMPED = OMEGA * (float) Math.sqrt(1 - ZETA * ZETA);
    /** The swing's peak, e^(-ζω t)·sin(ω_d t) at t = atan(ω_d / ζω) / ω_d, for a peak of exactly the amplitude. */
    private static final float PEAK;

    static {
        float t = (float) Math.atan2(DAMPED, ZETA * OMEGA) / DAMPED;
        PEAK = (float) (Math.exp(-ZETA * OMEGA * t) * Math.sin(DAMPED * t));
    }

    /** One hit: when (s, the level clock), the world yaw toward where it came from, the amplitude (deg). */
    private record Hit(double at, float yaw, float amplitude) {}

    private static final Map<Integer, List<Hit>> HITS = new HashMap<>();

    private HitReactClient() {
    }

    public static void accept(HealthPackets.Hit packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        HITS.computeIfAbsent(packet.entity(), id -> new ArrayList<>())
                .add(new Hit(mc.level.getGameTime() / 20.0, packet.yaw(), Math.min(MAX, BASE + PER_STRENGTH * packet.strength())));
    }

    /** This body's flinch now into the poser's input (zero when none). */
    static void apply(LivingEntity entity, HumanoidPoser.Input in, float partial) {
        in.flinchPitch = 0F;
        in.flinchRoll = 0F;
        List<Hit> hits = HITS.get(entity.getId());
        Minecraft mc = Minecraft.getInstance();
        if (hits == null || mc.level == null) return;
        double now = (mc.level.getGameTime() + partial) / 20.0;
        float bodyYaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        float pitch = 0F, roll = 0F;
        for (Hit h : hits) {
            float t = (float) (now - h.at());
            if (t < 0 || t > LASTS) continue;
            float swing = h.amplitude() * (float) (Math.exp(-ZETA * OMEGA * t) * Math.sin(DAMPED * t)) / PEAK;
            // where it came from, from the facing (+ = on the right): away from it, back from the front, left from the right
            float from = Mth.wrapDegrees(h.yaw() - bodyYaw) * Mth.DEG_TO_RAD;
            pitch -= swing * Mth.cos(from);
            roll += swing * Mth.sin(from);
        }
        in.flinchPitch = Mth.clamp(pitch, -MAX, MAX);
        in.flinchRoll = Mth.clamp(roll, -MAX, MAX);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            HITS.clear();
            return;
        }
        double now = mc.level.getGameTime() / 20.0;
        HITS.values().removeIf(list -> {
            list.removeIf(h -> now - h.at() > LASTS || now < h.at() - 1);
            return list.isEmpty();
        });
    }
}
