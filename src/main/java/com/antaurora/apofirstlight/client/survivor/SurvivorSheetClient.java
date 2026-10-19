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

    /**
     * The held gun's sway from its class's growth level (docs/gameplay/survivor_background_skills_v1_plan.md 7, P3):
     * 1.25 at level 0, 0.05 less per level (an ordinary survivor's level 1 = 1.2, the top 10 = 0.75). 1 without a set-up
     * survivor in the sheet.
     */
    /** Aiming sway with this gun class (SurvivorGrowth#adsSway); 1 without a set-up survivor. */
    public static float gunAdsSway(com.antaurora.apofirstlight.weapon.WeaponClass weaponClass) {
        int level = growthLevel(com.antaurora.apofirstlight.survivor.SurvivorGrowth.gunItem(weaponClass));
        return level < 0 ? 1 : com.antaurora.apofirstlight.survivor.SurvivorGrowth.adsSway(level);
    }

    /** Recoil recovery speed with this gun class (SurvivorGrowth#recoilRecovery); 1 without a set-up survivor. */
    public static float gunRecoilRecovery(com.antaurora.apofirstlight.weapon.WeaponClass weaponClass) {
        int level = growthLevel(com.antaurora.apofirstlight.survivor.SurvivorGrowth.gunItem(weaponClass));
        return level < 0 ? 1 : com.antaurora.apofirstlight.survivor.SurvivorGrowth.recoilRecovery(level);
    }

    /** The perk a growth item teaches at this level (the sheet's Perks), or null. */
    @org.jetbrains.annotations.Nullable
    public static String perk(String id, int level) {
        var list = sheet.getCompound("View").getList("Growth", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag g = list.getCompound(i);
            if (id.equals(g.getString("Id"))) {
                String perk = g.getCompound("Perks").getString(Integer.toString(level));
                return perk.isEmpty() ? null : perk;
            }
        }
        return null;
    }

    /** A growth item's level from the sheet's view; -1 when there is no set-up survivor or no such item. */
    public static int growthLevel(String id) {
        if (!com.antaurora.apofirstlight.survivor.SurvivorSetup.DONE.equals(sheet.getString("Setup"))) return -1;
        var list = sheet.getCompound("View").getList("Growth", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag g = list.getCompound(i);
            if (id.equals(g.getString("Id"))) return g.getInt("Lv");
        }
        return -1;
    }

    static void clear() {
        sheet = new CompoundTag();
    }
}
