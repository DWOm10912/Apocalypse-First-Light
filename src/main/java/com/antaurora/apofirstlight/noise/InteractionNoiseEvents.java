package com.antaurora.apofirstlight.noise;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InteractionNoiseEvents {
    private static final Map<UUID, PendingToggle> PENDING_TOGGLES = new HashMap<>();
    private static final Map<UUID, PendingContainer> PENDING_CONTAINERS = new HashMap<>();

    private InteractionNoiseEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = player.level().getBlockState(pos);
        InteractionNoiseResolver.resolveToggle(state).ifPresent(result -> PENDING_TOGGLES.put(
                player.getUUID(),
                new PendingToggle(player, pos.immutable(), state.getBlock(), state.getValue(BlockStateProperties.OPEN), result)
        ));
        InteractionNoiseResolver.resolveContainer(state).ifPresent(result -> PENDING_CONTAINERS.put(
                player.getUUID(),
                new PendingContainer(player, pos.immutable(), result, player.level().getGameTime())
        ));
    }

    @SubscribeEvent
    public static void onContainerOpened(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PendingContainer pending = PENDING_CONTAINERS.remove(player.getUUID());
        if (pending == null || pending.player() != player || pending.gameTime() != player.level().getGameTime()) {
            return;
        }

        BlockState state = player.level().getBlockState(pending.pos());
        InteractionNoiseResolver.resolveContainer(state)
                .filter(result -> result.kind() == pending.result().kind())
                .ifPresent(result -> emit(player, pending.pos(), state, result));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        for (var iterator = PENDING_TOGGLES.entrySet().iterator(); iterator.hasNext();) {
            PendingToggle pending = iterator.next().getValue();
            iterator.remove();
            ServerPlayer player = pending.player();
            BlockState currentState = player.level().getBlockState(pending.pos());
            if (currentState.getBlock() == pending.block()
                    && currentState.hasProperty(BlockStateProperties.OPEN)
                    && currentState.getValue(BlockStateProperties.OPEN) != pending.open()) {
                emit(player, pending.pos(), currentState, pending.result());
            }
        }
        PENDING_CONTAINERS.entrySet().removeIf(entry -> entry.getValue().gameTime() < event.getServer().overworld().getGameTime());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        BlockPos pos = event.getPos();
        emit(player, pos, event.getPlacedBlock(), InteractionNoiseResolver.blockPlace());
    }

    private static void emit(ServerPlayer player, BlockPos pos, BlockState state, InteractionNoiseResolver.Result result) {
        ResourceLocation blockId = state.getBlock().builtInRegistryHolder().key().location();
        NoiseSystem.emit(new NoiseEvent(
                player,
                pos.getCenter(),
                NoiseType.INTERACTION,
                player.level().getGameTime(),
                blockId,
                result.radius()
        ));
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL NOISE] Interaction kind={} block={} pos={} radius={}",
                result.kind(), blockId, pos, result.radius()
        );
    }

    private record PendingToggle(
            ServerPlayer player,
            BlockPos pos,
            net.minecraft.world.level.block.Block block,
            boolean open,
            InteractionNoiseResolver.Result result
    ) {
    }

    private record PendingContainer(
            ServerPlayer player,
            BlockPos pos,
            InteractionNoiseResolver.Result result,
            long gameTime
    ) {
    }
}
