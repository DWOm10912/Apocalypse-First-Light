package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.ChestFreezerBlock;
import com.antaurora.apofirstlight.blockentity.ChestFreezerBlockEntity;
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

@GameTestHolder("afl_chest_freezer_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class ChestFreezerGameTests {
    private static final String NAMESPACE = "afl_chest_freezer_tests";

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
        return List.of(new TestFunction("chest_freezer", NAMESPACE + ":integration",
                NAMESPACE + ":empty", 300, 0L, true, ChestFreezerGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = (ChestFreezerBlock) AflBlocks.CHEST_FREEZER.get();
        helper.assertTrue(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()), "wood too weak");
        helper.assertTrue(!new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()), "stone too weak");
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()), "iron mines");
        helper.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()), "diamond mines");
        BlockPos master = helper.absolutePos(new BlockPos(7, 2, 7));
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < facings.length; i++) {
            Direction facing = facings[i];
            prepare(helper, master);
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "freezer_test"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(master.getX() + 0.5, master.getY() + 1, master.getZ() + 3);
            player.setYRot(i * 90);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.CHEST_FREEZER.get()));
            helper.assertTrue(place(player, master), "placement " + facing);
            BlockPos right = ChestFreezerBlock.partPosition(master, facing, ChestFreezerBlock.Part.RIGHT);
            checkState(helper, master, right, facing, ChestFreezerBlock.LidState.CLOSED);
            var freezer = (ChestFreezerBlockEntity) level.getBlockEntity(master);
            helper.assertTrue(freezer != null && level.getBlockEntity(right) == null, "one master BE");
            helper.assertTrue(freezer.getRenderBoundingBox().contains(Vec3.atCenterOf(master))
                    && freezer.getRenderBoundingBox().contains(Vec3.atCenterOf(right)), "2-cell render AABB");
            checkShape(helper, master, facing, ChestFreezerBlock.LidState.CLOSED);
            click(block, level, player, master, facing, 8, 14.55);
            checkState(helper, master, right, facing, ChestFreezerBlock.LidState.CLOSED);
            click(block, level, player, master, facing, 8, 14.55); // spam cannot restart
            freezer.completeDue(level.getGameTime() + 7);
            checkState(helper, master, right, facing, ChestFreezerBlock.LidState.LEFT_OPEN);
            checkShape(helper, master, facing, ChestFreezerBlock.LidState.LEFT_OPEN);
            click(block, level, player, master, facing, 24, 15.05); // stacked lid closes left
            freezer.completeDue(level.getGameTime() + 7);
            checkState(helper, master, right, facing, ChestFreezerBlock.LidState.CLOSED);
            click(block, level, player, master, facing, 24, 15.05);
            freezer.completeDue(level.getGameTime() + 7);
            checkState(helper, master, right, facing, ChestFreezerBlock.LidState.RIGHT_OPEN);
            checkShape(helper, master, facing, ChestFreezerBlock.LidState.RIGHT_OPEN);
            click(block, level, player, master, facing, 8, 14.55); // stacked lid closes right
            freezer.completeDue(level.getGameTime() + 7);
            checkState(helper, master, right, facing, ChestFreezerBlock.LidState.CLOSED);

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            BlockPos broken = i % 2 == 0 ? master : right;
            helper.assertTrue(player.gameMode.destroyBlock(broken), "survival break " + facing);
            helper.assertTrue(level.getBlockState(master).isAir() && level.getBlockState(right).isAir(), "no ghost half");
            helper.assertTrue(drops(level, master) == 1, "one freezer drop " + facing);
        }

        prepare(helper, master);
        var creative = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "freezer_creative"));
        creative.setGameMode(GameType.CREATIVE);
        creative.setPos(master.getX() + 0.5, master.getY() + 1, master.getZ() + 3);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.CHEST_FREEZER.get()));
        helper.assertTrue(place(creative, master), "creative placement");
        BlockPos right = ChestFreezerBlock.partPosition(master, Direction.NORTH, ChestFreezerBlock.Part.RIGHT);
        helper.assertTrue(creative.gameMode.destroyBlock(right), "creative break");
        helper.assertTrue(level.getBlockState(master).isAir() && level.getBlockState(right).isAir(), "creative cleanup");
        helper.assertTrue(drops(level, master) == 0, "creative no drop");

        var miner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "freezer_mining"));
        miner.setGameMode(GameType.SURVIVAL);
        miner.setPos(master.getX() + 0.5, master.getY() + 1, master.getZ() + 3);
        ItemStack[] tools = {ItemStack.EMPTY, new ItemStack(Items.WOODEN_PICKAXE),
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.NETHERITE_PICKAXE)};
        for (int i = 0; i < tools.length; i++) {
            prepare(helper, master);
            creative.setYRot(0);
            helper.assertTrue(place(creative, master), "mining setup " + i);
            miner.setItemInHand(InteractionHand.MAIN_HAND, tools[i]);
            helper.assertTrue(miner.gameMode.destroyBlock(i % 2 == 0 ? master : right), "mining break " + i);
            helper.assertTrue(level.getBlockState(master).isAir() && level.getBlockState(right).isAir(), "mining cleanup " + i);
            helper.assertTrue(drops(level, master) == (i >= 3 ? 1 : 0), "mining tier drop " + i);
        }

        prepare(helper, master);
        helper.assertTrue(place(creative, master), "explosion setup");
        level.explode(null, master.getX() + 0.5, master.getY() + 0.5,
                master.getZ() + 0.5, 4, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        helper.assertTrue(level.getBlockState(master).isAir() && level.getBlockState(right).isAir(), "explosion cleanup");
        helper.assertTrue(drops(level, master) <= 1, "explosion cannot duplicate drop");

        prepare(helper, master);
        creative.setYRot(0);
        helper.assertTrue(place(creative, master), "blocked test setup");
        level.setBlock(master, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        prepare(helper, master);
        level.setBlock(right, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(!place(creative, master) && level.getBlockState(master).isAir(), "blocked second cell is atomic");

        prepare(helper, master);
        helper.assertTrue(place(creative, master), "scheduled transition placement");
        click(block, level, creative, master, Direction.NORTH, 8, 14.55);
        helper.assertTrue(level.getBlockState(master).getValue(ChestFreezerBlock.LID) == ChestFreezerBlock.LidState.CLOSED,
                "state waits for animation");
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(level.getBlockState(master).getValue(ChestFreezerBlock.LID) == ChestFreezerBlock.LidState.LEFT_OPEN,
                    "scheduled commit after seven ticks");
            checkShape(helper, master, Direction.NORTH, ChestFreezerBlock.LidState.LEFT_OPEN);
            ApocalypseFirstLight.LOGGER.info("[AFL CHEST FREEZER TEST] PASS four facings, states, open selection/collision, delayed commit, survival tool drops, break cleanup and single drop, creative, explosion, blocked placement");
            helper.succeed();
        });
    }

    private static boolean place(net.minecraft.world.entity.player.Player player, BlockPos master) {
        var stack = player.getMainHandItem();
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atBottomCenterOf(master), Direction.UP, master.below(), false));
        return ((BlockItem) AflItems.CHEST_FREEZER.get()).place(context).consumesAction();
    }

    private static void checkState(GameTestHelper helper, BlockPos master, BlockPos right, Direction facing,
                                   ChestFreezerBlock.LidState lid) {
        for (ChestFreezerBlock.Part part : ChestFreezerBlock.Part.values()) {
            BlockPos pos = part == ChestFreezerBlock.Part.LEFT ? master : right;
            var state = helper.getLevel().getBlockState(pos);
            helper.assertTrue(state.getBlock() == AflBlocks.CHEST_FREEZER.get()
                    && state.getValue(ChestFreezerBlock.PART) == part
                    && state.getValue(ChestFreezerBlock.FACING) == facing
                    && state.getValue(ChestFreezerBlock.LID) == lid, "part/state " + facing + " " + part + " " + lid);
        }
    }

    private static void checkShape(GameTestHelper helper, BlockPos master, Direction facing,
                                   ChestFreezerBlock.LidState lid) {
        boolean leftCovered = lid != ChestFreezerBlock.LidState.LEFT_OPEN;
        boolean rightCovered = lid != ChestFreezerBlock.LidState.RIGHT_OPEN;
        helper.assertTrue(contains(helper, master, facing, 8, 14.45, false) == leftCovered,
                "left collision/selection " + facing + " " + lid);
        helper.assertTrue(contains(helper, master, facing, 8, 14.45, true) == leftCovered,
                "left selection " + facing + " " + lid);
        helper.assertTrue(contains(helper, master, facing, 24, 15.05, false) == rightCovered,
                "right collision " + facing + " " + lid);
        helper.assertTrue(contains(helper, master, facing, 24, 15.05, true) == rightCovered,
                "right selection " + facing + " " + lid);
    }

    private static boolean contains(GameTestHelper helper, BlockPos master, Direction facing,
                                    double sourceX, double y, boolean selection) {
        Vec3 world = point(master, facing, sourceX, y, 8);
        BlockPos cell = ChestFreezerBlock.partPosition(master, facing,
                sourceX < 16 ? ChestFreezerBlock.Part.LEFT : ChestFreezerBlock.Part.RIGHT);
        var state = helper.getLevel().getBlockState(cell);
        var shape = selection ? state.getShape(helper.getLevel(), cell)
                : state.getCollisionShape(helper.getLevel(), cell);
        return shape.toAabbs().stream().anyMatch(box -> box.contains(
                world.x - cell.getX(), world.y - cell.getY(), world.z - cell.getZ()));
    }

    private static void click(ChestFreezerBlock block, net.minecraft.world.level.Level level,
                              net.minecraft.world.entity.player.Player player, BlockPos master,
                              Direction facing, double sourceX, double y) {
        BlockPos cell = ChestFreezerBlock.partPosition(master, facing,
                sourceX < 16 ? ChestFreezerBlock.Part.LEFT : ChestFreezerBlock.Part.RIGHT);
        block.use(level.getBlockState(cell), level, cell, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(point(master, facing, sourceX, y, 8), Direction.UP, cell, false));
    }

    private static Vec3 point(BlockPos master, Direction facing, double x, double y, double z) {
        Direction toRight = facing.getCounterClockWise();
        return new Vec3(master.getX() + 0.5 + (x - 8) / 16 * toRight.getStepX() - (z - 8) / 16 * facing.getStepX(),
                master.getY() + y / 16,
                master.getZ() + 0.5 + (x - 8) / 16 * toRight.getStepZ() - (z - 8) / 16 * facing.getStepZ());
    }

    private static void prepare(GameTestHelper helper, BlockPos master) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(master).inflate(4)).forEach(ItemEntity::discard);
        for (BlockPos pos : BlockPos.betweenClosed(master.offset(-2, 0, -2), master.offset(2, 2, 2)))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos pos : BlockPos.betweenClosed(master.offset(-2, -1, -2), master.offset(2, -1, 2)))
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static int drops(net.minecraft.server.level.ServerLevel level, BlockPos master) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(master).inflate(4)).stream()
                .filter(item -> item.getItem().is(AflItems.CHEST_FREEZER.get()))
                .mapToInt(item -> item.getItem().getCount()).sum();
    }
}
