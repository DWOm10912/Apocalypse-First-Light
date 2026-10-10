package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanStore;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;

/**
 * Terrain V2 Phase 2 acceptance tools (dev only, 2026-10-10; docs/worldgen/terrain_v2_phase2_generation_v1.md):
 * <pre>
 * /afl dev terrain_v2 here            plan vs generated surface at the player
 * /afl dev terrain_v2 check [chunks]  over the LOADED chunks round the player (nothing is generated or loaded):
 *                                     surface vs plan, chunk-border steps vs inner steps (seams), openings in the
 *                                     stable layer, estuary water
 * /afl dev terrain_v2 points          the acceptance points for this seed (the same rules as tools/TestPoints)
 * </pre>
 * The generated top is the ground's top block: down from the MOTION_BLOCKING_NO_LEAVES heightmap past logs, leaves,
 * plants and fluid (a full chunk keeps no worldgen heightmaps). The plan surface is the ground top, so a solid block
 * occupies y < plan height.
 */
public final class TerrainV2Command {
    private TerrainV2Command() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("terrain_v2")
                .then(Commands.literal("here").executes(TerrainV2Command::here))
                .then(Commands.literal("check").executes(c -> check(c, 6))
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 24)).executes(c -> check(c, IntegerArgumentType.getInteger(c, "chunks")))))
                .then(Commands.literal("points").executes(TerrainV2Command::points));
    }

    static TerrainPlanSurface plan(ServerLevel level) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        return access.apocalypse$hasTerrainPlan() ? TerrainPlanStore.peek(access.apocalypse$getSeed()) : null;
    }

    private static int here(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        TerrainPlanSurface plan = plan(level);
        if (plan == null) { c.getSource().sendFailure(Component.literal("No Terrain V2 plan in this world")); return 0; }
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        int x = pos.getX(), z = pos.getZ();
        double h = plan.heightAt(x + 0.5, z + 0.5);
        int top = groundTop(level.getChunkAt(pos), x & 15, z & 15);
        String[] water = {"land", "sea", "estuary", "marsh"};
        c.getSource().sendSuccess(() -> Component.literal(String.format(
                "Terrain V2 %s at %d %d: plan surface %.2f, generated top block %d (diff %+.2f) | %s | stable %d | shore %d m | grade %.3f | belt %.2f",
                plan.version, x, z, h, top, top + 1 - h, water[plan.waterClass(x, z)], plan.stableDepth(x, z),
                plan.shoreDistance(x, z), plan.slopeAt(x, z), plan.beltWeight(x, z))), false);
        return 1;
    }

    private static int check(CommandContext<CommandSourceStack> c, int radius) {
        ServerLevel level = c.getSource().getLevel();
        TerrainPlanSurface plan = plan(level);
        if (plan == null) { c.getSource().sendFailure(Component.literal("No Terrain V2 plan in this world")); return 0; }
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        int pcx = pos.getX() >> 4, pcz = pos.getZ() >> 4;
        int chunks = 0, cols = 0, within1 = 0, openCols = 0, estuaryCols = 0, estuaryWet = 0;
        double sumAbs = 0, maxAbs = 0;
        double[] diffs = new double[(2 * radius + 1) * (2 * radius + 1) * 256];
        double seamSum = 0, innerSum = 0;
        int seamN = 0, innerN = 0;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            chunks++;
            LevelChunk east = level.getChunkSource().getChunkNow(cx + 1, cz), south = level.getChunkSource().getChunkNow(cx, cz + 1);
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                int top = groundTop(chunk, lx, lz);
                int wc = plan.waterClass(x, z);
                if (wc == 2) {
                    estuaryCols++;
                    if (chunk.getBlockState(new BlockPos(x, 62, z)).getFluidState().isSource()) estuaryWet++;
                }
                if (wc != 0) continue;
                double d = top + 1 - plan.heightAt(x + 0.5, z + 0.5);
                diffs[cols++] = d;
                sumAbs += Math.abs(d);
                maxAbs = Math.max(maxAbs, Math.abs(d));
                if (Math.abs(d) <= 1) within1++;
                // an opening in the stable layer: air (a cave) between the stable depth and the surface
                int stable = plan.stableDepth(x, z);
                for (int y = top - 1; y >= top - stable && y > level.getMinBuildHeight(); y--) {
                    BlockState s = chunk.getBlockState(new BlockPos(x, y, z));
                    if (s.isAir()) { openCols++; break; }
                }
                // steps between neighbours: across the chunk border (lx 15 / lz 15) and inside the chunk
                if (lx < 15) { innerSum += Math.abs(top - groundTop(chunk, lx + 1, lz)); innerN++; }
                else if (east != null) { seamSum += Math.abs(top - groundTop(east, 0, lz)); seamN++; }
                if (lz < 15) { innerSum += Math.abs(top - groundTop(chunk, lx, lz + 1)); innerN++; }
                else if (south != null) { seamSum += Math.abs(top - groundTop(south, lx, 0)); seamN++; }
            }
        }
        if (cols == 0) { c.getSource().sendFailure(Component.literal("No loaded land chunks round the player")); return 0; }
        double[] sorted = Arrays.copyOf(diffs, cols);
        for (int i = 0; i < cols; i++) sorted[i] = Math.abs(sorted[i]);
        Arrays.sort(sorted);
        final int fChunks = chunks, fCols = cols, fWithin = within1, fOpen = openCols, fEst = estuaryCols, fWet = estuaryWet;
        final double mean = sumAbs / cols, p95 = sorted[(int) (cols * 0.95)], fMax = maxAbs;
        final double seam = seamN > 0 ? seamSum / seamN : Double.NaN, inner = innerN > 0 ? innerSum / innerN : Double.NaN;
        c.getSource().sendSuccess(() -> Component.literal(String.format(
                "Terrain V2 check: %d chunks, %d land columns | surface vs plan: mean |d| %.2f, P95 %.2f, max %.2f, within 1 block %.1f%% "
                        + "| mean step across chunk borders %.3f vs inside %.3f | stable-layer openings in %d columns (%.2f%%) | estuary columns %d, water at Y62 in %d",
                fChunks, fCols, mean, p95, fMax, 100.0 * fWithin / fCols, seam, inner, fOpen, 100.0 * fOpen / fCols, fEst, fWet)), false);
        return 1;
    }

    /** The ground's top block in a full chunk: below trees, plants and water. */
    static int groundTop(LevelChunk chunk, int lx, int lz) {
        int x = chunk.getPos().getMinBlockX() + lx, z = chunk.getPos().getMinBlockZ() + lz;
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (y > chunk.getMinBuildHeight()) {
            BlockState s = chunk.getBlockState(p.set(x, y, z));
            if (s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.canBeReplaced()
                    || !s.getFluidState().isEmpty()) y--;
            else break;
        }
        return y;
    }

    private static int points(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        TerrainPlanSurface plan = plan(level);
        if (plan == null) { c.getSource().sendFailure(Component.literal("No Terrain V2 plan in this world")); return 0; }
        int[] spawn = plan.naturalSpawn(4096);
        c.getSource().sendSuccess(() -> Component.literal("Terrain V2 " + plan.version + " seed " + plan.seed
                + ": natural spawn " + (spawn == null ? "none" : spawn[0] + " " + spawn[1])
                + "; the full point list for the test seed is in docs/worldgen/terrain_v2_phase2_generation_v1.md"), false);
        return 1;
    }
}
