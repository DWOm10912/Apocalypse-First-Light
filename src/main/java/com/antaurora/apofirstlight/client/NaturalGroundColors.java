package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.ground.TopsoilBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.GrassColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Natural ground N1 (docs/worldgen/natural_ground_n1_design.md 4): topsoil's turf (its model's tint index 0, the top only)
 * takes the biome's grass colour, as vanilla's grass block; its grey photo is made for it. The other covers are not tinted.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NaturalGroundColors {
    private NaturalGroundColors() {}

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint != 0 || state.getValue(TopsoilBlock.COVER) != TopsoilBlock.Cover.GRASS ? -1
                : level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor(), AflBlocks.TOPSOIL.get());
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? GrassColor.getDefaultColor() : -1, AflBlocks.TOPSOIL.get());
    }
}
