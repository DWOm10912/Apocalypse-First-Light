package com.antaurora.apofirstlight.creature.infected;

import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchState;
import com.antaurora.apofirstlight.creature.CorpsePoseHolder;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflEntities;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollBody;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollDefinition;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollSnapshot;
import com.antaurora.apofirstlight.survivor.SurvivorPackets;
import com.antaurora.apofirstlight.survivor.SurvivorRagdolls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * An infected's body (docs/gameplay/afl_infected_z0_design.md 12, the corpse rule of docs/gameplay/afl_fauna_plan_v0.md 1):
 * it takes the living one's place at the death and stays, saved with its chunk like any entity, until it rots (Z1d; Z1a's
 * bodies do not rot yet). A fresh body falls as a ragdoll first, as a survivor's does (Ragdoll V1: the player rig's
 * eleven boxes, SurvivorRagdolls; the server simulates it and sends its boxes to the clients that track it, SurvivorPackets
 * .CorpsePose); with too many bodies falling at once, or in water, it lies down on its back at once. A still body does
 * nothing per tick. A click searches it (Progressive Container Search, {@link InfectedCorpseContainer}); Z1a's are empty.
 * It cannot be hurt or pushed and is walked through; cutting it up is Z1d.
 */
public final class InfectedCorpse extends Entity implements IEntityAdditionalSpawnData, CorpsePoseHolder {
    static final int SLOTS = 9;

    private float bodyYaw;
    private long diedAt;
    final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    final AflContainerSearchState search = new AflContainerSearchState();
    /** Server: the falling body, or null once it is still (or never fell). */
    @Nullable private RagdollBody ragdoll;
    /** The pose shown (RagdollSnapshot about {@link #poseOrigin}), or null: lying on its back. */
    @Nullable private float[] pose;
    private Vec3 poseOrigin = Vec3.ZERO;
    private boolean falling;
    /** Server: loaded from a save made while it fell through the air; put on the ground below at the first tick. */
    private boolean unsettled;
    // client: the pose a tick earlier and the newest from the server (taken on at the next tick, one tick behind)
    @Nullable private float[] posePrevious, poseNext;
    private Vec3 poseOriginPrevious = Vec3.ZERO, poseOriginNext = Vec3.ZERO;
    private boolean nextLast, nextLying;

    public InfectedCorpse(EntityType<? extends InfectedCorpse> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Server, at an infected's death: its body where it stood, falling as the death pushes it (or lying down at once). */
    static void spawn(ServerLevel level, InfectedEntity dead, DamageSource source) {
        InfectedCorpse corpse = AflEntities.INFECTED_CORPSE.get().create(level);
        if (corpse == null) return;
        corpse.bodyYaw = dead.yBodyRot;
        corpse.diedAt = level.getGameTime();
        corpse.moveTo(dead.getX(), dead.getY(), dead.getZ(), dead.yBodyRot, 0.0F);
        corpse.poseOrigin = dead.position();
        RagdollBody body = SurvivorRagdolls.creature(dead, source);
        if (body != null) {
            corpse.ragdoll = body;
            corpse.falling = true;
            corpse.pose = RagdollSnapshot.capture(body, vec(corpse.poseOrigin));
        }
        level.addFreshEntity(corpse);
        if (body != null) SurvivorRagdolls.started(corpse.getUUID());
    }

    public float bodyYaw() {
        return bodyYaw;
    }

    /** Game time of the death (the rot clock, Z1d). */
    public long diedAt() {
        return diedAt;
    }

    public boolean falling() {
        return falling;
    }

    /** The pose to draw (RagdollSnapshot about {@link #poseOrigin()}), or null: lying on its back. */
    @Nullable
    public float[] pose() {
        return pose;
    }

    /** Client: the pose one tick before {@link #pose()} (the same once it is still). */
    @Nullable
    public float[] posePrevious() {
        return posePrevious != null ? posePrevious : pose;
    }

    public Vec3 poseOrigin() {
        return poseOrigin;
    }

    public Vec3 poseOriginPrevious() {
        return posePrevious != null ? poseOriginPrevious : poseOrigin;
    }

    @Override
    public void receivePose(Vec3 origin, float[] data, boolean last) {
        boolean lying = last && data.length == 0;
        if (!lying && !RagdollSnapshot.valid(data, RagdollDefinition.get().bodies.length)) return;
        poseNext = data;
        poseOriginNext = origin;
        nextLast |= last;
        nextLying = lying;
    }

    private void takeNextPose() {
        posePrevious = pose;
        poseOriginPrevious = poseOrigin;
        if (poseNext == null) return;
        if (nextLying) {
            pose = posePrevious = null;
        } else {
            pose = poseNext;
            if (posePrevious == null) {
                posePrevious = pose;
                poseOriginPrevious = poseOriginNext;
            }
        }
        poseOrigin = poseOriginNext;
        if (nextLast) {
            falling = false;
            setPos(poseOrigin);
            if (posePrevious != null) {
                posePrevious = rebase(posePrevious, poseOriginPrevious, poseOrigin);
                poseOriginPrevious = poseOrigin;
            }
        }
        poseNext = null;
        nextLast = nextLying = false;
    }

    private static float[] rebase(float[] data, Vec3 from, Vec3 to) {
        float[] out = data.clone();
        for (int o = 0; o < out.length; o += RagdollSnapshot.FLOATS) {
            out[o] += (float) (from.x - to.x);
            out[o + 1] += (float) (from.y - to.y);
            out[o + 2] += (float) (from.z - to.z);
        }
        return out;
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            takeNextPose();
            return;
        }
        if (unsettled) {
            unsettled = false;
            lieOnGround();
        }
        RagdollBody body = ragdoll;
        if (body == null) return;
        long start = System.nanoTime();
        body.tick(SurvivorRagdolls.world((ServerLevel) level()));
        SurvivorRagdolls.measured(level().getGameTime(), System.nanoTime() - start);
        if (body.asleep()) {
            settle();
            return;
        }
        pose = RagdollSnapshot.capture(body, vec(poseOrigin));
        AflNetwork.survivorToTracking(this, new SurvivorPackets.CorpsePose(getId(), poseOrigin, pose, false));
    }

