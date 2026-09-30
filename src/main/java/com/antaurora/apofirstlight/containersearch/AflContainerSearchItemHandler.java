package com.antaurora.apofirstlight.containersearch;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * Forge automation view of a searchable container (hoppers, droppers, pipes, other mods). Hidden slots read as
 * empty and refuse insertion and extraction; revealed slots keep the normal {@link InvWrapper} behavior.
 */
public final class AflContainerSearchItemHandler extends InvWrapper {
    private final AflSearchableContainer owner;

    public AflContainerSearchItemHandler(AflSearchableContainer owner) {
        super(owner);
        this.owner = owner;
    }

    private boolean revealed(int slot) {
        return owner.aflSearchState().isRevealed(owner, slot);
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        return revealed(slot) ? super.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return revealed(slot) ? super.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return revealed(slot) ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (revealed(slot)) {
            super.setStackInSlot(slot, stack);
        }
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return revealed(slot) && super.isItemValid(slot, stack);
    }
}
