package com.antaurora.apofirstlight.client;
import com.antaurora.apofirstlight.block.RestroomDoorRaycast;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class RestroomDoorInput {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void use(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();
        if(!event.isUseItem()||mc.player==null||mc.level==null||mc.gameMode==null||mc.screen!=null||!mc.isWindowActive()||mc.player.isSpectator())return;
        var eye=mc.player.getEyePosition();var hit=RestroomDoorRaycast.find(mc.level,mc.player,eye,eye.add(mc.player.getViewVector(1).scale(mc.gameMode.getPickRange())));
        if(hit==null)return;
        event.setCanceled(true);event.setSwingHand(false);
        if(event.getHand()==InteractionHand.MAIN_HAND)AflNetwork.requestRestroomDoor(hit.getBlockPos());
    }
}
