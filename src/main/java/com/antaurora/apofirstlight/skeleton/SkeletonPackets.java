package com.antaurora.apofirstlight.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.skeleton.SkeletonGunStance;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Player skeleton V1 P1 (docs/dev/player_skeleton_v1_design.md 6): the smallest piece of the action state, whether a
 * player aims down the sights, so the others draw the same stance. The client says when its own aim changes; the server
 * keeps it and tells the clients that track the player (and a client that starts tracking a player who aims). Only the
 * look depends on it: the shot's spread still uses the aim flag sent with each shot (NativeTriggerPacket).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class SkeletonPackets {
    /** Client -> server: the own aim changed. */
    public record Aim(boolean aiming) {}

    /** Server -> clients: a player's aim. */
    public record AimOf(int entity, boolean aiming) {}

    private static final Map<ServerPlayer, Boolean> AIMING = new WeakHashMap<>();

    private SkeletonPackets() {
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Aim.class, (p, b) -> b.writeBoolean(p.aiming()), b -> new Aim(b.readBoolean()),
                SkeletonPackets::onAim, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, AimOf.class, (p, b) -> {
            b.writeVarInt(p.entity());
            b.writeBoolean(p.aiming());
        }, b -> new AimOf(b.readVarInt(), b.readBoolean()), (p, s) -> {
            NetworkEvent.Context context = s.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkeletonGunStance.remoteAim(p.entity(), p.aiming())));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    private static void onAim(Aim packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            Boolean before = AIMING.put(player, packet.aiming());
            if (before == null || before != packet.aiming())
                AflNetwork.skeletonToTracking(player, new AimOf(player.getId(), packet.aiming()));
        });
        context.setPacketHandled(true);
    }

    /** The player's aim as last sent (Aim): a reload started while aiming plays the gun's ADS variant (NativeGunActions). */
    public static boolean aiming(ServerPlayer player) {
        return Boolean.TRUE.equals(AIMING.get(player));
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer tracker
                && Boolean.TRUE.equals(AIMING.get(target)))
            AflNetwork.skeletonTo(tracker, new AimOf(target.getId(), true));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AIMING.remove(player);
    }
}
