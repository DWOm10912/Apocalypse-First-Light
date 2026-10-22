package com.antaurora.apofirstlight.boulder;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rock R1a client side: the client's boulder index ({@link BoulderIndex#CLIENT}, read by the chunk builder through
 * BoulderModel and by collisions). A cell of a rock whose root chunk is not here yet draws nothing until that chunk
 * arrives and marks the rock's sections for a rebuild; a root change marks the sections of the old and the new rock. All
 * marks of a tick go through one set.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class BoulderClient {
    private static final LongOpenHashSet DIRTY = new LongOpenHashSet();

    private BoulderClient() {}

    static void dirty(BoulderIndex.Root r) {
        for (int sx = r.minX >> 4; sx <= r.maxX >> 4; sx++)
            for (int sy = r.minY >> 4; sy <= r.maxY >> 4; sy++)
                for (int sz = r.minZ >> 4; sz <= r.maxZ >> 4; sz++) DIRTY.add(SectionPos.asLong(sx, sy, sz));
    }

    static void rootChanged(BlockPos pos, BlockState now) {
        var gone = BoulderIndex.CLIENT.remove(pos);
        if (gone != null) dirty(gone);
        if (BoulderIndex.isRoot(now)) dirty(BoulderIndex.CLIENT.add(pos, now));
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!BoulderBlocks.ready() || !(event.getLevel() instanceof ClientLevel) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (var r : BoulderIndex.CLIENT.load(chunk)) dirty(r);
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) BoulderIndex.CLIENT.unload(event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BoulderIndex.CLIENT.clear();
        DIRTY.clear();
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DIRTY.isEmpty()) return;
        var renderer = Minecraft.getInstance().levelRenderer;
        if (renderer != null && Minecraft.getInstance().level != null) {
            var it = DIRTY.iterator();
            while (it.hasNext()) {
                long k = it.nextLong();
                renderer.setSectionDirty(SectionPos.x(k), SectionPos.y(k), SectionPos.z(k));
            }
        }
        DIRTY.clear();
    }
}
