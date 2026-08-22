package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The authoritative road footprint consumed by clearance and construction. */
public final class HighwayCorridor {
    public static final int ROW_MARGIN = 3;
    public static final int BRIDGE_WIDTH = HighwayPlan.MAIN_WIDTH + 2;
    public static final int VERTICAL_CLEARANCE = 6;
    public static final int MAX_CUT_CLEARANCE_HEIGHT = 128;
    public static final int MAX_CORE_VERTICAL_CLEARANCE_HEIGHT = 128;
    public static final double STRUCTURAL_SUPPORT_RATIO_THRESHOLD = 0.80;
    public static final int MAX_STRUCTURAL_APPROACH_LENGTH = 32;
    public static final int LANE_DIVIDER_DASH_ON = 3;
    public static final int LANE_DIVIDER_DASH_OFF = 6;
    public static final int LANE_DIVIDER_DASH_CYCLE = LANE_DIVIDER_DASH_ON + LANE_DIVIDER_DASH_OFF;

    private final HighwayPlan plan;
    private final List<Cell> cells;
    private final List<Cell> bridgeCells;
    private final List<Column> rowEnvelope;
    private final List<CenterCell> centerline;
    private final Set<SurfaceKey> surfacePositions;
    private final Set<SurfaceKey> roadFurniturePositions;
    private final List<RoadMarking> roadMarkings;
    private final Map<SurfaceKey, RoadMarking> roadMarkingPositions;
    private final List<RoadMarkingStepConnector> roadMarkingStepConnectors;
    private final Map<SurfaceKey, RoadMarkingStepConnector> roadMarkingStepConnectorPositions;
    private final Set<SurfaceKey> laneDividerStepConnectorGapPositions;
    private final List<CoreRoadColumnSnapshot> coreRoadColumns;
    private final List<CutColumn> cutColumns;
    private final Set<Key> cutColumnPositions;
    private final List<StructuralSpan> structuralBridgeSpans;
    private final int structuralApproachStations;
    private final int bridgeApproachStartExtensions;
    private final int bridgeApproachEndExtensions;
    private final int bridgeApproachSupportFailures;
    private final int duplicateXZDifferentRoadYSurfaceKeys;
    private final int markingsSkippedUnsupportedDiagonal;
    private final int roadStepTransitions;
    private final int roadStepRiseTransitions;
    private final int roadStepDropTransitions;
    private final int laneDividerStepConnectorSkipped;
    private final int unsupportedMarkingStepHeight;

