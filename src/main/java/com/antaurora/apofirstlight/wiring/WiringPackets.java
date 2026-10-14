package com.antaurora.apofirstlight.wiring;

import com.antaurora.apofirstlight.wiring.client.WiringView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Building Wiring V1 packets. The client only ever sees wires while it shows them (wrench or wire in hand): it asks with
 * {@link Hello}, the server answers with the selection and each nearby chunk's nodes ({@link ChunkWires}), then sends a
 * chunk again whenever its revision changes. The wrench's attack and sneak-scroll go to the server as {@link Cut} and
 * {@link Select}; laying is the item's own use packet.
 */
public final class WiringPackets {
    private WiringPackets() {}

    /** All nodes of one chunk (empty arrays: none). */
    public record ChunkWires(int cx, int cz, int[] keys, long[] nodes) {}
    /** The wrench's circuit (its colour follows). */
    public record Selection(int circuit) {}
    /** Client: start (again) showing wires; the server forgets what it sent and sends everything near. */
    public record Hello() {}
    /** Client: the wrench attacked this block. */
    public record Cut(BlockPos pos) {}
    /** Client: sneak + scroll, +1 next circuit, -1 previous. */
    public record Select(int delta) {}
    /** Client: the pick key on another circuit's wire in the aimed cell: select that circuit. */
    public record Choose(int circuit) {}

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, ChunkWires.class, (p, b) -> {
            b.writeInt(p.cx()); b.writeInt(p.cz()); b.writeVarIntArray(p.keys()); b.writeLongArray(p.nodes());
        }, b -> new ChunkWires(b.readInt(), b.readInt(), b.readVarIntArray(), b.readLongArray()),
                (p, s) -> client(s, () -> WiringView.accept(p)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Selection.class, (p, b) -> b.writeByte(p.circuit()),
                b -> new Selection(b.readByte()),
                (p, s) -> client(s, () -> WiringView.accept(p)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Hello.class, (p, b) -> {}, b -> new Hello(),
                (p, s) -> server(s, player -> WiringSync.hello(player)), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Cut.class, (p, b) -> b.writeBlockPos(p.pos()), b -> new Cut(b.readBlockPos()),
                (p, s) -> server(s, player -> WiringTool.cut(player, p.pos())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Select.class, (p, b) -> b.writeByte(Integer.signum(p.delta())), b -> new Select(b.readByte()),
                (p, s) -> server(s, player -> WiringTool.select(player, p.delta())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Choose.class, (p, b) -> b.writeByte(p.circuit()), b -> new Choose(b.readByte()),
                (p, s) -> server(s, player -> WiringTool.choose(player, p.circuit())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return id;
    }

    private static void client(Supplier<NetworkEvent.Context> supplier, Runnable work) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> work));
        c.setPacketHandled(true);
    }

    private static void server(Supplier<NetworkEvent.Context> supplier, java.util.function.Consumer<ServerPlayer> work) {
        var c = supplier.get();
        ServerPlayer player = c.getSender();
        if (player != null) c.enqueueWork(() -> work.accept(player));
        c.setPacketHandled(true);
    }
}
