package com.antaurora.apofirstlight.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.UUID;
import java.util.function.Supplier;

public record CrowbarSmashPacket(UUID player,UUID action,BlockPos target,int phase) {
    static void encode(CrowbarSmashPacket p,FriendlyByteBuf b){b.writeUUID(p.player);b.writeUUID(p.action);b.writeBlockPos(p.target);b.writeByte(p.phase);}
    static CrowbarSmashPacket decode(FriendlyByteBuf b){return new CrowbarSmashPacket(b.readUUID(),b.readUUID(),b.readBlockPos(),b.readUnsignedByte());}
    static void handle(CrowbarSmashPacket p,Supplier<NetworkEvent.Context> supplier){
        var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.antaurora.apofirstlight.client.CrowbarSmashClient.receive(p)));c.setPacketHandled(true);
    }
    public record Cancel(UUID action) {
        static void encode(Cancel p,FriendlyByteBuf b){b.writeUUID(p.action);}
        static Cancel decode(FriendlyByteBuf b){return new Cancel(b.readUUID());}
        static void handle(Cancel p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{
            if(c.getSender()!=null)com.antaurora.apofirstlight.interaction.CrowbarSmashAction.cancel(c.getSender(),p.action);
        });c.setPacketHandled(true);}
    }
}
