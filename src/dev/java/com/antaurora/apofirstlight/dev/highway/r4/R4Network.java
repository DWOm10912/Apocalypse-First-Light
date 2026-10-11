package com.antaurora.apofirstlight.dev.highway.r4;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Development-only: the segment's recipe (a few kB) to clients, which rebuild the pavement mesh from it. Never vertices. */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class R4Network {
    private static SimpleChannel channel;

    public record Snapshot(ResourceLocation dimension, boolean enabled, boolean preview, boolean report, UUID instance, String version, String recipe) {
        static void encode(Snapshot p, FriendlyByteBuf b) {
            b.writeResourceLocation(p.dimension);
            b.writeBoolean(p.enabled);
            b.writeBoolean(p.preview);
            b.writeBoolean(p.report);
            b.writeUUID(p.instance);
            b.writeUtf(p.version, 64);
            b.writeUtf(p.recipe, 32768);
        }

        static Snapshot decode(FriendlyByteBuf b) {
            return new Snapshot(b.readResourceLocation(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readUUID(), b.readUtf(64), b.readUtf(32768));
        }

        static void handle(Snapshot p, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> R4Client.receive(p)));
            c.setPacketHandled(true);
        }
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        if (FMLEnvironment.production) return;
        channel = NetworkRegistry.newSimpleChannel(new ResourceLocation("apocalypse_firstlight", "dev_highway_r4"), () -> "1", "1"::equals, "1"::equals);
        channel.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ServerPlayer player, Snapshot snapshot) {
        if (channel != null) channel.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }

    private R4Network() {}
}
