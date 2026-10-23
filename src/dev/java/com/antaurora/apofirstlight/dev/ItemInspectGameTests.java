package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inspect.InspectActions;
import com.antaurora.apofirstlight.inspect.InspectActions.Action;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.grid.GridSource;
import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.inventory.grid.Pockets;
import com.antaurora.apofirstlight.item.Battery9vItem;
import com.antaurora.apofirstlight.item.GeigerCounterItem;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Item inspect V1 (docs/gameplay/item_inspect_v1_design.md 9, I3): the hotspot actions on the server never make or lose a
 * battery. A survivor with the counter in hand and batteries in the pockets opens the cover, fits, refits, takes out,
 * switches on and drains; at every step the batteries in the pockets, the inventory, the counter and on the ground add
 * up to what there was. Also: refused actions change nothing, the drain is 1 FE per 16 ticks, and only a fitted NiMH
 * takes charge through the counter. Run: gradlew -I src/dev/item-inspect-gametest.init.gradle runGameTestServer
 * --offline -PaflWithoutShaders.
 */
@GameTestHolder("afl_item_inspect_tests")
@PrefixGameTestTemplate(false)
public final class ItemInspectGameTests {
    private static final String NAMESPACE = "afl_item_inspect_tests";

    // The power network's empty room: the native JSON reload test clears the structure cache and injects only that
    // template again, so a template of our own would be gone for batches that run after it.
    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("item_inspect", NAMESPACE + ":conservation", "apocalypse_firstlight:network_empty", 100, 0L, true,
                ItemInspectGameTests::run));
    }

    private static ServerPlayer survivor(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "inspect_survivor"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().selected = HotbarRoles.HAND_SLOT;
        var pockets = Pockets.of(player);
        if (pockets != null) pockets.set(new ListTag());
        for (String slot : AflEquipmentSlots.INVENTORY_PAGE)
            AflEquipmentSlots.stacks(player, slot).ifPresent(s -> { for (int i = 0; i < s.getSlots(); i++) s.setStackInSlot(i, ItemStack.EMPTY); });
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2, 1, 2)));
        return player;
    }

    /** Batteries of one kind wherever they are: grids, inventory, the counter's bay, the ground nearby. */
    private static int count(GameTestHelper h, ServerPlayer player, Item kind) {
        int n = 0;
        for (var target : PlayerGrids.Target.values()) {
            var storage = PlayerGrids.view(player).get(target);
            if (storage != null) for (var e : storage.entries()) if (e.stack().is(kind)) n += e.stack().getCount();
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(kind)) n += stack.getCount();
            if (stack.getItem() instanceof GeigerCounterItem && GeigerCounterItem.battery(stack).is(kind)) n++;
        }
        for (var e : h.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)))
            if (e.getItem().is(kind)) n += e.getItem().getCount();
        return n;
    }

    private static GridStorage.Entry pocket(ServerPlayer player, Item kind) {
        for (var e : PlayerGrids.view(player).get(PlayerGrids.Target.POCKETS).entries()) if (e.stack().is(kind)) return e;
        return null;
    }

    private static int source(GridStorage.Entry e) {
        return GridSource.encode(PlayerGrids.Target.POCKETS, e.section(), e.x(), e.y());
    }

    private static void run(GameTestHelper h) {
        var player = survivor(h);
        Item alkaline = AflItems.BATTERY_9V_ALKALINE.get(), nimh = AflItems.BATTERY_9V_NIMH.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.GEIGER_COUNTER.get()));
        var inventory = player.getInventory();
        h.assertTrue(inventory.add(new ItemStack(alkaline, 2)) && inventory.add(new ItemStack(nimh)), "batteries into the pockets");
        h.assertTrue(pocket(player, alkaline) != null && pocket(player, nimh) != null, "both kinds lie in the pockets");
        int hand = InspectActions.HAND;
        long now = h.getLevel().getGameTime();

        // shut cover: nothing to fit or take out
        h.assertTrue(!InspectActions.apply(player, hand, Action.FIT_BATTERY, source(pocket(player, alkaline))), "fit refused with the cover on");
        h.assertTrue(!InspectActions.apply(player, hand, Action.REMOVE_BATTERY, 0), "remove refused with the cover on");
        h.assertTrue(!InspectActions.apply(player, hand, Action.CLOSE_COVER, 0), "close refused when already shut");
        h.assertTrue(count(h, player, alkaline) == 2 && count(h, player, nimh) == 1, "refusals change nothing");

        h.assertTrue(InspectActions.apply(player, hand, Action.OPEN_COVER, 0) && GeigerCounterItem.coverOpen(player.getMainHandItem()), "cover open");
        h.assertTrue(!InspectActions.apply(player, hand, Action.OPEN_COVER, 0), "open refused when already open");
        h.assertTrue(!InspectActions.apply(player, hand, Action.REMOVE_BATTERY, 0), "remove refused with an empty bay");
        h.assertTrue(!InspectActions.apply(player, hand, Action.FIT_BATTERY, hand), "the counter is no battery");
        h.assertTrue(!InspectActions.apply(player, hand, Action.FIT_BATTERY, GridSource.encode(PlayerGrids.Target.POCKETS, 0, 9, 9)),
                "fit refused from an empty place");

        // fit one alkaline from the stack of two
        h.assertTrue(InspectActions.apply(player, hand, Action.FIT_BATTERY, source(pocket(player, alkaline))), "alkaline fitted");
        h.assertTrue(GeigerCounterItem.batteryKind(player.getMainHandItem()) == Battery9vItem.Kind.ALKALINE
                && pocket(player, alkaline).stack().getCount() == 1 && count(h, player, alkaline) == 2, "one taken from the stack, none made");
        h.assertTrue(!InspectActions.apply(player, hand, Action.FIT_BATTERY, source(pocket(player, nimh))), "fit refused with a battery in");
        h.assertTrue(count(h, player, nimh) == 1, "the refused fit kept the NiMH");

        // out again: a full alkaline goes back onto its stack
        h.assertTrue(InspectActions.apply(player, hand, Action.REMOVE_BATTERY, 0) && GeigerCounterItem.battery(player.getMainHandItem()).isEmpty(),
                "alkaline out");
        h.assertTrue(count(h, player, alkaline) == 2 && h.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).isEmpty(),
                "back in the pockets, nothing dropped");

        // NiMH in, switch on, 100 FE of running, out with the charge kept
        h.assertTrue(InspectActions.apply(player, hand, Action.FIT_BATTERY, source(pocket(player, nimh))) && pocket(player, nimh) == null,
                "NiMH fitted from its pocket");
        var energy = player.getMainHandItem().getCapability(ForgeCapabilities.ENERGY).orElse(null);
        h.assertTrue(energy != null && energy.getMaxEnergyStored() == 3000 && energy.receiveEnergy(64, true) == 0,
                "a full NiMH takes nothing");
        h.assertTrue(InspectActions.apply(player, hand, Action.SWITCH, 0) && GeigerCounterItem.on(player.getMainHandItem()), "switched on");
        player.getMainHandItem().getTag().putLong(GeigerCounterItem.SINCE, now - 100L * GeigerCounterItem.TICKS_PER_FE);
        h.assertTrue(GeigerCounterItem.energy(player.getMainHandItem(), now) == 2900, "1 FE per 16 ticks while on");
        energy = player.getMainHandItem().getCapability(ForgeCapabilities.ENERGY).orElse(null);
        h.assertTrue(energy.receiveEnergy(64, false) == 1 && GeigerCounterItem.energy(player.getMainHandItem(), now) == 2901,
                "the fitted NiMH takes 1 FE a tick through the counter");
        h.assertTrue(InspectActions.apply(player, hand, Action.REMOVE_BATTERY, 0), "NiMH out");
        var back = pocket(player, nimh);
        h.assertTrue(back != null && Battery9vItem.energy(back.stack()) == 2901 && count(h, player, nimh) == 1, "out with its charge, one NiMH");

        // alkaline in through the counter: no charge
        h.assertTrue(InspectActions.apply(player, hand, Action.FIT_BATTERY, source(pocket(player, alkaline))), "alkaline fitted again");
        energy = player.getMainHandItem().getCapability(ForgeCapabilities.ENERGY).orElse(null);
        player.getMainHandItem().getTag().putLong(GeigerCounterItem.SINCE, now - 50L * GeigerCounterItem.TICKS_PER_FE);
        h.assertTrue(energy.receiveEnergy(64, false) == 0 && !energy.canReceive(), "an alkaline takes no charge");
        h.assertTrue(InspectActions.apply(player, hand, Action.CLOSE_COVER, 0) && !GeigerCounterItem.coverOpen(player.getMainHandItem()),
                "cover back on with the battery in");
        h.assertTrue(count(h, player, alkaline) == 2 && count(h, player, nimh) == 1, "two alkaline, one NiMH at the end");

        // the counter gone from the hand: every action refused
        ItemStack counter = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(!InspectActions.apply(player, hand, Action.OPEN_COVER, 0) && !InspectActions.apply(player, hand, Action.SWITCH, 0),
                "nothing happens without the counter");
        player.setItemInHand(InteractionHand.MAIN_HAND, counter);

        ApocalypseFirstLight.LOGGER.info("[AFL ITEM INSPECT TEST] PASS refusals, cover, fit from a stack, refit, removal back to the pockets, "
                + "drain 1 FE / 16 ticks, NiMH charge through the counter, alkaline none, batteries conserved at every step");
        h.succeed();
    }
}
