package com.antaurora.apofirstlight.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import com.antaurora.apofirstlight.world.bunker.BunkerSavedData;
import com.antaurora.apofirstlight.world.bunker.BunkerPlacementManager;
import com.antaurora.apofirstlight.world.bunker.BunkerPlayerSpawnEvents;
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
        dev.then(Commands.literal("bunker")
                .then(Commands.literal("status")
                        .executes(AflDevCommands::bunkerStatus)));

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

    private static int bunkerStatus(CommandContext<CommandSourceStack> context) {
        ServerLevel overworld = context.getSource().getServer().overworld();
        BunkerSavedData data = overworld.getDataStorage().computeIfAbsent(BunkerSavedData::load,
                BunkerSavedData::new, BunkerSavedData.ID);
        if (!data.isGenerated()) {
            context.getSource().sendSuccess(() -> Component.literal("Generated: false"), false);
            return 1;
        }
        BlockPos preferred = BunkerPlayerSpawnEvents.preferredSpawn(overworld, data);
        BlockPos safe = BunkerPlayerSpawnEvents.findSafePosition(overworld, preferred);
        String playerInfo = " | Player Spawn Local: " + BunkerPlacementManager.PLAYER_SPAWN_LOCAL.toShortString()
                + " | Player Spawn World: " + (preferred == null ? "unavailable" : preferred.toShortString())
                + " | Player Spawn Safe: " + (preferred != null && BunkerPlayerSpawnEvents.isSafe(overworld, preferred));
        if (safe != null && !safe.equals(preferred)) playerInfo += " | Resolved Safe Spawn: " + safe.toShortString();
        final String playerStatus = playerInfo;
        context.getSource().sendSuccess(() -> Component.literal("Generated: true | Origin: "
                + data.getOrigin().toShortString() + " | Rotation: " + data.getRotation()
                + " | Reference Surface Y: " + data.getReferenceSurfaceY() + playerStatus), false);
        return 1;
    }
}
