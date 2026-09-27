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
public final class AflStaticMeshItemClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            bind(AflItems.ROUND_12_GAUGE.get(), "12_gauge_round", "12_gauge_round_mesh", 0.32);
            // ItemEntityRenderer adds 0.25 * ground scale above the entity. Keep these upright rounds
            // at the same visible base height as the 12 Gauge shell without changing other views.
            bind(AflItems.ROUND_50_AE.get(), "50_ae_round", "blackridge_50ae_ammo_v1", 0.431217, 0.257);
            bind(AflItems.CASING_50_AE.get(), "50_ae_casing", "blackridge_50ae_ammo_v1", 0.444568);
            // 9x19mm Pure Mesh set (tools/build-9x19mm-ammo.mjs): offset = 0.5 - mesh height / 32 centres it like .50 AE.
            bind(AflItems.ROUND_9MM.get(), "9x19mm_round", "9x19mm_ammo_v1", 0.451463, 0.2558333333);
            bind(AflItems.CASING_9MM.get(), "9x19mm_casing", "9x19mm_ammo_v1", 0.468168);
        });
    }

    private static void bind(Item item, String model, String atlas, double verticalOffset) {
        bind(item, model, atlas, verticalOffset, verticalOffset);
    }

    private static void bind(Item item, String model, String atlas, double verticalOffset, double groundVerticalOffset) {
        try {
            // Forge 1.20.1 has no separate ordinary-Item extension registration event.
            Field field = Item.class.getDeclaredField("renderProperties");
            field.setAccessible(true);
            field.set(item, new IClientItemExtensions() {
                private AflStaticMeshItemRenderer renderer;

                @Override
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) renderer = new AflStaticMeshItemRenderer(model, atlas, verticalOffset, groundVerticalOffset);
                    return renderer;
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot bind the ammo Mesh renderer to Forge's Item extension: " + model, e);
        }
    }

    private AflStaticMeshItemClient() {}
}
