package com.antaurora.apofirstlight.worldgen.network;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkStage;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * {@code /afl worldgen roads} (C3.2): the overworld's built routes, read-only: each route's ends and bridges (pavement
 * coordinates of the right carriageway's lane centre), and where the caller stands on a road (station, lateral offset,
 * column kind). {@code /afl worldgen town} (Town Framework V1): the framework towns and what the caller stands on in one.
 * Generates and loads nothing.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoadNetworkCommand {
    private RoadNetworkCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("worldgen")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("roads").executes(c -> status(c.getSource())))
                .then(Commands.literal("town").executes(c -> town(c.getSource())))));
    }

    /**
     * {@code /afl worldgen town} (Town Framework V1): the framework towns (name, state, exit, the limit's corners and
     * centre in the world, blocks, lots, streets) and what the caller stands on in one (TownWorks#describe). Read-only.
     */
    private static int town(CommandSourceStack source) {
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(source.getServer().overworld());
        if (n == null || n.works().towns().isEmpty()) {
            source.sendSuccess(() -> Component.literal("AFL towns: no framework town in this world."), false);
            return 0;
        }
        StringBuilder b = new StringBuilder("AFL towns (Town Framework V1):").append('\n');
        for (com.antaurora.apofirstlight.worldgen.network.town.TownPlan p : n.works().towns().plans()) {
            double[] c = p.world((p.limit[0] + p.limit[1]) / 2, (p.limit[2] + p.limit[3]) / 2), e = p.world(0, 0);
            b.append(String.format(Locale.ROOT, "%s (%s, %s): %d blocks, %d lots, %d streets; exit crossing %.0f %.0f, centre %.0f %.0f, %.0f x %.0f m%n",
                    p.name, p.town, p.state, p.blocks.size(), p.lots.size(), p.streets.size(), e[0], e[1], c[0], c[1],
                    p.limit[1] - p.limit[0], p.limit[3] - p.limit[2]));
        }
        Vec3 pos = source.getPosition();
        String here = n.works().towns().describe(pos.x, pos.z);
        b.append(here == null ? "you are in no town" : "you: " + here);
        String text = b.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(source.getServer().overworld());
        if (n == null) {
            source.sendSuccess(() -> Component.literal("AFL roads: no roads are generated in this world (not a federal world, or created before C3.2)."), false);
            return 0;
        }
        StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "AFL roads: stage %s, network digest %s\n", RoadNetworkStage.STAGE, n.digest()));
        for (RoadDesign d : n.works().roads()) {
            double lane = d.section.laneCentre(d.section.lanes - 1);
            b.append(String.format(Locale.ROOT, "%s  %s  %.0f m\n", d.id, d.section, d.length));
            b.append("  start ").append(at(d, 0, lane)).append("   end ").append(at(d, d.length, lane)).append('\n');
            for (RoadDesign.Span s : d.spans)
                b.append(String.format(Locale.ROOT, "  bridge %.0f..%.0f m%s at ", s.s0(), s.s1(), s.piers().length > 0 ? ", " + s.piers().length + " piers" : ""))
                        .append(at(d, (s.s0() + s.s1()) / 2, lane)).append('\n');
        }
        Vec3 p = source.getPosition();
        RoadWorks.Hit h = n.works().hit(p.x, p.z, RoadWorks.REACH);
        if (h == null) b.append("you are not on a road's works");
        else {
            var c = RoadChunkPass.columns(n, (int) Math.floor(p.x) >> 4, (int) Math.floor(p.z) >> 4);
            String kind = c == null ? "NONE" : c[((int) Math.floor(p.x) & 15) | ((int) Math.floor(p.z) & 15) << 4].kind().name();
            b.append(String.format(Locale.ROOT, "you: %s station %.1f m, offset %+.1f m, column %s", h.road().id, h.s(), h.u(), kind));
        }
        String text = b.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static String at(RoadDesign d, double s, double u) {
        double[] xz = d.point(s, u);
        return String.format(Locale.ROOT, "%.0f %.1f %.0f", xz[0], d.pavementY(s, u), xz[1]);
    }
}
