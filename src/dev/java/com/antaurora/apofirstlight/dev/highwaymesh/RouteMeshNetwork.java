package com.antaurora.apofirstlight.dev.highwaymesh;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Development-only, bounded immutable recipe. No full mesh stream and no shared production channel changes. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class RouteMeshNetwork {
    private static SimpleChannel channel;
    public static final Gson GSON=new Gson();
    public static String digest(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record Snapshot(ResourceLocation dimension,boolean enabled,boolean report,BlockPos origin,UUID instance,String version,String recipe) {
        static void encode(Snapshot p,FriendlyByteBuf b){b.writeResourceLocation(p.dimension);b.writeBoolean(p.enabled);b.writeBoolean(p.report);b.writeBlockPos(p.origin);b.writeUUID(p.instance);b.writeUtf(p.version,64);b.writeUtf(p.recipe,32768);}
        static Snapshot decode(FriendlyByteBuf b){return new Snapshot(b.readResourceLocation(),b.readBoolean(),b.readBoolean(),b.readBlockPos(),b.readUUID(),b.readUtf(64),b.readUtf(32768));}
        static void handle(Snapshot p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->RouteMeshClient.receive(p)));c.setPacketHandled(true);}
    }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){
        if(FMLEnvironment.production)return;
        channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("apocalypse_firstlight","dev_highway_route_mesh"),()->"1","1"::equals,"1"::equals);
        channel.registerMessage(0,Snapshot.class,Snapshot::encode,Snapshot::decode,Snapshot::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(ServerPlayer p,Snapshot s){if(channel!=null)channel.send(PacketDistributor.PLAYER.with(()->p),s);}
    private RouteMeshNetwork(){}
}