package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.EnergyCellBlockEntity;
import com.antaurora.apofirstlight.energy.EnergyCellStoredMode;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * DEVELOPMENT ONLY: {@code /dev energy fill} charges the energy cell you look at (within 8 blocks) to its capacity and
 * sets it to discharge, so it feeds the cable on its back port (2026-10-08: a source for the A1 fuel court and building
 * power tests; the authoring bridge never writes energy).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DevEnergyCommands {
    private DevEnergyCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dev")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("energy").then(Commands.literal("fill").executes(DevEnergyCommands::fill))));
    }

    private static int fill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (player.pick(8.0D, 0.0F, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockEntity(hit.getBlockPos()) instanceof EnergyCellBlockEntity cell) {
            int capacity = MachineBalanceManager.energyCell().capacityFe();
            CompoundTag charge = cell.saveWithoutMetadata();
            charge.putInt("EnergyStored", capacity);
            charge.putInt(EnergyCellStoredMode.MODE_KEY, 1);   // discharge
            cell.load(charge);
            cell.setChanged();
            var state = player.level().getBlockState(hit.getBlockPos());
            player.level().sendBlockUpdated(hit.getBlockPos(), state, state, Block.UPDATE_ALL);
            context.getSource().sendSuccess(() -> Component.literal("Energy cell at " + hit.getBlockPos().toShortString()
                    + " charged to " + capacity + " FE, discharging"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Look at an energy cell within 8 blocks."));
        return 0;
    }
}
