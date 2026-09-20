package com.antaurora.apofirstlight.tooltip;

import com.antaurora.apofirstlight.weapon.*;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla composition; replaces only the gun's explicit ammunition row marker. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class AflEquipmentTooltipLayout {
    @SubscribeEvent public static void gather(RenderTooltipEvent.GatherComponents e){
        var item=e.getItemStack().getItem();
        if(item instanceof NativeGunItem gun) {
            var elements = e.getTooltipElements();
            for(int i = 0; i < elements.size(); i++) {
                var text = elements.get(i).left().orElse(null);
                if(text instanceof Component component
                        && component.getContents() instanceof TranslatableContents translation
                        && GunAmmoTooltipComponent.ROW_KEY.equals(translation.getKey())) {
                    elements.set(i, Either.right(GunAmmoTooltipComponent.resolve(gun.definition())));
                    break;
                }
            }
        } else if(item instanceof NativeAttachment)
            e.setMaxWidth(e.getMaxWidth()>0?Math.min(300,e.getMaxWidth()):300);
    }
}
