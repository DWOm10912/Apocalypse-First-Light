package com.antaurora.apofirstlight.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AflDevCommands {
    private AflDevCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> mask = Commands.literal("mask_structure_exterior_air");
        mask.then(Commands.argument("input", StringArgumentType.word())
                .then(Commands.argument("output", StringArgumentType.word())
                        .executes(context -> maskExteriorAir(context, false))
                        .then(Commands.literal("--overwrite")
                                .executes(context -> maskExteriorAir(context, true)))));

        LiteralArgumentBuilder<CommandSourceStack> schem = Commands.literal("schem_to_nbt");
        schem.then(Commands.argument("input", StringArgumentType.word())
                .then(Commands.argument("output", StringArgumentType.word())
                        .executes(context -> convert(context, false))
                        .then(Commands.literal("--overwrite")
                                .executes(context -> convert(context, true)))));

        LiteralArgumentBuilder<CommandSourceStack> dev = Commands.literal("dev")
                .requires(source -> source.hasPermission(2));
        dev.then(schem);
        dev.then(mask);

        CommandNode<CommandSourceStack> afl = event.getDispatcher().getRoot().getChild("afl");
        if (afl != null) {
            afl.addChild(dev.build());
        } else {
            event.getDispatcher().register(Commands.literal("afl").then(dev));
        }
    }

    private static int convert(CommandContext<CommandSourceStack> context, boolean overwrite) {
        String input = StringArgumentType.getString(context, "input");
        String output = StringArgumentType.getString(context, "output");
        try {
            SchemToVanillaStructureConverter.Result result =
                    SchemToVanillaStructureConverter.convert(context.getSource().getServer(), input, output, overwrite);
            context.getSource().sendSuccess(() -> Component.literal(result.summary()), true);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("AFL schematic conversion failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int maskExteriorAir(CommandContext<CommandSourceStack> context, boolean overwrite) {
        String input = StringArgumentType.getString(context, "input");
        String output = StringArgumentType.getString(context, "output");
        try {
            BunkerExteriorAirMasker.Result result =
                    BunkerExteriorAirMasker.mask(context.getSource().getServer(), input, output, overwrite);
            context.getSource().sendSuccess(() -> Component.literal(result.summary()), true);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("AFL exterior air mask failed: " + exception.getMessage()));
            return 0;
        }
    }
}
