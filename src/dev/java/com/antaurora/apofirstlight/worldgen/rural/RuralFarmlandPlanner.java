package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Bounded, read-only road-frontage planner for new natural Agricultural Lot V1 plans. */
public final class RuralFarmlandPlanner {
    private static final int[][] SIZES = {{10, 14}, {12, 16}, {14, 18}, {16, 22}, {18, 24}, {20, 28}};
    private RuralFarmlandPlanner() { }

    public static RuralFarmPlanner.Result plan(RuralTerrainSource terrain, long seed, BlockPos center,
            BoundingBox reservation, List<RuralPlan.Road> roads, List<RuralPlan.Lot> buildings,
            RuralScaleTier tier, int target) {
        var network = RuralRoadNetwork.from(roads, buildings, reservation);
        var frontages = new ArrayList<>(RuralFrontagePlanner.frontages(network, seed, center));
        // MAIN is deliberately excluded: every current tier supplies a FARM_TRACK or SIDE.
        frontages.removeIf(f -> f.road().type() == RuralRoadType.MAIN);
        frontages.sort(Comparator.comparingInt(f -> f.road().type() == RuralRoadType.FARM_TRACK ? 0 : 1));
        List<RuralFarmPlot> accepted = new ArrayList<>();
        List<String> rejections = new ArrayList<>();
        int attempts = 0;
        int sizeCount = switch (tier) {
            case ISOLATED_HOMESTEAD -> 2;
            case FARMSTEAD -> 4;
            default -> 6;
        };
        search: for (var frontage : frontages) {
            if (accepted.size() >= target) break;
            long identity = RuralFarmlandVariant.mix(seed ^ 0x4641524D56324C31L
                    ^ RuralFarmlandVariant.mix(center.getX()) ^ Long.rotateLeft(RuralFarmlandVariant.mix(center.getZ()), 17)
                    ^ RuralFarmlandVariant.mix(frontage.center().asLong())
                    ^ ((long) frontage.facing().ordinal() * 0x9E3779B97F4A7C15L));
            int firstSize = Math.floorMod(identity, sizeCount);
            for (int sizeTry = 0; sizeTry < sizeCount; sizeTry++) {
                int sizeIndex = (firstSize + sizeTry) % sizeCount;
                BoundingBox bounds = bounds(frontage, SIZES[sizeIndex][0], SIZES[sizeIndex][1]);
                BlockPos gate = RuralAccessPlanner.midpoint(RuralAgriculturalLot.inset(bounds, 1), frontage.facing());
                Direction outward = frontage.facing().getOpposite();
                BlockPos connection = frontage.road().nearest(gate.getX(), gate.getZ())
                        .relative(outward, frontage.road().type().radius());
                List<BlockPos> access = access(gate, connection, frontage.facing(), 0, 0);
                if (!free(bounds, access, frontage.road(), reservation, roads, buildings, accepted)) continue;
                if (attempts >= RuralGenerator.MAX_NATURAL_FARM_CANDIDATES) break search;
                attempts++;
                Integer baseY = ground(terrain, bounds, gate);
                var roadGround = terrain.sample(connection.getX(), connection.getZ());
                if (baseY == null || !roadGround.valid() || roadGround.water()
                        || Math.abs(roadGround.surfaceY() - 1 - baseY) > 1) {
                    if (rejections.size() < 24) rejections.add("agricultural frontage=" + frontage.center()
                            + " size=" + sizeIndex + " reason=ground_or_entry_grade");
                    continue;
                }
                long patternSeed = RuralFarmlandVariant.mix(identity ^ ((long) sizeIndex << 32) ^ accepted.size());
                bounds = new BoundingBox(bounds.minX(), baseY, bounds.minZ(), bounds.maxX(), baseY, bounds.maxZ());
                gate = new BlockPos(gate.getX(), baseY + 1, gate.getZ());
                List<BlockPos> gradedAccess = access(gate, connection, frontage.facing(), baseY, roadGround.surfaceY() - 1);
                if (!safeAccess(terrain, gradedAccess)) {
                    if (rejections.size() < 24) rejections.add("agricultural frontage=" + frontage.center()
                            + " reason=access_water_or_grade");
                    continue;
                }
                var lot = new RuralAgriculturalLot(frontage.road(), frontage.center(), frontage.facing(), gate,
                        bounds, outward, RuralFarmlandVariant.choose(patternSeed), patternSeed,
                        gradedAccess);
                accepted.add(plot(accepted.size(), lot));
                break;
            }
        }
        return new RuralFarmPlanner.Result(target, List.copyOf(accepted), List.copyOf(rejections), attempts);
    }

