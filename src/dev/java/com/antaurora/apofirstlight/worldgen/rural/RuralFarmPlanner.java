package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.LevelReader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic, optional farm-plot planner. It never mutates the world. */
public final class RuralFarmPlanner {
    public static final int MIN_PLOTS = 1;
    public static final int MAX_PLOTS = 3;
    public static final int MAX_RELIEF = 3;
    public static final int MAX_CELL_ADJUST = 2;
    public static final int STRUCTURE_MARGIN = 3;
    public static final int ROAD_MARGIN = 3;
    public static final int FARM_CLEARANCE_MARGIN = 1;

    private static final long FARM_SALT = 0x4641524D5F56312L;
    private static final int MIN_WIDTH = 4;

    private RuralFarmPlanner() {
    }

    public static Result plan(net.minecraft.server.level.ServerLevel level, BlockPos center, BoundingBox reservation,
                              List<RuralPlan.Road> roads, List<RuralPlan.Lot> lots) {
        return plan(RuralTerrainSampler.source(level), level.getSeed(), center, reservation, roads, lots,
                MIN_PLOTS, MAX_PLOTS);
    }

    public static Result plan(LevelReader level, long seed, BlockPos center, BoundingBox reservation,
                              List<RuralPlan.Road> roads, List<RuralPlan.Lot> lots,
                              int minPlots, int maxPlots) {
        return plan(RuralTerrainSampler.source(level), seed, center, reservation, roads, lots, minPlots, maxPlots);
    }

    public static Result plan(RuralTerrainSource terrain, long seed, BlockPos center, BoundingBox reservation,
                              List<RuralPlan.Road> roads, List<RuralPlan.Lot> lots,
                              int minPlots, int maxPlots) {
        return planInternal(terrain, seed, center, reservation, roads, lots, minPlots, maxPlots, false);
    }

    public static Result planBounded(RuralTerrainSource terrain, long seed, BlockPos center, BoundingBox reservation,
                                     List<RuralPlan.Road> roads, List<RuralPlan.Lot> lots,
                                     int minPlots, int maxPlots) {
        return planInternal(terrain, seed, center, reservation, roads, lots, minPlots, maxPlots, true);
    }

    private static Result planInternal(RuralTerrainSource terrain, long seed, BlockPos center, BoundingBox reservation,
                                       List<RuralPlan.Road> roads, List<RuralPlan.Lot> lots,
                                       int minPlots, int maxPlots, boolean bounded) {
        int target = minPlots + (int) Math.floorMod(seed ^ center.asLong() ^ FARM_SALT,
                (long) (maxPlots - minPlots + 1));
        List<RuralPlan.Lot> owners = new ArrayList<>(lots.stream()
                .filter(lot -> lot.structure() == RuralStructurePool.FARMHOUSE
                        || lot.structure() == RuralStructurePool.BARN)
                .toList());
        owners.sort(Comparator.comparingInt(lot -> lot.structure() == RuralStructurePool.FARMHOUSE ? 0 : 1));

        List<RuralFarmPlot> plots = new ArrayList<>();
        List<String> rejections = new ArrayList<>();
        Set<Long> occupiedPlotCells = new HashSet<>();
        int attempt = 0;

        for (RuralPlan.Lot owner : owners) {
            if (plots.size() >= target) break;
            RandomSource random = RandomSource.create(seed ^ center.asLong() ^ FARM_SALT
                    ^ (long) (plots.size() + 1) * 0x9E3779B97F4A7C15L);
            for (Candidate candidate : (bounded
                    ? ownerCandidatesBounded(owner, reservation, random) : ownerCandidates(owner, reservation, random))) {
                if (bounded && attempt >= RuralGenerator.MAX_NATURAL_FARM_CANDIDATES) break;
                Validation validation = validate(terrain, center, reservation, roads, lots, occupiedPlotCells,
                        owner, candidate, plots.size(), random, bounded);
                attempt++;
                if (validation.plot() != null) {
                    plots.add(validation.plot());
                    occupiedPlotCells.addAll(validation.plot().cells().stream().map(RuralFarmPlot.Cell::key).toList());
                    break;
                }
                if (rejections.size() < 24) {
                    rejections.add("owner=" + owner.structure().id() + " candidate=" + candidate
                            + " reason=" + validation.reason());
                }
            }
        }

        if (plots.size() < target) {
            RandomSource fallbackRandom = RandomSource.create(seed ^ center.asLong() ^ FARM_SALT ^ 0xFA11BACCL);
            List<RuralPlan.Lot> fallbackOwners = owners.isEmpty() ? lots : owners;
            for (Candidate candidate : (bounded
                    ? fallbackCandidatesBounded(reservation, fallbackRandom) : fallbackCandidates(reservation, fallbackRandom))) {
                if (plots.size() >= target) break;
                if (bounded && attempt >= RuralGenerator.MAX_NATURAL_FARM_CANDIDATES) break;
                RuralPlan.Lot owner = nearestOwner(fallbackOwners, candidate);
                    Validation validation = validate(terrain, center, reservation, roads, lots, occupiedPlotCells,
                            owner, candidate, plots.size(), fallbackRandom, bounded);
                attempt++;
                if (validation.plot() != null) {
                    plots.add(validation.plot());
                    occupiedPlotCells.addAll(validation.plot().cells().stream().map(RuralFarmPlot.Cell::key).toList());
                } else if (rejections.size() < 24) {
                    rejections.add("fallback candidate=" + candidate + " reason=" + validation.reason());
                }
            }
        }

        return new Result(target, List.copyOf(plots), List.copyOf(rejections), attempt);
    }

