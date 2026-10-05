package com.antaurora.apofirstlight.thirst;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * No milk buckets (user, 2026-10-04): the vanilla bucket is being retired (its recipe is disabled, AFL's fluid scale is
 * 1 mB = 1 litre, docs/项目内容/01 - 设计/工业/流体系统.md) and milk will come later as modern cartons / jugs. A bucket
 * used on a cow, a mooshroom or a goat does nothing. Milk buckets already in the world still drink as vanilla, without
 * AFL hydration (thirst_v1.json no longer lists them).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class NoMilkingEvents {
    private NoMilkingEvents() {
    }

    @SubscribeEvent
    public static void noMilking(PlayerInteractEvent.EntityInteract event) {
        if ((event.getTarget() instanceof Cow || event.getTarget() instanceof Goat) && event.getItemStack().is(Items.BUCKET)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
