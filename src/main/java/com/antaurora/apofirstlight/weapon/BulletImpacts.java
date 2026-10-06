package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.fluid.FuelLeaks;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A bullet stopped by a block (NativeGunShot#trace, 2026-10-05, docs/native_guns/native_bullet_holes_v1.md): a fuel
 * container gets a hole that may leak, steel sometimes sparks (fluid/FuelLeaks, docs/gameplay/fuel_fire_v1.md); every
 * client watching the block is told where it struck, for the bullet hole decal and the dust (client/BulletHoles). A hole
 * in a fuel container is drawn from the synced leak instead (it lasts as long as the hole does).
 */
public final class BulletImpacts {
    private BulletImpacts() {
    }

    public static void onBlock(ServerPlayer shooter, BlockHitResult hit) {
        if (!shooter.serverLevel().isLoaded(hit.getBlockPos())) return;
        boolean holed = FuelLeaks.bullet(shooter, hit);
        AflNetwork.sendBulletImpact(shooter.serverLevel(), hit.getLocation(), hit.getDirection(), hit.getBlockPos(), holed);
    }
}
