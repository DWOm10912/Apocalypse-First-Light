package com.antaurora.apofirstlight.noise;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
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
        BlockBreakNoiseResolver.Result result = BlockBreakNoiseResolver.resolve(state);
        ResourceLocation blockId = state.getBlock().builtInRegistryHolder().key().location();
        NoiseSystem.emit(new NoiseEvent(
                event.getPlayer(),
                pos.getCenter(),
                NoiseType.BLOCK_BREAK,
                event.getPlayer().level().getGameTime(),
                blockId,
                result.radius()
        ));
        ApocalypseFirstLight.LOGGER.debug("[AFL NOISE] BlockBreak block={} category={} radius={}", blockId, result.category(), result.radius());
    }
}
