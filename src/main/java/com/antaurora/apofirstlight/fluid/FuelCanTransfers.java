package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.block.FluidPipeBlock;
import com.antaurora.apofirstlight.block.FuelSumpCoverBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Where hand-moved fuel goes in or comes out (2026-10-05, docs/models/fuel_containers_v1.md): a pouring can, the dispenser
 * filling a container, the hand pump's suction and its hose.
 * <ul>
 *   <li>an open fuel fill cover: the tank its riser leads to (the first fluid handler down its pipe run; the fill cell of an
 *   underground fuel tank hands over the tank's own handler). Shut, nothing.</li>
 *   <li>another fill opening (FuelPourTarget, 2026-10-09: the diesel generator's fill box): its own handler while it is open.</li>
 *   <li>any other block entity with a fluid handler: on the face reached, else its unsided one (a fluid tank's top port,
 *   a fuel container).</li>
 * </ul>
 */
public final class FuelCanTransfers {
    private static final int MAX_PIPES = 64;

    private FuelCanTransfers() {
    }

    @Nullable
    public static IFluidHandler handler(ServerLevel level, BlockPos pos, Direction face) {
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FuelSumpCoverBlock cover && cover.kind() == FuelSumpCoverBlock.Kind.FILL) {
            return state.getValue(FuelSumpCoverBlock.OPEN) ? throughPipes(level, pos, Direction.DOWN) : null;
        }
        if (state.getBlock() instanceof FuelPourTarget target) return target.pourHandler(level, pos, state);
        if (state.getBlock() instanceof com.antaurora.apofirstlight.block.FuelCanBlock && !state.getValue(com.antaurora.apofirstlight.block.FuelCanBlock.OPEN)) return null;   // the cap on
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) return null;
        IFluidHandler sided = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, face).resolve().orElse(null);
        return sided != null ? sided : entity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).resolve().orElse(null);
    }

    /** What a handler holds (its first tank), for messages. */
    public static FluidStack contents(IFluidHandler handler) {
        return handler.getTanks() > 0 ? handler.getFluidInTank(0) : FluidStack.EMPTY;
    }

    /** The nearest fluid handler at the far end of the pipe run leaving {@code from} through {@code face}, or null. */
    @Nullable
    public static IFluidHandler throughPipes(ServerLevel level, BlockPos from, Direction face) {
        BlockPos first = from.relative(face);
        if (!level.isLoaded(first) || !FluidPipeBlock.isPipe(level.getBlockState(first))
                || !FluidPipeBlock.canPipeEdgeConnect(level, first, from, face.getOpposite())) return null;
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        pending.add(first);
        seen.add(first);
        seen.add(from);
        while (!pending.isEmpty() && seen.size() < MAX_PIPES) {
            BlockPos pipe = pending.poll();
            for (Direction d : Direction.values()) {
                BlockPos next = pipe.relative(d);
                if (seen.contains(next) || !level.isLoaded(next) || !FluidPipeBlock.canPipeEdgeConnect(level, pipe, next, d)) continue;
                if (FluidPipeBlock.isPipe(level.getBlockState(next))) {
                    seen.add(next);
                    pending.add(next);
                    continue;
                }
                BlockEntity entity = level.getBlockEntity(next);
                IFluidHandler handler = entity == null ? null : entity.getCapability(ForgeCapabilities.FLUID_HANDLER, d.getOpposite()).resolve().orElse(null);
                if (handler != null) return handler;
            }
        }
        return null;
    }
}
