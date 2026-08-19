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

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RadiationCommands {
    private RadiationCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("afl").then(Commands.literal("radiation")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("here").executes(context -> execute(context.getSource())))
                .then(Commands.literal("locate").then(Commands.literal("natural_safe")
                        .executes(context -> locate(context.getSource(), RadiationSafeAreaFinder.DEFAULT_MAX_RADIUS))
                        .then(Commands.argument("maxRadius", IntegerArgumentType.integer(512, 50_000))
                                .executes(context -> locate(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "maxRadius"))))))));
    }

    private static int execute(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        RadiationSample sample = RadiationManager.getRadiationSample(level, pos);
        source.sendSuccess(() -> Component.literal("[AFL Radiation]"), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "Position: %d, %d, %d | Chunk: %d, %d | Zone: %s | Base Field: %.4f | World Ambient: %.2f RU/h | Local Radiation: %.2f RU/h | Final Radiation: %.2f RU/h | Spawn Safe Core: %s | Spawn Suppression: %.2f | Safe Anchor Chunk: %d, %d",
                pos.getX(), pos.getY(), pos.getZ(), pos.getX() >> 4, pos.getZ() >> 4, sample.zone(),
                sample.baseField(), sample.worldAmbientRadiation(), sample.localRadiation(), sample.finalRadiation(),
                sample.spawnSafeCore(), sample.spawnSuppression(), sample.safeChunkX(), sample.safeChunkZ())), false);
        return 1;
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
                    "No natural SAFE area found within %,d blocks. Search step: %d blocks.",
                    maxRadius, RadiationSafeAreaFinder.SEARCH_STEP)));
            return 0;
        }
        BlockPos pos = result.center();
        source.sendSuccess(() -> Component.literal(String.format(
                "Natural SAFE area found | Position: %d, ?, %d | Chunk: %d, %d | Distance: %.0f blocks | Base Field: %.4f | Base Zone: %s | Spawn Suppression: 1.00 | Sample Check: %d/%d SAFE | Validation Radius: %d blocks | Natural Safe: true | Teleport: /tp @s %d 120 %d",
                pos.getX(), pos.getZ(), pos.getX() >> 4, pos.getZ() >> 4, result.distance(),
                result.baseField(), result.baseZone(), result.safeSamples(), result.totalSamples(),
                result.validationRadius(), pos.getX(), pos.getZ())), false);
        return 1;
    }
}
