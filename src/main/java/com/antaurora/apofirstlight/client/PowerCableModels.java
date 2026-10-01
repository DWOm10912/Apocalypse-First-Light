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
 * Loads the Power Cable V2 pieces (models/block/power_cable/*.json, Forge OBJ) as additional models and replaces the
 * cable's baked model for every block state with {@link PowerCableBakedModel}, which assembles them.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PowerCableModels {
    private static final List<String> PIECES = pieceNames();

    private PowerCableModels() {
    }

    private static List<String> pieceNames() {
        List<String> names = new ArrayList<>(List.of("box", "item", "band_x", "band_y", "band_z"));
        Direction[] order = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction d : order) {
            for (String kind : List.of("half", "arm", "end")) {
                names.add(kind + "_cable_" + d.getName());
                names.add(kind + "_plug_" + d.getName());
            }
        }
        for (int i = 0; i < order.length; i++) {
            for (int j = i + 1; j < order.length; j++) {
                if (order[i].getAxis() != order[j].getAxis()) names.add("bend_" + order[i].getName() + "_" + order[j].getName());
            }
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            for (Direction side : order) {
                if (side.getAxis() != axis) names.add("clamp_" + axis.getName() + "_" + side.getName());
            }
        }
        return List.copyOf(names);
    }

    private static ResourceLocation location(String piece) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/power_cable/" + piece);
    }

    @SubscribeEvent
    public static void registerPieces(ModelEvent.RegisterAdditional event) {
        for (String piece : PIECES) event.register(location(piece));
    }

    @SubscribeEvent
    public static void replaceCableModel(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        Map<String, BakedModel> pieces = new HashMap<>();
        for (String piece : PIECES) {
            BakedModel model = models.get(location(piece));
            if (model != null) pieces.put(piece, model);
            else ApocalypseFirstLight.LOGGER.error("[AFL POWER CABLE] missing model piece {}", piece);
        }
        if (!pieces.containsKey("box")) return;
        PowerCableBakedModel cable = new PowerCableBakedModel(Map.copyOf(pieces));
        for (BlockState state : AflBlocks.POWER_CABLE.get().getStateDefinition().getPossibleStates()) {
            models.put(BlockModelShaper.stateToModelLocation(state), cable);
        }
    }
}
