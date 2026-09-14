package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.rural.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** WG-06 frozen Natural V1 plan evidence. This dev-only package is excluded from the release jar. */
@GameTestHolder("wg06_rural_baseline")
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class RuralLegacyRegressionTest {
    private static final String ASSET_ROOT = "/data/apocalypse_firstlight/structures/";
    private static final Method LEGACY_PLAN;

    static {
        try {
            LEGACY_PLAN = RuralNaturalGenerator.class.getDeclaredMethod("plan", RuralTerrainSource.class,
                    Function.class, long.class, BlockPos.class);
            LEGACY_PLAN.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private RuralLegacyRegressionTest() { }

    @SubscribeEvent
    public static void template(ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) return;
        var tag = net.minecraft.nbt.TagParser.parseTag(
                "{size:[16,8,16],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks = new net.minecraft.nbt.ListTag();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 8; y++) for (int z = 0; z < 16; z++) {
            var block = new net.minecraft.nbt.CompoundTag();
            var pos = new net.minecraft.nbt.ListTag();
            for (int coordinate : new int[]{x, y, z}) pos.add(net.minecraft.nbt.IntTag.valueOf(coordinate));
            block.put("pos", pos);
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        var level = event.getServer().overworld();
        level.getStructureManager().getOrCreate(new ResourceLocation("wg06_rural_baseline", "empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static List<TestFunction> tests() {
        return List.of(new TestFunction("wg06_rural_baseline", "wg06_rural_baseline:regression",
                "wg06_rural_baseline:empty", 1200, 0L, true, RuralLegacyRegressionTest::run));
    }

    private static void run(GameTestHelper helper) {
        try {
            main(Boolean.getBoolean("wg06.capture") ? new String[]{"--capture"} : new String[0]);
            helper.succeed();
        } catch (Throwable failure) {
            helper.fail("WG-06 baseline failed: " + failure);
        }
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Map<ResourceLocation, StructureTemplate> templates = templates();
        if (args.length == 1 && args[0].equals("--capture")) {
            for (Case input : captureCases()) {
                RuralPlan plan = plan(input, templates);
                System.out.println("WG06_CASE\t" + input.columns() + "\t" + digest(project(input, plan))
                        + "\t" + plan.valid() + "\t" + plan.scaleTier() + "\t" + plan.failureReason()
                        + "\t" + plan.lots().size() + "\t" + plan.farmPlotCount()
                        + "\t" + plan.branchRoads().size());
            }
            return;
        }
        List<Case> cases = readFixture();
        verifyCoreAdapterPaths(templates);
        int passed = 0;
        EnumMap<RuralScaleTier, Integer> byTier = new EnumMap<>(RuralScaleTier.class);
        EnumSet<Rotation> rotations = EnumSet.noneOf(Rotation.class);
        EnumSet<RuralStructurePool.Role> roles = EnumSet.noneOf(RuralStructurePool.Role.class);
        boolean clusterBranch = false;
        boolean clusterNoBranch = false;
        int accepted = 0;
        boolean mutationChecked = false;
        for (Case input : cases) {
            RuralPlan firstPlan = plan(input, templates);
            String first = digest(project(input, firstPlan));
            require(first.equals(input.expectedDigest()), input.name() + " frozen digest changed: " + first);
            String second = digest(project(input, plan(input, templates)));
            require(first.equals(second), input.name() + " repeat differs");
            byTier.merge(firstPlan.scaleTier(), 1, Integer::sum);
            if (firstPlan.valid()) {
                accepted++;
                if (!mutationChecked && firstPlan.lots().size() > 1 && firstPlan.farmPlots().size() > 1) {
                    verifyProjectionSensitivity(input, firstPlan);
                    mutationChecked = true;
                }
                for (RuralPlan.Lot lot : firstPlan.lots()) {
                    rotations.add(lot.rotation());
                    roles.add(lot.structure().role());
                }
                if (firstPlan.scaleTier() == RuralScaleTier.RURAL_CLUSTER) {
                    if (firstPlan.branchRoads().isEmpty()) clusterNoBranch = true;
                    else clusterBranch = true;
                }
            }
            passed++;
        }
        require(cases.size() >= 20, "fixture coverage below 20");
        for (RuralScaleTier tier : RuralScaleTier.values()) {
            require(byTier.getOrDefault(tier, 0) >= 4, "tier under-covered: " + tier);
        }
        require(accepted >= 8, "too few accepted plans");
        require(rotations.size() == Rotation.values().length, "four-way rotation coverage missing: " + rotations);
        require(roles.contains(RuralStructurePool.Role.RESIDENTIAL)
                && roles.contains(RuralStructurePool.Role.AGRICULTURAL_LARGE)
                && roles.contains(RuralStructurePool.Role.AGRICULTURAL_UTILITY),
                "structure role coverage missing: " + roles);
        require(clusterBranch && clusterNoBranch, "Cluster optional branch states not both covered");
        require(mutationChecked, "no multi-lot/multi-farm mutation case");
        require(RuralStructurePool.definitions().size() == 6, "legacy pool changed");
        require(RuralStructurePool.definitions().stream().map(definition -> definition.id().getPath())
                .collect(java.util.stream.Collectors.toSet()).equals(java.util.Set.of(
                        "rural_barn_large_01", "rural_farmhouse_01", "rural_grain_silo_01",
                        "rural_house_small_01", "rural_storage_small_01", "rural_water_tower_01")),
                "legacy six-asset membership changed");
        require(RuralStructurePool.definitions().stream().noneMatch(definition -> definition.id().getPath().endsWith("_02")),
                "new variants entered legacy pool");
        System.out.println("WG-06 Rural legacy regression: " + passed + " frozen cases passed; accepted="
                + accepted + " tiers=" + byTier + " rotations=" + rotations + " roles=" + roles
                + " clusterBranch=" + clusterBranch + " clusterNoBranch=" + clusterNoBranch);
    }

    private static List<Case> captureCases() {
        List<Case> cases = new ArrayList<>();
        for (RuralScaleTier tier : RuralScaleTier.values()) {
            int ordinal = tier.ordinal();
            for (int i = 0; i < 3; i++) {
                int x = (i - 1) * 320 + ordinal * 16;
                int z = (i - 1) * -272 + ordinal * 32;
                BlockPos center = new BlockPos(x, 0, z);
                long seed = findSeed(tier, center, 62091306L + ordinal * 10000L + i * 100L,
                        tier == RuralScaleTier.RURAL_CLUSTER ? i % 2 : -1);
                cases.add(new Case(tier.name().toLowerCase() + "_flat_" + i, seed, x, z, "flat", "none", ""));
            }
            int x = ordinal * 256 + 48;
            int z = -ordinal * 288 - 96;
            BlockPos center = new BlockPos(x, 0, z);
            long seed = findSeed(tier, center, 62191306L + ordinal * 10000L, -1);
            cases.add(new Case(tier.name().toLowerCase() + "_gentle", seed, x, z, "gentle", "none", ""));
            cases.add(new Case(tier.name().toLowerCase() + "_missing_barn", seed, x, z, "flat", "barn", ""));
        }
        cases.add(new Case("site_water", 62091306L, 0, 0, "water", "none", ""));
        cases.add(new Case("site_invalid", 62091306L, 0, 0, "invalid", "none", ""));
        cases.add(new Case("site_rough", 62091306L, 0, 0, "rough", "none", ""));
        cases.add(new Case("site_mixed", 62091306L, 0, 0, "mixed", "none", ""));
        return cases;
    }

    private static long findSeed(RuralScaleTier tier, BlockPos center, long start, int parity) {
        for (long seed = start; seed < start + 10000; seed++) {
            if (RuralScaleTier.choose(seed, center) == tier && (parity < 0 || (seed & 1L) == parity)) return seed;
        }
        throw new AssertionError("no seed for " + tier);
    }

    private static Map<ResourceLocation, StructureTemplate> templates() throws Exception {
        Map<ResourceLocation, StructureTemplate> result = new HashMap<>();
        for (RuralStructurePool.Definition definition : RuralStructurePool.definitions()) {
            String resource = ASSET_ROOT + definition.id().getPath() + ".nbt";
            try (InputStream input = RuralLegacyRegressionTest.class.getResourceAsStream(resource)) {
                require(input != null, "missing asset " + resource);
                CompoundTag tag = NbtIo.readCompressed(input);
                StructureTemplate template = new StructureTemplate();
                // Planner consumes only template dimensions; read the real NBT size without
                // bootstrapping Forge's game registry in this standalone JavaExec test.
                var size = tag.getList("size", 3);
                require(size.size() == 3, "invalid NBT size " + resource);
                var sizeField = StructureTemplate.class.getDeclaredField("size");
                sizeField.setAccessible(true);
                sizeField.set(template, new Vec3i(size.getInt(0), size.getInt(1), size.getInt(2)));
                result.put(definition.id(), template);
            }
        }
        return Map.copyOf(result);
    }

    private static RuralPlan plan(Case input, Map<ResourceLocation, StructureTemplate> templates) throws Exception {
        RuralTerrainSource terrain = (x, z) -> switch (input.terrain()) {
            case "flat" -> sample(72, false, true);
            case "gentle" -> sample(72 + Math.floorDiv(x - input.x(), 24), false, true);
            case "rough" -> sample(72 + Math.floorMod(x - input.x(), 32), false, true);
            case "water" -> sample(72, true, true);
            case "invalid" -> sample(0, false, false);
            case "mixed" -> sample(72, Math.floorMod(x - input.x(), 12) < 3, true);
            default -> throw new IllegalArgumentException(input.terrain());
        };
        Function<ResourceLocation, Optional<StructureTemplate>> source = id ->
                id.getPath().contains(input.missingTemplate()) && !input.missingTemplate().equals("none")
                        ? Optional.empty() : Optional.ofNullable(templates.get(id));
        try {
            return (RuralPlan) LEGACY_PLAN.invoke(null, terrain, source, input.seed(),
                    new BlockPos(input.x(), 0, input.z()));
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw new AssertionError("planner failed for " + input.name(), exception.getCause());
        }
    }

    private static RuralTerrainSampler.Sample sample(int y, boolean water, boolean valid) {
        return new RuralTerrainSampler.Sample(y, water, 0, valid);
    }

    /** Explicit length-prefixed fields; collection order is specified, never JVM/map iteration order. */
    static String project(Case input, RuralPlan plan) {
        StringBuilder out = new StringBuilder();
        field(out, "projection", "WG06_LEGACY_NATURAL_V1");
        field(out, "mode", "LEGACY_NATURAL_V1");
        field(out, "seed", input.seed());
        field(out, "center", plan.center().getX() + "," + plan.center().getY() + "," + plan.center().getZ());
        field(out, "tier", plan.scaleTier().name());
        field(out, "reservation", box(plan.reservation()));
        field(out, "roadLayout", plan.roadLayout());
        field(out, "mainRoad", road(plan.road()));
        field(out, "branches", plan.branchRoads().size());
        plan.branchRoads().stream().map(RuralLegacyRegressionTest::road).sorted().forEach(value -> field(out, "branch", value));
        field(out, "targetBuildings", plan.targetBuildings());
        field(out, "candidateLots", plan.candidateLots());
        field(out, "fallbackUsed", plan.fallbackUsed());
        field(out, "lots", plan.lots().size());
        plan.lots().stream().map(lot -> lot.structure().role().name() + "|" + lot.structure().id() + "|"
                        + lot.origin().getX() + "," + lot.origin().getY() + "," + lot.origin().getZ()
                        + "|" + lot.rotation().name() + "|" + lot.roadFacing().name() + "|" + box(lot.bounds())
                        + "|" + lot.baseY() + "|" + lot.classification().name() + "|" + lot.minSurfaceY()
                        + "|" + lot.maxSurfaceY() + "|" + lot.predictedCutBlocks() + "|" + lot.predictedFillBlocks()
                        + "|" + lot.maxCutDepth() + "|" + lot.maxFillDepth())
                .sorted().forEach(value -> field(out, "lot", value));
        field(out, "retrySlot", "NOT_AVAILABLE_IN_LEGACY_PLAN");
        field(out, "farmTarget", plan.farmPlotTarget());
        field(out, "farms", plan.farmPlots().size());
        List<RuralFarmPlot> orderedFarms = new ArrayList<>(plan.farmPlots());
        orderedFarms.sort(Comparator.comparingInt(RuralFarmPlot::index)
                .thenComparing(RuralFarmPlot::ownerId).thenComparing(farm -> box(farm.bounds())));
        for (RuralFarmPlot farm : orderedFarms) {
            String prefix = farm.index() + "|" + farm.ownerId() + "|";
            field(out, "farm", prefix + box(farm.bounds()) + "|" + farm.shape().name() + "|"
                    + farm.baseY() + "|" + farm.crop().name() + "|" + farm.irrigationType().name()
                    + "|" + farm.valid() + "|" + farm.rejectionReason());
            farm.cells().stream().map(cell -> prefix + cell.x() + "," + cell.z()).sorted()
                    .forEach(value -> field(out, "farmCell", value));
            farm.fences().stream().map(fence -> prefix + pos(fence.pos()) + "|" + fence.facing().name())
                    .sorted().forEach(value -> field(out, "farmFence", value));
            farm.gates().stream().map(gate -> prefix + pos(gate.pos()) + "|" + pos(gate.insideCell())
                            + "|" + gate.facing().name()).sorted()
                    .forEach(value -> field(out, "farmGate", value));
            farm.irrigationCells().stream().map(cell -> prefix + pos(cell)).sorted()
                    .forEach(value -> field(out, "farmIrrigation", value));
            farm.pathCells().stream().map(cell -> prefix + pos(cell)).sorted()
                    .forEach(value -> field(out, "farmPath", value));
            farm.surfaceYs().entrySet().stream().map(entry -> prefix + entry.getKey() + "|" + entry.getValue())
                    .sorted().forEach(value -> field(out, "farmSurface", value));
        }
        field(out, "farmOrientation", "NOT_AVAILABLE_IN_LEGACY_PLAN");
        field(out, "farmFallback", "NOT_AVAILABLE_IN_LEGACY_PLAN");
        field(out, "accepted", plan.valid());
        field(out, "rejectionStage", "NOT_AVAILABLE_IN_LEGACY_PLAN");
        field(out, "rejectionReason", plan.failureReason());
        return out.toString();
    }

    private static String road(RuralPlan.Road road) {
        return road.direction().name() + "|" + box(road.bounds()) + "|" + road.width() + "|"
                + road.bounds().getXSpan() + "x" + road.bounds().getZSpan() + "|" + road.branch();
    }

    private static String pos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static void verifyCoreAdapterPaths(Map<ResourceLocation, StructureTemplate> templates) {
        Function<ResourceLocation, Optional<StructureTemplate>> source = id -> Optional.ofNullable(templates.get(id));
        var natural = RuralPlanningCore.catalog(RuralPlanningCore.SelectionMode.LEGACY_NATURAL_V1, source);
        var dev = RuralPlanningCore.catalog(RuralPlanningCore.SelectionMode.LEGACY_DEV_V1, source);
        require(natural.firstMissing() == null && natural.templates().size() == 6, "Natural core catalog");
        require(dev.firstMissing() == null && dev.templates().size() == 6, "Dev core catalog");
        Function<ResourceLocation, Optional<StructureTemplate>> missingFarmhouse = id ->
                id.equals(RuralStructurePool.FARMHOUSE.id()) ? Optional.empty() : source.apply(id);
        natural = RuralPlanningCore.catalog(RuralPlanningCore.SelectionMode.LEGACY_NATURAL_V1, missingFarmhouse);
        dev = RuralPlanningCore.catalog(RuralPlanningCore.SelectionMode.LEGACY_DEV_V1, missingFarmhouse);
        require(natural.firstMissing() == RuralStructurePool.FARMHOUSE && natural.templates().size() == 5,
                "Natural must retain other optional template lookups");
        require(dev.firstMissing() == RuralStructurePool.FARMHOUSE && dev.templates().isEmpty(),
                "Dev must stop at first missing template");
        var house = RuralStructurePool.definitions().get(2);
        require(RuralPlanningCore.availableDev(RuralStructurePool.Role.RESIDENTIAL,
                Map.of(house, house.maxCount())).isEmpty(), "Dev maxCount policy");
        require(RuralPlanningCore.availableDev(RuralStructurePool.Role.RESIDENTIAL,
                Map.of()).equals(List.of(house)), "Dev available pool order");
        require(RuralPlanningCore.selectNatural(RuralStructurePool.Role.RESIDENTIAL, 1L,
                BlockPos.ZERO, 0) == house, "Natural role selection policy");
    }

    private static void verifyProjectionSensitivity(Case input, RuralPlan original) throws Exception {
        String expected = digest(project(input, original));
        RuralPlan.Lot first = original.lots().get(0);
        List<RuralPlan.Lot> lots = new ArrayList<>(original.lots());
        lots.set(0, new RuralPlan.Lot(RuralStructurePool.definitions().get(2), first.origin(),
                first.rotation(), first.bounds(), first.baseY(), first.roadFacing(), first.classification(),
                first.minSurfaceY(), first.maxSurfaceY(), first.predictedCutBlocks(), first.predictedFillBlocks(),
                first.maxCutDepth(), first.maxFillDepth()));
        require(!expected.equals(digest(project(input, copy(original, original.road(), lots,
                original.farmPlots())))), "structure ID mutation invisible");
        lots.set(0, new RuralPlan.Lot(first.structure(), first.origin().east(), first.rotation(), first.bounds(),
                first.baseY(), first.roadFacing(), first.classification(), first.minSurfaceY(), first.maxSurfaceY(),
                first.predictedCutBlocks(), first.predictedFillBlocks(), first.maxCutDepth(), first.maxFillDepth()));
        require(!expected.equals(digest(project(input, copy(original, original.road(), lots,
                original.farmPlots())))), "origin mutation invisible");
        lots.set(0, new RuralPlan.Lot(first.structure(), first.origin(),
                Rotation.values()[(first.rotation().ordinal() + 1) % Rotation.values().length], first.bounds(),
                first.baseY(), first.roadFacing(), first.classification(), first.minSurfaceY(), first.maxSurfaceY(),
                first.predictedCutBlocks(), first.predictedFillBlocks(), first.maxCutDepth(), first.maxFillDepth()));
        require(!expected.equals(digest(project(input, copy(original, original.road(), lots,
                original.farmPlots())))), "rotation mutation invisible");
        var oldRoad = original.road();
        var changedRoad = new RuralPlan.Road(oldRoad.direction(), oldRoad.bounds().moved(1, 0, 0),
                oldRoad.width(), oldRoad.branch());
        require(!expected.equals(digest(project(input, copy(original, changedRoad, original.lots(),
                original.farmPlots())))), "road mutation invisible");
        RuralFarmPlot firstFarm = original.farmPlots().get(0);
        List<RuralFarmPlot> farms = new ArrayList<>(original.farmPlots());
        farms.set(0, new RuralFarmPlot(firstFarm.index(), firstFarm.ownerId(), firstFarm.shape(),
                firstFarm.bounds().moved(1, 0, 0), firstFarm.baseY(), firstFarm.crop(),
                firstFarm.irrigationType(), firstFarm.cells(), firstFarm.fences(), firstFarm.gates(),
                firstFarm.irrigationCells(), firstFarm.pathCells(), firstFarm.surfaceYs(),
                firstFarm.valid(), firstFarm.rejectionReason()));
        require(!expected.equals(digest(project(input, copy(original, original.road(), original.lots(), farms)))),
                "farm mutation invisible");
        lots = new ArrayList<>(original.lots());
        farms = new ArrayList<>(original.farmPlots());
        java.util.Collections.reverse(lots);
        java.util.Collections.reverse(farms);
        require(expected.equals(digest(project(input, copy(original, original.road(), lots, farms)))),
                "canonical ordering depends on collection iteration order");
    }

    private static RuralPlan copy(RuralPlan plan, RuralPlan.Road road, List<RuralPlan.Lot> lots,
                                  List<RuralFarmPlot> farms) {
        return RuralPlan.validNatural(plan.center(), plan.reservation(), plan.site(), road,
                plan.branchRoads(), lots, plan.targetBuildings(), plan.candidateLots(), plan.rejectedLots(),
                plan.fallbackUsed(), plan.rejectionCounts(), plan.barnRejectionDetails(),
                plan.farmPlotTarget(), farms, plan.farmPlotRejections(), plan.scaleTier(),
                plan.deterministicSeed(), plan.roadLayout());
    }

    private static String box(BoundingBox box) {
        return box.minX() + "," + box.minY() + "," + box.minZ() + ","
                + box.maxX() + "," + box.maxY() + "," + box.maxZ();
    }

    private static void field(StringBuilder out, String name, Object value) {
        String text = String.valueOf(value);
        out.append(name).append(':').append(text.length()).append(':').append(text).append('\n');
    }

    private static String digest(String canonical) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Case> readFixture() throws Exception {
        String fixture = System.getProperty("wg06.fixture");
        require(fixture != null, "missing wg06.fixture path");
        try (InputStream stream = Files.newInputStream(Path.of(fixture))) {
            List<Case> result = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] columns = line.split("\\t", -1);
                require(columns.length == 7, "bad fixture line: " + line);
                result.add(new Case(columns[0], Long.parseLong(columns[1]), Integer.parseInt(columns[2]),
                        Integer.parseInt(columns[3]), columns[4], columns[5], columns[6]));
            }
            return List.copyOf(result);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Case(String name, long seed, int x, int z, String terrain, String missingTemplate,
                        String expectedDigest) {
        String columns() { return name + "\t" + seed + "\t" + x + "\t" + z + "\t" + terrain + "\t" + missingTemplate; }
    }
}
