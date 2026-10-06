package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.block.FluidTankBlock;
import com.antaurora.apofirstlight.block.FuelDispenserBlock;
import com.antaurora.apofirstlight.block.UndergroundFuelTankBlock;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity;
import com.antaurora.apofirstlight.blockentity.UndergroundFuelTankBlockEntity;
import com.antaurora.apofirstlight.registry.AflFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The fuel containers bullets and fire act on (2026-10-05, docs/gameplay/fuel_fire_v1.md "第二阶段"):
 * <ul>
 *   <li>a fuel dispenser's two lines (FuelDispenserBlockEntity, 200 mB each): the gasoline line runs through the master's
 *   column (A), the diesel line through the other (B), as the sump feeds them; the fuel sits in the column's lowest cell
 *   (the hydraulics cabinet). Steel.</li>
 *   <li>a fluid tank (V2 or heat-resistant) holding gasoline or diesel: its whole cuboid. Steel.</li>
 *   <li>an underground fuel tank: its 7 x 3 x 3 cells. Fibreglass: no sparks.</li>
 *   <li>a fuel container (2026-10-05: jerry can, 60 L and 200 L drums, block/FuelCanBlock) holding fuel: its body. Steel.</li>
 * </ul>
 * A container is looked up from any of its blocks; {@link Key} names it (its {@code probe} is a block that resolves to it).
 */
public final class FuelContainers {
    public enum Kind { DISPENSER, TANK, UNDERGROUND, CAN }

    /** A container's name: a block that resolves to it (a dispenser line: its cabinet cell; a tank: its master). */
    public record Key(BlockPos probe) {}

    /**
     * @param box   the space the fuel fills from the bottom up (its level: {@link #surface})
     * @param metal steel walls: a bullet off them can strike a spark
     */
    public record Container(Kind kind, Key key, BlockPos master, boolean diesel, int amount, int capacity, AABB box, boolean metal) {
        public double fill() {
            return capacity <= 0 ? 0 : Math.min(1.0, (double) amount / capacity);
        }

        /** The fuel's surface height. */
        public double surface() {
            return box.minY + box.getYsize() * fill();
        }

        public Vec3 centre() {
            return box.getCenter();
        }
    }

    private FuelContainers() {
    }

