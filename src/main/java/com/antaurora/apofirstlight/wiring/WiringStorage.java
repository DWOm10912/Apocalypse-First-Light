package com.antaurora.apofirstlight.wiring;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Building Wiring V1 storage (docs/gameplay/building_wiring_v1_design.md 3.3): each chunk's nodes go into its own region-file
 * entry under {@code apocalypse_firstlight:wiring} (an int array of keys, a long array of nodes), so the wiring loads,
 * unloads and saves with its blocks; nothing global grows with the world. Only full chunks carry wiring: the event hands
 * over the LevelChunk itself for those (ChunkSerializer#read, before it is wrapped in an ImposterProtoChunk).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WiringStorage {
    public static final String TAG = ApocalypseFirstLight.MOD_ID + ":wiring";
    private static int revisions;

    private WiringStorage() {}

    /** A new revision for a chunk's wiring (server thread). */
    static int nextRevision() {
        return ++revisions;
    }

    @SubscribeEvent
    public static void load(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !event.getData().contains(TAG, Tag.TAG_COMPOUND)) return;
        WiringChunk w = WiringChunk.load(event.getData().getCompound(TAG));
        ((WiringChunkHolder) chunk).afl$setWiring(w.isEmpty() ? null : w);
    }

    @SubscribeEvent
    public static void save(ChunkDataEvent.Save event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        WiringChunk w = ((WiringChunkHolder) chunk).afl$wiring();
        if (w != null && !w.isEmpty()) event.getData().put(TAG, w.save());
    }
}
