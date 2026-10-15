package com.antaurora.apofirstlight.dev;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.BoxStove;
import com.antaurora.apofirstlight.cabin.EmergencyRadio;
import com.antaurora.apofirstlight.cabin.EmergencyRadio.Radio;
import com.antaurora.apofirstlight.cabin.EmergencyRadioBlockEntity;
import com.antaurora.apofirstlight.cabin.FurnitureSeat;
import com.antaurora.apofirstlight.meshhit.MeshHitClip;
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
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The forest start home's M2 wrap-up (docs/worldgen/forest_start_home_b_v1_plan.md, 2026-10-14):
 * <ul>
 * <li>Emergency radio (docs/models/emergency_radio_v1.md): the surface under it (table, counter, floor, none), placement on
 * a table, a crank at the crank's end (charge, the arm's quarter), the switch at the front (on, lit), the charge running
 * out (dark, the switch stays on) and winding again, the crosshair from the side (the ray in the table's cell), the drop keeping its charge and placing it back, the table taken away
 * (the radio drops) or swapped for stone (it stands on the floor), every piece of every state.</li>
 * <li>Chair (docs/models/wood_furniture_v1.md): right-click sits (a FurnitureSeat), a second player cannot, the seat goes
 * when its rider stands. Counters: right-click opens 18 / 9 slots.</li>
 * <li>Survival mining (AGENTS.md audit): each M2 block's tool tag and tier tag, broken in survival with the hand, one tier
 * too low and the right tool: what drops.</li>
 * </ul>
 * Run with {@code src/dev/forest-home-m2-gametest.init.gradle}.
 */
@GameTestHolder("afl_m2_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class ForestHomeM2GameTests {
    private static final String NAMESPACE = "afl_m2_tests";
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
        return List.of(new TestFunction("forest_home_m2", NAMESPACE + ":integration", NAMESPACE + ":empty", 200, 0L, true,
                ForestHomeM2GameTests::run));
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

    /** The radio's state at the absolute pos. */
    private static BlockState radio(ServerLevel level, BlockPos at) {
        BlockState s = level.getBlockState(at);
        check(s.getBlock() instanceof Radio, "no radio at " + at + ": " + s);
        return s;
    }

    private static int charge(ServerLevel level, BlockPos at) {
        check(level.getBlockEntity(at) instanceof EmergencyRadioBlockEntity, "no radio block entity at " + at);
        return ((EmergencyRadioBlockEntity) level.getBlockEntity(at)).charge();
    }

    /** A right-click on the radio at the north-frame point (x, z) of its top part (y over its sunk bottom). */
    private static InteractionResult click(ServerLevel level, FakePlayer p, BlockPos at, double x, double y, double z) {
        BlockState s = radio(level, at);
        Vec3 hit = BoxStove.world(s.getValue(Radio.FACING), at, x, y - s.getValue(Radio.SURFACE).sink, z);
        return s.use(level, p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, at, false));
    }

    private static List<ItemEntity> drops(ServerLevel level, BlockPos at) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(1.5));
    }

    private static void clearDrops(ServerLevel level, BlockPos at) {
        for (ItemEntity e : drops(level, at)) e.discard();
    }

    private static void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        Block table = block("wooden_table"), counter = block("wooden_kitchen_counter"), sinkCounter = block("wooden_sink_counter");
        Radio radioBlock = (Radio) block("emergency_radio");

        // ---- radio: the surface under it
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), stone);
        helper.setBlock(new BlockPos(2, 1, 2), table);
        helper.setBlock(new BlockPos(5, 1, 2), counter);
        check(EmergencyRadio.surface(level, helper.absolutePos(new BlockPos(2, 2, 2))) == EmergencyRadio.Surface.TABLE, "table surface");
        check(EmergencyRadio.surface(level, helper.absolutePos(new BlockPos(5, 2, 2))) == EmergencyRadio.Surface.COUNTER, "counter surface");
        check(EmergencyRadio.surface(level, helper.absolutePos(new BlockPos(8, 1, 2))) == EmergencyRadio.Surface.FLOOR, "floor surface");
        check(EmergencyRadio.surface(level, helper.absolutePos(new BlockPos(8, 3, 2))) == null, "nothing under it, yet a surface");

        // ---- placement on the table: the player looks west, the radio faces east (the turn the pieces and hits go through)
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        FakePlayer p = player(level, "radio_test", at.east(2));
        p.setYRot(90);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(radioBlock.asItem()));
        var place = new BlockPlaceContext(p, InteractionHand.MAIN_HAND, p.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(at), Direction.UP, at.below(), false));
        check(((BlockItem) radioBlock.asItem()).place(place).consumesAction(), "radio placed on the table");
        BlockState s = radio(level, at);
        check(s.getValue(Radio.FACING) == Direction.EAST && s.getValue(Radio.SURFACE) == EmergencyRadio.Surface.TABLE
                && !s.getValue(Radio.ON) && !s.getValue(Radio.LIT) && s.getValue(Radio.CRANK) == 0, "placed state " + s);
        check(charge(level, at) == 0, "a new radio has no charge");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // the hits: the crank's end, the front
        check(Radio.action(s, at, BoxStove.world(Direction.EAST, at, 0.72, 0.1 - 0.24, 0.5)) == EmergencyRadio.Action.CRANK, "crank's end");
        check(Radio.action(s, at, BoxStove.world(Direction.EAST, at, 0.5, 0.1 - 0.24, 0.40)) == EmergencyRadio.Action.ON, "front: on");
        check(Radio.action(s, at, BoxStove.world(Direction.EAST, at, 0.31, 0.1 - 0.24, 0.5)) == EmergencyRadio.Action.ON, "far end: the switch");
        // crank once: a quarter, WIND of charge, still dark (off)
        check(click(level, p, at, 0.72, 0.1, 0.5).consumesAction(), "crank used");
        s = radio(level, at);
        check(s.getValue(Radio.CRANK) == 1 && charge(level, at) == EmergencyRadio.WIND && !s.getValue(Radio.LIT), "one crank " + s + " " + charge(level, at));
        // switch on: on and lit
        check(click(level, p, at, 0.5, 0.1, 0.40).consumesAction(), "switch used");
        s = radio(level, at);
        check(s.getValue(Radio.ON) && s.getValue(Radio.LIT), "switched on " + s);
        check(s.getLightEmission() == 2, "lit block light " + s.getLightEmission());
        // seen from 3.5 m off its front at a standing eye's height, the ray comes into the table's cell under the radio's
        // own: it still finds the radio (MeshHitReachesDown), not the table top behind it
        Vec3 aim = BoxStove.world(Direction.EAST, at, 0.5, 0.1 - 0.24, 0.5), eye = aim.add(3.5, 0.76, 0.3);
        double edgeY = eye.y + (aim.y - eye.y) * (eye.x - (at.getX() + 1)) / (eye.x - aim.x);
        check(edgeY < at.getY(), "the side ray enters the radio's own cell (" + edgeY + "): no test of the cell under it");
        BlockHitResult side = MeshHitClip.clip(level, new ClipContext(eye, aim.add(aim.subtract(eye).normalize()), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        check(side.getType() == HitResult.Type.BLOCK && side.getBlockPos().equals(at), "side view picks " + side.getBlockPos() + " " + level.getBlockState(side.getBlockPos()));

        // ---- every piece of every state exists, sunk by its surface
        var modFile = ModList.get().getModFileById(ApocalypseFirstLight.MOD_ID).getFile();
        Set<String> seen = new TreeSet<>();
        List<String> wrongLift = new ArrayList<>();
        for (BlockState any : radioBlock.getStateDefinition().getPossibleStates()) {
            float want = -any.getValue(Radio.SURFACE).sink;
            radioBlock.cabinPieces(any, 0, (name, turn, dy) -> { seen.add(name); if (dy != want) wrongLift.add(name + " " + dy); });
        }
        List<String> missing = new ArrayList<>();
        for (String name : seen)
            if (!Files.exists(modFile.findResource(("assets/" + ApocalypseFirstLight.MOD_ID + "/cabin_mesh/" + name + ".json").split("/")))) missing.add(name);
        check(seen.size() == 9 && missing.isEmpty(), "radio pieces " + seen + ", missing " + missing);
        check(wrongLift.isEmpty(), "pieces not sunk onto the surface " + wrongLift);

        // ---- chair: sit, a second player cannot, standing up ends the seat
        helper.setBlock(new BlockPos(2, 1, 6), block("wooden_chair"));
        BlockPos chairAt = helper.absolutePos(new BlockPos(2, 1, 6));
        BlockState chair = level.getBlockState(chairAt);
        FakePlayer sitter = player(level, "chair_test", chairAt.north());
        var chairHit = new BlockHitResult(Vec3.atCenterOf(chairAt), Direction.UP, chairAt, false);
        check(chair.use(level, sitter, InteractionHand.MAIN_HAND, chairHit) == InteractionResult.CONSUME, "chair used");
        check(sitter.getVehicle() instanceof FurnitureSeat && FurnitureSeat.occupied(level, chairAt), "sitting on the chair");
        FakePlayer second = player(level, "chair_test_2", chairAt.south());
        check(chair.use(level, second, InteractionHand.MAIN_HAND, chairHit) == InteractionResult.PASS && !second.isPassenger(), "two on one chair");
        sitter.stopRiding();
        check(!FurnitureSeat.occupied(level, chairAt), "stood up, the chair still taken");

        // ---- counters: 18 slots (drawer + doors) and 9 (under the sink)
        int[] slots = {18, 9};
        Block[] counters = {counter, sinkCounter};
        for (int i = 0; i < 2; i++) {
            helper.setBlock(new BlockPos(5 + 3 * i, 1, 6), counters[i]);
            BlockPos cAt = helper.absolutePos(new BlockPos(5 + 3 * i, 1, 6));
            FakePlayer user = player(level, "counter_test_" + i, cAt.north());
            check(level.getBlockState(cAt).use(level, user, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(cAt), Direction.NORTH, cAt, false))
                    == InteractionResult.CONSUME, "counter used");
            check(user.containerMenu instanceof ChestMenu menu && menu.getContainer().getContainerSize() == slots[i],
                    "counter menu " + user.containerMenu + " (want " + slots[i] + " slots)");
            user.closeContainer();
        }

        // ---- survival mining
        String report = mining(helper);

        // ---- the charge runs out (dark, the switch stays on), winding lights it again; the drop keeps the charge
        helper.runAfterDelay(EmergencyRadio.WIND + 20, () -> {
            BlockState out = radio(level, at);
            check(charge(level, at) == 0 && out.getValue(Radio.ON) && !out.getValue(Radio.LIT), "ran out " + out + " " + charge(level, at));
            check(!FurnitureSeat.occupied(level, chairAt) && level.getEntitiesOfClass(FurnitureSeat.class, new AABB(chairAt)).isEmpty(), "the empty seat stayed");
            check(click(level, p, at, 0.72, 0.1, 0.5).consumesAction(), "crank used again");
            BlockState again = radio(level, at);
            check(again.getValue(Radio.LIT) && again.getValue(Radio.CRANK) == 2 && charge(level, at) == EmergencyRadio.WIND, "wound again " + again);
            // broken by hand: it drops itself with the charge
            clearDrops(level, at);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            check(p.gameMode.destroyBlock(at), "radio broken");
            ItemStack dropped = drops(level, at).stream().map(ItemEntity::getItem).filter(st -> st.is(radioBlock.asItem())).findFirst().orElse(ItemStack.EMPTY);
            check(!dropped.isEmpty(), "the radio dropped nothing");
            check(dropped.getTag() != null && dropped.getTag().getCompound("BlockEntityTag").getInt("Charge") == EmergencyRadio.WIND, "drop's charge " + dropped.getTag());
            clearDrops(level, at);
            // placed back: the charge comes with it, off
            p.setItemInHand(InteractionHand.MAIN_HAND, dropped.copy());
            var back = new BlockPlaceContext(p, InteractionHand.MAIN_HAND, p.getMainHandItem(),
                    new BlockHitResult(Vec3.atBottomCenterOf(at), Direction.UP, at.below(), false));
            check(((BlockItem) radioBlock.asItem()).place(back).consumesAction(), "placed back");
            check(charge(level, at) == EmergencyRadio.WIND && !radio(level, at).getValue(Radio.ON), "placed back: charge " + charge(level, at));
            // the table becomes stone: it stands on the floor; the stone taken away: it drops
            helper.setBlock(new BlockPos(2, 1, 2), stone);
            check(radio(level, at).getValue(Radio.SURFACE) == EmergencyRadio.Surface.FLOOR, "on the stone: " + level.getBlockState(at));
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
            check(level.getBlockState(at).isAir(), "nothing under it, the radio stayed: " + level.getBlockState(at));
            check(drops(level, at).stream().anyMatch(e -> e.getItem().is(radioBlock.asItem())), "the falling radio dropped nothing");
            clearDrops(level, at);
            ApocalypseFirstLight.LOGGER.info("[AFL FOREST HOME M2 TEST] PASS radio (surfaces, crank, switch, run out, drop, support), {} pieces, chair, counters, mining {}",
                    seen.size(), report);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------- mining

    /** A block's mining rule: its tool tag, tier tag (null = none) and whether it needs the right tool to drop. */
    private record Rule(String id, TagKey<Block> tool, TagKey<Block> tier, boolean requires, ItemStack low, ItemStack right) {}

    private static String mining(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack hand = ItemStack.EMPTY;
        var axe = BlockTags.MINEABLE_WITH_AXE;
        var pick = BlockTags.MINEABLE_WITH_PICKAXE;
        List<Rule> rules = List.of(
                new Rule("wooden_table", axe, null, false, hand, new ItemStack(Items.WOODEN_AXE)),
                new Rule("wooden_chair", axe, null, false, hand, new ItemStack(Items.WOODEN_AXE)),
                new Rule("wooden_kitchen_counter", axe, null, false, hand, new ItemStack(Items.WOODEN_AXE)),
                new Rule("wooden_sink_counter", axe, null, false, hand, new ItemStack(Items.WOODEN_AXE)),
                new Rule("wooden_gun_rack", axe, null, false, hand, new ItemStack(Items.WOODEN_AXE)),
                new Rule("box_stove", pick, BlockTags.NEEDS_IRON_TOOL, true, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE)),
                new Rule("stove_heat_shield", pick, BlockTags.NEEDS_STONE_TOOL, true, new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.STONE_PICKAXE)),
                new Rule("stove_pipe", pick, BlockTags.NEEDS_STONE_TOOL, true, new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.STONE_PICKAXE)),
                new Rule("flagstone", pick, null, true, hand, new ItemStack(Items.WOODEN_PICKAXE)),
                new Rule("emergency_radio", pick, null, false, hand, new ItemStack(Items.WOODEN_PICKAXE)),
                new Rule("generator_inlet_box", pick, BlockTags.NEEDS_DIAMOND_TOOL, true, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE)),
                new Rule("load_center", pick, BlockTags.NEEDS_DIAMOND_TOOL, true, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE)),
                new Rule("porcelain_lampholder", null, null, false, hand, hand),
                new Rule("keyless_lampholder", null, null, false, hand, hand),
                new Rule("wall_switch", null, null, false, hand, hand),
                new Rule("wall_outlet", null, null, false, hand, hand));
        List<TagKey<Block>> tools = List.of(axe, pick, BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE);
        List<TagKey<Block>> tiers = List.of(BlockTags.NEEDS_STONE_TOOL, BlockTags.NEEDS_IRON_TOOL, BlockTags.NEEDS_DIAMOND_TOOL);
        // a stone cell (3 x 3 x 3, its middle open): every wall fitting finds its support
        BlockPos cell = new BlockPos(12, 3, 12);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            helper.setBlock(cell.offset(dx, dy, dz), dx == 0 && dy == 0 && dz == 0 ? Blocks.AIR : Blocks.STONE);
        BlockPos at = helper.absolutePos(cell);
        FakePlayer miner = player(level, "mining_test", at.below());
        int breaks = 0;
        for (Rule r : rules) {
            BlockState s = block(r.id).defaultBlockState();
            for (TagKey<Block> t : tools) check(s.is(t) == (t == r.tool), r.id + ": tool tag " + t.location() + " " + s.is(t));
            for (TagKey<Block> t : tiers) check(s.is(t) == (t == r.tier), r.id + ": tier tag " + t.location() + " " + s.is(t));
            check(s.requiresCorrectToolForDrops() == r.requires, r.id + ": requiresCorrectToolForDrops " + s.requiresCorrectToolForDrops());
            // bare hand (or one tier too low): drops only without the requirement; the right tool always drops
            check(dropsWhenBroken(level, miner, at, s, r.low) == !r.requires, r.id + ": broken with " + name(r.low));
            check(dropsWhenBroken(level, miner, at, s, r.right), r.id + ": nothing dropped with " + name(r.right));
            if (r.requires && !r.low.isEmpty()) check(!dropsWhenBroken(level, miner, at, s, hand), r.id + ": dropped by hand");
            breaks += r.requires && !r.low.isEmpty() ? 3 : 2;
        }
        return rules.size() + " blocks, " + breaks + " breaks";
    }

    private static String name(ItemStack tool) { return tool.isEmpty() ? "the hand" : ForgeRegistries.ITEMS.getKey(tool.getItem()).toString(); }

    /** Sets s at the cell, breaks it in survival with tool and tells whether it dropped itself. */
    private static boolean dropsWhenBroken(ServerLevel level, FakePlayer miner, BlockPos at, BlockState s, ItemStack tool) {
        clearDrops(level, at);
        level.setBlock(at, s, 3);
        check(level.getBlockState(at).is(s.getBlock()), "could not set " + s);
        miner.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
        check(miner.gameMode.destroyBlock(at), "not broken: " + s);
        check(level.getBlockState(at).isAir(), "still there after breaking: " + level.getBlockState(at));
        boolean dropped = drops(level, at).stream().anyMatch(e -> e.getItem().is(s.getBlock().asItem()));
        clearDrops(level, at);
        return dropped;
    }
}
