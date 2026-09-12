package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CashRegisterBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CashRegisterIntegrationGameTests {
    private CashRegisterIntegrationGameTests() {
    }

    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void staticCountertopAndMining(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        var placer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "register_place"));
        placer.setGameMode(GameType.SURVIVAL);
        placer.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 3);
        placer.setYRot(0);
        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.CASH_REGISTER.get()));
        var context = new BlockPlaceContext(placer, InteractionHand.MAIN_HAND, placer.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false));
        helper.assertTrue(((BlockItem) AflItems.CASH_REGISTER.get()).place(context).consumesAction(),
                "block item places on solid countertop");
        BlockState north = level.getBlockState(pos);
        helper.assertTrue(north.is(AflBlocks.CASH_REGISTER.get())
                        && north.getValue(CashRegisterBlock.FACING) == Direction.NORTH,
                "operator-facing north placement");
        helper.assertTrue(level.getBlockEntity(pos) == null, "no block entity");

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = north.setValue(CashRegisterBlock.FACING, facing);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            var outline = state.getShape(level, pos);
            var collision = state.getCollisionShape(level, pos);
            helper.assertTrue(!outline.isEmpty() && outline.equals(collision), facing + " collision matches outline");
            AABB bounds = outline.bounds();
            helper.assertTrue(bounds.getXsize() < 1 && bounds.getZsize() < 1 && bounds.getYsize() < 1,
                    facing + " shape is smaller than one block");
            helper.assertTrue(bounds.minX >= 0 && bounds.minY >= 0 && bounds.minZ >= 0
                            && bounds.maxX <= 1 && bounds.maxY <= 1 && bounds.maxZ <= 1,
                    facing + " shape remains centered in block");
        }
        level.setBlock(pos, north, Block.UPDATE_ALL);
        helper.assertTrue(north.is(BlockTags.MINEABLE_WITH_PICKAXE)
                        && north.is(BlockTags.NEEDS_IRON_TOOL), "iron-tier pickaxe tags loaded");

        assertToolDrop(helper, pos, Items.AIR, false);
        assertToolDrop(helper, pos, Items.WOODEN_PICKAXE, false);
        assertToolDrop(helper, pos, Items.STONE_PICKAXE, false);
        assertToolDrop(helper, pos, Items.IRON_PICKAXE, true);
        assertToolDrop(helper, pos, Items.DIAMOND_PICKAXE, true);
        assertToolDrop(helper, pos, Items.NETHERITE_PICKAXE, true);
        helper.succeed();
    }

    private static void assertToolDrop(GameTestHelper helper, BlockPos pos, Item tool, boolean shouldDrop) {
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
        level.setBlock(pos, AflBlocks.CASH_REGISTER.get().defaultBlockState(), Block.UPDATE_ALL);
        var miner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "register_mine"));
        miner.setGameMode(GameType.SURVIVAL);
        miner.setItemInHand(InteractionHand.MAIN_HAND,
                tool == Items.AIR ? ItemStack.EMPTY : new ItemStack(tool));
        helper.assertTrue(miner.gameMode.destroyBlock(pos), tool + " breaks register");
        int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
                .filter(entity -> entity.getItem().is(AflItems.CASH_REGISTER.get()))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(drops == (shouldDrop ? 1 : 0), tool + " yields " + drops + " register items");
    }
}
