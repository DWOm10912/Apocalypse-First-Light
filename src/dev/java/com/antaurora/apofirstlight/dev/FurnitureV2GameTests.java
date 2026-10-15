package com.antaurora.apofirstlight.dev;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.cabin.CabinPieces;
import com.antaurora.apofirstlight.cabin.EmergencyRadio;
import com.antaurora.apofirstlight.cabin.WoodFurnitureV2;
import com.antaurora.apofirstlight.cabin.WoodToolRackBlockEntity;
import com.mojang.authlib.GameProfile;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchMenu;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Wood Furniture V2 (docs/models/wood_furniture_v2.md, 2026-10-15):
 * <ul>
 * <li>Placement from the item: the bed (foot where clicked, head the way the player looks), the wardrobe (its upper
 * half), the vintage freezer and the storage chest (their right halves).</li>
 * <li>Opening in two steps (cabin/FurnitureOpening, aimed along the player's view): the doors / drawer / lid open (no
 * screen; {@code open} on every cell), the open interior gives the screen (wardrobe 27 from either half, nightstand 9,
 * freezer 27 from either half), the screen closed leaves them open, the doors / drawer / lid aimed at again close them; the
 * nightstand's top is no region (PASS: the radio goes there). A nightstand with a loot table that no player placed is
 * searched. The storage chest (two cells, 2026-10-15) the same: the lid, then 27 from either half.</li>
 * <li>Breaking one half takes the other: one item and the contents drop.</li>
 * <li>Tool rack: a tool on the aimed peg, a small item on the shelf, a block refused, taking back; its wall gone, it drops
 * with what hangs on it.</li>
 * <li>The emergency radio on the nightstand (surface {@code nightstand}).</li>
 * <li>Every piece of every state exists; survival mining (AGENTS.md audit): tool and tier tags, the hand, one tier too low,
 * the right tool.</li>
 * </ul>
 * Run with {@code src/dev/furniture-v2-gametest.init.gradle} (with -PaflWithoutShaders, the game closed).
 */
