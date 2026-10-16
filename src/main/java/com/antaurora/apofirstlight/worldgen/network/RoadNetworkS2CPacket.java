package com.antaurora.apofirstlight.worldgen.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Server to client (C3.2; docs/worldgen/federal_highways_c3.md, 10.5 and 11): the built roads of the overworld as the
 * built network JSON (RoadNetworkBuild: control points, 2 m profile, structures; about 316 KB, deflated on the wire), so
 * the client builds the same designs and the pavement mesh the server's collision follows. Empty: no roads (clear).
 * Sent at login; the client draws only while it is in the overworld.
 */
public record RoadNetworkS2CPacket(String json) {
    public static void encode(RoadNetworkS2CPacket packet, FriendlyByteBuf buffer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream d = new DeflaterOutputStream(out)) {
            d.write(packet.json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        buffer.writeByteArray(out.toByteArray());
    }

    public static RoadNetworkS2CPacket decode(FriendlyByteBuf buffer) {
        byte[] data = buffer.readByteArray(1 << 20);
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data))) {
            byte[] json = in.readNBytes(8 << 20);
            return new RoadNetworkS2CPacket(new String(json, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("bad road network packet", e);
        }
    }

    public static void handle(RoadNetworkS2CPacket packet, java.util.function.Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.antaurora.apofirstlight.client.road.RoadClient.receive(packet.json)));
        context.setPacketHandled(true);
    }
}
