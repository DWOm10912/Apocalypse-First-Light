package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A dead survivor's body (docs/gameplay/survivor_death_succession_v0.md 6). The record, not the entity, is the truth:
 * it lives in the world's survivor data with the items, the search state and the identity, so chunk unloads, restarts,
 * {@code /kill} or a lost entity never touch the items. The entity ({@link SurvivorCorpse}, never saved) is only the
 * body in the world and the thing to click; it is put back whenever its chunk is loaded.
 *
 * <p>The items sit packed in the first slots of a 9 x 1..6 grid, the smallest that holds them (a survivor carries at most
 * 36 + 4 + 1 + Curios slots, under 54 with the current slot set).
 */
public final class SurvivorCorpseRecord {
    static final int MAX_SLOTS = 54;
    /** The saved pose's layout: 1 = Ragdoll V1, 11 boxes x 7 floats. */
    static final int POSE_VERSION = 1;

    final UUID id;
    final UUID playerId;
    final UUID survivorId;
    final String survivorName;
    final int lifeIndex;
    final String dimension;
    /** Where the body lies (the entity's place): the death's place, then where its ragdoll came to rest. */
    double x;
    double y;
    double z;
    final float yaw;
    final long deathGameTime;
    final long deathDayTime;
    /** The dead survivor's own look (SurvivorSkins), copied at the death: later successors never change it. */
    String skin = SurvivorSkins.ACCOUNT;
    SurvivorSkins.Arms arms = SurvivorSkins.Arms.ACCOUNT;
    NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    /**
     * Ragdoll V1 (docs/dev/ragdoll_v1_plan.md 3): the pose the body came to rest in (RagdollSnapshot, relative to x y z),
     * or null: a body from before V1, a death that did not fall as a ragdoll, or one still falling. Null lies on its back.
     */
    @Nullable float[] pose;
    /**
     * Ragdoll V1: the body stopped in the air or below the world when its chunk unloaded (the level may not be read
     * then); it lies down on the surface below when its entity is put back (SurvivorCorpses#tick).
     */
    boolean unsettled;
    final AflContainerSearchState search = new AflContainerSearchState();

    /** The data this record belongs to (dirty marking); set on add and load. */
    @Nullable SurvivorWorldData owner;
    /** The one shared container of this body: every viewer works on the same object (concurrent searchers). */
    @Nullable SurvivorCorpseContainer container;

    SurvivorCorpseRecord(UUID id, UUID playerId, UUID survivorId, String survivorName, int lifeIndex, String dimension,
                         double x, double y, double z, float yaw, long deathGameTime, long deathDayTime) {
        this.id = id;
        this.playerId = playerId;
        this.survivorId = survivorId;
        this.survivorName = survivorName;
        this.lifeIndex = lifeIndex;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.deathGameTime = deathGameTime;
        this.deathDayTime = deathDayTime;
    }

    public UUID id() {
        return id;
    }

    public String survivorName() {
        return survivorName;
    }

    public int itemCount() {
        int n = 0;
        for (ItemStack stack : items) if (!stack.isEmpty()) n++;
        return n;
    }

    /**
     * Packs more stacks in (the death's drops, later a sweep), growing the grid by whole rows up to 9 x 6. Returns the
     * stacks that did not fit (only possible with more than 54 stacks); the caller drops those in the world.
     */
    List<ItemStack> addItems(List<ItemStack> stacks) {
        List<ItemStack> overflow = new java.util.ArrayList<>();
        int used = itemCount();
        int needed = Math.min(MAX_SLOTS, used + (int) stacks.stream().filter(s -> !s.isEmpty()).count());
        int size = Math.max(9, Math.min(MAX_SLOTS, (needed + 8) / 9 * 9));
        if (size > items.size()) {
            NonNullList<ItemStack> grown = NonNullList.withSize(size, ItemStack.EMPTY);
            for (int i = 0; i < items.size(); i++) grown.set(i, items.get(i));
            items = grown;
        }
        int slot = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            while (slot < items.size() && !items.get(slot).isEmpty()) slot++;
            if (slot >= items.size()) {
                overflow.add(stack);
            } else {
                items.set(slot, stack);
            }
        }
        changed();
        return overflow;
    }

    /** The body came to rest: its place and pose. */
    void rest(double x, double y, double z, @Nullable float[] pose) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.pose = pose;
        this.unsettled = false;
        changed();
    }

    /** The body stopped where it hung (chunk unloaded mid-fall): no pose, put on the ground later. */
    void stopInAir(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.pose = null;
        this.unsettled = true;
        changed();
    }

    void changed() {
        if (owner != null) owner.setDirty();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Player", playerId);
        tag.putUUID("Survivor", survivorId);
        tag.putString("Name", survivorName);
        tag.putInt("Life", lifeIndex);
        tag.putString("Dim", dimension);
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Yaw", yaw);
        tag.putLong("DeathGame", deathGameTime);
        tag.putLong("DeathDay", deathDayTime);
        tag.putString("Skin", skin);
        tag.putString("Arms", arms.id());
        tag.putInt("Size", items.size());
        ContainerHelper.saveAllItems(tag, items, true);
        search.save(tag);
        if (pose != null) {
            tag.putInt("PoseV", POSE_VERSION);
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (float f : pose) list.add(net.minecraft.nbt.FloatTag.valueOf(f));
            tag.put("Pose", list);
        }
        if (unsettled) tag.putBoolean("Unsettled", true);
        return tag;
    }

    @Nullable
    static SurvivorCorpseRecord load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("Player") || !tag.hasUUID("Survivor")) return null;
        SurvivorCorpseRecord r = new SurvivorCorpseRecord(tag.getUUID("Id"), tag.getUUID("Player"), tag.getUUID("Survivor"),
                tag.getString("Name"), tag.getInt("Life"), tag.getString("Dim"), tag.getDouble("X"), tag.getDouble("Y"),
                tag.getDouble("Z"), tag.getFloat("Yaw"), tag.getLong("DeathGame"), tag.getLong("DeathDay"));
        if (tag.contains("Skin", Tag.TAG_STRING)) {
            r.skin = tag.getString("Skin");
            r.arms = SurvivorSkins.Arms.byId(tag.getString("Arms"));
        }
        int size = tag.contains("Size", Tag.TAG_INT) ? tag.getInt("Size") : 9;
        size = Math.max(9, Math.min(MAX_SLOTS, size / 9 * 9));
        r.items = NonNullList.withSize(size, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, r.items);
        r.search.load(tag);
        if (tag.getInt("PoseV") == POSE_VERSION && tag.contains("Pose", Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = tag.getList("Pose", Tag.TAG_FLOAT);
            float[] pose = new float[list.size()];
            for (int i = 0; i < pose.length; i++) pose[i] = list.getFloat(i);
            // a pose of another body layout (a later rig) is dropped: the body lies on its back
            if (com.antaurora.apofirstlight.skeleton.ragdoll.RagdollSnapshot.valid(pose,
                    com.antaurora.apofirstlight.skeleton.ragdoll.RagdollDefinition.get().bodies.length)) r.pose = pose;
        }
        r.unsettled = tag.getBoolean("Unsettled");
        return r;
    }
}
