package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.tree.TreeForms.Species;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Wood Building Blocks V1 (docs/models/wood_building_blocks_v1.md): the rounded timber's block states and item get
 * {@link TreeLogModel}'s rounded-square prism at WoodBlocks.TIMBER_RADIUS with the planed side and end textures (their JSON
 * model holds the particle and the item transforms). TreeModels drops TreeLogModel's quad caches on every bake. Planks and
 * beams are plain JSON models.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WoodModels {
    private WoodModels() {}

    static ResourceLocation texture(Species s, String name) {
        return new ResourceLocation("apocalypse_firstlight", "block/wood/" + s.id + "_" + name);
    }

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        if (!WoodBlocks.ready()) return;
        var models = event.getModels();
        for (Species s : Species.values()) {
            Block block = WoodBlocks.timber(s);
            var item = new ModelResourceLocation(ForgeRegistries.BLOCKS.getKey(block), "inventory");
            BakedModel base = models.getOrDefault(item, models.get(ModelBakery.MISSING_MODEL_LOCATION));
            var model = new TreeLogModel(Species.values().length + s.ordinal(), WoodBlocks.TIMBER_RADIUS,
                    texture(s, "timber_side"), texture(s, "timber_end"), base);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) models.put(BlockModelShaper.stateToModelLocation(state), model);
            models.put(item, model);
        }
    }
}
