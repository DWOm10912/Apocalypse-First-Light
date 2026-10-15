package com.antaurora.apofirstlight.inventory;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.grid.GunSling;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.WeaponClass;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The hotbar's roles in Survival / Adventure (docs/ui/afl_inventory_grid_v0_plan.md 10): three weapon positions and the
 * hand. Index 0 (key 1) is the one primary weapon, carried on the back only with a sling fitted (GunSling); index 1
 * (key 2) the pistol, carried only in a worn holster; index 2 (key 3) a melee weapon; index 3 is the hand: what a quick
 * binding (keys 4-9, Loadout) or the inventory page's hands box puts there. Indices 4-8 are not used (locked). A weapon
 * is what its item tag says ({@code apocalypse_firstlight:weapons/primary}, {@code /secondary}, {@code /melee}); native
 * guns without a tag go by their weapon class (pistols secondary, every other class primary).
 */
public final class HotbarRoles {
    public enum Role { PRIMARY, SECONDARY, MELEE, HAND }

    public static final TagKey<Item> PRIMARY = tag("weapons/primary");
    public static final TagKey<Item> SECONDARY = tag("weapons/secondary");
    public static final TagKey<Item> MELEE = tag("weapons/melee");
    public static final int PRIMARY_SLOT = 0, PISTOL_SLOT = 1, MELEE_SLOT = 2, HAND_SLOT = 3;
    /** The hotbar indices in use (0..3); the rest of the hotbar stays empty. */
    public static final int USED = 4;

    private HotbarRoles() {}

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(ApocalypseFirstLight.MOD_ID, path));
    }

    /** The role of a hotbar index, or null for any other index. */
    @Nullable
    public static Role roleOf(int index) {
        return switch (index) {
            case PRIMARY_SLOT -> Role.PRIMARY;
            case PISTOL_SLOT -> Role.SECONDARY;
            case MELEE_SLOT -> Role.MELEE;
            case HAND_SLOT -> Role.HAND;
            default -> null;
        };
    }

    /** The weapon role an item can fill, or null for a non-weapon. */
    @Nullable
    public static Role weaponRole(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(PRIMARY)) return Role.PRIMARY;
        if (stack.is(SECONDARY)) return Role.SECONDARY;
        if (stack.is(MELEE)) return Role.MELEE;
        if (stack.getItem() instanceof NativeGunItem gun) {
            try {
                var definition = gun.definition();
                if (definition != null) return definition.weaponClass() == WeaponClass.PISTOL ? Role.SECONDARY : Role.PRIMARY;
            } catch (RuntimeException ignored) {
                // gun data missing: treat as a non-weapon rather than fail an inventory click
            }
        }
        return null;
    }

    /** Whether the player wears a pistol holster (the only way to carry a pistol at index 1). */
    public static boolean holsterWorn(Player player) {
        return !AflEquipmentSlots.get(player, AflEquipmentSlots.HOLSTER).isEmpty();
    }

    /**
     * Whether the hotbar index takes the stack by role: the primary only with a sling, the pistol only with a holster
     * worn, the hand anything. Indices 4-8 never (not used); indices outside the hotbar always.
     */
    public static boolean accepts(Player player, int index, ItemStack stack) {
        if (stack.isEmpty() || index >= 9 || index < 0) return true;
        Role role = roleOf(index);
        if (role == null) return false;
        return switch (role) {
            case PRIMARY -> weaponRole(stack) == Role.PRIMARY && GunSling.has(stack);
            case SECONDARY -> weaponRole(stack) == Role.SECONDARY && holsterWorn(player);
            case MELEE -> weaponRole(stack) == Role.MELEE;
            case HAND -> true;
        };
    }

    /** The weapon position a role is carried in (HAND: the hand). */
    public static int slotOf(Role role) {
        return switch (role) {
            case PRIMARY -> PRIMARY_SLOT;
            case SECONDARY -> PISTOL_SLOT;
            case MELEE -> MELEE_SLOT;
            case HAND -> HAND_SLOT;
        };
    }
}
