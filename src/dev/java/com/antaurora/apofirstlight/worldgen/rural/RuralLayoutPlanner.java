package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/** Shared, deterministic lot geometry used by both command and natural Rural planning. */
public final class RuralLayoutPlanner {
    private static final int MAIN_LOT_SIDE_OFFSET = 18;
    private RuralLayoutPlanner() {
    }

    public static List<Candidate> candidates(BlockPos center, Direction mainDirection, Direction branchDirection,
                                             StructureTemplate barnTemplate) {
        List<Candidate> result = new ArrayList<>();
        Direction mainPositiveSide = mainDirection.getClockWise();
        Direction branchPositiveSide = branchDirection.getClockWise();

        for (int side : new int[]{-1, 1}) {
            result.add(candidate(offset(center, branchDirection, 10, branchPositiveSide, side * 18),
                    branchPositiveSide, side, RuralStructurePool.Role.FARMHOUSE));
            result.add(candidate(offset(center, mainDirection, 0, mainPositiveSide,
                    side * MAIN_LOT_SIDE_OFFSET), mainPositiveSide, side,
                    RuralStructurePool.Role.FARMHOUSE));
        }

        addBarnCandidates(result, center, branchDirection, branchPositiveSide, barnTemplate);

        for (int distance : new int[]{10, 26}) {
            for (int side : new int[]{-1, 1}) {
                result.add(candidate(offset(center, branchDirection, distance, branchPositiveSide, side * 30),
                        branchPositiveSide, side, RuralStructurePool.Role.AGRICULTURAL_UTILITY));
            }
        }
        for (int longitudinal : new int[]{-30, -10, 10, 30}) {
            for (int side : new int[]{-1, 1}) {
                result.add(candidate(offset(center, mainDirection, longitudinal, mainPositiveSide,
                        side * MAIN_LOT_SIDE_OFFSET), mainPositiveSide, side,
                        RuralStructurePool.Role.RESIDENTIAL));
            }
        }
        for (int longitudinal : new int[]{-34, 34}) {
            for (int side : new int[]{-1, 1}) {
                result.add(candidate(offset(center, mainDirection, longitudinal, mainPositiveSide, side * 30),
                        mainPositiveSide, side, RuralStructurePool.Role.FLEX));
            }
        }
        for (int side : new int[]{-1, 1}) {
            result.add(candidate(offset(center, mainDirection, 36, mainPositiveSide, side * 28),
                    mainPositiveSide, side, RuralStructurePool.Role.LANDMARK));
        }
        return List.copyOf(result);
    }

    private static Candidate candidate(BlockPos anchor, Direction positiveSide, int side,
                                       RuralStructurePool.Role role) {
        return new Candidate(anchor, faceTowardRoad(positiveSide, side), role, null,
                side < 0 ? "negative" : "positive", 0);
    }

    private static void addBarnCandidates(List<Candidate> result, BlockPos center, Direction branchDirection,
                                          Direction branchSide, StructureTemplate template) {
        RuralPlan.Road road = road(center, branchDirection, true);
        for (int side : new int[]{-1, 1}) {
            Direction roadFacing = faceTowardRoad(branchSide, side);
            Rotation rotation = rotationFor(RuralStructurePool.BARN.frontDirection(), roadFacing);
            BlockPos anchor = anchorOutsideRoad(center, branchDirection, branchSide, side,
                    RuralGenerator.BRANCH_LENGTH - 6, rotation, template, road);
            BoundingBox bounds = boundsAt(template, rotation, anchor);
            result.add(new Candidate(anchor, roadFacing, RuralStructurePool.Role.AGRICULTURAL_LARGE,
                    rotation, side < 0 ? "left" : "right", horizontalGap(bounds, road.bounds())));
        }
    }

    private static BlockPos anchorOutsideRoad(BlockPos center, Direction forward, Direction sideDirection,
                                              int side, int forwardDistance, Rotation rotation,
                                              StructureTemplate template, RuralPlan.Road road) {
        for (int lateral = 0; lateral <= RuralGenerator.RESERVATION_SIZE / 2; lateral++) {
            BlockPos anchor = offset(center, forward, forwardDistance, sideDirection, side * lateral);
            if (!intersects2d(boundsAt(template, rotation, anchor), road.bounds(), RuralGenerator.LOT_MARGIN)) {
                return anchor;
            }
        }
        return offset(center, forward, forwardDistance, sideDirection,
                side * (RuralGenerator.RESERVATION_SIZE / 2));
    }

    public static Rotation rotationFor(Direction templateFront, Direction roadFacing) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(templateFront) == roadFacing) return rotation;
        }
        throw new IllegalArgumentException("No horizontal rotation from " + templateFront + " to " + roadFacing);
    }

    public static boolean facesRoad(RuralStructurePool.Definition definition, Rotation rotation,
                                    Direction roadFacing) {
        return rotation.rotate(definition.frontDirection()) == roadFacing;
    }

    private static Direction faceTowardRoad(Direction positiveSide, int side) {
        // A lot on the positive side points back toward the road; a negative-side lot points positive.
        return side > 0 ? positiveSide.getOpposite() : positiveSide;
    }

    private static BlockPos offset(BlockPos center, Direction forward, int forwardDistance,
                                   Direction side, int sideDistance) {
        return center.relative(forward, forwardDistance).relative(side, sideDistance);
    }

    static BoundingBox boundsAt(StructureTemplate template, Rotation rotation, BlockPos anchor) {
        return template.getBoundingBox(new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rotation),
                new BlockPos(anchor.getX(), 0, anchor.getZ()));
    }

    private static RuralPlan.Road road(BlockPos center, Direction direction, boolean branch) {
        int length = branch ? RuralGenerator.BRANCH_LENGTH : RuralGenerator.ROAD_LENGTH;
        int width = RuralGenerator.ROAD_WIDTH;
        if (direction.getAxis() == Direction.Axis.X) {
            return new RuralPlan.Road(direction, new BoundingBox(center.getX() - length / 2, 0,
                    center.getZ() - width / 2, center.getX() + length / 2 - 1, 0,
                    center.getZ() + width / 2), width, branch);
        }
        return new RuralPlan.Road(direction, new BoundingBox(center.getX() - width / 2, 0,
                center.getZ() - length / 2, center.getX() + width / 2, 0,
                center.getZ() + length / 2 - 1), width, branch);
    }

    private static boolean intersects2d(BoundingBox first, BoundingBox second, int margin) {
        return first.minX() - margin <= second.maxX() && first.maxX() + margin >= second.minX()
                && first.minZ() - margin <= second.maxZ() && first.maxZ() + margin >= second.minZ();
    }

    private static int horizontalGap(BoundingBox first, BoundingBox second) {
        int xGap = Math.max(second.minX() - first.maxX() - 1, first.minX() - second.maxX() - 1);
        int zGap = Math.max(second.minZ() - first.maxZ() - 1, first.minZ() - second.maxZ() - 1);
        return xGap <= 0 && zGap <= 0 ? 0 : Math.max(xGap, zGap);
    }

    public record Candidate(BlockPos anchor, Direction roadFacing, RuralStructurePool.Role role,
                            Rotation rotationOverride, String roadSide, int distanceFromRoad) {
        public Candidate(BlockPos anchor, Direction roadFacing, RuralStructurePool.Role role) {
            this(anchor, roadFacing, role, null, "n/a", 0);
        }
    }
}
