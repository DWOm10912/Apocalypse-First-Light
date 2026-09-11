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
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
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

@GameTestHolder("afl_office_partition_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class OfficeCubiclePartitionGameTests {
    private static final String NAMESPACE = "afl_office_partition_tests";

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
        return List.of(new TestFunction("office_partition", NAMESPACE + ":connections_shapes_drops",
                NAMESPACE + ":empty", 240, 0L, true, OfficeCubiclePartitionGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = AflBlocks.OFFICE_CUBICLE_PARTITION.get();
        var center = helper.absolutePos(new BlockPos(7, 2, 7));

        reset(helper, center);
        place(helper, center);
        assertConnections(helper, center, false, false, false, false, "single");

        reset(helper, center);
        place(helper, center);
        place(helper, center.east());
        assertConnections(helper, center, false, false, true, false, "end west block");
        assertConnections(helper, center.east(), false, false, false, true, "end east block");

        reset(helper, center);
        place(helper, center.west());
        place(helper, center);
        place(helper, center.east());
        assertConnections(helper, center, false, false, true, true, "straight middle");
        helper.assertTrue(destroyByHand(helper, center) == 1, "middle block drops exactly one item by hand");
        helper.assertTrue(level.getBlockState(center).isAir(), "destroyed middle is removed");
        helper.assertTrue(level.getBlockState(center.west()).is(block), "west block remains independent");
        helper.assertTrue(level.getBlockState(center.east()).is(block), "east block remains independent");
        assertConnections(helper, center.west(), false, false, false, false, "west reconnect after break");
        assertConnections(helper, center.east(), false, false, false, false, "east reconnect after break");

        reset(helper, center);
        place(helper, center);
        place(helper, center.north());
        place(helper, center.east());
        assertConnections(helper, center, true, false, true, false, "corner");

        reset(helper, center);
        place(helper, center);
        place(helper, center.north());
        place(helper, center.east());
        place(helper, center.west());
        assertConnections(helper, center, true, false, true, true, "T junction");

        reset(helper, center);
        place(helper, center);
        place(helper, center.north());
        place(helper, center.south());
        place(helper, center.east());
        place(helper, center.west());
        assertConnections(helper, center, true, true, true, true, "cross");

        var defaultState = block.defaultBlockState();
        helper.assertTrue(!(block instanceof EntityBlock), "partition has no BlockEntity contract");
        helper.assertTrue(!defaultState.isRandomlyTicking(), "partition has no random ticking");
        for (int mask = 0; mask < 16; mask++) {
            var state = stateForMask(block.defaultBlockState(), mask);
            var shape = state.getCollisionShape(level, center);
            helper.assertTrue(!shape.isEmpty(), "shape exists for mask " + mask);
            helper.assertTrue(Math.abs(shape.bounds().maxY - 2.0D) < 1.0E-6D, "two-block height for mask " + mask);
            double volume = shape.toAabbs().stream()
                    .mapToDouble(box -> box.getXsize() * box.getYsize() * box.getZsize()).sum();
            helper.assertTrue(volume < 0.55D, "thin dynamic shape for mask " + mask);
            var restored = NbtUtils.readBlockState(
                    level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), NbtUtils.writeBlockState(state));
            helper.assertTrue(restored.equals(state), "state persistence for mask " + mask);
        }

        var eastWest = defaultState.setValue(OfficeCubiclePartitionBlock.EAST, true)
                .setValue(OfficeCubiclePartitionBlock.WEST, true).getCollisionShape(level, center);
        var northSouth = defaultState.setValue(OfficeCubiclePartitionBlock.NORTH, true)
                .setValue(OfficeCubiclePartitionBlock.SOUTH, true).getCollisionShape(level, center);
        var corner = defaultState.setValue(OfficeCubiclePartitionBlock.NORTH, true)
                .setValue(OfficeCubiclePartitionBlock.EAST, true).getCollisionShape(level, center);
        helper.assertTrue(Shapes.joinIsNotEmpty(eastWest, northSouth, BooleanOp.NOT_SAME), "straight shape rotates by axis");
        helper.assertTrue(Shapes.joinIsNotEmpty(eastWest, corner, BooleanOp.NOT_SAME), "corner shape is L-shaped");

        var northOnly = defaultState.setValue(OfficeCubiclePartitionBlock.NORTH, true);
        var rotated = northOnly.rotate(Rotation.CLOCKWISE_90);
        helper.assertTrue(rotated.getValue(OfficeCubiclePartitionBlock.EAST)
                && !rotated.getValue(OfficeCubiclePartitionBlock.NORTH), "state rotation preserves connections");

        reset(helper, center);
        level.setBlock(center.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!defaultState.canSurvive(level, center), "solid upper block rejects two-block-tall partition");
        helper.assertTrue(!tryPlace(helper, center), "placement fails when upper space is occupied");

        reset(helper, center);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            place(helper, center.offset(x, 0, z));
        }
        helper.assertTrue(level.getBlockState(center).getValue(OfficeCubiclePartitionBlock.NORTH)
                && level.getBlockState(center).getValue(OfficeCubiclePartitionBlock.SOUTH)
                && level.getBlockState(center).getValue(OfficeCubiclePartitionBlock.EAST)
                && level.getBlockState(center).getValue(OfficeCubiclePartitionBlock.WEST),
                "bulk placement converges to cross state without polling");

        ApocalypseFirstLight.LOGGER.info("[AFL OFFICE PARTITION TEST] PASS single/end/straight/corner/T/cross, "
                + "placement and break updates, 16 cached shapes, hand drop, upper-space guard, 49-block batch");
        helper.succeed();
    }

    private static void reset(GameTestHelper helper, BlockPos center) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(8)).forEach(ItemEntity::discard);
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-4, 0, -4), center.offset(4, 2, 4))) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        }
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, -1, 4))) {
            level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
        }
    }

    private static void place(GameTestHelper helper, BlockPos position) {
        helper.assertTrue(tryPlace(helper, position), "place partition at " + position);
    }

    private static boolean tryPlace(GameTestHelper helper, BlockPos position) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "partition_place"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(position.getX() + 2.0D, position.getY(), position.getZ() + 2.0D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.OFFICE_CUBICLE_PARTITION.get()));
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(position), Direction.UP, position.below(), false));
        return ((BlockItem) AflItems.OFFICE_CUBICLE_PARTITION.get()).place(context).consumesAction();
    }

    private static int destroyByHand(GameTestHelper helper, BlockPos position) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).forEach(ItemEntity::discard);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "partition_break"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.AIR));
        helper.assertTrue(player.gameMode.destroyBlock(position), "empty hand destroys one partition");
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(4)).stream()
                .filter(entity -> entity.getItem().is(AflItems.OFFICE_CUBICLE_PARTITION.get()))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void assertConnections(GameTestHelper helper, BlockPos position, boolean north,
                                          boolean south, boolean east, boolean west, String label) {
        var state = helper.getLevel().getBlockState(position);
        helper.assertTrue(state.is(AflBlocks.OFFICE_CUBICLE_PARTITION.get()), label + " block exists");
        helper.assertTrue(state.getValue(OfficeCubiclePartitionBlock.NORTH) == north, label + " north");
        helper.assertTrue(state.getValue(OfficeCubiclePartitionBlock.SOUTH) == south, label + " south");
        helper.assertTrue(state.getValue(OfficeCubiclePartitionBlock.EAST) == east, label + " east");
        helper.assertTrue(state.getValue(OfficeCubiclePartitionBlock.WEST) == west, label + " west");
    }

    private static net.minecraft.world.level.block.state.BlockState stateForMask(
            net.minecraft.world.level.block.state.BlockState state, int mask) {
        return state
                .setValue(OfficeCubiclePartitionBlock.NORTH, (mask & 1) != 0)
                .setValue(OfficeCubiclePartitionBlock.SOUTH, (mask & 2) != 0)
                .setValue(OfficeCubiclePartitionBlock.EAST, (mask & 4) != 0)
                .setValue(OfficeCubiclePartitionBlock.WEST, (mask & 8) != 0);
    }
}
