package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Mesh Trees: puts {@link TreeCellModel} on every state of the sample blocks at each model bake (their model JSON is a
 * texture-only placeholder), colours the crowns (tint 0: the biome's foliage colour, as vanilla oak leaves; tint 1: the
 * white pine's own blue green, as vanilla conifers keep theirs), and drops the baked quad caches on every bake (atlas UVs
 * change). Every crown block knows both tints: crowns of two species may share a cell. P2.2 (mesh_trees_p2_felling.md, V):
 * the log blocks and their items get {@link TreeLogModel} (transforms from their JSON model); the leaf material items'
 * 3D model (tint 1) takes the crown's colour, their inventory icon (tint 0) none.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TreeModels {
    /** White pine needles (Pinus strobus is a soft blue green); P3: eastern hemlock, a deep green (dark glossy needles). */
    static final int PINE_COLOR = 0x5E9472, HEMLOCK_COLOR = 0x4C7A50;

    /** A species' own leaf colour by its tint index (TreeForms.Species#tint), -1 for none (0: the biome's). */
    static int ownColor(int tint) {
        return tint == 1 ? PINE_COLOR : tint == 2 ? HEMLOCK_COLOR : -1;
    }

    private TreeModels() {}

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        if (!TreeBlocks.ready()) return;
        var models = event.getModels();
        put(models, TreeBlocks.root(), new TreeCellModel(TreeCellModel.Kind.ROOT, Species.WHITE_OAK));
        put(models, TreeBlocks.wood(), new TreeCellModel(TreeCellModel.Kind.WOOD, Species.WHITE_OAK));
        put(models, TreeBlocks.stump(), new TreeCellModel(TreeCellModel.Kind.STUMP, Species.WHITE_OAK));
        for (var crown : TreeBlocks.crowns())
            put(models, crown, new TreeCellModel(crown.carrier ? TreeCellModel.Kind.FOLIAGE : TreeCellModel.Kind.CANOPY, crown.species));
        if (!FellingRegistry.ready()) return;
        for (Species s : Species.values()) {
            var block = FellingRegistry.logBlock(s);
            var item = new ModelResourceLocation(ForgeRegistries.BLOCKS.getKey(block), "inventory");
            BakedModel base = models.getOrDefault(item, models.get(ModelBakery.MISSING_MODEL_LOCATION));
            var model = new TreeLogModel(s, base);
            put(models, block, model);
            models.put(item, model);
        }
    }

    private static void put(Map<ResourceLocation, BakedModel> models, Block block, BakedModel model) {
        for (BlockState state : block.getStateDefinition().getPossibleStates()) models.put(BlockModelShaper.stateToModelLocation(state), model);
    }

    /** P2: the falling tree's renderer (FallingTreeRenderer). */
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        if (FellingRegistry.ready()) event.registerEntityRenderer(FellingRegistry.fallingTree, FallingTreeRenderer::new);
    }

    @SubscribeEvent
    public static void baked(ModelEvent.BakingCompleted event) {
        TreeCellModel.clearCache();
        TreeLogModel.clearCache();
    }

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Block event) {
        if (!TreeBlocks.ready()) return;
        event.register((state, level, pos, tint) -> tint >= 1 ? ownColor(tint) : tint != 0 ? -1 : level != null && pos != null
                ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor(), TreeBlocks.crowns());
    }

    /** P2.2: the leaf material's 3D model (tint 1) in the crown's colour; the inventory icon (tint 0) is drawn in colour. */
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        if (!FellingRegistry.ready()) return;
        for (Species s : Species.values()) {
            int color = s.tint == 0 ? FoliageColor.getDefaultColor() : ownColor(s.tint);
            event.register((stack, tint) -> tint == 1 ? color : -1, FellingRegistry.leaves(s));
        }
    }
}
