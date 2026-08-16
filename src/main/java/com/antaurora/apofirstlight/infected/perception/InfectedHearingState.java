package com.antaurora.apofirstlight.infected.perception;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

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
    private static final String PHASE = "phase";
    private static final String SEARCH_START = "search_start";
    private static final String SEARCH_INDEX = "search_index";
    private static final String SEARCH_POINTS = "search_points";

    public enum Phase {
        INVESTIGATING,
        SEARCHING
    }

    private InfectedHearingState() {
    }

    public static void hear(LivingEntity entity, Vec3 position, long gameTime, String type) {
        CompoundTag tag = entity.getPersistentData().getCompound(ROOT);
        boolean hadHearingState = tag.getBoolean(VALID);
        long previousInvestigationStart = tag.getLong(INVESTIGATE_START);
        long previousSearchStart = tag.getLong(SEARCH_START);
        boolean expiredState = hadHearingState
                && ((previousInvestigationStart > 0L && gameTime - previousInvestigationStart > 200L)
                || (Phase.SEARCHING.name().equals(tag.getString(PHASE))
                && previousSearchStart > 0L && gameTime - previousSearchStart > 160L));
        tag.putBoolean(VALID, true);
        tag.putDouble(X, position.x());
        tag.putDouble(Y, position.y());
        tag.putDouble(Z, position.z());
        tag.putLong(GAME_TIME, gameTime);
        tag.putString(TYPE, type);
        tag.putString(PHASE, Phase.INVESTIGATING.name());
        if (!hadHearingState || expiredState) {
            tag.putLong(INVESTIGATE_START, gameTime);
        }
        tag.putLong(WAIT_UNTIL, 0L);
        tag.remove(SEARCH_POINTS);
        tag.putInt(SEARCH_INDEX, 0);
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

    public static Phase phase(LivingEntity entity) {
        String value = entity.getPersistentData().getCompound(ROOT).getString(PHASE);
        return Phase.SEARCHING.name().equals(value) ? Phase.SEARCHING : Phase.INVESTIGATING;
    }

    public static void beginSearching(LivingEntity entity, long gameTime, List<Vec3> points) {
        CompoundTag tag = entity.getPersistentData().getCompound(ROOT);
        ListTag list = new ListTag();
        for (Vec3 point : points) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putDouble(X, point.x());
            pointTag.putDouble(Y, point.y());
            pointTag.putDouble(Z, point.z());
            list.add(pointTag);
        }
        tag.putString(PHASE, Phase.SEARCHING.name());
        tag.putLong(SEARCH_START, gameTime);
        tag.putInt(SEARCH_INDEX, 0);
        tag.put(SEARCH_POINTS, list);
        tag.putLong(WAIT_UNTIL, 0L);
        entity.getPersistentData().put(ROOT, tag);
    }

    public static long searchStart(LivingEntity entity) {
        return entity.getPersistentData().getCompound(ROOT).getLong(SEARCH_START);
    }

    public static int searchIndex(LivingEntity entity) {
        return entity.getPersistentData().getCompound(ROOT).getInt(SEARCH_INDEX);
    }

    public static void setSearchIndex(LivingEntity entity, int index) {
        CompoundTag tag = entity.getPersistentData().getCompound(ROOT);
        tag.putInt(SEARCH_INDEX, index);
        tag.putLong(WAIT_UNTIL, 0L);
        entity.getPersistentData().put(ROOT, tag);
    }

    public static List<Vec3> searchPoints(LivingEntity entity) {
        List<Vec3> points = new ArrayList<>();
        ListTag list = entity.getPersistentData().getCompound(ROOT).getList(SEARCH_POINTS, Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            CompoundTag pointTag = (CompoundTag) entry;
            points.add(new Vec3(pointTag.getDouble(X), pointTag.getDouble(Y), pointTag.getDouble(Z)));
        }
        return points;
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
