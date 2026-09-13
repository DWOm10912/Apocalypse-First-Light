package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.OfficeCubiclePartitionBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestroomPartitionGameTests {
    private static final String NAMESPACE = ApocalypseFirstLight.MOD_ID;
    private static final String TEMPLATE = "retail_shelf_empty";

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("restroom_partition", NAMESPACE + ":restroom_partition_connections_shapes_drops",
                NAMESPACE + ":" + TEMPLATE, 240, 0L, true, RestroomPartitionGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        var restroom = AflBlocks.RESTROOM_PARTITION.get();
        var center = helper.absolutePos(new BlockPos(7, 2, 7));
        reset(helper, center);
        place(helper, center, false);
        check(helper, center, 0, false, "single");
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            reset(helper, center);
            place(helper, center, false);
            place(helper, center.relative(direction), false);
            check(helper, center, bit(direction), false, "end " + direction);
            check(helper, center.relative(direction), bit(direction.getOpposite()), false, "neighbor " + direction);
        }
        reset(helper, center);
        place(helper, center, false);
        place(helper, center.east(), false);
        place(helper, center.west(), false);
        check(helper, center, 12, false, "straight");
        helper.assertTrue(destroyByHand(helper, center, false) == 1, "empty-hand break drops exactly one restroom item");
        check(helper, center.west(), 0, false, "west after removal");
        check(helper, center.east(), 0, false, "east after removal");

        int[][] layouts = {{1, 4}, {1, 4, 8}, {1, 2, 4, 8}};
        String[] labels = {"corner", "T", "cross"};
        for (int i = 0; i < layouts.length; i++) {
            reset(helper, center);
            place(helper, center, false);
            int mask = 0;
            for (int connection : layouts[i]) {
                Direction direction = direction(connection);
                place(helper, center.relative(direction), false);
                mask |= connection;
            }
            check(helper, center, mask, false, labels[i]);
        }

        for (int mask = 0; mask < 16; mask++) {
            var state = restroom.defaultBlockState()
                    .setValue(OfficeCubiclePartitionBlock.NORTH, (mask & 1) != 0)
                    .setValue(OfficeCubiclePartitionBlock.SOUTH, (mask & 2) != 0)
                    .setValue(OfficeCubiclePartitionBlock.EAST, (mask & 4) != 0)
                    .setValue(OfficeCubiclePartitionBlock.WEST, (mask & 8) != 0);
            var shape = state.getCollisionShape(level, center);
            helper.assertTrue(!shape.isEmpty() && Math.abs(shape.bounds().maxY - 2.0D) < 1.0E-6D,
                    "two-block shape for mask " + mask);
            double volume = shape.toAabbs().stream()
                    .mapToDouble(box -> box.getXsize() * box.getYsize() * box.getZsize()).sum();
            helper.assertTrue(volume < 0.55D, "thin dynamic shape for mask " + mask);
            var restored = NbtUtils.readBlockState(
                    level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), NbtUtils.writeBlockState(state));
            helper.assertTrue(restored.equals(state), "persist mask " + mask);
        }
        helper.assertTrue(!(restroom instanceof EntityBlock), "no block entity");
        helper.assertTrue(!restroom.defaultBlockState().isRandomlyTicking(), "no random tick");

        reset(helper, center);
        place(helper, center, false);
        place(helper, center.east(), true);
        check(helper, center, 0, false, "mixed restroom");
        check(helper, center.east(), 0, true, "mixed office");
        helper.assertTrue(destroyByHand(helper, center.east(), true) == 1, "office still drops by hand");
        check(helper, center, 0, false, "restroom after office removal");

        reset(helper, center);
        level.setBlock(center.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!restroom.defaultBlockState().canSurvive(level, center), "occupied upper space rejected");
        helper.assertTrue(!tryPlace(helper, center, false), "cannot place with occupied upper space");

        ApocalypseFirstLight.LOGGER.info("[AFL RESTROOM PARTITION TEST] PASS 4 directions, all junctions, "
                + "16 shapes, mixed office isolation, neighbor rebuild, hand drops");
        helper.succeed();
    }

    private static Direction direction(int bit) {
        return switch (bit) {
            case 1 -> Direction.NORTH;
            case 2 -> Direction.SOUTH;
            case 4 -> Direction.EAST;
            case 8 -> Direction.WEST;
            default -> throw new IllegalArgumentException("bit " + bit);
        };
    }

    private static int bit(Direction direction) {
        return switch (direction) {
            case NORTH -> 1;
            case SOUTH -> 2;
            case EAST -> 4;
            case WEST -> 8;
            default -> throw new IllegalArgumentException("direction " + direction);
        };
    }

    private static void reset(GameTestHelper helper, BlockPos center) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(8)).forEach(ItemEntity::discard);
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-4, 0, -4), center.offset(4, 2, 4)))
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, -1, 4)))
            level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
    }

    private static void place(GameTestHelper helper, BlockPos position, boolean office) {
        helper.assertTrue(tryPlace(helper, position, office), "place " + (office ? "office" : "restroom") + " at " + position);
    }

    private static boolean tryPlace(GameTestHelper helper, BlockPos position, boolean office) {
        var level = helper.getLevel();
        var item = office ? AflItems.OFFICE_CUBICLE_PARTITION.get() : AflItems.RESTROOM_PARTITION.get();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "partition_place"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(position.getX() + 2.0D, position.getY(), position.getZ() + 2.0D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(position), Direction.UP, position.below(), false));
        return ((BlockItem) item).place(context).consumesAction();
    }

    private static int destroyByHand(GameTestHelper helper, BlockPos position, boolean office) {
        var level = helper.getLevel();
        var item = office ? AflItems.OFFICE_CUBICLE_PARTITION.get() : AflItems.RESTROOM_PARTITION.get();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).forEach(ItemEntity::discard);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "partition_break"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.AIR));
        helper.assertTrue(player.gameMode.destroyBlock(position), "destroy partition by hand");
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).stream()
                .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void check(GameTestHelper helper, BlockPos position, int mask, boolean office, String label) {
        var state = helper.getLevel().getBlockState(position);
        helper.assertTrue(state.is(office ? AflBlocks.OFFICE_CUBICLE_PARTITION.get() : AflBlocks.RESTROOM_PARTITION.get()),
                label + " block exists");
        helper.assertTrue(OfficeCubiclePartitionBlock.connectionMask(state) == mask, label + " mask " + mask);
    }
}
