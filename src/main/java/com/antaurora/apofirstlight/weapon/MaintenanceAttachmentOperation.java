package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Server-owned action window. No inventory reservation or mutation until the validated deadline. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class MaintenanceAttachmentOperation {
    public static final double SOUND_DURATION_SECONDS=2.480;
    public static final int DURATION_TICKS=(int)Math.ceil(SOUND_DURATION_SECONDS*20)+1;
    private record Pending(ServerPlayer player,AbstractContainerMenu menu,MaintenanceActionRequest request,long endTick){}
    private static final Map<UUID,Pending> ACTIVE=new HashMap<>();
    public static boolean pending(ServerPlayer p){return ACTIVE.containsKey(p.getUUID());}
    public static void begin(ServerPlayer p,MaintenanceActionRequest request){
        if(pending(p))return; // Repeated Begin cannot reset the deadline or unlock the original client action.
        if(!MaintenanceAttachmentTransaction.valid(p,request)){AflNetwork.maintenanceResult(p,request.containerId(),0);return;}
        var snapshot=new MaintenanceActionRequest(request.containerId(),request.bench().immutable(),request.revision(),
                request.expectedGun().copy(),request.target(),request.sourceSlot(),request.expectedSource().copy());
        ACTIVE.put(p.getUUID(),new Pending(p,p.containerMenu,snapshot,p.serverLevel().getGameTime()+DURATION_TICKS));
        AflNetwork.maintenanceResult(p,request.containerId(),2);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var iterator=ACTIVE.values().iterator();
        while(iterator.hasNext()){
            var operation=iterator.next();var p=operation.player();var r=operation.request();
            if(p.containerMenu!=operation.menu()||!MaintenanceAttachmentTransaction.valid(p,r)){
                iterator.remove();AflNetwork.maintenanceResult(p,r.containerId(),0);continue;
            }
            if(p.serverLevel().getGameTime()<operation.endTick())continue;
            iterator.remove();
            AflNetwork.maintenanceResult(p,r.containerId(),MaintenanceAttachmentTransaction.commit(p,r)?1:0);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){ACTIVE.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ACTIVE.clear();}
    private MaintenanceAttachmentOperation(){}
}
