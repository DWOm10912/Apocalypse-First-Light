package com.antaurora.apofirstlight.client.survivor;

import net.minecraft.nbt.CompoundTag;

/**
 * The player's own survivor as the server last sent it (SurvivorSetupFlow#sheet: name, setup, scene, background, traits,
 * body and growth levels). Kept for the character page; cleared on logout. Shows what the server sent; decides nothing.
 */
public final class SurvivorSheetClient {
    private static CompoundTag sheet = new CompoundTag();

    private SurvivorSheetClient() {
    }

    public static void set(CompoundTag record) {
        sheet = record == null ? new CompoundTag() : record;
    }

    public static CompoundTag get() {
        return sheet;
    }

    static void clear() {
        sheet = new CompoundTag();
    }
}