    private static BoundingBox bounds(RuralFrontagePlanner.Frontage f, int width, int depth) {
        Direction outward = f.facing().getOpposite(), lateral = f.road().direction();
        BlockPos front = f.center().relative(outward, f.road().type().radius() + 3);
        BlockPos a = front.relative(lateral, -width / 2);
        BlockPos b = a.relative(lateral, width - 1).relative(outward, depth - 1);
        return new BoundingBox(Math.min(a.getX(), b.getX()), 0, Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), 0, Math.max(a.getZ(), b.getZ()));
    }

    private static List<BlockPos> access(BlockPos gate, BlockPos connection, Direction facing, int fieldY, int roadY) {
        var result = new ArrayList<BlockPos>();
        int length = Math.abs(gate.getX() - connection.getX()) + Math.abs(gate.getZ() - connection.getZ());
        for (int step = 0; step <= length; step++) {
            int y = fieldY + Math.round((roadY - fieldY) * step / (float) Math.max(1, length));
            BlockPos p = new BlockPos(gate.getX(), y, gate.getZ()).relative(facing, step);
            for (int side = -1; side <= 1; side++) result.add(p.relative(facing.getClockWise(), side));
        }
        return List.copyOf(result);
    }

    private static boolean safeAccess(RuralTerrainSource terrain, List<BlockPos> access) {
        for (BlockPos p : access) {
            var sample = terrain.sample(p.getX(), p.getZ());
            if (!sample.valid() || sample.water() || Math.abs(sample.surfaceY() - 1 - p.getY()) > 1) return false;
        }
        return true;
    }

    private static boolean free(BoundingBox field, List<BlockPos> access, RuralRoadSegment owner,
            BoundingBox reservation, List<RuralPlan.Road> roads, List<RuralPlan.Lot> buildings,
            List<RuralFarmPlot> farms) {
        if (!RuralAccessPlanner.inside(field, reservation)) return false;
        for (var road : roads) if (RuralAccessPlanner.intersects(field, road.bounds())) return false;
        var occupied = new ArrayList<BoundingBox>();
        occupied.add(field);
        for (BlockPos p : access) {
            BoundingBox cell = new BoundingBox(p.getX(), 0, p.getZ(), p.getX(), 0, p.getZ());
            if (!RuralAccessPlanner.inside(cell, reservation)) return false;
            for (var road : roads) if (RuralAccessPlanner.intersects(cell, road.bounds())) {
                boolean own = road.segment() != null && road.segment().type() == owner.type()
                        && road.segment().distance(owner.start().getX(), owner.start().getZ()) == 0
                        && road.segment().distance(owner.end().getX(), owner.end().getZ()) == 0;
                // Access touches only the owning envelope's outer edge, never the road interior.
                if (!own || road.segment().distance(p.getX(), p.getZ()) != owner.type().radius()) return false;
            }
            occupied.add(cell);
        }
        for (BoundingBox area : occupied) {
            for (var building : buildings) {
                // Includes the existing building earthwork/vegetation margin without changing that code.
                if (RuralAccessPlanner.intersects(expand(area, 3), building.bounds())) return false;
                if (building.access() != null) for (var b : building.access().accessBounds())
                    if (RuralAccessPlanner.intersects(expand(area, 1), b)) return false;
            }
            for (var farm : farms) for (var b : farm.agriculturalLot().occupiedBounds())
                if (RuralAccessPlanner.intersects(expand(area, 1), b)) return false;
        }
        return true;
    }

    private static BoundingBox expand(BoundingBox b, int amount) {
        return new BoundingBox(b.minX() - amount, 0, b.minZ() - amount,
                b.maxX() + amount, 0, b.maxZ() + amount);
    }

    /** Reuses the existing sparse, budgeted farm terrain contract, with an explicit gate probe. */
    private static Integer ground(RuralTerrainSource terrain, BoundingBox b, BlockPos gate) {
        var heights = new ArrayList<Integer>();
        for (int x : new int[]{b.minX(), b.getCenter().getX(), b.maxX()})
            for (int z : new int[]{b.minZ(), b.getCenter().getZ(), b.maxZ()}) {
                var sample = terrain.sample(x, z);
                if (!sample.valid() || sample.water()) return null;
                heights.add(sample.surfaceY() - 1);
            }
        var entry = terrain.sample(gate.getX(), gate.getZ());
        if (!entry.valid() || entry.water()) return null;
        heights.add(entry.surfaceY() - 1);
        heights.sort(Integer::compareTo);
        int target = heights.get(heights.size() / 2);
        if (heights.get(heights.size() - 1) - heights.get(0) > RuralFarmPlanner.MAX_RELIEF) return null;
        for (int y : heights) if (Math.abs(y - target) > RuralFarmPlanner.MAX_CELL_ADJUST) return null;
        return target;
    }

    private static RuralFarmPlot plot(int index, RuralAgriculturalLot lot) {
        var cells = new ArrayList<RuralFarmPlot.Cell>();
        var fences = new ArrayList<RuralFarmPlot.Fence>();
        var water = new ArrayList<BlockPos>();
        var paths = new ArrayList<BlockPos>();
        BoundingBox b = lot.lotBounds(), berm = lot.bermBounds(), crop = lot.cropBounds();
        for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
            BlockPos p = new BlockPos(x, b.minY(), z);
            cells.add(new RuralFarmPlot.Cell(x, z, BlockPos.asLong(x, 0, z)));
            if (RuralAgriculturalLot.edge(berm, x, z) && !lot.corner(x, z)
                    && !(x == lot.gate().getX() && z == lot.gate().getZ())) {
                boolean besideGate = Math.abs(x - lot.gate().getX()) + Math.abs(z - lot.gate().getZ()) == 1;
                // Keep every third fence position, plus gate posts, to bound the longest damage gap.
                if (besideGate || Math.floorMod(x + z, 3) == 0 || lot.roll(x, z, 1) < lot.variant().fencePercent)
                    fences.add(new RuralFarmPlot.Fence(p.above(), lot.facing()));
            }
            if (RuralAgriculturalLot.edge(lot.maintenanceBounds(), x, z)
                    && lot.roll(x, z, 2) < lot.variant().pathPercent) paths.add(p);
            if (RuralAccessPlanner.contains(crop, x, z) && lot.channel(x, z)
                    && lot.roll(lot.row(x, z), lot.along(x, z) / 3, 3) < lot.variant().waterPercent) water.add(p);
        }
        // Guarantee a passable gate approach even when the rest of the maintenance ring is degraded.
        BlockPos gateGround = lot.gate().below();
        paths.add(gateGround.relative(lot.facing()));
        paths.add(gateGround.relative(lot.facing().getOpposite()));
        var cropType = RuralFarmPlot.CropType.values()[Math.floorMod(RuralFarmlandVariant.mix(lot.patternSeed()), 4)];
        return new RuralFarmPlot(index, "agricultural_lot_v1", RuralFarmPlot.ShapeType.RECTANGLE, b, b.minY(),
                cropType, RuralFarmPlot.IrrigationType.OFFSET_CHANNEL, cells, fences,
                List.of(new RuralFarmPlot.Gate(lot.gate(), lot.gate().relative(lot.facing().getOpposite()), lot.facing())),
                water, paths, Map.of(), true, "OK", lot);
    }
}
