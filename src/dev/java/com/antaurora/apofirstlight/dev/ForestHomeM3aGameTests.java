package com.antaurora.apofirstlight.dev;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RuralMailboxBlock;
import com.antaurora.apofirstlight.block.TubeFarmGateBlock;
import com.antaurora.apofirstlight.blockentity.RuralMailboxBlockEntity;
import com.antaurora.apofirstlight.blockentity.TubeFarmGateBlockEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.BlockItem;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The forest start home's M3a driveway pieces (docs/models/rural_driveway_v1.md, 2026-10-14):
 * <ul>
 * <li>Tube farm gate: placed whole (4 x 2 cells, the hinge post in the clicked cell), opening away from the user (from the
 * front: to the back, and the other way), the shapes of the shut and open leaf, the animation channel and sign bones the
 * block entity reports, the PRIVATE DRIVE sign cycling on every cell (sneak, empty hand; not with an item), broken from any
 * cell: every cell gone, one gate dropped.</li>
 * <li>Rural mailbox: placed whole (2 high), opened from either cell (a 5-slot hopper screen, the door's OPEN while open), the
 * flag (sneak, empty hand) on both cells, broken: the mailbox and its contents drop.</li>
 * <li>Survival mining (AGENTS.md audit; tiers to be redone, user 2026-10-14): pickaxe + stone, needs the tool.</li>
 * </ul>
 * Run with {@code src/dev/forest-home-m3a-gametest.init.gradle}.
 */
@GameTestHolder("afl_m3a_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class ForestHomeM3aGameTests {
    private static final String NAMESPACE = "afl_m3a_tests";
    private static final int SIZE = 16, HEIGHT = 8;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = TagParser.parseTag("{size:[" + SIZE + "," + HEIGHT + "," + SIZE + "],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new ListTag();
        for (int x = 0; x < SIZE; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIZE; z++) {
            var block = new CompoundTag();
            var position = new ListTag();
            for (int coordinate : new int[]{x, y, z}) position.add(IntTag.valueOf(coordinate));
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
        return List.of(new TestFunction("forest_home_m3a", NAMESPACE + ":integration", NAMESPACE + ":empty", 100, 0L, true,
                ForestHomeM3aGameTests::run));
    }

    private static Block block(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, id));
        check(b != null && b != Blocks.AIR, "no block " + id);
        return b;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static FakePlayer player(ServerLevel level, String name, BlockPos at) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }

    private static InteractionResult use(ServerLevel level, FakePlayer p, BlockPos at) {
        return level.getBlockState(at).use(level, p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
    }

    private static boolean place(FakePlayer p, Block block, BlockPos at) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block.asItem()));
        var context = new BlockPlaceContext(p, InteractionHand.MAIN_HAND, p.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(at), Direction.UP, at.below(), false));
        boolean placed = ((BlockItem) block.asItem()).place(context).consumesAction();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return placed;
    }

    private static List<ItemEntity> drops(ServerLevel level, BlockPos at, double reach) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(reach));
    }

    private static void clearDrops(ServerLevel level, BlockPos at, double reach) {
        for (ItemEntity e : drops(level, at, reach)) e.discard();
    }

    private static int count(ServerLevel level, BlockPos at, double reach, net.minecraft.world.item.Item item) {
        return drops(level, at, reach).stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static VoxelShape shape(ServerLevel level, BlockPos at) {
        return level.getBlockState(at).getCollisionShape(level, at, CollisionContext.empty());
    }

    private static void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        String gate = gate(helper), mailbox = mailbox(helper), mining = mining(helper);
        ApocalypseFirstLight.LOGGER.info("[AFL FOREST HOME M3A TEST] PASS gate ({}), mailbox ({}), mining {}", gate, mailbox, mining);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------- gate

    private static String gate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TubeFarmGateBlock gate = (TubeFarmGateBlock) block("tube_farm_gate");
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 6));
        // the placer stands north, looking south: the gate faces north, its columns run east
        FakePlayer front = player(level, "gate_front", at.north(3));
        front.setYRot(0);
        check(place(front, gate, at), "gate placed");
        for (TubeFarmGateBlock.Cell cell : TubeFarmGateBlock.Cell.values()) {
            BlockState s = level.getBlockState(at.east(cell.col()).above(cell.row()));
            check(s.is(gate) && s.getValue(TubeFarmGateBlock.CELL) == cell && s.getValue(TubeFarmGateBlock.FACING) == Direction.NORTH
                    && !s.getValue(TubeFarmGateBlock.OPEN) && s.getValue(TubeFarmGateBlock.SIGN) == TubeFarmGateBlock.Sign.NONE, "cell " + cell + ": " + s);
        }
        check(level.getBlockEntity(at) instanceof TubeFarmGateBlockEntity, "no gate block entity on the master");
        check(level.getBlockEntity(at.east(2)) == null, "a block entity on another cell");
        BlockPos middle = at.east(2);
        check(!shape(level, middle).isEmpty(), "the shut leaf has no collision in the middle column");

        // opened from the front: it swings to the back; the middle columns open up, the leaf stands in the hinge column's back half
        check(use(level, front, middle).consumesAction(), "gate used from the front");
        BlockState s = level.getBlockState(at);
        check(s.getValue(TubeFarmGateBlock.OPEN) && s.getValue(TubeFarmGateBlock.SWING) == TubeFarmGateBlock.Swing.BACK, "from the front: " + s);
        check(level.getBlockState(at.east(3).above()).getValue(TubeFarmGateBlock.OPEN), "the other cells did not open");
        check(shape(level, middle).isEmpty(), "open, the middle column still collides: " + shape(level, middle));
        AABB hinge = shape(level, at).bounds();
        check(hinge.maxZ > 0.99 && hinge.minZ > 0.4, "open to the back, the hinge column's shape " + hinge);
        TubeFarmGateBlockEntity be = (TubeFarmGateBlockEntity) level.getBlockEntity(at);
        check(be.meshChannelTarget("open_back") && !be.meshChannelTarget("open_front"), "channels open to the back");
        check(use(level, front, middle).consumesAction() && !level.getBlockState(at).getValue(TubeFarmGateBlock.OPEN), "closed");
        check(!be.meshChannelTarget("open_back") && !be.meshChannelTarget("open_front"), "channels shut");

        // opened from the back: it swings to the front
        FakePlayer back = player(level, "gate_back", at.south(3));
        check(use(level, back, middle).consumesAction(), "gate used from the back");
        s = level.getBlockState(at);
        check(s.getValue(TubeFarmGateBlock.OPEN) && s.getValue(TubeFarmGateBlock.SWING) == TubeFarmGateBlock.Swing.FRONT, "from the back: " + s);
        check(shape(level, at).bounds().minZ < 0.01, "open to the front, the hinge column's shape " + shape(level, at).bounds());
        check(be.meshChannelTarget("open_front") && !be.meshChannelTarget("open_back"), "channels open to the front");
        use(level, back, middle);

        // the sign: sneak with an empty hand cycles none -> front -> back -> none on every cell; with an item in hand, nothing
        front.setShiftKeyDown(true);
        TubeFarmGateBlock.Sign[] expected = {TubeFarmGateBlock.Sign.FRONT, TubeFarmGateBlock.Sign.BACK, TubeFarmGateBlock.Sign.NONE};
        for (TubeFarmGateBlock.Sign want : expected) {
            check(use(level, front, at.east(3).above()).consumesAction(), "sign use");
            for (TubeFarmGateBlock.Cell cell : TubeFarmGateBlock.Cell.values())
                check(level.getBlockState(at.east(cell.col()).above(cell.row())).getValue(TubeFarmGateBlock.SIGN) == want, "sign " + want + " on " + cell);
            check(be.meshPartVisible("sign_front") == (want == TubeFarmGateBlock.Sign.FRONT)
                    && be.meshPartVisible("sign_back") == (want == TubeFarmGateBlock.Sign.BACK) && be.meshPartVisible("leaf"), "sign bones for " + want);
            check(!level.getBlockState(at).getValue(TubeFarmGateBlock.OPEN), "the sign opened the gate");
        }
        front.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        check(use(level, front, at.east(1)) == InteractionResult.PASS, "sneak with an item in hand acted");
        front.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        front.setShiftKeyDown(false);

        // broken from the far top cell with the right tool: every cell gone, one gate
        clearDrops(level, at, 5);
        front.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_PICKAXE));
        check(front.gameMode.destroyBlock(at.east(3).above()), "gate broken");
        for (TubeFarmGateBlock.Cell cell : TubeFarmGateBlock.Cell.values())
            check(level.getBlockState(at.east(cell.col()).above(cell.row())).isAir(), "cell left after breaking: " + cell);
        int gates = count(level, at, 5, gate.asItem());
        check(gates == 1, "gates dropped: " + gates);
        clearDrops(level, at, 5);
        return "8 cells, both ways, sign 3 looks, break";
    }

    // ------------------------------------------------------------------------------------------------- mailbox

    private static String mailbox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RuralMailboxBlock mailbox = (RuralMailboxBlock) block("rural_mailbox");
        BlockPos at = helper.absolutePos(new BlockPos(10, 1, 3));
        // the placer stands on the road to the south, looking north: the door faces south
        FakePlayer p = player(level, "mailbox_test", at.south(2));
        p.setYRot(180);
        check(place(p, mailbox, at), "mailbox placed");
        BlockState bottom = level.getBlockState(at), top = level.getBlockState(at.above());
        check(bottom.is(mailbox) && bottom.getValue(RuralMailboxBlock.CELL) == RuralMailboxBlock.Cell.R0 && bottom.getValue(RuralMailboxBlock.FACING) == Direction.SOUTH, "bottom " + bottom);
        check(top.is(mailbox) && top.getValue(RuralMailboxBlock.CELL) == RuralMailboxBlock.Cell.R1, "top " + top);
        check(level.getBlockEntity(at) instanceof RuralMailboxBlockEntity && level.getBlockEntity(at.above()) == null, "block entity on the bottom cell only");
        RuralMailboxBlockEntity be = (RuralMailboxBlockEntity) level.getBlockEntity(at);

        // opened from the box (the top cell): a 5-slot hopper screen on the master's storage, the door open until it closes
        check(use(level, p, at.above()) == InteractionResult.CONSUME, "mailbox used");
        check(p.containerMenu instanceof HopperMenu menu && menu.slots.get(0).container == be && be.getContainerSize() == 5, "menu " + p.containerMenu);
        check(level.getBlockState(at).getValue(RuralMailboxBlock.OPEN) && be.meshChannelTarget("open"), "open while in use");
        p.closeContainer();
        check(!level.getBlockState(at).getValue(RuralMailboxBlock.OPEN) && !be.meshChannelTarget("open"), "still open after closing");

        // the flag: sneak, empty hand, on both cells
        p.setShiftKeyDown(true);
        check(use(level, p, at).consumesAction(), "flag use");
        check(level.getBlockState(at).getValue(RuralMailboxBlock.FLAG) && level.getBlockState(at.above()).getValue(RuralMailboxBlock.FLAG) && be.meshChannelTarget("flag"), "flag up");
        check(!(p.containerMenu instanceof HopperMenu), "the flag opened the box");
        check(use(level, p, at.above()).consumesAction() && !level.getBlockState(at).getValue(RuralMailboxBlock.FLAG) && !be.meshChannelTarget("flag"), "flag down");
        p.setShiftKeyDown(false);

        // broken (top cell, the right tool): the mailbox and its letters drop, both cells go
        be.setItem(0, new ItemStack(Items.PAPER, 3));
        be.setItem(4, new ItemStack(Items.BOOK));
        clearDrops(level, at, 3);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_PICKAXE));
        check(p.gameMode.destroyBlock(at.above()), "mailbox broken");
        check(level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir(), "a cell left after breaking");
        check(count(level, at, 3, mailbox.asItem()) == 1, "mailboxes dropped: " + count(level, at, 3, mailbox.asItem()));
        check(count(level, at, 3, Items.PAPER) == 3 && count(level, at, 3, Items.BOOK) == 1, "contents dropped: " + drops(level, at, 3));
        clearDrops(level, at, 3);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return "2 cells, 5 slots, open, flag, contents";
    }

    // ------------------------------------------------------------------------------------------------- mining

    private static String mining(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<TagKey<Block>> tools = List.of(BlockTags.MINEABLE_WITH_AXE, BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE);
        List<TagKey<Block>> tiers = List.of(BlockTags.NEEDS_STONE_TOOL, BlockTags.NEEDS_IRON_TOOL, BlockTags.NEEDS_DIAMOND_TOOL);
        BlockPos at = helper.absolutePos(new BlockPos(12, 1, 12));
        FakePlayer miner = player(level, "mining_test", at.north(2));
        int breaks = 0;
        for (String id : List.of("tube_farm_gate", "rural_mailbox")) {
            BlockState s = block(id).defaultBlockState();
            for (TagKey<Block> t : tools) check(s.is(t) == (t == BlockTags.MINEABLE_WITH_PICKAXE), id + ": tool tag " + t.location());
            for (TagKey<Block> t : tiers) check(s.is(t) == (t == BlockTags.NEEDS_STONE_TOOL), id + ": tier tag " + t.location());
            check(s.requiresCorrectToolForDrops(), id + ": does not need the tool");
            check(!dropsWhenBroken(level, miner, at, s, ItemStack.EMPTY), id + ": dropped by hand");
            check(!dropsWhenBroken(level, miner, at, s, new ItemStack(Items.WOODEN_PICKAXE)), id + ": dropped with a wooden pickaxe");
            check(dropsWhenBroken(level, miner, at, s, new ItemStack(Items.STONE_PICKAXE)), id + ": nothing with a stone pickaxe");
            breaks += 3;
        }
        return "2 blocks, " + breaks + " breaks";
    }

    /** Sets s (the master cell, alone) at the cell, breaks it in survival with tool and tells whether it dropped itself. */
    private static boolean dropsWhenBroken(ServerLevel level, FakePlayer miner, BlockPos at, BlockState s, ItemStack tool) {
        clearDrops(level, at, 1.5);
        level.setBlock(at, s, 3);
        check(level.getBlockState(at).is(s.getBlock()), "could not set " + s);
        miner.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
        check(miner.gameMode.destroyBlock(at), "not broken: " + s);
        check(level.getBlockState(at).isAir(), "still there after breaking: " + level.getBlockState(at));
        boolean dropped = count(level, at, 1.5, s.getBlock().asItem()) > 0;
        clearDrops(level, at, 1.5);
        return dropped;
    }
}
