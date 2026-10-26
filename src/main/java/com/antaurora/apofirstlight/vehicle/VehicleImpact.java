package com.antaurora.apofirstlight.vehicle;

import com.antaurora.apofirstlight.health.BodyPart;
import com.antaurora.apofirstlight.health.Fractures;
import com.antaurora.apofirstlight.health.HealthConfig;
import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.noise.movement.MovementNoiseEvents;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.antaurora.apofirstlight.survivor.LiveRagdolls;
import com.antaurora.apofirstlight.vehicle.physics.VehiclePhysics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vehicle impact V1 (docs/gameplay/injury_knockdown_v1_plan.md 5, user 2026-10-22 「现在车，没有撞击伤害」): on the server,
 * each tick after the truck moved and before it pushes bodies aside (PickupTruckEntity#tick), every living body its boxes
 * reach (inflate + inflate_per_speed × speed blocks) is hit when the truck closes on it fast enough: the speed of the
 * truck's point at the body (centre of mass velocity + spin × arm) less the body's own, along the level line from the
 * centre of mass to the body, at least min_speed m/s; once per cooldown ticks. It loses the vehicle.damage curve's health
 * (of 100; passed / 5, the vanilla scale, so armour counts and a 20 HP zombie dies at about 50 km/h), is thrown along the
 * hit (throw_factor × closing × M / (M + m), up lift × closing at most lift_max), thumps down, and a player rolls a leg
 * (vehicle.leg; PlayerHealth then knocks it down: Knockdown, the vehicle type always does). A death keeps the throw for
 * the ragdoll's push ({@link #thrown}). Below vehicle.knock_speed (a truck backing up at walking pace, user 2026-10-22
 * 「倒车撞不到人」: it used to start at 1.5 m/s and slow reversing only pushed bodies aside) it is a bump: hurt a little,
 * a flinch and the shove, never thrown down ({@link #bump}). Not passengers, spectators or creative players. The truck itself is not slowed
 * (its physics runs on the driver's client).
 */
public final class VehicleImpact {
    public static final ResourceKey<DamageType> VEHICLE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("apocalypse_firstlight", "vehicle"));
    /** Each body's last hit (game ticks), for the cooldown. */
    private static final Map<UUID, Long> LAST = new HashMap<>();
    /** The throw of a hit being dealt (m/s, world), while it is (a death in it reads it: SurvivorRagdolls). */
    private static final Map<UUID, Vec3> THROWN = new HashMap<>();
    /** The bodies whose hit being dealt is a bump (closing below vehicle.knock_speed). */
    private static final Set<UUID> BUMPED = new HashSet<>();

    private VehicleImpact() {
    }

    static void tick(PickupTruckEntity truck, VehiclePhysics physics) {
        if (!(truck.level() instanceof ServerLevel level)) return;
        var c = HealthConfig.get().vehicle;
        double speed = physics.v.length() + physics.w.length() * 3;
        if (speed < c.minSpeed) return;
        long now = level.getGameTime();
        if (now % 200 == 0) LAST.values().removeIf(t -> now - t > c.cooldown);
        double reach = c.inflate + physics.v.length() * c.inflatePerSpeed;
        List<AABB> boxes = truck.collisionBoxes();
        AABB area = truck.collisionBounds().inflate(reach);
        for (Entity e : level.getEntities(truck, area, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !truck.passesThrough(e))) {
            LivingEntity body = (LivingEntity) e;
            if (body instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            AABB box = body.getBoundingBox();
            boolean touches = false;
            for (AABB b : boxes) if (b.inflate(reach).intersects(box)) { touches = true; break; }
            if (!touches) continue;
            Long last = LAST.get(body.getUUID());
            if (last != null && now - last < c.cooldown) continue;
            Vector3d com = physics.com;
            double nx = body.getX() - com.x, nz = body.getZ() - com.z, n = Math.sqrt(nx * nx + nz * nz);
            if (n < 1e-3) continue;
            nx /= n;
            nz /= n;
            // the truck's point at the body: centre of mass velocity + spin × arm
            double rx = body.getX() - com.x, ry = body.getY() + body.getBbHeight() * 0.5 - com.y, rz = body.getZ() - com.z;
            Vector3d w = physics.w, v = physics.v;
            double px = v.x + (w.y * rz - w.z * ry), pz = v.z + (w.x * ry - w.y * rx);
            // the body's own (a server player's is not kept: the client moves it)
            Vec3 own = body instanceof Player ? Vec3.ZERO : body.getDeltaMovement().scale(20);
            double closing = (px - own.x) * nx + (pz - own.z) * nz;
            if (closing < c.minSpeed) continue;
            LAST.put(body.getUUID(), now);
            hit(level, truck, body, closing, nx, nz);
        }
    }

    private static void hit(ServerLevel level, PickupTruckEntity truck, LivingEntity body, double closing, double nx, double nz) {
        var c = HealthConfig.get().vehicle;
        double health = c.damageAt(closing);
        double m = body.getBbWidth() * body.getBbWidth() * body.getBbHeight() * c.density;
        double out = c.throwFactor * closing * c.mass / (c.mass + m), up = Math.min(c.liftMax, c.lift * closing);
        Vec3 thrown = new Vec3(nx * out, up, nz * out);
        LivingEntity driver = truck.getControllingPassenger();
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(VEHICLE),
                truck, driver != null ? driver : truck);
        boolean lying = body instanceof ServerPlayer p && LiveRagdolls.active(p);
        THROWN.put(body.getUUID(), thrown);
        if (closing < c.knockSpeed) BUMPED.add(body.getUUID());
        try {
            if (health > 0) body.hurt(source, (float) (health / 5));
        } finally {
            THROWN.remove(body.getUUID());
            BUMPED.remove(body.getUUID());
        }
        RangedSound.play(level, body.position(), AflSounds.BODY_FALL.get(), SoundSource.PLAYERS, MovementNoiseEvents.HEAVY_LANDING_RADIUS,
                1.0F, 0.95F + body.getRandom().nextFloat() * 0.1F);
        if (!body.isAlive()) return;
        if (body instanceof ServerPlayer p && LiveRagdolls.active(p)) {
            // a living ragdoll (survivor/LiveRagdolls; this hit may have started it, with this throw): the body takes it
            if (lying) LiveRagdolls.push(p, thrown);
        } else {
            // blocks per tick; a player's own client takes it (hurtMarked sends it)
            body.setDeltaMovement(thrown.scale(1 / 20.0));
            body.hurtMarked = true;
            body.hasImpulse = true;
        }
        if (body instanceof ServerPlayer player)
            Fractures.roll(player, BodyPart.randomLeg(player.getRandom()), Fractures.chance(c.leg, health));
    }

    /** Whether the vehicle hit this body is taking now is a bump (below vehicle.knock_speed): no throwing down (Knockdown). */
    public static boolean bump(Entity body) {
        return BUMPED.contains(body.getUUID());
    }

    /** The throw (m/s, world) of the vehicle hit this body is taking now, or null (SurvivorRagdolls: a death's push). */
    @Nullable
    public static Vec3 thrown(Entity body) {
        return THROWN.get(body.getUUID());
    }
}
