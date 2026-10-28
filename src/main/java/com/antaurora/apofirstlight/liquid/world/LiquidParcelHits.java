package com.antaurora.apofirstlight.liquid.world;

import com.antaurora.apofirstlight.liquid.LiquidEngine;
import com.antaurora.apofirstlight.liquid.LiquidParcel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Parcels in the air meeting players (docs/gameplay/water_mesh_parcels_v1_design.md B2, M2): the water pushes them by its
 * momentum (its mass against the player's, along the velocity it has on them) and then drops straight down to their feet,
 * where it lands. Wetting is the body-wetness system's (B): it subscribes with {@link #listener}. Players only for now.
 */
public final class LiquidParcelHits {
    /** A player's mass for the push, kg (with what they carry). */
    static final double PLAYER_KG = 80;
    /** A parcel does not hit the one it came from for this long (s): a pour leaves from in front of the hand. */
    static final double OWN_GRACE = 0.5;

    /** Someone was hit by a parcel of ml at speed m/s (relative to them). */
    public interface Listener {
        void hit(ServerPlayer player, long ml, double speed);
    }

    /** The body-wetness system's hook (B); null until it subscribes. */
    public static volatile Listener listener;

    private LiquidParcelHits() {
    }

    static void tick(ServerLevel level, LiquidEngine e) {
        if (e.parcels().isEmpty() || level.players().isEmpty()) return;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            AABB box = player.getBoundingBox().inflate(0.05);
            Vec3 own = player.getDeltaMovement().scale(20);
            double dvx = 0, dvy = 0, dvz = 0;
            for (LiquidParcel p : e.parcels()) {
                if (p.hit() || p.ml() <= 0) continue;
                if (p.source() == player.getId() && p.age() < OWN_GRACE) continue;
                if (!box.contains(p.x(), p.y(), p.z())) continue;
                double kg = p.ml() / 1000.0;
                double rx = p.vx() - own.x, ry = p.vy() - own.y, rz = p.vz() - own.z;
                double share = kg / (PLAYER_KG + kg);
                dvx += share * rx;
                dvy += share * ry;
                dvz += share * rz;
                Listener l = listener;
                if (l != null) l.hit(player, p.ml(), Math.sqrt(rx * rx + ry * ry + rz * rz));
                e.stopParcel(p, p.x(), Math.max(player.getY() + 0.02, Math.min(p.y(), player.getY() + 0.3)), p.z());
            }
            if (dvx != 0 || dvy != 0 || dvz != 0) {
                // m/s to blocks a tick; standing on the ground takes the downward part
                player.push(dvx / 20, (player.onGround() ? Math.max(0, dvy) : dvy) / 20, dvz / 20);
                player.hurtMarked = true;
            }
        }
    }
}
