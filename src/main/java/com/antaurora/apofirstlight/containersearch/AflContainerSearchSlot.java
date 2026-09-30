package com.antaurora.apofirstlight.containersearch;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A container slot that is completely inert while hidden: it shows nothing and accepts no pickup, placement,
 * merge or swap. Revealed slots behave exactly like vanilla slots.
 *
 * <p>Writes ({@code set}) are deliberately not blocked here: on the client they carry the server's authoritative
 * slot updates, and on the server the masked {@link AflContainerSearchView} already refuses hidden writes.
 */
public class AflContainerSearchSlot extends Slot {
    /** Implemented by the server view and the client mirror behind a search menu. */
    public interface RevealMask {
        boolean isSlotRevealed(int slot);
    }

    public AflContainerSearchSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    public boolean isRevealed() {
        return !(container instanceof RevealMask mask) || mask.isSlotRevealed(getContainerSlot());
    }

    @Override
    public ItemStack getItem() {
        return isRevealed() ? super.getItem() : ItemStack.EMPTY;
    }

    @Override
    public boolean hasItem() {
        return isRevealed() && super.hasItem();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return isRevealed() && super.mayPlace(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return isRevealed() && super.mayPickup(player);
    }

    @Override
    public boolean allowModification(Player player) {
        return isRevealed() && super.allowModification(player);
    }

    @Override
    public ItemStack remove(int amount) {
        return isRevealed() ? super.remove(amount) : ItemStack.EMPTY;
    }

    @Override
    public boolean isHighlightable() {
        return isRevealed() && super.isHighlightable();
    }
}
