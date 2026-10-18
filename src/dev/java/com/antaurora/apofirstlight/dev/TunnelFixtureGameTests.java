package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity;
import com.antaurora.apofirstlight.meshhit.MeshBlockHitResult;
import com.antaurora.apofirstlight.meshhit.MeshHitClip;
import com.antaurora.apofirstlight.meshhit.MeshHitModels;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.worldgen.network.road.GuideSigns;
import com.antaurora.apofirstlight.worldgen.network.road.RoadSigns;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 * materials with the road_structure it took over given back. C3.3b: the road signs (road_&lt;mesh&gt;) placed from their
 * items, a side road sign's post collision, crowbar time and its drop with its variant; their hit meshes (the crosshair on
 * the drawn faces, passing beside the post, the whole sign's outline from any cell).
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

        // C3.3b road signs: a side road warning (bone l) placed on the floor from its item, collides on its post, the crowbar
        // takes it down in 3 s and it drops itself with its variant; STOP and curve signs place and take their cells
        clearDrops(h);
        player.moveTo(h.absoluteVec(new Vec3(4.5, 2, 14.5)).x, h.absoluteVec(new Vec3(4.5, 2, 14.5)).y, h.absoluteVec(new Vec3(4.5, 2, 14.5)).z, 0.0F, 0.0F);
        Item sideItem = AflItems.TUNNEL_FIXTURE_ITEMS.get(TunnelFixtureBlock.Kind.SIDE_ROAD_SIGN).get();
        ItemStack left = new ItemStack(sideItem);
        left.getOrCreateTag().putString("Bone", "l");
        h.assertTrue(place(h, player, left, new Vec3(4.5, 2, 18.5), Direction.UP) == InteractionResult.CONSUME, "side road sign placed");
        TunnelFixtureBlockEntity.Entry side = entryNear(h, h.absoluteVec(new Vec3(4.5, 2, 18.5)));
        h.assertTrue(side != null && side.kind == TunnelFixtureBlock.Kind.SIDE_ROAD_SIGN && "l".equals(side.placement.bone()), "side road sign stored with bone l");
        Set<Long> sideCells = cells(side);
        h.assertTrue(sideCells.size() >= 3, "the sign reaches its post's and its face's cells, was " + sideCells.size());
        for (long c : sideCells) h.assertTrue(holds(level, c, side), "the sign in all its cells");
        BlockPos postCell = BlockPos.containing(h.absoluteVec(new Vec3(4.5, 2.2, 18.5)));
        h.assertTrue(!level.getBlockState(postCell).getCollisionShape(level, postCell).isEmpty(), "the sign's post collides");
        // aim at the diamond, 0.4 m under the part's top (above the post)
        double[] sb = side.part().bounds();
        Vec3 face = new Vec3((sb[0] + sb[3]) / 2, sb[4] - 0.4, (sb[2] + sb[5]) / 2);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, face);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AflItems.CROWBAR.get()));
        BlockPos faceCell = BlockPos.containing(face);
        h.assertTrue(level.getBlockState(faceCell).getBlock() instanceof TunnelFixtureBlock, "the face's cell is a fixture");
        h.assertTrue(Math.abs(level.getBlockState(faceCell).getDestroyProgress(player, level, faceCell) - 1.0F / 60.0F) < 1e-6, "the crowbar takes a sign down in 3 s");
        h.assertTrue(level.getBlockState(faceCell).onDestroyedByPlayer(level, faceCell, player, true, level.getFluidState(faceCell)), "sign taken down");
        for (long c : sideCells) h.assertTrue(!holds(level, c, side), "the sign left every cell");
        List<ItemEntity> signDrop = h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h));
        h.assertTrue(signDrop.size() == 1 && signDrop.get(0).getItem().is(sideItem) && signDrop.get(0).getItem().getTag() != null
                && "l".equals(signDrop.get(0).getItem().getTag().getString("Bone")), "the sign dropped itself with bone l");
        clearDrops(h);
        player.moveTo(h.absoluteVec(new Vec3(12.5, 2, 14.5)).x, h.absoluteVec(new Vec3(12.5, 2, 14.5)).y, h.absoluteVec(new Vec3(12.5, 2, 14.5)).z, 0.0F, 0.0F);
        Item stopItem = AflItems.TUNNEL_FIXTURE_ITEMS.get(TunnelFixtureBlock.Kind.STOP_SIGN).get();
        Item curveItem = AflItems.TUNNEL_FIXTURE_ITEMS.get(TunnelFixtureBlock.Kind.CURVE_SIGN).get();
        h.assertTrue(place(h, player, stopItem, new Vec3(12.5, 2, 18.5), Direction.UP) == InteractionResult.CONSUME, "STOP sign placed");
        h.assertTrue(place(h, player, curveItem, new Vec3(18.5, 2, 18.5), Direction.UP) == InteractionResult.CONSUME, "curve sign placed");
        TunnelFixtureBlockEntity.Entry stop = entryNear(h, h.absoluteVec(new Vec3(12.5, 2, 18.5)));
        TunnelFixtureBlockEntity.Entry curve = entryNear(h, h.absoluteVec(new Vec3(18.5, 2, 18.5)));
        h.assertTrue(stop != null && stop.kind == TunnelFixtureBlock.Kind.STOP_SIGN, "STOP sign stored");
        h.assertTrue(curve != null && curve.kind == TunnelFixtureBlock.Kind.CURVE_SIGN && "r".equals(curve.placement.bone()), "curve sign stored with its default bone r");
        for (long c : cells(stop)) h.assertTrue(holds(level, c, stop), "STOP in all its cells");
        for (long c : cells(curve)) h.assertTrue(holds(level, c, curve), "curve sign in all its cells");
        h.assertTrue(AflBlocks.TUNNEL_FIXTURES.get(TunnelFixtureBlock.Kind.STOP_SIGN).getId().getPath().equals("road_stop_sign"), "road sign ids are road_<mesh>");
        // C3.3b group 2: the ramp terminal signs place from their items and take their cells
        Item dneItem = AflItems.TUNNEL_FIXTURE_ITEMS.get(TunnelFixtureBlock.Kind.DO_NOT_ENTER_SIGN).get();
        Item wwItem = AflItems.TUNNEL_FIXTURE_ITEMS.get(TunnelFixtureBlock.Kind.WRONG_WAY_SIGN).get();
        h.assertTrue(place(h, player, dneItem, new Vec3(8.5, 2, 21.5), Direction.UP) == InteractionResult.CONSUME, "DO NOT ENTER sign placed");
        h.assertTrue(place(h, player, wwItem, new Vec3(16.5, 2, 21.5), Direction.UP) == InteractionResult.CONSUME, "WRONG WAY sign placed");
        TunnelFixtureBlockEntity.Entry dne = entryNear(h, h.absoluteVec(new Vec3(8.5, 2, 21.5)));
        TunnelFixtureBlockEntity.Entry ww = entryNear(h, h.absoluteVec(new Vec3(16.5, 2, 21.5)));
        h.assertTrue(dne != null && dne.kind == TunnelFixtureBlock.Kind.DO_NOT_ENTER_SIGN, "DO NOT ENTER sign stored");
        h.assertTrue(ww != null && ww.kind == TunnelFixtureBlock.Kind.WRONG_WAY_SIGN, "WRONG WAY sign stored");
        for (long c : cells(dne)) h.assertTrue(holds(level, c, dne), "DO NOT ENTER in all its cells");
        for (long c : cells(ww)) h.assertTrue(holds(level, c, ww), "WRONG WAY in all its cells");
        h.assertTrue(AflBlocks.TUNNEL_FIXTURES.get(TunnelFixtureBlock.Kind.WRONG_WAY_SIGN).getId().getPath().equals("road_wrong_way_sign"), "ramp sign ids");
        // Survival: bare hands do not move them, the crowbar takes each down in 3 s and it drops itself
        int rampSignCells = 0;
        for (TunnelFixtureBlockEntity.Entry e : List.of(dne, ww)) {
            clearDrops(h);
            Set<Long> ec = cells(e);
            rampSignCells += ec.size();
            double[] eb = e.part().bounds();
            Vec3 ef = new Vec3((eb[0] + eb[3]) / 2, eb[4] - 0.3, (eb[2] + eb[5]) / 2);
            player.moveTo(ef.x, ef.y - player.getEyeHeight(), ef.z - 2.0, 0.0F, 0.0F);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, ef);
            BlockPos efCell = BlockPos.containing(ef);
            h.assertTrue(level.getBlockState(efCell).getBlock() instanceof TunnelFixtureBlock, e.kind + ": the face's cell is a fixture");
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            h.assertTrue(level.getBlockState(efCell).getDestroyProgress(player, level, efCell) == 0.0F, e.kind + ": bare hands do not move it");
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AflItems.CROWBAR.get()));
            h.assertTrue(Math.abs(level.getBlockState(efCell).getDestroyProgress(player, level, efCell) - 1.0F / 60.0F) < 1e-6, e.kind + ": the crowbar takes it down in 3 s");
            h.assertTrue(level.getBlockState(efCell).onDestroyedByPlayer(level, efCell, player, true, level.getFluidState(efCell)), e.kind + " taken down");
            for (long c : ec) h.assertTrue(!holds(level, c, e), e.kind + " left every cell");
            List<ItemEntity> drop = h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h));
            Item own = AflItems.TUNNEL_FIXTURE_ITEMS.get(e.kind).get();
            h.assertTrue(drop.size() == 1 && drop.get(0).getItem().is(own) && drop.get(0).getItem().getCount() == 1, e.kind + " dropped itself, was " + drop);
        }
        clearDrops(h);

        // C3.3b group 2 package B: a guide sign's item (cut to a slope where it stood) stands level again; it places from its
        // item facing the player, keeps its legend, collides on its posts; the crowbar takes it down in 8 s, it drops itself
        Item guideItem = AflItems.TUNNEL_FIXTURE_ITEMS.get(TunnelFixtureBlock.Kind.GUIDE_SIGN).get();
        String sloped = "adv|4|R61|S|Redfield|1|0.00,-0.40";
        ItemStack guideStack = TunnelFixtureBlock.stack(TunnelFixtureBlock.Kind.GUIDE_SIGN, sloped);
        String level0 = "adv|4|R61|S|Redfield|1|0.00,0.00";
        h.assertTrue(guideStack.getTag() != null && level0.equals(guideStack.getTag().getString("Bone")), "a guide sign's item stands level, was " + guideStack.getTag());
        h.assertTrue("EXIT 4 | RT 61 SOUTH | Redfield | 1 MILE".equals(GuideSigns.describe(sloped)), "the legend reads " + GuideSigns.describe(sloped));
        // C3.3b group 3: a split sign carries two routes (one row each), its up-left arrow against the panel's left margin
        String split = "split||F40+F65|E+S|Bellhaven|ul|0,0";
        h.assertTrue("F-40 EAST | F-65 SOUTH | Bellhaven".equals(GuideSigns.describe(split)), "the split sign reads " + GuideSigns.describe(split));
        GuideSigns.Layout sl = GuideSigns.layout(split);
        GuideSigns.Piece splitArrow = null;
        int shields = 0;
        for (GuideSigns.Piece q : sl.pieces()) {
            if ("arrow_ul".equals(q.tile())) splitArrow = q;
            if ("F40".equals(q.tile()) || "F65".equals(q.tile())) shields++;
        }
        h.assertTrue(shields == 2, "the split sign shows both shields, was " + shields);
        h.assertTrue(splitArrow != null && Math.abs(splitArrow.x0() - sl.panel()[0] - GuideSigns.MARGIN_X) < 1e-6, "the split sign's arrow on its left margin");
        Vec3 gStand = h.absoluteVec(new Vec3(12.5, 2, 1.0));
        player.moveTo(gStand.x, gStand.y, gStand.z, 0.0F, 0.0F);
        h.assertTrue(place(h, player, guideStack, new Vec3(12.5, 2, 5.5), Direction.UP) == InteractionResult.CONSUME, "guide sign placed");
        TunnelFixtureBlockEntity.Entry guide = entryNear(h, h.absoluteVec(new Vec3(12.5, 2, 5.5)));
        h.assertTrue(guide != null && guide.kind == TunnelFixtureBlock.Kind.GUIDE_SIGN && level0.equals(guide.placement.bone()), "guide sign stored with its legend");
        h.assertTrue(guide.placement.ez()[2] < -0.99, "the guide sign faces the player");
        Set<Long> guideCells = cells(guide);
        for (long c : guideCells) h.assertTrue(holds(level, c, guide), "the guide sign in all its cells");
        GuideSigns.Layout gl = GuideSigns.layout(level0);
        Vec3 gPost = at(guide.placement, gl.posts()[0], 1.0, -0.13);
        BlockPos gPostCell = BlockPos.containing(gPost);
        h.assertTrue(!level.getBlockState(gPostCell).getCollisionShape(level, gPostCell).isEmpty(), "the guide sign's post collides");
        h.assertTrue(AflBlocks.TUNNEL_FIXTURES.get(TunnelFixtureBlock.Kind.GUIDE_SIGN).getId().getPath().equals("road_guide_sign"), "guide sign id");
        Vec3 gFace = at(guide.placement, 0, (gl.panel()[1] + gl.panel()[3]) / 2, 0);
        player.moveTo(gFace.x, gFace.y - player.getEyeHeight(), gFace.z - 3.0, 0.0F, 0.0F);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, gFace);
        BlockPos gFaceCell = BlockPos.containing(at(guide.placement, 0, (gl.panel()[1] + gl.panel()[3]) / 2, -0.05));
        h.assertTrue(level.getBlockState(gFaceCell).getBlock() instanceof TunnelFixtureBlock, "the panel's cell is a fixture");
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        h.assertTrue(level.getBlockState(gFaceCell).getDestroyProgress(player, level, gFaceCell) == 0.0F, "bare hands do not move a guide sign");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AflItems.CROWBAR.get()));
        h.assertTrue(Math.abs(level.getBlockState(gFaceCell).getDestroyProgress(player, level, gFaceCell) - 1.0F / 160.0F) < 1e-6, "the crowbar takes a guide sign down in 8 s");
        clearDrops(h);
        h.assertTrue(level.getBlockState(gFaceCell).onDestroyedByPlayer(level, gFaceCell, player, true, level.getFluidState(gFaceCell)), "guide sign taken down");
        for (long c : guideCells) h.assertTrue(!holds(level, c, guide), "the guide sign left every cell");
        List<ItemEntity> guideDrop = h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h));
        h.assertTrue(guideDrop.size() == 1 && guideDrop.get(0).getItem().is(guideItem) && guideDrop.get(0).getItem().getTag() != null
                && level0.equals(guideDrop.get(0).getItem().getTag().getString("Bone")), "the guide sign dropped itself with its legend, was " + guideDrop);
        clearDrops(h);

        // hit meshes (meshhit/TunnelFixtureHits): a look at the plaque stops on its face; one beside the post, inside the old
        // 1/8 column boxes, passes; the outline is the sign's whole mesh (its extent in the part's frame), from any cell
        String meshHits = meshHits(h, player, curve);

        ApocalypseFirstLight.LOGGER.info("[AFL TUNNEL FIXTURE TEST] PASS Survival rail placement from the item ({} + {} cells, {} shared), wall face refused, "
                + "bare hand / pickaxe 0, crowbar 2 s, aimed rail taken down from the shared cell with one rail item, other rail kept, "
                + "command removal clears all cells without drops, egress sign on a stone wall placed and taken down (stone kept), jet fan ({} cells) 15 s, materials {}, structure layers restored; "
                + "road signs: side road (bone l, {} cells) placed, post collides, crowbar 3 s, dropped with bone l; STOP ({} cells) and curve ({} cells) placed; "
                + "DO NOT ENTER and WRONG WAY ({} cells) placed, bare hand 0, crowbar 3 s, each dropped itself; "
                + "guide sign: item level, placed facing the player ({} cells), legend kept, post collides, bare hand 0, crowbar 8 s, dropped with its legend; {}",
                aCells.size(), bCells.size(), shared.size(), fanCells.length, materials, sideCells.size(), cells(stop).size(), cells(curve).size(), rampSignCells,
                guideCells.size(), meshHits);
        h.succeed();
    }

    /** The curve sign's hit mesh: the plaque hit on its face, a look beside the post passing, the outline's extent. */
    private static String meshHits(GameTestHelper h, Player player, TunnelFixtureBlockEntity.Entry curve) {
        ServerLevel level = h.getLevel();
        TunnelMesh.Placement p = curve.placement;
        // the plaque's centre on its front (part frame: x right, y up, z out of the face; the face's front 3 mm out)
        Vec3 plaque = at(p, 0, RoadSigns.BOTTOM + RoadSigns.PLAQUE / 2, 0.003), front = at(p, 0, 0, 2).subtract(at(p, 0, 0, 0));
        BlockHitResult hit = MeshHitClip.clip(level, new ClipContext(plaque.add(front), plaque.subtract(front), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        h.assertTrue(hit instanceof MeshBlockHitResult && hit.getLocation().distanceTo(plaque) < 0.005,
                "a look at the plaque stops on its face, was " + hit.getType() + " at " + hit.getLocation().distanceTo(plaque));
        // 6.5 cm right of the post's side at 0.8 m, inside its old box (+-0.12 m): passes
        Vec3 beside = at(p, 0.09, 0.8, -0.04);
        BlockHitResult past = MeshHitClip.clip(level, new ClipContext(beside.add(front), beside.subtract(front), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        h.assertTrue(past.getType() == HitResult.Type.MISS || !(level.getBlockState(past.getBlockPos()).getBlock() instanceof TunnelFixtureBlock),
                "a look beside the post passes, was " + past.getType() + " " + past.getBlockPos());
        // the outline from the plaque's cell and from the post's foot cell: the whole sign
        Vec3 eye = plaque.add(front), dir = front.reverse().normalize();
        double[][] ext = new double[2][];
        BlockPos[] from = {hit.getBlockPos(), BlockPos.containing(at(p, 0, 0.2, -0.04))};
        int edges = 0;
        for (int k = 0; k < 2; k++) {
            BlockPos cell = from[k];
            h.assertTrue(level.getBlockState(cell).getBlock() instanceof TunnelFixtureBlock, "outline cell " + k + " is the sign's");
            MeshHitModels.Shape shape = MeshHitModels.outline(level, cell, level.getBlockState(cell), eye, dir);
            h.assertTrue(shape != null, "the sign has a mesh outline from cell " + k);
            double[] e = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
            int[] n = {0};
            shape.forEachEdge((x0, y0, z0, x1, y1, z1) -> {
                n[0]++;
                for (double[] q : new double[][]{{x0, y0, z0}, {x1, y1, z1}}) {
                    double[] l = local(p, cell.getX() + q[0], cell.getY() + q[1], cell.getZ() + q[2]);
                    for (int i = 0; i < 3; i++) {
                        e[i] = Math.min(e[i], l[i]);
                        e[i + 3] = Math.max(e[i + 3], l[i]);
                    }
                }
            });
            ext[k] = e;
            edges = n[0];
        }
        double half = RoadSigns.DIAMOND / 2, top = RoadSigns.CURVE_BOTTOM + RoadSigns.DIAMOND;
        for (double[] e : ext)
            h.assertTrue(Math.abs(e[0] + half) < 0.01 && Math.abs(e[3] - half) < 0.01 && Math.abs(e[1] + 0.3) < 0.01 && Math.abs(e[4] - top) < 0.01
                    && Math.abs(e[2] + 0.065) < 0.005 && Math.abs(e[5] - 0.003) < 0.005, "the outline spans the sign's mesh, was " + java.util.Arrays.toString(e));
        return String.format(java.util.Locale.ROOT, "hit meshes: plaque hit on its face, beside the post passes, outline %d edges spanning the mesh from 2 cells", edges);
    }

    /** The world point of part-local (x, y, z). */
    private static Vec3 at(TunnelMesh.Placement p, double x, double y, double z) {
        return new Vec3(p.x() + p.ex()[0] * x + p.ey()[0] * y + p.ez()[0] * z, p.y() + p.ex()[1] * x + p.ey()[1] * y + p.ez()[1] * z,
                p.z() + p.ex()[2] * x + p.ey()[2] * y + p.ez()[2] * z);
    }

    /** The part-local point of world (x, y, z) (orthonormal axes). */
    private static double[] local(TunnelMesh.Placement p, double x, double y, double z) {
        double dx = x - p.x(), dy = y - p.y(), dz = z - p.z();
        double[][] axes = {p.ex(), p.ey(), p.ez()};
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) out[i] = dx * axes[i][0] + dy * axes[i][1] + dz * axes[i][2];
        return out;
    }

    /** Use the item on a floor/wall face at a test-relative hit point. */
    private static InteractionResult place(GameTestHelper h, Player player, Item item, Vec3 rel, Direction face) {
        return place(h, player, new ItemStack(item), rel, face);
    }

    /** Use the stack (its variant in its tag) on a floor/wall face at a test-relative hit point. */
    private static InteractionResult place(GameTestHelper h, Player player, ItemStack stack, Vec3 rel, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = h.absoluteVec(rel);
        BlockPos on = BlockPos.containing(hit.x - face.getStepX() * 0.01, hit.y - face.getStepY() * 0.01, hit.z - face.getStepZ() * 0.01);
        return stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, on, false)));
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
