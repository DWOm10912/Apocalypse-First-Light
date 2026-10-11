package com.antaurora.apofirstlight.dev.terrainsmooth;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Terrain smoothing P1 diagnostics (development runs only): the local player's movement inside the test area, tick by tick,
 * in a ring of the last 40 ticks. Written to the log (latest.log, "[AFL terrain smooth probe]") when the player is stuck
 * (moving keys held, under 1 block in 20 ticks) or the server moved the player (a correction between two ticks), at most
 * once per 5 seconds, and on /afl dev terrain_smooth probe. Each record also lists the blocks under and around the feet with
 * the slope the client and the server compute for them and whether the player's box cuts into their collision boxes.
 */
final class SmoothProbe {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int RING = 40, STUCK_WINDOW = 20;
    private static final ArrayDeque<String> ticks = new ArrayDeque<>();
    private static final ArrayDeque<double[]> moves = new ArrayDeque<>();
    private static Vec3 lastEnd;
    private static long tick, lastDump = -1000, events;
    private static int corrections;
    private static String correction = "";

    private SmoothProbe() {}

    static long events() { return events; }

    /** Before the player's tick: a position change since the end of the last tick came from outside (a server correction). */
    static void start(LocalPlayer p) {
        Vec3 now = p.position();
        if (lastEnd != null && now.distanceToSqr(lastEnd) > 1.0E-10) {
            corrections++;
            correction = String.format(Locale.ROOT, " MOVED-BETWEEN-TICKS by %+.4f %+.4f %+.4f", now.x - lastEnd.x, now.y - lastEnd.y, now.z - lastEnd.z);
        }
    }

    /** After the player's tick (and the lift). */
    static void end(LocalPlayer p, double lift) {
        tick++;
        Vec3 pos = p.position(), d = p.getDeltaMovement();
        boolean keys = p.input != null && (Math.abs(p.input.forwardImpulse) > 0.01F || Math.abs(p.input.leftImpulse) > 0.01F);
        double water = p.getFluidTypeHeight(net.minecraftforge.common.ForgeMod.WATER_TYPE.get());
        String line = String.format(Locale.ROOT, "t%d pos %.4f %.4f %.4f d %+.4f %+.4f %+.4f ground %s hcol %s vcol %s water %s %.3f pose %s%s%s keys %s%s%s",
                tick, pos.x, pos.y, pos.z, d.x, d.y, d.z, p.onGround(), p.horizontalCollision, p.verticalCollision, p.isInWater(), water,
                p.getPose(), p.isCrouching() ? " crouch" : "", p.isSprinting() ? " sprint" : "", keys,
                lift > 0 ? String.format(Locale.ROOT, " LIFT %.4f", lift) : lift < 0 ? String.format(Locale.ROOT, " LIFT-BLOCKED %.4f", -lift) : "", correction);
        boolean corrected = !correction.isEmpty();
        correction = "";
        ticks.addLast(line);
        moves.addLast(new double[]{pos.x, pos.z, keys ? 1 : 0});
        while (ticks.size() > RING) ticks.removeFirst();
        while (moves.size() > RING) moves.removeFirst();
        lastEnd = p.position();
        String reason = corrected ? "server corrected the position" : stuck() ? "stuck: moving keys held, under 1 block in " + STUCK_WINDOW + " ticks" : null;
        if (reason != null && tick - lastDump >= 100) dump(p, reason);
    }

    private static boolean stuck() {
        if (moves.size() < STUCK_WINDOW) return false;
        double[][] m = moves.toArray(new double[0][]);
        int from = m.length - STUCK_WINDOW;
        for (int i = from; i < m.length; i++) if (m[i][2] == 0) return false;
        return Math.hypot(m[m.length - 1][0] - m[from][0], m[m.length - 1][1] - m[from][1]) < 1.0;
    }

    static void reset() {
        ticks.clear();
        moves.clear();
        lastEnd = null;
        corrections = 0;
        correction = "";
    }

