package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

/**
 * {@code /afl dev ground here} (terrain rework R0, docs/worldgen/terrain_rework_v1_design.md 5): GroundView read back where
 * the caller stands, on the server: the ground under the feet and its material, the open litres of the blocks round the
 * feet and of the edge toward the east, and what the queries cost here (openArea of 32 blocks, surfaceGrid of the chunk).
 * {@code /afl dev ground samples} (natural ground N1b, docs/worldgen/natural_ground_n1_design.md 7): lays a 5 x 5 patch of
 * each AFL natural ground (every cover and gravel kind) and red sandstone east of the caller, two rows of six, each patch
 * with a one-block step at its back so Terrain Smooth's slopes show too. For a test world only: it overwrites what is there.
 * {@code /afl dev ground census} (N1c, natural_ground_n1_design.md 12): the vanilla natural ground blocks left in the
 * loaded chunks within 8 chunks of the caller (NaturalGroundTable#SWAP), the AFL ground there, whether the world has the
 * natural ground gate, and what the swap has cost since the start.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GroundDevCommand {
    private GroundDevCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("ground")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("here").executes(c -> here(c.getSource())))
                .then(Commands.literal("samples").executes(c -> samples(c.getSource())))
                .then(Commands.literal("census").executes(c -> census(c.getSource()))))));
    }

    private static List<BlockState> samples() {
        BlockState topsoil = com.antaurora.apofirstlight.registry.AflBlocks.TOPSOIL.get().defaultBlockState();
        BlockState gravel = com.antaurora.apofirstlight.registry.AflBlocks.GRAVEL.get().defaultBlockState();
        return List.of(
                topsoil.setValue(TopsoilBlock.COVER, TopsoilBlock.Cover.GRASS),
                topsoil.setValue(TopsoilBlock.COVER, TopsoilBlock.Cover.LITTER),
                topsoil.setValue(TopsoilBlock.COVER, TopsoilBlock.Cover.MOSS),
                topsoil.setValue(TopsoilBlock.COVER, TopsoilBlock.Cover.BARE),
                com.antaurora.apofirstlight.registry.AflBlocks.MUD.get().defaultBlockState(),
                com.antaurora.apofirstlight.registry.AflBlocks.CLAY.get().defaultBlockState(),
                com.antaurora.apofirstlight.registry.AflBlocks.SAND.get().defaultBlockState(),
                com.antaurora.apofirstlight.registry.AflBlocks.RED_SAND.get().defaultBlockState(),
                gravel.setValue(GravelGroundBlock.KIND, GravelGroundBlock.Kind.ROUND),
                gravel.setValue(GravelGroundBlock.KIND, GravelGroundBlock.Kind.ANGULAR),
                com.antaurora.apofirstlight.registry.AflBlocks.SNOW.get().defaultBlockState(),
                com.antaurora.apofirstlight.registry.AflBlocks.RED_SANDSTONE_ROCK.get().defaultBlockState());
    }

    private static int samples(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos feet = BlockPos.containing(source.getPosition());
        List<BlockState> all = samples();
        int x0 = feet.getX() + 3, z0 = feet.getZ() - 6, y = feet.getY() - 1;
        for (int i = 0; i < all.size(); i++) {
            BlockState s = all.get(i);
            int px = x0 + (i % 6) * 6, pz = z0 + (i / 6) * 7;
            for (int dx = -1; dx <= 5; dx++)
                for (int dz = -1; dz <= 5; dz++) {
                    boolean in = dx >= 0 && dx < 5 && dz >= 0 && dz < 5;
                    int top = in && dz >= 3 ? y + 1 : y;   // the back two rows a block higher: a step to slope
                    for (int yy = y - 2; yy <= y + 4; yy++) {
                        BlockPos q = new BlockPos(px + dx, yy, pz + dz);
                        BlockState want = yy > top ? Blocks.AIR.defaultBlockState() : in ? s : null;
                        if (want == null) continue;   // the gap round a patch keeps the ground that is there, cleared above
                        level.setBlock(q, want, 2);
                    }
                }
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Laid %d ground samples east of (%d, %d, %d): row 1 topsoil grass / litter / moss / bare, mud, clay; row 2 sand, red sand, round gravel, angular gravel, snow, red sandstone",
                all.size(), feet.getX(), feet.getY(), feet.getZ())).withStyle(ChatFormatting.YELLOW), false);
        return all.size();
    }

    private static int census(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        java.util.Set<net.minecraft.world.level.block.Block> vanilla = NaturalGroundPass.table().keySet();
        java.util.Map<String, Long> left = new java.util.TreeMap<>();
        long afl = 0;
        int chunks = 0, cx0 = BlockPos.containing(source.getPosition()).getX() >> 4, cz0 = BlockPos.containing(source.getPosition()).getZ() >> 4;
        for (int cz = cz0 - 8; cz <= cz0 + 8; cz++) for (int cx = cx0 - 8; cx <= cx0 + 8; cx++) {
            var chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            chunks++;
            for (var sec : chunk.getSections()) {
                if (sec.hasOnlyAir()) continue;
                for (int i = 0; i < 4096; i++) {
                    BlockState s = sec.getBlockState(i & 15, i >> 8, i >> 4 & 15);
                    if (vanilla.contains(s.getBlock()))
                        left.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(), 1L, Long::sum);
                    else if (s.getBlock() instanceof NaturalGroundBlock || s.getBlock() instanceof com.antaurora.apofirstlight.block.NaturalRockBlock) afl++;
                }
            }
        }
        long total = left.values().stream().mapToLong(Long::longValue).sum(), aflN = afl;
        int n = chunks;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Natural ground gate %s; %d loaded chunks: %d vanilla natural ground blocks %s, %d AFL ground and rock; %s",
                NaturalGroundPass.on(level) ? "ON" : "off", n, total, left, aflN, NaturalGroundPass.stats())).withStyle(ChatFormatting.YELLOW), false);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int here(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 p = source.getPosition();
        GroundView view = GroundView.of(level);
        double ground = view.surfaceBelow(p.x, p.y + 0.5, p.z);
        BlockPos under = BlockPos.containing(p.x, (Double.isNaN(ground) ? p.y : ground) - 0.01, p.z);
        GroundMaterial m = view.materialAt(under.getX(), under.getY(), under.getZ());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Ground under (%.2f, %.2f, %.2f): %s, %s%s",
                p.x, p.y, p.z, Double.isNaN(ground) ? "none" : String.format(Locale.ROOT, "y %.3f", ground), m.id(),
                Double.isNaN(m.repose()) ? "" : String.format(Locale.ROOT, " (repose %.0f deg, excavation %.0f deg)", m.repose(), m.excavation())))
                .withStyle(ChatFormatting.YELLOW), false);
        int x = under.getX(), z = under.getZ(), y0 = under.getY() - 1, y1 = under.getY() + 3;
        short[] area = new short[(y1 - y0) * GroundView.SLABS];
        byte[] edge = new byte[area.length];
        view.openArea(x, z, y0, y1, area);
        view.openEdge(x, z, Direction.EAST, y0, y1, edge);
        StringBuilder b = new StringBuilder("Open per block (L), column / east edge (cm2 x 100): ");
        for (int y = y0; y < y1; y++) {
            long a = 0, e = 0;
            for (int s = 0; s < GroundView.SLABS; s++) {
                a += area[(y - y0) * GroundView.SLABS + s];
                e += edge[(y - y0) * GroundView.SLABS + s];
            }
            // area units x slab = 0.244 L; edge units: 1/16 m x 1/16 m = 39.06 cm2
            b.append(String.format(Locale.ROOT, "%sy %d: %.0f / %.0f", y == y0 ? "" : ", ", y, a * 1000.0 / 4096, e * 39.0625 / 100));
        }
        source.sendSuccess(() -> Component.literal(b.toString()), false);
        long t0 = System.nanoTime();
        short[] big = new short[32 * GroundView.SLABS];
        for (int i = 0; i < 100; i++) view.openArea(x + (i % 5) - 2, z + (i / 5 % 5) - 2, y0 - 16, y0 + 16, big);
        double us = (System.nanoTime() - t0) / 1000.0 / 100;
        int[] grid = new int[65 * 65];
        long t1 = System.nanoTime();
        for (int i = 0; i < 10; i++) view.surfaceGrid(x >> 4, z >> 4, level.getMaxBuildHeight() - 1, grid);
        double ms = (System.nanoTime() - t1) / 1e6 / 10;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Cost here: openArea of 32 blocks %.1f us (target 20), surfaceGrid of this chunk %.2f ms (target 1)", us, ms))
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
