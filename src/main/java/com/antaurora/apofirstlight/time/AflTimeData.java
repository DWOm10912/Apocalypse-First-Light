package com.antaurora.apofirstlight.time;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World Time V1 (docs/gameplay/world_time_v1_design.md 4): the overworld's clock accumulator and the day length it was
 * gathered at, so a reload goes on from the same share of a unit. The world time itself stays vanilla's (level.dat). A
 * world without it (older saves) starts at 0.
 */
public final class AflTimeData extends SavedData {
    private static final String DATA_ID = "apocalypse_firstlight_world_time";

    int minutes;
    long acc;

    static AflTimeData load(CompoundTag tag) {
        AflTimeData data = new AflTimeData();
        data.minutes = tag.getInt("Minutes");
        data.acc = tag.getLong("Acc");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Minutes", minutes);
        tag.putLong("Acc", acc);
        return tag;
    }

    static AflTimeData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(AflTimeData::load, AflTimeData::new, DATA_ID);
    }

    void store(WorldClock clock) {
        if (minutes == clock.minutes() && acc == clock.acc()) return;
        minutes = clock.minutes();
        acc = clock.acc();
        setDirty();
    }
}
