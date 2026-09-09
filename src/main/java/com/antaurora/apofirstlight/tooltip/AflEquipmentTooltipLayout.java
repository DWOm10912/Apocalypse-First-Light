package com.antaurora.apofirstlight.tooltip;

import com.antaurora.apofirstlight.weapon.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla wrapping, limited to AFL equipment; no replacement tooltip renderer. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class AflEquipmentTooltipLayout {
    @SubscribeEvent public static void gather(RenderTooltipEvent.GatherComponents e){
        var item=e.getItemStack().getItem();
        if(item instanceof NativeGunItem||item instanceof NativeAttachment)
            e.setMaxWidth(e.getMaxWidth()>0?Math.min(300,e.getMaxWidth()):300);
    }
}
