package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.inventory.grid.Pockets;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * P9-01 magazines (docs/native_guns/magazine_system_v1.md 9): the fitted magazine and the chamber, reload swaps from the rig
 * and the pockets only, rounds conserved through swaps and the maintenance bench's magazine change, loading by hand.
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagazineSystemGameTests {
    private static ServerPlayer survivor(GameTestHelper h, String name) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        var pockets = Pockets.of(player);
        if (pockets != null) pockets.set(new ListTag());
        for (String slot : AflEquipmentSlots.INVENTORY_PAGE)
            AflEquipmentSlots.stacks(player, slot).ifPresent(s -> { for (int i = 0; i < s.getSlots(); i++) s.setStackInSlot(i, ItemStack.EMPTY); });
        return player;
    }

    private static void wear(ServerPlayer player, String slot, ItemStack stack) {
        AflEquipmentSlots.stacks(player, slot).ifPresent(s -> s.setStackInSlot(0, stack));
    }

    private static ItemStack magazine(Item item, int rounds) {
        var stack = new ItemStack(item);
        NativeMagazines.setRounds(stack, rounds);
        return stack;
    }

    private static void put(ServerPlayer player, PlayerGrids.Target target, ItemStack stack) {
        var view = PlayerGrids.view(player);
        if (!view.get(target).insert(stack)) throw new IllegalStateException("no room in " + target);
        view.commit();
    }

    /** Rounds of every magazine of this item in the grids, in grid order. */
    private static java.util.List<Integer> carried(ServerPlayer player, Item item) {
        return PlayerGrids.stacks(player).stream().filter(s -> s.is(item)).map(NativeMagazines::rounds).sorted().toList();
    }

    private static ItemStack p9(int count, boolean chambered) {
        var d = NativeGunDefinition.P9_01;
        var gun = new ItemStack(AflItems.P9_01.get());
        NativeGunAmmo.initialize(gun, d);
        NativeGunAmmo.set(gun, d, count);
        if (chambered) NativeGunAmmo.setChambered(gun, d, true);
        return gun;
    }

    @GameTest(template = "network_empty")
    public static void magazineFitted(GameTestHelper h) {
        var d = NativeGunDefinition.P9_01;
        h.assertTrue(NativeMagazines.system(d), "the P9-01 loads from magazine items");
        var fresh = new ItemStack(AflItems.P9_01.get());
        h.assertTrue(NativeMagazines.fitted(fresh, d).is(AflItems.P9_01_MAGAZINE.get()), "a new gun has its standard magazine before its first tick");
        h.assertTrue(NativeGunAmmo.capacity(fresh, d) == 18 && NativeGunAmmo.read(fresh, d) == 0, "17 + 1, empty");
        // a gun from before magazines: 17 loose rounds in it, no mark
        var old = new ItemStack(AflItems.P9_01.get());
        NativeGunAmmo.set(old, d, 17);
        NativeGunAmmo.initialize(old, d);
        h.assertTrue(NativeAttachments.stored(old, NativeAttachment.Slot.MAGAZINE).is(AflItems.P9_01_MAGAZINE.get()), "the standard magazine is stored once marked");
        h.assertTrue(NativeGunAmmo.read(old, d) == 17 && NativeMagazines.magazineRounds(old, d) == 17, "its rounds are kept, in the magazine");
        var gun = p9(18, true);
        h.assertTrue(NativeMagazines.magazineRounds(gun, d) == 17 && NativeMagazines.chamber(gun, d) == 1, "17 in the magazine + 1");
        h.assertTrue(BR51Probe.magazineItems(), "the BR-51 V3 loads from magazine items too (20 + 1)");
        h.succeed();
    }

    @GameTest(template = "network_empty")
    public static void reloadSwapsFromRigAndPockets(GameTestHelper h) {
        var d = NativeGunDefinition.P9_01;
        var player = survivor(h, "mag_swap");
        var standard = AflItems.P9_01_MAGAZINE.get();
        wear(player, AflEquipmentSlots.RIG, new ItemStack(AflItems.FIELD_CHEST_RIG.get()));
        wear(player, AflEquipmentSlots.BACK, new ItemStack(AflItems.CANVAS_BACKPACK.get()));
        put(player, PlayerGrids.Target.BACKPACK, magazine(standard, 17));
        var gun = p9(4, true);   // 3 + 1
        h.assertTrue(!NativeMagazines.canReload(player, d) && NativeMagazines.inBackpack(player, d), "the backpack is never searched");
        put(player, PlayerGrids.Target.RIG, magazine(standard, 12));
        put(player, PlayerGrids.Target.POCKETS, magazine(standard, 15));
        h.assertTrue(NativeMagazines.canReload(player, d), "a loaded magazine in the rig or the pockets");
        h.assertTrue(NativeMagazines.spares(player, d)[0] == 2 && NativeMagazines.spares(player, d)[1] == 27, "spares: rig and pockets only");
        h.assertTrue(NativeMagazines.swap(player, gun, d), "swap");
        h.assertTrue(NativeMagazines.magazineRounds(gun, d) == 15 && NativeGunAmmo.read(gun, d) == 16, "the fullest went in, the chamber kept: 15 + 1");
        h.assertTrue(carried(player, standard).equals(java.util.List.of(3, 12, 17)), "the old one came out with its 3 rounds " + carried(player, standard));
        var pockets = PlayerGrids.view(player).get(PlayerGrids.Target.POCKETS);
        h.assertTrue(pockets.entries().stream().anyMatch(e -> e.stack().is(standard) && NativeMagazines.rounds(e.stack()) == 3),
                "it took the new one's place in the pockets");
        // held on purpose (user 2026-10-22): the next swap takes the fullest other loaded one, fewer rounds or not
        h.assertTrue(NativeMagazines.swap(player, gun, d) && NativeMagazines.magazineRounds(gun, d) == 12,
                "the 12-round one goes in for the 15: " + NativeMagazines.magazineRounds(gun, d));
        h.succeed();
    }

    @GameTest(template = "network_empty")
    public static void maintenanceMagazineChangeKeepsRounds(GameTestHelper h) {
        var d = NativeGunDefinition.P9_01;
        var player = survivor(h, "mag_bench");
        var extended = AflItems.P9_01_EXTENDED_MAGAZINE.get();
        var gun = p9(11, true);   // 10 + 1
        player.getInventory().setItem(0, gun);
        player.getInventory().setItem(9, magazine(extended, 20));
        var published = new ItemStack[1];
        h.assertTrue(AttachmentInteractionCore.commit(player, gun, NativeAttachment.Slot.MAGAZINE, 9,
                player.getInventory().getItem(9).copy(), s -> published[0] = s), "install the extended magazine");
        var after = published[0];
        h.assertTrue(NativeMagazines.fitted(after, d).is(extended) && NativeGunAmmo.read(after, d) == 21
                && NativeGunAmmo.capacity(after, d) == 29, "20 + 1 with the extended one");
        h.assertTrue(NativeMagazines.rounds(NativeAttachments.stored(after, NativeAttachment.Slot.MAGAZINE)) == 0, "the fitted copy carries no rounds tag");
        var out = PlayerGrids.stacks(player).stream().filter(s -> s.is(AflItems.P9_01_MAGAZINE.get())).findFirst()
                .orElse(player.getInventory().items.stream().filter(s -> s.is(AflItems.P9_01_MAGAZINE.get())).findFirst().orElse(ItemStack.EMPTY));
        h.assertTrue(NativeMagazines.rounds(out) == 10, "the standard one came off with its 10 rounds");
        h.assertTrue(AttachmentInteractionCore.commit(player, after, NativeAttachment.Slot.MAGAZINE, -1, ItemStack.EMPTY, s -> published[0] = s),
                "pull the magazine");
        h.assertTrue(NativeMagazines.fitted(published[0], d).isEmpty() && NativeGunAmmo.read(published[0], d) == 1
                && NativeGunAmmo.capacity(published[0], d) == 1, "no magazine: the chambered round only");
        h.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 120)
    public static void loadingByHand(GameTestHelper h) {
        var d = NativeGunDefinition.P9_01;
        var player = survivor(h, "mag_load");
        var mag = magazine(AflItems.P9_01_MAGAZINE.get(), 15);
        player.getInventory().setItem(0, mag);
        var ammo = ForgeRegistries.ITEMS.getValue(d.ammoType());
        player.getInventory().setItem(20, new ItemStack(ammo, 5));
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        var held = player.getMainHandItem();
        held.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(NativeMagazineLoading.loading(player) && NativeMagazines.loadingSince(held) >= 0, "a click starts loading");
        var event = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player);
        h.onEachTick(() -> NativeMagazineLoading.tick(event));
        h.runAtTickTime(NativeMagazineLoading.PRESS_TICK + 2, () ->
                h.assertTrue(NativeMagazines.rounds(held) == 16, "the first round goes in at the press: " + NativeMagazines.rounds(held)));
        h.runAtTickTime(NativeMagazineLoading.PRESS_TICK + NativeMagazineLoading.ROUND_TICKS + 4, () -> {
            h.assertTrue(NativeMagazines.rounds(held) == 17, "full after the second");
            h.assertTrue(!NativeMagazineLoading.loading(player) && NativeMagazines.loadingSince(held) < 0, "loading stops when full");
            h.assertTrue(player.getInventory().getItem(20).getCount() == 3, "two loose rounds used");
            h.succeed();
        });
    }

    @GameTest(template = "network_empty")
    public static void ejectToHand(GameTestHelper h) {
        var d = NativeGunDefinition.P9_01;
        var player = survivor(h, "mag_eject");
        var standard = AflItems.P9_01_MAGAZINE.get();
        var inventory = player.getInventory();
        wear(player, AflEquipmentSlots.HOLSTER, new ItemStack(AflItems.PISTOL_HOLSTER.get()));
        wear(player, AflEquipmentSlots.RIG, new ItemStack(AflItems.FIELD_CHEST_RIG.get()));
        var gun = p9(1, true);   // 0 + 1
        inventory.items.set(com.antaurora.apofirstlight.inventory.HotbarRoles.PISTOL_SLOT, gun);
        inventory.selected = com.antaurora.apofirstlight.inventory.HotbarRoles.PISTOL_SLOT;
        put(player, PlayerGrids.Target.RIG, magazine(standard, 0));
        h.assertTrue(!NativeMagazines.canReload(player, d) && NativeMagazines.ejectable(gun, d),
                "an empty spare is no reload; a tap takes the magazine out");
        h.assertTrue(NativeMagazines.eject(player, gun, d), "eject");
        int hand = com.antaurora.apofirstlight.inventory.HotbarRoles.HAND_SLOT;
        h.assertTrue(inventory.selected == hand && inventory.getItem(hand).is(standard) && NativeMagazines.rounds(inventory.getItem(hand)) == 0,
                "the magazine is held, in the hand");
        h.assertTrue(inventory.getItem(com.antaurora.apofirstlight.inventory.HotbarRoles.PISTOL_SLOT) == gun
                && NativeMagazines.fitted(gun, d).isEmpty() && NativeGunAmmo.read(gun, d) == 1, "the gun stays holstered, the chambered round in it");
        h.assertTrue(!NativeMagazines.ejectable(gun, d), "nothing left to take out");
        // a loaded spare: a tap with none fitted puts it in; a tap takes even a full fitted magazine out (user 2026-10-22)
        put(player, PlayerGrids.Target.RIG, magazine(standard, 9));
        h.assertTrue(NativeMagazines.canReload(player, d) && !NativeMagazines.ejectable(gun, d), "a loaded spare goes in");
        var full = p9(18, true);
        h.assertTrue(NativeMagazines.ejectable(full, d), "a full magazine comes out too");
        // creative swaps real magazines too, from the rig and pockets only
        player.setGameMode(GameType.CREATIVE);
        h.assertTrue(NativeMagazines.swap(player, gun, d) && NativeMagazines.magazineRounds(gun, d) == 9, "creative swaps the 9-round spare in");
        h.assertTrue(NativeMagazines.full(standard).is(standard) && NativeMagazines.rounds(NativeMagazines.full(standard)) == 17, "a full magazine item");
        h.succeed();
    }

    /** The BR-51 V3 (docs/native_guns/br51_01_v3_game_model_v1.md): its standard 20-round magazine, as the P9's. */
    private static final class BR51Probe {
        static boolean magazineItems() {
            var d = NativeGunDefinition.BR51_01;
            var fresh = new ItemStack(AflItems.BR51_01.get());
            return NativeMagazines.system(d) && NativeMagazines.fitted(fresh, d).is(AflItems.BR51_01_MAGAZINE.get())
                    && NativeGunAmmo.capacity(fresh, d) == 21;
        }
    }
}
