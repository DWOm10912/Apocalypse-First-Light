package com.antaurora.apofirstlight.dev;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.cabin.CabinBlocks;
import com.antaurora.apofirstlight.wiring.BuildingWiring;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringSupply;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeLayout;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeRuntime;
import com.antaurora.apofirstlight.worldgen.home.StartAreaPlanner;
import com.antaurora.apofirstlight.worldgen.network.contract.StructureReservation;
import com.antaurora.apofirstlight.worldgen.structure.AflTemplatePlacer;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanStore;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainProfile;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * M4 round 1 (docs/worldgen/forest_home_b_m4_placement.md):
 * <ul>
 * <li>planner: the start-area planner (G2) on the federal surface of several seeds: sites found, time, every site inside
 * the start region, sites at least StartAreaPlanner.SPACING apart, the ground near the plan.</li>
 * <li>placement: a reservation (rotation 1) activated far from the test area before its chunks exist, then the chunks
 * generated in a shuffled order through real world generation (the chunk pass mixin, the guard): every block of the
 * template box against a reference copy placed directly (AflTemplatePlacer) in the sky; the block entities; the panel's
 * packed wiring unpacked on both copies (wired cells, devices); the plot filled under its floor; the graded ring's inner
 * edge at the yard's grass; trees excluded round the plot.</li>
 * </ul>
 * Run with {@code src/dev/forest-home-m4-gametest.init.gradle}.
 */
