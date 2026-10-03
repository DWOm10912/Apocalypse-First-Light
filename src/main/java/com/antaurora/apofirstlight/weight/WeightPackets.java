package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;

/** Own-player S2C only. Client never submits weight or calculates it from its inventory. */
public final class WeightPackets {
    private WeightPackets() {}
    public record Policy(long revision, ItemMassData.Policy policy) {}
    public record State(long sequence, EncumbranceState state) {}
    public record Data(ItemMassData.Snapshot snapshot) {}
    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Policy.class, WeightPackets::encodePolicy, WeightPackets::decodePolicy,
                WeightPackets::handlePolicy, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, State.class, WeightPackets::encodeState, WeightPackets::decodeState,
                WeightPackets::handleState, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Data.class, WeightPackets::encodeData, WeightPackets::decodeData,
                WeightPackets::handleData, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }
    public static void sendPolicy(ServerPlayer player, ItemMassData.Snapshot data) {
        AflNetwork.weightPolicy(player, new Policy(data.revision(), data.policy()));
    }
    public static void sendState(ServerPlayer player, EncumbranceState state, long sequence) {
        AflNetwork.weightState(player, new State(sequence, state));
    }
    public static void sendData(ServerPlayer player, ItemMassData.Snapshot data) {
        AflNetwork.weightData(player, new Data(data));
    }
    private static void writeUnit(FriendlyByteBuf b, ItemMassData.Unit unit) {
        b.writeLong(unit.grams()); b.writeBoolean(unit.estimated()); b.writeUtf(unit.source());
    }
    private static ItemMassData.Unit readUnit(FriendlyByteBuf b) {
        long grams = b.readLong();
        if (grams < 0) throw new IllegalArgumentException("Negative synced item mass");
        return new ItemMassData.Unit(grams, b.readBoolean(), b.readUtf());
    }
    private static int readCount(FriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > 65536) throw new IllegalArgumentException("Invalid mass table size");
        return count;
    }
    private static void encodeData(Data packet, FriendlyByteBuf b) {
        var data = packet.snapshot();
        encodePolicy(new Policy(data.revision(), data.policy()), b);
        b.writeVarInt(data.units().size());
        data.units().forEach((id, unit) -> { b.writeResourceLocation(id); writeUnit(b, unit); });
        b.writeVarInt(data.guns().size());
        data.guns().forEach((id, gun) -> {
            b.writeResourceLocation(id); writeUnit(b, gun.receiver()); writeUnit(b, gun.magazine());
        });
    }
    private static Data decodeData(FriendlyByteBuf b) {
        var policy = decodePolicy(b);
        var units = new HashMap<ResourceLocation, ItemMassData.Unit>();
        int count = readCount(b);
        for (int i = 0; i < count; i++) units.put(b.readResourceLocation(), readUnit(b));
        var guns = new HashMap<ResourceLocation, ItemMassData.Gun>();
        count = readCount(b);
        for (int i = 0; i < count; i++) guns.put(b.readResourceLocation(), new ItemMassData.Gun(readUnit(b), readUnit(b)));
        return new Data(new ItemMassData.Snapshot(policy.revision(), policy.policy(), units, guns));
    }
    private static void encodePolicy(Policy p, FriendlyByteBuf b) {
        b.writeLong(p.revision()); b.writeLong(p.policy().fallbackGrams()); b.writeLong(p.policy().comfortGrams());
        b.writeDouble(p.policy().onset()); b.writeDouble(p.policy().severe());
    }
    private static Policy decodePolicy(FriendlyByteBuf b) {
        return new Policy(b.readLong(), new ItemMassData.Policy(b.readLong(), b.readLong(), b.readDouble(), b.readDouble()));
    }
    private static void encodeState(State p, FriendlyByteBuf b) {
        var s = p.state(); b.writeLong(p.sequence()); b.writeLong(s.carriedMassGrams()); b.writeLong(s.comfortCapacityGrams());
        b.writeDouble(s.encumbranceRatio()); b.writeDouble(s.severity()); b.writeEnum(s.tier());
        b.writeBoolean(s.penaltiesEnabled()); b.writeLong(s.dataRevision()); b.writeUtf(s.quality());
    }
    private static State decodeState(FriendlyByteBuf b) {
        return new State(b.readLong(), new EncumbranceState(b.readLong(), b.readLong(), b.readDouble(), b.readDouble(),
                b.readEnum(EncumbranceState.Tier.class), b.readBoolean(), b.readLong(), b.readUtf(32)));
    }
    private static void handlePolicy(Policy p, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientWeightState.accept(p))); c.setPacketHandled(true);
    }
    private static void handleState(State p, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientWeightState.accept(p))); c.setPacketHandled(true);
    }
    private static void handleData(Data p, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientWeightState.accept(p))); c.setPacketHandled(true);
    }
}
