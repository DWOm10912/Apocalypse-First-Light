package com.antaurora.apofirstlight.network;

import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.block.BeverageCoolerDoorRaycast;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client suggests a visible open leaf; the server repeats the reach/occlusion ray test. */
public record BeverageCoolerDoorC2SPacket(BlockPos master, boolean left) {
    public static void encode(BeverageCoolerDoorC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.master);
        buffer.writeBoolean(packet.left);
    }

    public static BeverageCoolerDoorC2SPacket decode(FriendlyByteBuf buffer) {
        return new BeverageCoolerDoorC2SPacket(buffer.readBlockPos(), buffer.readBoolean());
    }

    public static void handle(BeverageCoolerDoorC2SPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (!player.isAlive() || player.isSpectator()) return;
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getLookAngle().scale(player.getBlockReach()));
            BeverageCoolerDoorRaycast.DoorHit hit = BeverageCoolerDoorRaycast.find(
                    player.serverLevel(), player, eye, end);
            if (hit == null || !hit.master().equals(packet.master) || hit.left() != packet.left) return;
            BlockState state = player.level().getBlockState(hit.hit().getBlockPos());
            if (state.getBlock() instanceof BeverageCoolerBlock cooler)
                cooler.use(state, player.level(), hit.hit().getBlockPos(), player,
                        InteractionHand.MAIN_HAND, hit.hit());
        });
        context.setPacketHandled(true);
    }
}
