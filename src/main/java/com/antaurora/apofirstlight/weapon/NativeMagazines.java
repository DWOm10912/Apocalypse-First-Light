package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Guns that keep their rounds in magazine items (docs/native_guns/magazine_system_v1.md), first the P9-01: its mount
 * names a standard magazine ({@link NativeMagazineMount#magazineItems}).
 * <ul>
 * <li>A magazine holds its rounds itself ({@code AflMagazine.rounds}). Empty magazines carry no rounds tag, so a new one
 * and an emptied one are the same stack.</li>
 * <li>The gun stores the fitted magazine in its MAGAZINE slot, without its rounds tag; its rounds are the gun's count
 * ({@link NativeGunAmmo#read}, which holds the chambered round too): in the magazine = count − 1 when chambered. The HUD
 * shows "16+1". An empty slot is no magazine at all (only the chambered round can be fired). A gun from before this
 * (or a new one before its first server tick: no mark) has its standard magazine fitted.</li>
 * <li>A reload swaps the fitted magazine for the fullest compatible one in the chest rig or the pockets (never the
 * backpack, the hotbar or the main inventory), at the reload's magazine-in moment. The magazine that comes out keeps its
 * rounds and goes where the new one was (the same item), else by the pickup order, else on the ground. Creative plays by
 * the same rules (2026-10-20: it used to fill the fitted magazine, so no magazine ever came out).</li>
 * <li>With no fuller magazine to swap in, the reload key takes the fitted magazine out into the hand ({@link #eject}),
 * to be loaded there (NativeMagazineLoading); the chambered round stays in the gun.</li>
 * </ul>
 */
public final class NativeMagazines {
    private static final String ROOT = "AflMagazine", ROUNDS = "rounds";
    /** Server game time the magazine's loading started at (NativeMagazineLoading); absent when it is not being loaded. */
    static final String LOADING = "loading";
    /** On the gun's ammo data: its MAGAZINE slot is the fitted magazine (empty = none), written on its first server tick. */
    static final String MARK = "magazines";
    /** On the gun's ammo data during a reload: the magazine going out and the one coming in, for the reload's bones. */
    private static final String SWAP = "swap";

    public enum Role { FITTED, INCOMING, OUTGOING }
    /** A magazine as a bone shows it: the item (empty: no magazine there) and its rounds. */
    public record Shown(ItemStack magazine, int rounds) {}

    private NativeMagazines() {}

    public static boolean system(NativeGunDefinition definition) {
        return definition != null && definition.magazineMount() != null && definition.magazineMount().magazineItems();
    }

    public static boolean system(ItemStack gun) {
        return gun.getItem() instanceof NativeGunItem item && system(item.definition());
    }

    // ---------------------------------------------------------------- the magazine item

    /** Rounds in a magazine item, 0..its capacity. */
    public static int rounds(ItemStack magazine) {
        if (!(magazine.getItem() instanceof NativeMagazineItem item)) return 0;
        CompoundTag tag = magazine.getTag();
        return tag == null ? 0 : Math.max(0, Math.min(item.capacity(), tag.getCompound(ROOT).getInt(ROUNDS)));
    }

    public static void setRounds(ItemStack magazine, int rounds) {
        if (!(magazine.getItem() instanceof NativeMagazineItem item)) return;
        CompoundTag root = magazine.getOrCreateTag().getCompound(ROOT);
        int n = Math.max(0, Math.min(item.capacity(), rounds));
        if (n > 0) root.putInt(ROUNDS, n); else root.remove(ROUNDS);
        write(magazine, root);
    }

    /** Server game time the loading started at, or -1 when the magazine is not being loaded. */
    public static long loadingSince(ItemStack magazine) {
        CompoundTag tag = magazine.getTag();
        return tag != null && tag.getCompound(ROOT).contains(LOADING, Tag.TAG_LONG) ? tag.getCompound(ROOT).getLong(LOADING) : -1;
    }

    static void setLoading(ItemStack magazine, long since) {
        CompoundTag root = magazine.getOrCreateTag().getCompound(ROOT);
        if (since >= 0) root.putLong(LOADING, since); else root.remove(LOADING);
        write(magazine, root);
    }

    private static void write(ItemStack magazine, CompoundTag root) {
        CompoundTag tag = magazine.getOrCreateTag();
        if (root.isEmpty()) tag.remove(ROOT); else tag.put(ROOT, root);
        if (tag.isEmpty()) magazine.setTag(null);
    }

    /** The stack's tag without its rounds and loading clock (re-equip comparison). */
    static CompoundTag tagWithoutRounds(ItemStack magazine) {
        CompoundTag copy = magazine.hasTag() ? magazine.getTag().copy() : new CompoundTag();
        copy.remove(ROOT);
        return copy;
    }

    /** A copy for the gun's MAGAZINE slot: one item, no rounds or loading clock (the gun's count holds them). */
    private static ItemStack forSlot(ItemStack magazine) {
        ItemStack copy = magazine.copyWithCount(1);
        if (copy.hasTag()) {
            copy.getTag().remove(ROOT);
            if (copy.getTag().isEmpty()) copy.setTag(null);
        }
        return copy;
    }

    // ---------------------------------------------------------------- the gun

    private static boolean marked(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        return tag != null && tag.getCompound("AflGunAmmo").getBoolean(MARK);
    }

    private static ItemStack standard(NativeGunDefinition definition) {
        var item = ForgeRegistries.ITEMS.getValue(definition.magazineMount().standard());
        return item instanceof NativeMagazineItem ? new ItemStack(item) : ItemStack.EMPTY;
    }

    /** The magazine in the gun (no rounds tag), empty when there is none. */
    public static ItemStack fitted(ItemStack gun, NativeGunDefinition definition) {
        if (!marked(gun) && NativeAttachments.stored(gun, NativeAttachment.Slot.MAGAZINE).isEmpty()) return standard(definition);
        return NativeAttachments.active(gun, NativeAttachment.Slot.MAGAZINE);
    }

    /** Server, once per gun (NativeGunAmmo#initialize): an old or new gun gets its standard magazine fitted. */
    static void normalize(ItemStack gun, NativeGunDefinition definition) {
        if (!system(definition) || marked(gun)) return;
        if (NativeAttachments.stored(gun, NativeAttachment.Slot.MAGAZINE).isEmpty())
            NativeAttachments.writeStored(gun, NativeAttachment.Slot.MAGAZINE, standard(definition));
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag ammo = tag.getCompound("AflGunAmmo");
        ammo.putBoolean(MARK, true);
        tag.put("AflGunAmmo", ammo);
    }

    /** The whole gun's capacity: the fitted magazine and the chamber; the chamber alone without a magazine. */
    static int capacity(ItemStack gun, NativeGunDefinition definition) {
        return fitted(gun, definition).getItem() instanceof NativeMagazineItem m ? m.capacity() + 1 : 1;
    }

    /** Whether a round is in the chamber (the count holds it): chambered and loaded. */
    public static int chamber(ItemStack gun, NativeGunDefinition definition) {
        return NativeGunAmmo.chambered(gun) && NativeGunAmmo.read(gun, definition) > 0 ? 1 : 0;
    }

    /** Rounds in the fitted magazine. */
    public static int magazineRounds(ItemStack gun, NativeGunDefinition definition) {
        return Math.max(0, NativeGunAmmo.read(gun, definition) - chamber(gun, definition));
    }

    /** Fits a magazine with these rounds, keeping the chambered round (server; the attachment slot is the caller's check). */
    static void fit(ItemStack gun, NativeGunDefinition definition, ItemStack magazine, int rounds) {
        int chamber = chamber(gun, definition);
        NativeAttachments.writeStored(gun, NativeAttachment.Slot.MAGAZINE, magazine.isEmpty() ? ItemStack.EMPTY : forSlot(magazine));
        normalize(gun, definition);
        NativeGunAmmo.set(gun, definition, chamber + (magazine.isEmpty() ? 0 : rounds));
    }

    /** The fitted magazine as an item to carry: the gun's magazine rounds written on it. */
    static ItemStack takenOut(ItemStack gun, NativeGunDefinition definition) {
        ItemStack fitted = fitted(gun, definition);
        if (fitted.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = fitted.copyWithCount(1);
        setRounds(out, magazineRounds(gun, definition));
        return out;
    }

    // ---------------------------------------------------------------- spares in the rig and the pockets

    private static boolean fits(ItemStack magazine, NativeGunDefinition definition) {
        return magazine.getItem() instanceof NativeMagazineItem m && m.accepts(definition) && definition.magazineMount() != null
                && definition.magazineMount().accepts().contains(ForgeRegistries.ITEMS.getKey(magazine.getItem()));
    }

    private static final PlayerGrids.Target[] SEARCHED = {PlayerGrids.Target.RIG, PlayerGrids.Target.POCKETS};

    /** The fullest compatible magazine with more rounds than {@code than}, rig first on a tie; null when there is none. */
    private static GridStorage.Entry best(PlayerGrids.View view, NativeGunDefinition definition, int than) {
        GridStorage.Entry best = null;
        int most = than;
        for (PlayerGrids.Target target : SEARCHED) {
            GridStorage storage = view.get(target);
            if (storage == null) continue;
            for (GridStorage.Entry entry : storage.entries()) {
                int r = fits(entry.stack(), definition) ? rounds(entry.stack()) : -1;
                if (r > most) {
                    most = r;
                    best = entry;
                }
            }
        }
        return best;
    }

    /** Spare magazines in the rig and the pockets: [count, rounds] (client: its synced copies; the HUD). */
    public static int[] spares(Player player, NativeGunDefinition definition) {
        var view = PlayerGrids.view(player);
        int count = 0, total = 0;
        for (PlayerGrids.Target target : SEARCHED) {
            GridStorage storage = view.get(target);
            if (storage == null) continue;
            for (GridStorage.Entry entry : storage.entries())
                if (fits(entry.stack(), definition)) {
                    count++;
                    total += rounds(entry.stack());
                }
        }
        return new int[]{count, total};
    }

    /** A reload would change something: a fuller spare than the fitted magazine in the rig or the pockets (creative too). */
    public static boolean canReload(Player player, ItemStack gun, NativeGunDefinition definition) {
        return best(PlayerGrids.view(player), definition, fitted(gun, definition).isEmpty() ? 0 : magazineRounds(gun, definition)) != null;
    }

    /**
     * The reload key would take the fitted magazine out into the hand instead (user 2026-10-20): no fuller spare to swap
     * in, and the fitted one is not full (a full one stays in: nothing to load).
     */
    public static boolean ejectable(Player player, ItemStack gun, NativeGunDefinition definition) {
        ItemStack fitted = fitted(gun, definition);
        return !fitted.isEmpty() && fitted.getItem() instanceof NativeMagazineItem m && magazineRounds(gun, definition) < m.capacity()
                && !canReload(player, gun, definition);
    }

    /**
     * Server: the fitted magazine out of the gun into the hand (inventory/grid/Loadout#toHand: the hands box, the gun
     * staying in its weapon position), with its rounds; the chambered round stays. Where the hand cannot be freed: by the
     * pickup order, else on the ground. Silent: the unload clip's own cues are the sound (NativeGunActions#unload).
     */
    public static boolean eject(ServerPlayer player, ItemStack gun, NativeGunDefinition definition) {
        ItemStack out = takenOut(gun, definition);
        if (out.isEmpty()) return false;
        fit(gun, definition, ItemStack.EMPTY, 0);
        if (com.antaurora.apofirstlight.inventory.grid.Loadout.toHand(player, out)) return true;
        player.getInventory().add(out);
        if (!out.isEmpty()) player.drop(out, false);
        return true;
    }

    /** A magazine item loaded to capacity (the creative tab's full magazines). */
    public static ItemStack full(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        if (item instanceof NativeMagazineItem m) setRounds(stack, m.capacity());
        return stack;
    }

    /** Server, at the reload's start: the magazines its bones show going out and coming in. */
    static void beginSwap(ServerPlayer player, ItemStack gun, NativeGunDefinition definition) {
        ItemStack out = fitted(gun, definition);
        var entry = best(PlayerGrids.view(player), definition, out.isEmpty() ? 0 : magazineRounds(gun, definition));
        ItemStack in = entry == null ? ItemStack.EMPTY : entry.stack();
        int inRounds = entry == null ? 0 : rounds(entry.stack());
        CompoundTag swap = new CompoundTag();
        swap.putString("from", out.isEmpty() ? "" : ForgeRegistries.ITEMS.getKey(out.getItem()).toString());
        swap.putInt("fromRounds", magazineRounds(gun, definition));
        swap.putString("to", in.isEmpty() ? "" : ForgeRegistries.ITEMS.getKey(in.getItem()).toString());
        swap.putInt("toRounds", inRounds);
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag ammo = tag.getCompound("AflGunAmmo");
        ammo.put(SWAP, swap);
        tag.put("AflGunAmmo", ammo);
    }

    /** Server: the reload is over (done or cut short); its bones show the fitted magazine again. */
    static void endSwap(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        if (tag != null && tag.getCompound("AflGunAmmo").contains(SWAP)) tag.getCompound("AflGunAmmo").remove(SWAP);
    }

    static boolean swapping(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        return tag != null && tag.getCompound("AflGunAmmo").contains(SWAP);
    }

    /**
     * Server, at the reload's magazine-in moment: the swap. Returns whether a magazine went in. The rig and pocket search
     * runs again now (no magazine is reserved at the reload's start).
     */
    public static boolean swap(ServerPlayer player, ItemStack gun, NativeGunDefinition definition) {
        ItemStack out = takenOut(gun, definition);
        var view = PlayerGrids.view(player);
        var entry = best(view, definition, out.isEmpty() ? 0 : rounds(out));
        if (entry == null) return false;
        ItemStack in = entry.stack().copyWithCount(1);
        int inRounds = rounds(in);
        boolean returned = false;
        if (!out.isEmpty() && out.getItem() == in.getItem() && entry.stack().getCount() == 1) {
            // the same magazine type: the old one takes the new one's place in the grid
            entry.stack().setTag(out.getTag() == null ? null : out.getTag().copy());
            returned = true;
        } else {
            entry.stack().shrink(1);
        }
        view.commit();
        fit(gun, definition, in, inRounds);
        if (!out.isEmpty() && !returned) {
            player.getInventory().add(out);   // the pickup order: pockets, rig, backpack
            if (!out.isEmpty()) player.drop(out, false);
        }
        return true;
    }

    // ---------------------------------------------------------------- what the bones show

    /** The magazine a bone of this role shows (client): a reload's going-out and coming-in ones, else the fitted one. */
    public static Shown shown(ItemStack gun, NativeGunDefinition definition, Role role) {
        CompoundTag tag = gun.getTag();
        CompoundTag swap = tag == null ? null : tag.getCompound("AflGunAmmo").getCompound(SWAP);
        if (role != Role.FITTED && swap != null && !swap.isEmpty()) {
            boolean in = role == Role.INCOMING;
            String id = swap.getString(in ? "to" : "from");
            var item = id.isEmpty() ? null : ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
            return new Shown(item instanceof NativeMagazineItem ? new ItemStack(item) : ItemStack.EMPTY,
                    swap.getInt(in ? "toRounds" : "fromRounds"));
        }
        return new Shown(fitted(gun, definition), magazineRounds(gun, definition));
    }
}
