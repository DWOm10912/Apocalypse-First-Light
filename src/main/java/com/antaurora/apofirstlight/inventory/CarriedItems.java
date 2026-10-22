package com.antaurora.apofirstlight.inventory;

import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a player carries on the body: the hands and weapon positions (the vanilla inventory) and the grids (pockets, rig,
 * backpack). The workbench's crafting page and the building hammer count, take and give through here, so the numbers a
 * screen shows are the ones the server checks.
 */
public final class CarriedItems {
    private CarriedItems() {}

    /** How many of the item the player carries (client or server). */
    public static int count(Player player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(item)) n += stack.getCount();
        for (ItemStack stack : PlayerGrids.stacks(player)) if (stack.is(item)) n += stack.getCount();
        return n;
    }

    /** Server: takes up to n of the item, the inventory first, then the grids; returns how many were taken. */
    public static int take(ServerPlayer player, Item item, int n) {
        int left = n;
        var inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            if (left == 0) break;
            if (!stack.is(item)) continue;
            int k = Math.min(left, stack.getCount());
            stack.shrink(k);
            left -= k;
        }
        inventory.setChanged();
        if (left > 0) {
            var view = PlayerGrids.view(player);
            boolean changed = false;
            for (ItemStack stack : view.stacks()) {
                if (left == 0) break;
                if (!stack.is(item)) continue;
                int k = Math.min(left, stack.getCount());
                stack.shrink(k);
                left -= k;
                changed = true;
            }
            if (changed) view.commit();
        }
        return n - left;
    }

    /** Server: into the grids where it fits, else the inventory, the rest dropped at the player's feet. */
    public static void give(ServerPlayer player, ItemStack stack) {
        PlayerGrids.insert(player, stack);
        if (!stack.isEmpty()) player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
    }
}
