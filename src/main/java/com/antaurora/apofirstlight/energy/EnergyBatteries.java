package com.antaurora.apofirstlight.energy;

import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.item.EnergyBatteryItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * Spare power for rechargeable tools: when a tool runs dry it can draw from charged Energy Batteries the player carries
 * (main inventory, hotbar and offhand, in slot order, then the pockets, rig and backpack), with no screen. Server side only.
 */
public final class EnergyBatteries {
    private EnergyBatteries() {
    }

    /** FE stored in all Energy Batteries the player carries. */
    public static long storedInInventory(Player player) {
        long total = 0;
        for (ItemStack stack : player.getInventory().items) total += stored(stack);
        for (ItemStack stack : player.getInventory().offhand) total += stored(stack);
        for (ItemStack stack : PlayerGrids.stacks(player)) total += stored(stack);
        return total;
    }

    /** Draws up to {@code amount} FE from the carried batteries (each limited by its own per-call extract rate). */
    public static int drawFromInventory(Player player, int amount, boolean simulate) {
        int remaining = Math.max(0, amount);
        for (ItemStack stack : player.getInventory().items) remaining -= draw(stack, remaining, simulate);
        for (ItemStack stack : player.getInventory().offhand) remaining -= draw(stack, remaining, simulate);
        if (remaining > 0 && !player.level().isClientSide) {
            var grids = PlayerGrids.view(player);
            int before = remaining;
            for (ItemStack stack : grids.stacks()) remaining -= draw(stack, remaining, simulate);
            if (!simulate && remaining < before) grids.commit();
        }
        return Math.max(0, amount) - remaining;
    }

    private static long stored(ItemStack stack) {
        return stack.getItem() instanceof EnergyBatteryItem ? EnergyBatteryItem.energy(stack) : 0;
    }

    private static int draw(ItemStack stack, int amount, boolean simulate) {
        if (amount <= 0 || !(stack.getItem() instanceof EnergyBatteryItem)) return 0;
        return stack.getCapability(ForgeCapabilities.ENERGY).map(storage -> storage.extractEnergy(amount, simulate)).orElse(0);
    }
}
