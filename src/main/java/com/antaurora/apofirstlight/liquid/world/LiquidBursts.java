package com.antaurora.apofirstlight.liquid.world;

import com.antaurora.apofirstlight.liquid.LiquidEngine;
import com.antaurora.apofirstlight.liquid.Orifice;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Burst pipes ({@code /afl dev water burst <mm> <bar> [seconds]}; docs/gameplay/water_mesh_parcels_v1_design.md B3, M4):
 * water forced out of a hole at a block face by pressure, as fast and as much as Orifice gives, out along the face (straight
 * up from a top face, like a broken hydrant; 20 degrees above level from a side; down from a bottom face). Each tick its
 * flow leaves as engine parcels (at most 1 L, spread over the tick and a few degrees round the jet's axis) flown with air
 * drag, so a fast jet carries as far as a real one, not twice as far. The clients draw the same jet (LiquidPackets.Burst).
 * Pipes and tanks themselves are a later feature's; this is the jet.
 */
final class LiquidBursts {
    /** Players this close are told about a burst (m). */
    static final double TELL = 128;
    /** How far round its axis the jet's parcels spread (radians, at most): a burst is no nozzle. */
    static final double SPREAD = 0.05;
    /** Rise of a jet out of a side face (radians). */
    static final double SIDE_RISE = Math.toRadians(20);

    static final class Burst {
        final int id;
        final Vec3 at, dir;
        final double speed, litresPerSecond;
        final Random spread;
        long left, out;
        final Set<UUID> told = new HashSet<>();

        Burst(int id, Vec3 at, Vec3 dir, double speed, double litresPerSecond, long ml) {
            this.id = id;
            this.at = at;
            this.dir = dir;
            this.speed = speed;
            this.litresPerSecond = litresPerSecond;
            this.left = ml;
            this.spread = new Random(id * 0x9E3779B97F4A7C15L);
        }

        LiquidPackets.Burst packet(boolean on) {
            return new LiquidPackets.Burst(id, at.x, at.y, at.z, (float) dir.x, (float) dir.y, (float) dir.z, (float) speed,
                    on ? (float) litresPerSecond : 0, (float) Orifice.JET_DRAG);
        }
    }

    private static int nextId;

    private LiquidBursts() {
    }

    /** The jet's direction out of a face. */
    static Vec3 direction(Direction face) {
        if (face == Direction.UP) return new Vec3(0, 1, 0);
        if (face == Direction.DOWN) return new Vec3(0, -1, 0);
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        return n.scale(Math.cos(SIDE_RISE)).add(0, Math.sin(SIDE_RISE), 0);
    }

    static Burst start(LiquidWorld.State st, Vec3 at, Direction face, double mm, double bar, double seconds) {
        double speed = Orifice.speed(bar), flow = Orifice.litresPerSecond(mm, bar);
        Burst b = new Burst(nextId++, at, direction(face), speed, flow, Math.round(flow * 1000 * seconds));
        st.bursts.add(b);
        tell(st.level, b, true);
        return b;
    }

    /** Stops them all; how many there were. */
    static int stop(LiquidWorld.State st) {
        int n = 0;
        for (Burst b : st.bursts) {
            if (b.left > 0) n++;
            b.left = 0;
        }
        return n;
    }

    static void tick(LiquidWorld.State st) {
        if (st.bursts.isEmpty()) return;
        LiquidEngine e = st.engine;
        for (Iterator<Burst> it = st.bursts.iterator(); it.hasNext(); ) {
            Burst b = it.next();
            if (b.left <= 0) {
                for (ServerPlayer p : st.level.players()) if (b.told.contains(p.getUUID())) AflNetwork.sendLiquid(p, b.packet(false));
                it.remove();
                continue;
            }
            if (st.level.getGameTime() % 20 == 0) tell(st.level, b, false);
            long ml = Math.min(b.left, Math.max(1, Math.round(b.litresPerSecond * 1000 / 20)));
            b.left -= ml;
            int n = (int) Math.max(1, (ml + LiquidEngine.PARCEL_ML - 1) / LiquidEngine.PARCEL_ML);
            for (int i = 0; i < n; i++) {
                long part = ml / n + (i < ml % n ? 1 : 0);
                Vec3 v = aside(b.dir, b.spread).scale(b.speed);
                if (e.launch(b.at.x, b.at.y, b.at.z, v.x, v.y, v.z, part, -1, LiquidEngine.DT * (n - i - 0.5) / n, Orifice.JET_DRAG))
                    b.out += part;
            }
        }
    }

    /** dir tipped a little round itself, at random. */
    private static Vec3 aside(Vec3 dir, Random r) {
        Vec3 u = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(u).normalize(), c = dir.cross(a);
        double t = Math.sqrt(r.nextDouble()) * SPREAD, p = r.nextDouble() * Math.PI * 2;
        return dir.add(a.scale(Math.cos(p) * t)).add(c.scale(Math.sin(p) * t)).normalize();
    }

    /** Players near it not told yet hear of it (all of them when everyone is). */
    private static void tell(ServerLevel level, Burst b, boolean everyone) {
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(b.at) > TELL * TELL) continue;
            if (!everyone && b.told.contains(p.getUUID())) continue;
            b.told.add(p.getUUID());
            AflNetwork.sendLiquid(p, b.packet(true));
        }
    }
}
