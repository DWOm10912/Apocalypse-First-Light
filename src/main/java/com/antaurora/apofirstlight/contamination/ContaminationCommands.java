package com.antaurora.apofirstlight.contamination;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.radiation.ContainerRadiation;
import com.antaurora.apofirstlight.radiation.RadiationManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ContaminationCommands {
    private ContaminationCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("afl")
                .then(Commands.literal("contamination")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("get")
                                .executes(context -> get(context.getSource())))
                        .then(Commands.literal("here")
                                .executes(context -> here(context.getSource())))
                        .then(Commands.literal("carried")
                                .executes(context -> carried(context.getSource())))
                        .then(Commands.literal("nearby")
                                .executes(context -> nearby(context.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 5))
                                        .executes(context -> set(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "level")))))
                        .then(Commands.literal("clear")
                                .executes(context -> clear(context.getSource())))));
    }

    private static int get(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = mainHand(source);
        if (stack == null) return 0;
        ItemContamination.Level level = ItemContamination.getLevel(stack);
        source.sendSuccess(() -> Component.translatable(
                "command.apocalypse_firstlight.contamination.get",
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                level.value(), Component.translatable(level.translationKey())), false);
        return 1;
    }

    private static int here(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos position = player.blockPosition();
        double ambient = RadiationManager.getAmbientRadiationForContamination(
                player.serverLevel(), position);
        ItemContamination.Level target = ItemContamination.getTargetLevel(ambient);
        source.sendSuccess(() -> Component.translatable(
                "command.apocalypse_firstlight.contamination.here",
                position.getX(), position.getY(), position.getZ(),
                String.format(Locale.ROOT, "%.2f", ambient), target.value(),
                Component.translatable(target.translationKey())), false);
        return 1;
    }

    private static int carried(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        double rate = ItemContamination.getPlayerCarriedSourceRate(player);
        int contaminatedStacks = ItemContamination.getPlayerContaminatedStackCount(player);
        source.sendSuccess(() -> Component.translatable(
                "command.apocalypse_firstlight.contamination.carried",
                String.format(Locale.ROOT, "%.2f", rate), contaminatedStacks), false);
        return 1;
    }

    private static int nearby(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ContainerRadiation.DebugScanResult result = ContainerRadiation.debugScan(player);
        source.sendSuccess(() -> Component.translatable(
                "command.apocalypse_firstlight.contamination.nearby",
                String.format(Locale.ROOT, "%.2f", result.totalRadiation()),
                result.contaminatedContainers()), false);
        source.sendSuccess(() -> Component.translatable(
                "command.apocalypse_firstlight.contamination.nearby.player",
                format(result.playerCenter().x), format(result.playerCenter().y),
                format(result.playerCenter().z)), false);
        for (ContainerRadiation.SourceBreakdown container : result.strongestSources()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.apocalypse_firstlight.contamination.nearby.container",
                    container.blockId().toString(), container.position().getX(),
                    container.position().getY(), container.position().getZ()), false);
            source.sendSuccess(() -> Component.translatable(
                    "command.apocalypse_firstlight.contamination.nearby.internal",
                    format(container.internalRadiation())), false);
            source.sendSuccess(() -> Component.translatable(
                    "command.apocalypse_firstlight.contamination.nearby.distance",
                    format(container.distance())), false);
            source.sendSuccess(() -> Component.translatable(
                    "command.apocalypse_firstlight.contamination.nearby.distance_factor",
                    String.format(Locale.ROOT, "%.6f", container.distanceFactor())), false);
            source.sendSuccess(() -> Component.translatable(
                    "command.apocalypse_firstlight.contamination.nearby.shield_hits",
                    container.shieldingHits()), false);
            source.sendSuccess(() -> Component.translatable(
                    "command.apocalypse_firstlight.contamination.nearby.shield_transmission",
                    String.format(Locale.ROOT, "%.6f", container.shieldingTransmission())), false);
            source.sendSuccess(() -> Component.translatable(
                    "command.apocalypse_firstlight.contamination.nearby.contribution",
                    format(container.contribution())), false);
            if (container.shieldingBlocks().isEmpty()) {
                source.sendSuccess(() -> Component.translatable(
                        "command.apocalypse_firstlight.contamination.nearby.shield_blocks_none"), false);
            } else {
                source.sendSuccess(() -> Component.translatable(
                        "command.apocalypse_firstlight.contamination.nearby.shield_blocks"), false);
                for (ContainerRadiation.ShieldHit hit : container.shieldingBlocks()) {
                    source.sendSuccess(() -> Component.translatable(
                            "command.apocalypse_firstlight.contamination.nearby.shield_block",
                            hit.position().getX(), hit.position().getY(), hit.position().getZ(),
                            hit.blockId().toString()), false);
                }
            }
        }
        return 1;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static int set(CommandSourceStack source, int value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = mainHand(source);
        if (stack == null) return 0;
        if (value == 0) {
            return clearStack(source, stack);
        }
        ItemContamination.setLevel(stack, value);
        ItemContamination.Level level = ItemContamination.getLevel(stack);
        source.sendSuccess(() -> Component.translatable(
                "command.apocalypse_firstlight.contamination.set",
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                level.value(), Component.translatable(level.translationKey())), true);
        return 1;
    }

    private static int clear(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = mainHand(source);
        return stack == null ? 0 : clearStack(source, stack);
    }

    private static int clearStack(CommandSourceStack source, ItemStack stack) {
        ItemContamination.clear(stack);
        source.sendSuccess(() -> Component.translatable(
                "command.apocalypse_firstlight.contamination.clear",
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()), true);
        return 1;
    }

    private static ItemStack mainHand(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "command.apocalypse_firstlight.contamination.empty_hand"));
            return null;
        }
        return stack;
    }
}
