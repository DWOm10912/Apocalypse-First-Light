package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.NativeGunAmmo;
import com.antaurora.apofirstlight.weapon.NativeGunDefinition;
import com.antaurora.apofirstlight.weapon.P901Actions;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeGunAmmoGameTests {
    private static final NativeGunDefinition D = NativeGunDefinition.P9_01;

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void authoritativeFireAndRejectedReload(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ammo_fire"));
        ItemStack gun = new ItemStack(AflItems.P9_01.get());
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, gun);
        player.getInventory().setItem(1, new ItemStack(AflItems.ROUND_9MM.get(), 20));
        // A rejected full-mag reload must not leave a session that blocks fire.
        P901Actions.request(player, true, 0);
        P901Actions.request(player, false, 0);
        for (int i = 0; i < 20; i++) P901Actions.request(player, false, 0);
        h.assertTrue(NativeGunAmmo.read(gun, D) == 16, "One accepted shot, repeated requests ignored");
        for (int shot = 2; shot <= 5; shot++) {
            final int expected = 17 - shot;
            h.runAfterDelay((shot - 1) * 4, () -> {
                P901Actions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                P901Actions.request(player, false, 0);
                h.assertTrue(NativeGunAmmo.read(gun, D) == expected, "Authoritative five-shot count");
            });
        }
        h.runAfterDelay(20, () -> {
            P901Actions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            player.getInventory().setItem(1, ItemStack.EMPTY);
            NativeGunAmmo.set(gun, D, 0);
            P901Actions.request(player, true, 0);
            P901Actions.request(player, false, 0);
            h.assertTrue(NativeGunAmmo.read(gun, D) == 0, "Empty stays zero");
            // Neither rejected operation may leave a session blocking the next valid shot.
            NativeGunAmmo.set(gun, D, 1);
            P901Actions.request(player, false, 0);
            h.assertTrue(NativeGunAmmo.read(gun, D) == 0, "Empty/reload rejection leaves no session");
            h.succeed();
        });
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void stackPersistenceAndBounds(GameTestHelper h) {
        ItemStack gun = new ItemStack(AflItems.P9_01.get());
        h.assertTrue(NativeGunAmmo.read(gun, D) == 17 && !gun.hasTag(), "New gun/read-only default");
        for (int i = 0; i < 5; i++) h.assertTrue(NativeGunAmmo.consumeOne(gun, D), "Consume");
        h.assertTrue(NativeGunAmmo.read(gun, D) == 12, "Five shots");
        ItemStack copied = gun.copy();
        NativeGunAmmo.consumeOne(copied, D);
        h.assertTrue(NativeGunAmmo.read(gun, D) == 12, "Copy must own its tag");
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, gun);
        gun = chest.removeItemNoUpdate(0);
        ItemEntity dropped = new ItemEntity(h.getLevel(), 0, 4, 0, gun);
        CompoundTag entityTag = new CompoundTag();
        dropped.save(entityTag);
        ItemEntity restored = new ItemEntity(h.getLevel(), 0, 4, 0, ItemStack.EMPTY);
        restored.load(entityTag);
        gun = ItemStack.of(restored.getItem().save(new CompoundTag()));
        h.assertTrue(NativeGunAmmo.read(gun, D) == 12, "Container/entity/disk NBT roundtrip");
        NativeGunAmmo.set(gun, D, -5);
        NativeGunAmmo.initialize(gun, D);
        h.assertTrue(!NativeGunAmmo.consumeOne(gun, D) && NativeGunAmmo.read(gun, D) == 0, "Zero never refills");
        NativeGunAmmo.set(gun, D, 999);
        h.assertTrue(NativeGunAmmo.read(gun, D) == 17, "Capacity bound");
        ItemStack before = gun.copy();
        NativeGunAmmo.consumeOne(gun, D);
        var item = AflItems.P9_01.get();
        h.assertTrue(!item.shouldCauseReequipAnimation(before, gun, false), "Ammo sync must not re-equip");
        gun.getOrCreateTag().putLong(software.bernie.geckolib.animatable.GeoItem.ID_NBT_KEY, 100L);
        h.assertTrue(!item.shouldCauseReequipAnimation(before, gun, false), "Initial render ID must not re-equip");
        h.assertTrue(item.shouldCauseReequipAnimation(before, gun, true), "Slot switch must re-equip");
        before = gun.copy();
        gun.getOrCreateTag().putLong(software.bernie.geckolib.animatable.GeoItem.ID_NBT_KEY, 101L);
        h.assertTrue(item.shouldCauseReequipAnimation(before, gun, false), "Different gun identity must re-equip");
        h.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void inventoryPartialAndFull(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ammo_transfer"));
        var inventory = player.getInventory();
        ItemStack gun = new ItemStack(AflItems.P9_01.get());
        NativeGunAmmo.set(gun, D, 12);
        inventory.setItem(1, new ItemStack(AflItems.ROUND_9MM.get(), 20));
        h.assertTrue(NativeGunAmmo.transfer(inventory, gun, D) == 5
                && NativeGunAmmo.reserve(inventory, D) == 15 && NativeGunAmmo.read(gun, D) == 17, "12+20");
        h.assertTrue(NativeGunAmmo.transfer(inventory, gun, D) == 0, "Full magazine");
        NativeGunAmmo.set(gun, D, 12);
        inventory.setItem(1, new ItemStack(AflItems.ROUND_9MM.get(), 3));
        h.assertTrue(NativeGunAmmo.transfer(inventory, gun, D) == 3
                && NativeGunAmmo.reserve(inventory, D) == 0 && NativeGunAmmo.read(gun, D) == 15, "12+3");
        h.assertTrue(NativeGunAmmo.transfer(inventory, gun, D) == 0, "Empty reserve");
        h.succeed();
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void reloadCommitAndCancellation(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ammo_timing"));
        ItemStack gun = new ItemStack(AflItems.P9_01.get());
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, gun);
        player.getInventory().setItem(1, new ItemStack(AflItems.ROUND_9MM.get(), 20));
        NativeGunAmmo.set(gun, D, 12);
        P901Actions.request(player, true, 0);
        P901Actions.request(player, true, 0);
        P901Actions.request(player, false, 0);
        h.assertTrue(NativeGunAmmo.read(gun, D) == 12 && NativeGunAmmo.reserve(player.getInventory(), D) == 20,
                "Start/spam/fire during reload must not transfer");
        h.runAfterDelay(18, () -> {
            P901Actions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            h.assertTrue(NativeGunAmmo.read(gun, D) == 12, "Before mag-in");
        });
        h.runAfterDelay(19, () -> {
            P901Actions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            P901Actions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            h.assertTrue(NativeGunAmmo.read(gun, D) == 17 && NativeGunAmmo.reserve(player.getInventory(), D) == 15,
                    "Exactly one transfer at tick19");
        });
        h.runAfterDelay(26, () -> {
            P901Actions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            NativeGunAmmo.set(gun, D, 12);
            P901Actions.request(player, true, 0);
            player.getInventory().selected = 2;
            P901Actions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            player.getInventory().selected = 0;
        });
        h.runAfterDelay(48, () -> {
            P901Actions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            h.assertTrue(NativeGunAmmo.read(gun, D) == 12 && NativeGunAmmo.reserve(player.getInventory(), D) == 15,
                    "Slot cancellation must not later commit");
            h.succeed();
        });
    }
}
