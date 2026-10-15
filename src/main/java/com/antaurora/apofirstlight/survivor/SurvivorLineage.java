package com.antaurora.apofirstlight.survivor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * All survivors one player (account UUID) has played in this world, oldest first. The last entry may be a PENDING
 * successor; every entry before it is DEAD. Status:
 * <ul>
 * <li>ACTIVE: the newest survivor is alive.</li>
 * <li>AWAITING_SUCCESSOR (NORMAL): the newest survivor died, a PENDING successor is stored and waits for the respawn.</li>
 * <li>ENDED (HARD): the newest survivor died and the run is over; no successor, no respawn, ever.</li>
 * </ul>
 */
public final class SurvivorLineage {
    public enum Status { ACTIVE, AWAITING_SUCCESSOR, ENDED }

    final UUID playerId;
    String accountName;
    Status status = Status.ACTIVE;
    final List<SurvivorRecord> lives = new ArrayList<>();
    /** The respawn point the last dead survivor had (its bed), kept so a successor never starts next to it. */
    @Nullable String lastBedDimension;
    @Nullable BlockPos lastBed;

    SurvivorLineage(UUID playerId, String accountName) {
        this.playerId = playerId;
        this.accountName = accountName;
    }

    public UUID playerId() {
        return playerId;
    }

    public Status status() {
        return status;
    }

    public List<SurvivorRecord> lives() {
        return Collections.unmodifiableList(lives);
    }

    /** The survivor the player plays or last played (never a PENDING successor), or null for a new lineage. */
    @Nullable
    public SurvivorRecord current() {
        for (int i = lives.size() - 1; i >= 0; i--) {
            SurvivorRecord r = lives.get(i);
            if (r.state != SurvivorRecord.State.PENDING) return r;
        }
        return null;
    }

    /** The rolled successor waiting for the respawn, or null. */
    @Nullable
    public SurvivorRecord pending() {
        if (lives.isEmpty()) return null;
        SurvivorRecord last = lives.get(lives.size() - 1);
        return last.state == SurvivorRecord.State.PENDING ? last : null;
    }

    @Nullable
    SurvivorRecord find(UUID survivorId) {
        for (SurvivorRecord r : lives) {
            if (r.id.equals(survivorId)) return r;
        }
        return null;
    }

    int nextLifeIndex() {
        return lives.isEmpty() ? 1 : lives.get(lives.size() - 1).lifeIndex + 1;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Player", playerId);
        tag.putString("Account", accountName);
        tag.putString("Status", status.name());
        ListTag list = new ListTag();
        for (SurvivorRecord r : lives) list.add(r.save());
        tag.put("Lives", list);
        if (lastBed != null && lastBedDimension != null) {
            tag.putString("BedDim", lastBedDimension);
            tag.putLong("Bed", lastBed.asLong());
        }
        return tag;
    }

    @Nullable
    static SurvivorLineage load(CompoundTag tag) {
        if (!tag.hasUUID("Player")) return null;
        SurvivorLineage lineage = new SurvivorLineage(tag.getUUID("Player"), tag.getString("Account"));
        try {
            lineage.status = Status.valueOf(tag.getString("Status"));
        } catch (IllegalArgumentException e) {
            lineage.status = Status.ACTIVE;
        }
        for (Tag t : tag.getList("Lives", Tag.TAG_COMPOUND)) {
            SurvivorRecord r = SurvivorRecord.load((CompoundTag) t);
            if (r != null) lineage.lives.add(r);
        }
        if (tag.contains("Bed", Tag.TAG_LONG) && tag.contains("BedDim", Tag.TAG_STRING)) {
            lineage.lastBedDimension = tag.getString("BedDim");
            lineage.lastBed = BlockPos.of(tag.getLong("Bed"));
        }
        return lineage;
    }
}