    /** The fuel container {@code pos} is part of, or null (not a container, or one holding no fuel kind). */
    @Nullable
    public static Container at(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FuelDispenserBlock) {
            BlockPos root = FuelDispenserBlock.rootPosition(pos, state);
            Direction facing = state.getValue(FuelDispenserBlock.FACING);
            boolean diesel = state.getValue(FuelDispenserBlock.CELL).dx != 0;
            if (!(level.getBlockEntity(root) instanceof FuelDispenserBlockEntity dispenser)) return null;
            var line = dispenser.line(diesel ? FuelDispenserBlock.Grade.DIESEL : FuelDispenserBlock.Grade.GASOLINE);
            BlockPos cabinet = FuelDispenserBlock.cellPosition(root, facing, diesel ? FuelDispenserBlock.Cell.B0 : FuelDispenserBlock.Cell.A0);
            return new Container(Kind.DISPENSER, new Key(cabinet), root, diesel, line.getFluidAmount(), line.getCapacity(), new AABB(cabinet), true);
        }
        if (state.getBlock() instanceof FluidTankBlock && level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) {
            Boolean diesel = fuel(tank.getFluid());
            if (diesel == null) return null;
            FluidTankStructures.Shape shape = tank.shape();
            BlockPos m = shape.master();
            AABB box = new AABB(m.getX(), m.getY(), m.getZ(), m.getX() + shape.sx(), m.getY() + shape.sy(), m.getZ() + shape.sz());
            return new Container(Kind.TANK, new Key(m), m, diesel, tank.getFluidAmount(), tank.getCapacity(), box, true);
        }
        if (state.getBlock() instanceof com.antaurora.apofirstlight.block.FuelCanBlock
                && level.getBlockEntity(pos) instanceof com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity can) {
            Boolean diesel = fuel(can.tank().getFluid());
            if (diesel == null) return null;
            return new Container(Kind.CAN, new Key(pos.immutable()), pos.immutable(), diesel, can.tank().getFluidAmount(), can.tank().getCapacity(),
                    com.antaurora.apofirstlight.block.FuelCanBlock.fuelBox(pos, state), true);
        }
        if (state.getBlock() instanceof UndergroundFuelTankBlock block) {
            BlockPos master = UndergroundFuelTankBlock.masterPosition(pos, state);
            if (!(level.getBlockEntity(master) instanceof UndergroundFuelTankBlockEntity tank)) return null;
            Direction.Axis axis = state.getValue(UndergroundFuelTankBlock.AXIS);
            BlockPos root = UndergroundFuelTankBlock.rootPosition(pos, state);
            BlockPos a = UndergroundFuelTankBlock.cellPosition(root, axis, 0, 0, 0), b = UndergroundFuelTankBlock.cellPosition(root, axis, 6, 2, 2);
            AABB box = new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
            boolean diesel = block.fuel().isSame(AflFluids.DIESEL.get());
            return new Container(Kind.UNDERGROUND, new Key(master), master, diesel, tank.tank().getFluidAmount(), tank.tank().getCapacity(), box, false);
        }
        return null;
    }

    @Nullable
    public static Container of(Level level, Key key) {
        Container c = at(level, key.probe());
        return c != null && c.key().equals(key) ? c : null;
    }

    /** True for diesel, false for gasoline, null for anything else (or nothing). */
    @Nullable
    private static Boolean fuel(FluidStack stack) {
        if (stack.isEmpty()) return null;
        Fluid fluid = stack.getFluid();
        if (fluid.isSame(AflFluids.GASOLINE.get())) return false;
        if (fluid.isSame(AflFluids.DIESEL.get())) return true;
        return null;
    }

    /** Takes up to {@code mb} out of the container (leaked or burnt); what it got. */
    public static int drain(Level level, Container c, int mb) {
        if (mb <= 0) return 0;
        return switch (c.kind()) {
            case DISPENSER -> level.getBlockEntity(c.master()) instanceof FuelDispenserBlockEntity d
                    ? d.line(c.diesel() ? FuelDispenserBlock.Grade.DIESEL : FuelDispenserBlock.Grade.GASOLINE).drain(mb, IFluidHandler.FluidAction.EXECUTE).getAmount() : 0;
            case TANK -> level.getBlockEntity(c.master()) instanceof FluidTankBlockEntity t ? t.drainShared(mb) : 0;
            case UNDERGROUND -> level.getBlockEntity(c.master()) instanceof UndergroundFuelTankBlockEntity t
                    ? t.tank().drain(mb, IFluidHandler.FluidAction.EXECUTE).getAmount() : 0;
            case CAN -> level.getBlockEntity(c.master()) instanceof com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity t
                    ? t.tank().drain(mb, IFluidHandler.FluidAction.EXECUTE).getAmount() : 0;
        };
    }

    /** The containers that go with this one when it bursts: a dispenser's other line; else just itself. */
    public static List<Container> together(Level level, Container c) {
        List<Container> out = new ArrayList<>(List.of(c));
        if (c.kind() == Kind.DISPENSER && level.getBlockEntity(c.master()) instanceof FuelDispenserBlockEntity) {
            BlockState state = level.getBlockState(c.master());
            Direction facing = state.getValue(FuelDispenserBlock.FACING);
            Container other = at(level, FuelDispenserBlock.cellPosition(c.master(), facing, c.diesel() ? FuelDispenserBlock.Cell.A0 : FuelDispenserBlock.Cell.B0));
            if (other != null && !other.key().equals(c.key())) out.add(other);
        }
        return out;
    }

    /** A burst container is gone: its fuel (already spilt by the caller) and its blocks, with no drops. */
    public static void destroy(ServerLevel level, Container c) {
        drain(level, c, Integer.MAX_VALUE);
        switch (c.kind()) {
            case DISPENSER, UNDERGROUND, CAN -> level.removeBlock(c.master(), false);   // their removal takes every other cell along
            case TANK -> {
                for (int x = (int) c.box().minX; x < (int) c.box().maxX; x++)
                    for (int y = (int) c.box().minY; y < (int) c.box().maxY; y++)
                        for (int z = (int) c.box().minZ; z < (int) c.box().maxZ; z++) {
                            BlockPos p = new BlockPos(x, y, z);
                            if (level.getBlockState(p).getBlock() instanceof FluidTankBlock) level.removeBlock(p, false);
                        }
            }
        }
    }

    /** The containers holding fuel whose box comes within {@code reach} of {@code centre} (block entities of loaded chunks only). */
    public static List<Container> near(ServerLevel level, Vec3 centre, double reach) {
        List<Container> out = new ArrayList<>();
        java.util.Set<Key> seen = new java.util.HashSet<>();
        int c0x = ((int) Math.floor(centre.x - reach - 7)) >> 4, c1x = ((int) Math.floor(centre.x + reach + 7)) >> 4;
        int c0z = ((int) Math.floor(centre.z - reach - 7)) >> 4, c1z = ((int) Math.floor(centre.z + reach + 7)) >> 4;
        for (int cx = c0x; cx <= c1x; cx++) for (int cz = c0z; cz <= c1z; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (BlockEntity entity : new ArrayList<>(chunk.getBlockEntities().values())) {
                List<BlockPos> probes = new ArrayList<>();
                if (entity instanceof FuelDispenserBlockEntity) {
                    Direction facing = entity.getBlockState().getValue(FuelDispenserBlock.FACING);
                    probes.add(FuelDispenserBlock.cellPosition(entity.getBlockPos(), facing, FuelDispenserBlock.Cell.A0));
                    probes.add(FuelDispenserBlock.cellPosition(entity.getBlockPos(), facing, FuelDispenserBlock.Cell.B0));
                } else if (entity instanceof FluidTankBlockEntity tank && tank.shape().master().equals(entity.getBlockPos())) {
                    probes.add(entity.getBlockPos());
                } else if (entity instanceof UndergroundFuelTankBlockEntity && UndergroundFuelTankBlock.isMaster(entity.getBlockState())) {
                    probes.add(entity.getBlockPos());
                } else if (entity instanceof com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity) {
                    probes.add(entity.getBlockPos());
                }
                for (BlockPos probe : probes) {
                    Container c = at(level, probe);
                    if (c == null || c.amount() <= 0 || !seen.add(c.key())) continue;
                    if (distance(c.box(), centre) <= reach) out.add(c);
                }
            }
        }
        return out;
    }

    public static double distance(AABB box, Vec3 p) {
        double dx = Math.max(0, Math.max(box.minX - p.x, p.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - p.y, p.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - p.z, p.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
