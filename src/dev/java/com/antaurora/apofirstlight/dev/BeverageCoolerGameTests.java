package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.block.BeverageCoolerLayout;
import com.antaurora.apofirstlight.block.BeverageCoolerDoorRaycast;
import com.antaurora.apofirstlight.blockentity.BeverageCoolerBlockEntity;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@GameTestHolder("afl_beverage_cooler_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class BeverageCoolerGameTests {
    private static final String NAMESPACE = "afl_beverage_cooler_tests";

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = net.minecraft.nbt.TagParser.parseTag("{size:[16,8,16],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new net.minecraft.nbt.ListTag();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 8; y++) for (int z = 0; z < 16; z++) {
            var block = new net.minecraft.nbt.CompoundTag();
            var position = new net.minecraft.nbt.ListTag();
            for (int coordinate : new int[]{x, y, z}) position.add(net.minecraft.nbt.IntTag.valueOf(coordinate));
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
        return List.of(new TestFunction("beverage_cooler", NAMESPACE + ":integration",
                NAMESPACE + ":empty", 300, 0L, true, BeverageCoolerGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = (BeverageCoolerBlock) AflBlocks.BEVERAGE_COOLER.get();
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()),
                "iron pickaxe harvests");
        helper.assertTrue(!new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()),
                "stone pickaxe does not harvest");
        BlockPos master = helper.absolutePos(new BlockPos(7, 2, 7));
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < facings.length; i++) {
            Direction facing = facings[i];
            prepare(helper, master);
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cooler_test"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(master.getX() + 0.5, master.getY() + 1, master.getZ() + 3);
            player.setYRot(i * 90);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.BEVERAGE_COOLER.get()));
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                    player.getMainHandItem(), new BlockHitResult(Vec3.atBottomCenterOf(master),
                    Direction.UP, master.below(), false));
            helper.assertTrue(((BlockItem) AflItems.BEVERAGE_COOLER.get()).place(context).consumesAction(),
                    "places " + facing);
            BlockPos[] cells = new BlockPos[BeverageCoolerBlock.Part.values().length];
            for (BeverageCoolerBlock.Part part : BeverageCoolerBlock.Part.values()) {
                BlockPos cell = BeverageCoolerBlock.partPosition(master, facing, part);
                cells[part.ordinal()] = cell;
                var state = level.getBlockState(cell);
                helper.assertTrue(state.is(block) && state.getValue(BeverageCoolerBlock.PART) == part
                                && state.getValue(BeverageCoolerBlock.FACING) == facing,
                        "four matching cells " + facing + " / " + part);
                helper.assertTrue(!state.getValue(BeverageCoolerBlock.LEFT_OPEN)
                                && !state.getValue(BeverageCoolerBlock.RIGHT_OPEN), "initially closed");
            }
            var cooler = (BeverageCoolerBlockEntity) level.getBlockEntity(master);
            helper.assertTrue(cooler != null, "master owns BlockEntity");
            for (int j = 1; j < cells.length; j++)
                helper.assertTrue(level.getBlockEntity(cells[j]) == null, "slave has no BlockEntity");
            for (BlockPos cell : cells)
                helper.assertTrue(cooler.getRenderBoundingBox().contains(Vec3.atCenterOf(cell)),
                        "render box spans structure");

            checkShape(helper, master, facing, false, false);
            click(block, level, player, master, facing, true);
            helper.assertTrue(!level.getBlockState(master).getValue(BeverageCoolerBlock.LEFT_OPEN),
                    "left state waits for animation");
            click(block, level, player, master, facing, true); // Same-door spam must not restart.
            cooler.completeDue(level.getGameTime() + 8);
            checkStates(helper, cells, true, false);
            checkShape(helper, master, facing, true, false);
            click(block, level, player, master, facing, false);
            cooler.completeDue(level.getGameTime() + 8);
            checkStates(helper, cells, true, true);
            checkShape(helper, master, facing, true, true);
            click(block, level, player, master, facing, true);
            cooler.completeDue(level.getGameTime() + 8);
            checkStates(helper, cells, false, true);
            checkShape(helper, master, facing, false, true);
            click(block, level, player, master, facing, false);
            cooler.completeDue(level.getGameTime() + 8);
            checkStates(helper, cells, false, false);
            checkShape(helper, master, facing, false, false);

            // Both controllers accept transitions concurrently, and both commit independently.
            click(block, level, player, master, facing, true);
            click(block, level, player, master, facing, false);
            checkStates(helper, cells, false, false);
            cooler.completeDue(level.getGameTime() + 8);
            checkStates(helper, cells, true, true);
            checkShape(helper, master, facing, true, true);

            // The full projected glass leaf must remain clickable, not just its hinge.
            Vec3 leftStart = point(master, facing, 16, 10, -15);
            Vec3 leftEnd = point(master, facing, 28, 10, -15);
            var leftDoor = BeverageCoolerDoorRaycast.find(level, player, leftStart, leftEnd);
            helper.assertTrue(leftDoor != null && leftDoor.master().equals(master) && leftDoor.left(),
                    "ray hits full left open leaf " + facing);
            var leftState = level.getBlockState(leftDoor.hit().getBlockPos());
            block.use(leftState, level, leftDoor.hit().getBlockPos(), player,
                    InteractionHand.MAIN_HAND, leftDoor.hit());
            cooler.completeDue(level.getGameTime() + 8);
            checkStates(helper, cells, false, true);

            Vec3 rightStart = point(master, facing, 0, 10, -15);
            Vec3 rightEnd = point(master, facing, -14, 10, -15);
            var rightDoor = BeverageCoolerDoorRaycast.find(level, player, rightStart, rightEnd);
            helper.assertTrue(rightDoor != null && rightDoor.master().equals(master) && !rightDoor.left(),
                    "ray hits full right open leaf " + facing);
            var rightState = level.getBlockState(rightDoor.hit().getBlockPos());
            block.use(rightState, level, rightDoor.hit().getBlockPos(), player,
                    InteractionHand.MAIN_HAND, rightDoor.hit());
            cooler.completeDue(level.getGameTime() + 8);
            checkStates(helper, cells, false, false);
            checkShape(helper, master, facing, false, false);

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            helper.assertTrue(player.gameMode.destroyBlock(cells[i]), "break part " + i);
            for (BlockPos cell : cells) helper.assertTrue(level.getBlockState(cell).isAir(), "no ghost part");
            int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(master).inflate(4)).stream()
                    .filter(item -> item.getItem().is(AflItems.BEVERAGE_COOLER.get()))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(drops == 1, "exactly one item from part " + i + ", got " + drops);
        }

        prepare(helper, master);
        var creative = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cooler_creative"));
        creative.setGameMode(GameType.CREATIVE);
        creative.setPos(master.getX() + 0.5, master.getY() + 1, master.getZ() + 3);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.BEVERAGE_COOLER.get()));
        BlockPlaceContext creativeContext = new BlockPlaceContext(creative, InteractionHand.MAIN_HAND,
                creative.getMainHandItem(), new BlockHitResult(Vec3.atBottomCenterOf(master),
                Direction.UP, master.below(), false));
        helper.assertTrue(((BlockItem) AflItems.BEVERAGE_COOLER.get()).place(creativeContext).consumesAction(),
                "creative test placement");
        helper.assertTrue(creative.gameMode.destroyBlock(master.above()), "creative break upper master");
        assertStructureGone(helper, master, Direction.NORTH, "creative cleanup");
        helper.assertTrue(coolerDrops(level, master) == 0, "creative break drops nothing");

        prepare(helper, master);
        creative.setYRot(0);
        BlockPlaceContext explosionContext = new BlockPlaceContext(creative, InteractionHand.MAIN_HAND,
                creative.getMainHandItem(), new BlockHitResult(Vec3.atBottomCenterOf(master),
                Direction.UP, master.below(), false));
        helper.assertTrue(((BlockItem) AflItems.BEVERAGE_COOLER.get()).place(explosionContext).consumesAction(),
                "explosion test placement");
        level.explode(null, master.getX() + 0.5, master.getY() + 1,
                master.getZ() + 0.5, 4, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        assertStructureGone(helper, master, Direction.NORTH, "explosion cleanup");
        helper.assertTrue(coolerDrops(level, master) <= 1, "explosion never duplicates the cooler drop");

        prepare(helper, master);
        level.setBlock(master.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cooler_blocked"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(master.getX(), master.getY() + 1, master.getZ() + 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.BEVERAGE_COOLER.get()));
        BlockPlaceContext blocked = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                player.getMainHandItem(), new BlockHitResult(Vec3.atBottomCenterOf(master),
                Direction.UP, master.below(), false));
        helper.assertTrue(!((BlockItem) AflItems.BEVERAGE_COOLER.get()).place(blocked).consumesAction()
                && level.getBlockState(master).isAir(), "blocked upper position leaves no partial cooler");

        prepare(helper, master);
        player.setYRot(0);
        BlockPlaceContext valid = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                player.getMainHandItem(), new BlockHitResult(Vec3.atBottomCenterOf(master),
                Direction.UP, master.below(), false));
        helper.assertTrue(((BlockItem) AflItems.BEVERAGE_COOLER.get()).place(valid).consumesAction(),
                "final scheduled-tick placement");
        click(block, level, player, master, Direction.NORTH, true);
        helper.assertTrue(!level.getBlockState(master).getValue(BeverageCoolerBlock.LEFT_OPEN),
                "scheduled transition has not committed immediately");
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(level.getBlockState(master).getValue(BeverageCoolerBlock.LEFT_OPEN),
                    "scheduled tick commits left after animation");
            checkShape(helper, master, Direction.NORTH, true, false);
            checkContents(helper, block, master, player);
            ApocalypseFirstLight.LOGGER.info("[AFL BEVERAGE COOLER TEST] PASS four facings, four states, full open-leaf ray hits, independent concurrent doors, terminal shapes, delayed commit, 2x1x2 placement, blocked placement, iron tier, four break parts and single drops, creative and explosion cleanup, 18-slot searchable contents opened behind an open door and dropped once");
            helper.succeed();
        });
    }

    private static void checkStates(GameTestHelper helper, BlockPos[] cells, boolean left, boolean right) {
        for (BlockPos cell : cells) {
            var state = helper.getLevel().getBlockState(cell);
            helper.assertTrue(state.getValue(BeverageCoolerBlock.LEFT_OPEN) == left
                    && state.getValue(BeverageCoolerBlock.RIGHT_OPEN) == right,
                    "synchronized state " + left + "/" + right + " at " + cell);
        }
    }

    private static void checkShape(GameTestHelper helper, BlockPos master, Direction facing,
                                   boolean left, boolean right) {
        helper.assertTrue(contains(helper, master, facing, true, 16, 10, -7.5) == !left,
                "left front collision " + facing + " " + left + "/" + right);
        helper.assertTrue(contains(helper, master, facing, false, 0, 10, -7.5) == !right,
                "right front collision " + facing + " " + left + "/" + right);
        helper.assertTrue(contains(helper, master, facing, true, 22.9, 10, -15) == left,
                "left swung collision " + facing + " " + left + "/" + right);
        helper.assertTrue(contains(helper, master, facing, false, -6.9, 10, -15) == right,
                "right swung collision " + facing + " " + left + "/" + right);
        helper.assertTrue(containsSelection(helper, master, facing, true, 22, 10, -5) == left,
                "left open hinge remains selectable " + facing);
        helper.assertTrue(containsSelection(helper, master, facing, false, -6, 10, -5) == right,
                "right open hinge remains selectable " + facing);
        helper.assertTrue(!contains(helper, master, facing, true, 22, 10, -5)
                        && !contains(helper, master, facing, false, -6, 10, -5),
                "selectable open hinges do not add collision " + facing);
    }

    private static boolean contains(GameTestHelper helper, BlockPos master, Direction facing, boolean left,
                                    double x, double y, double z) {
        BlockPos cell = BeverageCoolerBlock.partPosition(master, facing,
                left ? BeverageCoolerBlock.Part.LOWER_LEFT : BeverageCoolerBlock.Part.LOWER_RIGHT);
        Vec3 world = point(master, facing, x, y, z);
        var state = helper.getLevel().getBlockState(cell);
        return state.getCollisionShape(helper.getLevel(), cell).toAabbs().stream().anyMatch(box ->
                box.contains(world.x - cell.getX(), world.y - cell.getY(), world.z - cell.getZ()));
    }

    private static boolean containsSelection(GameTestHelper helper, BlockPos master, Direction facing, boolean left,
                                             double x, double y, double z) {
        BlockPos cell = BeverageCoolerBlock.partPosition(master, facing,
                left ? BeverageCoolerBlock.Part.LOWER_LEFT : BeverageCoolerBlock.Part.LOWER_RIGHT);
        Vec3 world = point(master, facing, x, y, z);
        var state = helper.getLevel().getBlockState(cell);
        return state.getShape(helper.getLevel(), cell).toAabbs().stream().anyMatch(box ->
                box.contains(world.x - cell.getX(), world.y - cell.getY(), world.z - cell.getZ()));
    }

    /**
     * Left door open, right closed (NORTH): a level aim through shelf 1, column 4 onto the back wall opens the contents
     * (18 slots, 6 x 3 menu; placed by a player, so never searched) and moves nothing by hand. The closed right half is
     * out of reach. Breaking the master drops the contents once.
     */
    private static void checkContents(GameTestHelper helper, BeverageCoolerBlock block, BlockPos master,
                                      net.minecraft.server.level.ServerPlayer player) {
        var level = helper.getLevel();
        var cooler = (BeverageCoolerBlockEntity) level.getBlockEntity(master);
        helper.assertTrue(cooler != null && cooler.getContainerSize() == BeverageCoolerBlockEntity.SIZE
                        && cooler.aflSearchLayout() == AflContainerSearchLayout.GRID_6X3 && AflContainerSearch.isComplete(cooler),
                "18-slot searchable contents in the 6 x 3 menu, never searched when placed by a player");
        double x = BeverageCoolerLayout.columnX(4), y = BeverageCoolerLayout.shelfTop(1) + 2.0;
        Vec3 eye = point(master, Direction.NORTH, x, y, -24);
        Vec3 wall = point(master, Direction.NORTH, x, y, 6.8);
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
        var aim = new BlockHitResult(wall, Direction.NORTH, master, false);
        helper.assertTrue(block.use(level.getBlockState(master), level, master, player, InteractionHand.MAIN_HAND, aim).consumesAction()
                        && player.containerMenu != player.inventoryMenu && player.getMainHandItem().getCount() == 2 && cooler.isEmpty(),
                "aiming behind the open door opens the contents and moves nothing");
        player.closeContainer();

        double rightX = BeverageCoolerLayout.columnX(1);
        Vec3 rightEye = point(master, Direction.NORTH, rightX, y, -24);
        player.setPos(rightEye.x, rightEye.y - player.getEyeHeight(), rightEye.z);
        BlockPos rightCell = BeverageCoolerBlock.partPosition(master, Direction.NORTH, BeverageCoolerBlock.Part.LOWER_RIGHT);
        var closedAim = new BlockHitResult(point(master, Direction.NORTH, rightX, y, 6.8), Direction.NORTH, rightCell, false);
        helper.assertTrue(!block.use(level.getBlockState(rightCell), level, rightCell, player, InteractionHand.MAIN_HAND, closedAim).consumesAction(),
                "the closed right half is out of reach");

        cooler.setItem(3, new ItemStack(Items.APPLE));
        level.getEntitiesOfClass(ItemEntity.class, new AABB(master).inflate(4)).forEach(ItemEntity::discard);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(player.gameMode.destroyBlock(master), "break the master with an item inside");
        int apples = level.getEntitiesOfClass(ItemEntity.class, new AABB(master).inflate(4)).stream()
                .filter(item -> item.getItem().is(Items.APPLE)).mapToInt(item -> item.getItem().getCount()).sum();
        helper.assertTrue(apples == 1, "the stored item drops once, got " + apples);
    }

    private static void click(BeverageCoolerBlock block, net.minecraft.world.level.Level level,
                              net.minecraft.world.entity.player.Player player, BlockPos master,
                              Direction facing, boolean left) {
        BlockPos cell = BeverageCoolerBlock.partPosition(master, facing,
                left ? BeverageCoolerBlock.Part.LOWER_LEFT : BeverageCoolerBlock.Part.LOWER_RIGHT);
        boolean open = level.getBlockState(cell).getValue(left
                ? BeverageCoolerBlock.LEFT_OPEN : BeverageCoolerBlock.RIGHT_OPEN);
        Vec3 point = open ? point(master, facing, left ? 22 : -6, 10, -5)
                : point(master, facing, left ? 16 : 0, 10, -7.5);
        block.use(level.getBlockState(cell), level, cell, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(point, facing, cell, false));
    }

    private static Vec3 point(BlockPos master, Direction facing, double x, double y, double z) {
        Direction left = facing.getClockWise();
        double modelX = master.getX() + 0.5 - left.getStepX() * 0.5;
        double modelZ = master.getZ() + 0.5 - left.getStepZ() * 0.5;
        return new Vec3(modelX + (x - 8) / 16 * left.getStepX() - z / 16 * facing.getStepX(),
                master.getY() + y / 16,
                modelZ + (x - 8) / 16 * left.getStepZ() - z / 16 * facing.getStepZ());
    }

    private static void prepare(GameTestHelper helper, BlockPos master) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(master).inflate(6)).forEach(ItemEntity::discard);
        for (BlockPos pos : BlockPos.betweenClosed(master.offset(-2, 0, -2), master.offset(2, 2, 2)))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos pos : BlockPos.betweenClosed(master.offset(-2, -1, -2), master.offset(2, -1, 2)))
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void assertStructureGone(GameTestHelper helper, BlockPos master, Direction facing, String label) {
        for (BeverageCoolerBlock.Part part : BeverageCoolerBlock.Part.values())
            helper.assertTrue(helper.getLevel().getBlockState(BeverageCoolerBlock.partPosition(master, facing, part))
                    .isAir(), label + " " + part);
    }

    private static int coolerDrops(net.minecraft.server.level.ServerLevel level, BlockPos master) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(master).inflate(4)).stream()
                .filter(item -> item.getItem().is(AflItems.BEVERAGE_COOLER.get()))
                .mapToInt(item -> item.getItem().getCount()).sum();
    }
}