@GameTestHolder("afl_fv2_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class FurnitureV2GameTests {
    private static final String NAMESPACE = "afl_fv2_tests";
    private static final int SIZE = 16, HEIGHT = 8;
    private static final String[] IDS = {"wooden_bed", "wooden_wardrobe", "wooden_nightstand", "wooden_tool_rack", "wooden_storage_chest", "vintage_chest_freezer"};

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = TagParser.parseTag("{size:[" + SIZE + "," + HEIGHT + "," + SIZE + "],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new ListTag();
        for (int x = 0; x < SIZE; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIZE; z++) {
            var block = new CompoundTag();
            var position = new ListTag();
            for (int coordinate : new int[]{x, y, z}) position.add(IntTag.valueOf(coordinate));
            block.put("pos", position);
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        level.getStructureManager().getOrCreate(new ResourceLocation(NAMESPACE, "empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("furniture_v2", NAMESPACE + ":integration", NAMESPACE + ":empty", 100, 0L, true,
                FurnitureV2GameTests::run));
    }

    private static Block block(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, id));
        check(b != null && b != Blocks.AIR, "no block " + id);
        return b;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static FakePlayer player(ServerLevel level, String name, BlockPos at) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }

    /** The player (looking along yRot) places id's item on the floor cell at (clicking the top of the cell under it). */
    private static void place(ServerLevel level, FakePlayer p, String id, BlockPos at, float yRot) {
        p.setYRot(yRot);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block(id).asItem()));
        var context = new BlockPlaceContext(p, InteractionHand.MAIN_HAND, p.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(at), Direction.UP, at.below(), false));
        check(((BlockItem) block(id).asItem()).place(context).consumesAction(), id + " not placed at " + at);
    }

    /**
     * Right-clicks the cell with an empty hand, aimed: the player's eyes `up` above and `back` north of the point (x, y, z
     * from the cell's corner), looking at it (cabin/FurnitureOpening takes the region from the view ray).
     */
    static InteractionResult aim(ServerLevel level, FakePlayer p, BlockPos cell, double x, double y, double z, double up, double back) {
        Vec3 target = new Vec3(cell.getX() + x, cell.getY() + y, cell.getZ() + z);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setPos(target.x, target.y + up - p.getEyeHeight(), target.z - back);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        p.setYHeadRot(p.getYRot());
        return level.getBlockState(cell).use(level, p, InteractionHand.MAIN_HAND, new BlockHitResult(target, Direction.NORTH, cell, false));
    }

    /** {@link #aim} into the open interior: expects a chest screen of `slots`, returns its container (the screen left open). */
    private static Container view(ServerLevel level, FakePlayer p, BlockPos cell, double x, double y, double z, double up, double back, int slots, String what) {
        check(aim(level, p, cell, x, y, z, up, back).consumesAction(), what + ": the interior not used");
        check(p.containerMenu instanceof ChestMenu menu && menu.getContainer().getContainerSize() == slots,
                what + ": menu " + p.containerMenu + " (want " + slots + " slots)");
        return ((ChestMenu) p.containerMenu).getContainer();
    }

    /** Right-clicks the cell (empty hand), expects a chest screen of `slots`, returns its container (the screen left open). */
    private static Container open(ServerLevel level, FakePlayer p, BlockPos at, int slots, String what) {
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        level.getBlockState(at).use(level, p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
        check(p.containerMenu instanceof ChestMenu menu && menu.getContainer().getContainerSize() == slots,
                what + ": menu " + p.containerMenu + " (want " + slots + " slots)");
        return ((ChestMenu) p.containerMenu).getContainer();
    }

    private static boolean open(ServerLevel level, BlockPos at) {
        BlockState s = level.getBlockState(at);
        return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN);
    }

    private static List<ItemEntity> drops(ServerLevel level, BlockPos at) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2.5));
    }

    private static int dropped(ServerLevel level, BlockPos at, net.minecraft.world.item.Item item) {
        return drops(level, at).stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void clearDrops(ServerLevel level, BlockPos at) {
        for (ItemEntity e : drops(level, at)) e.discard();
    }

    private static void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        FakePlayer p = player(level, "fv2_test", helper.absolutePos(new BlockPos(0, 1, 0)));

        // ---- bed: foot where clicked, head one cell the way the player looks (south)
        BlockPos foot = helper.absolutePos(new BlockPos(1, 1, 1));
        place(level, p, "wooden_bed", foot, 0);
        BlockState footState = level.getBlockState(foot), headState = level.getBlockState(foot.south());
        check(footState.getBlock() instanceof WoodFurnitureV2.Bed && footState.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT
                && headState.getBlock() instanceof WoodFurnitureV2.Bed && headState.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD
                && footState.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.SOUTH, "bed placed " + footState + " / " + headState);
        check(level.getBlockEntity(foot) == null && footState.isBed(level, foot, p), "bed: a block entity, or not a bed");

        // ---- wardrobe: the upper half comes with it; 27 slots from either half, both doors open, closed again
        BlockPos wardrobe = helper.absolutePos(new BlockPos(4, 1, 1));
        place(level, p, "wooden_wardrobe", wardrobe, 0);
        BlockState upper = level.getBlockState(wardrobe.above());
        check(upper.getBlock() instanceof WoodFurnitureV2.Wardrobe && upper.getValue(WoodFurnitureV2.Wardrobe.HALF) == DoubleBlockHalf.UPPER
                && upper.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH, "wardrobe upper " + upper);
        check(level.getBlockEntity(wardrobe.above()) == null, "the wardrobe's upper half has a block entity");
        check(aim(level, p, wardrobe.above(), 0.3, 0.2, 0.34, 0, 1.5).consumesAction(), "wardrobe doors not used");
        check(open(level, wardrobe) && open(level, wardrobe.above()), "wardrobe doors not open on both halves");
        check(p.containerMenu == p.inventoryMenu, "wardrobe: a screen on opening the doors " + p.containerMenu);
        Container things = view(level, p, wardrobe.above(), 0.5, 0.2, 0.37, 0, 1.5, 27, "wardrobe (upper)");
        things.setItem(0, new ItemStack(Items.LEATHER_CHESTPLATE));
        p.closeContainer();
        check(open(level, wardrobe), "wardrobe doors shut with the screen");
        view(level, p, wardrobe, 0.5, 0.8, 0.37, 0, 1.5, 27, "wardrobe (lower)");
        p.closeContainer();
        check(aim(level, p, wardrobe.above(), 0.042, 0.2, 0.1, 0, 1.5).consumesAction(), "wardrobe left door not used");
        check(!open(level, wardrobe) && !open(level, wardrobe.above()), "wardrobe still open after closing");

        // ---- nightstand: 9 slots; the radio stands on it
        BlockPos stand = helper.absolutePos(new BlockPos(7, 1, 1));
        place(level, p, "wooden_nightstand", stand, 0);
        check(!aim(level, p, stand, 0.5, 0.66, 0.7, 1.0, 0.3).consumesAction(), "the nightstand's top took the click");
        check(aim(level, p, stand, 0.5, 0.58, 0.5, 0, 1.5).consumesAction() && open(level, stand), "nightstand drawer not pulled out");
        view(level, p, stand, 0.5, 0.6, 0.42, 1.0, 0.42, 9, "nightstand");
        p.closeContainer();
        check(aim(level, p, stand, 0.5, 0.55, 0.26, 0, 1.5).consumesAction() && !open(level, stand), "nightstand still open");
        // one with a loot table no player placed: searched
        BlockPos lootStand = helper.absolutePos(new BlockPos(7, 1, 4));
        level.setBlock(lootStand, block("wooden_nightstand").defaultBlockState(), 3);
        check(level.getBlockEntity(lootStand) instanceof RandomizableContainerBlockEntity, "nightstand block entity");
        ((RandomizableContainerBlockEntity) level.getBlockEntity(lootStand)).setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, 7L);
        check(aim(level, p, lootStand, 0.5, 0.58, 0.5, 0, 1.5).consumesAction() && open(level, lootStand), "loot nightstand drawer not pulled out");
        check(aim(level, p, lootStand, 0.5, 0.6, 0.42, 1.0, 0.42).consumesAction() && p.containerMenu instanceof AflContainerSearchMenu,
                "loot nightstand: menu " + p.containerMenu + " (want the search)");
        p.closeContainer();
        check(EmergencyRadio.surface(level, stand.above()) == EmergencyRadio.Surface.NIGHTSTAND, "radio surface on the nightstand: " + EmergencyRadio.surface(level, stand.above()));
        place(level, p, "emergency_radio", stand.above(), 0);
        check(level.getBlockState(stand.above()).getValue(EmergencyRadio.Radio.SURFACE) == EmergencyRadio.Surface.NIGHTSTAND, "radio placed " + level.getBlockState(stand.above()));

        // ---- vintage freezer: the right half to the north frame's +x (east, facing north); 27 slots from either half
        BlockPos freezer = helper.absolutePos(new BlockPos(9, 1, 1));
        place(level, p, "vintage_chest_freezer", freezer, 0);
        BlockState right = level.getBlockState(freezer.east());
        check(right.getBlock() instanceof WoodFurnitureV2.VintageFreezer && right.getValue(WoodFurnitureV2.VintageFreezer.PART) == WoodFurnitureV2.VintageFreezer.Part.RIGHT,
                "freezer right half " + right);
        check(WoodFurnitureV2.VintageFreezer.masterPosition(freezer.east(), right).equals(freezer), "freezer master position");
        check(aim(level, p, freezer.east(), 0.3, 0.9, 0.5, 1.0, 0.8).consumesAction(), "freezer lid not used");
        check(open(level, freezer) && open(level, freezer.east()), "freezer lid not open on both halves");
        check(p.containerMenu == p.inventoryMenu, "freezer: a screen on lifting the lid " + p.containerMenu);
        Container frozen = view(level, p, freezer.east(), 0.3, 0.83, 0.5, 1.0, 0.5, 27, "freezer (right)");
        frozen.setItem(3, new ItemStack(Items.BEEF, 5));
        p.closeContainer();
        view(level, p, freezer, 0.8, 0.83, 0.5, 1.0, 0.5, 27, "freezer (left)");
        p.closeContainer();
        check(aim(level, p, freezer, 0.8, 1.3, 0.85, 0, 1.5).consumesAction(), "open freezer lid not used");
        check(!open(level, freezer) && !open(level, freezer.east()), "freezer still open");
        check(!level.getBlockState(freezer).getValue(BlockStateProperties.LIT), "unplugged freezer lit");

        // ---- storage chest (the blanket chest): the right half to +x (east, facing north); the lid, then 27 from either half
        BlockPos chest = helper.absolutePos(new BlockPos(12, 1, 4));
        place(level, p, "wooden_storage_chest", chest, 0);
        BlockState chestRight = level.getBlockState(chest.east());
        check(chestRight.getBlock() instanceof WoodFurnitureV2.StorageChest && chestRight.getValue(WoodFurnitureV2.Wide.PART) == WoodFurnitureV2.Wide.Part.RIGHT,
                "chest right half " + chestRight);
        check(level.getBlockEntity(chest.east()) == null && WoodFurnitureV2.Wide.masterPosition(chest.east(), chestRight).equals(chest), "chest master");
        check(aim(level, p, chest.east(), 0.2, 0.6, 0.5, 1.0, 0.8).consumesAction() && open(level, chest) && open(level, chest.east()), "chest lid not raised");
        check(p.containerMenu == p.inventoryMenu, "chest: a screen on raising the lid " + p.containerMenu);
        Container chestThings = view(level, p, chest, 0.7, 0.56, 0.5, 1.0, 0.5, 27, "chest (left)");
        chestThings.setItem(5, new ItemStack(Items.ARROW, 16));
        p.closeContainer();
        view(level, p, chest.east(), 0.3, 0.56, 0.5, 1.0, 0.5, 27, "chest (right)");
        p.closeContainer();
        check(aim(level, p, chest, 0.8, 0.9, 0.75, 0, 1.5).consumesAction() && !open(level, chest) && !open(level, chest.east()), "chest lid still up");

        // ---- tool rack on a stone wall (rack facing north, the wall to its south)
        BlockPos rack = helper.absolutePos(new BlockPos(2, 2, 6));
        helper.setBlock(new BlockPos(2, 2, 7), Blocks.STONE);
        level.setBlock(rack, block("wooden_tool_rack").defaultBlockState(), 3);
        BlockState rs = level.getBlockState(rack);
        check(rs.getBlock() instanceof WoodFurnitureV2.ToolRack && rs.canSurvive(level, rack), "tool rack " + rs);
        check(level.getBlockEntity(rack) instanceof WoodToolRackBlockEntity, "no tool rack block entity");
        WoodToolRackBlockEntity tools = (WoodToolRackBlockEntity) level.getBlockEntity(rack);
        Vec3 peg0 = new Vec3(rack.getX() + 0.2, rack.getY() + 0.6, rack.getZ() + 0.9), shelf = new Vec3(rack.getX() + 0.3, rack.getY() + 0.9, rack.getZ() + 0.9);
        check(WoodFurnitureV2.ToolRack.slotAt(rs, rack, peg0) == 0 && WoodFurnitureV2.ToolRack.slotAt(rs, rack, shelf) == 4, "rack slots " +
                WoodFurnitureV2.ToolRack.slotAt(rs, rack, peg0) + " " + WoodFurnitureV2.ToolRack.slotAt(rs, rack, shelf));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        check(rs.use(level, p, InteractionHand.MAIN_HAND, new BlockHitResult(peg0, Direction.NORTH, rack, false)).consumesAction(), "axe not hung");
        check(tools.item(0).is(Items.IRON_AXE) && p.getMainHandItem().isEmpty(), "axe on peg 0: " + tools.item(0));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 7));
        check(rs.use(level, p, InteractionHand.MAIN_HAND, new BlockHitResult(peg0, Direction.NORTH, rack, false)).consumesAction(), "sticks not put");
        check(tools.item(3).is(Items.STICK) && tools.item(3).getCount() == 7, "sticks on the first free shelf place (3): " + tools.item(3));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
        check(!rs.use(level, p, InteractionHand.MAIN_HAND, new BlockHitResult(peg0, Direction.NORTH, rack, false)).consumesAction()
                && p.getMainHandItem().getCount() == 1, "a block went onto the rack");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(rs.use(level, p, InteractionHand.MAIN_HAND, new BlockHitResult(peg0, Direction.NORTH, rack, false)).consumesAction()
                && p.getMainHandItem().is(Items.IRON_AXE) && tools.item(0).isEmpty(), "axe not taken back");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        clearDrops(level, rack);
        helper.setBlock(new BlockPos(2, 2, 7), Blocks.AIR);
        check(level.getBlockState(rack).isAir(), "rack stayed without its wall");
        check(dropped(level, rack, block("wooden_tool_rack").asItem()) == 1 && dropped(level, rack, Items.STICK) == 7, "rack and sticks did not drop");
        clearDrops(level, rack);

        // ---- breaking one half takes the other: one item, the contents
        clearDrops(level, wardrobe);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        check(p.gameMode.destroyBlock(wardrobe.above()), "wardrobe upper not broken");
        check(level.getBlockState(wardrobe).isAir() && level.getBlockState(wardrobe.above()).isAir(), "wardrobe half left: " + level.getBlockState(wardrobe));
        check(dropped(level, wardrobe, block("wooden_wardrobe").asItem()) == 1 && dropped(level, wardrobe, Items.LEATHER_CHESTPLATE) == 1,
                "wardrobe drops " + dropped(level, wardrobe, block("wooden_wardrobe").asItem()) + " wardrobes, contents " + dropped(level, wardrobe, Items.LEATHER_CHESTPLATE));
        clearDrops(level, wardrobe);
        clearDrops(level, freezer);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        check(p.gameMode.destroyBlock(freezer.east()), "freezer right not broken");
        check(level.getBlockState(freezer).isAir(), "freezer master left: " + level.getBlockState(freezer));
        check(dropped(level, freezer, block("vintage_chest_freezer").asItem()) == 1 && dropped(level, freezer, Items.BEEF) == 5,
                "freezer drops " + dropped(level, freezer, block("vintage_chest_freezer").asItem()) + ", beef " + dropped(level, freezer, Items.BEEF));
        clearDrops(level, freezer);
        // the right half by hand (no iron pickaxe): no freezer, its things still drop (vanilla iron doors)
        place(level, p, "vintage_chest_freezer", freezer, 0);
        ((Container) level.getBlockEntity(freezer)).setItem(0, new ItemStack(Items.COD, 2));
        clearDrops(level, freezer);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(p.gameMode.destroyBlock(freezer.east()), "freezer right not broken by hand");
        check(level.getBlockState(freezer).isAir() && dropped(level, freezer, block("vintage_chest_freezer").asItem()) == 0
                && dropped(level, freezer, Items.COD) == 2, "hand-broken freezer: " + level.getBlockState(freezer) + ", freezers "
                + dropped(level, freezer, block("vintage_chest_freezer").asItem()) + ", cod " + dropped(level, freezer, Items.COD));
        clearDrops(level, freezer);
        clearDrops(level, foot);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(p.gameMode.destroyBlock(foot), "bed foot not broken");
        check(level.getBlockState(foot.south()).isAir() && dropped(level, foot, block("wooden_bed").asItem()) == 1, "bed: head left or drops "
                + dropped(level, foot, block("wooden_bed").asItem()));
        clearDrops(level, foot);
        clearDrops(level, chest);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(p.gameMode.destroyBlock(chest.east()), "chest right not broken");
        check(level.getBlockState(chest).isAir() && dropped(level, chest, block("wooden_storage_chest").asItem()) == 1 && dropped(level, chest, Items.ARROW) == 16,
                "chest: " + level.getBlockState(chest) + ", chests " + dropped(level, chest, block("wooden_storage_chest").asItem()) + ", arrows " + dropped(level, chest, Items.ARROW));
        clearDrops(level, chest);

        // ---- every piece of every state exists (bed, tool rack: cabin pieces); the rest are animated meshes (Furniture
        // Mesh V1): profile, geo, sidecar and atlas in the jar, the master's block entity their host
        var modFile = ModList.get().getModFileById(ApocalypseFirstLight.MOD_ID).getFile();
        Set<String> seen = new TreeSet<>();
        List<String> missing = new ArrayList<>();
        for (String id : IDS) {
            Block b = block(id);
            if (b instanceof CabinPieces cabin) {
                for (BlockState any : b.getStateDefinition().getPossibleStates()) cabin.cabinPieces(any, 0, (name, turn, dy) -> seen.add("cabin_mesh/" + name + ".json"));
                continue;
            }
            for (String file : new String[]{"block_mesh_profiles/" + id + ".json", "geo/" + id + ".geo.json", "meshes/" + id + ".aflmesh.json", "textures/block/furniture/" + id + ".png"})
                seen.add(file);
            check(b instanceof EntityBlock e && e.newBlockEntity(BlockPos.ZERO, b.defaultBlockState()) instanceof AflAnimatedMeshHost, id + ": the master is no animated mesh host");
        }
        for (String name : seen)
            if (!Files.exists(modFile.findResource(("assets/" + ApocalypseFirstLight.MOD_ID + "/" + name).split("/")))) missing.add(name);
        check(seen.size() == 20 && missing.isEmpty(), "pieces / mesh files " + seen.size() + " " + seen + ", missing " + missing);

        // ---- survival mining
        String report = mining(helper);
        ApocalypseFirstLight.LOGGER.info("[AFL FURNITURE V2 TEST] PASS placement, screens, open states, halves, tool rack, radio on nightstand, {} pieces, mining {}",
                seen.size(), report);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------- mining

    private record Rule(String id, TagKey<Block> tool, TagKey<Block> tier, boolean requires, ItemStack low, ItemStack right) {}

    private static String mining(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack hand = ItemStack.EMPTY;
        var axe = BlockTags.MINEABLE_WITH_AXE;
        List<Rule> rules = List.of(
                new Rule("wooden_bed", axe, null, false, hand, new ItemStack(Items.WOODEN_AXE)),
                new Rule("wooden_wardrobe", axe, null, false, hand, new ItemStack(Items.WOODEN_AXE)),
                new Rule("wooden_nightstand", axe, null, false, hand, new ItemStack(Items.WOODEN_AXE)),
                new Rule("wooden_tool_rack", axe, null, false, hand, new ItemStack(Items.WOODEN_AXE)),
                new Rule("wooden_storage_chest", axe, null, false, hand, new ItemStack(Items.WOODEN_AXE)),
                new Rule("vintage_chest_freezer", BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL, true,
                        new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE)));
        List<TagKey<Block>> tools = List.of(axe, BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE);
        List<TagKey<Block>> tiers = List.of(BlockTags.NEEDS_STONE_TOOL, BlockTags.NEEDS_IRON_TOOL, BlockTags.NEEDS_DIAMOND_TOOL);
        BlockPos cell = new BlockPos(12, 4, 12);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            helper.setBlock(cell.offset(dx, dy, dz), dx == 0 && dy == 0 && dz == 0 ? Blocks.AIR : Blocks.STONE);
        BlockPos at = helper.absolutePos(cell);
        FakePlayer miner = player(level, "fv2_mining", at.below());
        int breaks = 0;
        for (Rule r : rules) {
            BlockState s = block(r.id).defaultBlockState();
            if (s.hasProperty(BlockStateProperties.BED_PART)) s = s.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);   // the bed drops from its head
            for (TagKey<Block> t : tools) check(s.is(t) == (t == r.tool), r.id + ": tool tag " + t.location() + " " + s.is(t));
            for (TagKey<Block> t : tiers) check(s.is(t) == (t == r.tier), r.id + ": tier tag " + t.location() + " " + s.is(t));
            check(s.requiresCorrectToolForDrops() == r.requires, r.id + ": requiresCorrectToolForDrops " + s.requiresCorrectToolForDrops());
            check(dropsWhenBroken(level, miner, at, s, r.low) == !r.requires, r.id + ": broken with " + name(r.low));
            check(dropsWhenBroken(level, miner, at, s, r.right), r.id + ": nothing dropped with " + name(r.right));
            if (r.requires && !r.low.isEmpty()) check(!dropsWhenBroken(level, miner, at, s, hand), r.id + ": dropped by hand");
            breaks += r.requires && !r.low.isEmpty() ? 3 : 2;
        }
        return rules.size() + " blocks, " + breaks + " breaks";
    }

    private static String name(ItemStack tool) { return tool.isEmpty() ? "the hand" : ForgeRegistries.ITEMS.getKey(tool.getItem()).toString(); }

    private static boolean dropsWhenBroken(ServerLevel level, FakePlayer miner, BlockPos at, BlockState s, ItemStack tool) {
        clearDrops(level, at);
        level.setBlock(at, s, 3);
        check(level.getBlockState(at).is(s.getBlock()), "could not set " + s);
        miner.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
        check(miner.gameMode.destroyBlock(at), "not broken: " + s);
        check(level.getBlockState(at).isAir(), "still there after breaking: " + level.getBlockState(at));
        boolean dropped = drops(level, at).stream().anyMatch(e -> e.getItem().is(s.getBlock().asItem()));
        clearDrops(level, at);
        return dropped;
    }
}
