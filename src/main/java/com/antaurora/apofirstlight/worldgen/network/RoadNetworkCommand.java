package com.antaurora.apofirstlight.worldgen.network;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.network.road.R4CrossSection;
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
 * column kind). Generates and loads nothing.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoadNetworkCommand {
    private RoadNetworkCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("worldgen")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("roads").executes(c -> status(c.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(source.getServer().overworld());
        if (n == null) {
            source.sendSuccess(() -> Component.literal("AFL roads: no roads are generated in this world (not a federal world, or created before C3.2)."), false);
            return 0;
        }
        StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "AFL roads: stage %s, network digest %s\n", RoadNetworkStage.STAGE, n.digest()));
        double lane = R4CrossSection.CROWN + R4CrossSection.LANE;
        for (RoadDesign d : n.works().roads()) {
            b.append(String.format(Locale.ROOT, "%s  %.0f m\n", d.id, d.length));
            b.append("  start ").append(at(d, 0, lane)).append("   end ").append(at(d, d.length, lane)).append('\n');
            for (RoadDesign.Span s : d.spans)
                b.append(String.format(Locale.ROOT, "  bridge %.0f..%.0f m at ", s.s0(), s.s1())).append(at(d, (s.s0() + s.s1()) / 2, lane)).append('\n');
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
