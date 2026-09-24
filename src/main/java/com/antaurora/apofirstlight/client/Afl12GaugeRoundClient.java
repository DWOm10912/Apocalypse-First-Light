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

/** Binds only the existing plain Item to a client renderer; its registry and gameplay class stay intact. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Afl12GaugeRoundClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                // Forge 1.20.1 has no separate ordinary-Item extension registration event.
                // This is Forge's own extension slot, set once for the one opted-in Item.
                Field field = Item.class.getDeclaredField("renderProperties");
                field.setAccessible(true);
                field.set(AflItems.ROUND_12_GAUGE.get(), new IClientItemExtensions() {
                    private Afl12GaugeRoundRenderer renderer;

                    @Override
                    public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        if (renderer == null) renderer = new Afl12GaugeRoundRenderer();
                        return renderer;
                    }
                });
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot bind the 12 Gauge Mesh renderer to Forge's Item extension", e);
            }
        });
    }

    private Afl12GaugeRoundClient() {}
}
