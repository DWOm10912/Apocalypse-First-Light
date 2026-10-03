package com.antaurora.apofirstlight.stamina;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

/** Server → client only; the client never reports stamina. */
public final class StaminaPackets {
    private StaminaPackets() {}

    /**
     * The player's own stamina: value / max, winded, and the derived multipliers the client applies itself (weapon sway
     * from fatigue, dig speed while winded) and its own breathing level, so the client needs no copy of the numbers file.
     */
    public record State(float value, float max, boolean winded, float swayScale, float digScale, byte breath) {}
    /** Another player's breathing (0 none, 1 light, 2 heavy), to the players tracking them; repeated as a keep-alive. */
    public record Breath(int entityId, byte level) {}

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, State.class, (p, b) -> {
            b.writeFloat(p.value()); b.writeFloat(p.max()); b.writeBoolean(p.winded()); b.writeFloat(p.swayScale()); b.writeFloat(p.digScale()); b.writeByte(p.breath());
        }, b -> new State(b.readFloat(), b.readFloat(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readByte()),
                StaminaPackets::handleState, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Breath.class, (p, b) -> { b.writeVarInt(p.entityId()); b.writeByte(p.level()); },
                (FriendlyByteBuf b) -> new Breath(b.readVarInt(), b.readByte()),
                StaminaPackets::handleBreath, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }
    private static void handleState(State packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStamina.accept(packet)));
        c.setPacketHandled(true);
    }
    private static void handleBreath(Breath packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStamina.accept(packet)));
        c.setPacketHandled(true);
    }
}
