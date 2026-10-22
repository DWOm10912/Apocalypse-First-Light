package com.antaurora.apofirstlight.terrainsmooth.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.Tags;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Ground draws no selection outline (user 2026-10-21): natural ground and rock are a continuous surface that the dig and
 * mining systems work on as a volume, not a grid of blocks, so no box (and no outline of the old Terrain Smooth slope or
 * earthwork skin, docs/worldgen/earthwork_a1.md 4.3) is drawn on them; the same for the road's ground, its graded turf
 * slopes and its pavement (user 2026-10-21, after the A1 check). The crosshair is the target; the breaking cracks stay.
 * Placed blocks, bridges and other structures keep their outlines. First of all outline handlers (MeshHitPicking...): a
 * cancelled event reaches none of them.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class SmoothOutline {
    private SmoothOutline() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void outline(RenderHighlightEvent.Block event) {
        var level = Minecraft.getInstance().level;
        if (level != null && ground(level.getBlockState(event.getTarget().getBlockPos()))) event.setCanceled(true);
    }

    /**
     * Terrain Smooth's ground, overworld base stone (stone, granite, deepslate...), ores (vanilla's and AFL's), and the
     * road's graded turf and pavement, and the boulders (Rock R1a).
     */
    static boolean ground(BlockState s) {
        Block b = s.getBlock();
        if (TerrainSmooth.ground(b) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Tags.Blocks.ORES)) return true;
        if (b == AflBlocks.GRADED_TURF.get() || b == AflBlocks.ROAD_SURFACE.get()) return true;
        if (com.antaurora.apofirstlight.boulder.BoulderBlocks.isBoulder(s)) return true;      // Rock R1a: boulders are ground
        var id = ForgeRegistries.BLOCKS.getKey(b);
        return id != null && id.getNamespace().equals(ApocalypseFirstLight.MOD_ID) && id.getPath().endsWith("_ore");
    }
}