    /** Write the ring and the blocks around the feet (client now, server on its own thread) to the log. */
    static void dump(LocalPlayer p, String reason) {
        lastDump = tick;
        long id = ++events;
        Level level = p.level();
        AABB box = p.getBoundingBox();
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "[AFL terrain smooth probe] event %d: %s; server corrections so far %d; box %.4f %.4f %.4f .. %.4f %.4f %.4f%n",
                id, reason, corrections, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        for (String t : ticks) out.append("  ").append(t).append('\n');
        out.append("  client blocks:\n");
        blocks(level, p, box, SmoothWorld.CLIENT, out);
        LOG.info(out.toString());
        SmoothPrototype.message("probe event " + id + " written to the log (" + reason + ")");
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        var uuid = p.getUUID();
        var dim = level.dimension();
        server.execute(() -> {
            ServerLevel sl = server.getLevel(dim);
            var sp = server.getPlayerList().getPlayer(uuid);
            if (sl == null || sp == null) return;
            StringBuilder s = new StringBuilder();
            AABB sb = sp.getBoundingBox();
            s.append(String.format(Locale.ROOT, "[AFL terrain smooth probe] event %d, server side: player %.4f %.4f %.4f ground %s; at the client's box:%n",
                    id, sp.getX(), sp.getY(), sp.getZ(), sp.onGround()));
            blocks(sl, sp, box, SmoothWorld.SERVER, s);
            s.append(String.format(Locale.ROOT, "  server box %.4f %.4f %.4f: free %s%n", sb.minX, sb.minY, sb.minZ, sl.noCollision(sp, sb.deflate(1.0E-5))));
            LOG.info(s.toString());
        });
    }

    /** Blocks in the box's columns plus one ring, from 2 below the feet to 1 above: state, slope corners, box cut-in. */
    private static void blocks(Level level, net.minecraft.world.entity.Entity e, AABB box, SmoothWorld.Config c, StringBuilder out) {
        AABB inner = box.deflate(1.0E-5);
        VoxelShape player = Shapes.create(inner);
        var pos = new BlockPos.MutableBlockPos();
        int feet = Mth.floor(box.minY);
        CollisionContext ctx = CollisionContext.of(e);
        for (int x = Mth.floor(box.minX) - 1; x <= Mth.floor(box.maxX) + 1; x++)
            for (int z = Mth.floor(box.minZ) - 1; z <= Mth.floor(box.maxZ) + 1; z++)
                for (int y = feet - 2; y <= feet + 1; y++) {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    if (s.isAir()) continue;
                    long g = SmoothWorld.geometry(level, c, x, y, z);
                    // what collision uses: the per-position cache (a stale entry would differ from the fresh rules)
                    String stale = "";
                    if (SmoothWorld.surface(s.getBlock()) && c.active() && c.contains(x, z)) {
                        long cached = c.cachedGeometry(level, pos.immutable());
                        if (cached != g) stale = String.format(Locale.ROOT, " CACHE DIFFERS (cached %x, fresh %x)", cached, g);
                    }
                    VoxelShape shape = s.getCollisionShape(level, pos, ctx);
                    boolean cut = !shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(x, y, z), player, BooleanOp.AND);
                    String slope = "";
                    if (g >= 0) {
                        float[] k = SmoothRules.corners(g);
                        slope = String.format(Locale.ROOT, " slope nw %.2f ne %.2f sw %.2f se %.2f", k[0], k[1], k[2], k[3]);
                    }
                    out.append(String.format(Locale.ROOT, "    %d %d %d %s%s shape top %.4f%s%s%s%n", x, y, z, s, slope,
                            shape.isEmpty() ? 0 : shape.max(net.minecraft.core.Direction.Axis.Y), shape.isEmpty() ? " (no collision)" : "", cut ? " CUTS INTO THE PLAYER" : "", stale));
                }
    }
}
