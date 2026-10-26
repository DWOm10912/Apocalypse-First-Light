package com.antaurora.apofirstlight.worldgen.river;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.ground.GravelGroundBlock;
import com.antaurora.apofirstlight.ground.NaturalGroundBlock;
import com.antaurora.apofirstlight.ground.TopsoilBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverNetwork;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * River / lake V1 R0 (docs/worldgen/river_lake_v1_design.md 7, 11):
 * <ul>
 *   <li>{@code /afl dev river sample}: lays RiverSample east of the caller (64 x 96 blocks, its plain at the caller's
 *   feet, the water level two blocks below): a meandering reach with riffles and pools, a cut bank, point bars and a pond
 *   with a beach and a steep shore, beds and shores of wet AFL ground under vanilla water. For a test world only: it
 *   overwrites what is there, from 4 below the water level to 20 above it.</li>
 *   <li>{@code /afl dev river section}: the river under the caller as it stands: the river network's column there and two
 *   profiles (west-east, north-south, 20 blocks either side) of the ground top and the water on it, and how many water
 *   edges are a step of a block or more (the vanilla-looking bank).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RiverDevCommand {
    private RiverDevCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("river")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("sample").executes(c -> sample(c.getSource())))
                .then(Commands.literal("section").executes(c -> section(c.getSource()))))));
    }

    private static BlockState top(byte material, boolean wet) {
        BlockState s = switch (material) {
            case RiverSample.GRASS -> AflBlocks.TOPSOIL.get().defaultBlockState().setValue(TopsoilBlock.COVER, TopsoilBlock.Cover.GRASS);
            case RiverSample.BARE -> AflBlocks.TOPSOIL.get().defaultBlockState().setValue(TopsoilBlock.COVER, TopsoilBlock.Cover.BARE);
            case RiverSample.GRAVEL -> AflBlocks.GRAVEL.get().defaultBlockState().setValue(GravelGroundBlock.KIND, GravelGroundBlock.Kind.ROUND);
            case RiverSample.SAND -> AflBlocks.SAND.get().defaultBlockState();
            default -> AflBlocks.MUD.get().defaultBlockState();
        };
        return s.setValue(NaturalGroundBlock.WATERLOGGED, wet);
    }

    private static int sample(CommandSourceStack source) {
        long begin = System.nanoTime();
        ServerLevel level = source.getLevel();
        BlockPos feet = BlockPos.containing(source.getPosition());
        RiverSample s = RiverSample.make();
        int ox = feet.getX() + 4, oz = feet.getZ() - RiverSample.SIZE_Z / 2, l = feet.getY() - RiverSample.PLAIN;
        BlockState water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState(), fill = top(RiverSample.BARE, false);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int wetBeds = 0, wetShore = 0, waterCells = 0;
        for (int z = 0; z < RiverSample.SIZE_Z; z++)
            for (int x = 0; x < RiverSample.SIZE_X; x++) {
                int i = z * RiverSample.SIZE_X + x, t = s.top[i];
                if (s.wet[i]) { if (t <= 0) wetBeds++; else wetShore++; }
                for (int rel = -4; rel <= 20; rel++) {
                    BlockState want;
                    if (rel < t - 2) want = fill;
                    else if (rel == t - 2) want = top(s.material[i], false);
                    else if (rel == t - 1) want = top(s.material[i], s.wet[i]);
                    else if (rel <= 0) { want = water; waterCells++; }
                    else want = air;
                    // no neighbour updates: the basin is closed, nothing has to flow
                    level.setBlock(p.set(ox + x, l + rel, oz + z), want, 2 | 16);
                }
            }
        String text = String.format(Locale.ROOT,
                "river sample at %d %d %d (64 x 96, water level %d, surface %.2f): %d wet beds, %d wet shore cells, %d water blocks; %.0f ms. "
                        + "Riffles at z %d, %d, %d; pools at z %d, %d; pond at x %d z %d",
                ox, l, oz, l, l + RiverSample.SURFACE, wetBeds, wetShore, waterCells, (System.nanoTime() - begin) / 1e6,
                oz, oz + 44, oz + 88, oz + 22, oz + 66, ox + 50, oz + 48);
        source.sendSuccess(() -> Component.literal(text), true);
        return 1;
    }

    private static int section(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos feet = BlockPos.containing(source.getPosition());
        StringBuilder out = new StringBuilder();
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        TerrainPlanSurface plan = access.apocalypse$hasTerrainPlan() ? access.apocalypse$terrainPlanSurface() : null;
        int ref = Integer.MIN_VALUE;
        if (plan != null && plan.rivers != null) {
            RiverNetwork.Column c = new RiverNetwork.Column();
            plan.rivers.column(feet.getX(), feet.getZ(), c);
            String[] kinds = {"none", "river", "bank"};
            out.append(String.format(Locale.ROOT, "network: %s, water top %d, depth %d, edge %.1f m, area %.2f km2, width %.1f m%n",
                    kinds[c.kind], c.level, c.depth, c.edge, c.area / 1e6, RiverNetwork.width(c.area)));
            if (c.kind != RiverNetwork.Column.NONE) ref = c.level;
        } else {
            out.append("network: this world has no terrain plan\n");
        }
        int[][] ground = new int[2][41], waterTop = new int[2][41];
        for (int a = 0; a < 2; a++)
            for (int k = -20; k <= 20; k++) {
                int x = feet.getX() + (a == 0 ? k : 0), z = feet.getZ() + (a == 1 ? k : 0);
                int[] gw = column(level, x, feet.getY(), z);
                ground[a][k + 20] = gw[0];
                waterTop[a][k + 20] = gw[1];
                if (ref == Integer.MIN_VALUE && gw[1] != Integer.MIN_VALUE) ref = Math.max(ref, gw[1]);
            }
        if (ref == Integer.MIN_VALUE) ref = feet.getY() - 1;
        int edges = 0, steps = 0;
        for (int a = 0; a < 2; a++) {
            out.append(a == 0 ? "west-east" : "north-south").append(String.format(Locale.ROOT, " (ground top - water top %d; ~ = water depth):%n", ref));
            StringBuilder g = new StringBuilder(), w = new StringBuilder();
            for (int k = 0; k <= 40; k++) {
                g.append(String.format(Locale.ROOT, "%3d", ground[a][k] == Integer.MIN_VALUE ? 99 : ground[a][k] - ref));
                w.append(waterTop[a][k] == Integer.MIN_VALUE ? "  ." : String.format(Locale.ROOT, "%3d", waterTop[a][k] - ground[a][k]));
                if (k > 0) {
                    boolean wa = waterTop[a][k - 1] != Integer.MIN_VALUE, wb = waterTop[a][k] != Integer.MIN_VALUE;
                    if (wa != wb) {
                        edges++;
                        int dry = wa ? ground[a][k] : ground[a][k - 1], wt = wa ? waterTop[a][k - 1] : waterTop[a][k];
                        if (dry >= wt + 1) steps++;
                    }
                }
            }
            out.append(" ground").append(g).append('\n').append(" water ").append(w).append('\n');
        }
        out.append(String.format(Locale.ROOT, "water edges %d, of them a step of a block or more above the water top: %d", edges, steps));
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** {top ground block y, top water y} of column (x, z) near y (from y + 10 down to y - 20), MIN_VALUE where none. */
    private static int[] column(ServerLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int water = Integer.MIN_VALUE;
        for (int yy = y + 10; yy >= y - 20; yy--) {
            int k = TerrainSmooth.cell(level.getBlockState(p.set(x, yy, z)));
            if (k == SmoothRules.FLUID) { if (water == Integer.MIN_VALUE) water = yy; continue; }
            if (k == SmoothRules.WET) return new int[]{yy, water == Integer.MIN_VALUE ? yy : water};
            if (k == SmoothRules.NATURAL || k == SmoothRules.ROCK || k == SmoothRules.OTHER) return new int[]{yy, water};
        }
        return new int[]{Integer.MIN_VALUE, water};
    }
}
