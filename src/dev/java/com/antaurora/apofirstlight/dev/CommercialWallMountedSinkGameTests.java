package com.antaurora.apofirstlight.dev;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CommercialWallMountedSinkBlock;
import com.antaurora.apofirstlight.block.WallMirrorBlock;
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

/**
 * Restroom Fixtures V2 (docs/models/restroom_fixtures_v2.md): the one-cell lavatory, the cleanup of V1's obsolete upper
 * halves, and the wall mirror above it. Probe points follow tools/build-restroom-fixtures-v2.mjs SHAPES.
 */
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
            helper.assertTrue(placeSink(helper, pos, facing), "place without wall " + facing);
            BlockState state = level.getBlockState(pos);
            helper.assertTrue(state.is(sink) && state.getValue(CommercialWallMountedSinkBlock.FACING) == facing
                    && state.getValue(CommercialWallMountedSinkBlock.HALF) == DoubleBlockHalf.LOWER, "facing " + facing);
            helper.assertTrue(level.getBlockState(pos.above()).isAir(), "one cell " + facing);
            var shape = state.getCollisionShape(level, pos);
            helper.assertTrue(!shape.isEmpty() && !Shapes.block().equals(shape), "custom shape " + facing);
            helper.assertTrue(shape.bounds().minY > 0 && shape.bounds().maxY < 1.0, "fits the cell " + facing);
            // Rotate local probes in lockstep with the model's blockstate variant.
            helper.assertTrue(contains(shape, rotate(.5, .79, .75, facing)), "deck " + facing);
            helper.assertTrue(!contains(shape, rotate(.5, .79, .4, facing)), "front of the deck " + facing);
            helper.assertTrue(contains(shape, rotate(.5, .9, .88, facing)), "faucet " + facing);
            helper.assertTrue(contains(shape, rotate(.5, .55, .85, facing)), "P-trap " + facing);
            helper.assertTrue(!contains(shape, rotate(.3, .55, .6, facing)), "under-sink air " + facing);
            helper.assertTrue(!contains(shape, rotate(.5, .3, .6, facing)), "knee clearance " + facing);

            // Rear wall is visual only: placing/removing it does not destroy the sink.
            BlockPos behind = pos.relative(facing.getOpposite());
            level.setBlock(behind, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(pos).is(sink), "wall added " + facing);
            level.removeBlock(behind, false);
            helper.assertTrue(level.getBlockState(pos).is(sink), "wall removed " + facing);
        }

        clear(helper, pos);
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(placeSink(helper, pos, Direction.NORTH) && level.getBlockState(pos).is(sink)
                && level.getBlockState(pos.above()).is(Blocks.STONE), "an occupied cell above does not block placement");

        // A V1 upper half left in a saved world: invisible, empty, no drop, gone on the next shape update.
        clear(helper, pos);
        BlockState legacy = sink.defaultBlockState().setValue(CommercialWallMountedSinkBlock.HALF, DoubleBlockHalf.UPPER);
        level.setBlock(pos, legacy, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        helper.assertTrue(level.getBlockState(pos).is(sink) && level.getBlockState(pos).getShape(level, pos).isEmpty()
                && legacy.isRandomlyTicking(), "legacy upper is empty and ticks");
        level.setBlock(pos.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(level.getBlockState(pos).isAir(), "legacy upper removes itself");
        helper.assertTrue(drops(helper, pos, AflItems.COMMERCIAL_WALL_MOUNTED_SINK.get()) == 0, "legacy upper drops nothing");

        for (Item tool : List.of(Items.AIR, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE,
                Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
            clear(helper, pos);
            helper.assertTrue(placeSink(helper, pos, Direction.NORTH), "place before break " + tool);
            boolean shouldDrop = tool == Items.IRON_PICKAXE || tool == Items.DIAMOND_PICKAXE
                    || tool == Items.NETHERITE_PICKAXE;
            breakAndCount(helper, pos, tool, shouldDrop, AflItems.COMMERCIAL_WALL_MOUNTED_SINK.get(), "sink");
        }

        mirror(helper, pos);
        ApocalypseFirstLight.LOGGER.info("[AFL WALL SINK TEST] PASS four facings, one-cell placement/shape, wall independence, legacy upper cleanup, six survival break cases; wall mirror four facings, wall support, seven survival break cases");
        helper.succeed();
    }

    private static void mirror(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        Block mirror = AflBlocks.WALL_MIRROR.get();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            clear(helper, pos);
            BlockPos wall = pos.relative(facing.getOpposite());
            helper.assertTrue(!placeMirror(helper, wall, facing), "no wall, no mirror " + facing);
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(placeMirror(helper, wall, facing), "place on the wall " + facing);
            BlockState state = level.getBlockState(pos);
            helper.assertTrue(state.is(mirror) && state.getValue(WallMirrorBlock.FACING) == facing, "facing " + facing);
            var shape = state.getShape(level, pos);
            helper.assertTrue(contains(shape, rotate(.5, .4, .99, facing)), "glass against the wall " + facing);
            helper.assertTrue(!contains(shape, rotate(.5, .4, .5, facing)), "thin " + facing);
            level.removeBlock(wall, false);
            helper.assertTrue(level.getBlockState(pos).isAir(), "falls off without its wall " + facing);
        }
        clear(helper, pos);
        helper.assertTrue(!placeMirror(helper, pos.below(), Direction.UP), "not on a floor");

        for (Item tool : List.of(Items.AIR, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
                Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
            clear(helper, pos);
            level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(placeMirror(helper, pos.south(), Direction.NORTH), "place mirror before break " + tool);
            breakAndCount(helper, pos, tool, tool != Items.AIR, AflItems.WALL_MIRROR.get(), "mirror");
        }
    }

    private static void breakAndCount(GameTestHelper helper, BlockPos pos, Item tool, boolean shouldDrop, Item item, String what) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "sink_mining"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        helper.assertTrue(player.getMainHandItem().isCorrectToolForDrops(level.getBlockState(pos)) == shouldDrop,
                what + " correct tool predicate " + tool);
        helper.assertTrue(player.gameMode.destroyBlock(pos), what + " destroy " + tool);
        helper.assertTrue(level.getBlockState(pos).isAir(), what + " cleared " + tool);
        int drops = drops(helper, pos, item);
        helper.assertTrue(drops == (shouldDrop ? 1 : 0), what + " drop count " + tool + " = " + drops);
    }

    private static int drops(GameTestHelper helper, BlockPos pos, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static Vec3 rotate(double x, double y, double z, Direction facing) {
        int turns = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        for (int i = 0; i < turns; i++) { double oldX = x; x = 1 - z; z = oldX; }
        return new Vec3(x, y, z);
    }

    private static boolean contains(net.minecraft.world.phys.shapes.VoxelShape shape, Vec3 point) {
        return shape.toAabbs().stream().anyMatch(box -> box.contains(point));
    }

    private static boolean placeSink(GameTestHelper helper, BlockPos pos, Direction facing) {
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

    /** Clicks {@code face} of {@code clicked}, so the mirror goes into the cell on that side, facing that way. */
    private static boolean placeMirror(GameTestHelper helper, BlockPos clicked, Direction face) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "mirror_place"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.WALL_MIRROR.get()));
        Vec3 hit = Vec3.atCenterOf(clicked).add(face.getStepX() * .5, face.getStepY() * .5, face.getStepZ() * .5);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(hit, face, clicked, false));
        return ((BlockItem) AflItems.WALL_MIRROR.get()).place(context).consumesAction();
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
