package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/** Saved Agricultural Lot V1. Bounds include the outer soil transition; access is also occupied. */
public record RuralAgriculturalLot(RuralRoadSegment sourceRoad, BlockPos frontage, Direction facing,
                                   BlockPos gate, BoundingBox lotBounds, Direction rowDirection,
                                   RuralFarmlandVariant variant, long patternSeed, List<BlockPos> access) {
    public RuralAgriculturalLot {
        access = List.copyOf(access);
    }

    public BoundingBox bermBounds() { return inset(lotBounds, 1); }
    public BoundingBox maintenanceBounds() { return inset(lotBounds, 2); }
    public BoundingBox cropBounds() { return inset(lotBounds, 3); }
    public List<BoundingBox> occupiedBounds() {
        var result = new java.util.ArrayList<BoundingBox>();
        result.add(lotBounds);
        for (BlockPos p : access) result.add(new BoundingBox(p.getX(), 0, p.getZ(), p.getX(), 0, p.getZ()));
        return List.copyOf(result);
    }

    public int roll(int x, int z, long salt) {
        return Math.floorMod(RuralFarmlandVariant.mix(patternSeed ^ salt
                ^ ((long) variant.ordinal() * 0xD6E8FEB86659FD93L)
                ^ RuralFarmlandVariant.mix((long) (x - lotBounds.minX()) * 0x9E3779B97F4A7C15L)
                ^ RuralFarmlandVariant.mix((long) (z - lotBounds.minZ()) * 0xA0761D6478BD642FL)), 100);
    }

    public int row(int x, int z) {
        return rowDirection.getAxis() == Direction.Axis.Z ? x - cropBounds().minX() : z - cropBounds().minZ();
    }

    public int along(int x, int z) {
        return rowDirection.getAxis() == Direction.Axis.Z ? z - cropBounds().minZ() : x - cropBounds().minX();
    }

    public boolean channel(int x, int z) { return row(x, z) % 5 == 2; }

    public static BoundingBox inset(BoundingBox b, int amount) {
        return new BoundingBox(b.minX() + amount, b.minY(), b.minZ() + amount,
                b.maxX() - amount, b.maxY(), b.maxZ() - amount);
    }

    public static boolean edge(BoundingBox b, int x, int z) {
        return RuralAccessPlanner.contains(b, x, z)
                && (x == b.minX() || x == b.maxX() || z == b.minZ() || z == b.maxZ());
    }

    public boolean corner(int x, int z) {
        BoundingBox b = bermBounds();
        return (x == b.minX() || x == b.maxX()) && (z == b.minZ() || z == b.maxZ());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("RoadType", sourceRoad.type().name());
        tag.putLong("RoadStart", sourceRoad.start().asLong());
        tag.putLong("RoadEnd", sourceRoad.end().asLong());
        tag.putLong("Frontage", frontage.asLong());
        tag.putString("Facing", facing.name());
        tag.putLong("Gate", gate.asLong());
        tag.putIntArray("Bounds", new int[]{lotBounds.minX(), lotBounds.minY(), lotBounds.minZ(),
                lotBounds.maxX(), lotBounds.maxY(), lotBounds.maxZ()});
        tag.putString("RowDirection", rowDirection.name());
        tag.putString("Variant", variant.name());
        tag.putLong("PatternSeed", patternSeed);
        ListTag cells = new ListTag();
        for (BlockPos pos : access) {
            CompoundTag cell = new CompoundTag();
            cell.putLong("Pos", pos.asLong());
            cells.add(cell);
        }
        tag.put("Access", cells);
        return tag;
    }

    public static RuralAgriculturalLot load(CompoundTag tag) {
        int[] b = tag.getIntArray("Bounds");
        if (b.length != 6) throw new IllegalArgumentException("Invalid agricultural lot bounds");
        ListTag cells = tag.getList("Access", 10);
        var access = new java.util.ArrayList<BlockPos>();
        for (int i = 0; i < cells.size(); i++) access.add(BlockPos.of(cells.getCompound(i).getLong("Pos")));
        return new RuralAgriculturalLot(new RuralRoadSegment(BlockPos.of(tag.getLong("RoadStart")),
                BlockPos.of(tag.getLong("RoadEnd")), RuralRoadType.valueOf(tag.getString("RoadType"))),
                BlockPos.of(tag.getLong("Frontage")), Direction.valueOf(tag.getString("Facing")),
                BlockPos.of(tag.getLong("Gate")), new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]),
                Direction.valueOf(tag.getString("RowDirection")),
                RuralFarmlandVariant.valueOf(tag.getString("Variant")), tag.getLong("PatternSeed"), access);
    }
}
