package com.antaurora.apofirstlight.dev.authoring.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Light rechecks for bridge edits (2026-10-08, docs/dev/minecraft_authoring_mcp_v1.md). The Fuel Stop A1 store kept the
 * block light of ceiling lights removed by a WorldEdit clear on 2026-10-07 until the user broke the new ones and found the
 * store still bright, so WorldEdit edits did not leave the light engine consistent there. Every bridge WorldEdit write,
 * undo and redo now rechecks its cells, and relight_region rechecks a whole plot: the light engine zeroes every checked
 * cell whose stored light is not backed by its own emission before it spreads light again (ThreadedLevelLightEngine runs up
 * to 1000 checks per batch; cells refilled from a later batch are cleared again when that batch runs), so unsupported
 * light anywhere inside the checked box goes away and real sources light it again.
 * <p>
 * Clients: a light change the server makes without a block change reaches only players at the edge of their view
 * distance (ChunkHolder#broadcastChanges sends light updates to getPlayers(pos, true)); a player standing in front of the
 * store kept the old light on screen after the first relight. So the checked chunks' full light is also sent to every
 * player who sees them, 2 s and 5 s later, once the light thread has run the checks.
 */
final class BridgeLighting {
    static final int MAX_MARGIN = 16;
    private static final int[] PUSH_DELAYS = {40, 100};

    private record Push(ServerLevel level, Set<ChunkPos> chunks, int at) {}
    private static final List<Push> PENDING = new ArrayList<>();
    private static boolean listening;

    private BridgeLighting() {}

    /** Queues a light check for every loaded cell of the bounds grown by {@code margin} (clipped to the build height). */
    static int recheck(ServerLevel level, BridgeBounds b, int margin) {
        var engine = level.getChunkSource().getLightEngine();
        int y0 = Math.max(level.getMinBuildHeight(), b.min().getY() - margin), y1 = Math.min(level.getMaxBuildHeight() - 1, b.max().getY() + margin);
        int count = 0;
        var chunks = new LinkedHashSet<ChunkPos>();
        for (var pos : BlockPos.betweenClosed(b.min().getX() - margin, y0, b.min().getZ() - margin, b.max().getX() + margin, y1, b.max().getZ() + margin)) {
            if (!level.hasChunkAt(pos)) continue;
            engine.checkBlock(pos);
            chunks.add(new ChunkPos(pos));
            count++;
        }
        engine.tryScheduleUpdate();
        if (!chunks.isEmpty()) push(level, chunks);
        return count;
    }

    private static void push(ServerLevel level, Set<ChunkPos> chunks) {
        int now = level.getServer().getTickCount();
        for (int delay : PUSH_DELAYS) PENDING.add(new Push(level, chunks, now + delay));
        if (!listening) {
            listening = true;
            MinecraftForge.EVENT_BUS.addListener(BridgeLighting::onServerTick);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        int now = event.getServer().getTickCount();
        PENDING.removeIf(p -> {
            if (p.level().getServer() != event.getServer()) return true;   // a closed world
            if (now < p.at()) return false;
            send(p.level(), p.chunks());
            return true;
        });
    }

    /** The chunks' full sky and block light to every player who sees them. */
    private static void send(ServerLevel level, Set<ChunkPos> chunks) {
        var engine = level.getChunkSource().getLightEngine();
        var map = level.getChunkSource().chunkMap;
        for (var pos : chunks) {
            if (!level.hasChunk(pos.x, pos.z)) continue;
            var players = map.getPlayers(pos, false);
            if (players.isEmpty()) continue;
            var packet = new ClientboundLightUpdatePacket(pos, engine, null, null);
            for (var player : players) player.connection.send(packet);
        }
    }
}
