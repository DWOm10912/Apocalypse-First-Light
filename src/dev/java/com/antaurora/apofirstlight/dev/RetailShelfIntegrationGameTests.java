package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RetailShelfLayout;
import com.antaurora.apofirstlight.block.RetailShelfSingleBlock;
import com.antaurora.apofirstlight.blockentity.RetailShelfSingleBlockEntity;
import com.antaurora.apofirstlight.containersearch.AflContainerGoods;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.nio.file.Path;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class RetailShelfIntegrationGameTests {
    private static final String NAMESPACE = ApocalypseFirstLight.MOD_ID;
    private static final String TEMPLATE = "retail_shelf_empty";

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        StructureUtils.testStructuresDir = Path.of(System.getProperty("user.dir"), "..", "src", "dev",
                "gameteststructures").normalize().toString();
        return List.of(new TestFunction("retail_shelf", NAMESPACE + ":retail_shelf_integration",
                NAMESPACE + ":" + TEMPLATE, 300, 0L, true, RetailShelfIntegrationGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        BlockPos lower = helper.absolutePos(new BlockPos(7, 2, 7));
        reset(helper, lower);
        helper.assertTrue(place(helper, lower), "old Registry ID BlockItem places the shelf");
        BlockState lowerState = helper.getLevel().getBlockState(lower);
        BlockState upperState = helper.getLevel().getBlockState(lower.above());
        helper.assertTrue(lowerState.is(AflBlocks.RETAIL_SHELF_SINGLE.get())
                        && upperState.is(AflBlocks.RETAIL_SHELF_SINGLE.get())
                        && lowerState.getValue(RetailShelfSingleBlock.HALF) == DoubleBlockHalf.LOWER
                        && upperState.getValue(RetailShelfSingleBlock.HALF) == DoubleBlockHalf.UPPER
                        && lowerState.getValue(RetailShelfSingleBlock.FACING)
                        == upperState.getValue(RetailShelfSingleBlock.FACING),
                "shelf uses a synchronized two-block pair");
        var shelf = (RetailShelfSingleBlockEntity) helper.getLevel().getBlockEntity(lower);
        helper.assertTrue(shelf != null && helper.getLevel().getBlockEntity(lower.above()) == null,
                "only lower shelf owns a BlockEntity");
        helper.assertTrue(shelf.getContainerSize() == RetailShelfSingleBlockEntity.SIZE
                        && shelf.aflSearchLayout() == AflContainerSearchLayout.GRID_3X3,
                "searchable container of 9 slots in the 3 x 3 menu");
        helper.assertTrue(AflContainerSearch.isComplete(shelf), "a player-placed shelf is never searched");

        assertLayoutAndShapes(helper, lower);
        assertWorldRayTrace(helper, helper.absolutePos(new BlockPos(11, 2, 7)));
        reset(helper, lower);
        helper.assertTrue(place(helper, lower), "shelf replaces for the menu test");
        assertUpperUse(helper, lower, (RetailShelfSingleBlockEntity) helper.getLevel().getBlockEntity(lower));
        assertWorldLoot(helper, lower);

        reset(helper, lower);
        helper.assertTrue(place(helper, lower), "shelf replaces after reset");
        var dropShelf = (RetailShelfSingleBlockEntity) helper.getLevel().getBlockEntity(lower);
        dropShelf.setItem(0, new ItemStack(Items.PAPER));
        dropShelf.setItem(4, new ItemStack(Items.APPLE));
        dropShelf.setItem(8, new ItemStack(Items.BREAD));
        helper.assertTrue(destroy(helper, lower, AflItems.RETAIL_SHELF_SINGLE.get()) == 1,
                "lower break drops one shelf");
        assertCleared(helper, lower);
        helper.assertTrue(countDrops(helper, lower, Items.PAPER) == 1
                        && countDrops(helper, lower, Items.APPLE) == 1
                        && countDrops(helper, lower, Items.BREAD) == 1,
                "lower break drops each stored item once");

        reset(helper, lower);
        helper.assertTrue(place(helper, lower), "shelf places for upper break");
        dropShelf = (RetailShelfSingleBlockEntity) helper.getLevel().getBlockEntity(lower);
        dropShelf.setItem(0, new ItemStack(Items.PAPER));
        dropShelf.setItem(4, new ItemStack(Items.APPLE));
        dropShelf.setItem(8, new ItemStack(Items.BREAD));
        helper.assertTrue(destroy(helper, lower.above(), AflItems.RETAIL_SHELF_SINGLE.get()) == 1,
                "upper break drops one shelf");
        assertCleared(helper, lower);
        helper.assertTrue(countDrops(helper, lower, Items.PAPER) == 1
                        && countDrops(helper, lower, Items.APPLE) == 1
                        && countDrops(helper, lower, Items.BREAD) == 1,
                "upper break drops each stored item once");

        ApocalypseFirstLight.LOGGER.info("[AFL RETAIL SHELF TEST] PASS 9-slot searchable container, four-facing real ray hits "
                + "on the 15 deck cells, five collision decks, upper-half menu, world loot rolled on the first tick with goods, "
                + "single lower/upper drops");
        helper.succeed();
    }

    private static void assertLayoutAndShapes(GameTestHelper helper, BlockPos lower) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState base = AflBlocks.RETAIL_SHELF_SINGLE.get().defaultBlockState()
                    .setValue(RetailShelfSingleBlock.FACING, facing);
            for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                var shape = base.setValue(RetailShelfSingleBlock.HALF, half)
                        .getCollisionShape(helper.getLevel(), half == DoubleBlockHalf.LOWER ? lower : lower.above());
                helper.assertTrue(!shape.isEmpty(), facing + " " + half + " shape exists");
                for (AABB box : shape.toAabbs()) {
                    helper.assertTrue(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0
                                    && box.maxX <= 1 && box.maxY <= 1 && box.maxZ <= 1,
                            facing + " " + half + " shape stays in its block");
                }
            }
            for (int cell = 0; cell < RetailShelfLayout.CELLS; cell++) {
                int row = cell / RetailShelfLayout.COLUMNS;
                int column = cell % RetailShelfLayout.COLUMNS;
                double x = RetailShelfLayout.columnX(column);
                double y = RetailShelfLayout.rowY(row);
                BlockPos hitPos = y >= 1.0D ? lower.above() : lower;
                // level aim through the cell onto the back panel
                var panelHit = new BlockHitResult(world(lower, facing, x, y, 15.424D / 16.0D), facing, hitPos, false);
                helper.assertTrue(RetailShelfSingleBlock.getClickedCell(world(lower, facing, x, y, -0.5D), facing, lower,
                        panelHit) == cell, facing + " level aim selects cell " + cell);
                double deckTop = RetailShelfLayout.deckTopUnits(row) / 16.0D;
                double lipY = deckTop + 0.003D;
                var lipHit = new BlockHitResult(world(lower, facing, x, lipY, RetailShelfLayout.FRONT_Z + 0.012D),
                        facing, hitPos, false);
                helper.assertTrue(RetailShelfSingleBlock.getClickedCell(world(lower, facing, x, lipY, -0.5D),
                        facing, lower, lipHit) == cell, facing + " front lip maps to cell " + cell);
                // looking down onto the deck top, front or back half
                Vec3 above = world(lower, facing, x, deckTop + 0.3D, 0.0D);
                for (int depth = 0; depth < RetailShelfLayout.DEPTHS; depth++) {
                    var deckHit = new BlockHitResult(world(lower, facing, x, deckTop, RetailShelfLayout.depthZ(depth)),
                            Direction.UP, hitPos, false);
                    helper.assertTrue(RetailShelfSingleBlock.getClickedCell(above, facing, lower, deckHit) == cell,
                            facing + " deck-top landing selects cell " + cell + " (" + depth + ")");
                }
            }
        }
    }

    private static Vec3 world(BlockPos origin, Direction facing, double x, double y, double z) {
        return switch (facing) {
            case NORTH -> new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            case SOUTH -> new Vec3(origin.getX() + 1.0D - x, origin.getY() + y,
                    origin.getZ() + 1.0D - z);
            case EAST -> new Vec3(origin.getX() + 1.0D - z, origin.getY() + y, origin.getZ() + x);
            case WEST -> new Vec3(origin.getX() + z, origin.getY() + y, origin.getZ() + 1.0D - x);
            default -> throw new IllegalArgumentException("not a horizontal facing");
        };
    }

    private static void assertWorldRayTrace(GameTestHelper helper, BlockPos lower) {
        var level = helper.getLevel();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            reset(helper, lower);
            BlockState state = AflBlocks.RETAIL_SHELF_SINGLE.get().defaultBlockState()
                    .setValue(RetailShelfSingleBlock.FACING, facing);
            level.setBlock(lower, state.setValue(RetailShelfSingleBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
            level.setBlock(lower.above(), state.setValue(RetailShelfSingleBlock.HALF, DoubleBlockHalf.UPPER),
                    Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(lower).is(AflBlocks.RETAIL_SHELF_SINGLE.get())
                            && level.getBlockState(lower.above()).is(AflBlocks.RETAIL_SHELF_SINGLE.get()),
                    facing + " ray-test shelf pair remains placed");
            for (int cell = 0; cell < RetailShelfLayout.CELLS; cell++) {
                double x = RetailShelfLayout.columnX(cell % RetailShelfLayout.COLUMNS);
                double y = RetailShelfLayout.rowY(cell / RetailShelfLayout.COLUMNS);
                Vec3 eye = world(lower, facing, x, 1.62D, -1.5D);
                Vec3 itemCenter = world(lower, facing, x, y, RetailShelfLayout.depthZ(RetailShelfLayout.FRONT));
                Vec3 end = itemCenter.add(itemCenter.subtract(eye).scale(0.5D));
                BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, null));
                helper.assertTrue(hit.getType() == HitResult.Type.BLOCK
                                && (hit.getBlockPos().equals(lower) || hit.getBlockPos().equals(lower.above())),
                        facing + " real ray reaches shelf for cell " + cell + ", hit " + hit.getBlockPos());
                helper.assertTrue(RetailShelfSingleBlock.getClickedCell(eye, facing, lower, hit) == cell,
                        facing + " real collision hit selects cell " + cell);
            }
        }
    }

    /** A click on the upper half forwards to the lower shelf and opens its menu (no items move by hand any more). */
    private static void assertUpperUse(GameTestHelper helper, BlockPos lower, RetailShelfSingleBlockEntity shelf) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "shelf_upper_use"));
        player.setGameMode(GameType.SURVIVAL);
        double y = RetailShelfLayout.rowY(4);
        player.setPos(lower.getX() + 0.5D, lower.getY() + y - player.getEyeHeight(), lower.getZ() - 2.0D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 2));
        var hit = new BlockHitResult(new Vec3(lower.getX() + 0.5D, lower.getY() + y,
                lower.getZ() + 15.424D / 16.0D), Direction.NORTH, lower.above(), false);
        BlockState upper = helper.getLevel().getBlockState(lower.above());
        var result = AflBlocks.RETAIL_SHELF_SINGLE.get().use(upper, helper.getLevel(), lower.above(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result.consumesAction() && player.containerMenu != player.inventoryMenu
                        && player.getMainHandItem().getCount() == 2 && shelf.isEmpty(),
                "upper-half click opens the shelf menu and moves nothing");
        player.closeContainer();
    }

    /** World loot: rolled on the first server tick, searched, and the goods count follows the occupied slots. */
    private static void assertWorldLoot(GameTestHelper helper, BlockPos lower) {
        reset(helper, lower);
        var level = helper.getLevel();
        BlockState state = AflBlocks.RETAIL_SHELF_SINGLE.get().defaultBlockState();
        level.setBlock(lower, state.setValue(RetailShelfSingleBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        level.setBlock(lower.above(), state.setValue(RetailShelfSingleBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        var shelf = (RetailShelfSingleBlockEntity) level.getBlockEntity(lower);
        shelf.setLootTable(new ResourceLocation("chests/simple_dungeon"), 42L);
        helper.assertTrue(shelf.getUpdateTag().getInt("Goods") == 0, "nothing shows before the loot is rolled");
        shelf.serverTick();
        int occupied = AflContainerGoods.occupied(List.of(shelf.getItem(0), shelf.getItem(1), shelf.getItem(2), shelf.getItem(3),
                shelf.getItem(4), shelf.getItem(5), shelf.getItem(6), shelf.getItem(7), shelf.getItem(8)));
        helper.assertTrue(!AflContainerSearch.isComplete(shelf), "world loot is searched");
        helper.assertTrue(shelf.getUpdateTag().getInt("Goods")
                        == AflContainerGoods.shown(occupied, RetailShelfLayout.CELLS, RetailShelfSingleBlockEntity.GOODS_FULL_AT),
                "goods follow the rolled loot (" + occupied + " slots)");
    }

    private static boolean place(GameTestHelper helper, BlockPos lower) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "shelf_place"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(lower.getX(), lower.getY(), lower.getZ() + 3.0D);
        var item = AflItems.RETAIL_SHELF_SINGLE.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(lower), Direction.UP, lower.below(), false));
        return ((BlockItem) item).place(context).consumesAction();
    }

    private static int destroy(GameTestHelper helper, BlockPos position, Item expected) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).forEach(ItemEntity::discard);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "shelf_break"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(player.gameMode.destroyBlock(position), "shelf breaks with iron pickaxe");
        return countDrops(helper, position, expected);
    }

    private static int countDrops(GameTestHelper helper, BlockPos position, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void assertCleared(GameTestHelper helper, BlockPos lower) {
        helper.assertTrue(helper.getLevel().getBlockState(lower).isAir()
                        && helper.getLevel().getBlockState(lower.above()).isAir(),
                "breaking either half clears both positions");
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
