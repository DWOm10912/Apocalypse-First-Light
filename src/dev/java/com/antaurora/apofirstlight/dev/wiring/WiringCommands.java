package com.antaurora.apofirstlight.dev.wiring;

import com.antaurora.apofirstlight.wiring.BuildingWiring;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringRouter;
import com.antaurora.apofirstlight.wiring.WiringTool;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * {@code /afl dev wiring}: Building Wiring V1 by coordinates (docs/gameplay/building_wiring_v1.md), development runs
 * only, op level 2. Circuits 1-4, 5 = the feeder. Laying here costs nothing (the colour is the circuit's); it
 * joins and taps exactly like the wrench (the same server calls).
 * <pre>
 *   lay &lt;pos&gt; &lt;circuit&gt;              one node, joined to the circuit around it, taps the devices mounted there
 *   route &lt;from&gt; &lt;to&gt; &lt;circuit&gt;     the router's path (up to 96 cells), laid as above
 *   cut &lt;pos&gt; &lt;circuit&gt;              one node and its links
 *   clear &lt;from&gt; [&lt;to&gt;]              every node in the cell / box (at most 32768 cells)
 *   read &lt;pos&gt;                        the nodes in a cell: circuit, colour, links, taps and their groups
 *   stats                             nodes in the loaded chunks within 4 chunks of the source
 * </pre>
 * Nothing here refunds wire.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class WiringCommands {
    static final int ROUTE_CELLS = 96, ROUTE_MARGIN = 8, ROUTE_VISITS = 200_000, CLEAR_MAX = 32768;

    private WiringCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        LiteralArgumentBuilder<CommandSourceStack> c = Commands.literal("wiring").requires(s -> !FMLEnvironment.production && s.hasPermission(2));
        c.then(Commands.literal("lay").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("circuit", IntegerArgumentType.integer(1, WiringNode.CIRCUITS)).executes(WiringCommands::lay))));
        c.then(Commands.literal("route").then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos())
                .then(Commands.argument("circuit", IntegerArgumentType.integer(1, WiringNode.CIRCUITS)).executes(WiringCommands::route)))));
        c.then(Commands.literal("cut").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("circuit", IntegerArgumentType.integer(1, WiringNode.CIRCUITS)).executes(WiringCommands::cut))));
        c.then(Commands.literal("clear").then(Commands.argument("from", BlockPosArgument.blockPos()).executes(x -> clear(x, false))
                .then(Commands.argument("to", BlockPosArgument.blockPos()).executes(x -> clear(x, true)))));
        c.then(Commands.literal("read").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(WiringCommands::read)));
        c.then(Commands.literal("stats").executes(WiringCommands::stats));
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c)));
    }

    /** Lays the cells as the wrench does (add, join around, tap), without cost; how many nodes were new. */
    public static int layAll(ServerLevel level, List<BlockPos> cells, int circuit) {
        int made = 0;
        for (BlockPos p : cells) {
            if (BuildingWiring.add(level, p, circuit)) made++;
            if (BuildingWiring.node(level, p, circuit) == 0) continue;
            BuildingWiring.linkAround(level, p, circuit);
            BuildingWiring.autoTap(level, p, circuit);
        }
        return made;
    }

    private static int lay(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerLevel level = x.getSource().getLevel();
        BlockPos p = BlockPosArgument.getLoadedBlockPos(x, "pos");
        int circuit = IntegerArgumentType.getInteger(x, "circuit");
        if (!BuildingWiring.wireable(level, p)) return fail(x, "Wiring: " + p.toShortString() + " cannot hold wire");
        int made = layAll(level, List.of(p), circuit);
        x.getSource().sendSuccess(() -> Component.literal("Wiring: " + (made > 0 ? "laid " : "already had ") + name(circuit) + " at " + p.toShortString()), false);
        return 1;
    }

    private static int route(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerLevel level = x.getSource().getLevel();
        BlockPos from = BlockPosArgument.getLoadedBlockPos(x, "from"), to = BlockPosArgument.getLoadedBlockPos(x, "to");
        int circuit = IntegerArgumentType.getInteger(x, "circuit");
        List<BlockPos> path = WiringRouter.route(level, from, to, circuit, ROUTE_MARGIN, ROUTE_VISITS, ROUTE_CELLS);
        if (path == null) return fail(x, "Wiring: no route from " + from.toShortString() + " to " + to.toShortString() + " within " + ROUTE_CELLS + " cells");
        int made = layAll(level, path, circuit);
        x.getSource().sendSuccess(() -> Component.literal("Wiring: " + name(circuit) + " routed over " + path.size() + " cells, " + made + " new"), false);
        return path.size();
    }

    private static int cut(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        BlockPos p = BlockPosArgument.getLoadedBlockPos(x, "pos");
        int circuit = IntegerArgumentType.getInteger(x, "circuit");
        int n = BuildingWiring.cut(x.getSource().getLevel(), p, circuit);
        x.getSource().sendSuccess(() -> Component.literal("Wiring: " + (n > 0 ? "cut " : "no ") + name(circuit) + " at " + p.toShortString()), false);
        return n;
    }

    private static int clear(CommandContext<CommandSourceStack> x, boolean box) throws CommandSyntaxException {
        ServerLevel level = x.getSource().getLevel();
        BlockPos a = BlockPosArgument.getLoadedBlockPos(x, "from"), b = box ? BlockPosArgument.getLoadedBlockPos(x, "to") : a;
        long cells = (long) (Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1) * (Math.abs(a.getZ() - b.getZ()) + 1);
        if (cells > CLEAR_MAX) return fail(x, "Wiring: " + cells + " cells, at most " + CLEAR_MAX);
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(a, b)) if (level.isLoaded(p)) n += BuildingWiring.clear(level, p);
        int removed = n;
        x.getSource().sendSuccess(() -> Component.literal("Wiring: cleared " + removed + " nodes in " + cells + " cells"), false);
        return removed;
    }

    private static int read(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerLevel level = x.getSource().getLevel();
        BlockPos p = BlockPosArgument.getLoadedBlockPos(x, "pos");
        int[] circuits = BuildingWiring.circuits(level, p);
        StringBuilder out = new StringBuilder("Wiring at " + p.toShortString() + (BuildingWiring.wireable(level, p) ? "" : " (cannot hold wire)") + ": ");
        if (circuits.length == 0) out.append("none");
        for (int c : circuits) {
            long node = BuildingWiring.node(level, p, c);
            out.append("\n  ").append(name(c)).append(" ").append(WiringNode.colourName(WiringNode.colour(node))).append(" links ");
            for (Direction d : Direction.values()) if (WiringNode.linked(node, d)) out.append(d.getName().charAt(0));
            out.append(" taps ");
            for (Direction d : Direction.values()) if (WiringNode.tapped(node, d)) out.append(d.getName().charAt(0)).append(':').append(WiringNode.group(node, d)).append(' ');
        }
        x.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return circuits.length;
    }

    private static int stats(CommandContext<CommandSourceStack> x) {
        ServerLevel level = x.getSource().getLevel();
        ChunkPos at = new ChunkPos(BlockPos.containing(x.getSource().getPosition()));
        int chunks = 0, nodes = 0;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            int n = BuildingWiring.count(level, new ChunkPos(at.x + dx, at.z + dz));
            if (n > 0) { chunks++; nodes += n; }
        }
        int c = chunks, n = nodes;
        String selection = x.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                ? ", your wrench on " + WiringTool.circuitName(WiringTool.circuit(player)).getString() : "";
        x.getSource().sendSuccess(() -> Component.literal("Wiring: " + n + " nodes in " + c + " chunks (9 x 9 around)" + selection), false);
        return n;
    }

    private static String name(int circuit) {
        return circuit == WiringNode.FEEDER ? "feeder" : "circuit " + circuit;
    }

    private static int fail(CommandContext<CommandSourceStack> x, String message) {
        x.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
