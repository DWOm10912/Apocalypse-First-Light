package com.antaurora.apofirstlight.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Mesh Trees P4 (docs/rendering/mesh_trees_p4_formal.md, K): the trees' own channel {@code apocalypse_firstlight:mesh_tree}
 * (protocol 4; P0-P3 used a development channel). The server is the authority for every tree state; the client only
 * draws.
 * <ul>
 * <li>{@link Msg} server to client: ROOT, a root appeared or went (the client's tree index follows; the block update
 * itself comes with vanilla's packets); LOD and STATUS, diagnostics the development commands send.</li>
 * <li>{@link Chop} client to server: a chop at a trunk cell; FellingServer#chop checks everything again.</li>
 * </ul>
 */
public final class TreeNetwork {
    static final int ROOT = 0, STATUS = 1, LOD = 2;
    static final String PROTOCOL = "4";

    private static SimpleChannel channel;

    private TreeNetwork() {}

    public record Msg(int action, long pos, int value) {
        static void encode(Msg m, FriendlyByteBuf b) {
            b.writeByte(m.action);
            b.writeLong(m.pos);
            b.writeInt(m.value);
        }

        static Msg decode(FriendlyByteBuf b) {
            return new Msg(b.readByte(), b.readLong(), b.readInt());
        }

        static void handle(Msg m, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TreeClient.handle(m)));
            c.setPacketHandled(true);
        }
    }

    /** A chop at a trunk cell, client to server (FellingClient sends, FellingServer#chop decides). */
    public record Chop(long pos) {
        static void encode(Chop m, FriendlyByteBuf b) { b.writeLong(m.pos); }

        static Chop decode(FriendlyByteBuf b) { return new Chop(b.readLong()); }

        static void handle(Chop m, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            ServerPlayer sender = c.getSender();
            c.enqueueWork(() -> FellingServer.chop(sender, BlockPos.of(m.pos)));
            c.setPacketHandled(true);
        }
    }

    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(FMLCommonSetupEvent event) {
            channel = NetworkRegistry.newSimpleChannel(new ResourceLocation("apocalypse_firstlight", "mesh_tree"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
            channel.registerMessage(0, Msg.class, Msg::encode, Msg::decode, Msg::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            channel.registerMessage(1, Chop.class, Chop::encode, Chop::decode, Chop::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        }
    }

    static void sendChop(BlockPos pos) {
        if (channel != null) channel.sendToServer(new Chop(pos.asLong()));
    }

    /** A diagnostic message to one player (the development commands); false without a channel. */
    public static boolean toPlayer(ServerPlayer player, int action, int value) {
        if (channel == null) return false;
        channel.send(PacketDistributor.PLAYER.with(() -> player), new Msg(action, 0, value));
        return true;
    }

    /** A root appeared or went (server): the server's index follows, and the clients tracking its chunk are told. */
    static void rootChanged(ServerLevel level, BlockPos pos, BlockState now) {
        if (level.dimension().equals(Level.OVERWORLD)) {
            if (TreeIndex.isRoot(now)) TreeIndex.SERVER.add(pos, now); else TreeIndex.SERVER.remove(pos);
        }
        if (channel == null) return;
        channel.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), new Msg(ROOT, pos.asLong(), Block.getId(now)));
    }
}
