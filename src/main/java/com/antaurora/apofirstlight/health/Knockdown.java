package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.survivor.LiveRagdolls;
import com.antaurora.apofirstlight.weight.EncumbranceState;
import com.antaurora.apofirstlight.weight.PlayerWeightRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Injury knockdown V1 (docs/gameplay/injury_knockdown_v1_plan.md, user 2026-10-22), on the server. After a player's damage
 * was scaled (PlayerHealth#damage) and does not kill:
 * <ul>
 * <li><b>the flinch</b> (plan 2): a hit that came from somewhere tells the player and its trackers where from and how hard
 * (HealthPackets.Hit; client/skeleton/HitReactClient sways the body away from it);</li>
 * <li><b>the collapse</b> (plan 4): health left at most collapse.at drops the player onto the front (body/collapse, then
 * crawling, forced pose SWIMMING from the start: the clip lands in the prone stance); it crawls × collapse.crawl, cannot
 * jump or sprint, and gets up (FallLanding#getUp) after min_seconds once health is back to up_at, or after max_seconds,
 * as soon as it can stand there. Not saved: a player that logs out lying gets up on its own;</li>
 * <li><b>the knockdown</b> (plan 3): a hit from somewhere knocks the player down away from it by chance (HealthConfig.Knockdown),
 * explosions, falling trees and vehicles always (FallLanding#knock: body/knock_back from the front, knock_front from behind,
 * knock_side from the right, mirrored from the left); the held item may fly.</li>
 * </ul>
 * Neither in water, riding, already falling or getting up, or lying (crawling, lightning, collapsed).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class Knockdown {
    private static final UUID MOVE_ID = UUID.fromString("5d8e2a17-3c94-4b6f-a1e0-7f24c9b3d615");
    /** Each collapsed player's start (server ticks). */
    private static final Map<UUID, Integer> COLLAPSED = new HashMap<>();

    private Knockdown() {
    }

    /** After a player's damage was scaled (PlayerHealth), after the wounds, fractures and lightning had theirs. */
    static void onDamage(ServerPlayer player, DamageSource source, double healthLost) {
        if (!(healthLost < player.getHealth()) || !player.isAlive()) return;
        Vec3 from = source.is(DamageTypes.LIGHTNING_BOLT) ? null : source.getSourcePosition();
        if (from != null) {
            float yaw = (float) (Mth.atan2(from.z - player.getZ(), from.x - player.getX()) * Mth.RAD_TO_DEG) - 90F;
            var packet = new HealthPackets.Hit(player.getId(), yaw, (float) Mth.clamp(healthLost / player.getMaxHealth(), 0, 1));
            AflNetwork.skeletonTo(player, packet);
            AflNetwork.skeletonToTracking(player, packet);
        }
        // a vehicle's hit (vehicle/VehicleImpact) throws the living body as a ragdoll (plan 9), crawling or collapsed too;
        // it gets up (or stays down collapsed) once it lies still
        Vec3 thrown = com.antaurora.apofirstlight.vehicle.VehicleImpact.thrown(player);
        if (thrown != null && !player.isInWater() && !player.isPassenger() && !player.isSleeping() && !LightningInjury.down(player)
                && !FallLanding.fallingOver(player) && LiveRagdolls.canStart(player)) {
            if (COLLAPSED.remove(player.getUUID()) != null) {
                moveModifier(player, false);
                player.setForcedPose(null);
            }
            LiveRagdolls.start(player, source, thrown);
            if (player.getRandom().nextDouble() < HealthConfig.get().knockdown.drop) LightningInjury.throwHeld(player, player.getRandom());
            return;
        }
        if (player.isInWater() || player.isPassenger() || player.isSleeping() || player.isFallFlying() || FallLanding.fallingOver(player)
                || player.getForcedPose() == Pose.SWIMMING || player.isVisuallyCrawling() || LightningInjury.down(player)
                || COLLAPSED.containsKey(player.getUUID()))
            return;
        var config = HealthConfig.get();
        if (player.getHealth() - healthLost <= config.collapse.at && !any(config.collapse.ignore, source)) {
            collapse(player);
            return;
        }
        var k = config.knockdown;
        boolean always = any(k.always, source) && healthLost >= k.alwaysMin;
        // a hit from nowhere (a falling tree) knocks down only when it always does: onto the front
        if (from == null && !always) return;
        if (!always && !(player.getRandom().nextDouble() < chance(player, healthLost))) return;
        knock(player, from);
    }

    /** Plan 3.1: the chance a hit taking this much health knocks the player down. */
    static double chance(ServerPlayer player, double healthLost) {
        var k = HealthConfig.get().knockdown;
        double p = Fractures.chance(k.chance, healthLost);
        if (!(p > 0)) return 0;
        if (PlayerWeightRuntime.state(player).tier() != EncumbranceState.Tier.LIGHT) p += k.load;
        boolean legBroken = false;
        for (Fractures.View v : Fractures.views(player)) legBroken |= v.part().leg();
        if (legBroken) p *= k.brokenLeg;
        if (player.isSprinting()) p += k.sprint;
        if (player.isShiftKeyDown()) p *= k.sneak;
        return Mth.clamp(p, 0, 1);
    }

    /** Plan 3.2: down away from where the hit came from (onto the front when it came from nowhere). */
    private static void knock(ServerPlayer player, @Nullable Vec3 from) {
        byte kind = FallLanding.KNOCK_FRONT;
        boolean mirror = false;
        if (from != null) {
            Vec3 forward = Vec3.directionFromRotation(0, player.yBodyRot), left = Vec3.directionFromRotation(0, player.yBodyRot - 90);
            double dx = from.x - player.getX(), dz = from.z - player.getZ();
            double ahead = dx * forward.x + dz * forward.z, aside = dx * left.x + dz * left.z;
            double angle = Math.toDegrees(Math.atan2(Math.abs(aside), ahead));
            double cone = HealthConfig.get().knockdown.frontCone;
            if (angle <= cone) kind = FallLanding.KNOCK_BACK;
            else if (angle < 180 - cone) {
                kind = FallLanding.KNOCK_SIDE;
                // the clip falls onto the left side, away from a hit on the right; a hit on the left mirrors it
                mirror = aside > 0;
            }
        }
        FallLanding.knock(player, kind, mirror);
        if (player.getRandom().nextDouble() < HealthConfig.get().knockdown.drop) LightningInjury.throwHeld(player, player.getRandom());
    }

    private static void collapse(ServerPlayer player) {
        COLLAPSED.put(player.getUUID(), player.server.getTickCount());
        player.setForcedPose(Pose.SWIMMING);
        FallLanding.knock(player, FallLanding.COLLAPSE, false);
        message(player, "down");
    }

    /** A living ragdoll comes to rest (survivor/LiveRagdolls): whether it stays down collapsed (health at most collapse.at). */
    public static boolean staysDown(ServerPlayer player) {
        return player.getHealth() <= HealthConfig.get().collapse.at;
    }

    /** A living ragdoll came to rest at low health: collapsed where it lies, no clip (the clients blend into lying). */
    public static void collapseWhereLying(ServerPlayer player) {
        COLLAPSED.put(player.getUUID(), player.server.getTickCount());
        player.setForcedPose(Pose.SWIMMING);
        message(player, "down");
    }

    /** Whether this player lies collapsed (the server's record). */
    public static boolean collapsed(Player player) {
        if (player instanceof ServerPlayer) return COLLAPSED.containsKey(player.getUUID());
        return player.isLocalPlayer() && ClientKnockdown.collapsed();
    }

    private static boolean any(List<String> types, DamageSource source) {
        var key = source.typeHolder().unwrapKey().orElse(null);
        if (key == null) return false;
        String id = key.location().toString();
        for (String type : types) if (type.equals(id)) return true;
        return false;
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.knockdown." + key), true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Integer start = COLLAPSED.get(player.getUUID());
        if (start == null) {
            moveModifier(player, false);
            return;
        }
        if (!player.isAlive()) {
            COLLAPSED.remove(player.getUUID());
            moveModifier(player, false);
            return;
        }
        var c = HealthConfig.get().collapse;
        int lying = player.server.getTickCount() - start;
        boolean better = lying >= c.minSeconds * 20 && (player.getHealth() >= c.upAt || lying >= c.maxSeconds * 20);
        // up, unless a low ceiling keeps it crawling (it stands as soon as it is out from under it)
        if (better && !FallLanding.fallingOver(player)
                && player.level().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7))) {
            COLLAPSED.remove(player.getUUID());
            moveModifier(player, false);
            if (!LightningInjury.down(player)) {
                player.setForcedPose(null);
                FallLanding.getUp(player);
            }
            message(player, "up");
            return;
        }
        if (player.getForcedPose() != Pose.SWIMMING) player.setForcedPose(Pose.SWIMMING);
        if (player.isSprinting()) player.setSprinting(false);
        moveModifier(player, true);
    }

    private static void moveModifier(ServerPlayer player, boolean on) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier old = speed.getModifier(MOVE_ID);
        double amount = HealthConfig.get().collapse.crawl - 1;
        if (!on) {
            if (old != null) speed.removeModifier(MOVE_ID);
        } else if (old == null || Math.abs(old.getAmount() - amount) > 1e-4) {
            if (old != null) speed.removeModifier(MOVE_ID);
            speed.addTransientModifier(new AttributeModifier(MOVE_ID, "AFL collapse", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** Not saved: one logging out lying stands up on its own (no forced pose is kept). */
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (COLLAPSED.remove(event.getEntity().getUUID()) != null && event.getEntity() instanceof ServerPlayer player) {
            moveModifier(player, false);
            if (!LightningInjury.down(player)) player.setForcedPose(null);
        }
    }

    /** A death ends it (the next survivor is a new body, it does not start collapsed: the client clears its own, FallLandingClient). */
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (COLLAPSED.remove(event.getEntity().getUUID()) != null && event.getEntity() instanceof ServerPlayer player)
            moveModifier(player, false);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (COLLAPSED.remove(event.getEntity().getUUID()) != null && event.getEntity() instanceof ServerPlayer player) {
            moveModifier(player, false);
            if (!LightningInjury.down(player)) player.setForcedPose(null);
        }
    }

    /** No jumping collapsed. Both sides: the client moves its own player. */
    @SubscribeEvent
    public static void jump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isPassenger() || !collapsed(player)) return;
        Vec3 v = player.getDeltaMovement();
        if (v.y > 0) player.setDeltaMovement(v.x, 0, v.z);
    }
}
