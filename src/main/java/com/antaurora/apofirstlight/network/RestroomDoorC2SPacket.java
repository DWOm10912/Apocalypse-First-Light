package com.antaurora.apofirstlight.network;
import com.antaurora.apofirstlight.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record RestroomDoorC2SPacket(BlockPos pos){
    public static void encode(RestroomDoorC2SPacket p,FriendlyByteBuf b){b.writeBlockPos(p.pos);}
    public static RestroomDoorC2SPacket decode(FriendlyByteBuf b){return new RestroomDoorC2SPacket(b.readBlockPos());}
    public static void handle(RestroomDoorC2SPacket p,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->{
            var player=context.getSender();if(player==null||!player.isAlive()||player.isSpectator())return;
            var eye=player.getEyePosition();var hit=RestroomDoorRaycast.find(player.serverLevel(),player,eye,eye.add(player.getLookAngle().scale(player.getBlockReach())));
            if(hit==null||!hit.getBlockPos().equals(p.pos))return;
            var state=player.level().getBlockState(p.pos);
            if(state.getBlock() instanceof RestroomStallDoorBlock door)door.use(state,player.level(),p.pos,player,InteractionHand.MAIN_HAND,hit);
        });context.setPacketHandled(true);
    }
}
