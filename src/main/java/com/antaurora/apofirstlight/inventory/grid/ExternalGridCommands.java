package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Test commands for external grids (permission 2, docs/ui/afl_inventory_grid_v0_plan.md 15):
 * <ul><li>{@code /afl dev grid external [w h]}: a test crate's grid (default 9 x 7), one per player, kept until the
 * server stops; usable within 8 blocks of where it was opened;</li>
 * <li>{@code /afl dev grid truck}: the cargo bed of the nearest T15 within 16 blocks, until the truck hosts its grid
 * itself (Main); usable within 8 blocks of the truck.</li></ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExternalGridCommands {
    private static final double REACH = 8;
    private static final SimpleCommandExceptionType NO_TRUCK = new SimpleCommandExceptionType(Component.literal("No T15 truck within 16 blocks"));
    private static final Map<UUID, Crate> CRATES = new HashMap<>();
    private static final Map<PickupTruckEntity, TruckBed> BEDS = new WeakHashMap<>();

    private ExternalGridCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("grid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("external")
                        .executes(c -> crate(c, 9, 7))
                        .then(Commands.argument("w", IntegerArgumentType.integer(1, 12))
                                .then(Commands.argument("h", IntegerArgumentType.integer(1, 10))
                                        .executes(c -> crate(c, IntegerArgumentType.getInteger(c, "w"), IntegerArgumentType.getInteger(c, "h"))))))
                .then(Commands.literal("truck").executes(ExternalGridCommands::truck)))));
    }

    private static int crate(CommandContext<CommandSourceStack> c, int w, int h) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        var crate = CRATES.get(player.getUUID());
        if (crate == null || crate.grid.layout().section(0).w() != w || crate.grid.layout().section(0).h() != h) {
            if (crate != null) for (var stack : crate.grid.stacks()) if (!stack.isEmpty()) player.drop(stack.copy(), false);
            crate = new Crate(new GridStorage(GridLayout.of(w, h), false));
            CRATES.put(player.getUUID(), crate);
        }
        crate.at = player.position();
        ExternalGrids.open(player, crate, -1);
        return 1;
    }

    private static int truck(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        var truck = player.level().getEntitiesOfClass(PickupTruckEntity.class, player.getBoundingBox().inflate(16)).stream()
                .min(Comparator.comparingDouble(t -> t.distanceToSqr(player))).orElseThrow(NO_TRUCK::create);
        var bed = BEDS.computeIfAbsent(truck, TruckBed::new);
        ExternalGrids.open(player, bed, truck.getId());
        return 1;
    }

    /** The test crate: a grid at the place it was opened. */
    private static final class Crate implements ExternalGridHost {
        final GridStorage grid;
        Vec3 at = Vec3.ZERO;

        Crate(GridStorage grid) { this.grid = grid; }

        @Override public GridStorage grid() { return grid; }
        @Override public Component title() { return Component.translatable("container.apocalypse_firstlight.dev_external_grid"); }
        @Override public boolean stillValid(Player player) { return player.isAlive() && player.position().distanceTo(at) <= REACH; }
        @Override public void onGridChanged() { grid.prune(); }
    }

    /** The truck's bed through its cargo grid (the truck saves and draws it). */
    private record TruckBed(PickupTruckEntity truck) implements ExternalGridHost {
        @Override public GridStorage grid() { return truck.cargo(); }
        @Override public Component title() { return truck.getName(); }
        @Override public boolean stillValid(Player player) { return !truck.isRemoved() && player.isAlive() && player.distanceTo(truck) <= REACH; }
        @Override public void onGridChanged() { truck.cargoChanged(); }
    }
}