    private static List<Candidate> ownerCandidatesBounded(RuralPlan.Lot owner, BoundingBox reservation,
                                                           RandomSource random) {
        List<Candidate> result = new ArrayList<>();
        Direction back = owner.roadFacing().getOpposite();
        Direction side = back.getClockWise();
        int minWidth = owner.structure() == RuralStructurePool.BARN ? 12 : 7;
        int maxWidth = owner.structure() == RuralStructurePool.BARN ? 19 : 11;
        int minDepth = owner.structure() == RuralStructurePool.BARN ? 14 : 8;
        int maxDepth = owner.structure() == RuralStructurePool.BARN ? 22 : 13;
        for (Direction direction : new Direction[]{back, side, side.getOpposite()}) {
            for (int distance : new int[]{7, 15, 23, 31}) {
                int width = between(random, minWidth, maxWidth);
                int depth = between(random, minDepth, maxDepth);
                if (random.nextBoolean()) {
                    int swap = width;
                    width = depth;
                    depth = swap;
                }
                int x = owner.bounds().getCenter().getX() + direction.getStepX() * distance;
                int z = owner.bounds().getCenter().getZ() + direction.getStepZ() * distance;
                result.add(new Candidate(x - width / 2, z - depth / 2, width, depth,
                        shape(random, owner.structure() == RuralStructurePool.BARN), owner.structure().id().toString()));
            }
        }
        return result;
    }

    private static List<Candidate> ownerCandidates(RuralPlan.Lot owner, BoundingBox reservation,
                                                    RandomSource random) {
        List<Candidate> result = new ArrayList<>();
        Direction back = owner.roadFacing().getOpposite();
        Direction sideA = back.getClockWise();
        Direction sideB = back.getCounterClockWise();
        Direction[] directions = {back, sideA, sideB};
        int minWidth = owner.structure() == RuralStructurePool.BARN ? 12 : 7;
        int maxWidth = owner.structure() == RuralStructurePool.BARN ? 19 : 11;
        int minDepth = owner.structure() == RuralStructurePool.BARN ? 14 : 8;
        int maxDepth = owner.structure() == RuralStructurePool.BARN ? 22 : 13;
        for (int directionIndex = 0; directionIndex < directions.length; directionIndex++) {
            Direction direction = directions[directionIndex];
            Direction lateral = direction.getClockWise();
            for (int distance = 7; distance <= 31; distance += 4) {
                for (int offset : new int[]{-24, -12, 0, 12, 24}) {
                    int width = between(random, minWidth, maxWidth);
                    int depth = between(random, minDepth, maxDepth);
                    if (random.nextBoolean()) {
                        int swap = width;
                        width = depth;
                        depth = swap;
                    }
                    int x = owner.bounds().getCenter().getX() + direction.getStepX() * distance
                            + lateral.getStepX() * offset;
                    int z = owner.bounds().getCenter().getZ() + direction.getStepZ() * distance
                            + lateral.getStepZ() * offset;
                    result.add(new Candidate(x - width / 2, z - depth / 2, width, depth,
                            shape(random, owner.structure() == RuralStructurePool.BARN),
                            owner.structure().id().toString()));
                }
            }
        }
        return result;
    }

