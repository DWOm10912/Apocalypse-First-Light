package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.grid.ExternalGridHost;
import com.antaurora.apofirstlight.inventory.grid.ExternalGridMenu;
import com.antaurora.apofirstlight.inventory.grid.GridActions;
import com.antaurora.apofirstlight.inventory.grid.GridLayout;
import com.antaurora.apofirstlight.inventory.grid.GridPackets;
import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.inventory.grid.Pockets;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * External grids (docs/ui/afl_inventory_grid_v0_plan.md 15): the grid actions on an ExternalGridHost's grid with its
 * menu open, shift-click both ways, Q, the weapon positions, the host's validity, Creative players.
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExternalGridGameTests {
    private static final int EXT = GridActions.EXTERNAL_TARGET;

    /** A 9 x 7 grid (the pickup bed's layout) that counts its changes. */
    private static final class TestHost implements ExternalGridHost {
        final GridStorage grid = new GridStorage(GridLayout.of(9, 7), false);
        boolean valid = true;
        int changes;

        @Override public GridStorage grid() { return grid; }
        @Override public Component title() { return Component.literal("test"); }
        @Override public boolean stillValid(Player player) { return valid; }
        @Override public void onGridChanged() { changes++; }
    }

    private static ServerPlayer survivor(GameTestHelper h, String name, GameType mode) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(mode);
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        var pockets = Pockets.of(player);
        if (pockets != null) pockets.set(new ListTag());
        for (String slot : AflEquipmentSlots.INVENTORY_PAGE)
            AflEquipmentSlots.stacks(player, slot).ifPresent(s -> { for (int i = 0; i < s.getSlots(); i++) s.setStackInSlot(i, ItemStack.EMPTY); });
        return player;
    }

    private static ExternalGridMenu open(ServerPlayer player, TestHost host) {
        var menu = new ExternalGridMenu(1, player.getInventory(), host, -1);
        player.containerMenu = menu;
        return menu;
    }

    private static void act(ServerPlayer player, GridActions.Kind kind, int target, int section, int x, int y, boolean rotated, int extra) {
        GridActions.handle(player, new GridPackets.Action(kind.ordinal(), target, section, x, y, rotated, extra));
    }

    private static int count(GridStorage storage, Item item) {
        int n = 0;
        for (var stack : storage.stacks()) if (stack.is(item)) n += stack.getCount();
        return n;
    }

    private static int inGrids(ServerPlayer player, Item item) {
        int n = 0;
        for (var target : PlayerGrids.Target.values()) {
            var storage = PlayerGrids.view(player).get(target);
            if (storage != null) n += count(storage, item);
        }
        return n;
    }

    @GameTest(template = "network_empty")
    public static void externalActions(GameTestHelper h) {
        var player = survivor(h, "ext_actions", GameType.SURVIVAL);
        var inventory = player.getInventory();
        var host = new TestHost();
        var menu = open(player, host);
        h.assertTrue(menu.slots.size() == HotbarRoles.USED && GridActions.gridMenu(player, menu),
                "the menu holds the weapon positions and hand, so the grids work in it");

        host.grid.place(new ItemStack(Items.APPLE, 10), 0, 0, 0, false);
        act(player, GridActions.Kind.PICK, EXT, 0, 0, 0, false, 1);
        h.assertTrue(menu.getCarried().is(Items.APPLE) && menu.getCarried().getCount() == 5 && count(host.grid, Items.APPLE) == 5,
                "right click takes half onto the menu's cursor");
        h.assertTrue(host.changes == 1, "the host hears of the change, got " + host.changes);
        act(player, GridActions.Kind.PLACE, EXT, 0, 3, 3, false, 0);
        h.assertTrue(menu.getCarried().isEmpty() && host.grid.at(0, 3, 3) != null && host.grid.at(0, 3, 3).stack().getCount() == 5,
                "placed at 3,3");
        act(player, GridActions.Kind.PICK, EXT, 0, 0, 0, false, 0);
        act(player, GridActions.Kind.MERGE, EXT, 0, 3, 3, false, 0);
        h.assertTrue(host.grid.at(0, 3, 3).stack().getCount() == 10 && host.grid.at(0, 0, 0) == null, "merged onto the stack at 3,3");

        inventory.add(new ItemStack(Items.BREAD, 4));
        h.assertTrue(inGrids(player, Items.BREAD) == 4, "bread in the pockets");
        act(player, GridActions.Kind.TO_CONTAINER, PlayerGrids.Target.POCKETS.ordinal(), 0, 0, 0, false, 0);
        h.assertTrue(inGrids(player, Items.BREAD) == 0 && count(host.grid, Items.BREAD) == 4, "shift-click on a pocket entry: into the external grid");
        var bread = host.grid.entries().stream().filter(e -> e.stack().is(Items.BREAD)).findFirst().orElseThrow();
        act(player, GridActions.Kind.QUICK, EXT, 0, bread.x(), bread.y(), false, 0);
        h.assertTrue(inGrids(player, Items.BREAD) == 4 && count(host.grid, Items.BREAD) == 0, "shift-click on an external entry: into the player's grids");

        host.grid.place(new ItemStack(AflItems.CROWBAR.get()), 0, 0, 5, false);
        act(player, GridActions.Kind.QUICK, EXT, 0, 0, 5, false, 0);
        h.assertTrue(inventory.getItem(HotbarRoles.MELEE_SLOT).is(AflItems.CROWBAR.get()) && count(host.grid, AflItems.CROWBAR.get()) == 0,
                "a weapon shift-clicked out goes to its empty weapon position");
        menu.quickMoveStack(player, HotbarRoles.MELEE_SLOT);
        h.assertTrue(inventory.getItem(HotbarRoles.MELEE_SLOT).isEmpty() && count(host.grid, AflItems.CROWBAR.get()) == 1,
                "shift-click on the melee position: into the external grid");
        var crowbar = host.grid.entries().stream().filter(e -> e.stack().is(AflItems.CROWBAR.get())).findFirst().orElseThrow();
        act(player, GridActions.Kind.HOTBAR_KEY, EXT, 0, crowbar.x(), crowbar.y(), false, HotbarRoles.MELEE_SLOT);
        h.assertTrue(inventory.getItem(HotbarRoles.MELEE_SLOT).is(AflItems.CROWBAR.get()), "key 3 over the crowbar: into the melee position");

        int before = host.changes;
        act(player, GridActions.Kind.BIND, EXT, 0, 3, 3, false, 0);
        h.assertTrue(host.changes == before, "binding keys 4-9 is for the player's own grids only");
        act(player, GridActions.Kind.DROP, EXT, 0, 3, 3, false, 0);
        h.assertTrue(count(host.grid, Items.APPLE) == 9, "Q drops one");

        host.valid = false;
        act(player, GridActions.Kind.PICK, EXT, 0, 3, 3, false, 0);
        h.assertTrue(menu.getCarried().isEmpty() && count(host.grid, Items.APPLE) == 9, "no action once the host is no longer valid");
        host.valid = true;

        var chest = net.minecraft.world.inventory.ChestMenu.threeRows(2, inventory, new net.minecraft.world.SimpleContainer(27));
        player.containerMenu = chest;
        act(player, GridActions.Kind.PICK, EXT, 0, 3, 3, false, 0);
        h.assertTrue(chest.getCarried().isEmpty() && count(host.grid, Items.APPLE) == 9, "the external target needs its menu open");
        h.succeed();
    }

    /** Creative players have no grids of their own; the external grid still works, shift-click out fills the vanilla inventory. */
    @GameTest(template = "network_empty")
    public static void externalCreative(GameTestHelper h) {
        var player = survivor(h, "ext_creative", GameType.CREATIVE);
        var host = new TestHost();
        var menu = open(player, host);
        host.grid.place(new ItemStack(Items.APPLE, 10), 0, 0, 0, false);
        act(player, GridActions.Kind.PICK, EXT, 0, 0, 0, false, 0);
        h.assertTrue(menu.getCarried().getCount() == 10 && host.grid.entries().isEmpty(), "a Creative player picks from the external grid");
        act(player, GridActions.Kind.PLACE, EXT, 0, 2, 2, false, 0);
        act(player, GridActions.Kind.QUICK, EXT, 0, 2, 2, false, 0);
        int apples = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i).is(Items.APPLE)) apples += player.getInventory().getItem(i).getCount();
        h.assertTrue(apples == 10 && host.grid.entries().isEmpty(), "shift-click out goes into the vanilla inventory, got " + apples);
        act(player, GridActions.Kind.PICK, PlayerGrids.Target.POCKETS.ordinal(), 0, 0, 0, false, 0);
        h.assertTrue(menu.getCarried().isEmpty(), "player-grid actions stay off for Creative players");
        h.succeed();
    }
}
