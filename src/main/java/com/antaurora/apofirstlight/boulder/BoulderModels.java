package com.antaurora.apofirstlight.boulder;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rock R1a / R1b: puts {@link BoulderModel} on every state of the boulder blocks and {@link LooseStonesModel} on the loose
 * stones' (their JSON is particle-only), clears their caches.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BoulderModels {
    private BoulderModels() {}

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        if (!BoulderBlocks.ready()) return;
        var model = new BoulderModel();
        for (Block b : new Block[]{BoulderBlocks.root(), BoulderBlocks.cell()})
            for (BlockState state : b.getStateDefinition().getPossibleStates()) event.getModels().put(BlockModelShaper.stateToModelLocation(state), model);
        if (!LooseStones.ready()) return;
        var loose = new LooseStonesModel();
        for (BlockState state : LooseStones.block().getStateDefinition().getPossibleStates())
            event.getModels().put(BlockModelShaper.stateToModelLocation(state), loose);
    }

    @SubscribeEvent
    public static void baked(ModelEvent.BakingCompleted event) {
        BoulderModel.clearCache();
        LooseStonesModel.clearCache();
    }
}
