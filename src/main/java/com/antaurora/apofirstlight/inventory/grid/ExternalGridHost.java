package com.antaurora.apofirstlight.inventory.grid;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Something outside the player that owns a grid players open and use like their own (docs/ui/afl_inventory_grid_v0_plan.md
 * 15): the T15 pickup's cargo bed. {@link ExternalGrids#open} shows it; {@link GridActions} changes it on the server
 * (take, put, merge, turn, shift between it and the player's grids, Q), each action checked against
 * {@link #stillValid}; after a change it calls {@link #onGridChanged} and sends the grid to everyone looking at it.
 */
public interface ExternalGridHost {
    /** The live storage, server side: the actions change it in place. Its layout is {@code grid().layout()}. */
    GridStorage grid();

    /** The panel's title. */
    Component title();

    /** Whether the player may still use the grid (checked before every action and every tick; false closes the panel). */
    boolean stillValid(Player player);

    /** The grid changed (an action, server side): save it, update what shows it. The open panels are synced already. */
    void onGridChanged();
}
