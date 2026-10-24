package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.vegetation.MeshPlantBlock;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * AFL vegetation P1 dev commands (docs/gameplay/vegetation_v1_design.md 8, 13), development runs only (not in the jar):
 * <ul>
 * <li>{@code /afl dev vegetation patch [radius]}: Kentucky bluegrass on every top of soil in a square around the player
 * (default 12), a 5 x 5 square of bare turf left under the player to compare;</li>
 * <li>{@code /afl dev vegetation yarrow [radius]}: the same turf with yarrow in drifts (about a third of the blocks);</li>
 * <li>{@code /afl dev vegetation cattail [radius]}: broadleaf cattail on every top of soil, sand, clay or gravel and in
 * every water a block deep over it (waterlogged), the 5 x 5 square left;</li>
 * <li>{@code /afl dev vegetation species <id> [radius]} (P2b): one mesh plant species (big_bluestem, little_bluestem,
 * pennsylvania_sedge, bracken_fern, ...) on every top of soil around, the 5 x 5 square left;</li>
 * <li>{@code /afl dev vegetation clear [radius]}: removes the mesh plants around (10 blocks up and down);</li>
 * <li>{@code /afl dev vegetation flatten [radius]}: lays the plants around down the way the player looks (8 ways), as a
 * vehicle driving over them (default 3). Walking through them leaves footprints (VegetationFootfalls).</li>
 * <li>{@code /afl dev vegetation plan [radius]} (P2a, 14.6): the natural vegetation's plan over the chunks in a square
 * round the player (default 128 m), without generating anything: each biome's cover, the species, the plants a chunk
 * holds, and the quads the player would see from here at the distance levels (P1's measured means per block); the
 * feature's own counts so far. The real ground (roads, floors, deeper water) refuses some of these columns.</li>
 * </ul>
 * The client's own side: {@code /afl_vegetation status|lod <auto|0|1|2|off>}.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class VegetationCommands {
    private VegetationCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        var c = Commands.literal("vegetation").requires(s -> !FMLEnvironment.production && s.hasPermission(2) && s.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("patch").executes(x -> patch(x, 12, 0))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(3, 48)).executes(x -> patch(x, IntegerArgumentType.getInteger(x, "radius"), 0))))
                .then(Commands.literal("yarrow").executes(x -> patch(x, 12, 1))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(3, 48)).executes(x -> patch(x, IntegerArgumentType.getInteger(x, "radius"), 1))))
                .then(Commands.literal("cattail").executes(x -> patch(x, 12, 2))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(3, 48)).executes(x -> patch(x, IntegerArgumentType.getInteger(x, "radius"), 2))))
                .then(Commands.literal("species").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((x, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(speciesIds(), b))
                        .executes(x -> species(x, com.mojang.brigadier.arguments.StringArgumentType.getString(x, "id"), 12))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(3, 48))
                                .executes(x -> species(x, com.mojang.brigadier.arguments.StringArgumentType.getString(x, "id"), IntegerArgumentType.getInteger(x, "radius"))))))
                .then(Commands.literal("clear").executes(x -> clear(x, 16))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64)).executes(x -> clear(x, IntegerArgumentType.getInteger(x, "radius")))))
                .then(Commands.literal("plan").executes(x -> plan(x, 128))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 512)).executes(x -> plan(x, IntegerArgumentType.getInteger(x, "radius")))))
                .then(Commands.literal("flatten").executes(x -> flatten(x, 3))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16)).executes(x -> flatten(x, IntegerArgumentType.getInteger(x, "radius")))));
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c)));
    }

    /** Places a patch: 0 bluegrass, 1 bluegrass with yarrow in drifts, 2 cattail (in water a block deep too). */
    private static int patch(CommandContext<CommandSourceStack> x, int radius, int kind) {
        ServerPlayer player = (ServerPlayer) x.getSource().getEntity();
        ServerLevel level = player.serverLevel();
        BlockState grass = AflBlocks.KENTUCKY_BLUEGRASS.get().defaultBlockState(), yarrow = AflBlocks.COMMON_YARROW.get().defaultBlockState(),
                cattail = AflBlocks.BROADLEAF_CATTAIL.get().defaultBlockState();
        BlockPos at = player.blockPosition();
        int placed = 0, wet = 0, skipped = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) continue;
                int wx = at.getX() + dx, wz = at.getZ() + dz;
                pos.set(wx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz), wz);
                BlockState plant = kind == 2 ? cattail : kind == 1 && drift(wx, wz) ? yarrow : grass;
                if (kind == 2 && level.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER) && level.getFluidState(pos.below()).isSource()) {
                    // water a block deep over the bed: the cattail in it, waterlogged
                    BlockPos in = pos.below();
                    BlockState w = plant.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
                    if (w.canSurvive(level, in) && level.getBlockState(in).is(Blocks.WATER)) {
                        level.setBlock(in, w, Block.UPDATE_CLIENTS);
                        placed++;
                        wet++;
                    } else skipped++;
                    continue;
                }
                BlockState here = level.getBlockState(pos);
                if (!(here.isAir() || here.canBeReplaced()) || !plant.canSurvive(level, pos)) { skipped++; continue; }
                level.setBlock(pos, plant, Block.UPDATE_CLIENTS);
                placed++;
            }
        int p = placed, w = wet, s = skipped;
        String what = kind == 2 ? "Broadleaf cattail" : kind == 1 ? "Kentucky bluegrass with common yarrow" : "Kentucky bluegrass";
        x.getSource().sendSuccess(() -> Component.literal(what + ": " + p + " blocks placed" + (kind == 2 ? " (" + w + " in water)" : "")
                + ", " + s + " columns skipped (no ground for it, or occupied)"), true);
        return placed;
    }

    /** The ids of the mesh plant blocks (apocalypse_firstlight's). */
    private static java.util.List<String> speciesIds() {
        var out = new java.util.ArrayList<String>();
        for (var e : net.minecraftforge.registries.ForgeRegistries.BLOCKS.getEntries())
            if (e.getValue() instanceof MeshPlantBlock && e.getKey().location().getNamespace().equals("apocalypse_firstlight"))
                out.add(e.getKey().location().getPath());
        java.util.Collections.sort(out);
        return out;
    }

    /** One species on every top of soil in a square around the player, a 5 x 5 square of bare ground left under the player. */
    private static int species(CommandContext<CommandSourceStack> x, String id, int radius) {
        Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", id));
        if (!(block instanceof MeshPlantBlock)) {
            x.getSource().sendFailure(Component.literal("Not a mesh plant: " + id + " (" + String.join(", ", speciesIds()) + ")"));
            return 0;
        }
        ServerPlayer player = (ServerPlayer) x.getSource().getEntity();
        ServerLevel level = player.serverLevel();
        BlockState plant = block.defaultBlockState();
        BlockPos at = player.blockPosition();
        int placed = 0, skipped = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) continue;
                int wx = at.getX() + dx, wz = at.getZ() + dz;
                pos.set(wx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz), wz);
                BlockState here = level.getBlockState(pos);
                if (!(here.isAir() || here.canBeReplaced()) || !plant.canSurvive(level, pos)) { skipped++; continue; }
                level.setBlock(pos, plant, Block.UPDATE_CLIENTS);
                placed++;
            }
        int p = placed, k = skipped;
        x.getSource().sendSuccess(() -> Component.literal(id + ": " + p + " blocks placed, " + k + " columns skipped (no ground for it, or occupied)"), true);
        return placed;
    }

    /** Yarrow's drifts: about a third of the blocks, in patches a few metres across (two hashed lattices blended). */
    private static boolean drift(int x, int z) {
        double v = 0;
        for (int octave = 0; octave < 2; octave++) {
            double s = octave == 0 ? 5 : 2.5, fx = x / s, fz = z / s;
            int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
            double tx = fx - ix, tz = fz - iz, a = hash(ix, iz, octave), b = hash(ix + 1, iz, octave), c = hash(ix, iz + 1, octave), d = hash(ix + 1, iz + 1, octave);
            tx = tx * tx * (3 - 2 * tx);
            tz = tz * tz * (3 - 2 * tz);
            v += (octave == 0 ? 0.65 : 0.35) * ((a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz);
        }
        return v > 0.58;
    }

    private static double hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }

    private static int clear(CommandContext<CommandSourceStack> x, int radius) {
        ServerPlayer player = (ServerPlayer) x.getSource().getEntity();
        ServerLevel level = player.serverLevel();
        int removed = 0;
        for (BlockPos p : BlockPos.betweenClosed(player.blockPosition().offset(-radius, -10, -radius), player.blockPosition().offset(radius, 10, radius)))
            if (level.getBlockState(p).getBlock() instanceof MeshPlantBlock) {
                // a cattail standing in water leaves the water
                level.setBlock(p, level.getBlockState(p).getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS);
                removed++;
            }
        int r = removed;
        x.getSource().sendSuccess(() -> Component.literal("Mesh plants removed: " + r), true);
        return removed;
    }

    /** The measured mean quads per block (LOD0, LOD1, LOD2) of the species blocks, in MeshPlantBlock.Species' order (doc 11.7, 13.3, 13.4, 15.6, 16.5, 17.2, 18.5, 18.8). */
    private static final int[][] QUADS = {{423, 34, 6}, {566, 77, 4}, {650, 57, 6}, {473, 24, 4}, {445, 21, 4}, {426, 57, 6}, {120, 62, 43},
            {341, 112, 2}, {418, 59, 2}, {624, 46, 10}, {640, 56, 10}, {650, 50, 10}, {493, 86, 8}, {529, 14, 4}};
    /** Where the client's distance levels give way (m, the middles of VegetationClient.BANDS, doc 17.3). */
    private static final double[] LEVELS = {32, 68, 108};

    private static int plan(CommandContext<CommandSourceStack> x, int radius) {
        ServerPlayer player = (ServerPlayer) x.getSource().getEntity();
        ServerLevel level = player.serverLevel();
        var plan = com.antaurora.apofirstlight.vegetation.VegetationGate.plan(level);
        if (plan == null) {
            x.getSource().sendFailure(Component.literal("No vegetation plan here (the overworld of an AFL world with a Terrain V2 plan only)"));
            return 0;
        }
        var plants = com.antaurora.apofirstlight.vegetation.VegetationPlan.Plant.values();
        java.util.Map<String, int[]> biomes = new java.util.TreeMap<>();
        int[] species = new int[plants.length];
        long[] quads = new long[3];
        int chunks = 0, excluded = 0, maxChunk = 0;
        long sumChunk = 0, nanos = 0;
        BlockPos at = player.blockPosition();
        var pos = new BlockPos.MutableBlockPos();
        for (int cx = (at.getX() - radius) >> 4; cx <= (at.getX() + radius) >> 4; cx++)
            for (int cz = (at.getZ() - radius) >> 4; cz <= (at.getZ() + radius) >> 4; cz++) {
                long t0 = System.nanoTime();
                var area = plan.area(cx, cz);
                int inChunk = 0;
                for (int i = 0; i < 16; i++)
                    for (int j = 0; j < 16; j++) {
                        int wx = (cx << 4) + i, wz = (cz << 4) + j;
                        pos.set(wx, (int) Math.round(plan.surface.heightAt(wx + 0.5, wz + 0.5)), wz);
                        String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().getPath()).orElse("?");
                        int[] b = biomes.computeIfAbsent(biome, k -> new int[2]);
                        b[0]++;
                        if (area.excluded(wx, wz)) { excluded++; continue; }
                        var p = area.choose(wx, wz, biome);
                        if (p == null) continue;
                        b[1]++;
                        species[p.ordinal()]++;
                        inChunk++;
                        double d = Math.hypot(wx + 0.5 - player.getX(), wz + 0.5 - player.getZ());
                        for (int l = 0; l < 3; l++)
                            if (d < LEVELS[l]) { quads[l] += QUADS[p.standIn.ordinal()][l]; break; }
                    }
                nanos += System.nanoTime() - t0;
                chunks++;
                sumChunk += inChunk;
                maxChunk = Math.max(maxChunk, inChunk);
            }
        var out = new StringBuilder();
        out.append(String.format(java.util.Locale.ROOT, "Vegetation plan v%d over %d chunks (%.2f ms a chunk, as the feature would), %d columns excluded (towns, the home's plot)%n",
                com.antaurora.apofirstlight.vegetation.VegetationPlan.VERSION, chunks, chunks == 0 ? 0 : nanos / 1e6 / chunks, excluded));
        biomes.forEach((k, v) -> out.append(String.format(java.util.Locale.ROOT, "  %s: %d columns, cover %.0f%%%n", k, v[0], v[0] == 0 ? 0 : 100.0 * v[1] / v[0])));
        out.append("  species:");
        for (var p : plants) if (species[p.ordinal()] > 0) out.append(' ').append(p.name().toLowerCase(java.util.Locale.ROOT)).append(' ').append(species[p.ordinal()]);
        out.append(String.format(java.util.Locale.ROOT, "%n  plant blocks a chunk: mean %.0f, max %d%n", chunks == 0 ? 0 : sumChunk / (double) chunks, maxChunk));
        out.append(String.format(java.util.Locale.ROOT, "  quads seen from here (P1 means): LOD0 (< 20 m) %d, LOD1 (< 48 m) %d, LOD2 (< 80 m) %d%n", quads[0], quads[1], quads[2]));
        out.append("  ").append(com.antaurora.apofirstlight.vegetation.VegetationGate.status());
        for (String line : out.toString().split("\n")) x.getSource().sendSuccess(() -> Component.literal(line), false);
        com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[AFL vegetation plan]\n{}", out);
        return chunks;
    }

    private static int flatten(CommandContext<CommandSourceStack> x, int radius) {
        ServerPlayer player = (ServerPlayer) x.getSource().getEntity();
        ServerLevel level = player.serverLevel();
        var look = player.getLookAngle();
        int heading = MeshPlantBlock.heading(look.x, look.z);
        int laid = 0;
        for (BlockPos p : BlockPos.betweenClosed(player.blockPosition().offset(-radius, -3, -radius), player.blockPosition().offset(radius, 3, radius))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof MeshPlantBlock plant && plant.flatten(level, p.immutable(), s, heading)) laid++;
        }
        int n = laid;
        x.getSource().sendSuccess(() -> Component.literal("Mesh plants laid down as under a wheel, heading " + heading + " (0 south, clockwise): " + n), true);
        return laid;
    }
}
