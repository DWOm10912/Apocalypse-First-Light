package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RetailShelfLayout;
import com.antaurora.apofirstlight.block.RetailShelfSingleBlock;
import com.antaurora.apofirstlight.blockentity.RetailShelfSingleBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ContainerHelper;
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
        helper.assertTrue(shelf.getContainerSize() == 15 && shelf.getMaxStackSize() == 1,
                "five rows by three columns and one item per display slot");

        assertLayoutAndShapes(helper, lower);
        assertWorldRayTrace(helper, helper.absolutePos(new BlockPos(11, 2, 7)));
        assertLegacySave(helper, shelf);
        assertOneItemSemantics(helper, shelf);
        assertUpperUse(helper, lower, shelf);

        reset(helper, lower);
        helper.assertTrue(place(helper, lower), "shelf replaces after reset");
        var dropShelf = (RetailShelfSingleBlockEntity) helper.getLevel().getBlockEntity(lower);
        dropShelf.setItem(0, new ItemStack(Items.PAPER));
        dropShelf.setItem(14, new ItemStack(Items.APPLE));
        helper.assertTrue(destroy(helper, lower, AflItems.RETAIL_SHELF_SINGLE.get()) == 1,
                "lower break drops one shelf");
        assertCleared(helper, lower);
        helper.assertTrue(countDrops(helper, lower, Items.PAPER) == 1
                        && countDrops(helper, lower, Items.APPLE) == 1,
                "lower break drops each displayed item once");

        reset(helper, lower);
        helper.assertTrue(place(helper, lower), "shelf places for upper break");
        dropShelf = (RetailShelfSingleBlockEntity) helper.getLevel().getBlockEntity(lower);
        dropShelf.setItem(0, new ItemStack(Items.PAPER));
        dropShelf.setItem(14, new ItemStack(Items.APPLE));
        helper.assertTrue(destroy(helper, lower.above(), AflItems.RETAIL_SHELF_SINGLE.get()) == 1,
                "upper break drops one shelf");
        assertCleared(helper, lower);
        helper.assertTrue(countDrops(helper, lower, Items.PAPER) == 1
                        && countDrops(helper, lower, Items.APPLE) == 1,
                "upper break drops each displayed item once");

        ApocalypseFirstLight.LOGGER.info("[AFL RETAIL SHELF TEST] PASS 15 slots, four-facing real ray hits, "
                + "five collision decks, old 12-slot NBT, stack-one, upper forwarding, single lower/upper drops");
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
            for (int slot = 0; slot < RetailShelfLayout.SLOTS; slot++) {
                int row = slot / RetailShelfLayout.COLUMNS;
                int column = slot % RetailShelfLayout.COLUMNS;
                double x = RetailShelfLayout.columnX(column);
                double y = RetailShelfLayout.rowY(row);
                Vec3 eye = world(lower, facing, x, y, -0.5D);
                Vec3 hitLocation = world(lower, facing, x, y, RetailShelfLayout.DISPLAY_Z);
                BlockPos hitPos = y >= 1.0D ? lower.above() : lower;
                var hit = new BlockHitResult(hitLocation, facing, hitPos, false);
                helper.assertTrue(RetailShelfSingleBlock.getClickedSlot(eye, facing, lower, hit) == slot,
                        facing + " selects exact display slot " + slot);
                double lipY = RetailShelfLayout.deckTopUnits(row) / 16.0D + 0.003D;
                var lipHit = new BlockHitResult(world(lower, facing, x, lipY, RetailShelfLayout.FRONT_Z + 0.012D),
                        facing, hitPos, false);
                helper.assertTrue(RetailShelfSingleBlock.getClickedSlot(world(lower, facing, x, lipY, -0.5D),
                        facing, lower, lipHit) == slot, facing + " front lip maps to slot " + slot);
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
            for (int slot = 0; slot < RetailShelfLayout.SLOTS; slot++) {
                double x = RetailShelfLayout.columnX(slot % RetailShelfLayout.COLUMNS);
                double y = RetailShelfLayout.rowY(slot / RetailShelfLayout.COLUMNS);
                Vec3 eye = world(lower, facing, x, 1.62D, -1.5D);
                Vec3 itemCenter = world(lower, facing, x, y, RetailShelfLayout.DISPLAY_Z);
                Vec3 end = itemCenter.add(itemCenter.subtract(eye).scale(0.5D));
                BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, null));
                helper.assertTrue(hit.getType() == HitResult.Type.BLOCK
                                && (hit.getBlockPos().equals(lower) || hit.getBlockPos().equals(lower.above())),
                        facing + " real ray reaches shelf for slot " + slot + ", hit " + hit.getBlockPos());
                helper.assertTrue(RetailShelfSingleBlock.getClickedSlot(eye, facing, lower, hit) == slot,
                        facing + " real collision hit selects slot " + slot);
            }
        }
    }

    private static void assertLegacySave(GameTestHelper helper, RetailShelfSingleBlockEntity shelf) {
        Item[] legacyItems = {Items.PAPER, Items.APPLE, Items.BREAD, Items.CARROT, Items.POTATO,
                Items.BOOK, Items.STRING, Items.STICK, Items.IRON_INGOT, Items.GLASS,
                Items.COAL, Items.REDSTONE};
        NonNullList<ItemStack> old = NonNullList.withSize(12, ItemStack.EMPTY);
        for (int slot = 0; slot < old.size(); slot++) old.set(slot, new ItemStack(legacyItems[slot]));
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, old);
        shelf.load(tag);
        for (int slot = 0; slot < old.size(); slot++) {
            helper.assertTrue(shelf.getItem(slot).is(legacyItems[slot]) && shelf.getItem(slot).getCount() == 1,
                    "legacy slot " + slot + " keeps its item and order");
        }
        for (int slot = 12; slot < 15; slot++) helper.assertTrue(shelf.isEmpty(slot), "new slot starts empty");

        old.set(0, new ItemStack(Items.PAPER, 4));
        tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, old);
        shelf.load(tag);
        helper.assertTrue(shelf.getItem(0).getCount() == 1, "legacy overstack becomes one visible item");
        CompoundTag persisted = shelf.saveWithoutMetadata();
        shelf.load(persisted);
        for (int remaining = 3; remaining >= 0; remaining--) {
            helper.assertTrue(shelf.removeOne(0).getCount() == 1, "legacy item remains retrievable");
            helper.assertTrue(shelf.getItem(0).getCount() == (remaining > 0 ? 1 : 0),
                    "legacy reserve refills only one display item");
        }
    }

    private static void assertOneItemSemantics(GameTestHelper helper, RetailShelfSingleBlockEntity shelf) {
        ItemStack large = new ItemStack(Items.DIAMOND, 64);
        shelf.setItem(14, large);
        helper.assertTrue(shelf.getItem(14).getCount() == 1 && large.getCount() == 64,
                "setItem clamps without mutating the caller's stack");
        helper.assertTrue(!shelf.canPlaceItem(14, large), "occupied display slot rejects hopper insertion");
        helper.assertTrue(shelf.canPlaceItem(13, large), "empty display slot accepts insertion");
        helper.assertTrue(shelf.removeItem(14, 64).getCount() == 1 && shelf.isEmpty(14),
                "container extraction cannot take more than one");
    }

    private static void assertUpperUse(GameTestHelper helper, BlockPos lower, RetailShelfSingleBlockEntity shelf) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "shelf_upper_use"));
        player.setGameMode(GameType.SURVIVAL);
        double y = RetailShelfLayout.rowY(4);
        player.setPos(lower.getX() + 0.5D, lower.getY() + y - player.getEyeHeight(), lower.getZ() - 2.0D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 2));
        var hit = new BlockHitResult(new Vec3(lower.getX() + 0.5D, lower.getY() + y,
                lower.getZ() + RetailShelfLayout.DISPLAY_Z), Direction.NORTH, lower.above(), false);
        var result = AflBlocks.RETAIL_SHELF_SINGLE.get().use(helper.getLevel().getBlockState(lower.above()),
                helper.getLevel(), lower.above(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result.consumesAction() && shelf.getItem(13).is(Items.EMERALD)
                        && shelf.getItem(13).getCount() == 1 && player.getMainHandItem().getCount() == 1,
                "upper-half click forwards to lower and inserts exactly one item");
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