    private HighwayCorridor(HighwayPlan plan, List<Cell> cells, List<Cell> bridgeCells,
                            List<Column> rowEnvelope, List<CenterCell> centerline,
                            Set<SurfaceKey> surfacePositions, Set<SurfaceKey> roadFurniturePositions,
                            List<RoadMarking> roadMarkings,
                            List<RoadMarkingStepConnector> roadMarkingStepConnectors,
                            Set<SurfaceKey> laneDividerStepConnectorGapPositions,
                            List<CoreRoadColumnSnapshot> coreRoadColumns, List<CutColumn> cutColumns,
                            List<StructuralSpan> structuralBridgeSpans,
                            int structuralApproachStations, int bridgeApproachStartExtensions,
                            int bridgeApproachEndExtensions, int bridgeApproachSupportFailures,
                            int duplicateXZDifferentRoadYSurfaceKeys,
                            int markingsSkippedUnsupportedDiagonal,
                            int roadStepTransitions, int roadStepRiseTransitions,
                            int roadStepDropTransitions, int laneDividerStepConnectorSkipped,
                            int unsupportedMarkingStepHeight) {
        this.plan = plan;
        this.cells = List.copyOf(cells);
        this.bridgeCells = List.copyOf(bridgeCells);
        this.rowEnvelope = List.copyOf(rowEnvelope);
        this.centerline = List.copyOf(centerline);
        this.surfacePositions = Set.copyOf(surfacePositions);
        this.roadFurniturePositions = Set.copyOf(roadFurniturePositions);
        this.roadMarkings = List.copyOf(roadMarkings);
        Map<SurfaceKey, RoadMarking> markingPositions = new LinkedHashMap<>();
        for (RoadMarking marking : roadMarkings) {
            markingPositions.put(new SurfaceKey(marking.x(), marking.y(), marking.z()), marking);
        }
        this.roadMarkingPositions = Map.copyOf(markingPositions);
        this.roadMarkingStepConnectors = List.copyOf(roadMarkingStepConnectors);
        Map<SurfaceKey, RoadMarkingStepConnector> connectorPositions = new LinkedHashMap<>();
        for (RoadMarkingStepConnector connector : roadMarkingStepConnectors) {
            connectorPositions.put(new SurfaceKey(connector.x(), connector.y(), connector.z()), connector);
        }
        this.roadMarkingStepConnectorPositions = Map.copyOf(connectorPositions);
        this.laneDividerStepConnectorGapPositions = Set.copyOf(laneDividerStepConnectorGapPositions);
        this.coreRoadColumns = List.copyOf(coreRoadColumns);
        this.cutColumns = List.copyOf(cutColumns);
        Set<Key> cutPositions = new LinkedHashSet<>();
        for (CutColumn column : cutColumns) cutPositions.add(new Key(column.x(), column.z()));
        this.cutColumnPositions = Set.copyOf(cutPositions);
        this.structuralBridgeSpans = List.copyOf(structuralBridgeSpans);
        this.structuralApproachStations = structuralApproachStations;
        this.bridgeApproachStartExtensions = bridgeApproachStartExtensions;
        this.bridgeApproachEndExtensions = bridgeApproachEndExtensions;
        this.bridgeApproachSupportFailures = bridgeApproachSupportFailures;
        this.duplicateXZDifferentRoadYSurfaceKeys = duplicateXZDifferentRoadYSurfaceKeys;
        this.markingsSkippedUnsupportedDiagonal = markingsSkippedUnsupportedDiagonal;
        this.roadStepTransitions = roadStepTransitions;
        this.roadStepRiseTransitions = roadStepRiseTransitions;
        this.roadStepDropTransitions = roadStepDropTransitions;
        this.laneDividerStepConnectorSkipped = laneDividerStepConnectorSkipped;
        this.unsupportedMarkingStepHeight = unsupportedMarkingStepHeight;
    }

