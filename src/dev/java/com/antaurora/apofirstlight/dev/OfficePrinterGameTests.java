package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.OfficeMultifunctionPrinterBlock;
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

@GameTestHolder("afl_office_printer_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class OfficePrinterGameTests {
    private static final String NAMESPACE = "afl_office_printer_tests";

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
        return List.of(new TestFunction("office_printer", NAMESPACE + ":static_block",
                NAMESPACE + ":empty", 300, 0L, true, OfficePrinterGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(7, 2, 7));
        Block printer = AflBlocks.OFFICE_MULTIFUNCTION_PRINTER.get();
        Item item = AflItems.OFFICE_MULTIFUNCTION_PRINTER.get();

        helper.assertTrue(!(printer instanceof EntityBlock), "printer has no BlockEntity contract");
        helper.assertTrue(!printer.defaultBlockState().isSignalSource()
                        && !printer.hasAnalogOutputSignal(printer.defaultBlockState()),
                "printer has no redstone output");
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(printer.defaultBlockState()),
                "iron pickaxe harvests printer");
        helper.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(printer.defaultBlockState())
                        && new ItemStack(Items.NETHERITE_PICKAXE).isCorrectToolForDrops(printer.defaultBlockState()),
                "higher-tier pickaxes harvest printer");
        helper.assertTrue(!new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(printer.defaultBlockState())
                        && !new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(printer.defaultBlockState())
                        && !ItemStack.EMPTY.isCorrectToolForDrops(printer.defaultBlockState()),
                "stone, wood and empty hand cannot harvest printer");

        assertFacingShapes(helper, printer.defaultBlockState(), position, DoubleBlockHalf.LOWER, "lower");
        assertFacingShapes(helper, printer.defaultBlockState(), position.above(), DoubleBlockHalf.UPPER, "upper");

        reset(helper, position);
        helper.assertTrue(place(helper, item, position), "printer places");
        assertPair(helper, position);
        helper.assertTrue(destroy(helper, position, Items.IRON_PICKAXE, false, item) == 1,
                "breaking lower drops exactly once");
        assertCleared(helper, position, "lower break clears both halves");

        reset(helper, position);
        helper.assertTrue(place(helper, item, position), "printer places for upper break");
        helper.assertTrue(destroy(helper, position.above(), Items.IRON_PICKAXE, false, item) == 1,
                "breaking upper drops exactly once");
        assertCleared(helper, position, "upper break clears both halves");

        reset(helper, position);
        helper.assertTrue(place(helper, item, position), "printer places for wrong-tool break");
        helper.assertTrue(destroy(helper, position.above(), Items.STONE_PICKAXE, false, item) == 0,
                "stone pickaxe gives no printer drop");
        assertCleared(helper, position, "wrong-tool upper break clears both halves");

        reset(helper, position);
        helper.assertTrue(place(helper, item, position), "printer places for creative break");
        helper.assertTrue(destroy(helper, position.above(), Items.IRON_PICKAXE, true, item) == 0,
                "creative upper break gives no drop");
        assertCleared(helper, position, "creative upper break clears both halves");

        reset(helper, position);
        helper.getLevel().setBlock(position.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(!place(helper, item, position), "occupied upper space rejects printer");
        helper.assertTrue(helper.getLevel().getBlockState(position).isAir(),
                "failed placement leaves lower space empty");

        ApocalypseFirstLight.LOGGER.info("[AFL OFFICE PRINTER TEST] PASS iron-tier harvesting, four-facing shapes, "
                + "real double occupancy, upper/lower single drop, creative no-drop, ghost cleanup, placement guard");
        helper.succeed();
    }

    private static void assertFacingShapes(GameTestHelper helper, BlockState base, BlockPos position,
                                           DoubleBlockHalf half, String label) {
        BlockState north = base.setValue(OfficeMultifunctionPrinterBlock.FACING, Direction.NORTH)
                .setValue(OfficeMultifunctionPrinterBlock.HALF, half);
        BlockState east = north.rotate(Rotation.CLOCKWISE_90);
        helper.assertTrue(!north.getCollisionShape(helper.getLevel(), position).isEmpty(),
                label + " north shape exists");
        helper.assertTrue(Shapes.joinIsNotEmpty(north.getCollisionShape(helper.getLevel(), position),
                east.getCollisionShape(helper.getLevel(), position), BooleanOp.NOT_SAME), label + " shape rotates");
        helper.assertTrue(east.getValue(OfficeMultifunctionPrinterBlock.FACING) == Direction.EAST
                        && north.rotate(Rotation.CLOCKWISE_180).getValue(OfficeMultifunctionPrinterBlock.FACING) == Direction.SOUTH
                        && north.rotate(Rotation.COUNTERCLOCKWISE_90).getValue(OfficeMultifunctionPrinterBlock.FACING) == Direction.WEST,
                label + " supports four facings");
        for (AABB box : north.getCollisionShape(helper.getLevel(), position).toAabbs()) {
            helper.assertTrue(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0
                            && box.maxX <= 1 && box.maxY <= 1 && box.maxZ <= 1,
                    label + " shape stays inside its block");
        }
    }

    private static void assertPair(GameTestHelper helper, BlockPos lowerPosition) {
        BlockState lower = helper.getLevel().getBlockState(lowerPosition);
        BlockState upper = helper.getLevel().getBlockState(lowerPosition.above());
        helper.assertTrue(lower.is(AflBlocks.OFFICE_MULTIFUNCTION_PRINTER.get())
                && lower.getValue(OfficeMultifunctionPrinterBlock.HALF) == DoubleBlockHalf.LOWER,
                "printer lower exists");
        helper.assertTrue(upper.is(AflBlocks.OFFICE_MULTIFUNCTION_PRINTER.get())
                && upper.getValue(OfficeMultifunctionPrinterBlock.HALF) == DoubleBlockHalf.UPPER,
                "printer upper exists");
        helper.assertTrue(lower.getValue(OfficeMultifunctionPrinterBlock.FACING)
                        == upper.getValue(OfficeMultifunctionPrinterBlock.FACING),
                "printer halves share facing");
    }

    private static boolean place(GameTestHelper helper, Item item, BlockPos position) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "printer_place"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(position.getX(), position.getY(), position.getZ() + 3.0D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(position), Direction.UP, position.below(), false));
        return ((BlockItem) item).place(context).consumesAction();
    }

    private static int destroy(GameTestHelper helper, BlockPos position, Item tool,
                               boolean creative, Item expectedDrop) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).forEach(ItemEntity::discard);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "printer_break"));
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        helper.assertTrue(player.gameMode.destroyBlock(position), "printer block destroys at " + position);
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).stream()
                .filter(entity -> entity.getItem().is(expectedDrop))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void assertCleared(GameTestHelper helper, BlockPos lowerPosition, String label) {
        helper.assertTrue(helper.getLevel().getBlockState(lowerPosition).isAir()
                && helper.getLevel().getBlockState(lowerPosition.above()).isAir(), label);
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
