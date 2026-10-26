package com.antaurora.apofirstlight.liquid.world;

import com.antaurora.apofirstlight.fluid.LiquidJet;
import com.antaurora.apofirstlight.liquid.LiquidCell;
import com.antaurora.apofirstlight.liquid.LiquidColumn;
import com.antaurora.apofirstlight.liquid.LiquidEngine;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Players pouring water ({@code /afl dev water pour}; docs/gameplay/water_falling_v1_design.md 3.3): a LiquidJet from the
 * hand, one parcel a tick carrying its share of the flow, landing in the AFL water (on a surface it comes down to, else
 * in the open layer where it meets a block) and only then counted as poured. The clients draw the same stream
 * (LiquidPackets.Pour). Water still in the air when the pour is cut short (the player leaves, the world stops) is lost:
 * under a second's worth, and never in the engine's account.
 */
final class LiquidPours {
    /** How fast it leaves the hand, m/s (a bucket tipped over). */
    static final double SPEED = 1.5;

    static final class Pour {
        final ServerPlayer player;
        final LiquidJet jet = new LiquidJet(LiquidJet.EARTH_GRAVITY, 6);
        final Map<Long, Long> carried = new HashMap<>();
        final double litresPerSecond;
        long left, poured, lost;

        Pour(ServerPlayer player, long ml, double litresPerSecond) {
            this.player = player;
            this.left = ml;
            this.litresPerSecond = litresPerSecond;
        }

        boolean done() {
            return left <= 0 && jet.isEmpty();
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
        LiquidJet.Surface surface = (x, y, z) -> {
            LiquidColumn col = e.column(Mth.floor(x), Mth.floor(z));
            LiquidCell c = col == null ? null : col.cellAt(y);
            return c != null && c.volume() > 0 && y <= c.level() ? c.level() : Double.NaN;
        };
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
                p.carried.put(p.jet.nextId(), ml);
                p.jet.emit(hand(p.player), p.player.getLookAngle().scale(SPEED), 0);
                if (p.left <= 0) AflNetwork.sendTrackingAndSelf(p.player, new LiquidPackets.Pour(p.player.getId(), 0, 0));
            }
            p.jet.tick(level, p.player, (parcel, hit) -> {
                // just off the face it met, into the open
                land(e, p, parcel, hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.05)));
            }, surface, (parcel, at) -> land(e, p, parcel, at.add(0, -0.01, 0)));
            // parcels that aged out or fell out of the world
            if (!p.carried.isEmpty()) {
                long oldest = p.jet.parcels().isEmpty() ? p.jet.nextId() : p.jet.parcels().getFirst().id;
                for (Iterator<Map.Entry<Long, Long>> c = p.carried.entrySet().iterator(); c.hasNext(); ) {
                    Map.Entry<Long, Long> en = c.next();
                    if (en.getKey() < oldest) {
                        p.lost += en.getValue();
                        c.remove();
                    }
                }
            }
            if (p.done()) it.remove();
        }
    }

    private static void land(LiquidEngine e, Pour p, LiquidJet.Parcel parcel, Vec3 at) {
        Long ml = p.carried.remove(parcel.id);
        if (ml == null) return;
        int x = Mth.floor(at.x), z = Mth.floor(at.z);
        if (e.add(x, at.y, z, ml) || e.add(x, at.y + 0.1, z, ml)) p.poured += ml;
        else p.lost += ml;
    }
}
