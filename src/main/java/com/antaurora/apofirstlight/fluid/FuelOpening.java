package com.antaurora.apofirstlight.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Where a pouring jerry can (item/FuelCanItem) or an inserted dispenser nozzle (blockentity/FuelDispenserBlockEntity) is
 * aimed (2026-10-16): a block's opening (a fill cover, a FuelPourTarget, a fuel container) or an entity's
 * ({@link FuelFillEntity}, the T15's filler). Entities are found by UUID on the server and by network id on clients;
 * the id is written with the UUID and corrected by the server when the entity came back under a new id.
 */
public record FuelOpening(@Nullable BlockPos pos, int entityId, @Nullable UUID uuid) {
    public static FuelOpening block(BlockPos pos) {
        return new FuelOpening(pos.immutable(), -1, null);
    }

    public static FuelOpening entity(Entity entity) {
        return new FuelOpening(null, entity.getId(), entity.getUUID());
    }

    public boolean isEntity() {
        return pos == null;
    }

    /** The entity this opening belongs to (null for a block, or when it is gone or shut out of reach of this side). */
    @Nullable
    public Entity entityIn(Level level) {
        if (pos != null) return null;
        Entity e = level instanceof ServerLevel server && uuid != null ? server.getEntity(uuid) : level.getEntity(entityId);
        return e != null && !e.isRemoved() && e instanceof FuelFillEntity ? e : null;
    }

    @Nullable
    public FuelFillEntity fillEntity(Level level) {
        return entityIn(level) instanceof FuelFillEntity fill ? fill : null;
    }

    /** The same opening with the entity's current network id (the server, after a reload). */
    public FuelOpening withId(int id) {
        return new FuelOpening(pos, id, uuid);
    }

    /** Writes it under `key` (a block as a long, as before; an entity as key + "Entity" and key + "Uuid"). */
    public void save(CompoundTag tag, String key) {
        if (pos != null) tag.putLong(key, pos.asLong());
        else {
            tag.putInt(key + "Entity", entityId);
            if (uuid != null) tag.putUUID(key + "Uuid", uuid);
        }
    }

    @Nullable
    public static FuelOpening load(CompoundTag tag, String key) {
        if (tag.contains(key)) return block(BlockPos.of(tag.getLong(key)));
        if (tag.contains(key + "Entity")) return new FuelOpening(null, tag.getInt(key + "Entity"), tag.hasUUID(key + "Uuid") ? tag.getUUID(key + "Uuid") : null);
        return null;
    }
}
