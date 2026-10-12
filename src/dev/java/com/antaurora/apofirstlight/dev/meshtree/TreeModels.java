package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Mesh Trees: puts {@link TreeCellModel} on every state of the sample blocks at each model bake (their model JSON is a
 * texture-only placeholder), colours the crowns (tint 0: the biome's foliage colour, as vanilla oak leaves; tint 1: the
 * white pine's own blue green, as vanilla conifers keep theirs), and drops the baked quad caches on every bake (atlas UVs
 * change). Every crown block knows both tints: crowns of two species may share a cell.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TreeModels {
    /** White pine needles (Pinus strobus is a soft blue green). */
    static final int PINE_COLOR = 0x5E9472;

    private TreeModels() {}

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        if (!TreeBlocks.ready()) return;
        var models = event.getModels();
        put(models, TreeBlocks.root(), new TreeCellModel(TreeCellModel.Kind.ROOT, Species.WHITE_OAK));
        put(models, TreeBlocks.wood(), new TreeCellModel(TreeCellModel.Kind.WOOD, Species.WHITE_OAK));
        for (var crown : TreeBlocks.crowns())
            put(models, crown, new TreeCellModel(crown.carrier ? TreeCellModel.Kind.FOLIAGE : TreeCellModel.Kind.CANOPY, crown.species));
    }

    private static void put(Map<ResourceLocation, BakedModel> models, Block block, TreeCellModel model) {
        for (BlockState state : block.getStateDefinition().getPossibleStates()) models.put(BlockModelShaper.stateToModelLocation(state), model);
    }

    @SubscribeEvent
    public static void baked(ModelEvent.BakingCompleted event) {
        TreeCellModel.clearCache();
    }

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Block event) {
        if (!TreeBlocks.ready()) return;
        event.register((state, level, pos, tint) -> tint == 1 ? PINE_COLOR : tint != 0 ? -1 : level != null && pos != null
                ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor(), TreeBlocks.crowns());
    }
}
