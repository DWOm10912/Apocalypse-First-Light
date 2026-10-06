package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.fluid.FuelLeaks;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A bullet stopped by a block (NativeGunShot#trace, 2026-10-05, docs/native_guns/native_bullet_holes_v1.md): a fuel
 * container gets a hole that may leak (fluid/FuelLeaks, docs/gameplay/fuel_fire_v1.md); off steel, sparks fly now and
 * then (FuelLeaks.SPARK_CHANCE), and a spark sometimes sets gasoline alight (SPARK_IGNITES). Every client watching the
 * block is told where it struck (and the surface's normal: a hit mesh's own, docs/rendering/mesh_hit_runtime_v1.md) and whether it sparked, for the bullet hole decal, the dust and the sparks
 * (client/BulletHoles, client/FireFx). A hole in a fuel container is drawn from the synced leak instead (it lasts as long
 * as the hole does).
 */
public final class BulletImpacts {
    private BulletImpacts() {
    }

    public static void onBlock(ServerPlayer shooter, BlockHitResult hit) {
        ServerLevel level = shooter.serverLevel();
        BlockPos pos = hit.getBlockPos();
        if (!level.isLoaded(pos)) return;
        Vec3 normal = com.antaurora.apofirstlight.meshhit.MeshBlockHitResult.normalOf(hit);   // a hit mesh's own, else the face's
        boolean holed = FuelLeaks.bullet(shooter, hit);
        boolean spark = FuelLeaks.metal(level.getBlockState(pos).getSoundType(level, pos, shooter)) && level.random.nextFloat() < FuelLeaks.SPARK_CHANCE;
        if (spark && level.random.nextFloat() < FuelLeaks.SPARK_IGNITES) {
            Vec3 p = hit.getLocation().add(normal.scale(0.05));
            FuelLeaks.get(level).spark(level, p);
        }
        AflNetwork.sendBulletImpact(level, hit.getLocation(), hit.getDirection(), normal, pos, holed, spark);
    }
}
