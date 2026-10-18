package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.NoOffHand;
import com.antaurora.apofirstlight.inventory.grid.GridActions;
import com.antaurora.apofirstlight.inventory.grid.GridContainerItem;
import com.antaurora.apofirstlight.inventory.grid.GridLayout;
import com.antaurora.apofirstlight.inventory.grid.GridPackets;
import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import com.antaurora.apofirstlight.inventory.grid.GunSling;
import com.antaurora.apofirstlight.inventory.grid.ItemSizes;
import com.antaurora.apofirstlight.inventory.grid.Loadout;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.inventory.grid.Pockets;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.NativeGunAmmo;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Grid Inventory V0 (docs/ui/afl_inventory_grid_v0_plan.md): pickup order, grid actions, storage, death, ammo, and the
 * weapon positions / bindings / hand of plan 10.
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GridInventoryGameTests {
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

    private static ItemStack slung(Item gun) {
        var stack = new ItemStack(gun);
        GunSling.fit(stack);
        return stack;
    }

    @GameTest(template = "network_empty")
    public static void pickupOrder(GameTestHelper h) {
        var player = survivor(h, "grid_pickup");
        var inventory = player.getInventory();
        wear(player, AflEquipmentSlots.BACK, new ItemStack(AflItems.CANVAS_BACKPACK.get()));
        h.assertTrue(inventory.add(new ItemStack(AflItems.BR51_01.get())) && inventory.getItem(0).isEmpty(),
                "a rifle without a sling goes into the grids, not to key 1");
        h.assertTrue(inventory.add(slung(AflItems.BR51_01.get())) && inventory.getItem(0).is(AflItems.BR51_01.get()),
                "a slung rifle goes to the primary position (key 1)");
        h.assertTrue(inventory.add(new ItemStack(AflItems.P9_01.get())) && inventory.getItem(1).isEmpty(),
                "a pistol without a holster goes into the grids, not to key 2");
        wear(player, AflEquipmentSlots.HOLSTER, new ItemStack(AflItems.PISTOL_HOLSTER.get()));
        h.assertTrue(inventory.add(new ItemStack(AflItems.P9_01.get())) && inventory.getItem(1).is(AflItems.P9_01.get()),
                "with a holster worn the pistol goes to key 2");
        h.assertTrue(inventory.add(new ItemStack(AflItems.CROWBAR.get())) && inventory.getItem(2).is(AflItems.CROWBAR.get()),
                "crowbar goes to the melee position (key 3)");
        inventory.add(new ItemStack(Items.STONE, 64));
        for (int i = HotbarRoles.HAND_SLOT; i < 36; i++) h.assertTrue(inventory.getItem(i).isEmpty(), "hand / locked slot " + i + " stays empty");

        wear(player, AflEquipmentSlots.BACK, ItemStack.EMPTY);
        Pockets.of(player).set(new ListTag());
        var pocketItems = new Item[]{Items.APPLE, Items.BREAD, Items.CARROT, Items.STICK};
        for (var item : pocketItems) h.assertTrue(inventory.add(new ItemStack(item, 3)), "into the pockets: " + item);
        h.assertTrue(PlayerGrids.view(player).get(PlayerGrids.Target.POCKETS).entries().size() == 4, "four pocket stacks");
        h.assertTrue(inventory.add(new ItemStack(Items.APPLE, 2)), "same item merges into its pocket");
        var spare = new ItemStack(Items.FLINT, 5);
        h.assertTrue(!inventory.add(spare) && spare.getCount() == 5, "nothing worn and pockets full: refused, stack untouched");

        wear(player, AflEquipmentSlots.BACK, new ItemStack(AflItems.CANVAS_BACKPACK.get()));
        h.assertTrue(inventory.add(spare) && spare.isEmpty(), "with a backpack the rest goes into its grid");
        var drum = new ItemStack(AflItems.BR51_DRUM_MAGAZINE_50.get());
        h.assertTrue(ItemSizes.of(drum, false).equals(new ItemSizes.Size(2, 2)), "drum magazine is 2x2");
        h.assertTrue(inventory.add(drum), "drum magazine into the backpack");
        var pack = PlayerGrids.view(player).get(PlayerGrids.Target.BACKPACK);
        h.assertTrue(pack != null && pack.entries().size() == 2, "fresh backpack holds flint + drum, got " + (pack == null ? -1 : pack.entries().size()));
        h.assertTrue(PlayerGrids.gear(player, PlayerGrids.Target.BACKPACK).getTag().contains("Items"), "contents saved in the backpack NBT");
        h.succeed();
    }

    @GameTest(template = "network_empty")
    public static void storageRotationAndReload(GameTestHelper h) {
        var rifle = new ItemStack(AflItems.BR51_01.get());
        var size = ItemSizes.of(rifle, false);
        h.assertTrue(size.equals(new ItemSizes.Size(5, 2)), "rifle class default 5x2, got " + size);
        var storage = new GridStorage(GridLayout.of(5, 6), false);
        h.assertTrue(storage.fits(rifle, 0, 0, 0, false, null) && !storage.fits(rifle, 0, 2, 0, false, null), "5 wide fits at x0 only");
        h.assertTrue(storage.fits(rifle, 0, 3, 0, true, null), "rotated (2x5) fits at x3");
        storage.place(rifle, 0, 3, 0, true);
        h.assertTrue(storage.at(0, 4, 3) != null && storage.at(0, 2, 0) == null, "rotated footprint covers 3..4 x 0..4");
        var loaded = GridStorage.load(GridLayout.of(5, 6), storage.save(), false);
        var e = loaded.entries().get(0);
        h.assertTrue(e.x() == 3 && e.y() == 0 && e.rotated(), "position and rotation survive save / load");
        var small = GridStorage.load(GridLayout.of(2, 2), storage.save(), false);
        h.assertTrue(small.entries().isEmpty() && small.overflow().size() == 1, "fits nowhere after a layout change: overflow");
        var reserved = new GridStorage(GridLayout.of(4, 4), false);
        reserved.reserve(new GridStorage.Reserved(0, 1, 1, 2, 2));
        h.assertTrue(!reserved.fits(new ItemStack(Items.STONE), 0, 2, 2, false, null) && reserved.fits(new ItemStack(Items.STONE), 0, 0, 0, false, null),
                "reserved cells (the hand's origin) take nothing");
        h.succeed();
    }

    @GameTest(template = "network_empty")
    public static void gridActions(GameTestHelper h) {
        var player = survivor(h, "grid_actions");
        wear(player, AflEquipmentSlots.BACK, new ItemStack(AflItems.CANVAS_BACKPACK.get()));
        var inventory = player.getInventory();
        inventory.add(new ItemStack(Items.APPLE, 10));   // pocket 0
        int pockets = PlayerGrids.Target.POCKETS.ordinal(), back = PlayerGrids.Target.BACKPACK.ordinal();
        act(player, GridActions.Kind.PICK, pockets, 0, 0, 0, false, 1);
        h.assertTrue(player.inventoryMenu.getCarried().getCount() == 5, "right click takes half");
        act(player, GridActions.Kind.PLACE, back, 0, 2, 3, false, 0);
        h.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "placed all into the backpack");
        var pack = PlayerGrids.view(player).get(PlayerGrids.Target.BACKPACK);
        h.assertTrue(pack.at(0, 2, 3) != null && pack.at(0, 2, 3).stack().getCount() == 5, "five apples at backpack 2,3");
        act(player, GridActions.Kind.PICK, pockets, 0, 0, 0, false, 0);
        act(player, GridActions.Kind.MERGE, back, 0, 2, 3, false, 0);
        pack = PlayerGrids.view(player).get(PlayerGrids.Target.BACKPACK);
        h.assertTrue(pack.at(0, 2, 3).stack().getCount() == 10 && PlayerGrids.view(player).get(PlayerGrids.Target.POCKETS).entries().isEmpty(),
                "merge puts the other five on top");
        player.inventoryMenu.setCarried(new ItemStack(AflItems.CROWBAR.get()));
        act(player, GridActions.Kind.PLACE, back, 0, 0, 0, false, 0);
        act(player, GridActions.Kind.HOTBAR_KEY, back, 0, 0, 0, false, HotbarRoles.MELEE_SLOT);
        h.assertTrue(inventory.getItem(HotbarRoles.MELEE_SLOT).is(AflItems.CROWBAR.get()), "key 3 over the crowbar puts it into the melee position");
        act(player, GridActions.Kind.HOTBAR_KEY, back, 0, 2, 3, false, 0);
        h.assertTrue(inventory.getItem(0).isEmpty(), "a non-weapon never goes into a weapon position");
        act(player, GridActions.Kind.HOTBAR_KEY, back, 0, 2, 3, false, HotbarRoles.HAND_SLOT);
        h.assertTrue(inventory.getItem(HotbarRoles.HAND_SLOT).isEmpty(), "number keys never fill the hand");
        var rig = new ItemStack(AflItems.FIELD_CHEST_RIG.get());
        player.inventoryMenu.setCarried(rig);
        act(player, GridActions.Kind.PLACE, back, 0, 0, 4, false, 0);
        h.assertTrue(player.inventoryMenu.getCarried() == rig, "grid gear never goes into a grid");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        act(player, GridActions.Kind.QUICK_FROM_SLOT, 0, 0, 0, 0, false, HotbarRoles.MELEE_SLOT);
        h.assertTrue(inventory.getItem(HotbarRoles.MELEE_SLOT).isEmpty(), "shift-click moves a hotbar stack into the grids");
        h.succeed();
    }

    private static void act(ServerPlayer player, GridActions.Kind kind, int target, int section, int x, int y, boolean rotated, int extra) {
        GridActions.handle(player, new GridPackets.Action(kind.ordinal(), target, section, x, y, rotated, extra));
    }

    private static GridStorage.Entry first(ServerPlayer player, PlayerGrids.Target target, Item item) {
        var storage = PlayerGrids.view(player).get(target);
        if (storage == null) return null;
        for (var e : storage.entries()) if (e.stack().is(item)) return e;
        return null;
    }

    private static boolean inGrids(ServerPlayer player, Item item) {
        for (var target : PlayerGrids.Target.values()) if (first(player, target, item) != null) return true;
        return false;
    }

    @GameTest(template = "network_empty")
    public static void bindingsAndHand(GameTestHelper h) {
        var player = survivor(h, "grid_binds");
        var inventory = player.getInventory();
        var loadout = Loadout.of(player);
        h.assertTrue(loadout != null, "players carry a loadout");
        inventory.add(new ItemStack(Items.APPLE, 10));
        var apple = first(player, PlayerGrids.Target.POCKETS, Items.APPLE);
        int pockets = PlayerGrids.Target.POCKETS.ordinal(), back = PlayerGrids.Target.BACKPACK.ordinal();
        act(player, GridActions.Kind.BIND, pockets, apple.section(), apple.x(), apple.y(), false, 0);
        h.assertTrue(loadout.bind(0) == Items.APPLE, "key 4 bound to apples");

        wear(player, AflEquipmentSlots.BACK, new ItemStack(AflItems.CANVAS_BACKPACK.get()));
        player.inventoryMenu.setCarried(new ItemStack(Items.STICK, 4));
        act(player, GridActions.Kind.PLACE, back, 0, 0, 0, false, 0);
        act(player, GridActions.Kind.BIND, back, 0, 0, 0, false, 1);
        h.assertTrue(loadout.bind(1) == null, "backpack stacks cannot be bound");

        Loadout.useBind(player, 0);
        h.assertTrue(inventory.selected == HotbarRoles.HAND_SLOT && inventory.getItem(HotbarRoles.HAND_SLOT).is(Items.APPLE)
                && inventory.getItem(HotbarRoles.HAND_SLOT).getCount() == 10, "key 4 takes the apples into the hand");
        h.assertTrue(first(player, PlayerGrids.Target.POCKETS, Items.APPLE) == null && loadout.handBind() == 0, "taken out of the pocket");
        var origin = loadout.origin();
        h.assertTrue(origin != null && origin.target() == pockets, "the hand remembers the pocket");
        h.assertTrue(!PlayerGrids.view(player).get(PlayerGrids.Target.POCKETS).fits(new ItemStack(Items.FLINT), origin.section(), origin.x(), origin.y(), false, null),
                "the apples' cells stay reserved while held");

        inventory.add(new ItemStack(Items.APPLE, 5));
        h.assertTrue(inventory.getItem(HotbarRoles.HAND_SLOT).getCount() == 10, "pickups never merge into the hand");
        inventory.items.set(HotbarRoles.HAND_SLOT, ItemStack.EMPTY);   // eaten up
        Loadout.tickNow(player);
        h.assertTrue(inventory.getItem(HotbarRoles.HAND_SLOT).is(Items.APPLE) && inventory.getItem(HotbarRoles.HAND_SLOT).getCount() == 5
                && loadout.handBind() == 0, "the next stack of the bound kind follows into the hand");

        inventory.selected = 0;
        Loadout.tickNow(player);
        h.assertTrue(inventory.getItem(HotbarRoles.HAND_SLOT).isEmpty() && loadout.handBind() == Loadout.NONE && loadout.origin() == null,
                "switching away puts the hand back");
        var back5 = first(player, PlayerGrids.Target.POCKETS, Items.APPLE);
        h.assertTrue(back5 != null && back5.stack().getCount() == 5, "the apples are in a pocket again");

        inventory.items.set(HotbarRoles.HAND_SLOT, new ItemStack(Items.BREAD, 2));   // dropped into the hands box
        Loadout.tickNow(player);
        h.assertTrue(inventory.selected == HotbarRoles.HAND_SLOT && loadout.handBind() == Loadout.HANDS, "the hands box selects the hand");
        inventory.selected = 1;
        Loadout.tickNow(player);
        h.assertTrue(inventory.getItem(HotbarRoles.HAND_SLOT).isEmpty() && inGrids(player, Items.BREAD), "a hands-box item goes into the grids when put away");

        Loadout.useBind(player, 3);
        h.assertTrue(inventory.selected == 1, "an unbound key does nothing");
        act(player, GridActions.Kind.UNBIND, 0, 0, 0, 0, false, 0);
        h.assertTrue(loadout.bind(0) == null, "right-click unbinds");

        inventory.items.set(0, new ItemStack(Items.GRAVEL, 8));   // an older save: a block where the primary goes
        inventory.selected = 1;
        Loadout.tickNow(player);
        h.assertTrue(inventory.getItem(0).isEmpty() && inGrids(player, Items.GRAVEL),
                "what may not sit in a weapon position is tidied into the grids");

        player.inventoryMenu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS, 6));   // crafting input
        player.inventoryMenu.quickMoveStack(player, 1);
        h.assertTrue(player.inventoryMenu.getSlot(1).getItem().isEmpty() && inventory.getItem(HotbarRoles.HAND_SLOT).isEmpty()
                && inGrids(player, Items.OAK_PLANKS),
                "shift-click out of the crafting grid lands in the grids, never in the hand");
        h.succeed();
    }

    /** No off hand (plan 16, inventory/NoOffHand), and guns held right-handed (mixin/PlayerGunRightArmMixin). */
    @GameTest(template = "network_empty")
    public static void noOffHand(GameTestHelper h) {
        var player = survivor(h, "grid_no_off_hand");
        var inventory = player.getInventory();
        var menu = player.inventoryMenu;
        wear(player, AflEquipmentSlots.BACK, new ItemStack(AflItems.CANVAS_BACKPACK.get()));
        menu.setCarried(new ItemStack(Items.TORCH, 8));
        menu.clicked(InventoryMenu.SHIELD_SLOT, 0, ClickType.PICKUP, player);
        h.assertTrue(inventory.offhand.get(0).isEmpty() && menu.getCarried().getCount() == 8, "a click does not put anything into the off hand");
        menu.setCarried(ItemStack.EMPTY);
        inventory.items.set(HotbarRoles.HAND_SLOT, new ItemStack(Items.TORCH, 8));
        menu.clicked(InventoryMenu.USE_ROW_SLOT_START + HotbarRoles.HAND_SLOT, Inventory.SLOT_OFFHAND, ClickType.SWAP, player);
        h.assertTrue(inventory.offhand.get(0).isEmpty() && inventory.items.get(HotbarRoles.HAND_SLOT).getCount() == 8,
                "F over a slot does not swap it into the off hand");
        h.assertTrue(ForgeHooks.onLivingSwapHandItems(player).isCanceled(), "the swap-hands key is cancelled");

        inventory.offhand.set(0, new ItemStack(Items.FLINT, 5));   // an older save, a command
        NoOffHand.playerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        h.assertTrue(inventory.offhand.get(0).isEmpty() && inGrids(player, Items.FLINT), "what is in the off hand goes into the grids");
        player.setGameMode(GameType.CREATIVE);
        menu.setCarried(new ItemStack(Items.TORCH, 8));
        menu.clicked(InventoryMenu.SHIELD_SLOT, 0, ClickType.PICKUP, player);
        h.assertTrue(inventory.offhand.get(0).isEmpty(), "no off hand in Creative either");
        menu.setCarried(ItemStack.EMPTY);
        player.setGameMode(GameType.SURVIVAL);

        player.setMainArm(HumanoidArm.LEFT);
        wear(player, AflEquipmentSlots.HOLSTER, new ItemStack(AflItems.PISTOL_HOLSTER.get()));
        inventory.items.set(HotbarRoles.PISTOL_SLOT, new ItemStack(AflItems.P9_01.get()));
        inventory.selected = HotbarRoles.PISTOL_SLOT;
        h.assertTrue(player.getMainArm() == HumanoidArm.RIGHT, "a gun is held right-handed whatever the Main Hand option");
        inventory.selected = HotbarRoles.MELEE_SLOT;
        h.assertTrue(player.getMainArm() == HumanoidArm.LEFT, "other items keep the Main Hand option");
        player.setMainArm(HumanoidArm.RIGHT);
        h.succeed();
    }

    @GameTest(template = "network_empty")
    public static void slingAndHolster(GameTestHelper h) {
        var player = survivor(h, "grid_sling");
        var inventory = player.getInventory();
        var menu = player.inventoryMenu;
        var rifle = new ItemStack(AflItems.BR51_01.get());
        h.assertTrue(GunSling.canFit(rifle) && !GunSling.canFit(new ItemStack(AflItems.P9_01.get())), "only long guns take a sling");
        h.assertTrue(!HotbarRoles.accepts(player, HotbarRoles.PRIMARY_SLOT, rifle), "no sling: no primary position");
        inventory.items.set(HotbarRoles.PRIMARY_SLOT, rifle);
        menu.setCarried(new ItemStack(AflItems.GUN_SLING.get(), 2));
        act(player, GridActions.Kind.SLING, GridActions.SLOT_TARGET, 0, 0, 0, false, HotbarRoles.PRIMARY_SLOT);
        h.assertTrue(GunSling.has(rifle) && menu.getCarried().getCount() == 1, "the sling on the cursor is fitted to the rifle");
        h.assertTrue(HotbarRoles.accepts(player, HotbarRoles.PRIMARY_SLOT, rifle), "slung: the primary position takes it");
        menu.setCarried(ItemStack.EMPTY);
        act(player, GridActions.Kind.SLING, GridActions.SLOT_TARGET, 0, 0, 0, false, HotbarRoles.PRIMARY_SLOT);
        h.assertTrue(!GunSling.has(rifle) && menu.getCarried().is(AflItems.GUN_SLING.get()), "an empty cursor takes the sling off");
        menu.setCarried(ItemStack.EMPTY);

        wear(player, AflEquipmentSlots.BACK, new ItemStack(AflItems.CANVAS_BACKPACK.get()));
        inventory.selected = 2;
        Loadout.tickNow(player);
        h.assertTrue(inventory.getItem(HotbarRoles.PRIMARY_SLOT).isEmpty() && first(player, PlayerGrids.Target.BACKPACK, AflItems.BR51_01.get()) != null,
                "an unslung rifle leaves the primary position for the backpack");

        var pistol = new ItemStack(AflItems.P9_01.get());
        h.assertTrue(!HotbarRoles.accepts(player, HotbarRoles.PISTOL_SLOT, pistol), "no holster: no pistol position");
        var holster = new ItemStack(AflItems.PISTOL_HOLSTER.get());
        h.assertTrue(inventory.add(holster) && holster.isEmpty(), "a picked-up holster is taken");
        h.assertTrue(HotbarRoles.holsterWorn(player) && HotbarRoles.accepts(player, HotbarRoles.PISTOL_SLOT, pistol), "a picked-up holster is worn: the pistol position takes it");
        var rig = new ItemStack(AflItems.FIELD_CHEST_RIG.get());
        h.assertTrue(inventory.add(rig) && AflEquipmentSlots.get(player, AflEquipmentSlots.RIG).is(AflItems.FIELD_CHEST_RIG.get()),
                "a picked-up rig is worn when the rig slot is free");
        var worn = AflEquipmentSlots.get(player, AflEquipmentSlots.BACK);
        inventory.add(new ItemStack(AflItems.HIKING_BACKPACK.get()));
        h.assertTrue(AflEquipmentSlots.get(player, AflEquipmentSlots.BACK) == worn, "a picked-up backpack never replaces the one worn");
        h.assertTrue(!HotbarRoles.accepts(player, HotbarRoles.MELEE_SLOT, pistol) && !HotbarRoles.accepts(player, 4, new ItemStack(Items.STONE)),
                "keys 4-9 are not hotbar positions any more");
        h.succeed();
    }

    @GameTest(template = "network_empty")
    public static void pocketsDropAndAmmo(GameTestHelper h) {
        var player = survivor(h, "grid_death");
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        var inventory = player.getInventory();
        var definition = ((NativeGunItem) AflItems.P9_01.get()).definition();
        inventory.add(new ItemStack(AflItems.ROUND_9MM.get(), 12));
        h.assertTrue(NativeGunAmmo.reserve(inventory, definition) == 12, "rounds in a pocket count as reserve");
        var gun = new ItemStack(AflItems.P9_01.get());
        NativeGunAmmo.set(gun, definition, 0);
        h.assertTrue(NativeGunAmmo.transfer(inventory, gun, definition) == 12 && NativeGunAmmo.reserve(inventory, definition) == 0,
                "reload takes the rounds out of the pocket");
        inventory.add(new ItemStack(Items.APPLE, 3));
        var box = new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(4);
        int before = h.getLevel().getEntitiesOfClass(ItemEntity.class, box).size();
        inventory.dropAll();
        var pocketsLeft = Pockets.of(player).items();
        int after = h.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.APPLE)).size();
        h.assertTrue(pocketsLeft.isEmpty() && after == 1, "dropAll empties the pockets onto the ground (before " + before + ")");
        h.assertTrue(GridContainerItem.read(new ItemStack(AflItems.CANVAS_BACKPACK.get()), false).isEmpty(), "a new backpack is empty");
        h.succeed();
    }

    private static ItemEntity lying(GameTestHelper h, double x, double z, ItemStack stack) {
        var item = new ItemEntity(h.getLevel(), 0, 0, 0, stack);
        item.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(x, 2, z)));
        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        h.getLevel().addFreshEntity(item);
        return item;
    }

    /** Ground items (plan 12): no walk-over pickup; nearby list; onto the cursor, straight in, dropped at the feet; reach. */
    @GameTest(template = "network_empty")
    public static void groundItems(GameTestHelper h) {
        var player = survivor(h, "grid_ground");
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        var inventory = player.getInventory();
        var apples = lying(h, 1.5, 1.5, new ItemStack(Items.APPLE, 5));
        apples.setNoPickUpDelay();
        apples.playerTouch(player);
        h.assertTrue(apples.isAlive() && apples.getItem().getCount() == 5 && !inGrids(player, Items.APPLE), "walking over an item does not pick it up");
        var far = lying(h, 1.5, 7.5, new ItemStack(Items.BREAD));
        var listed = com.antaurora.apofirstlight.inventory.grid.GroundItems.nearby(player);
        h.assertTrue(listed.contains(apples) && !listed.contains(far), "nearby lists the apples, not the bread 6 blocks away");
        act(player, GridActions.Kind.GROUND_QUICK, 0, 0, com.antaurora.apofirstlight.inventory.grid.GroundItems.AUTO, 0, false, far.getId());
        h.assertTrue(far.isAlive() && !inGrids(player, Items.BREAD), "out of reach: refused");

        act(player, GridActions.Kind.GROUND_PICK, 0, 0, 1, 0, false, apples.getId());
        h.assertTrue(player.inventoryMenu.getCarried().getCount() == 3 && apples.getItem().getCount() == 2, "half onto the cursor (3 of 5)");
        act(player, GridActions.Kind.GROUND_QUICK, 0, 0, com.antaurora.apofirstlight.inventory.grid.GroundItems.AUTO, 0, false, apples.getId());
        h.assertTrue(!apples.isAlive() && inGrids(player, Items.APPLE), "the rest straight into the pockets, the entity gone");
        act(player, GridActions.Kind.GROUND_DROP, 0, 0, 0, 0, false, 0);
        var box = new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(3);
        h.assertTrue(player.inventoryMenu.getCarried().isEmpty()
                        && h.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.APPLE) && e.getItem().getCount() == 3).size() == 1,
                "the cursor's 3 apples dropped at the feet");

        var rig = lying(h, 2.5, 1.5, new ItemStack(AflItems.FIELD_CHEST_RIG.get()));
        act(player, GridActions.Kind.GROUND_QUICK, 0, 0, com.antaurora.apofirstlight.inventory.grid.GroundItems.AUTO, 0, false, rig.getId());
        h.assertTrue(!rig.isAlive() && AflEquipmentSlots.get(player, AflEquipmentSlots.RIG).is(AflItems.FIELD_CHEST_RIG.get()), "a rig is worn when its slot is free");
        var rifle = lying(h, 2.5, 2.5, new ItemStack(AflItems.BR51_01.get()));
        act(player, GridActions.Kind.GROUND_QUICK, 0, 0, com.antaurora.apofirstlight.inventory.grid.GroundItems.AUTO, 0, false, rifle.getId());
        h.assertTrue(!rifle.isAlive() && inventory.getItem(HotbarRoles.HAND_SLOT).is(AflItems.BR51_01.get()),
                "a rifle without a sling that fits no grid goes into the empty hand");
        h.succeed();
    }

    /** A grid entry's place now, or null. */
    /** Items lying around the player: what a grid action dropped. */
    private static int dropped(GameTestHelper h, ServerPlayer player) {
        int n = 0;
        for (var e : h.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8))) n += e.getItem().getCount();
        return n;
    }

    private static GridStorage.Entry at(ServerPlayer player, PlayerGrids.Target target, int x, int y) {
        var storage = PlayerGrids.view(player).get(target);
        return storage == null ? null : storage.at(0, x, y);
    }

    /**
     * Container screens (plan 13): the grids work with any open menu showing the player's inventory, with that menu's
     * cursor; shift-click on a grid entry goes in by the menu's own rules and what does not go in stays where it was.
     */
    @GameTest(template = "network_empty")
    public static void containerGrids(GameTestHelper h) {
        var player = survivor(h, "grid_container");
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        var inventory = player.getInventory();
        wear(player, AflEquipmentSlots.BACK, new ItemStack(AflItems.CANVAS_BACKPACK.get()));
        int pockets = PlayerGrids.Target.POCKETS.ordinal(), back = PlayerGrids.Target.BACKPACK.ordinal();
        var chestItems = new net.minecraft.world.SimpleContainer(27);
        var chest = net.minecraft.world.inventory.ChestMenu.threeRows(1, inventory, chestItems);
        player.containerMenu = chest;

        inventory.add(new ItemStack(Items.APPLE, 10));
        act(player, GridActions.Kind.PICK, pockets, 0, 0, 0, false, 0);
        h.assertTrue(chest.getCarried().is(Items.APPLE) && chest.getCarried().getCount() == 10 && player.inventoryMenu.getCarried().isEmpty(),
                "a grid pick in a chest goes onto the chest menu's cursor");
        chest.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        h.assertTrue(chestItems.getItem(0).is(Items.APPLE) && chest.getCarried().isEmpty(), "and clicks into a chest slot");
        chestItems.setItem(1, new ItemStack(Items.COAL, 5));
        chest.clicked(1, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        act(player, GridActions.Kind.PLACE, back, 0, 4, 5, false, 0);
        h.assertTrue(chest.getCarried().isEmpty() && at(player, PlayerGrids.Target.BACKPACK, 4, 5) != null, "a chest stack placed into the backpack grid");

        player.containerMenu.setCarried(new ItemStack(Items.BREAD, 6));
        act(player, GridActions.Kind.PLACE, back, 0, 0, 0, false, 0);
        act(player, GridActions.Kind.TO_CONTAINER, back, 0, 0, 0, false, 0);
        int bread = 0;
        for (int i = 0; i < chestItems.getContainerSize(); i++) if (chestItems.getItem(i).is(Items.BREAD)) bread += chestItems.getItem(i).getCount();
        h.assertTrue(bread == 6 && !inGrids(player, Items.BREAD), "shift-click on a grid entry moves it into the chest");
        for (int i = 0; i < inventory.getContainerSize(); i++)
            h.assertTrue(i < HotbarRoles.USED && i != HotbarRoles.HAND_SLOT || inventory.getItem(i).isEmpty(), "player slot " + i + " left empty");

        for (int i = 0; i < chestItems.getContainerSize(); i++) chestItems.setItem(i, new ItemStack(Items.STONE, 64));
        player.containerMenu.setCarried(new ItemStack(Items.FLINT, 3));
        act(player, GridActions.Kind.PLACE, back, 0, 2, 2, false, 0);
        act(player, GridActions.Kind.TO_CONTAINER, back, 0, 2, 2, false, 0);
        var flint = at(player, PlayerGrids.Target.BACKPACK, 2, 2);
        h.assertTrue(flint != null && flint.stack().is(Items.FLINT) && flint.stack().getCount() == 3, "chest full: the flint stays where it was");
        h.assertTrue(dropped(h, player) == 0, "and no copy of it drops");

        var furnaceItems = new net.minecraft.world.SimpleContainer(3);
        var furnace = new net.minecraft.world.inventory.FurnaceMenu(2, inventory, furnaceItems, new net.minecraft.world.inventory.SimpleContainerData(4));
        player.containerMenu = furnace;
        act(player, GridActions.Kind.TO_CONTAINER, back, 0, 2, 2, false, 0);
        flint = at(player, PlayerGrids.Target.BACKPACK, 2, 2);
        h.assertTrue(flint != null && flint.stack().getCount() == 3 && furnaceItems.isEmpty() && dropped(h, player) == 0,
                "flint neither smelts nor burns: stays in the grid, no copy drops");
        // the reported dupe (2026-10-15): a 2x2 drum the furnace will not take went back to its place and dropped as well
        furnace.setCarried(new ItemStack(AflItems.BR51_DRUM_MAGAZINE_50.get()));
        act(player, GridActions.Kind.PLACE, back, 0, 0, 0, false, 0);
        h.assertTrue(at(player, PlayerGrids.Target.BACKPACK, 0, 0) != null, "the drum lies in the backpack");
        act(player, GridActions.Kind.TO_CONTAINER, back, 0, 0, 0, false, 0);
        var drum = at(player, PlayerGrids.Target.BACKPACK, 0, 0);
        h.assertTrue(drum != null && drum.stack().is(AflItems.BR51_DRUM_MAGAZINE_50.get()) && furnaceItems.isEmpty() && dropped(h, player) == 0,
                "the furnace refuses the drum: it stays where it was, once");
        furnace.setCarried(new ItemStack(Items.COBBLESTONE, 8));
        act(player, GridActions.Kind.PLACE, back, 0, 0, 3, false, 0);
        act(player, GridActions.Kind.TO_CONTAINER, back, 0, 0, 3, false, 0);
        h.assertTrue(furnaceItems.getItem(0).is(Items.COBBLESTONE) && furnaceItems.getItem(0).getCount() == 8
                && at(player, PlayerGrids.Target.BACKPACK, 0, 3) == null && !inGrids(player, Items.COBBLESTONE), "cobblestone goes to the furnace input, out of the grid");
        // part of a stack: the input holds 60, 4 of 8 go in, the other 4 stay
        furnaceItems.setItem(0, new ItemStack(Items.COBBLESTONE, 60));
        furnace.setCarried(new ItemStack(Items.COBBLESTONE, 8));
        act(player, GridActions.Kind.PLACE, back, 0, 0, 3, false, 0);
        act(player, GridActions.Kind.TO_CONTAINER, back, 0, 0, 3, false, 0);
        var cobble = at(player, PlayerGrids.Target.BACKPACK, 0, 3);
        h.assertTrue(furnaceItems.getItem(0).getCount() == 64 && cobble != null && cobble.stack().getCount() == 4 && dropped(h, player) == 0,
                "a full input takes 4: 4 stay, nothing drops");
        cobble.stack().setCount(0);
        PlayerGrids.view(player).commit();
        furnace.setCarried(new ItemStack(AflItems.CROWBAR.get()));
        act(player, GridActions.Kind.PLACE, back, 0, 3, 0, false, 0);
        act(player, GridActions.Kind.TO_CONTAINER, back, 0, 3, 0, false, 0);
        h.assertTrue(inventory.getItem(HotbarRoles.MELEE_SLOT).isEmpty() && at(player, PlayerGrids.Target.BACKPACK, 3, 0) != null && dropped(h, player) == 0,
                "a furnace's fallback move into the hotbar is undone: the crowbar stays in the grid, no copy drops");

        furnace.setCarried(new ItemStack(Items.STICK));
        act(player, GridActions.Kind.GROUND_DROP, 0, 0, 0, 0, false, 0);
        h.assertTrue(furnace.getCarried().is(Items.STICK), "ground actions only on the inventory page");
        furnace.setCarried(ItemStack.EMPTY);
        player.containerMenu = player.inventoryMenu;
        act(player, GridActions.Kind.TO_CONTAINER, back, 0, 2, 2, false, 0);
        h.assertTrue(at(player, PlayerGrids.Target.BACKPACK, 2, 2) != null, "no container open: nothing to move into");
        h.succeed();
    }

    /** Attachments (plan 13): a grid entry is a source at the maintenance bench; what comes off goes into the grids. */
    @GameTest(template = "network_empty")
    public static void attachmentsFromGrids(GameTestHelper h) {
        var root = h.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4));
        var level = h.getLevel();
        for (var part : com.antaurora.apofirstlight.block.StaticWorkstationBlock.Part.values())
            level.setBlock(com.antaurora.apofirstlight.block.StaticWorkstationBlock.partPosition(root, net.minecraft.core.Direction.NORTH, part),
                    com.antaurora.apofirstlight.registry.AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(net.minecraft.core.Direction.NORTH, part), 3);
        var bench = (com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity) level.getBlockEntity(root);
        var player = survivor(h, "grid_attach");
        player.setPos(root.getCenter());
        var menu = new com.antaurora.apofirstlight.menu.GunMaintenanceMenu(71, player.getInventory(), bench);
        player.containerMenu = menu;
        player.getInventory().setItem(4, new ItemStack(AflItems.P9_01.get()));
        h.assertTrue(menu.clickMenuButton(player, 4), "pistol onto the bench");
        var suppressor = AflItems.PISTOL_SUPPRESSOR_01.get();
        var muzzle = com.antaurora.apofirstlight.weapon.NativeAttachment.Slot.MUZZLE;
        player.getInventory().add(new ItemStack(suppressor));
        var entry = first(player, PlayerGrids.Target.POCKETS, suppressor);
        h.assertTrue(entry != null, "the suppressor lies in a pocket");
        int source = com.antaurora.apofirstlight.inventory.grid.GridSource.encode(PlayerGrids.Target.POCKETS, entry.section(), entry.x(), entry.y());
        var install = new com.antaurora.apofirstlight.weapon.MaintenanceActionRequest(menu.containerId, bench.getBlockPos(), bench.attachmentRevision(),
                bench.getItem(0).copy(), muzzle, source, entry.stack().copy());
        h.assertTrue(com.antaurora.apofirstlight.weapon.MaintenanceAttachmentTransaction.commit(player, install), "installed from the pocket");
        h.assertTrue(!inGrids(player, suppressor) && com.antaurora.apofirstlight.weapon.NativeAttachments.active(bench.getItem(0), muzzle).is(suppressor),
                "the pocket's suppressor is on the pistol");
        h.assertTrue(!com.antaurora.apofirstlight.weapon.MaintenanceAttachmentTransaction.commit(player, install), "the same grid source again: refused");
        var remove = new com.antaurora.apofirstlight.weapon.MaintenanceActionRequest(menu.containerId, bench.getBlockPos(), bench.attachmentRevision(),
                bench.getItem(0).copy(), muzzle, -1, ItemStack.EMPTY);
        h.assertTrue(com.antaurora.apofirstlight.weapon.MaintenanceAttachmentTransaction.commit(player, remove) && inGrids(player, suppressor),
                "taken off: back into the grids");
        for (int i = 0; i < 36; i++) h.assertTrue(player.getInventory().getItem(i).isEmpty(), "vanilla slot " + i + " stays empty (no weapon position, no hand)");
        bench.clearContent();
        player.containerMenu = player.inventoryMenu;
        h.succeed();
    }

    /**
     * The maintenance bench's gun picker: a gun goes up from a grid entry (GridSource) or a weapon position, only while it
     * is still the expected one; it comes back to where it was while that place is free and takes it, otherwise by the
     * normal pickup order, at the feet only when nothing takes it.
     */
    @GameTest(template = "network_empty")
    public static void benchGunPicker(GameTestHelper h) {
        var root = h.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4));
        var level = h.getLevel();
        for (var part : com.antaurora.apofirstlight.block.StaticWorkstationBlock.Part.values())
            level.setBlock(com.antaurora.apofirstlight.block.StaticWorkstationBlock.partPosition(root, net.minecraft.core.Direction.NORTH, part),
                    com.antaurora.apofirstlight.registry.AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(net.minecraft.core.Direction.NORTH, part), 3);
        var bench = (com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity) level.getBlockEntity(root);
        var player = survivor(h, "grid_bench");
        player.setPos(root.getCenter());
        wear(player, AflEquipmentSlots.BACK, new ItemStack(AflItems.CANVAS_BACKPACK.get()));
        var menu = new com.antaurora.apofirstlight.menu.GunMaintenanceMenu(72, player.getInventory(), bench);
        player.containerMenu = menu;
        var rifle = AflItems.BR51_01.get();
        var inventory = player.getInventory();

        // from the backpack: a rifle without a sling goes there by the pickup order
        var gun = new ItemStack(rifle);
        gun.getOrCreateTag().putString("BenchPickerMarker", "kept");
        inventory.add(gun.copy());
        var entry = first(player, PlayerGrids.Target.BACKPACK, rifle);
        h.assertTrue(entry != null, "the rifle lies in the backpack");
        int x = entry.x(), y = entry.y();
        boolean turned = entry.rotated();
        int source = com.antaurora.apofirstlight.inventory.grid.GridSource.encode(PlayerGrids.Target.BACKPACK, entry.section(), x, y);
        h.assertTrue(!menu.insert(player, source, new ItemStack(AflItems.P9_01.get())), "a request for another gun is refused");
        h.assertTrue(menu.insert(player, source, entry.stack().copy()), "onto the bench from the backpack");
        h.assertTrue(!inGrids(player, rifle) && ItemStack.matches(gun, bench.getItem(0)) && menu.originFor(player.getUUID()) == source,
                "out of the grid, on the bench, origin remembered");
        h.assertTrue(!menu.insert(player, source, gun), "the same request again: refused");
        var saved = bench.saveWithoutMetadata();
        bench.load(saved);
        h.assertTrue(menu.originFor(player.getUUID()) == source, "the grid origin survives save and load");
        h.assertTrue(menu.clickMenuButton(player, com.antaurora.apofirstlight.menu.GunMaintenanceMenu.RETURN_GUN), "taken back");
        var back = at(player, PlayerGrids.Target.BACKPACK, x, y);
        h.assertTrue(back != null && ItemStack.matches(gun, back.stack()) && back.rotated() == turned && bench.isEmpty(), "back where it was, turned the same");

        // origin taken: the normal pickup order finds it another place in the grids
        h.assertTrue(menu.insert(player, source, back.stack().copy()), "onto the bench again");
        var view = PlayerGrids.view(player);
        view.get(PlayerGrids.Target.BACKPACK).place(new ItemStack(Items.BREAD), 0, x, y, false);
        view.commit();
        h.assertTrue(menu.clickMenuButton(player, com.antaurora.apofirstlight.menu.GunMaintenanceMenu.RETURN_GUN), "taken back with its place taken");
        var moved = first(player, PlayerGrids.Target.BACKPACK, rifle);
        h.assertTrue(moved != null && ItemStack.matches(gun, moved.stack()) && dropped(h, player) == 0, "elsewhere in the backpack, nothing at the feet");

        // from the primary position with a sling: back to it
        var slung = moved.stack().copy();
        com.antaurora.apofirstlight.inventory.grid.GunSling.fit(slung);
        var emptied = PlayerGrids.view(player);
        var backpack = emptied.get(PlayerGrids.Target.BACKPACK);
        for (var e : java.util.List.copyOf(backpack.entries())) if (e.stack().is(rifle)) backpack.remove(e);
        emptied.commit();
        inventory.setItem(HotbarRoles.PRIMARY_SLOT, slung.copy());
        h.assertTrue(menu.insert(player, HotbarRoles.PRIMARY_SLOT, slung.copy()) && inventory.getItem(HotbarRoles.PRIMARY_SLOT).isEmpty(), "onto the bench from key 1");
        h.assertTrue(menu.clickMenuButton(player, com.antaurora.apofirstlight.menu.GunMaintenanceMenu.RETURN_GUN)
                && ItemStack.matches(slung, inventory.getItem(HotbarRoles.PRIMARY_SLOT)), "back to key 1");

        // nothing takes it: at the feet, the bench empty, the gun once
        h.assertTrue(menu.insert(player, HotbarRoles.PRIMARY_SLOT, slung.copy()), "onto the bench for the full case");
        inventory.setItem(HotbarRoles.PRIMARY_SLOT, new ItemStack(Items.STICK));
        wear(player, AflEquipmentSlots.BACK, ItemStack.EMPTY);
        h.assertTrue(menu.clickMenuButton(player, com.antaurora.apofirstlight.menu.GunMaintenanceMenu.RETURN_GUN) && bench.isEmpty(), "taken back with no room");
        var lying = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8), e -> e.getItem().is(rifle));
        h.assertTrue(lying.size() == 1 && ItemStack.matches(slung, lying.get(0).getItem()), "at the feet, once, NBT intact");
        lying.forEach(net.minecraft.world.entity.Entity::discard);
        player.containerMenu = player.inventoryMenu;
        h.succeed();
    }

    /**
     * What the other players' clients are sent of a carrier's body (CarriedWeaponsSync): the slung primary on the back
     * and its sling, the pistol in the holster while one is worn; a gun in the hand is not on the body (the sling still
     * is); creative the same, by the guns' roles; nothing for a spectator.
     */
    @GameTest(template = "network_empty")
    public static void carriedWeaponsShown(GameTestHelper h) {
        var player = survivor(h, "grid_carried");
        var inventory = player.getInventory();
        var rifle = slung(AflItems.BR51_01.get());
        var pistol = new ItemStack(AflItems.P9_01.get());
        inventory.setItem(HotbarRoles.PRIMARY_SLOT, rifle.copy());
        inventory.setItem(HotbarRoles.PISTOL_SLOT, pistol.copy());
        inventory.selected = HotbarRoles.HAND_SLOT;
        var shown = com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync.of(player);
        h.assertTrue(ItemStack.matches(rifle, shown.back()) && shown.sling() && shown.thigh().isEmpty(), "no holster worn: the rifle on the back only");
        wear(player, AflEquipmentSlots.HOLSTER, new ItemStack(AflItems.PISTOL_HOLSTER.get()));
        shown = com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync.of(player);
        h.assertTrue(ItemStack.matches(pistol, shown.thigh()), "a holster worn: the pistol in it");
        inventory.selected = HotbarRoles.PRIMARY_SLOT;
        shown = com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync.of(player);
        h.assertTrue(shown.back().isEmpty() && shown.sling() && !shown.thigh().isEmpty(), "rifle in the hand: off the back, the sling still on");
        inventory.selected = HotbarRoles.PISTOL_SLOT;
        shown = com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync.of(player);
        h.assertTrue(shown.thigh().isEmpty() && !shown.back().isEmpty(), "pistol in the hand: out of the holster");
        inventory.setItem(HotbarRoles.PRIMARY_SLOT, new ItemStack(AflItems.BR51_01.get()));
        inventory.selected = HotbarRoles.HAND_SLOT;
        shown = com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync.of(player);
        h.assertTrue(shown.back().isEmpty() && !shown.sling(), "a rifle without a sling is not on the back");
        // creative by the same rules (user 2026-10-17: a P9 put back into the holster vanished), the guns' roles checked:
        // its hotbar takes anything at index 1
        player.setGameMode(GameType.CREATIVE);
        shown = com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync.of(player);
        h.assertTrue(ItemStack.matches(pistol, shown.thigh()), "creative: the pistol in the holster");
        inventory.setItem(HotbarRoles.PISTOL_SLOT, slung(AflItems.BR51_01.get()));
        shown = com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync.of(player);
        h.assertTrue(shown.thigh().isEmpty(), "creative: a rifle at the pistol's index is not in the holster");
        player.setGameMode(GameType.SPECTATOR);
        h.assertTrue(com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync.of(player) == com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync.Shown.NONE,
                "spectator: nothing on the body");
        player.setGameMode(GameType.SURVIVAL);
        inventory.setItem(HotbarRoles.PISTOL_SLOT, ItemStack.EMPTY);
        wear(player, AflEquipmentSlots.HOLSTER, ItemStack.EMPTY);
        h.succeed();
    }
}
