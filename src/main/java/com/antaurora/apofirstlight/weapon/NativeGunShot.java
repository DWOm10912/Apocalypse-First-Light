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
    public record ShotResult(Hit representative, java.util.List<Vec3> endpoints) {
        public ShotResult { endpoints = java.util.List.copyOf(endpoints); }
    }
    public static final int MAX_PASS_THROUGH_BREAKABLE_BLOCKS = 16;
    public static final double EPSILON = .01;
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
        Vec3 forward = direction.normalize();
        // Unloaded cells are stopping boundaries, never a request to generate/load terrain.
        net.minecraft.world.level.BlockGetter loaded = new net.minecraft.world.level.BlockGetter() {
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos p) { return null; }
            public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos p) {
                return level.hasChunkAt(p) ? level.getBlockState(p) : net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState();
            }
            public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos p) {
                return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
            }
            public int getHeight() { return level.getHeight(); }
            public int getMinBuildHeight() { return level.getMinBuildHeight(); }
        };
        int broken = 0;
        while (true) {
        var block = loaded.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        double closest = start.distanceToSqr(block.getLocation());
        Entity target = null;
        boolean head = false;
        Vec3 point = block.getLocation();
        for (Entity entity : level.getEntities(shooter, new AABB(start, block.getLocation()).inflate(1),
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
        if (target != null || block.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
            return new Hit(target, point, head);
        var pos = block.getBlockPos();
        if (!level.hasChunkAt(pos) || broken >= MAX_PASS_THROUGH_BREAKABLE_BLOCKS
                || BulletBlockInteraction.resolve(level.getBlockState(pos)) != BulletBlockInteraction.BREAK_AND_PASS
                || !BulletBlockInteraction.breakGlass(shooter, pos)) return new Hit(null, point, false);
        broken++;
        // Fixed endpoint preserves original range including the epsilon step.
        double remaining = point.distanceTo(end);
        if (remaining <= EPSILON) return new Hit(null, end, false);
        start = point.add(forward.scale(EPSILON));
        }
    }

    private static final class PelletDamage {
        double amount;
        boolean head;
    }

    public static Hit execute(ServerPlayer shooter, NativeGunDefinition d) {
        return execute(shooter, d, false);
    }

    public static Hit execute(ServerPlayer shooter, NativeGunDefinition d, boolean aiming) {
        return executeWithTrajectories(shooter, d, aiming).representative();
    }

    public static ShotResult executeWithTrajectories(ServerPlayer shooter, NativeGunDefinition d, boolean aiming) {
        Vec3 start = shooter.getEyePosition();
        var stance = NativeStanceAccuracy.evaluate(shooter, d);
        double spreadDegrees = aiming && d.spreadDegrees() > 0
                ? stance.finalDegrees() * d.adsSpreadDegrees() / d.spreadDegrees() : stance.finalDegrees();
        var damageByEntity = new java.util.IdentityHashMap<Entity, PelletDamage>();
        var endpoints = new java.util.ArrayList<Vec3>(d.pelletsPerShot());
        Hit representative = null;
        for (int pellet = 0; pellet < d.pelletsPerShot(); pellet++) {
            // Each pellet independently samples the same established cone, traces and resolves hit part/range.
            Hit hit = trace(shooter, start,
                    spread(shooter.getLookAngle(), spreadDegrees, shooter.getRandom()), d.maxRange());
            endpoints.add(hit.point());
            if (representative == null || representative.entity() == null && hit.entity() != null) representative = hit;
            if (hit.entity() != null && (!(hit.entity() instanceof net.minecraft.world.entity.player.Player player)
                    || shooter.canHarmPlayer(player))) {
                var accumulated = damageByEntity.computeIfAbsent(hit.entity(), ignored -> new PelletDamage());
                accumulated.amount += damageAt(d, start.distanceTo(hit.point()))
                        * (hit.head() ? d.headshotMultiplier() : 1);
                accumulated.head |= hit.head();
            }
        }
        // Apply once per target so Minecraft's same-tick hurt immunity cannot discard later pellets.
        if (!damageByEntity.isEmpty()) {
            var type = shooter.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BULLET);
            for (var entry : damageByEntity.entrySet()) {
                boolean damaged = entry.getKey().hurt(new DamageSource(type, shooter), (float)entry.getValue().amount);
                if (damaged && entry.getKey() instanceof net.minecraft.world.entity.LivingEntity)
                    com.antaurora.apofirstlight.network.AflNetwork.sendNativeHit(shooter, entry.getValue().head);
            }
        }
        var noise=NativeGunNoise.resolve(shooter.getMainHandItem(),d);
        NoiseSystem.emit(new NoiseEvent(shooter, start, NoiseType.GUNSHOT, shooter.level().getGameTime(),
                d.id(), noise.radius()), shooter.serverLevel());
        if (d.gunshotTinnitus())
            com.antaurora.apofirstlight.tinnitus.GunshotExposureTracker.onGunshot(
                    shooter.serverLevel(), shooter, start, noise.radius(), noise.suppressed());
        return new ShotResult(representative, endpoints);
    }
}
