package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.LowFilingCabinetBlock;
import com.antaurora.apofirstlight.block.TallFilingCabinetBlock;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@GameTestHolder("afl_filing_cabinet_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class FilingCabinetGameTests {
    private static final String NAMESPACE = "afl_filing_cabinet_tests";

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = net.minecraft.nbt.TagParser.parseTag(
                "{size:[16,8,16],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
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
        return List.of(new TestFunction("filing_cabinets", NAMESPACE + ":static_blocks",
                NAMESPACE + ":empty", 260, 0L, true, FilingCabinetGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(7, 2, 7));
        var level = helper.getLevel();
        var low = AflBlocks.LOW_FILING_CABINET.get();
        var tall = AflBlocks.TALL_FILING_CABINET.get();

        helper.assertTrue(!(low instanceof EntityBlock) && !(tall instanceof EntityBlock),
                "filing cabinets have no BlockEntity contract");
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(low.defaultBlockState()),
                "iron pickaxe harvests low cabinet");
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(tall.defaultBlockState()),
                "iron pickaxe harvests tall cabinet");
        helper.assertTrue(!new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(low.defaultBlockState()),
                "stone pickaxe cannot harvest low cabinet");
        helper.assertTrue(!new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(tall.defaultBlockState()),
                "stone pickaxe cannot harvest tall cabinet");

        assertFacingShapes(helper, low.defaultBlockState(), position, "low");
        assertFacingShapes(helper, tall.defaultBlockState().setValue(TallFilingCabinetBlock.HALF,
                DoubleBlockHalf.LOWER), position, "tall lower");
        assertFacingShapes(helper, tall.defaultBlockState().setValue(TallFilingCabinetBlock.HALF,
                DoubleBlockHalf.UPPER), position, "tall upper");
        assertLowSeparatedDrawerShapes(helper, low.defaultBlockState(), position);
        assertTallSeparatedDrawerShapes(helper, tall.defaultBlockState(), position);

        reset(helper, position);
        helper.assertTrue(place(helper, AflItems.LOW_FILING_CABINET.get(), position), "low cabinet places");
        helper.assertTrue(destroy(helper, position, Items.IRON_PICKAXE, false, AflItems.LOW_FILING_CABINET.get()) == 1,
                "low cabinet drops once with iron pickaxe");

        reset(helper, position);
        helper.assertTrue(place(helper, AflItems.LOW_FILING_CABINET.get(), position), "low cabinet places for stone test");
        helper.assertTrue(destroy(helper, position, Items.STONE_PICKAXE, false, AflItems.LOW_FILING_CABINET.get()) == 0,
                "low cabinet does not drop with stone pickaxe");

        reset(helper, position);
        helper.assertTrue(place(helper, AflItems.TALL_FILING_CABINET.get(), position), "tall cabinet places");
        assertTallPair(helper, position);
        helper.assertTrue(destroy(helper, position, Items.IRON_PICKAXE, false, AflItems.TALL_FILING_CABINET.get()) == 1,
                "breaking tall lower drops once");
        helper.assertTrue(level.getBlockState(position).isAir() && level.getBlockState(position.above()).isAir(),
                "breaking tall lower clears both halves");

        reset(helper, position);
        helper.assertTrue(place(helper, AflItems.TALL_FILING_CABINET.get(), position), "tall cabinet replaces for upper break");
        helper.assertTrue(destroy(helper, position.above(), Items.IRON_PICKAXE, false,
                AflItems.TALL_FILING_CABINET.get()) == 1, "breaking tall upper drops once");
        helper.assertTrue(level.getBlockState(position).isAir() && level.getBlockState(position.above()).isAir(),
                "breaking tall upper clears both halves");

        reset(helper, position);
        helper.assertTrue(place(helper, AflItems.TALL_FILING_CABINET.get(), position), "tall cabinet places for stone test");
        helper.assertTrue(destroy(helper, position.above(), Items.STONE_PICKAXE, false,
                AflItems.TALL_FILING_CABINET.get()) == 0, "stone pickaxe gives no tall cabinet drop");
        helper.assertTrue(level.getBlockState(position).isAir() && level.getBlockState(position.above()).isAir(),
                "stone break still clears both halves");

        reset(helper, position);
        helper.assertTrue(place(helper, AflItems.TALL_FILING_CABINET.get(), position), "tall cabinet places for creative test");
        helper.assertTrue(destroy(helper, position.above(), Items.IRON_PICKAXE, true,
                AflItems.TALL_FILING_CABINET.get()) == 0, "creative upper break gives no drop");
        helper.assertTrue(level.getBlockState(position).isAir() && level.getBlockState(position.above()).isAir(),
                "creative upper break clears both halves");

        reset(helper, position);
        level.setBlock(position.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(!place(helper, AflItems.TALL_FILING_CABINET.get(), position),
                "occupied upper space rejects tall cabinet");
        helper.assertTrue(level.getBlockState(position).isAir(), "failed tall placement leaves lower space empty");

        ApocalypseFirstLight.LOGGER.info("[AFL FILING CABINET TEST] PASS iron/stone harvest, four-facing shapes, "
                + "separate body/drawer-handle shapes, real double occupancy, upper/lower single drop, "
                + "creative no-drop, ghost cleanup, placement guard");
        helper.succeed();
    }

    private static void assertLowSeparatedDrawerShapes(GameTestHelper helper, BlockState north,
                                                        BlockPos position) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = north.setValue(LowFilingCabinetBlock.FACING, facing);
            var body = LowFilingCabinetBlock.cabinetBodyShape(state);
            var handle1 = LowFilingCabinetBlock.drawerHandleShape(state, 1);
            var handle2 = LowFilingCabinetBlock.drawerHandleShape(state, 2);
            helper.assertTrue(!body.isEmpty() && !handle1.isEmpty() && !handle2.isEmpty(),
                    "low cabinet body and both handles exist for " + facing);
            helper.assertTrue(!Shapes.joinIsNotEmpty(handle1, handle2, BooleanOp.AND),
                    "low cabinet handles remain separated for " + facing);
            assertShapeEquals(helper, state.getCollisionShape(helper.getLevel(), position),
                    Shapes.or(body, handle1, handle2).optimize(), "low cabinet shape composition for " + facing);
        }
        helper.assertTrue(LowFilingCabinetBlock.drawerHandleShape(north, 0).isEmpty()
                        && LowFilingCabinetBlock.drawerHandleShape(north, 3).isEmpty(),
                "low cabinet rejects invalid drawer numbers");
    }

    private static void assertTallSeparatedDrawerShapes(GameTestHelper helper, BlockState north,
                                                         BlockPos position) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState lower = north
                    .setValue(TallFilingCabinetBlock.FACING, facing)
                    .setValue(TallFilingCabinetBlock.HALF, DoubleBlockHalf.LOWER);
            BlockState upper = lower.setValue(TallFilingCabinetBlock.HALF, DoubleBlockHalf.UPPER);
            var lowerBody = TallFilingCabinetBlock.cabinetBodyShape(lower);
            var handle1 = TallFilingCabinetBlock.drawerHandleShape(lower, 1);
            var handle2 = TallFilingCabinetBlock.drawerHandleShape(lower, 2);
            var upperBody = TallFilingCabinetBlock.cabinetBodyShape(upper);
            var handle3 = TallFilingCabinetBlock.drawerHandleShape(upper, 3);
            var handle4 = TallFilingCabinetBlock.drawerHandleShape(upper, 4);
            helper.assertTrue(!lowerBody.isEmpty() && !upperBody.isEmpty(),
                    "tall cabinet body shapes exist for " + facing);
            helper.assertTrue(!Shapes.joinIsNotEmpty(handle1, handle2, BooleanOp.AND)
                            && !Shapes.joinIsNotEmpty(handle3, handle4, BooleanOp.AND),
                    "tall cabinet handles remain separated for " + facing);
            helper.assertTrue(TallFilingCabinetBlock.drawerHandleShape(lower, 3).isEmpty()
                            && TallFilingCabinetBlock.drawerHandleShape(upper, 1).isEmpty(),
                    "tall cabinet drawer handles belong only to their block half for " + facing);
            assertShapeEquals(helper, lower.getCollisionShape(helper.getLevel(), position),
                    Shapes.or(lowerBody, handle1, handle2).optimize(),
                    "tall lower shape composition for " + facing);
            assertShapeEquals(helper, upper.getCollisionShape(helper.getLevel(), position.above()),
                    Shapes.or(upperBody, handle3, handle4).optimize(),
                    "tall upper shape composition for " + facing);
        }
    }

    private static void assertShapeEquals(GameTestHelper helper, net.minecraft.world.phys.shapes.VoxelShape actual,
                                          net.minecraft.world.phys.shapes.VoxelShape expected, String label) {
        helper.assertTrue(!Shapes.joinIsNotEmpty(actual, expected, BooleanOp.NOT_SAME), label);
    }

    private static void assertFacingShapes(GameTestHelper helper, BlockState north, BlockPos position, String label) {
        var level = helper.getLevel();
        BlockState east = north.rotate(Rotation.CLOCKWISE_90);
        helper.assertTrue(north.getValue(LowFilingCabinetBlock.FACING) == Direction.NORTH, label + " north state");
        helper.assertTrue(east.getValue(LowFilingCabinetBlock.FACING) == Direction.EAST, label + " east state");
        helper.assertTrue(!north.getCollisionShape(level, position).isEmpty(), label + " north shape exists");
        helper.assertTrue(Shapes.joinIsNotEmpty(north.getCollisionShape(level, position),
                east.getCollisionShape(level, position), BooleanOp.NOT_SAME), label + " shape rotates");
        helper.assertTrue(north.rotate(Rotation.CLOCKWISE_180).getValue(LowFilingCabinetBlock.FACING) == Direction.SOUTH,
                label + " south state");
        helper.assertTrue(north.rotate(Rotation.COUNTERCLOCKWISE_90).getValue(LowFilingCabinetBlock.FACING) == Direction.WEST,
                label + " west state");
    }

    private static void assertTallPair(GameTestHelper helper, BlockPos lowerPosition) {
        BlockState lower = helper.getLevel().getBlockState(lowerPosition);
        BlockState upper = helper.getLevel().getBlockState(lowerPosition.above());
        helper.assertTrue(lower.is(AflBlocks.TALL_FILING_CABINET.get())
                && lower.getValue(TallFilingCabinetBlock.HALF) == DoubleBlockHalf.LOWER, "tall lower exists");
        helper.assertTrue(upper.is(AflBlocks.TALL_FILING_CABINET.get())
                && upper.getValue(TallFilingCabinetBlock.HALF) == DoubleBlockHalf.UPPER, "tall upper exists");
        helper.assertTrue(lower.getValue(TallFilingCabinetBlock.FACING) == upper.getValue(TallFilingCabinetBlock.FACING),
                "tall halves share facing");
    }

    private static boolean place(GameTestHelper helper, Item item, BlockPos position) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "filing_place"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(position.getX(), position.getY(), position.getZ() + 3.0D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(position), Direction.UP, position.below(), false));
        return ((BlockItem) item).place(context).consumesAction();
    }

    private static int destroy(GameTestHelper helper, BlockPos position, Item tool, boolean creative, Item expectedDrop) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).forEach(ItemEntity::discard);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "filing_break"));
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        helper.assertTrue(player.gameMode.destroyBlock(position), "cabinet block destroys at " + position);
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).stream()
                .filter(entity -> entity.getItem().is(expectedDrop))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void reset(GameTestHelper helper, BlockPos center) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(6)).forEach(ItemEntity::discard);
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 2, 2))) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, -1, 2))) {
            level.setBlock(position, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
