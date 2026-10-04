package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Testing: /afltemp [player <name>], /afltemp set core <°C>, /afltemp set wet <0..1> (permission 2). */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class TemperatureCommands {
    private TemperatureCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afltemp").requires(s -> s.hasPermission(2))
                .executes(c -> show(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.literal("player").then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> show(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("set")
                        .then(Commands.literal("core").then(Commands.argument("value", DoubleArgumentType.doubleArg(30, 45))
                                .executes(c -> {
                                    var player = c.getSource().getPlayerOrException();
                                    PlayerTemperature.setCore(player, DoubleArgumentType.getDouble(c, "value"));
                                    return show(c.getSource(), player);
                                })))
                        .then(Commands.literal("wet").then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1))
                                .executes(c -> {
                                    var player = c.getSource().getPlayerOrException();
                                    PlayerTemperature.setWet(player, DoubleArgumentType.getDouble(c, "value"));
                                    return show(c.getSource(), player);
                                })))));
    }
    private static int show(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.literal(PlayerTemperature.describe(player)), false);
        return 1;
    }
}
