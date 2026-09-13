package com.antaurora.apofirstlight.dev;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CommercialWallMountedSinkBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class CommercialWallMountedSinkGameTests {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) return;
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
        level.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation(
                        "afl_sink_tests", "sink_empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("wall_sink", "afl_sink_tests:static_dry_sink",
                "afl_sink_tests:sink_empty", 300, 0L, true,
                CommercialWallMountedSinkGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(7, 2, 7));
        var level = helper.getLevel();
        Block sink = AflBlocks.COMMERCIAL_WALL_MOUNTED_SINK.get();
        helper.assertTrue(!(sink instanceof EntityBlock), "sink must stay static");

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            clear(helper, pos);
            helper.assertTrue(place(helper, pos, facing), "place without wall " + facing);
            BlockState state = level.getBlockState(pos);
            helper.assertTrue(state.is(sink) && state.getValue(CommercialWallMountedSinkBlock.FACING) == facing,
                    "facing " + facing);
            BlockState upper = level.getBlockState(pos.above());
            helper.assertTrue(state.getValue(CommercialWallMountedSinkBlock.HALF) == DoubleBlockHalf.LOWER
                    && upper.is(sink) && upper.getValue(CommercialWallMountedSinkBlock.HALF) == DoubleBlockHalf.UPPER
                    && upper.getValue(CommercialWallMountedSinkBlock.FACING) == facing, "two aligned halves " + facing);
            var shape = state.getCollisionShape(level, pos);
            var upperShape = upper.getCollisionShape(level, pos.above());
            helper.assertTrue(!shape.isEmpty() && !Shapes.block().equals(shape), "custom shape " + facing);
            helper.assertTrue(shape.bounds().minY > 0 && shape.bounds().maxY <= 1.0
                    && !upperShape.isEmpty() && upperShape.bounds().maxY < .2,
                    "split height and small upper footprint " + facing);
            // Rotate local probes in lockstep with the model's blockstate variant.
            helper.assertTrue(!contains(shape, rotate(.5, .9125, .62, facing)), "open basin " + facing);
            helper.assertTrue(!contains(shape, rotate(.38, .5125, .58, facing)), "under-sink air " + facing);
            helper.assertTrue(contains(shape, rotate(.5, .5125, .68, facing)), "P-trap " + facing);
            helper.assertTrue(contains(shape, rotate(.1, .8925, .62, facing)), "ceramic rim " + facing);
            helper.assertTrue(contains(upperShape, rotate(.5, .1, .75, facing)), "upper faucet " + facing);
            helper.assertTrue(!contains(upperShape, rotate(.25, .1, .62, facing)), "upper air " + facing);

            // Rear wall is visual only: placing/removing it does not destroy the sink.
            BlockPos behind = pos.relative(facing.getOpposite());
            level.setBlock(behind, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(pos).is(sink), "wall added " + facing);
            level.removeBlock(behind, false);
            helper.assertTrue(level.getBlockState(pos).is(sink), "wall removed " + facing);
        }

        clear(helper, pos);
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(!place(helper, pos, Direction.NORTH) && level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).is(Blocks.STONE), "occupied upper cell rejects placement");

        for (Item tool : List.of(Items.AIR, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE,
                Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
            for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                clear(helper, pos);
                helper.assertTrue(place(helper, pos, Direction.NORTH), "place before break " + tool + half);
                var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "sink_mining"));
                player.setGameMode(GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
                boolean shouldDrop = tool == Items.IRON_PICKAXE || tool == Items.DIAMOND_PICKAXE
                        || tool == Items.NETHERITE_PICKAXE;
                BlockPos target = half == DoubleBlockHalf.UPPER ? pos.above() : pos;
                helper.assertTrue(player.getMainHandItem().isCorrectToolForDrops(level.getBlockState(target)) == shouldDrop,
                        "correct tool predicate " + tool + half);
                helper.assertTrue(player.gameMode.destroyBlock(target), "destroy " + tool + half);
                helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(),
                        "both halves cleared " + tool + half);
                int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
                        .filter(entity -> entity.getItem().is(AflItems.COMMERCIAL_WALL_MOUNTED_SINK.get()))
                        .mapToInt(entity -> entity.getItem().getCount()).sum();
                helper.assertTrue(drops == (shouldDrop ? 1 : 0), "drop count " + tool + half + " = " + drops);
            }
        }
        ApocalypseFirstLight.LOGGER.info("[AFL WALL SINK TEST] PASS four facings, two-cell placement/shape, wall independence, blocked upper placement, twelve survival break cases");
        helper.succeed();
    }

    private static Vec3 rotate(double x, double y, double z, Direction facing) {
        int turns = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        for (int i = 0; i < turns; i++) { double oldX = x; x = 1 - z; z = oldX; }
        return new Vec3(x, y, z);
    }

    private static boolean contains(net.minecraft.world.phys.shapes.VoxelShape shape, Vec3 point) {
        return shape.toAabbs().stream().anyMatch(box -> box.contains(point));
    }

    private static boolean place(GameTestHelper helper, BlockPos pos, Direction facing) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "sink_place"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        player.setYRot(facing.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.COMMERCIAL_WALL_MOUNTED_SINK.get()));
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false));
        return ((BlockItem) AflItems.COMMERCIAL_WALL_MOUNTED_SINK.get()).place(context).consumesAction();
    }

    private static void clear(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4)).forEach(ItemEntity::discard);
        for (BlockPos target : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 2, 2)))
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos target : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, -1, 2)))
            level.setBlock(target, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    }
}
