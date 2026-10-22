package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Rock R1a (docs/gameplay/rock_r1_stones_boulders_design.md 11.2): the server's boulder index and the clients'. The server
 * indexes the overworld's roots per loaded chunk and follows every root that appears or goes; a client indexes the chunks
 * it receives and hears of every root change from {@link RootPacket} (the block update itself comes with vanilla's
 * packets; a client runs no onPlace / onRemove).
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class BoulderNetwork {
    private BoulderNetwork() {}

    /** Server to client: a root appeared, changed or went (state id; not a root: gone). */
    public record RootPacket(long pos, int state) {
        public static void encode(RootPacket p, FriendlyByteBuf b) {
            b.writeLong(p.pos);
            b.writeVarInt(p.state);
        }

        public static RootPacket decode(FriendlyByteBuf b) {
            return new RootPacket(b.readLong(), b.readVarInt());
        }

        public static void handle(RootPacket p, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BoulderClient.rootChanged(BlockPos.of(p.pos), Block.stateById(p.state))));
            c.setPacketHandled(true);
        }
    }

    /** A root appeared, changed or went (server): the server's index follows, and the clients tracking its chunk are told. */
    static void rootChanged(ServerLevel level, BlockPos pos, BlockState now) {
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        if (BoulderIndex.isRoot(now)) BoulderIndex.SERVER.add(pos, now); else BoulderIndex.SERVER.remove(pos);
        AflNetwork.sendTrackingChunk(level.getChunkAt(pos), new RootPacket(pos.asLong(), Block.getId(now)));
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!BoulderBlocks.ready() || !(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)
                || !(event.getChunk() instanceof LevelChunk chunk)) return;
        BoulderIndex.SERVER.load(chunk);
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) BoulderIndex.SERVER.unload(event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        BoulderIndex.SERVER.clear();
    }
}
