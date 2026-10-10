package com.antaurora.apofirstlight.network;

import com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The portable diesel generator's recoil QTE (client/PortableGeneratorPull, docs/machines/portable_diesel_generator_v1.md):
 * a pull (timed in the bar's zone or not) or letting go of the handle. The grip itself is the block's use.
 */
public record PortableGeneratorPullC2SPacket(BlockPos pos, boolean pull, boolean good) {
    public static void encode(PortableGeneratorPullC2SPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeBoolean(p.pull);
        b.writeBoolean(p.good);
    }

    public static PortableGeneratorPullC2SPacket decode(FriendlyByteBuf b) {
        return new PortableGeneratorPullC2SPacket(b.readBlockPos(), b.readBoolean(), b.readBoolean());
    }

    public static void handle(PortableGeneratorPullC2SPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || !player.isAlive() || player.isSpectator()) return;
            if (!player.level().isLoaded(p.pos) || !(player.level().getBlockEntity(p.pos) instanceof PortableDieselGeneratorBlockEntity generator)) return;
            if (p.pull) generator.pull(player, p.good); else generator.release(player);
        });
        context.setPacketHandled(true);
    }
}
