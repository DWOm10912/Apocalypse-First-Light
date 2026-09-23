package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.ItemStack;

/** Fixed lower-then-upper two-chamber interpretation of the existing synced ammo count. */
public final class NativeBreakActionChambers {
    public enum State { LIVE, SPENT }
    public record Pair(State lower, State upper) {}

    private NativeBreakActionChambers() {}

    public static Pair read(ItemStack stack, NativeGunDefinition definition) {
        if (definition.actionType() != NativeActionType.BREAK_ACTION || definition.magazineCapacity() != 2)
            throw new IllegalArgumentException("Expected a two-chamber break action");
        int count = NativeGunAmmo.read(stack, definition);
        return new Pair(count == 2 ? State.LIVE : State.SPENT,
                count >= 1 ? State.LIVE : State.SPENT);
    }
}
