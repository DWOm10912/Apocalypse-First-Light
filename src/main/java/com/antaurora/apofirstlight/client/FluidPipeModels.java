package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the Fluid Pipe V2 pieces (models/block/fluid_pipe/*.json, Forge OBJ; tools/build-fluid-pipe-v2.mjs) as additional
 * models and replaces the pipe's baked model for every block state with {@link FluidPipeBakedModel}, which assembles them.
 * Steel pieces have their name, glass pieces {@code <name>_glass}.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FluidPipeModels {
    private static final List<String> PIECES = pieceNames();

    private FluidPipeModels() {
    }

    private static List<String> pieceNames() {
        List<String> names = new ArrayList<>(List.of("box", "item"));
        Direction[] order = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction d : order) {
            for (String kind : List.of("half", "arm", "end")) {
                for (String link : List.of("pipe", "port")) {
                    names.add(kind + "_" + link + "_" + d.getName());
                    names.add(kind + "_" + link + "_" + d.getName() + "_glass");
                }
            }
            names.add("box_glass_" + d.getName() + "_glass");
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            names.add("band_" + axis.getName());
            for (Direction side : order) {
                if (side.getAxis() != axis) names.add("clamp_" + axis.getName() + "_" + side.getName());
            }
        }
        return List.copyOf(names);
    }

    private static ResourceLocation location(String piece) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/fluid_pipe/" + piece);
    }

    @SubscribeEvent
    public static void registerPieces(ModelEvent.RegisterAdditional event) {
        for (String piece : PIECES) event.register(location(piece));
    }

    @SubscribeEvent
    public static void replacePipeModel(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        Map<String, BakedModel> pieces = new HashMap<>();
        for (String piece : PIECES) {
            BakedModel model = models.get(location(piece));
            if (model != null) pieces.put(piece, model);
            else ApocalypseFirstLight.LOGGER.error("[AFL FLUID PIPE] missing model piece {}", piece);
        }
        if (!pieces.containsKey("box")) return;
        FluidPipeBakedModel pipe = new FluidPipeBakedModel(Map.copyOf(pieces));
        for (BlockState state : AflBlocks.FLUID_PIPE.get().getStateDefinition().getPossibleStates()) {
            models.put(BlockModelShaper.stateToModelLocation(state), pipe);
        }
    }
}
