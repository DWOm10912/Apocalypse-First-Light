package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FluidPipeVisualManager {
    public static final int VISUAL_HOLD_TICKS = 20;
    private static final Map<ServerLevel, Map<BlockPos, VisualState>> ACTIVE_BY_LEVEL = new WeakHashMap<>();

    private FluidPipeVisualManager() {
    }

    public static void markRoute(ServerLevel level, List<BlockPos> pipePath,
                                 BlockPos sourceTankPosition, BlockPos sinkTankPosition,
                                 FluidStack fluid, boolean isFlowing) {
        if (pipePath.isEmpty() || fluid.isEmpty()) {
            return;
        }
        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        if (fluidId == null) {
            return;
        }

        long gameTime = level.getGameTime();
        Map<BlockPos, VisualState> active = ACTIVE_BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
        List<AflNetwork.FluidPipeVisualUpdate> changed = new ArrayList<>();
        for (int index = 0; index < pipePath.size(); index++) {
            BlockPos pipePosition = pipePath.get(index);
            BlockPos previousPosition = index == 0 ? sourceTankPosition : pipePath.get(index - 1);
            BlockPos nextPosition = index + 1 == pipePath.size()
                    ? sinkTankPosition
                    : pipePath.get(index + 1);
            int directionMask = directionBit(directionFrom(pipePosition, previousPosition))
                    | directionBit(directionFrom(pipePosition, nextPosition));
            BlockPos key = pipePosition.immutable();
            VisualState oldState = active.get(key);
            active.put(key, new VisualState(fluidId, directionMask, isFlowing, gameTime));
            if (oldState == null || !oldState.fluidId().equals(fluidId)
                    || oldState.directionMask() != directionMask || oldState.isFlowing() != isFlowing) {
                changed.add(new AflNetwork.FluidPipeVisualUpdate(key, fluidId, directionMask, true, isFlowing));
            }
        }
        if (!changed.isEmpty()) {
            AflNetwork.sendFluidPipeVisuals(level, changed);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            expireLevel(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ACTIVE_BY_LEVEL.remove(serverLevel);
        }
    }

    private static void expireLevel(ServerLevel level) {
        Map<BlockPos, VisualState> active = ACTIVE_BY_LEVEL.get(level);
        if (active == null || active.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        List<AflNetwork.FluidPipeVisualUpdate> cleared = new ArrayList<>();
        active.entrySet().removeIf(entry -> {
            boolean expired = gameTime - entry.getValue().lastTransferGameTime() >= VISUAL_HOLD_TICKS;
            boolean missingPipe = !level.hasChunkAt(entry.getKey())
                    || !level.getBlockState(entry.getKey()).is(AflBlocks.FLUID_PIPE.get());
            if (expired || missingPipe) {
                cleared.add(AflNetwork.FluidPipeVisualUpdate.clear(entry.getKey()));
                return true;
            }
            return false;
        });
        if (!cleared.isEmpty()) {
            AflNetwork.sendFluidPipeVisuals(level, cleared);
        }
        if (active.isEmpty()) {
            ACTIVE_BY_LEVEL.remove(level);
        }
    }

    private static Direction directionFrom(BlockPos origin, BlockPos target) {
        int deltaX = target.getX() - origin.getX();
        int deltaY = target.getY() - origin.getY();
        int deltaZ = target.getZ() - origin.getZ();
        for (Direction direction : Direction.values()) {
            if (direction.getStepX() == deltaX
                    && direction.getStepY() == deltaY
                    && direction.getStepZ() == deltaZ) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Fluid route contains non-adjacent positions: " + origin + " -> " + target);
    }

    private static int directionBit(Direction direction) {
        return 1 << direction.get3DDataValue();
    }

    private record VisualState(ResourceLocation fluidId, int directionMask, boolean isFlowing,
                               long lastTransferGameTime) {
    }
}
