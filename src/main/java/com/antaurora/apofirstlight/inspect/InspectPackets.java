package com.antaurora.apofirstlight.inspect;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Item inspect V1 (docs/gameplay/item_inspect_v1_design.md 7): a hotspot click to the server ({@link Request}) and its
 * answer ({@link Result}); the screen plays the action meanwhile and keeps the new state only when the server agrees.
 */
public final class InspectPackets {
    private InspectPackets() {}

    /** id: the screen's own count, echoed in the result; source and candidate as InspectActions names places. */
    public record Request(int id, int source, int action, int candidate) {
        static void encode(Request p, FriendlyByteBuf b) {
            b.writeVarInt(p.id);
            b.writeInt(p.source);
            b.writeByte(p.action);
            b.writeInt(p.candidate);
        }

        static Request decode(FriendlyByteBuf b) {
            return new Request(b.readVarInt(), b.readInt(), b.readByte(), b.readInt());
        }
    }

    public record Result(int id, boolean ok) {
        static void encode(Result p, FriendlyByteBuf b) {
            b.writeVarInt(p.id);
            b.writeBoolean(p.ok);
        }

        static Result decode(FriendlyByteBuf b) {
            return new Result(b.readVarInt(), b.readBoolean());
        }
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Request.class, Request::encode, Request::decode, InspectPackets::handleRequest,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Result.class, Result::encode, Result::decode, InspectPackets::handleResult,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    private static void handleRequest(Request packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> {
            var player = c.getSender();
            var action = InspectActions.Action.of(packet.action());
            if (player == null) return;
            boolean ok = action != null && InspectActions.apply(player, packet.source(), action, packet.candidate());
            com.antaurora.apofirstlight.network.AflNetwork.inspect(player, new Result(packet.id(), ok));
        });
        c.setPacketHandled(true);
    }

    private static void handleResult(Result packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.antaurora.apofirstlight.client.inspect.ItemInspectScreen.result(packet.id(), packet.ok())));
        c.setPacketHandled(true);
    }
}
