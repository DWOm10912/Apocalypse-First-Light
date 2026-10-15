package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The world's survivor ledger (docs/gameplay/survivor_death_succession_v0.md 3), saved with the overworld as
 * {@code data/apocalypse_firstlight_survivors.dat}: the AFL mode, every player's lineage of survivors and every corpse
 * with its items. Server only; clients get what they need by packet.
 *
 * <p>Save order on a death: this file is written at once (end of the death tick), then the player files. A crash
 * between the two leaves a player file from before the death; login reconciliation (SurvivorEvents) recognises it by
 * the survivor id in the player's persistent data and clears the stale copies of the corpse items, so nothing is
 * duplicated.
 */
public final class SurvivorWorldData extends SavedData {
    public static final String NAME = ApocalypseFirstLight.MOD_ID + "_survivors";
    private static final int FORMAT = 1;

    SurvivorMode mode = SurvivorMode.UNSET;
    /** Set when this world was created with the survivor system present: the host is asked for the mode. */
    boolean freshWorld;
    final Map<UUID, SurvivorLineage> lineages = new LinkedHashMap<>();
    final Map<UUID, SurvivorCorpseRecord> corpses = new LinkedHashMap<>();

    public static SurvivorWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SurvivorWorldData::load, SurvivorWorldData::new, NAME);
    }

    public SurvivorMode mode() {
        return mode;
    }

    public Collection<SurvivorLineage> lineages() {
        return Collections.unmodifiableCollection(lineages.values());
    }

    public SurvivorLineage lineage(UUID player) {
        return lineages.get(player);
    }

    SurvivorLineage lineage(ServerPlayer player) {
        SurvivorLineage lineage = lineages.get(player.getUUID());
        if (lineage == null) {
            lineage = new SurvivorLineage(player.getUUID(), player.getGameProfile().getName());
            lineages.put(player.getUUID(), lineage);
            setDirty();
        } else if (!lineage.accountName.equals(player.getGameProfile().getName())) {
            lineage.accountName = player.getGameProfile().getName();
            setDirty();
        }
        return lineage;
    }

    void setMode(SurvivorMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            setDirty();
        }
    }

    public Collection<SurvivorCorpseRecord> corpses() {
        return Collections.unmodifiableCollection(corpses.values());
    }

    public SurvivorCorpseRecord corpse(UUID id) {
        return corpses.get(id);
    }

    void addCorpse(SurvivorCorpseRecord corpse) {
        corpses.put(corpse.id, corpse);
        corpse.owner = this;
        setDirty();
    }

    /**
     * Writes this file now (only if dirty), outside the autosave: the death tick calls it before the player files are
     * written. Same thread as every other save (the server thread), so it never races the autosave.
     */
    void writeNow(MinecraftServer server) {
        if (!isDirty()) return;
        File file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME + ".dat").toFile();
        save(file);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Format", FORMAT);
        tag.putString("Mode", mode.id());
        tag.putBoolean("Fresh", freshWorld);
        ListTag list = new ListTag();
        for (SurvivorLineage lineage : lineages.values()) list.add(lineage.save());
        tag.put("Lineages", list);
        ListTag bodies = new ListTag();
        for (SurvivorCorpseRecord corpse : corpses.values()) bodies.add(corpse.save());
        tag.put("Corpses", bodies);
        return tag;
    }

    public static SurvivorWorldData load(CompoundTag tag) {
        SurvivorWorldData data = new SurvivorWorldData();
        data.mode = SurvivorMode.byId(tag.getString("Mode"));
        data.freshWorld = tag.getBoolean("Fresh");
        for (Tag t : tag.getList("Lineages", Tag.TAG_COMPOUND)) {
            SurvivorLineage lineage = SurvivorLineage.load((CompoundTag) t);
            if (lineage != null) data.lineages.put(lineage.playerId, lineage);
        }
        for (Tag t : tag.getList("Corpses", Tag.TAG_COMPOUND)) {
            SurvivorCorpseRecord corpse = SurvivorCorpseRecord.load((CompoundTag) t);
            if (corpse != null) {
                corpse.owner = data;
                data.corpses.put(corpse.id, corpse);
            }
        }
        return data;
    }
}
