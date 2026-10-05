package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.FluidTankBlock;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fluid Tank V2 structures (docs/models/fluid_tank_v2.md, user 2026-10-05). Connected tank blocks join into one tank
 * only when together they fill a complete cuboid (a x b x h, at most {@link #MAX_SIDE} x {@link #MAX_SIDE} x
 * {@link #MAX_HEIGHT}) and hold at most one kind of fluid; otherwise every block is its own one-cell tank. A joined tank
 * keeps all its fluid in its master cell (the cuboid's lowest north-west cell, capacity cells x 800 mB); its shape lives
 * in the block states (FluidTankBlock's six joined flags), so client and server read it the same way. When a structure
 * changes (a block placed, broken or loaded), each old joined tank first hands every cell its share (filled from the
 * bottom layer up: {@link #share}), then the component is joined again if it is a complete cuboid, so breaking a joined
 * tank leaves single tanks that keep their fluid, and rebuilding the cuboid joins them again.
 */
public final class FluidTankStructures {
    public static final int MAX_SIDE = 5;
    public static final int MAX_HEIGHT = 16;
    /** A connected group larger than this is left alone (it can never be one tank). */
    public static final int SCAN_LIMIT = 1024;
    private static boolean scanLimitWarned;

    private FluidTankStructures() {
    }

    /** A tank's extent: its master (lowest north-west) cell and its size along x, y, z. */
    public record Shape(BlockPos master, int sx, int sy, int sz) {
        public int cells() {
            return sx * sy * sz;
        }

        public int layerCells() {
            return sx * sz;
        }

        public int capacity() {
            return cells() * FluidTankBlockEntity.CAPACITY_MB;
        }

        public int layerOf(BlockPos position) {
            return position.getY() - master.getY();
        }

        public List<BlockPos> positions() {
            List<BlockPos> out = new ArrayList<>(cells());
            for (int y = 0; y < sy; y++) for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) out.add(master.offset(x, y, z));
            return out;
        }
    }

    private static boolean joined(BlockState state, Direction direction) {
        return state.getBlock() instanceof FluidTankBlock && state.getValue(FluidTankBlock.JOINED.get(direction));
    }

    /**
     * The tank {@code position} belongs to, read from the joined flags (client or server). {@code state} stands in for the
     * block state at {@code position} (the old state while it is being removed).
     */
    public static Shape shapeOf(BlockGetter level, BlockPos position, BlockState state) {
        BlockPos master = position;
        for (Direction step : new Direction[]{Direction.WEST, Direction.DOWN, Direction.NORTH}) {
            for (int n = 0; n < MAX_HEIGHT && joined(stateAt(level, master, position, state), step)
                    && stateAt(level, master.relative(step), position, state).getBlock() instanceof FluidTankBlock; n++) {
                master = master.relative(step);
            }
        }
        int[] size = new int[3];
        Direction[] grow = {Direction.EAST, Direction.UP, Direction.SOUTH};
        for (int k = 0; k < 3; k++) {
            BlockPos cursor = master;
            size[k] = 1;
            while (size[k] < (k == 1 ? MAX_HEIGHT : MAX_SIDE) && joined(stateAt(level, cursor, position, state), grow[k])
                    && stateAt(level, cursor.relative(grow[k]), position, state).getBlock() instanceof FluidTankBlock) {
                cursor = cursor.relative(grow[k]);
                size[k]++;
            }
        }
        return new Shape(master.immutable(), size[0], size[1], size[2]);
    }

    private static BlockState stateAt(BlockGetter level, BlockPos at, BlockPos position, BlockState state) {
        return at.equals(position) ? state : level.getBlockState(at);
    }

    /**
     * The share of {@code total} mB that cell {@code index} (0..cells-1 in {@link Shape#positions()} order) holds: the
     * fluid fills the bottom layer first, each layer is shared evenly, the remainder to its first cells.
     */
    public static int share(int total, Shape shape, int index) {
        int layer = index / shape.layerCells(), inLayer = index % shape.layerCells(), cells = shape.layerCells();
        long layerCapacity = (long) cells * FluidTankBlockEntity.CAPACITY_MB;
        int layerAmount = (int) Math.max(0, Math.min(layerCapacity, (long) total - layer * layerCapacity));
        return layerAmount / cells + (inLayer < layerAmount % cells ? 1 : 0);
    }

    /** The share of {@code position}'s cell. */
    public static int shareAt(int total, Shape shape, BlockPos position) {
        BlockPos d = position.subtract(shape.master());
        return share(total, shape, (d.getY() * shape.sx() + d.getX()) * shape.sz() + d.getZ());
    }

    // ---------------------------------------------------------------- server

    /** Re-forms the tanks of {@code origin}'s connected group (see the class comment). */
    public static void rebuild(ServerLevel level, BlockPos origin) {
        if (!isTank(level, origin)) return;
        Set<BlockPos> cells = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        cells.add(origin.immutable());
        queue.add(origin.immutable());
        while (!queue.isEmpty()) {
            BlockPos position = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = position.relative(direction);
                if (!level.isLoaded(next)) {   // part of the group may be unloaded: try again later, change nothing
                    level.scheduleTick(origin, level.getBlockState(origin).getBlock(), 20);
                    return;
                }
                if (!cells.contains(next) && isTank(level, next)) {
                    if (cells.size() >= SCAN_LIMIT) {
                        if (!scanLimitWarned) {
                            scanLimitWarned = true;
                            ApocalypseFirstLight.LOGGER.warn("[AFL FLUID] More than {} connected fluid tanks at {}; left as they are", SCAN_LIMIT, origin);
                        }
                        return;
                    }
                    cells.add(next.immutable());
                    queue.add(next.immutable());
                }
            }
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : cells) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        Shape box = new Shape(new BlockPos(minX, minY, minZ), maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        boolean cuboid = cells.size() > 1 && cells.size() == box.cells() && box.sx() <= MAX_SIDE && box.sz() <= MAX_SIDE && box.sy() <= MAX_HEIGHT;

        // already right? (a merged tank exactly this cuboid, or all singles): leave the fluid where it is
        if (cuboid ? cells.stream().allMatch(p -> shapeOf(level, p, level.getBlockState(p)).equals(box))
                : cells.stream().allMatch(p -> shapeOf(level, p, level.getBlockState(p)).cells() == 1)) return;

        explode(level, cells);
        if (cuboid && oneFluid(level, cells)) merge(level, box);
        else for (BlockPos p : cells) setJoined(level, p, null);
        for (BlockPos p : cells) if (level.getBlockEntity(p) instanceof FluidTankBlockEntity tank) tank.syncAfterTopologyChange();
    }

    /**
     * A tank block is going away ({@code oldState} at {@code position}, its block entity {@code removed}): its joined tank
     * becomes single tanks, each with its share; the removed cell's share goes to {@code removed}'s drop when a survival
     * player broke it (FluidTankBlockEntity#preparePlayerBreakDrop), else it is lost. Then the neighbours re-form.
     */
    public static void handleRemoved(ServerLevel level, BlockPos position, BlockState oldState, FluidTankBlockEntity removed) {
        Shape shape = shapeOf(level, position, oldState);
        FluidTankBlockEntity master = shape.master().equals(position) ? removed
                : level.getBlockEntity(shape.master()) instanceof FluidTankBlockEntity m ? m : null;
        FluidStack total = master == null ? FluidStack.EMPTY : master.takeContents();
        List<BlockPos> positions = shape.positions();
        for (int i = 0; i < positions.size(); i++) {
            BlockPos p = positions.get(i);
            FluidStack part = slice(total, share(total.getAmount(), shape, i));
            if (p.equals(position)) {
                removed.keepForDrop(part);
                continue;
            }
            if (level.getBlockEntity(p) instanceof FluidTankBlockEntity tank) {
                tank.setContents(FluidTankBlockEntity.CAPACITY_MB, part);
                setJoined(level, p, null);
            }
        }
        for (Direction direction : Direction.values()) {
            BlockPos next = position.relative(direction);
            if (isTank(level, next)) rebuild(level, next);
        }
        for (BlockPos p : positions) if (!p.equals(position) && level.getBlockEntity(p) instanceof FluidTankBlockEntity tank) tank.syncAfterTopologyChange();
    }

    /** Every joined tank among {@code cells} hands each of its cells its share; afterwards every cell holds its own fluid. */
    private static void explode(ServerLevel level, Set<BlockPos> cells) {
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos p : cells) {
            Shape shape = shapeOf(level, p, level.getBlockState(p));
            if (shape.cells() == 1 || !done.add(shape.master())) continue;
            if (!(level.getBlockEntity(shape.master()) instanceof FluidTankBlockEntity master)) continue;
            FluidStack total = master.takeContents();
            List<BlockPos> positions = shape.positions();
            for (int i = 0; i < positions.size(); i++) {
                if (level.getBlockEntity(positions.get(i)) instanceof FluidTankBlockEntity tank) {
                    tank.setContents(FluidTankBlockEntity.CAPACITY_MB, slice(total, share(total.getAmount(), shape, i)));
                }
            }
        }
    }

    private static boolean oneFluid(ServerLevel level, Set<BlockPos> cells) {
        FluidStack kind = FluidStack.EMPTY;
        for (BlockPos p : cells) {
            if (!(level.getBlockEntity(p) instanceof FluidTankBlockEntity tank)) continue;
            FluidStack own = tank.ownContents();
            if (own.isEmpty()) continue;
            if (kind.isEmpty()) kind = own;
            else if (!kind.isFluidEqual(own)) return false;
        }
        return true;
    }

    private static void merge(ServerLevel level, Shape box) {
        FluidStack total = FluidStack.EMPTY;
        for (BlockPos p : box.positions()) {
            if (!(level.getBlockEntity(p) instanceof FluidTankBlockEntity tank)) continue;
            FluidStack own = tank.takeContents();
            if (own.isEmpty()) continue;
            if (total.isEmpty()) total = own.copy();
            else total.grow(own.getAmount());
        }
        for (BlockPos p : box.positions()) {
            if (!(level.getBlockEntity(p) instanceof FluidTankBlockEntity tank)) continue;
            boolean master = p.equals(box.master());
            tank.setContents(master ? box.capacity() : FluidTankBlockEntity.CAPACITY_MB, master ? total : FluidStack.EMPTY);
            setJoined(level, p, box);
        }
    }

    /** Sets {@code position}'s joined flags: toward the neighbours inside {@code box}, or none (null). */
    private static void setJoined(ServerLevel level, BlockPos position, Shape box) {
        BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof FluidTankBlock)) return;
        BlockState next = state;
        for (Direction direction : Direction.values()) {
            BlockPos n = position.relative(direction).subtract(box == null ? position : box.master());
            boolean inside = box != null && n.getX() >= 0 && n.getY() >= 0 && n.getZ() >= 0 && n.getX() < box.sx() && n.getY() < box.sy() && n.getZ() < box.sz();
            next = next.setValue(FluidTankBlock.JOINED.get(direction), inside);
        }
        if (next != state) level.setBlock(position, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static FluidStack slice(FluidStack total, int amount) {
        if (total.isEmpty() || amount <= 0) return FluidStack.EMPTY;
        FluidStack part = total.copy();
        part.setAmount(amount);
        return part;
    }

    private static boolean isTank(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockState(position).getBlock() instanceof FluidTankBlock;
    }
}
