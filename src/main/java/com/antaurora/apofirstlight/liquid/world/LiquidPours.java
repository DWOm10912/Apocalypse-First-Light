package com.antaurora.apofirstlight.liquid.world;

import com.antaurora.apofirstlight.liquid.LiquidEngine;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;

/**
 * Players pouring water ({@code /afl dev water pour}; docs/gameplay/water_falling_v1_design.md 3.3): each tick the
 * tick's share of the flow leaves the hand as engine parcels (LiquidEngine#launch, at most 1 L each, M2 of
 * docs/gameplay/water_mesh_parcels_v1_design.md). They are on the engine's account from the moment they leave, fly the
 * same arc the clients draw (LiquidPackets.Pour) and add to the water only where they come down, so a pour cut short
 * still lands what is in the air.
 */
final class LiquidPours {
    /** How fast it leaves the hand, m/s (a bucket tipped over). */
    static final double SPEED = 1.5;

    static final class Pour {
        final ServerPlayer player;
        final double litresPerSecond;
        long left, poured;

        Pour(ServerPlayer player, long ml, double litresPerSecond) {
            this.player = player;
            this.left = ml;
            this.litresPerSecond = litresPerSecond;
        }

        boolean done() {
            return left <= 0;
        }
    }

    private LiquidPours() {
    }

    /** Where the stream leaves the hand and how: the same as ClientFalls draws it. */
    static Vec3 hand(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        return p.getEyePosition().add(look.scale(0.45)).add(right.scale(0.25)).add(0, -0.3, 0);
    }

    static void start(LiquidWorld.State st, ServerPlayer player, long ml, double litresPerSecond) {
        Pour old = st.pours.get(player.getId());
        if (old != null) old.left = 0;
        Pour p = new Pour(player, ml, litresPerSecond);
        st.pours.put(player.getId(), p);
        AflNetwork.sendTrackingAndSelf(player, new LiquidPackets.Pour(player.getId(), (float) litresPerSecond, (float) SPEED));
    }

    static boolean stop(LiquidWorld.State st, ServerPlayer player) {
        Pour p = st.pours.get(player.getId());
        if (p == null || p.left <= 0) return false;
        p.left = 0;
        AflNetwork.sendTrackingAndSelf(player, new LiquidPackets.Pour(player.getId(), 0, 0));
        return true;
    }

    static void tick(LiquidWorld.State st) {
        if (st.pours.isEmpty()) return;
        ServerLevel level = st.level;
        LiquidEngine e = st.engine;
        for (Iterator<Pour> it = st.pours.values().iterator(); it.hasNext(); ) {
            Pour p = it.next();
            boolean gone = p.player.isRemoved() || p.player.level() != level;
            if (gone && p.left > 0) {
                p.left = 0;
                AflNetwork.sendTrackingAndSelf(p.player, new LiquidPackets.Pour(p.player.getId(), 0, 0));
            }
            if (p.left > 0) {
                long ml = Math.min(p.left, Math.max(1, Math.round(p.litresPerSecond * 1000 / 20)));
                p.left -= ml;
                Vec3 at = hand(p.player), v = p.player.getLookAngle().scale(SPEED);
                int n = (int) Math.max(1, (ml + LiquidEngine.PARCEL_ML - 1) / LiquidEngine.PARCEL_ML);
                for (int i = 0; i < n; i++) {
                    long part = ml / n + (i < ml % n ? 1 : 0);
                    if (e.launch(at.x, at.y, at.z, v.x, v.y, v.z, part, p.player.getId())) p.poured += part;
                }
                if (p.left <= 0) AflNetwork.sendTrackingAndSelf(p.player, new LiquidPackets.Pour(p.player.getId(), 0, 0));
            }
            if (p.done()) it.remove();
        }
    }
}
