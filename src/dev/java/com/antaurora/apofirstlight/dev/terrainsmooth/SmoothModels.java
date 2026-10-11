package com.antaurora.apofirstlight.dev.terrainsmooth;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Map;
import java.util.function.Function;

/**
 * Development runs only: wraps the vanilla block models of the prototype's natural ground blocks (SmoothSurfaceModel)
 * and of the small plants (SinkPlantModel). The wrappers return the vanilla model unchanged until the prototype is switched
 * on, and then only inside its test area; nothing is registered, no block or item changes.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SmoothModels {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SmoothModels() {}

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        if (FMLEnvironment.production) return;
        Map<ResourceLocation, BakedModel> models = event.getModels();
        int surface = 0, plants = 0;
        for (Block block : SmoothWorld.SURFACE) surface += wrap(models, block, SmoothSurfaceModel::new);
        for (Block block : ForgeRegistries.BLOCKS) {
            if (block instanceof BushBlock && !(block instanceof WaterlilyBlock)) plants += wrap(models, block, SinkPlantModel::new);
        }
        LOGGER.info("[AFL terrain smooth P0] wrapped {} ground block states and {} plant states (inactive until /afl dev terrain_smooth a|b)", surface, plants);
    }

    private static int wrap(Map<ResourceLocation, BakedModel> models, Block block, Function<BakedModel, BakedModel> wrapper) {
        int n = 0;
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ResourceLocation key = BlockModelShaper.stateToModelLocation(state);
            BakedModel model = models.get(key);
            if (model == null || model instanceof SmoothSurfaceModel || model instanceof SinkPlantModel) continue;
            models.put(key, wrapper.apply(model));
            n++;
        }
        return n;
    }
}
