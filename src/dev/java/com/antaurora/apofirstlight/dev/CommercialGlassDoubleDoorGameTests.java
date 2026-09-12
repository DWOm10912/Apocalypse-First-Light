package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CommercialGlassDoubleDoorBlock;
import com.antaurora.apofirstlight.blockentity.CommercialGlassDoubleDoorBlockEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

@GameTestHolder("afl_commercial_door_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class CommercialGlassDoubleDoorGameTests {
    private static final String NAMESPACE = "afl_commercial_door_tests";

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
        return List.of(new TestFunction("commercial_door", NAMESPACE + ":integration",
                NAMESPACE + ":empty", 300, 0L, true, CommercialGlassDoubleDoorGameTests::run));
    }

    private static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        CommercialGlassDoubleDoorBlock door = (CommercialGlassDoubleDoorBlock) AflBlocks.COMMERCIAL_GLASS_DOUBLE_DOOR.get();
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(door.defaultBlockState()), "iron pickaxe harvests");
        helper.assertTrue(!new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(door.defaultBlockState()), "stone pickaxe does not harvest");
        BlockPos root = helper.absolutePos(new BlockPos(7, 2, 7));
        for (int i = 0; i < 4; i++) {
            Direction facing = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}[i];
            Direction width = facing.getClockWise();
            prepare(helper, root);
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "door_test"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(root.getX() + 0.5, root.getY() + 1, root.getZ() + 3);
            player.setYRot(i * 90);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.COMMERCIAL_GLASS_DOUBLE_DOOR.get()));
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                    player.getMainHandItem(), new BlockHitResult(Vec3.atBottomCenterOf(root), Direction.UP, root.below(), false));
            helper.assertTrue(((BlockItem) AflItems.COMMERCIAL_GLASS_DOUBLE_DOOR.get()).place(context).consumesAction(), "places facing " + facing);
            BlockPos[] cells = {root, root.relative(width), root.above(), root.above().relative(width)};
            var parts = CommercialGlassDoubleDoorBlock.Part.values();
            for (int j = 0; j < 4; j++) {
                var state = level.getBlockState(cells[j]);
                helper.assertTrue(state.is(door) && state.getValue(CommercialGlassDoubleDoorBlock.PART) == parts[j]
                                && state.getValue(CommercialGlassDoubleDoorBlock.FACING) == facing
                                && !state.getValue(CommercialGlassDoubleDoorBlock.OPEN), "four matching parts " + facing);
            }
            var renderBounds = ((CommercialGlassDoubleDoorBlockEntity) level.getBlockEntity(root)).getRenderBoundingBox();
            for (BlockPos cell : cells) {
                helper.assertTrue(renderBounds.contains(Vec3.atCenterOf(cell)), "render bounds include " + cell + " facing " + facing);
            }
            helper.assertTrue(renderBounds.getYsize() > 2.0, "render bounds cover the full door height");
            BlockPos clicked = cells[i];
            var hit = new BlockHitResult(Vec3.atCenterOf(clicked), facing, clicked, false);
            door.use(level.getBlockState(clicked), level, clicked, player, InteractionHand.MAIN_HAND, hit);
            for (BlockPos cell : cells) helper.assertTrue(level.getBlockState(cell).getValue(CommercialGlassDoubleDoorBlock.OPEN), "all open");
            var clearance = switch (facing) {
                case NORTH -> Shapes.box(9.0 / 16, 0.2, 6.0 / 16, 1, 1, 10.0 / 16);
                case EAST -> Shapes.box(6.0 / 16, 0.2, 9.0 / 16, 10.0 / 16, 1, 1);
                case SOUTH -> Shapes.box(0, 0.2, 6.0 / 16, 7.0 / 16, 1, 10.0 / 16);
                case WEST -> Shapes.box(6.0 / 16, 0.2, 0, 10.0 / 16, 1, 7.0 / 16);
                default -> throw new IllegalStateException();
            };
            helper.assertTrue(!Shapes.joinIsNotEmpty(level.getBlockState(root).getCollisionShape(level, root), clearance, BooleanOp.AND), "open center clear " + facing);
            var upperClearance = switch (facing) {
                case NORTH -> Shapes.box(9.0 / 16, 0, 6.0 / 16, 1, 14.0 / 16, 10.0 / 16);
                case EAST -> Shapes.box(6.0 / 16, 0, 9.0 / 16, 10.0 / 16, 14.0 / 16, 1);
                case SOUTH -> Shapes.box(0, 0, 6.0 / 16, 7.0 / 16, 14.0 / 16, 10.0 / 16);
                case WEST -> Shapes.box(6.0 / 16, 0, 0, 10.0 / 16, 14.0 / 16, 7.0 / 16);
                default -> throw new IllegalStateException();
            };
            helper.assertTrue(!Shapes.joinIsNotEmpty(level.getBlockState(root.above()).getCollisionShape(level, root.above()),
                    upperClearance, BooleanOp.AND), "open passage clear through 1.875 blocks " + facing);
            door.use(level.getBlockState(clicked), level, clicked, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(level.getBlockState(root).getValue(CommercialGlassDoubleDoorBlock.OPEN), "rapid click ignored");
            ((CommercialGlassDoubleDoorBlockEntity) level.getBlockEntity(root)).markToggled(level.getGameTime() - 12);
            door.use(level.getBlockState(clicked), level, clicked, player, InteractionHand.MAIN_HAND, hit);
            for (BlockPos cell : cells) helper.assertTrue(!level.getBlockState(cell).getValue(CommercialGlassDoubleDoorBlock.OPEN), "all closed");
            helper.assertTrue(Shapes.joinIsNotEmpty(level.getBlockState(root).getCollisionShape(level, root), clearance,
                    BooleanOp.AND), "closed passage blocks " + facing);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            helper.assertTrue(player.gameMode.destroyBlock(clicked), "break part " + i);
            for (BlockPos cell : cells) helper.assertTrue(level.getBlockState(cell).isAir(), "no ghost parts");
            int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(4)).stream()
                    .filter(e -> e.getItem().is(AflItems.COMMERCIAL_GLASS_DOUBLE_DOOR.get()))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(drops == 1, "one drop from part " + i + ": " + drops);
        }
        prepare(helper, root);
        level.setBlock(root.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "door_blocked"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(root.getX(), root.getY() + 1, root.getZ() + 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.COMMERCIAL_GLASS_DOUBLE_DOOR.get()));
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(root), Direction.UP, root.below(), false));
        helper.assertTrue(!((BlockItem) AflItems.COMMERCIAL_GLASS_DOUBLE_DOOR.get()).place(context).consumesAction() && level.getBlockState(root).isAir(), "blocked placement is atomic");
        ApocalypseFirstLight.LOGGER.info("[AFL COMMERCIAL DOOR TEST] PASS four facings, four parts, render bounds, 1.875-block open passage, close/cooldown, single drops, blocked placement, iron tier");
        helper.succeed();
    }

    private static void prepare(GameTestHelper helper, BlockPos root) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(6)).forEach(ItemEntity::discard);
        for (BlockPos pos : BlockPos.betweenClosed(root.offset(-2, 0, -2), root.offset(2, 2, 2)))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos pos : BlockPos.betweenClosed(root.offset(-2, -1, -2), root.offset(2, -1, 2)))
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    }
}
