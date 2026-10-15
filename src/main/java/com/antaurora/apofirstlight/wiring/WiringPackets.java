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
 * {@link Select}; laying is the item's own use packet. Phase 5: the route's clicks go as {@link RouteAction} and come back
 * as {@link Route}; the group picker is asked for with {@link GroupsOpen}, opened by {@link Groups}, answered by
 * {@link SetGroup}.
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
    /**
     * The player's route (phase 5; WiringTool.ROUTE_*): none, a start (and waypoints), or a preview: its end, the cells from
     * the start (BlockPos longs, a cell may come twice), the distinct cells and how many of them get new wire.
     */
    public record Route(int status, long start, long[] waypoints, long end, long[] path, int total, int fresh) {}
    /** Client: a route action (WiringTool.ROUTE_MARK / ROUTE_USE / ROUTE_CANCEL) on the aimed block. */
    public record RouteAction(int action, BlockPos pos) {}
    /** Client: open the group picker of the light or switch at pos. */
    public record GroupsOpen(BlockPos pos) {}
    /**
     * The group picker's data: the device, its tap's circuit and group, a switch or a light, whether its panel solved it, and
     * per group (0-15) the lights and switches of that solve on the circuit.
     */
    public record Groups(BlockPos pos, int circuit, boolean isSwitch, int group, boolean solved, int[] lights, int[] switches) {}
    /** Client: the picker's choice for the device at pos. */
    public record SetGroup(BlockPos pos, int group) {}

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
        channel.registerMessage(id++, Route.class, (p, b) -> {
            b.writeByte(p.status()); b.writeLong(p.start()); b.writeLongArray(p.waypoints()); b.writeLong(p.end()); b.writeLongArray(p.path());
            b.writeVarInt(p.total()); b.writeVarInt(p.fresh());
        }, b -> new Route(b.readByte(), b.readLong(), b.readLongArray(), b.readLong(), b.readLongArray(),
                b.readVarInt(), b.readVarInt()),
                (p, s) -> client(s, () -> WiringView.accept(p)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, RouteAction.class, (p, b) -> { b.writeByte(p.action()); b.writeBlockPos(p.pos()); },
                b -> new RouteAction(b.readByte(), b.readBlockPos()),
                (p, s) -> server(s, player -> WiringTool.route(player, p.action(), p.pos())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, GroupsOpen.class, (p, b) -> b.writeBlockPos(p.pos()), b -> new GroupsOpen(b.readBlockPos()),
                (p, s) -> server(s, player -> WiringTool.openGroups(player, p.pos())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Groups.class, (p, b) -> {
            b.writeBlockPos(p.pos()); b.writeByte(p.circuit()); b.writeBoolean(p.isSwitch()); b.writeByte(p.group()); b.writeBoolean(p.solved());
            b.writeVarIntArray(p.lights()); b.writeVarIntArray(p.switches());
        }, b -> new Groups(b.readBlockPos(), b.readByte(), b.readBoolean(), b.readByte(), b.readBoolean(), b.readVarIntArray(16), b.readVarIntArray(16)),
                (p, s) -> client(s, () -> WiringView.accept(p)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, SetGroup.class, (p, b) -> { b.writeBlockPos(p.pos()); b.writeByte(p.group()); },
                b -> new SetGroup(b.readBlockPos(), b.readByte()),
                (p, s) -> server(s, player -> WiringTool.setGroup(player, p.pos(), p.group())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
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
