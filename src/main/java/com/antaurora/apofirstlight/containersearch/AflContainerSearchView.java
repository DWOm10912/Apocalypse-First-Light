package com.antaurora.apofirstlight.containersearch;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side masked facade handed to search menus as their container. Hidden slots read as empty and reject
 * every write, so the real ItemStack of a hidden slot can never reach a menu, a slot listener or a packet.
 * The real inventory stays untouched inside the owning block entity.
 */
public final class AflContainerSearchView implements Container, AflContainerSearchSlot.RevealMask {
    private final AflSearchableContainer owner;

    public AflContainerSearchView(AflSearchableContainer owner) {
        this.owner = owner;
    }

    public AflSearchableContainer owner() {
        return owner;
    }

    @Override
    public boolean isSlotRevealed(int slot) {
        return owner.aflSearchState().isRevealed(owner, slot);
    }

    @Override
    public int getContainerSize() {
        return owner.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (isSlotRevealed(slot) && !owner.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return isSlotRevealed(slot) ? owner.getItem(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return isSlotRevealed(slot) ? owner.removeItem(slot, amount) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return isSlotRevealed(slot) ? owner.removeItemNoUpdate(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (isSlotRevealed(slot)) {
            owner.setItem(slot, stack);
        }
    }

    @Override
    public int getMaxStackSize() {
        return owner.getMaxStackSize();
    }

    @Override
    public void setChanged() {
        owner.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner.stillValid(player);
    }

    @Override
    public void startOpen(Player player) {
        owner.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        owner.stopOpen(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isSlotRevealed(slot) && owner.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return isSlotRevealed(slot) && owner.canTakeItem(target, slot, stack);
    }

    /** Clears revealed slots only; hidden contents are never touched through the facade. */
    @Override
    public void clearContent() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (isSlotRevealed(slot)) {
                owner.setItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
