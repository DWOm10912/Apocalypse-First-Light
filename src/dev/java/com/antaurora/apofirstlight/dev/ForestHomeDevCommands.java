package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.home.ForestHomeLayout;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeRuntime;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeSavedData;
import com.antaurora.apofirstlight.worldgen.home.StartAreaPlanner;
import com.antaurora.apofirstlight.worldgen.network.contract.RoadNode;
import com.antaurora.apofirstlight.worldgen.network.contract.StructureReservation;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Locale;

/**
 * /afl dev forest_home (M4 round 1, docs/worldgen/forest_home_b_m4_placement.md, P7): find (run the start-area planner on
 * this federal world and list its candidates; changes nothing), info (the stored reservation, its ground, the node H and
 * the planner's report from world creation), tp (to the front porch). Placing and removing by command are not part of
 * round 1: the house is placed by world generation only. Development builds, OP 2, overworld.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class ForestHomeDevCommands {
    private ForestHomeDevCommands() {}

    private static boolean allowed(CommandSourceStack s) {
        return !FMLEnvironment.production && s.hasPermission(2) && s.getLevel().dimension().equals(Level.OVERWORLD);
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        var c = Commands.literal("forest_home").requires(ForestHomeDevCommands::allowed);
        c.then(Commands.literal("find").executes(ForestHomeDevCommands::find));
        c.then(Commands.literal("info").executes(ForestHomeDevCommands::info));
        c.then(Commands.literal("tp").executes(ForestHomeDevCommands::tp));
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c)));
    }

    private static int find(CommandContext<CommandSourceStack> x) {
        ServerLevel level = x.getSource().getLevel();
        TerrainPlanSurface surface = ForestHomeRuntime.surface(level);
        if (surface == null || !surface.federal()) {
            x.getSource().sendFailure(Component.literal("not a federal world: the start home is planned on the federal surface only"));
            return 0;
        }
        StartAreaPlanner.Result r = ForestHomeRuntime.plan(level, surface);
        x.getSource().sendSuccess(() -> Component.literal(ForestHomeRuntime.report(r)), false);
        return r.sites().size();
    }

    private static int info(CommandContext<CommandSourceStack> x) {
        ServerLevel level = x.getSource().getServer().overworld();
        ForestHomeSavedData d = ForestHomeRuntime.data(level);
        StructureReservation r = d.reservation();
        if (r == null) {
            String why = d.report().isEmpty() ? "this world was not created with the start home gate (folder " + ForestHomeRuntime.GATE
                    + "*, federal surface)" : "the planner found no site:\n" + d.report();
            x.getSource().sendFailure(Component.literal("no start home reservation; " + why));
            return 0;
        }
        RoadNode h = r.nodes().get(0);
        double[] porch = ForestHomeLayout.porch(r);
        ForestHomeRuntime.Active a = ForestHomeRuntime.active();
        String text = String.format(Locale.ROOT,
                "%s %s: origin %d %d %d rotation %d (front heading %.0f), ground Y %d, %s%n"
                        + "box %s%nporch %.1f %d %.1f%nnode H %s: %.1f %d %.1f heading %.0f %s %s width %.0f %s%n"
                        + "chunk pass %s%nplanner at creation: %s",
                r.id(), r.state(), r.x(), r.y(), r.z(), r.rotation(), ForestHomeLayout.heading(r.rotation()), ForestHomeLayout.groundY(r),
                r.template(), a == null ? "-" : a.box().toString(), porch[0], ForestHomeLayout.groundY(r), porch[1],
                h.id(), h.x(), Math.round(h.y()), h.z(), h.heading(), h.privateSpec(), h.publicClass(), h.width(), h.state(),
                a == null ? "INACTIVE (template missing? see the log)" : "active", d.report());
        x.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> x) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer p)) return 0;
        StructureReservation r = ForestHomeRuntime.data(x.getSource().getServer().overworld()).reservation();
        if (r == null) {
            x.getSource().sendFailure(Component.literal("no start home reservation in this world"));
            return 0;
        }
        double[] porch = ForestHomeLayout.porch(r);
        p.teleportTo(x.getSource().getServer().overworld(), porch[0], ForestHomeLayout.groundY(r), porch[1], p.getYRot(), p.getXRot());
        return 1;
    }
}