    /** The body is still: the entity moves to where it lies; the clients get the final pose. */
    private void settle() {
        RagdollBody body = stop();
        if (body == null) return;
        Vec3 rest = SurvivorRagdolls.restingPoint(body);
        float[] still = RagdollSnapshot.capture(body, vec(rest));
        if (rest.y < level().getMinBuildHeight()) {
            rest = position();
            still = null;
        }
        pose = still;
        poseOrigin = rest;
        setPos(rest);
        AflNetwork.survivorToTracking(this, new SurvivorPackets.CorpsePose(getId(), rest, still != null ? still : new float[0], true));
    }

    /** Lying on its back on the first ground below (a body saved while it fell through the air). */
    private void lieOnGround() {
        BlockPos.MutableBlockPos p = blockPosition().mutable();
        int floor = level().getMinBuildHeight();
        while (p.getY() > floor && level().getBlockState(p.below()).getCollisionShape(level(), p.below()).isEmpty()) p.move(0, -1, 0);
        pose = null;
        poseOrigin = new Vec3(getX(), p.getY(), getZ());
        setPos(poseOrigin);
        AflNetwork.survivorToTracking(this, new SurvivorPackets.CorpsePose(getId(), poseOrigin, new float[0], true));
    }

    @Nullable
    private RagdollBody stop() {
        RagdollBody body = ragdoll;
        if (body == null) return null;
        ragdoll = null;
        falling = false;
        SurvivorRagdolls.stopped(getUUID());
        body.sleep();
        return body;
    }

    @Override
    public void onRemovedFromWorld() {
        if (!level().isClientSide && ragdoll != null) {
            // unloaded or removed while falling: it keeps its pose where it is if it has touched something
            RagdollBody body = ragdoll;
            Vec3 rest = SurvivorRagdolls.restingPoint(body);
            stop();
            if (body.touched() >= 0 && rest.y >= level().getMinBuildHeight()) {
                pose = RagdollSnapshot.capture(body, vec(rest));
                poseOrigin = rest;
            } else {
                pose = null;
                unsettled = true;
            }
        }
        super.onRemovedFromWorld();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(falling ? 6.0 : 1.5);
    }

    // ---------------------------------------------------------------- search

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer viewer) || !player.isAlive()) return InteractionResult.PASS;
        InfectedCorpseContainer container = new InfectedCorpseContainer(this);
        if (!container.stillValid(viewer)) return InteractionResult.PASS;
        AflContainerSearch.open(viewer, new SimpleMenuProvider((id, inventory, p) -> AflContainerSearch.createMenu(id, inventory, container),
                Component.translatable("container.apocalypse_firstlight.infected_corpse")), container);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    // ---------------------------------------------------------------- save and spawn data

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("BodyYaw", bodyYaw);
        tag.putLong("DiedAt", diedAt);
        ContainerHelper.saveAllItems(tag, items, true);
        search.save(tag);
        RagdollBody body = ragdoll;
        float[] saved = pose;
        Vec3 origin = poseOrigin;
        boolean air = unsettled;
        if (body != null) {
            // saved while falling: where it is now, or (never touched anything) put on the ground when loaded
            origin = SurvivorRagdolls.restingPoint(body);
            air = body.touched() < 0;
            saved = air ? null : RagdollSnapshot.capture(body, vec(origin));
        }
        tag.putDouble("PoseX", origin.x);
        tag.putDouble("PoseY", origin.y);
        tag.putDouble("PoseZ", origin.z);
        if (saved != null) {
            ListTag list = new ListTag();
            for (float f : saved) list.add(FloatTag.valueOf(f));
            tag.put("Pose", list);
        }
        if (air) tag.putBoolean("Unsettled", true);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        bodyYaw = tag.getFloat("BodyYaw");
        diedAt = tag.getLong("DiedAt");
        ContainerHelper.loadAllItems(tag, items);
        search.load(tag);
        poseOrigin = new Vec3(tag.getDouble("PoseX"), tag.getDouble("PoseY"), tag.getDouble("PoseZ"));
        pose = null;
        if (tag.contains("Pose", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Pose", Tag.TAG_FLOAT);
            float[] read = new float[list.size()];
            for (int i = 0; i < read.length; i++) read[i] = list.getFloat(i);
            if (RagdollSnapshot.valid(read, RagdollDefinition.get().bodies.length)) pose = read;
        }
        unsettled = tag.getBoolean("Unsettled");
        if (pose != null || unsettled) setPos(poseOrigin);
        falling = false;
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf b) {
        b.writeFloat(bodyYaw);
        b.writeBoolean(falling);
        b.writeDouble(poseOrigin.x);
        b.writeDouble(poseOrigin.y);
        b.writeDouble(poseOrigin.z);
        SurvivorPackets.writePose(b, pose);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf b) {
        bodyYaw = b.readFloat();
        falling = b.readBoolean();
        poseOrigin = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        float[] read = SurvivorPackets.readPose(b);
        pose = RagdollSnapshot.valid(read, RagdollDefinition.get().bodies.length) ? read : null;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private static Vector3d vec(Vec3 v) {
        return new Vector3d(v.x, v.y, v.z);
    }
}
