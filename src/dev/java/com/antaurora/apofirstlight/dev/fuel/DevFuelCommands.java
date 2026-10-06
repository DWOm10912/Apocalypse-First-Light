package com.antaurora.apofirstlight.dev.fuel;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.UndergroundFuelTankBlock;
import com.antaurora.apofirstlight.blockentity.UndergroundFuelTankBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Supplier;

/**
 * DEVELOPMENT ONLY (OP 2). Fuel fluids have no bucket (docs/gameplay/fuel_fluids_v1.md):
 * <ul>
 * <li>{@code /dev fuel source <gasoline|diesel>}: a source block in the cell in front of the face in view (or the cell in
 * view when it can be replaced). {@code /setblock ... apocalypse_firstlight:gasoline} works as well.</li>
 * <li>{@code /dev fuel fill [mB]}: fills the underground fuel tank in view with its own fuel (default: full); a fuel container
 * in view (jerry can, drum) with what it holds, gasoline when empty.</li>
 * <li>{@code /dev fuel info}: the underground fuel tank in view: fuel and amount.</li>
 * <li>{@code /dev fuel station}: a cut-open demo fuel station east of the player (DevFuelStation).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DevFuelCommands {
    private DevFuelCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dev")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("fuel")
                        .then(Commands.literal("source")
                                .then(Commands.literal("gasoline").executes(context -> source(context, AflBlocks.GASOLINE)))
                                .then(Commands.literal("diesel").executes(context -> source(context, AflBlocks.DIESEL))))
                        .then(Commands.literal("fill").executes(context -> fill(context, Integer.MAX_VALUE))
                                .then(Commands.argument("mB", IntegerArgumentType.integer(1))
                                        .executes(context -> fill(context, IntegerArgumentType.getInteger(context, "mB")))))
                        .then(Commands.literal("info").executes(DevFuelCommands::info))
                        .then(Commands.literal("station").executes(DevFuelStation::build))));
    }

    private static int source(CommandContext<CommandSourceStack> context, Supplier<? extends Block> liquid) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!(player.pick(8.0D, 0.0F, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            context.getSource().sendFailure(Component.literal("Look at a block within 8 blocks."));
            return 0;
        }
        BlockPos target = player.level().getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        player.level().setBlock(target, liquid.get().defaultBlockState(), Block.UPDATE_ALL);
        context.getSource().sendSuccess(() -> Component.literal("Placed a " + liquid.get().getName().getString() + " source at " + target.toShortString()), false);
        return 1;
    }

    private static UndergroundFuelTankBlockEntity tankInView(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (player.pick(12.0D, 0.0F, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = player.level().getBlockState(hit.getBlockPos());
            if (state.getBlock() instanceof UndergroundFuelTankBlock
                    && player.level().getBlockEntity(UndergroundFuelTankBlock.masterPosition(hit.getBlockPos(), state)) instanceof UndergroundFuelTankBlockEntity tank) {
                return tank;
            }
        }
        context.getSource().sendFailure(Component.literal("Look at an underground fuel tank within 12 blocks."));
        return null;
    }

    private static int fill(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (player.pick(12.0D, 0.0F, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockEntity(hit.getBlockPos()) instanceof com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity can) {
            var held = can.tank().getFluid();
            var fuel = held.isEmpty() ? com.antaurora.apofirstlight.registry.AflFluids.GASOLINE.get() : held.getFluid();
            int filled = can.tank().fill(new FluidStack(fuel, Math.min(amount, can.tank().getCapacity())), IFluidHandler.FluidAction.EXECUTE);
            context.getSource().sendSuccess(() -> Component.literal("Filled " + filled + " mB; now " + can.tank().getFluidAmount() + " / " + can.tank().getCapacity() + " mB"), false);
            return filled > 0 ? 1 : 0;
        }
        UndergroundFuelTankBlockEntity tank = tankInView(context);
        if (tank == null) return 0;
        UndergroundFuelTankBlock block = (UndergroundFuelTankBlock) tank.getBlockState().getBlock();
        int filled = tank.tank().fill(new FluidStack(block.fuel(), Math.min(amount, UndergroundFuelTankBlockEntity.CAPACITY_MB)), IFluidHandler.FluidAction.EXECUTE);
        context.getSource().sendSuccess(() -> Component.literal("Filled " + filled + " mB; now " + tank.tank().getFluidAmount() + " / " + tank.tank().getCapacity() + " mB"), false);
        return filled > 0 ? 1 : 0;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UndergroundFuelTankBlockEntity tank = tankInView(context);
        if (tank == null) return 0;
        FluidStack stored = tank.tank().getFluid();
        context.getSource().sendSuccess(() -> Component.literal(tank.getBlockPos().toShortString() + " "
                + (stored.isEmpty() ? "empty" : stored.getDisplayName().getString() + " " + stored.getAmount()) + " / " + tank.tank().getCapacity() + " mB"), false);
        return 1;
    }
}