    private static List<Candidate> fallbackCandidates(BoundingBox reservation, RandomSource random) {
        List<Candidate> result = new ArrayList<>();
        int minX = reservation.minX() + 10;
        int maxX = reservation.maxX() - 24;
        int minZ = reservation.minZ() + 10;
        int maxZ = reservation.maxZ() - 24;
        for (int x = minX; x <= maxX; x += 6) {
            for (int z = minZ; z <= maxZ; z += 6) {
                int width = between(random, 8, 15);
                int depth = between(random, 9, 17);
                result.add(new Candidate(x, z, width, depth, shape(random, false), "fallback"));
            }
        }
        return result;
    }

    private static List<Candidate> fallbackCandidatesBounded(BoundingBox reservation, RandomSource random) {
        List<Candidate> result = new ArrayList<>();
        int minX = reservation.minX() + 10;
        int maxX = reservation.maxX() - 24;
        int minZ = reservation.minZ() + 10;
        int maxZ = reservation.maxZ() - 24;
        for (int ix = 0; ix < 4; ix++) {
            for (int iz = 0; iz < 4; iz++) {
                int x = minX + (maxX - minX) * ix / 3;
                int z = minZ + (maxZ - minZ) * iz / 3;
                result.add(new Candidate(x, z, between(random, 8, 15), between(random, 9, 17),
                        shape(random, false), "fallback"));
            }
        }
        return result;
    }

    private static RuralPlan.Lot nearestOwner(List<RuralPlan.Lot> owners, Candidate candidate) {
        return owners.stream().min(Comparator.comparingLong(owner -> {
            long dx = owner.bounds().getCenter().getX() - candidate.centerX();
            long dz = owner.bounds().getCenter().getZ() - candidate.centerZ();
            return dx * dx + dz * dz;
        })).orElse(null);
    }

