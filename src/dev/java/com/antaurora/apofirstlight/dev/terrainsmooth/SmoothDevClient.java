package com.antaurora.apofirstlight.dev.terrainsmooth;

import com.antaurora.apofirstlight.terrainsmooth.SmoothChunkData;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.antaurora.apofirstlight.terrainsmooth.client.TerrainSmoothClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Locale;

/** Client side of the development command (SmoothCommands): status, probe, refresh, smooth lighting switch. */
final class SmoothDevClient {
    private SmoothDevClient() {}

    static void handle(SmoothCommands.Msg msg) {
        if (FMLEnvironment.production) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        switch (msg.action()) {
            case SmoothCommands.STATUS -> status(mc.level);
            case SmoothCommands.PROBE -> SmoothProbe.dump(mc.player, "requested (probe command)");
            case SmoothCommands.REFRESH -> {
                TerrainSmoothClient.refresh();
                message("natural surfaces and slopes made again, re-meshing");
            }
            case SmoothCommands.AO -> {
                TerrainSmoothClient.aoOnSlopes = msg.flag();
                if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
                message("sloped tops are lit " + (msg.flag() ? "with smooth lighting (AO; may darken them)" : "flat (no AO, the default)"));
            }
            default -> {}
        }
    }

    static void message(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal("Terrain smooth: " + text), false);
    }

    /** Counters of both sides and a count of the 9 x 9 chunks around the player (natural surfaces, sloped tops). */
    private static void status(ClientLevel level) {
        var c = TerrainSmooth.CLIENT;
        message(c.summary() + "; dimension " + level.dimension().location() + (TerrainSmooth.applies(level) ? "" : " (not smoothed here)"));
        if (!c.enabled() || !TerrainSmooth.applies(level)) return;
        long begin = System.nanoTime();
        var p = Minecraft.getInstance().player;
        int px = p.getBlockX() >> 4, pz = p.getBlockZ() >> 4;
        int columns = 0, natural = 0, none = 0, missing = 0, sloped = 0, raised = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int cx = px - 4; cx <= px + 4; cx++) for (int cz = pz - 4; cz <= pz + 4; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null) { missing++; continue; }
            SmoothChunkData d = TerrainSmooth.peek(chunk, c);
            if (d == null) { missing++; continue; }
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int wx = (cx << 4) + x, wz = (cz << 4) + z, s = d.surface(wx, wz);
                columns++;
                if (s == SmoothRules.NO_SURFACE) { none++; continue; }
                natural++;
                long g = TerrainSmooth.fresh(level, chunk, wx, s, wz);
                if (g < 0) continue;
                sloped++;
                float[] k = SmoothRules.corners(g);
                if (Math.max(Math.max(k[0], k[1]), Math.max(k[2], k[3])) > 1.001F) raised++;
            }
        }
        double ms = (System.nanoTime() - begin) / 1e6;
        message(String.format(Locale.ROOT, "9 x 9 chunks around you: %d columns, natural surface %d, none (water, built on, overhang) %d, chunks without data %d; sloped tops %d (%.1f %% of the natural surface), of which rising above their cell %d (counted in %.0f ms)",
                columns, natural, none, missing, sloped, natural == 0 ? 0 : 100.0 * sloped / natural, raised, ms));
        long n = TerrainSmoothClient.candidates.sum();
        message(String.format(Locale.ROOT, "client chunk builds: %d natural surface tops %.1f ms (%.2f us each), %d sloped, %d slope meshes built %.1f ms, %d plants moved, %d snow skins; sections marked by edits %d; player lifted %d times",
                n, TerrainSmoothClient.candidateNanos.sum() / 1e6, n == 0 ? 0 : TerrainSmoothClient.candidateNanos.sum() / 1e3 / n, TerrainSmoothClient.sloped.sum(),
                TerrainSmoothClient.quadBuilds.sum(), TerrainSmoothClient.quadNanos.sum() / 1e6, TerrainSmoothClient.plantsMoved.sum(), TerrainSmoothClient.snowSkins.sum(),
                TerrainSmoothClient.sectionsMarked, TerrainSmoothClient.lifts));
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getBlockX(), p.getBlockZ());
        LevelChunk here = level.getChunkSource().getChunk(px, pz, ChunkStatus.FULL, false);
        SmoothChunkData d = here == null ? null : TerrainSmooth.peek(here, c);
        int s = d == null ? SmoothRules.NO_SURFACE : d.surface(p.getBlockX(), p.getBlockZ());
        message("your column: highest block " + (top - 1) + ", natural surface " + (s == SmoothRules.NO_SURFACE ? "none" : String.valueOf(s))
                + ", block there " + (s == SmoothRules.NO_SURFACE ? "-" : level.getBlockState(pos.set(p.getBlockX(), s, p.getBlockZ())).getBlock().getName().getString()));
    }
}
