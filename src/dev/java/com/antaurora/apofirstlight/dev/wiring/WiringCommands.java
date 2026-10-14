package com.antaurora.apofirstlight.dev.wiring;

import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.energy.BuildingSupply;
import com.antaurora.apofirstlight.wiring.BuildingWiring;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringRouter;
import com.antaurora.apofirstlight.wiring.WiringSupply;
import com.antaurora.apofirstlight.wiring.WiringTool;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
 *   feed &lt;pos&gt;                        what powers the device there: the WIRED panel and circuit, or the footprint panel
 *   group &lt;device&gt; &lt;group&gt;            the group of the tap feeding the device (phase 3): 0 always on, 1-8 a light group
 *                                     (a switch: the group it works), 15 the photocell (lights only); outlets stay 0
 * </pre>
 * {@code /afl dev panel_mode <pos> [wired|legacy_area]} (phase 2): reads a Distribution Panel's supply mode and its last
 * solve (per circuit: devices reached, conflict, the light groups in use and whether each is on; wire into unloaded chunks),
 * or sets the mode.
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
        c.then(Commands.literal("feed").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(WiringCommands::feed)));
        c.then(Commands.literal("group").then(Commands.argument("device", BlockPosArgument.blockPos())
                .then(Commands.argument("group", IntegerArgumentType.integer(0, WiringNode.PHOTOCELL)).executes(WiringCommands::group))));
        LiteralArgumentBuilder<CommandSourceStack> mode = Commands.literal("panel_mode").requires(s -> !FMLEnvironment.production && s.hasPermission(2));
        mode.then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(WiringCommands::panelRead)
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, b) -> { b.suggest("wired"); b.suggest("legacy_area"); return b.buildFuture(); })
                        .executes(WiringCommands::panelSet)));
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c).then(mode)));
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

    private static int feed(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerLevel level = x.getSource().getLevel();
        BlockPos p = BlockPosArgument.getLoadedBlockPos(x, "pos");
        WiringSupply.Feed f = WiringSupply.feed(level, p);
        BlockPos entry = WiringSupply.entryPanel(level, p);
        BuildingSupply.Source source = BuildingSupply.lighting(level, p, p);
        String out = "Feed at " + p.toShortString() + ": "
                + (f != null ? "wired panel " + f.panel().toShortString() + " " + name(f.circuit()) + " group " + f.group()
                : entry != null ? "service entry for wired panel " + entry.toShortString()
                : source != null ? "footprint panel " + source.panel().getBlockPos().toShortString()
                : "none")
                + (source != null ? (source.live() ? ", live" : ", dead") : "");
        x.getSource().sendSuccess(() -> Component.literal(out), false);
        return f != null || entry != null || source != null ? 1 : 0;
    }

    /** Sets the group of the tap that feeds the device (one per device; circuits 1-4 only). */
    public static String setGroup(ServerLevel level, BlockPos device, int group) {
        net.minecraft.world.level.block.state.BlockState s = level.getBlockState(device);
        BuildingWiring.Kind kind = BuildingWiring.kind(s);
        if (kind != BuildingWiring.Kind.LOAD) return "not a light, switch or outlet";
        WiringSupply.Role role = WiringSupply.role(s);
        boolean light = group >= 1 && group <= WiringNode.GROUPS;
        if (!(group == WiringNode.ALWAYS || role == WiringSupply.Role.LIGHT && (light || group == WiringNode.PHOTOCELL) || role == WiringSupply.Role.SWITCH && light))
            return role == WiringSupply.Role.OTHER ? "outlets and emergency lights stay in group 0" : "group " + group + " does not fit a " + role.name().toLowerCase();
        BlockPos m = BuildingWiring.mount(s, device);
        Direction toward = m == null ? null : Direction.fromDelta(device.getX() - m.getX(), device.getY() - m.getY(), device.getZ() - m.getZ());
        if (toward == null) return "no mount cell";
        for (int c = 1; c <= WiringNode.BREAKERS; c++)
            if (WiringNode.tapped(BuildingWiring.node(level, m, c), toward)) return BuildingWiring.setGroup(level, m, c, toward, group) ? null : "tap unchanged";
        return "no tap feeds it (lay wire into " + m.toShortString() + ")";
    }

    private static int group(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerLevel level = x.getSource().getLevel();
        BlockPos p = BlockPosArgument.getLoadedBlockPos(x, "device");
        int g = IntegerArgumentType.getInteger(x, "group");
        String err = setGroup(level, p, g);
        if (err != null) return fail(x, "Wiring group at " + p.toShortString() + ": " + err);
        x.getSource().sendSuccess(() -> Component.literal("Wiring: " + p.toShortString() + " now in " + groupName(g)), false);
        return 1;
    }

    private static String groupName(int g) {
        return g == WiringNode.ALWAYS ? "always on" : g == WiringNode.PHOTOCELL ? "photocell" : "group " + g;
    }

    private static DistributionPanelBlockEntity panel(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        BlockPos p = BlockPosArgument.getLoadedBlockPos(x, "pos");
        return x.getSource().getLevel().getBlockEntity(p) instanceof DistributionPanelBlockEntity panel ? panel : null;
    }

    private static int panelRead(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        DistributionPanelBlockEntity panel = panel(x);
        if (panel == null) return fail(x, "Panel mode: no Distribution Panel there");
        StringBuilder out = new StringBuilder("Panel " + panel.getBlockPos().toShortString() + ": " + modeName(panel.mode()));
        WiringSupply.Result r = panel.wiring();
        if (panel.wired() && r == null) out.append(", not solved yet");
        if (r != null) {
            for (int c = 1; c <= WiringNode.CIRCUITS; c++)
                if (r.wired[c]) out.append("\n  ").append(name(c)).append(": ").append(r.devices[c]).append(c == WiringNode.FEEDER ? " entries" : " devices")
                        .append(r.conflict[c] ? ", JOINED TO ANOTHER PANEL (no power)" : "");
            for (int c = 1; c <= WiringNode.BREAKERS; c++) for (int g : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, WiringNode.PHOTOCELL})
                if (r.used(c, g)) out.append("\n    ").append(name(c)).append(" ").append(groupName(g)).append(": ").append(r.lights[c][g]).append(" lights, ")
                        .append(r.switches[c][g]).append(" switches").append(g >= 1 && g <= WiringNode.GROUPS ? (panel.groupOn(c, g) ? ", on" : ", OFF") : "");
            if (r.partial) out.append("\n  some wire runs into unloaded chunks");
        }
        x.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static int panelSet(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        DistributionPanelBlockEntity panel = panel(x);
        if (panel == null) return fail(x, "Panel mode: no Distribution Panel there");
        String m = StringArgumentType.getString(x, "mode");
        DistributionPanelBlockEntity.SupplyMode mode = switch (m) {
            case "wired" -> DistributionPanelBlockEntity.SupplyMode.WIRED;
            case "legacy_area" -> DistributionPanelBlockEntity.SupplyMode.LEGACY_AREA;
            default -> null;
        };
        if (mode == null) return fail(x, "Panel mode: wired or legacy_area");
        panel.setMode(mode);
        x.getSource().sendSuccess(() -> Component.literal("Panel " + panel.getBlockPos().toShortString() + ": now " + modeName(mode)), true);
        return 1;
    }

    private static String modeName(DistributionPanelBlockEntity.SupplyMode mode) {
        return mode == DistributionPanelBlockEntity.SupplyMode.WIRED ? "wired" : "legacy_area";
    }

    private static String name(int circuit) {
        return circuit == WiringNode.FEEDER ? "feeder" : "circuit " + circuit;
    }

    private static int fail(CommandContext<CommandSourceStack> x, String message) {
        x.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
