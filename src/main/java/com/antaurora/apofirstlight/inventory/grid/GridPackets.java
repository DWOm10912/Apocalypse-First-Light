package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Grid Inventory V0 packets (docs/ui/afl_inventory_grid_v0_plan.md 1, 10): the item sizes, the pockets and the quick
 * bindings / hand state (Loadout) to the owner's client; the inventory page's grid clicks and the quick binding keys to the
 * server, which checks and does them (GridActions, Loadout). The worn rig's
 * and backpack's contents reach the client with Curios' own slot sync.
 */
public final class GridPackets {
    /** Server: every item size that is not 1x1. */
    public record Sizes(Map<ResourceLocation, ItemSizes.Size> sizes) {}

    /** Server: the owner's pockets. */
    public record PocketsSync(ListTag items) {}

    /**
     * Client: one grid action on the inventory page (GridActions.Kind), on a target (PlayerGrids.Target), at a cell, with
     * the carried item's rotation; {@code extra}: the mouse button, a hotbar index or an overflow index, by kind.
     */
    public record Action(int kind, int target, int section, int x, int y, boolean rotated, int extra) {}

    /** Server: the owner's quick bindings and hand state (Loadout). */
    public record LoadoutSync(Loadout.ClientState state) {}

    /** Client: key 4 + {@code index} pressed in the world (a quick binding, Loadout). */
    public record UseBind(int index) {}

    /**
     * The maintenance bench's gun picker: put the gun at a source (a hotbar index or a GridSource) onto the bench open as
     * containerId, if it is still the expected stack (vanilla's button packet carries one byte, too little for a grid place).
     */
    public record BenchInsert(int containerId, int source, net.minecraft.world.item.ItemStack expected) {}

    /** Server -> the players tracking a carrier: the weapons drawn on its body (CarriedWeaponsSync). */
    public record CarriedSync(int entity, CarriedWeaponsSync.Shown shown) {}

    private static SimpleChannel channel;

    private GridPackets() {}

