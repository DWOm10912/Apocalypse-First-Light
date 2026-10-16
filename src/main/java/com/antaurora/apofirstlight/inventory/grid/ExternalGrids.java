package com.antaurora.apofirstlight.inventory.grid;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

/**
 * Server: opening an {@link ExternalGridHost}'s grid and keeping everyone looking at it current
 * (docs/ui/afl_inventory_grid_v0_plan.md 15).
 */
public final class ExternalGrids {
    private ExternalGrids() {}

    /**
     * Opens the host's grid for the player (the host checks reach and use first, e.g. at the cargo bed's side).
     * {@code hostEntityId}: the entity the grid belongs to, for the client (-1 for none).
     */
    public static void open(ServerPlayer player, ExternalGridHost host, int hostEntityId) {
        if (!host.stillValid(player)) return;
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, p) -> new ExternalGridMenu(id, inventory, host, hostEntityId), host.title()),
                buf -> ExternalGridMenu.writeOpen(buf, host, hostEntityId));
    }

    /** The host's grid changed: every player with it open gets it again. */
    public static void contentsChanged(ExternalGridHost host) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        net.minecraft.nbt.ListTag items = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof ExternalGridMenu menu) || menu.host() != host) continue;
            if (items == null) items = host.grid().save();
            GridPackets.sendExternal(player, menu.containerId, items);
        }
    }
}
