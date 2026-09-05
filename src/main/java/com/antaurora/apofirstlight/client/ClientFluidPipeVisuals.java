package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientFluidPipeVisuals {
    private static final Map<BlockPos, VisualState> ACTIVE = new HashMap<>();

    private ClientFluidPipeVisuals() {
    }

    public static void apply(List<AflNetwork.FluidPipeVisualUpdate> updates) {
        for (AflNetwork.FluidPipeVisualUpdate update : updates) {
            if (!update.active()) {
                ACTIVE.remove(update.position());
                continue;
            }
            var fluid = ForgeRegistries.FLUIDS.getValue(update.fluidId());
            if (fluid == null) {
                ACTIVE.remove(update.position());
                continue;
            }
            ACTIVE.put(update.position().immutable(),
                    new VisualState(new FluidStack(fluid, 1), update.directionMask(), update.isFlowing()));
        }
    }

    public static Map<BlockPos, VisualState> snapshot() {
        return Map.copyOf(ACTIVE);
    }

    @SubscribeEvent
    public static void clearOnDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void clearOnLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ACTIVE.clear();
        }
    }

    @SubscribeEvent
    public static void clearOnChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        ChunkPos chunkPosition = event.getChunk().getPos();
        ACTIVE.keySet().removeIf(position -> new ChunkPos(position).equals(chunkPosition));
    }

    public record VisualState(FluidStack fluid, int directionMask, boolean isFlowing) {
        public VisualState {
            fluid = fluid.copy();
        }

        public boolean uses(DirectionBit direction) {
            return (directionMask & direction.mask()) != 0;
        }
    }

    public enum DirectionBit {
        DOWN(1 << 0),
        UP(1 << 1),
        NORTH(1 << 2),
        SOUTH(1 << 3),
        WEST(1 << 4),
        EAST(1 << 5);

        private final int mask;

        DirectionBit(int mask) {
            this.mask = mask;
        }

        public int mask() {
            return mask;
        }
    }
}
