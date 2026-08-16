package com.antaurora.apofirstlight.infected.perception;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

public final class InfectedHearingState {
    private static final String ROOT = "apocalypse_firstlight_hearing";
    private static final String VALID = "valid";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String GAME_TIME = "game_time";
    private static final String TYPE = "type";
    private static final String INVESTIGATE_START = "investigate_start";
    private static final String WAIT_UNTIL = "wait_until";
    private static final String LAST_PATH_REFRESH = "last_path_refresh";
    private static final String LAST_PATH_X = "last_path_x";
    private static final String LAST_PATH_Y = "last_path_y";
    private static final String LAST_PATH_Z = "last_path_z";

    private InfectedHearingState() {
    }

    public static void hear(LivingEntity entity, Vec3 position, long gameTime, String type) {
        CompoundTag tag = entity.getPersistentData().getCompound(ROOT);
        boolean hadHearingState = tag.getBoolean(VALID);
        tag.putBoolean(VALID, true);
        tag.putDouble(X, position.x());
        tag.putDouble(Y, position.y());
        tag.putDouble(Z, position.z());
        tag.putLong(GAME_TIME, gameTime);
        tag.putString(TYPE, type);
        if (!hadHearingState) {
            tag.putLong(INVESTIGATE_START, gameTime);
        }
        tag.putLong(WAIT_UNTIL, 0L);
        entity.getPersistentData().put(ROOT, tag);
    }

    public static boolean isValid(LivingEntity entity) {
        return entity.getPersistentData().getCompound(ROOT).getBoolean(VALID);
    }

    public static long heardGameTime(LivingEntity entity) {
        return entity.getPersistentData().getCompound(ROOT).getLong(GAME_TIME);
    }

    public static long investigateStart(LivingEntity entity) {
        return entity.getPersistentData().getCompound(ROOT).getLong(INVESTIGATE_START);
    }

    public static long waitUntil(LivingEntity entity) {
        return entity.getPersistentData().getCompound(ROOT).getLong(WAIT_UNTIL);
    }

    public static void setWaitUntil(LivingEntity entity, long gameTime) {
        CompoundTag tag = entity.getPersistentData().getCompound(ROOT);
        tag.putLong(WAIT_UNTIL, gameTime);
        entity.getPersistentData().put(ROOT, tag);
    }

    @Nullable
    public static Vec3 lastHeardPosition(LivingEntity entity) {
        CompoundTag tag = entity.getPersistentData().getCompound(ROOT);
        return tag.getBoolean(VALID) ? new Vec3(tag.getDouble(X), tag.getDouble(Y), tag.getDouble(Z)) : null;
    }

    public static boolean shouldRefreshPath(LivingEntity entity, Vec3 position, long gameTime) {
        CompoundTag tag = entity.getPersistentData().getCompound(ROOT);
        long lastRefresh = tag.getLong(LAST_PATH_REFRESH);
        if (gameTime - lastRefresh >= 10L) {
            return true;
        }
        Vec3 lastPath = new Vec3(tag.getDouble(LAST_PATH_X), tag.getDouble(LAST_PATH_Y), tag.getDouble(LAST_PATH_Z));
        return lastPath.distanceToSqr(position) > 16.0;
    }

    public static void markPathRefresh(LivingEntity entity, Vec3 position, long gameTime) {
        CompoundTag tag = entity.getPersistentData().getCompound(ROOT);
        tag.putLong(LAST_PATH_REFRESH, gameTime);
        tag.putDouble(LAST_PATH_X, position.x());
        tag.putDouble(LAST_PATH_Y, position.y());
        tag.putDouble(LAST_PATH_Z, position.z());
        entity.getPersistentData().put(ROOT, tag);
    }

    public static void clear(LivingEntity entity) {
        entity.getPersistentData().remove(ROOT);
    }
}
