package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.ItemStack;

/** Fixed right-then-left (side-by-side) two-chamber interpretation of the existing synced ammo count. */
public final class NativeBreakActionChambers {
    /** EMPTY: no shell at all, only in a new gun that was never loaded (new guns start empty, user 2026-10-19). */
    public enum State { LIVE, SPENT, EMPTY }
    public record Pair(State right, State left) {}

    private NativeBreakActionChambers() {}

    public static Pair read(ItemStack stack, NativeGunDefinition definition) {
        if (definition.actionType() != NativeActionType.BREAK_ACTION || definition.magazineCapacity() != 2)
            throw new IllegalArgumentException("Expected a two-chamber break action");
        int count = NativeGunAmmo.read(stack, definition);
        if (count == 0 && !NativeGunAmmo.everLoaded(stack)) return new Pair(State.EMPTY, State.EMPTY);
        return new Pair(count == 2 ? State.LIVE : State.SPENT,
                count >= 1 ? State.LIVE : State.SPENT);
    }
}
