package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelFixtures;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tunnel parts removable V1.1 (block/TunnelFixtureBlock): Survival placement of a handrail from its item, two rail
 * segments sharing a cell, the crowbar gate and time, taking the aimed part down from a shared cell (its item drops, the
 * other part stays), removal some other way (no drops, all its cells), and a large part (the jet fan) stripped for its
 * materials with the road_structure it took over given back.
 */
@GameTestHolder("afl_tunnel_fixture_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class TunnelFixtureGameTests {
    private static final String NAMESPACE = "afl_tunnel_fixture_tests";
    private static final int SX = 24, SY = 12, SZ = 24;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = net.minecraft.nbt.TagParser.parseTag("{size:[" + SX + "," + SY + "," + SZ + "],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new net.minecraft.nbt.ListTag();
        for (int x = 0; x < SX; x++) for (int y = 0; y < SY; y++) for (int z = 0; z < SZ; z++) {
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
        return List.of(new TestFunction("tunnel_fixture", NAMESPACE + ":integration",
                NAMESPACE + ":empty", 200, 0L, true, TunnelFixtureGameTests::run));
    }

    private static void run(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < SX; x++) for (int z = 0; z < SZ; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        Item railItem = AflItems.TUNNEL_FIXTURE_ITEMS.get(TunnelFixtureBlock.Kind.HANDRAIL).get();

        // a Survival player facing +z places two 2.4 m rails end to end on the floor
        Player player = h.makeMockSurvivalPlayer();
        player.getAbilities().instabuild = false;
        Vec3 stand = h.absoluteVec(new Vec3(8.5, 2, 4.5));
        player.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        h.assertTrue(place(h, player, railItem, new Vec3(8.5, 2, 8.5), Direction.UP) == InteractionResult.CONSUME, "first rail placed");
        h.assertTrue(player.getMainHandItem().isEmpty(), "Survival placement uses the item");
        h.assertTrue(place(h, player, railItem, new Vec3(10.9, 2, 8.5), Direction.UP) == InteractionResult.CONSUME, "second rail placed");
        h.assertTrue(place(h, player, railItem, new Vec3(14.5, 2.5, 8.0), Direction.NORTH) == InteractionResult.FAIL, "a floor part refuses a wall face");

        TunnelFixtureBlockEntity.Entry a = entryNear(h, h.absoluteVec(new Vec3(8.5, 2, 8.5)));
        TunnelFixtureBlockEntity.Entry b = entryNear(h, h.absoluteVec(new Vec3(10.9, 2, 8.5)));
        h.assertTrue(a != null && b != null && !a.same(b), "both rails stored");
        // the road network read back from its cache places a part a few mm off, with another length bone: still the same part
        TunnelMesh.Placement ap = a.placement;
        h.assertTrue(a.same(new TunnelFixtureBlockEntity.Entry(a.kind, new TunnelMesh.Placement(ap.mesh(), "2.3990",
                ap.x() + 0.004, ap.y() - 0.003, ap.z() + 0.005, ap.ex(), ap.ey(), ap.ez()))), "a near copy is the same part");
        Set<Long> aCells = cells(a), bCells = cells(b);
        for (long c : aCells) h.assertTrue(holds(level, c, a), "first rail in all its cells");
        for (long c : bCells) h.assertTrue(holds(level, c, b), "second rail in all its cells");
        Set<Long> shared = new HashSet<>(aCells);
        shared.retainAll(bCells);
        h.assertTrue(!shared.isEmpty(), "the rails meet in a shared cell");
        BlockPos sharedCell = BlockPos.of(shared.iterator().next());
        h.assertTrue(level.getBlockState(sharedCell).getCollisionShape(level, sharedCell).isEmpty() == false, "a rail cell collides");

        // aim at the second rail from in front of it
        double[] bb = b.part().bounds();
        Vec3 target = new Vec3((bb[0] + bb[3]) / 2, (bb[1] + bb[4]) / 2, (bb[2] + bb[5]) / 2);
        player.moveTo(target.x, target.y - player.getEyeHeight(), target.z - 2.0, 0.0F, 0.0F);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        var state = level.getBlockState(sharedCell);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        h.assertTrue(state.getDestroyProgress(player, level, sharedCell) == 0.0F, "bare hands do not move a fixture");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        h.assertTrue(state.getDestroyProgress(player, level, sharedCell) == 0.0F, "a pickaxe does not move a fixture");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AflItems.CROWBAR.get()));
        float progress = state.getDestroyProgress(player, level, sharedCell);
        h.assertTrue(Math.abs(progress - 1.0F / 40.0F) < 1e-6, "the crowbar takes a rail down in 2 s, was " + progress);

        clearDrops(h);
        h.assertTrue(state.onDestroyedByPlayer(level, sharedCell, player, true, level.getFluidState(sharedCell)), "taken down");
        for (long c : bCells) h.assertTrue(!holds(level, c, b), "the second rail left all its cells");
        for (long c : aCells) h.assertTrue(holds(level, c, a), "the first rail stays");
        h.assertTrue(level.getBlockState(sharedCell).getBlock() instanceof TunnelFixtureBlock, "the shared cell keeps the first rail");
        for (long c : bCells) if (!aCells.contains(c)) h.assertTrue(level.getBlockState(BlockPos.of(c)).isAir(), "an emptied cell is air again");
        Map<Item, Integer> drops = drops(h);
        h.assertTrue(drops.equals(Map.of(railItem, 1)), "one rail dropped, was " + drops);

        // removed some other way: the part leaves all its cells, nothing drops
        clearDrops(h);
        long other = aCells.stream().filter(c -> !shared.contains(c)).findFirst().orElse(aCells.iterator().next());
        level.setBlock(BlockPos.of(other), Blocks.AIR.defaultBlockState(), 3);
        for (long c : aCells) h.assertTrue(level.getBlockState(BlockPos.of(c)).isAir(), "the first rail left every cell");
        h.assertTrue(drops(h).isEmpty(), "no drops when removed by a command");

        // an egress sign on an ordinary stone wall: the stone stays, the sign takes the cells in front; the crowbar takes it down
        h.setBlock(new BlockPos(14, 2, 12), Blocks.STONE);
        player.moveTo(h.absoluteVec(new Vec3(14.5, 2, 9.5)).x, h.absoluteVec(new Vec3(14.5, 2, 9.5)).y, h.absoluteVec(new Vec3(14.5, 2, 9.5)).z, 0.0F, 0.0F);
        Item signItem = AflItems.TUNNEL_FIXTURE_ITEMS.get(TunnelFixtureBlock.Kind.EGRESS_SIGN).get();
        h.assertTrue(place(h, player, signItem, new Vec3(14.5, 2.5, 12.0), Direction.NORTH) == InteractionResult.CONSUME, "a wall sign goes on a stone block");
        BlockPos stone = h.absolutePos(new BlockPos(14, 2, 12));
        h.assertTrue(level.getBlockState(stone).is(Blocks.STONE), "the stone wall stays");
        TunnelFixtureBlockEntity.Entry sign = entryNear(h, h.absoluteVec(new Vec3(14.5, 2.5, 12.0)));
        h.assertTrue(sign != null && sign.kind == TunnelFixtureBlock.Kind.EGRESS_SIGN, "the sign is stored in front of the wall");
        BlockPos signCell = null;
        for (long c : sign.part().cells()) if (holds(level, c, sign)) { signCell = BlockPos.of(c); break; }
        h.assertTrue(signCell != null && signCell.getZ() == stone.getZ() - 1, "the sign's cell is the one in front of the wall");
        h.assertTrue(!level.getBlockState(signCell).getShape(level, signCell).isEmpty(), "the sign has an outline to aim at");
        h.assertTrue(level.getBlockState(signCell).getCollisionShape(level, signCell).isEmpty(), "a thin sign does not collide");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AflItems.CROWBAR.get()));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, h.absoluteVec(new Vec3(14.5, 2.65, 12.0)));
        clearDrops(h);
        h.assertTrue(level.getBlockState(signCell).onDestroyedByPlayer(level, signCell, player, true, level.getFluidState(signCell)), "sign taken down");
        for (long c : sign.part().cells()) h.assertTrue(!holds(level, c, sign), "the sign left every cell");
        h.assertTrue(level.getBlockState(stone).is(Blocks.STONE), "the stone wall is still there");
        Map<Item, Integer> signDrops = drops(h);
        h.assertTrue(signDrops.equals(Map.of(signItem, 1)), "one sign dropped, was " + signDrops);
        clearDrops(h);

        // the jet fan, hung over road_structure it took over: crowbar strips it for materials, the structure comes back
        TunnelMesh.Placement zero = new TunnelMesh.Placement("jet_fan", TunnelFixtureBlock.Kind.JET_FAN.defaultBone(), 0, 0, 0,
                new double[]{1, 0, 0}, new double[]{0, 1, 0}, new double[]{0, 0, 1});
        double[] fb = TunnelFixtures.Part.of(zero).bounds();
        Vec3 centre = h.absoluteVec(new Vec3(SX / 2.0, 7.0, 17.0));
        TunnelMesh.Placement fan = new TunnelMesh.Placement("jet_fan", zero.bone(), centre.x - (fb[0] + fb[3]) / 2,
                centre.y - (fb[1] + fb[4]) / 2, centre.z - (fb[2] + fb[5]) / 2, zero.ex(), zero.ey(), zero.ez());
        TunnelFixtureBlockEntity.Entry f = new TunnelFixtureBlockEntity.Entry(TunnelFixtureBlock.Kind.JET_FAN, fan);
        long[] fanCells = f.part().cells();
        BlockPos master = BlockPos.of(fanCells[0]);
        var fixture = AflBlocks.TUNNEL_FIXTURES.get(TunnelFixtureBlock.Kind.JET_FAN).get().defaultBlockState();
        for (long c : fanCells) {
            BlockPos q = BlockPos.of(c);
            level.setBlock(q, fixture, 3);
            if (level.getBlockEntity(q) instanceof TunnelFixtureBlockEntity be) be.set(List.of(f), q.equals(master) ? 3 : 0);
        }
        for (long c : fanCells) h.assertTrue(holds(level, c, f), "the fan in all its cells");
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(master));
        h.assertTrue(Math.abs(level.getBlockState(master).getDestroyProgress(player, level, master) - 1.0F / 300.0F) < 1e-6, "the fan takes 15 s");
        clearDrops(h);
        h.assertTrue(level.getBlockState(master).onDestroyedByPlayer(level, master, player, true, level.getFluidState(master)), "fan taken down");
        h.assertTrue(level.getBlockState(master).is(AflBlocks.ROAD_STRUCTURE.get())
                && level.getBlockState(master).getValue(RoadStructureBlock.LAYERS) == 3, "the structure it took over is back");
        for (long c : fanCells) if (c != fanCells[0]) h.assertTrue(level.getBlockState(BlockPos.of(c)).isAir(), "the fan left every cell");
        Map<Item, Integer> materials = drops(h);
        Map<Item, Integer> want = Map.of(AflItems.STEEL_PLATE.get(), 8, AflItems.STEEL_SCRAP.get(), 12,
                AflItems.INSULATED_COPPER_WIRE.get(), 6, AflItems.POWER_CABLE.get(), 2, AflItems.STEEL_CABLE.get(), 2);
        h.assertTrue(materials.equals(want), "fan materials, was " + materials);

        ApocalypseFirstLight.LOGGER.info("[AFL TUNNEL FIXTURE TEST] PASS Survival rail placement from the item ({} + {} cells, {} shared), wall face refused, "
                + "bare hand / pickaxe 0, crowbar 2 s, aimed rail taken down from the shared cell with one rail item, other rail kept, "
                + "command removal clears all cells without drops, egress sign on a stone wall placed and taken down (stone kept), jet fan ({} cells) 15 s, materials {}, structure layers restored",
                aCells.size(), bCells.size(), shared.size(), fanCells.length, materials);
        h.succeed();
    }

    /** Use the item on a floor/wall face at a test-relative hit point. */
    private static InteractionResult place(GameTestHelper h, Player player, Item item, Vec3 rel, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        Vec3 hit = h.absoluteVec(rel);
        BlockPos on = BlockPos.containing(hit.x - face.getStepX() * 0.01, hit.y - face.getStepY() * 0.01, hit.z - face.getStepZ() * 0.01);
        return item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, on, false)));
    }

    private static TunnelFixtureBlockEntity.Entry entryNear(GameTestHelper h, Vec3 at) {
        for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos q = BlockPos.containing(at).offset(dx, dy, dz);
            if (h.getLevel().getBlockEntity(q) instanceof TunnelFixtureBlockEntity be) {
                for (TunnelFixtureBlockEntity.Entry o : be.entries()) if (Math.abs(o.placement.x() - at.x) < 0.01) return o;
            }
        }
        return null;
    }

    private static Set<Long> cells(TunnelFixtureBlockEntity.Entry e) {
        Set<Long> out = new HashSet<>();
        for (long c : e.part().cells()) out.add(c);
        return out;
    }

    private static boolean holds(ServerLevel level, long cell, TunnelFixtureBlockEntity.Entry e) {
        return level.getBlockEntity(BlockPos.of(cell)) instanceof TunnelFixtureBlockEntity be && be.holds(e);
    }

    private static AABB area(GameTestHelper h) {
        return new AABB(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(SX, SY, SZ))).inflate(2);
    }

    private static void clearDrops(GameTestHelper h) {
        h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h)).forEach(ItemEntity::discard);
    }

    private static Map<Item, Integer> drops(GameTestHelper h) {
        Map<Item, Integer> out = new HashMap<>();
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h)))
            if (e.isAlive()) out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
        return out;
    }
}
