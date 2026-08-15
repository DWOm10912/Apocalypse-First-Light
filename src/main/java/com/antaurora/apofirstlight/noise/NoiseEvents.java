package com.antaurora.apofirstlight.noise;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.debug.SoundDataDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NoiseEvents {
    private NoiseEvents() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ResourceLocation blockId = state.getBlock().builtInRegistryHolder().key().location();
        SoundDataDebug.logBlockSound(blockId, state);

        NoiseSystem.emit(new NoiseEvent(
                event.getPlayer(),
                pos.getCenter(),
                NoiseType.BLOCK_BREAK,
                event.getPlayer().level().getGameTime(),
                blockId
        ));
    }
}
