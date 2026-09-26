package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.reflect.Field;

/** Binds the opted-in plain ammo Items to the existing AFL Mesh item renderer. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Afl12GaugeRoundClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            bind(AflItems.ROUND_12_GAUGE.get(), "12_gauge_round", "12_gauge_round_mesh", 0.32);
            bind(AflItems.ROUND_50_AE.get(), "50_ae_round", "blackridge_50ae_ammo_v1", 0.431217);
            bind(AflItems.CASING_50_AE.get(), "50_ae_casing", "blackridge_50ae_ammo_v1", 0.444568);
        });
    }

    private static void bind(Item item, String model, String atlas, double verticalOffset) {
        try {
            // Forge 1.20.1 has no separate ordinary-Item extension registration event.
            Field field = Item.class.getDeclaredField("renderProperties");
            field.setAccessible(true);
            field.set(item, new IClientItemExtensions() {
                private Afl12GaugeRoundRenderer renderer;

                @Override
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) renderer = new Afl12GaugeRoundRenderer(model, atlas, verticalOffset);
                    return renderer;
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot bind the ammo Mesh renderer to Forge's Item extension: " + model, e);
        }
    }

    private Afl12GaugeRoundClient() {}
}