    private static Validation validate(RuralTerrainSource terrain, BlockPos center, BoundingBox reservation,
                                       List<RuralPlan.Road> roads, List<RuralPlan.Lot> lots,
                                       Set<Long> occupiedPlotCells, RuralPlan.Lot owner,
                                       Candidate candidate, int plotIndex, RandomSource random, boolean bounded) {
        boolean[][] mask = buildMask(candidate.width(), candidate.depth(), candidate.shape());
        String maskError = validateMask(mask, candidate.shape());
        if (maskError != null) return Validation.rejected(maskError);
        int minX = candidate.minX();
        int minZ = candidate.minZ();
        BoundingBox bounds = new BoundingBox(minX, 0, minZ, minX + candidate.width() - 1, 0,
                minZ + candidate.depth() - 1);
        BoundingBox fencedEnvelope = new BoundingBox(bounds.minX() - FARM_CLEARANCE_MARGIN, 0,
                bounds.minZ() - FARM_CLEARANCE_MARGIN, bounds.maxX() + FARM_CLEARANCE_MARGIN, 0,
                bounds.maxZ() + FARM_CLEARANCE_MARGIN);
        if (!inside(fencedEnvelope, reservation)) return Validation.rejected("reservation_bounds");
        for (RuralPlan.Road road : roads) {
            if (intersects2d(fencedEnvelope, road.bounds(), ROAD_MARGIN)) return Validation.rejected("road_overlap");
        }
        for (RuralPlan.Lot lot : lots) {
            if (intersects2d(fencedEnvelope, lot.bounds(), STRUCTURE_MARGIN)) return Validation.rejected("structure_overlap");
        }
        for (long key : occupiedPlotCells) {
            BlockPos occupied = BlockPos.of(key);
            if (occupied.getX() >= fencedEnvelope.minX() && occupied.getX() <= fencedEnvelope.maxX()
                    && occupied.getZ() >= fencedEnvelope.minZ() && occupied.getZ() <= fencedEnvelope.maxZ()) {
                return Validation.rejected("plot_overlap");
            }
        }

        List<RuralFarmPlot.Cell> cells = new ArrayList<>();
        Map<Long, Integer> surfaceYs = new HashMap<>();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int boundedBaseY = 0;
        if (bounded) {
            int midX = (minX + bounds.maxX()) / 2;
            int midZ = (minZ + bounds.maxZ()) / 2;
            int[][] points = {{minX, minZ}, {minX, bounds.maxZ()}, {bounds.maxX(), minZ},
                    {bounds.maxX(), bounds.maxZ()}, {midX, minZ}, {midX, bounds.maxZ()},
                    {minX, midZ}, {bounds.maxX(), midZ}, {midX, midZ}};
            List<Integer> probeHeights = new ArrayList<>();
            for (int[] point : points) {
                RuralTerrainSampler.Sample sample = terrain.sample(point[0], point[1]);
                if (!sample.valid()) return Validation.rejected("invalid_ground");
                if (sample.water()) return Validation.rejected("natural_water");
                probeHeights.add(sample.surfaceY());
                minY = Math.min(minY, sample.surfaceY());
                maxY = Math.max(maxY, sample.surfaceY());
            }
            if (maxY - minY > MAX_RELIEF) return Validation.rejected("relief=" + (maxY - minY));
            boundedBaseY = median(probeHeights.stream().sorted().toList());
            for (int surfaceY : probeHeights) {
                if (Math.abs(surfaceY - boundedBaseY) > MAX_CELL_ADJUST) {
                    return Validation.rejected("adjustment=" + Math.abs(surfaceY - boundedBaseY));
                }
            }
            for (int x = 0; x < candidate.width(); x++) for (int z = 0; z < candidate.depth(); z++) {
                if (mask[x][z]) {
                    int worldX = minX + x;
                    int worldZ = minZ + z;
                    cells.add(new RuralFarmPlot.Cell(worldX, worldZ, BlockPos.asLong(worldX, 0, worldZ)));
                }
            }
        } else {
            for (int x = 0; x < candidate.width(); x++) {
                for (int z = 0; z < candidate.depth(); z++) {
                    if (!mask[x][z]) continue;
                    int worldX = minX + x;
                    int worldZ = minZ + z;
                    RuralTerrainSampler.Sample sample = terrain.sample(worldX, worldZ);
                    if (!sample.valid()) return Validation.rejected("invalid_ground");
                    if (sample.water()) return Validation.rejected("natural_water");
                    long key = BlockPos.asLong(worldX, 0, worldZ);
                    cells.add(new RuralFarmPlot.Cell(worldX, worldZ, key));
                    surfaceYs.put(key, sample.surfaceY());
                    minY = Math.min(minY, sample.surfaceY());
                    maxY = Math.max(maxY, sample.surfaceY());
                }
            }
            if (maxY - minY > MAX_RELIEF) return Validation.rejected("relief=" + (maxY - minY));
        }
        if (cells.isEmpty()) return Validation.rejected("empty_mask");
        int baseY = bounded ? boundedBaseY : median(surfaceYs.values().stream().sorted().toList());

        Map<Long, RuralFarmPlot.Fence> fenceMap = new HashMap<>();
        List<Edge> edges = new ArrayList<>();
        for (RuralFarmPlot.Cell cell : cells) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int nx = cell.x() + direction.getStepX();
                int nz = cell.z() + direction.getStepZ();
                if (contains(mask, minX, minZ, nx, nz)) continue;
                BlockPos outside = new BlockPos(nx, baseY, nz);
                if (!inside(outside, reservation)) return Validation.rejected("fence_outside_reservation");
                fenceMap.putIfAbsent(BlockPos.asLong(nx, baseY, nz),
                        new RuralFarmPlot.Fence(outside, direction));
                int localX = cell.x() - minX;
                int localZ = cell.z() - minZ;
                edges.add(new Edge(cell.at(baseY), outside, direction,
                        localX * candidate.depth() + localZ));
            }
        }
        List<Set<Integer>> components = components(mask, candidate.width(), candidate.depth());
        List<RuralFarmPlot.Gate> gates = selectGates(components, edges, owner, candidate.shape());
        if (gates.size() != components.size()) return Validation.rejected("no_safe_gate");
        Set<Long> gateKeys = new HashSet<>();
        for (RuralFarmPlot.Gate gate : gates) gateKeys.add(BlockPos.asLong(gate.pos().getX(), baseY, gate.pos().getZ()));
        List<RuralFarmPlot.Fence> fences = fenceMap.values().stream()
                .filter(fence -> !gateKeys.contains(BlockPos.asLong(fence.pos().getX(), baseY, fence.pos().getZ())))
                .toList();
        List<BlockPos> irrigation = irrigationCells(mask, minX, minZ, components);
        RuralFarmPlot.CropType crop = RuralFarmPlot.CropType.values()[random.nextInt(RuralFarmPlot.CropType.values().length)];
        RuralFarmPlot.IrrigationType irrigationType = components.size() > 1
                ? RuralFarmPlot.IrrigationType.TWO_SHORT_CHANNELS : RuralFarmPlot.IrrigationType.CENTER_CHANNEL;
        RuralFarmPlot plot = new RuralFarmPlot(plotIndex, owner == null ? "fallback" : owner.structure().id().toString(),
                candidate.shape(), new BoundingBox(bounds.minX(), baseY, bounds.minZ(), bounds.maxX(), baseY, bounds.maxZ()),
                baseY, crop, irrigationType, cells, fences, gates, irrigation, List.of(), surfaceYs, true, "OK");
        return Validation.accepted(plot);
    }

    private static List<RuralFarmPlot.Gate> selectGates(List<Set<Integer>> components, List<Edge> edges,
                                                        RuralPlan.Lot owner, RuralFarmPlot.ShapeType shape) {
        List<RuralFarmPlot.Gate> result = new ArrayList<>();
        Set<Integer> remaining = new HashSet<>();
        for (int i = 0; i < components.size(); i++) remaining.add(i);
        for (Set<Integer> component : components) {
            Edge best = edges.stream().filter(edge -> component.contains(edge.localKey()))
                    .min(Comparator.comparingLong(edge -> {
                        int ox = owner == null ? edge.inside().getX() : owner.bounds().getCenter().getX();
                        int oz = owner == null ? edge.inside().getZ() : owner.bounds().getCenter().getZ();
                        long dx = edge.outside().getX() - ox;
                        long dz = edge.outside().getZ() - oz;
                        return dx * dx + dz * dz;
                    })).orElse(null);
            if (best == null) return List.of();
            result.add(new RuralFarmPlot.Gate(best.outside(), best.inside(), best.direction().getOpposite()));
        }
        return result;
    }

    private static List<BlockPos> irrigationCells(boolean[][] mask, int minX, int minZ, List<Set<Integer>> components) {
        List<BlockPos> result = new ArrayList<>();
        int width = mask.length;
        int depth = mask[0].length;
        for (Set<Integer> component : components) {
            int minLocalX = Integer.MAX_VALUE, maxLocalX = Integer.MIN_VALUE;
            int minLocalZ = Integer.MAX_VALUE, maxLocalZ = Integer.MIN_VALUE;
            for (int key : component) {
                int x = key / depth;
                int z = key % depth;
                minLocalX = Math.min(minLocalX, x);
                maxLocalX = Math.max(maxLocalX, x);
                minLocalZ = Math.min(minLocalZ, z);
                maxLocalZ = Math.max(maxLocalZ, z);
            }
            if (maxLocalX - minLocalX >= maxLocalZ - minLocalZ) {
                int channelX = (minLocalX + maxLocalX) / 2;
                for (int z = minLocalZ; z <= maxLocalZ; z++) {
                    if (mask[channelX][z] && component.contains(channelX * depth + z))
                        result.add(new BlockPos(minX + channelX, 0, minZ + z));
                }
            } else {
                int channelZ = (minLocalZ + maxLocalZ) / 2;
                for (int x = minLocalX; x <= maxLocalX; x++) {
                    if (mask[x][channelZ] && component.contains(x * depth + channelZ))
                        result.add(new BlockPos(minX + x, 0, minZ + channelZ));
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean[][] buildMask(int width, int depth, RuralFarmPlot.ShapeType shape) {
        boolean[][] mask = new boolean[width][depth];
        for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) mask[x][z] = true;
        switch (shape) {
            case L_SHAPE -> {
                int cutW = Math.max(3, width / 3);
                int cutD = Math.max(3, depth / 3);
                for (int x = 0; x < cutW; x++) for (int z = 0; z < cutD; z++) mask[x][z] = false;
            }
            case CUT_CORNER -> {
                int cut = Math.max(3, Math.min(width, depth) / 3);
                for (int x = 0; x < cut; x++) for (int z = 0; z < cut; z++) mask[x][z] = false;
            }
            case STEPPED -> {
                int steps = Math.min(3, Math.min(width, depth) / 4);
                for (int z = 0; z < steps * 2; z++) {
                    int cut = Math.max(0, steps * 2 - z);
                    for (int x = 0; x < cut; x++) mask[x][z] = false;
                }
            }
            case TWO_PATCH -> {
                int gapStart = width / 2 - 1;
                int gapEnd = gapStart + 1;
                for (int x = gapStart; x <= gapEnd && x < width; x++)
                    for (int z = 0; z < depth; z++) mask[x][z] = false;
            }
            default -> {
            }
        }
        return mask;
    }

    private static String validateMask(boolean[][] mask, RuralFarmPlot.ShapeType shape) {
        int width = mask.length;
        int depth = mask[0].length;
        List<Set<Integer>> components = components(mask, width, depth);
        if (shape != RuralFarmPlot.ShapeType.TWO_PATCH && components.size() != 1) return "mask_disconnected";
        if (shape == RuralFarmPlot.ShapeType.TWO_PATCH && components.size() != 2) return "two_patch_components=" + components.size();
        for (int z = 0; z < depth; z++) {
            int run = 0;
            for (int x = 0; x <= width; x++) {
                boolean filled = x < width && mask[x][z];
                if (filled) run++;
                else if (run > 0) {
                    if (run < MIN_WIDTH) return "row_width=" + run;
                    run = 0;
                }
            }
        }
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (!mask[x][z]) continue;
                int neighbors = 0;
                for (Direction direction : Direction.Plane.HORIZONTAL)
                    if (contains(mask, 0, 0, x + direction.getStepX(), z + direction.getStepZ())) neighbors++;
                if (neighbors < 2) return "isolated_or_spike";
            }
        }
        return null;
    }

    private static List<Set<Integer>> components(boolean[][] mask, int width, int depth) {
        Set<Integer> unvisited = new HashSet<>();
        for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) if (mask[x][z]) unvisited.add(x * depth + z);
        List<Set<Integer>> result = new ArrayList<>();
        while (!unvisited.isEmpty()) {
            int start = unvisited.iterator().next();
            Set<Integer> component = new HashSet<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            unvisited.remove(start);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                component.add(current);
                int x = current / depth;
                int z = current % depth;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    int nx = x + direction.getStepX();
                    int nz = z + direction.getStepZ();
                    int next = nx * depth + nz;
                    if (contains(mask, 0, 0, nx, nz) && unvisited.remove(next)) queue.add(next);
                }
            }
            result.add(component);
        }
        return result;
    }

    private static boolean contains(boolean[][] mask, int minX, int minZ, int x, int z) {
        int localX = x - minX;
        int localZ = z - minZ;
        return localX >= 0 && localX < mask.length && localZ >= 0 && localZ < mask[0].length && mask[localX][localZ];
    }

    private static RuralFarmPlot.ShapeType shape(RandomSource random, boolean barn) {
        RuralFarmPlot.ShapeType[] shapes = RuralFarmPlot.ShapeType.values();
        return shapes[random.nextInt(shapes.length)];
    }

    private static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static int median(List<Integer> sorted) {
        if (sorted.isEmpty()) return 0;
        return sorted.get(sorted.size() / 2);
    }

    private static boolean inside(BlockPos pos, BoundingBox box) {
        return pos.getX() >= box.minX() && pos.getX() <= box.maxX()
                && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ();
    }

    private static boolean inside(BoundingBox inner, BoundingBox outer) {
        return inner.minX() >= outer.minX() && inner.maxX() <= outer.maxX()
                && inner.minZ() >= outer.minZ() && inner.maxZ() <= outer.maxZ();
    }

    private static boolean intersects2d(BoundingBox a, BoundingBox b, int margin) {
        return a.minX() - margin <= b.maxX() && a.maxX() + margin >= b.minX()
                && a.minZ() - margin <= b.maxZ() && a.maxZ() + margin >= b.minZ();
    }

    public record Result(int target, List<RuralFarmPlot> plots, List<String> rejections, int attempts) {
        public int count() { return plots.size(); }
    }

    private record Candidate(int minX, int minZ, int width, int depth, RuralFarmPlot.ShapeType shape,
                             String ownerId) {
        private int centerX() { return minX + width / 2; }
        private int centerZ() { return minZ + depth / 2; }
    }

    private record Edge(BlockPos inside, BlockPos outside, Direction direction, int localKey) {
    }

    private record Validation(RuralFarmPlot plot, String reason) {
        private static Validation accepted(RuralFarmPlot plot) { return new Validation(plot, "OK"); }
        private static Validation rejected(String reason) { return new Validation(null, reason); }
    }
}
