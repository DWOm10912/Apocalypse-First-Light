package com.antaurora.apofirstlight.dev.highwaymesh;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

/** Small development-only scene descriptor, never mesh/vertex streaming and never the production gun channel. */
public final class DevHighwayMeshNetwork {
    private static SimpleChannel channel;
    public record Snapshot(ResourceLocation dimension,boolean enabled,boolean preview,boolean report,BlockPos origin,UUID instance,String version) {
        static void encode(Snapshot p,FriendlyByteBuf b){b.writeResourceLocation(p.dimension);b.writeBoolean(p.enabled);b.writeBoolean(p.preview);b.writeBoolean(p.report);b.writeBlockPos(p.origin);b.writeUUID(p.instance);b.writeUtf(p.version,64);}
        static Snapshot decode(FriendlyByteBuf b){return new Snapshot(b.readResourceLocation(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBlockPos(),b.readUUID(),b.readUtf(64));}
        static void handle(Snapshot p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->DevHighwayMeshClient.receive(p)));c.setPacketHandled(true);}
    }
    public static void register(){
        channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("apocalypse_firstlight","dev_highway_mesh"),()->"1","1"::equals,"1"::equals);
        channel.registerMessage(0,Snapshot.class,Snapshot::encode,Snapshot::decode,Snapshot::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(ServerPlayer player,Snapshot snapshot){if(channel!=null)channel.send(PacketDistributor.PLAYER.with(()->player),snapshot);}
    private DevHighwayMeshNetwork(){}
}