    public static HighwayCorridor build(ServerLevel level, HighwayPlan plan, HighwayProfile profile) {
        HighwayPlan.Tangent tangent = plan.tangent(0.0);
        double rightX = -tangent.z();
        double rightZ = tangent.x();
        Map<Key, Double> centerDistances = new LinkedHashMap<>();
        int longitudinalSteps = Math.max(1, (int) Math.ceil(plan.length()));
        HighwayPlan.Point previous = plan.sample(0.0);
        int previousX = (int) Math.round(previous.x());
        int previousZ = (int) Math.round(previous.z());
        centerDistances.put(new Key(previousX, previousZ), 0.0);
        for (int step = 1; step <= longitudinalSteps; step++) {
            double distance = Math.min(plan.length(), step);
            HighwayPlan.Point point = plan.sample(distance);
            int x = (int) Math.round(point.x());
            int z = (int) Math.round(point.z());
            addSupercover(centerDistances, previousX, previousZ, x, z, distance - 1.0, distance);
            previousX = x;
            previousZ = z;
        }

        List<CenterCell> centerline = new ArrayList<>(centerDistances.size());
        for (Map.Entry<Key, Double> entry : centerDistances.entrySet()) {
            centerline.add(new CenterCell(entry.getKey().x(), entry.getKey().z(), entry.getValue()));
        }

        StructuralResolution structural = resolveStructuralBridge(level, profile);
        Map<Key, Cell> unique = new LinkedHashMap<>();
        Map<Key, Integer> firstRoadYByXZ = new LinkedHashMap<>();
        Set<Key> duplicateXZDifferentRoadY = new LinkedHashSet<>();
        for (CenterCell center : centerline) {
            HighwayProfile.Sample sample = profile.sampleAt(center.distance());
            boolean structuralBridge = isStructuralStation(sample, structural.spans(), center.distance());
            int half = plan.width() / 2;
            for (int lateral = -half; lateral <= half; lateral++) {
                int x = (int) Math.round(center.x() + rightX * lateral);
                int z = (int) Math.round(center.z() + rightZ * lateral);
                Key key = new Key(x, z);
                Cell next = new Cell(x, z, center.distance(), lateral, role(plan.width(), lateral),
                        sample.roadY(), sample.terrainY(), sample.mode(), structuralBridge);
                Integer firstRoadY = firstRoadYByXZ.putIfAbsent(key, next.roadY());
                if (firstRoadY != null && firstRoadY != next.roadY()) {
                    duplicateXZDifferentRoadY.add(key);
                }
                Cell existing = unique.get(key);
                if (existing == null || prefer(next, existing)) unique.put(key, next);
            }
        }

        Map<Key, Cell> bridge = new LinkedHashMap<>(unique);
        int bridgeHalf = plan.width() / 2 + 1;
        for (CenterCell center : centerline) {
            HighwayProfile.Sample sample = profile.sampleAt(center.distance());
            if (!isStructuralStation(sample, structural.spans(), center.distance())) continue;
            for (int lateral : new int[] {-bridgeHalf, bridgeHalf}) {
                int x = (int) Math.round(center.x() + rightX * lateral);
                int z = (int) Math.round(center.z() + rightZ * lateral);
                Key key = new Key(x, z);
                bridge.putIfAbsent(key, new Cell(x, z, center.distance(), lateral, Role.BRIDGE_EDGE,
                        sample.roadY(), sample.terrainY(), sample.mode(), true));
            }
        }

        Map<Key, Column> row = new LinkedHashMap<>();
        for (Cell cell : unique.values()) {
            for (int dx = -ROW_MARGIN; dx <= ROW_MARGIN; dx++) {
                for (int dz = -ROW_MARGIN; dz <= ROW_MARGIN; dz++) {
                    Key key = new Key(cell.x() + dx, cell.z() + dz);
                    row.merge(key, new Column(key.x(), key.z(), cell.roadY()),
                            (a, b) -> a.roadY() >= b.roadY() ? a : b);
                }
            }
        }
        Set<SurfaceKey> surfacePositions = new LinkedHashSet<>();
        for (Cell cell : unique.values()) surfacePositions.add(new SurfaceKey(cell.x(), cell.roadY(), cell.z()));
        Set<SurfaceKey> roadFurniturePositions = new LinkedHashSet<>();
        for (Cell cell : bridge.values()) {
            if (cell.structuralBridge() && cell.role() == Role.BRIDGE_EDGE) {
                roadFurniturePositions.add(new SurfaceKey(cell.x(), cell.roadY() + 1, cell.z()));
            }
        }
        for (Cell cell : unique.values()) {
            if (cell.role() == Role.MEDIAN && cell.lateral() == 0) {
                roadFurniturePositions.add(new SurfaceKey(cell.x(), cell.roadY() + 1, cell.z()));
            }
        }
        RoadMarkingResolution markings = buildRoadMarkings(unique.values(), centerline, tangent, profile);
        List<CoreRoadColumnSnapshot> coreRoadColumns = buildCoreRoadColumns(level, unique.values());
        List<CutColumn> cutColumns = buildCutColumns(level, unique.values(), row);
        int approachStations = countApproachStations(profile, structural.spans());
        return new HighwayCorridor(plan, new ArrayList<>(unique.values()), new ArrayList<>(bridge.values()),
                new ArrayList<>(row.values()), centerline, surfacePositions, roadFurniturePositions,
                markings.markings(), markings.stepConnectors(), markings.laneDividerStepConnectorGapPositions(),
                coreRoadColumns, cutColumns,
                structural.spans(), approachStations,
                structural.startExtensions(), structural.endExtensions(), structural.supportFailures(),
                duplicateXZDifferentRoadY.size(), markings.skippedUnsupportedDiagonal(),
                markings.roadStepTransitions(), markings.roadStepRiseTransitions(),
                markings.roadStepDropTransitions(), markings.laneDividerStepConnectorSkipped(),
                markings.unsupportedMarkingStepHeight());
    }

    public static boolean isLaneDividerPainted(double distance) {
        long station = (long) Math.floor(distance);
        return Math.floorMod(station, (long) LANE_DIVIDER_DASH_CYCLE) < LANE_DIVIDER_DASH_ON;
    }

