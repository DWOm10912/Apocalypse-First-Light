package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Health V1a dev commands (docs/gameplay/survival_health_v1a.md 9), permission 2, on the running player:
 * <ul>
 * <li>{@code /afl health}: health, regen state, wounds;</li>
 * <li>{@code /afl health hp <value>}: sets health (to the maximum at most; 0 kills);</li>
 * <li>{@code /afl health scratch}: a scratch as a zombie's would give;</li>
 * <li>{@code /afl health wound <scratch|infection|suppuration>}: a wound already at that stage;</li>
 * <li>{@code /afl health clear}: no wounds;</li>
 * <li>{@code /afl health advance <days>}: every wound that many game days older, its checks rolled as online.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HealthCommands {
    private HealthCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var wound = Commands.literal("wound");
        for (Wounds.Stage stage : Wounds.Stage.values())
            wound.then(Commands.literal(stage.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(c -> done(c.getSource(), p -> Wounds.set(p, stage))));
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("health")
                .requires(s -> s.hasPermission(2))
                .executes(c -> done(c.getSource(), p -> {}))
                .then(Commands.literal("hp").then(Commands.argument("value", FloatArgumentType.floatArg(0, 10_000))
                        .executes(c -> {
                            float v = FloatArgumentType.getFloat(c, "value");
                            return done(c.getSource(), p -> p.setHealth(Math.min(v, p.getMaxHealth())));
                        })))
                .then(Commands.literal("scratch").executes(c -> done(c.getSource(), Wounds::scratch)))
                .then(wound)
                .then(Commands.literal("clear").executes(c -> done(c.getSource(), Wounds::clearDev)))
                .then(Commands.literal("advance").then(Commands.argument("days", DoubleArgumentType.doubleArg(0, 30))
                        .executes(c -> {
                            double days = DoubleArgumentType.getDouble(c, "days");
                            return done(c.getSource(), p -> Wounds.advance(p, days));
                        })))));
    }

    private static int done(CommandSourceStack source, java.util.function.Consumer<ServerPlayer> change) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        change.accept(player);
        PlayerHealth.markSync(player);
        String line = PlayerHealth.describe(player);
        source.sendSuccess(() -> Component.literal("[AFL Health] " + line), false);
        return 1;
    }
}
