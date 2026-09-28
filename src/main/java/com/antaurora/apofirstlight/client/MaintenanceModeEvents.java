package com.antaurora.apofirstlight.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class MaintenanceModeEvents {
    // Oculus calls ItemInHandRenderer directly, bypassing GameRenderer.renderItemInHand.
    // Both vanilla and that shader path still dispatch this Forge hook for each hand.
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void hands(RenderHandEvent e){if(MaintenanceModeClientState.INSTANCE.active())e.setCanceled(true);}
    @SubscribeEvent public static void inspection(net.minecraftforge.event.TickEvent.RenderTickEvent e){
        if(e.phase==net.minecraftforge.event.TickEvent.Phase.START)MaintenanceModeClientState.INSTANCE.inspectionFrame();
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Pre e){if(MaintenanceModeClientState.INSTANCE.active())e.setCanceled(true);}
    @SubscribeEvent public static void outline(RenderHighlightEvent.Block e){if(MaintenanceModeClientState.INSTANCE.active())e.setCanceled(true);}
    @SubscribeEvent public static void input(MovementInputUpdateEvent e){
        if(!MaintenanceModeClientState.INSTANCE.active())return;
        var i=e.getInput();i.forwardImpulse=0;i.leftImpulse=0;i.up=false;i.down=false;i.left=false;i.right=false;i.jumping=false;i.shiftKeyDown=false;
    }
    @SubscribeEvent public static void player(RenderPlayerEvent.Pre e){if(MaintenanceModeClientState.INSTANCE.active()&&e.getEntity()==Minecraft.getInstance().player)e.setCanceled(true);}
}