    public static int laneDividerPhase(double distance) {
        long station = (long) Math.floor(distance);
        return (int) Math.floorMod(station, (long) LANE_DIVIDER_DASH_CYCLE);
    }

    private static boolean prefer(Cell next, Cell existing) {
        if (next.structuralBridge() != existing.structuralBridge()) return next.structuralBridge();
        return next.mode() == HighwayTerrainMode.VIADUCT && existing.mode() != HighwayTerrainMode.VIADUCT;
    }

    private static List<CoreRoadColumnSnapshot> buildCoreRoadColumns(ServerLevel level,
                                                                     Iterable<Cell> cells) {
        List<CoreRoadColumnSnapshot> result = new ArrayList<>();
        for (Cell cell : cells) {
            int preConstructionTopY = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.x(), cell.z()) - 1;
            result.add(new CoreRoadColumnSnapshot(cell.x(), cell.z(), cell.roadY(),
                    preConstructionTopY, cell.mode(), cell.distance(), cell.lateral()));
        }
        return result;
    }

    private static RoadMarkingResolution buildRoadMarkings(Iterable<Cell> cells,
                                                            List<CenterCell> centerline,
                                                            HighwayPlan.Tangent tangent,
                                                            HighwayProfile profile) {
        Direction longitudinal = cardinalDirection(tangent);
        if (longitudinal == null) {
            int skipped = 0;
            for (Cell cell : cells) {
                if (markingType(cell.lateral()) != null) skipped++;
            }
            return new RoadMarkingResolution(List.of(), List.of(), Set.of(), skipped, 0, 0, 0, 0, 0);
        }

        Direction right = longitudinal.getClockWise();
        double rightX = -tangent.z();
        double rightZ = tangent.x();
        List<RoadMarking> markings = new ArrayList<>();
        for (Cell cell : cells) {
            RoadMarkingType type = markingType(cell.lateral());
            if (type == null) continue;
            if (type == RoadMarkingType.WHITE_LANE_DIVIDER
                    && !isLaneDividerPainted(cell.distance())) continue;
            Direction facing = markingFacing(cell.lateral(), right, longitudinal);
            markings.add(new RoadMarking(cell.x(), cell.roadY() + 1, cell.z(), type, facing));
        }

        List<RoadMarkingStepConnector> stepConnectors = new ArrayList<>();
        int roadStepTransitions = 0;
        int roadStepRiseTransitions = 0;
        int roadStepDropTransitions = 0;
        int laneDividerStepConnectorSkipped = 0;
        int unsupportedMarkingStepHeight = 0;
        Set<SurfaceKey> laneDividerStepConnectorGapPositions = new LinkedHashSet<>();
        for (int i = 0; i + 1 < centerline.size(); i++) {
            CenterCell current = centerline.get(i);
            CenterCell next = centerline.get(i + 1);
            Direction transitionDirection = directionBetween(current, next);
            if (transitionDirection == null) continue;
            int currentRoadY = profile.sampleAt(current.distance()).roadY();
            int nextRoadY = profile.sampleAt(next.distance()).roadY();
            int deltaY = nextRoadY - currentRoadY;
            if (deltaY == 0) continue;

            roadStepTransitions++;
            if (Math.abs(deltaY) > 1) {
                unsupportedMarkingStepHeight++;
                continue;
            }
            if (deltaY > 0) roadStepRiseTransitions++;
            else roadStepDropTransitions++;

            CenterCell lower = deltaY > 0 ? current : next;
            Direction higherDirection = deltaY > 0
                    ? transitionDirection : transitionDirection.getOpposite();
            int connectorY = Math.max(currentRoadY, nextRoadY) + 1;
            boolean dividerPainted = isLaneDividerPainted(lower.distance());
            for (int lateral : new int[] {-9, 9, -3, 3, -6, 6}) {
                RoadMarkingType type = markingType(lateral);
                int x = (int) Math.round(lower.x() + rightX * lateral);
                int z = (int) Math.round(lower.z() + rightZ * lateral);
                if (type == RoadMarkingType.WHITE_LANE_DIVIDER) {
                    if (!dividerPainted) {
                        laneDividerStepConnectorSkipped++;
                        laneDividerStepConnectorGapPositions.add(new SurfaceKey(x, connectorY, z));
                        continue;
                    }
                }
                Direction markingFacing = markingFacing(lateral, right, longitudinal);
                boolean leftSide = markingFacing == higherDirection.getCounterClockWise();
                stepConnectors.add(new RoadMarkingStepConnector(
                        x, connectorY, z, type, higherDirection, leftSide));
            }
        }
        return new RoadMarkingResolution(markings, stepConnectors, laneDividerStepConnectorGapPositions,
                0, roadStepTransitions,
                roadStepRiseTransitions, roadStepDropTransitions, laneDividerStepConnectorSkipped,
                unsupportedMarkingStepHeight);
    }

    private static Direction markingFacing(int lateral, Direction right, Direction longitudinal) {
        return switch (lateral) {
            case -9, 3 -> right.getOpposite();
            case 9, -3 -> right;
            case -6, 6 -> longitudinal;
            default -> throw new IllegalStateException("Unexpected marking lateral " + lateral);
        };
    }

    private static Direction directionBetween(CenterCell from, CenterCell to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (Math.abs(dx) + Math.abs(dz) != 1) return null;
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dz > 0) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private static RoadMarkingType markingType(int lateral) {
        return switch (lateral) {
            case -9, 9 -> RoadMarkingType.WHITE_EDGE;
            case -3, 3 -> RoadMarkingType.YELLOW_EDGE;
            case -6, 6 -> RoadMarkingType.WHITE_LANE_DIVIDER;
            default -> null;
        };
    }

    private static Direction cardinalDirection(HighwayPlan.Tangent tangent) {
        double x = tangent.x();
        double z = tangent.z();
        if (Math.abs(x) > 0.999 && Math.abs(z) < 0.001) {
            return x > 0.0 ? Direction.EAST : Direction.WEST;
        }
        if (Math.abs(z) > 0.999 && Math.abs(x) < 0.001) {
            return z > 0.0 ? Direction.SOUTH : Direction.NORTH;
        }
        return null;
    }

    private static List<CutColumn> buildCutColumns(ServerLevel level, Iterable<Cell> cells,
                                                   Map<Key, Column> rowEnvelope) {
        Set<Key> positions = new LinkedHashSet<>();
        for (Cell cell : cells) {
            if (cell.mode() != HighwayTerrainMode.CUT) continue;
            for (int dx = -ROW_MARGIN; dx <= ROW_MARGIN; dx++) {
                for (int dz = -ROW_MARGIN; dz <= ROW_MARGIN; dz++) {
                    positions.add(new Key(cell.x() + dx, cell.z() + dz));
                }
            }
        }
        List<CutColumn> result = new ArrayList<>(positions.size());
        for (Key key : positions) {
            Column row = rowEnvelope.get(key);
            if (row == null) continue;
            int terrainTopY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, key.x(), key.z()) - 1;
            result.add(new CutColumn(key.x(), key.z(), row.roadY(), terrainTopY));
        }
        return result;
    }

    private static StructuralResolution resolveStructuralBridge(ServerLevel level, HighwayProfile profile) {
        List<HighwayProfile.Sample> samples = profile.samples();
        List<SupportSample> support = new ArrayList<>(samples.size());
        for (HighwayProfile.Sample sample : samples) support.add(evaluateSupport(level, sample));
        List<StructuralSpan> spans = new ArrayList<>();
        int startExtensions = 0;
        int endExtensions = 0;
        int supportFailures = 0;
        for (HighwayBridgeSpanResolver.Span span : profile.bridgeSpans()) {
            int start = nearestSample(samples, span.startStation());
            int end = nearestSample(samples, span.endStation());
            int extendedStart = start;
            while (extendedStart > 0
                    && samples.get(start).distance() - samples.get(extendedStart - 1).distance()
                    <= MAX_STRUCTURAL_APPROACH_LENGTH
                    && !support.get(extendedStart - 1).stable()) extendedStart--;
            int extendedEnd = end;
            while (extendedEnd + 1 < samples.size()
                    && samples.get(extendedEnd + 1).distance() - samples.get(end).distance()
                    <= MAX_STRUCTURAL_APPROACH_LENGTH
                    && !support.get(extendedEnd + 1).stable()) extendedEnd++;
            if (extendedStart > 0 && !support.get(extendedStart - 1).stable()
                    && samples.get(start).distance() - samples.get(extendedStart - 1).distance()
                    >= MAX_STRUCTURAL_APPROACH_LENGTH) supportFailures++;
            if (extendedEnd + 1 < samples.size() && !support.get(extendedEnd + 1).stable()
                    && samples.get(extendedEnd + 1).distance() - samples.get(end).distance()
                    >= MAX_STRUCTURAL_APPROACH_LENGTH) supportFailures++;
            startExtensions += (int) Math.round(samples.get(start).distance() - samples.get(extendedStart).distance());
            endExtensions += (int) Math.round(samples.get(extendedEnd).distance() - samples.get(end).distance());
            spans.add(new StructuralSpan(samples.get(extendedStart).distance(), samples.get(extendedEnd).distance()));
        }
        spans = mergeSpans(spans);
        return new StructuralResolution(spans, startExtensions, endExtensions, supportFailures);
    }

    private static SupportSample evaluateSupport(ServerLevel level, HighwayProfile.Sample sample) {
        int supported = 0;
        boolean leftEdge = false;
        boolean rightEdge = false;
        double rightX = -sample.tangentZ();
        double rightZ = sample.tangentX();
        for (int lateral = -HighwayProfile.ROAD_HALF_WIDTH; lateral <= HighwayProfile.ROAD_HALF_WIDTH; lateral++) {
            int x = (int) Math.round(sample.x() + rightX * lateral);
            int z = (int) Math.round(sample.z() + rightZ * lateral);
            boolean columnSupported = supportedColumn(level, x, z, sample.roadY());
            if (columnSupported) supported++;
            if (lateral == -HighwayProfile.ROAD_HALF_WIDTH) leftEdge = columnSupported;
            if (lateral == HighwayProfile.ROAD_HALF_WIDTH) rightEdge = columnSupported;
        }
        double ratio = supported / (double) HighwayPlan.MAIN_WIDTH;
        return new SupportSample(supported, HighwayPlan.MAIN_WIDTH - supported, ratio, leftEdge && rightEdge,
                ratio >= STRUCTURAL_SUPPORT_RATIO_THRESHOLD && leftEdge && rightEdge);
    }

    private static boolean supportedColumn(ServerLevel level, int x, int z, int roadY) {
        BlockPos upper = new BlockPos(x, roadY - 1, z);
        BlockPos lower = new BlockPos(x, roadY - 2, z);
        if (!level.getFluidState(upper).isEmpty() || !level.getFluidState(lower).isEmpty()) return false;
        return stable(level, upper) || stable(level, lower);
    }

    private static boolean stable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.is(BlockTags.LEAVES)
                && !state.canBeReplaced() && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static int nearestSample(List<HighwayProfile.Sample> samples, double distance) {
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) {
            double difference = Math.abs(samples.get(i).distance() - distance);
            if (difference < best) {
                best = difference;
                nearest = i;
            }
        }
        return nearest;
    }

    private static List<StructuralSpan> mergeSpans(List<StructuralSpan> spans) {
        spans.sort((a, b) -> Double.compare(a.startStation(), b.startStation()));
        List<StructuralSpan> merged = new ArrayList<>();
        for (StructuralSpan span : spans) {
            if (merged.isEmpty() || span.startStation() > merged.get(merged.size() - 1).endStation()) {
                merged.add(span);
            } else {
                StructuralSpan previous = merged.remove(merged.size() - 1);
                merged.add(new StructuralSpan(previous.startStation(),
                        Math.max(previous.endStation(), span.endStation())));
            }
        }
        return merged;
    }

    private static boolean isStructural(List<StructuralSpan> spans, double distance) {
        return spans.stream().anyMatch(span -> span.contains(distance));
    }

    private static boolean isStructuralStation(HighwayProfile.Sample sample, List<StructuralSpan> spans,
                                               double distance) {
        return sample.mode() == HighwayTerrainMode.VIADUCT || isStructural(spans, distance);
    }

    private static int countApproachStations(HighwayProfile profile, List<StructuralSpan> spans) {
        int count = 0;
        for (HighwayProfile.Sample sample : profile.samples()) {
            if (isStructural(spans, sample.distance()) && !profile.isWithinResolvedBridgeSpan(sample.distance())) count++;
        }
        return count;
    }

    private static void addSupercover(Map<Key, Double> cells, int x0, int z0, int x1, int z1,
                                      double previousDistance, double currentDistance) {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int error = dx - dz;
        int x = x0;
        int z = z0;
        int count = Math.max(1, dx + dz);
        int index = 0;
        while (true) {
            double distance = previousDistance + (currentDistance - previousDistance) * index / (double) count;
            cells.putIfAbsent(new Key(x, z), distance);
            if (x == x1 && z == z1) break;
            int oldX = x;
            int oldZ = z;
            int doubled = error * 2;
            boolean movedX = false;
            boolean movedZ = false;
            if (doubled > -dz) { error -= dz; x += sx; movedX = true; }
            if (doubled < dx) { error += dx; z += sz; movedZ = true; }
            if (movedX && movedZ) {
                cells.putIfAbsent(new Key(oldX, z), distance);
                cells.putIfAbsent(new Key(x, oldZ), distance);
            }
            index++;
        }
    }

    private static Role role(int width, int lateral) {
        if (width == HighwayPlan.MAIN_WIDTH) {
            if (lateral <= -10) return Role.OUTER_SHOULDER;
            if (lateral <= -3) return Role.CARRIAGEWAY_LEFT;
            if (lateral == -2) return Role.INNER_SHOULDER_LEFT;
            if (lateral <= 1) return Role.MEDIAN;
            if (lateral == 2) return Role.INNER_SHOULDER_RIGHT;
            if (lateral <= 9) return Role.CARRIAGEWAY_RIGHT;
            return Role.OUTER_SHOULDER;
        }
        return Role.CARRIAGEWAY_LEFT;
    }

    public HighwayPlan plan() { return plan; }
    public List<Cell> cells() { return cells; }
    public List<Cell> bridgeCells() { return bridgeCells; }
    public List<Column> rowEnvelope() { return rowEnvelope; }
    public List<RoadMarking> roadMarkings() { return roadMarkings; }
    public List<RoadMarkingStepConnector> roadMarkingStepConnectors() { return roadMarkingStepConnectors; }
    public List<CoreRoadColumnSnapshot> coreRoadColumns() { return coreRoadColumns; }
    public List<CutColumn> cutColumns() { return cutColumns; }
    public List<CenterCell> centerline() { return centerline; }
    public List<StructuralSpan> structuralBridgeSpans() { return structuralBridgeSpans; }
    public int structuralApproachStations() { return structuralApproachStations; }
    public int structuralBridgeCells() { return (int) cells.stream().filter(Cell::structuralBridge).count(); }
    public int structuralApproachCells() {
        return (int) cells.stream().filter(cell -> cell.structuralBridge() && cell.mode() != HighwayTerrainMode.VIADUCT).count();
    }
    public int bridgeApproachStartExtensions() { return bridgeApproachStartExtensions; }
    public int bridgeApproachEndExtensions() { return bridgeApproachEndExtensions; }
    public int bridgeApproachSupportFailures() { return bridgeApproachSupportFailures; }
    public int authoritativeSurfaceKeys() { return surfacePositions.size(); }
    public int uniqueCoreRoadXZColumns() { return coreRoadColumns.size(); }
    public int duplicateXZDifferentRoadYSurfaceKeys() { return duplicateXZDifferentRoadYSurfaceKeys; }
    public int markingsSkippedUnsupportedDiagonal() { return markingsSkippedUnsupportedDiagonal; }
    public int roadStepTransitions() { return roadStepTransitions; }
    public int roadStepRiseTransitions() { return roadStepRiseTransitions; }
    public int roadStepDropTransitions() { return roadStepDropTransitions; }
    public int laneDividerStepConnectorSkipped() { return laneDividerStepConnectorSkipped; }
    public int unsupportedMarkingStepHeight() { return unsupportedMarkingStepHeight; }
    public int expectedSurfaceCells() { return cells.size(); }
    public boolean isExpectedSurface(int x, int y, int z) { return surfacePositions.contains(new SurfaceKey(x, y, z)); }
    public boolean isExpectedRoadFurniture(int x, int y, int z) {
        return roadFurniturePositions.contains(new SurfaceKey(x, y, z));
    }
    public RoadMarking expectedRoadMarking(int x, int y, int z) {
        return roadMarkingPositions.get(new SurfaceKey(x, y, z));
    }
    public RoadMarkingStepConnector expectedRoadMarkingStepConnector(int x, int y, int z) {
        return roadMarkingStepConnectorPositions.get(new SurfaceKey(x, y, z));
    }
    public Set<BlockPos> laneDividerStepConnectorGapPositions() {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (SurfaceKey key : laneDividerStepConnectorGapPositions) {
            positions.add(new BlockPos(key.x(), key.y(), key.z()));
        }
        return Set.copyOf(positions);
    }
    public boolean isCutColumn(int x, int z) {
        return cutColumnPositions.contains(new Key(x, z));
    }

    public record Cell(int x, int z, double distance, int lateral, Role role, int roadY, int terrainY,
                       HighwayTerrainMode mode, boolean structuralBridge) {}
    public record Column(int x, int z, int roadY) {}
    public record RoadMarking(int x, int y, int z, RoadMarkingType type, Direction facing) {}
    public record RoadMarkingStepConnector(int x, int y, int z, RoadMarkingType type,
                                           Direction facing, boolean leftSide) {}
    public record CoreRoadColumnSnapshot(int x, int z, int roadY, int preConstructionTopY,
                                         HighwayTerrainMode mode, double distance, int lateral) {
        public int clearanceTopY(int maxBuildHeight) {
            return Math.min(maxBuildHeight - 1,
                    Math.min(roadY + MAX_CORE_VERTICAL_CLEARANCE_HEIGHT, preConstructionTopY));
        }

        public boolean hasOriginalObstruction() {
            return preConstructionTopY >= roadY + 1;
        }

        public boolean capped(int maxBuildHeight) {
            return preConstructionTopY > clearanceTopY(maxBuildHeight);
        }
    }
    public record CutColumn(int x, int z, int roadY, int terrainTopY) {
        public int clearanceTopY(int maxBuildHeight) {
            int desiredTop = Math.max(roadY + VERTICAL_CLEARANCE, terrainTopY);
            return Math.min(maxBuildHeight - 1,
                    Math.min(roadY + MAX_CUT_CLEARANCE_HEIGHT, desiredTop));
        }

        public boolean capped(int maxBuildHeight) {
            return terrainTopY > clearanceTopY(maxBuildHeight);
        }
    }
    public record CenterCell(int x, int z, double distance) {}
    public record StructuralSpan(double startStation, double endStation) {
        public boolean contains(double distance) { return distance >= startStation && distance <= endStation; }
    }
    private record Key(int x, int z) {}
    private record SurfaceKey(int x, int y, int z) {}
    private record SupportSample(int supportedColumns, int unsupportedColumns, double supportRatio,
                                 boolean edgeColumnsSupported, boolean stable) {}
    private record StructuralResolution(List<StructuralSpan> spans, int startExtensions, int endExtensions,
                                       int supportFailures) {}
    private record RoadMarkingResolution(List<RoadMarking> markings,
                                         List<RoadMarkingStepConnector> stepConnectors,
                                         Set<SurfaceKey> laneDividerStepConnectorGapPositions,
                                         int skippedUnsupportedDiagonal,
                                         int roadStepTransitions,
                                         int roadStepRiseTransitions,
                                         int roadStepDropTransitions,
                                         int laneDividerStepConnectorSkipped,
                                         int unsupportedMarkingStepHeight) {}

    public enum RoadMarkingType {
        WHITE_EDGE,
        YELLOW_EDGE,
        WHITE_LANE_DIVIDER
    }

    public enum Role {
        OUTER_SHOULDER,
        CARRIAGEWAY_LEFT,
        INNER_SHOULDER_LEFT,
        MEDIAN,
        INNER_SHOULDER_RIGHT,
        CARRIAGEWAY_RIGHT,
        BRIDGE_EDGE
    }
}
