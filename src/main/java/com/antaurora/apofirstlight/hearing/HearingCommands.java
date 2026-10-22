package com.antaurora.apofirstlight.hearing;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Tinnitus V2 dev commands (op): {@code /afl hearing} shows the own load and stage; {@code load <value>} sets it,
 * {@code clear} empties it; {@code dose <value>} adds a raw dose as a loud event would (hearing protection, attribute and
 * jolt included).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class HearingCommands {
    private HearingCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("hearing")
                .requires(s -> s.hasPermission(2))
                .executes(c -> done(c.getSource(), p -> {}))
                .then(Commands.literal("clear").executes(c -> done(c.getSource(), p -> HearingLoad.set(p, 0))))
                .then(Commands.literal("load").then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1000))
                        .executes(c -> {
                            double v = DoubleArgumentType.getDouble(c, "value");
                            return done(c.getSource(), p -> HearingLoad.set(p, v));
                        })))
                .then(Commands.literal("dose").then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1000))
                        .executes(c -> {
                            double v = DoubleArgumentType.getDouble(c, "value");
                            return done(c.getSource(), p -> HearingLoad.add(p, v));
                        })))));
    }

    private static int done(CommandSourceStack source, java.util.function.Consumer<ServerPlayer> change) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        change.accept(player);
        double load = HearingLoad.load(player);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "hearing load %.2f, stage %d",
                load, TinnitusConfig.get().stage(load))), false);
        return 1;
    }
}
