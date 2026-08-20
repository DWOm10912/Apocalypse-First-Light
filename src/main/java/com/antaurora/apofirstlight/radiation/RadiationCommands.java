package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RadiationCommands {
    private RadiationCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> locate = Commands.literal("locate");
        locate.then(zoneCommand("natural_safe", null));
        locate.then(zoneCommand("irradiated", RadiationZone.IRRADIATED));
        locate.then(zoneCommand("heavy_fallout", RadiationZone.HEAVY_FALLOUT));
        locate.then(zoneCommand("scorched", RadiationZone.SCORCHED));
        LiteralArgumentBuilder<CommandSourceStack> dose = Commands.literal("dose")
                .executes(context -> doseStatus(context.getSource()))
                .then(Commands.literal("reset").executes(context -> doseReset(context.getSource())))
                .then(Commands.literal("add").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                        .executes(context -> doseAdd(context.getSource(), DoubleArgumentType.getDouble(context, "amount")))))
                .then(Commands.literal("set").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                        .executes(context -> doseSet(context.getSource(), DoubleArgumentType.getDouble(context, "amount")))));
        dispatcher.register(Commands.literal("afl").then(Commands.literal("radiation")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("here").executes(context -> execute(context.getSource())))
                .then(dose)
                .then(locate)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> zoneCommand(String name, RadiationZone zone) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(name);
        if (zone == null) {
            command.executes(context -> locate(context.getSource(), RadiationSafeAreaFinder.DEFAULT_MAX_RADIUS));
            command.then(Commands.argument("maxRadius", IntegerArgumentType.integer(512, 20_000))
                    .executes(context -> locate(context.getSource(), IntegerArgumentType.getInteger(context, "maxRadius"))));
        } else {
            command.executes(context -> locateZone(context.getSource(), zone, RadiationSafeAreaFinder.DEFAULT_MAX_RADIUS));
            command.then(Commands.argument("maxRadius", IntegerArgumentType.integer(512, 20_000))
                    .executes(context -> locateZone(context.getSource(), zone, IntegerArgumentType.getInteger(context, "maxRadius"))));
        }
        return command;
    }

    private static int execute(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        RadiationSample sample = RadiationManager.getRadiationSample(level, pos);
        source.sendSuccess(() -> Component.literal("[AFL Radiation]"), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "Position: %d, %d, %d | Chunk: %d, %d | Zone: %s | Base Field: %.4f | Ambient Radiation: %.2f RU/h | Shelter Transmission: %.3f | Shelter Shielding: %.1f%% | Shielded Ambient: %.2f RU/h | Local Radiation: %.2f RU/h | Final Radiation: %.2f RU/h | Spawn Safe Core: %s | Spawn Suppression: %.2f | Safe Anchor: %d, %d | Safe Anchor Source: %s",
                pos.getX(), pos.getY(), pos.getZ(), pos.getX() >> 4, pos.getZ() >> 4, sample.zone(),
                sample.baseField(), sample.worldAmbientRadiation() / Math.max(sample.shelterTransmission(), 0.000001),
                sample.shelterTransmission(), (1.0 - sample.shelterTransmission()) * 100.0,
                sample.worldAmbientRadiation(), sample.localRadiation(), sample.finalRadiation(),
                sample.spawnSafeCore(), sample.spawnSuppression(), sample.safeAnchorX(), sample.safeAnchorZ(),
                sample.safeAnchorSource())), false);
        return 1;
    }

    private static int doseStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return player.getCapability(RadiationExposureProvider.CAPABILITY).map(exposure -> {
            double rate = RadiationManager.getFinalRadiation(player.serverLevel(), player.blockPosition());
            source.sendSuccess(() -> Component.literal(String.format("Current Rate: %.2f RU/h | Cumulative Dose: %.4f RU",
                    rate, exposure.getDose())), false);
            return 1;
        }).orElseGet(() -> {
            source.sendFailure(Component.literal("[AFL Radiation] Exposure data unavailable."));
            return 0;
        });
    }

    private static int doseReset(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return player.getCapability(RadiationExposureProvider.CAPABILITY).map(exposure -> {
            exposure.resetDose();
            source.sendSuccess(() -> Component.literal("Cumulative Dose reset to 0.0000 RU"), false);
            return 1;
        }).orElse(0);
    }

    private static int doseAdd(CommandSourceStack source, double amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return player.getCapability(RadiationExposureProvider.CAPABILITY).map(exposure -> {
            if (!exposure.addDose(amount)) {
                source.sendFailure(Component.literal("[AFL Radiation] Dose amount must be finite and non-negative."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(String.format("Cumulative Dose: %.4f RU", exposure.getDose())), false);
            return 1;
        }).orElse(0);
    }

    private static int doseSet(CommandSourceStack source, double amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return player.getCapability(RadiationExposureProvider.CAPABILITY).map(exposure -> {
            if (!exposure.setDose(amount)) {
                source.sendFailure(Component.literal("[AFL Radiation] Dose amount must be finite and non-negative."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(String.format("Cumulative Dose: %.4f RU", exposure.getDose())), false);
            return 1;
        }).orElse(0);
    }

    private static int locate(CommandSourceStack source, int maxRadius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            source.sendFailure(Component.literal("[AFL Radiation] Natural SAFE search is Overworld-only."));
            return 0;
        }
        NaturalSafeAreaResult result = RadiationSafeAreaFinder.findNearestNaturalSafeArea(
                level, player.blockPosition(), maxRadius);
        source.sendSuccess(() -> Component.literal("[AFL Radiation]"), false);
        if (result == null) {
            source.sendFailure(Component.literal(String.format(
                    "No natural SAFE area found within %,d blocks. Samples: %,d | Time: %d ms | Search step: %d blocks.",
                    maxRadius, RadiationSafeAreaFinder.lastSearchStats().samples(),
                    RadiationSafeAreaFinder.lastSearchStats().elapsedMs(), RadiationSafeAreaFinder.SEARCH_STEP)));
            return 0;
        }
        BlockPos pos = result.center();
        source.sendSuccess(() -> Component.literal(String.format(
                "Natural SAFE area found | Position: %d, ?, %d | Chunk: %d, %d | Distance: %.0f blocks | Base Field: %.4f | Base Zone: %s | Spawn Suppression: 1.00 | Sample Check: %d/%d SAFE | Validation Radius: %d blocks | Natural Safe: true | Teleport: /tp @s %d 120 %d",
                pos.getX(), pos.getZ(), pos.getX() >> 4, pos.getZ() >> 4, result.distance(),
                result.baseField(), result.baseZone(), result.safeSamples(), result.totalSamples(),
                result.validationRadius(), pos.getX(), pos.getZ())
                + String.format(" | Search Samples: %,d | Search Time: %d ms | Search Step: %d",
                RadiationSafeAreaFinder.lastSearchStats().samples(), RadiationSafeAreaFinder.lastSearchStats().elapsedMs(),
                RadiationSafeAreaFinder.SEARCH_STEP)), false);
        return 1;
    }

    private static int locateZone(CommandSourceStack source, RadiationZone target, int maxRadius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            source.sendFailure(Component.literal("[AFL Radiation] Zone search is Overworld-only."));
            return 0;
        }
        RadiationZoneAreaResult result = RadiationSafeAreaFinder.findNearestZoneArea(level, player.blockPosition(), target, maxRadius);
        source.sendSuccess(() -> Component.literal("[AFL Radiation]"), false);
        if (result == null) {
            source.sendFailure(Component.literal(String.format("No %s area found within %,d blocks. Samples: %,d | Time: %d ms | Search step: %d",
                    target, maxRadius, RadiationSafeAreaFinder.lastSearchStats().samples(),
                    RadiationSafeAreaFinder.lastSearchStats().elapsedMs(), RadiationSafeAreaFinder.SEARCH_STEP)));
            return 0;
        }
        BlockPos pos = result.center();
        source.sendSuccess(() -> Component.literal(String.format(
                "%s area found | Position: %d, ?, %d | Chunk: %d, %d | Distance: %.0f blocks | Base Field: %.4f | Base Zone: %s | Spawn Suppression: 1.00 | Sample Check: %d/%d %s | Validation Radius: %d blocks | Teleport: /tp @s %d 120 %d",
                target, pos.getX(), pos.getZ(), pos.getX() >> 4, pos.getZ() >> 4, result.distance(), result.baseField(),
                result.baseZone(), result.matchingSamples(), result.totalSamples(), target, result.validationRadius(), pos.getX(), pos.getZ())
                + String.format(" | Search Samples: %,d | Search Time: %d ms | Search Step: %d",
                RadiationSafeAreaFinder.lastSearchStats().samples(), RadiationSafeAreaFinder.lastSearchStats().elapsedMs(),
                RadiationSafeAreaFinder.SEARCH_STEP)), false);
        return 1;
    }
}