@GameTestHolder("afl_m4_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class ForestHomeM4GameTests {
    private static final String NAMESPACE = "afl_m4_tests", TAG = "[AFL FOREST HOME M4 TEST] ";
    private static final long[] SEEDS = {-295378578869149513L, 1L, 42L, 20261015L};

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = TagParser.parseTag("{size:[4,4,4],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new ListTag();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) {
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
        return List.of(
                new TestFunction("forest_home_m4_planner", NAMESPACE + ":planner", NAMESPACE + ":empty", 100, 0L, true, ForestHomeM4GameTests::planner),
                new TestFunction("forest_home_m4_placement", NAMESPACE + ":placement", NAMESPACE + ":empty", 400, 0L, true, ForestHomeM4GameTests::placement));
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static void planner(GameTestHelper helper) {
        StringBuilder all = new StringBuilder();
        for (long seed : SEEDS) {
            TerrainPlanSurface s = TerrainPlanStore.get(TerrainProfile.FEDERAL, seed);
            check(s.federal(), "seed " + seed + ": not a federal surface");
            FederalMap.Start start = FederalMap.forSeed(seed).start();
            StartAreaPlanner.Result r = StartAreaPlanner.plan(new StartAreaPlanner.Context(seed, s, start.x(), start.z(), start.radius(), List.of(), List.of()));
            ApocalypseFirstLight.LOGGER.info(TAG + "seed {} start {} {} r {}: {}", seed, Math.round(start.x()), Math.round(start.z()),
                    Math.round(start.radius()), ForestHomeRuntime.report(r));
            check(!r.sites().isEmpty(), "seed " + seed + ": no site in the start region");
            check(r.sites().size() == r.reservations().size(), "seed " + seed + ": sites and reservations differ");
            for (int i = 0; i < r.sites().size(); i++) {
                StartAreaPlanner.Site a = r.sites().get(i);
                check(Math.hypot(a.x() - start.x(), a.z() - start.z()) <= start.radius(), "seed " + seed + " site " + i + " outside the start region");
                double plan = s.heightAt(a.x() + 0.5, a.z() + 0.5);
                check(Math.abs(a.groundY() - plan) <= 3, "seed " + seed + " site " + i + ": ground " + a.groundY() + " far from the plan " + plan);
                StructureReservation res = r.reservations().get(i);
                check(ForestHomeLayout.groundY(res) == a.groundY(), "seed " + seed + " site " + i + ": reservation ground");
                for (int j = 0; j < i; j++) {
                    StartAreaPlanner.Site b = r.sites().get(j);
                    check(Math.hypot(a.x() - b.x(), a.z() - b.z()) >= StartAreaPlanner.SPACING - 1e-6, "seed " + seed + " sites " + j + "/" + i + " too close");
                }
            }
            all.append(String.format(Locale.ROOT, " seed %d: %d sites (%d woodland, %d riparian) %d ms;", seed, r.sites().size(), r.woodland(),
                    r.riparian(), r.millis()));
        }
        ApocalypseFirstLight.LOGGER.info(TAG + "PASS planner:{}", all);
        helper.succeed();
    }

    /**
     * A dry, flat site far from the test area (no chunk generated there): the plan's natural spawn search round (3000, 3000)
     * when the test world has a Terrain V2 plan, else the generator's base heights; the ground Y from the base height.
     */
    private static int[] site(ServerLevel level) {
        var gen = level.getChunkSource().getGenerator();
        var rs = level.getChunkSource().randomState();
        TerrainPlanSurface plan = ForestHomeRuntime.surface(level);
        int[] xz = plan == null ? null : plan.naturalSpawn(3000, 3000, 3000, 12);
        if (xz != null) return new int[]{xz[0], gen.getBaseHeight(xz[0], xz[1], Heightmap.Types.OCEAN_FLOOR_WG, level, rs), xz[1]};
        for (int i = 0; i < 400; i++) {
            int x = 6000 + (i % 20) * 96, z = 6000 + (i / 20) * 96;
            boolean ok = true;
            int ground = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
            for (int dx = -32; dx <= 32 && ok; dx += 16) for (int dz = -32; dz <= 32 && ok; dz += 16) {
                int floor = gen.getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                int surf = gen.getBaseHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
                ok = surf == floor && floor > 66 && Math.abs(floor - ground) <= 6;
            }
            if (ok) return new int[]{x, ground, z};
        }
        throw new GameTestAssertException("no dry site for the placement test");
    }

    private static void placement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try {
            placementChecks(level);
        } finally {
            ForestHomeRuntime.activate(level, null);
        }
        helper.succeed();
    }

    private static void placementChecks(ServerLevel level) {
        int[] s = site(level);
        int rot = 1;
        int[] o = ForestHomeLayout.origin(s[0] + 0.5, s[2] + 0.5, rot);
        StructureReservation r = ForestHomeLayout.reservation(o[0], s[1], o[1], rot);
        ForestHomeRuntime.Active a = ForestHomeRuntime.activate(level, r);
        check(a != null, "the reservation did not activate (template apocalypse_firstlight:forest_home_b missing?)");
        check(ForestHomeRuntime.excludesTree(level.getSeed(), s[0], s[2], 12), "no tree exclusion at the pivot");
        check(!ForestHomeRuntime.excludesTree(level.getSeed(), s[0] + 60, s[2] + 60, 12), "tree exclusion 60 m away");

        // generate every chunk of the keep area (and one more ring) in a shuffled order
        List<long[]> chunks = new ArrayList<>();
        for (int cx = (a.minX() >> 4) - 1; cx <= (a.maxX() >> 4) + 1; cx++)
            for (int cz = (a.minZ() >> 4) - 1; cz <= (a.maxZ() >> 4) + 1; cz++) chunks.add(new long[]{cx, cz});
        Collections.shuffle(chunks, new Random(7));
        long t0 = System.nanoTime();
        for (long[] c : chunks) level.getChunk((int) c[0], (int) c[1]);
        long genMs = (System.nanoTime() - t0) / 1_000_000;

        // the reference: the same template, same rotation, placed directly in the sky 400 blocks east
        BlockPos ref = new BlockPos(a.origin().getX() + 400, 240, a.origin().getZ());
        check(AflTemplatePlacer.place(level, a.template(), ref, a.rotation(), Mirror.NONE, false), "the reference placed nothing");
        BoundingBox box = a.box();
        int diffs = 0, blocks = 0, entities = 0;
        List<String> shown = new ArrayList<>();
        BlockPos panel = null, refPanel = null;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockPos q = ref.offset(p.getX() - a.origin().getX(), p.getY() - a.origin().getY(), p.getZ() - a.origin().getZ());
            BlockState w = level.getBlockState(p), e = level.getBlockState(q);
            blocks++;
            // the woven wire fence's posts follow world coordinates ((x + z) mod 3) when its shape updates: the reference
            // recomputed them, the generated copy keeps the template's (known shape); only that flag may differ
            if (w.hasProperty(CabinBlocks.WovenWireFence.POST) && w.getBlock() == e.getBlock())
                w = w.setValue(CabinBlocks.WovenWireFence.POST, e.getValue(CabinBlocks.WovenWireFence.POST));
            if (!w.equals(e)) {
                diffs++;
                if (shown.size() < 12) shown.add(p.toShortString() + " " + w + " (reference " + e + ")");
            }
            BlockEntity wb = level.getBlockEntity(p), eb = level.getBlockEntity(q);
            if (eb != null) {
                entities++;
                if (wb == null || wb.getType() != eb.getType()) {
                    diffs++;
                    if (shown.size() < 12) shown.add(p.toShortString() + " block entity " + wb + " (reference " + eb.getType() + ")");
                }
            }
            if (wb instanceof DistributionPanelBlockEntity) panel = p.immutable();
            if (eb instanceof DistributionPanelBlockEntity) refPanel = q.immutable();
        }
        check(diffs == 0, diffs + " differences against the reference: " + String.join("; ", shown));
        check(panel != null && refPanel != null, "no distribution panel in the house");

        // the packed wiring: unpacked by the panel's tick on both copies
        int[] wired = wiring(level, panel, box, BlockPos.ZERO);
        BlockPos shift = ref.subtract(a.origin());
        int[] refWired = wiring(level, refPanel, box, shift);
        check(wired[0] > 0 && wired[0] == refWired[0] && wired[1] == refWired[1],
                "wiring after generation " + wired[0] + " cells / " + wired[1] + " devices, reference " + refWired[0] + " / " + refWired[1]);

        // the grading: the plot is solid under its floor; the ring's inner edge sits at the yard's grass
        int y0 = r.y(), grass = ForestHomeLayout.groundY(r) - 1, holes = 0, edge = 0, edgeOff = 0, step = 0;
        for (int x = a.minX(); x <= a.maxX(); x++) for (int z = a.minZ(); z <= a.maxZ(); z++) {
            double d = a.plot().distance(x + 0.5, z + 0.5);
            if (d <= 0) {
                if (level.getBlockState(new BlockPos(x, y0 - 1, z)).isAir()) holes++;
            } else if (d <= 1) {
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                edge++;
                if (Math.abs(top - grass) > 1) edgeOff++;
            }
            if (d > 0 && d <= ForestHomeLayout.GRADE_MARGIN) {
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z), east = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x + 1, z),
                        south = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z + 1);
                step = Math.max(step, Math.max(Math.abs(top - east), Math.abs(top - south)));
            }
        }
        check(holes == 0, holes + " plot columns open under the floor");
        check(edgeOff == 0, edgeOff + " of " + edge + " ring edge columns off the yard grass (" + grass + ")");
        ApocalypseFirstLight.LOGGER.info(TAG + "PASS placement: site {} {} {} rotation {} origin {} box {}; {} chunks generated in {} ms (shuffled); "
                        + "{} blocks and {} block entities match the reference; wiring {} cells {} devices (reference {} / {}); "
                        + "plot solid under the floor; {} ring edge columns at grass {}; largest ring step {}",
                s[0], s[1], s[2], rot, a.origin().toShortString(), box, chunks.size(), genMs, blocks, entities, wired[0], wired[1],
                refWired[0], refWired[1], edge, grass, step);
    }

    /** The panel's tick (unpacks its wiring, solves); then the wired cells in the box (shifted) and the devices fed. */
    private static int[] wiring(ServerLevel level, BlockPos panelPos, BoundingBox box, BlockPos shift) {
        DistributionPanelBlockEntity panel = (DistributionPanelBlockEntity) level.getBlockEntity(panelPos);
        panel.serverTick();
        panel.serverTick();
        int cells = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
            if (BuildingWiring.any(level, p.offset(shift))) cells++;
        WiringSupply.Result r = panel.wiring();
        int devices = 0;
        if (r != null) for (int c = 1; c <= WiringNode.CIRCUITS; c++) devices += r.devices[c];
        return new int[]{cells, devices};
    }
}
