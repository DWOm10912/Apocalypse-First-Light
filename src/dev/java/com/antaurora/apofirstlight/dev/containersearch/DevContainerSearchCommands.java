package com.antaurora.apofirstlight.dev.containersearch;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** DEVELOPMENT ONLY. {@code /dev container_search info}: read-only state of the searchable container in view. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DevContainerSearchCommands {
    private DevContainerSearchCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dev")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("container_search")
                        .then(Commands.literal("info").executes(DevContainerSearchCommands::info))));
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (player.pick(8.0D, 0.0F, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockEntity(hit.getBlockPos()) instanceof AflSearchableContainer container) {
            String summary = hit.getBlockPos().toShortString() + " " + AflContainerSearch.debugSummary(container);
            context.getSource().sendSuccess(() -> Component.literal(summary), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Look at a searchable container within 8 blocks."));
        return 0;
    }
}
