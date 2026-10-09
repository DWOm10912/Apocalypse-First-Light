package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PavementPaintBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.item.PavementPaintBlockItem;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Pavement Markings V1: the paint colour of the tinted tiles (hatch, cross-hatch, bar): blocks by COLOR, items by their BlockStateTag. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PavementMarkingClient {
    private PavementMarkingClient() {}

    @SubscribeEvent
    public static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> state.hasProperty(PavementPaintBlock.COLOR) ? state.getValue(PavementPaintBlock.COLOR).tint() : -1,
                AflBlocks.PAVEMENT_HATCH.get(), AflBlocks.PAVEMENT_CROSSHATCH.get(), AflBlocks.PAVEMENT_BAR.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 0 ? PavementPaintBlockItem.paint(stack).tint() : -1,
                AflItems.PAVEMENT_HATCH.get(), AflItems.PAVEMENT_CROSSHATCH.get(), AflItems.PAVEMENT_BAR.get());
    }
}