    public static int register(SimpleChannel channel, int id) {
        GridPackets.channel = channel;
        channel.registerMessage(id++, Sizes.class, (p, b) -> {
            b.writeVarInt(p.sizes().size());
            p.sizes().forEach((item, size) -> {
                b.writeResourceLocation(item);
                b.writeByte(size.w());
                b.writeByte(size.h());
            });
        }, b -> {
            int n = b.readVarInt();
            Map<ResourceLocation, ItemSizes.Size> sizes = new HashMap<>();
            for (int i = 0; i < n; i++) sizes.put(b.readResourceLocation(), new ItemSizes.Size(b.readByte(), b.readByte()));
            return new Sizes(sizes);
        }, (p, s) -> client(s, () -> {
            ItemSizes.receive(p.sizes());
            PlayerGrids.invalidateClientCache();
        }), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, PocketsSync.class, (p, b) -> {
            CompoundTag tag = new CompoundTag();
            tag.put("Items", p.items());
            b.writeNbt(tag);
        }, b -> {
            CompoundTag tag = b.readNbt();
            return new PocketsSync(tag == null ? new ListTag() : tag.getList("Items", Tag.TAG_COMPOUND));
        }, (p, s) -> client(s, () -> PlayerGrids.receivePockets(p.items())), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Action.class, (p, b) -> {
            b.writeByte(p.kind());
            b.writeByte(p.target());
            b.writeByte(p.section());
            b.writeByte(p.x());
            b.writeByte(p.y());
            b.writeBoolean(p.rotated());
            b.writeVarInt(p.extra());
        }, b -> new Action(b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readBoolean(), b.readVarInt()),
                (p, s) -> {
                    NetworkEvent.Context context = s.get();
                    ServerPlayer player = context.getSender();
                    if (player != null) context.enqueueWork(() -> GridActions.handle(player, p));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, LoadoutSync.class, (p, b) -> {
            var state = p.state();
            for (int i = 0; i < Loadout.BINDS; i++) {
                var item = state.binds()[i];
                b.writeBoolean(item != null);
                if (item != null) b.writeResourceLocation(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item));
            }
            b.writeVarInt(state.handBind() + 2);
            var o = state.origin();
            b.writeBoolean(o != null);
            if (o != null) {
                b.writeByte(o.target());
                b.writeByte(o.section());
                b.writeByte(o.x());
                b.writeByte(o.y());
                b.writeByte(o.w());
                b.writeByte(o.h());
                b.writeBoolean(o.rotated());
            }
        }, b -> {
            var binds = new net.minecraft.world.item.Item[Loadout.BINDS];
            for (int i = 0; i < Loadout.BINDS; i++)
                if (b.readBoolean()) binds[i] = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(b.readResourceLocation());
            int hand = b.readVarInt() - 2;
            Loadout.Origin origin = b.readBoolean()
                    ? new Loadout.Origin(b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readBoolean())
                    : null;
            return new LoadoutSync(new Loadout.ClientState(binds, hand, origin));
        }, (p, s) -> client(s, () -> {
            Loadout.receive(p.state());
            PlayerGrids.invalidateClientCache();
        }), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, UseBind.class, (p, b) -> b.writeByte(p.index()), b -> new UseBind(b.readByte()),
                (p, s) -> {
                    NetworkEvent.Context context = s.get();
                    ServerPlayer player = context.getSender();
                    if (player != null) context.enqueueWork(() -> Loadout.useBind(player, p.index()));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, BenchInsert.class, (p, b) -> {
                    b.writeVarInt(p.containerId());
                    b.writeVarInt(p.source());
                    b.writeItem(p.expected());
                }, b -> new BenchInsert(b.readVarInt(), b.readVarInt(), b.readItem()),
                (p, s) -> {
                    NetworkEvent.Context context = s.get();
                    ServerPlayer player = context.getSender();
                    if (player != null) context.enqueueWork(() -> {
                        if (player.containerMenu instanceof com.antaurora.apofirstlight.menu.GunMaintenanceMenu menu && menu.containerId == p.containerId())
                            menu.insert(player, p.source(), p.expected());
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, CarriedSync.class, (p, b) -> {
                    b.writeVarInt(p.entity());
                    b.writeItem(p.shown().back());
                    b.writeBoolean(p.shown().sling());
                    b.writeItem(p.shown().thigh());
                }, b -> new CarriedSync(b.readVarInt(), new CarriedWeaponsSync.Shown(b.readItem(), b.readBoolean(), b.readItem())),
                (p, s) -> client(s, () -> CarriedWeaponsSync.receive(p.entity(), p.shown())), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    public static void sendSizes(ServerPlayer player) {
        AflNetwork.grid(player, new Sizes(ItemSizes.serverSizes()));
    }

    public static void sendPockets(ServerPlayer player) {
        var pockets = Pockets.of(player);
        AflNetwork.grid(player, new PocketsSync(pockets == null ? new ListTag() : pockets.items().copy()));
    }

    public static void sendLoadout(ServerPlayer player, Loadout.ClientState state) {
        AflNetwork.grid(player, new LoadoutSync(state));
    }

    /** The weapons on a carrier's body: to one viewer, or (viewer null) to every player tracking the carrier. */
    public static void sendCarried(ServerPlayer carrier, ServerPlayer viewer, CarriedWeaponsSync.Shown shown) {
        if (channel == null) return;
        var packet = new CarriedSync(carrier.getId(), shown);
        if (viewer != null) channel.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
        else channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> carrier), packet);
    }

    /** Client: a quick binding key for the server. */
    public static void sendUseBind(int index) {
        AflNetwork.gridToServer(new UseBind(index));
    }

    /** Client: the bench's gun picker asks for a gun on the bench. */
    public static void sendBenchInsert(int containerId, int source, net.minecraft.world.item.ItemStack expected) {
        AflNetwork.gridToServer(new BenchInsert(containerId, source, expected));
    }

    /** Client: a grid action for the server. */
    public static void send(Action action) {
        AflNetwork.gridToServer(action);
    }

    private static void client(Supplier<NetworkEvent.Context> supplier, Runnable work) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(work);
        context.setPacketHandled(true);
    }
}
