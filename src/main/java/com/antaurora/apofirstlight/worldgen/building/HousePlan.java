package com.antaurora.apofirstlight.worldgen.building;

import com.antaurora.apofirstlight.townhouse.TownHouseBlocks;
import com.antaurora.apofirstlight.townhouse.TownHouseBlocks.FacedWall;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A town house in its plan frame (Town House A and B, 2026-10-21): as the canvas, facing north (u to the right seen from
 * the street = west, w into the lot = south), u -1 .. W (the chimney's column at -1), y -2 .. H - 1, w 0 .. depth - 1.
 * The cells, the doors, stoops and partitions, and every state a block would work out from its neighbours when placed
 * (worldgen sets no neighbour updates): the walls' outside faces, the partitions' joins, skirting and ceiling.
 */
abstract class HousePlan {
    /** The plan frame's ways: u+ (to the right seen from the street), u-, w+ (into the lot), w- (toward the street). */
    static final Direction RIGHT = Direction.WEST, LEFT = Direction.EAST, BACK = Direction.SOUTH, FRONT = Direction.NORTH;

    final int W, H, depth;
    final BlockState[] cells;

    HousePlan(int width, int height, int depth) {
        this.W = width;
        this.H = height;
        this.depth = depth;
        cells = new BlockState[(W + 2) * (H + BuildingCanvas.BELOW) * depth];
    }

    static BlockState town(String id) { return TownHouseBlocks.block(id).defaultBlockState(); }

    private int index(int u, int y, int w) {
        if (u < -1 || u > W || y < -BuildingCanvas.BELOW || y >= H || w < 0 || w >= depth) return -1;
        return ((y + BuildingCanvas.BELOW) * depth + w) * (W + 2) + u + 1;
    }

    BlockState get(int u, int y, int w) {
        int i = index(u, y, w);
        return i < 0 ? null : cells[i];
    }

    void set(int u, int y, int w, BlockState s) {
        int i = index(u, y, w);
        if (i >= 0) cells[i] = s;
    }

    /** Inside the house's walls (their cells included). */
    abstract boolean inside(int u, int w);

    /** A wall's column: inside, beside (or at a corner of) a cell outside. */
    boolean wall(int u, int w) {
        if (!inside(u, w)) return false;
        for (int du = -1; du <= 1; du++)
            for (int dw = -1; dw <= 1; dw++) if (!inside(u + du, w + dw)) return true;
        return false;
    }

    /** A two-tall door (facing as the player who opened it from the swing side; hinge left). */
    void door(int u, int w, BlockState d, Direction facing) {
        BlockState s = d.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        set(u, 1, w, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(u, 2, w, s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A stoop's column: the landing at (u, w) up to the floor, the steps' upper and lower cells beyond it toward out. */
    void stoop(int u, int w, Direction out, BlockState concrete) {
        int dw = out == FRONT ? -1 : 1;
        for (int y = -2; y <= 0; y++) set(u, y, w, concrete);
        BlockState steps = town("concrete_stoop_steps").setValue(TownHouseBlocks.StoopSteps.FACING, out.getOpposite());
        for (int i = 1; i <= 2; i++) {
            for (int y = -2; y <= -1; y++) set(u, y, w + i * dw, concrete);
            set(u, 0, w + i * dw, steps.setValue(TownHouseBlocks.StoopSteps.PART, i == 1 ? TownHouseBlocks.StepPart.UPPER : TownHouseBlocks.StepPart.LOWER));
        }
    }

    void partition(int u, int w, BlockState part) {
        for (int y = 1; y <= 3; y++) set(u, y, w, part);
    }

    /** The walls' outside faces: toward a cell outside the house. */
    void faces() {
        for (int y = 1; y < H; y++)
            for (int w = 0; w < depth; w++)
                for (int u = 0; u < W; u++) {
                    BlockState s = get(u, y, w);
                    if (s == null || !(s.getBlock() instanceof FacedWall)) continue;
                    for (Direction d : Direction.Plane.HORIZONTAL) s = FacedWall.withOutside(s, d, !inside(u - d.getStepX(), w + d.getStepZ()));
                    set(u, y, w, s);
                }
    }

    /** The partitions' joins (partitions, interior doors across them, walls), skirting on the floor, the ceiling in y 3. */
    void joins() {
        for (int y = 1; y <= 3; y++)
            for (int w = 0; w < depth; w++)
                for (int u = 0; u < W; u++) {
                    BlockState s = get(u, y, w);
                    if (s == null || !(s.getBlock() instanceof TownHouseBlocks.Partition)) continue;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockState o = get(u - d.getStepX(), y, w + d.getStepZ());   // u runs west
                        boolean join = o != null && (o.getBlock() instanceof TownHouseBlocks.Partition || o.getBlock() instanceof FacedWall
                                || o.getBlock() instanceof TownHouseBlocks.HouseDoor && o.getValue(DoorBlock.FACING).getAxis() != d.getAxis());
                        s = s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), join);
                    }
                    BlockState below = get(u, y - 1, w);
                    boolean floor = below != null && !(below.getBlock() instanceof TownHouseBlocks.Partition) && !(below.getBlock() instanceof DoorBlock) && !below.isAir();
                    set(u, y, w, s.setValue(TownHouseBlocks.Partition.BASE, floor).setValue(TownHouseBlocks.Partition.CEILING, y == 3));
                }
    }

    /**
     * Onto the canvas: the canvas cleared up to H, the plan (and its chimney column) in the middle of the site's width,
     * flipped when mirrored (u flipped, states mirrored front to back: the canvas's x).
     */
    void lay(BuildingCanvas c, boolean chimney, boolean mirror) {
        int base = (c.width - W - (chimney ? 1 : 0)) / 2;
        c.fill(0, 0, 0, c.width - 1, H - 1, c.depth - 1, Blocks.AIR.defaultBlockState());
        for (int y = -BuildingCanvas.BELOW; y < H; y++)
            for (int ww = 0; ww < c.depth; ww++)
                for (int u = -1; u <= W; u++) {
                    BlockState s = get(u, y, ww);
                    if (s == null) continue;
                    int cu = mirror ? base + (W - 1 - u) : base + (chimney ? 1 : 0) + u;
                    c.set(cu, y, ww, mirror ? s.mirror(Mirror.FRONT_BACK) : s);
                }
    }
}
