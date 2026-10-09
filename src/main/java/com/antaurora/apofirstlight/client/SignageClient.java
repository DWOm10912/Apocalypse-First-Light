package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.item.ChannelLetterBlockItem;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Fuel Stop A1 details V1: the channel letter's icon follows its letter. Item property apocalypse_firstlight:letter is the
 * letter's index / 25 (A = 0, Z = 1); models/item/channel_letter.json overrides it to channel_letter_&lt;letter&gt;.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SignageClient {
    private SignageClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(AflItems.CHANNEL_LETTER.get(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "letter"),
                (stack, level, entity, seed) -> ChannelLetterBlockItem.letter(stack).ordinal() / 25.0F));
    }
}
