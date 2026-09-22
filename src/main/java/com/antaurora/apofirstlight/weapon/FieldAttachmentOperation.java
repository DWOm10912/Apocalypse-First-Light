package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;
import java.util.*;

/** No reservation/mutation until the server deadline; cancellation is an explicit token. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class FieldAttachmentOperation {
    private record Pending(ServerPlayer player, FieldAttachmentActionRequest request, ItemStack identity,
                           ResourceKey<Level> dimension, long deadline) {}
    private static final Map<UUID,Pending> ACTIVE=new HashMap<>();
    private static final Map<UUID,Long> SEQUENCES=new HashMap<>();
    public static boolean pending(ServerPlayer player){return ACTIVE.containsKey(player.getUUID());}
    private static boolean valid(ServerPlayer p,FieldAttachmentActionRequest r) {
        return p.isAlive()&&!p.isSpectator()&&!p.hasDisconnected()&&!p.isUsingItem()&&!p.isSprinting()
                &&p.containerMenu==p.inventoryMenu &&r.selectedSlot()>=0&&r.selectedSlot()<9
                &&p.getInventory().selected==r.selectedSlot()
                &&r.gunId()!=Long.MAX_VALUE&&GeoItem.getId(p.getMainHandItem())==r.gunId()
                &&ItemStack.matches(p.getMainHandItem(),r.expectedGun())
                &&!NativeGunActions.busy(p)&&!NativeFireControl.active(p)
                &&!MaintenanceAttachmentOperation.pending(p)
                &&r.sourceSlot()!=r.selectedSlot()
                &&AttachmentInteractionCore.valid(p,p.getMainHandItem(),r.target(),r.sourceSlot(),r.expectedSource());
    }
    public static void begin(ServerPlayer p,FieldAttachmentActionRequest r) {
        if(r.token()<=SEQUENCES.getOrDefault(p.getUUID(),0L))return;
        SEQUENCES.put(p.getUUID(),r.token());
        if(pending(p)||!valid(p,r)){AflNetwork.fieldAttachmentResult(p,r.token(),0);return;}
        ACTIVE.put(p.getUUID(),new Pending(p,r,p.getMainHandItem(),p.level().dimension(),
                p.server.getTickCount()+MaintenanceAttachmentOperation.DURATION_TICKS));
        AflNetwork.fieldAttachmentResult(p,r.token(),2);
    }
    public static void cancel(ServerPlayer p,long token) {
        var pending=ACTIVE.get(p.getUUID());
        if(pending!=null&&pending.request().token()==token){
            ACTIVE.remove(p.getUUID());AflNetwork.fieldAttachmentResult(p,token,0);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var it=ACTIVE.values().iterator();
        while(it.hasNext()){
            var op=it.next();var p=op.player();var r=op.request();
            if(p.getMainHandItem()!=op.identity()||p.level().dimension()!=op.dimension()||!valid(p,r)){
                it.remove();AflNetwork.fieldAttachmentResult(p,r.token(),0);continue;
            }
            if(p.server.getTickCount()<op.deadline())continue;
            // Full identity and content validation immediately precedes the shared atomic exchange.
            boolean committed=AttachmentInteractionCore.commit(p,op.identity(),r.target(),r.sourceSlot(),r.expectedSource(),
                    updated->op.identity().setTag(updated.getTag()));
            // Preserve the target ItemStack object: replacing it would trigger NativeGunActions' equip animation.
            it.remove();AflNetwork.fieldAttachmentResult(p,r.token(),committed?1:0);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){
        ACTIVE.remove(e.getEntity().getUUID());SEQUENCES.remove(e.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){ACTIVE.clear();SEQUENCES.clear();}
    private FieldAttachmentOperation(){}
}
