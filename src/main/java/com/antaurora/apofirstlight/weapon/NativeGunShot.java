package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseSystem;
import com.antaurora.apofirstlight.noise.NoiseType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Called only after the authoritative ammo debit. No client-supplied origin, aim or hit. */
public final class NativeGunShot {
    public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("apocalypse_firstlight", "native_bullet"));
    public record Hit(Entity entity, Vec3 point, boolean head) {}
    private NativeGunShot() {}

    public static double damageAt(NativeGunDefinition d, double distance) {
        double t = distance <= d.falloffStart() ? 0 : d.maxRange() == d.falloffStart() ? 1
                : Math.max(0, Math.min(1, (distance - d.falloffStart()) / (d.maxRange() - d.falloffStart())));
        return d.baseDamage() * (1 - t * (1 - d.minimumDamageMultiplier()));
    }

    public static Vec3 spread(Vec3 forward, double degrees, net.minecraft.util.RandomSource random) {
        Vec3 w = forward.normalize();
        Vec3 u = w.cross(Math.abs(w.y) > .99 ? new Vec3(1,0,0) : new Vec3(0,1,0)).normalize();
        Vec3 v = w.cross(u);
        double cos = 1 - random.nextDouble() * (1 - Math.cos(Math.toRadians(degrees)));
        double sin = Math.sqrt(Math.max(0, 1 - cos*cos)), phi = random.nextDouble() * Math.PI * 2;
        return w.scale(cos).add(u.scale(sin*Math.cos(phi))).add(v.scale(sin*Math.sin(phi))).normalize();
    }

    public static Hit trace(ServerPlayer shooter, Vec3 start, Vec3 direction, double range) {
        var level = shooter.serverLevel();
        Vec3 end = start.add(direction.normalize().scale(range));
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        double closest = start.distanceToSqr(block.getLocation());
        Entity target = null;
        boolean head = false;
        Vec3 point = block.getLocation();
        for (Entity entity : level.getEntities(shooter, new AABB(start, end).inflate(1),
                e -> !e.isSpectator() && e.isAlive() && e.isPickable() && !e.isPassengerOfSameVehicle(shooter))) {
            var bounds = entity.getBoundingBox();
            var intersection = NativeHeadshots.intersect(bounds, NativeHeadshots.enabled(entity), start, end);
            if (intersection.isPresent() && start.distanceToSqr(intersection.get().point()) < closest) {
                closest = start.distanceToSqr(intersection.get().point());
                target = entity;
                point = intersection.get().point();
                head = intersection.get().head();
            }
        }
        return new Hit(target, point, head);
    }

    public static Hit execute(ServerPlayer shooter, NativeGunDefinition d) {
        Vec3 start = shooter.getEyePosition();
        double spreadDegrees = NativeStanceAccuracy.evaluate(shooter, d).finalDegrees();
        Hit hit = trace(shooter, start, spread(shooter.getLookAngle(), spreadDegrees, shooter.getRandom()), d.maxRange());
        if (hit.entity() != null && (!(hit.entity() instanceof net.minecraft.world.entity.player.Player player)
                || shooter.canHarmPlayer(player))) {
            var type = shooter.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BULLET);
            boolean damaged = hit.entity().hurt(new DamageSource(type, shooter),
                    (float)(damageAt(d, start.distanceTo(hit.point())) * (hit.head() ? NativeHeadshots.MULTIPLIER.get() : 1)));
            if (damaged && hit.entity() instanceof net.minecraft.world.entity.LivingEntity)
                com.antaurora.apofirstlight.network.AflNetwork.sendNativeHit(shooter, hit.head());
        }
        var noise=NativeGunNoise.resolve(shooter.getMainHandItem(),d);
        NoiseSystem.emit(new NoiseEvent(shooter, start, NoiseType.GUNSHOT, shooter.level().getGameTime(),
                d.id(), noise.radius()), shooter.serverLevel());
        if (d.gunshotTinnitus())
            com.antaurora.apofirstlight.tinnitus.GunshotExposureTracker.onGunshot(
                    shooter.serverLevel(), shooter, start, noise.radius(), noise.suppressed());
        return hit;
    }
}
