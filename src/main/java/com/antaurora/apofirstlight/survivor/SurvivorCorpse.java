package com.antaurora.apofirstlight.survivor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollBody;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollDefinition;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollSnapshot;

import java.util.UUID;

/**
 * A dead survivor's body in the world (placeholder look: the owner's skin lying on its back, darkened; client
 * SurvivorCorpseRenderer). Never saved and never summoned: the corpse record in the survivor data is the truth and
 * SurvivorCorpses puts this entity back whenever its chunk is loaded. It cannot be hurt or pushed and is walked through;
 * a click opens the body's search.
 *
 * <p>Ragdoll V1 (docs/dev/ragdoll_v1_plan.md 3): a fresh body falls first. The server simulates it every tick and sends
 * its boxes to the clients that track it (SurvivorPackets.CorpsePose); once it is still, the pose and the place go into
 * the record and the entity moves to where it lies. Unloaded while falling, it stops where it is (in the air: it lies
 * down on the surface below when it is put back). After that it does
 * nothing per tick (no physics, no gravity, no fire, no AI).
 *
 * <p>Its UUID is the corpse id, so the level's own UUID check refuses a second copy.
 */
public final class SurvivorCorpse extends Entity implements IEntityAdditionalSpawnData {
    private static final EntityDataAccessor<Boolean> SEARCHED = SynchedEntityData.defineId(SurvivorCorpse.class,
            EntityDataSerializers.BOOLEAN);

    private UUID ownerPlayer = new UUID(0L, 0L);
    private String survivorName = "";
    private float bodyYaw;
    /** The dead survivor's look (SurvivorSkins id and arm model). */
    private String skin = SurvivorSkins.ACCOUNT;
    private SurvivorSkins.Arms arms = SurvivorSkins.Arms.ACCOUNT;
    /** Server: the falling body, or null once it is still (or never fell). */
    @Nullable private RagdollBody ragdoll;
    /** The pose shown (RagdollSnapshot relative to poseOrigin), or null (lying on the back, the V0 look). */
    @Nullable private float[] pose;
    private Vec3 poseOrigin = Vec3.ZERO;
    private boolean falling;
    /** Client: the pose one tick earlier (drawn from it to {@link #pose} over the tick) and its origin. */
    @Nullable private float[] posePrevious;
    private Vec3 poseOriginPrevious = Vec3.ZERO;
    /** Client: the newest pose from the server, taken on at the next client tick (one tick behind, smooth). */
    @Nullable private float[] poseNext;
    private Vec3 poseOriginNext = Vec3.ZERO;
    private boolean nextLast, nextLying;

    public SurvivorCorpse(EntityType<? extends SurvivorCorpse> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = false;
    }

    void setup(SurvivorCorpseRecord record) {
        setUUID(record.id);
        ownerPlayer = record.playerId;
        survivorName = record.survivorName;
        bodyYaw = record.yaw;
        skin = record.skin;
        arms = record.arms;
        moveTo(record.x, record.y, record.z, record.yaw, 0.0F);
        pose = record.pose;
        poseOrigin = new Vec3(record.x, record.y, record.z);
    }

    /** Server, right after setup at the death: this body falls, starting as {@code body}. */
    void fall(RagdollBody body) {
        ragdoll = body;
        falling = true;
        poseOrigin = position();
        pose = RagdollSnapshot.capture(body, new Vector3d(poseOrigin.x, poseOrigin.y, poseOrigin.z));
        SurvivorRagdolls.started(getUUID());
    }

    /** Still falling (server: being simulated; client: no final pose received yet). */
    public boolean falling() {
        return falling;
    }

    /** The pose to draw (RagdollSnapshot relative to {@link #poseOrigin()}), or null: lying on the back. */
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

    /** Client: a pose from the server (SurvivorPackets.CorpsePose); an empty last pose: it lies on its back after all. */
    public void receivePose(Vec3 origin, float[] data, boolean last) {
        boolean lying = last && data.length == 0;
        if (!lying && !RagdollSnapshot.valid(data, RagdollDefinition.get().bodies.length)) return;
        poseNext = data;
        poseOriginNext = origin;
        nextLast |= last;
        nextLying = lying;
    }

    /** Client tick: the queued pose becomes the drawn one. */
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
            // the entity moved under the pose: the previous snapshot is drawn from the same origin
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

    /** Drawn wider than its click box: a falling body moves away from the entity, a resting one is 1.9 m long. */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(falling ? 6.0 : 1.5);
    }

    void setSearched(boolean searched) {
        entityData.set(SEARCHED, searched);
    }

    public boolean searched() {
        return entityData.get(SEARCHED);
    }

    public UUID ownerPlayer() {
        return ownerPlayer;
    }

    public String survivorName() {
        return survivorName;
    }

    public float bodyYaw() {
        return bodyYaw;
    }

    /** The dead survivor's skin id, or SurvivorSkins.ACCOUNT for the owner's account skin. */
    public String skin() {
        return skin;
    }

    public SurvivorSkins.Arms arms() {
        return arms;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SEARCHED, false);
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            takeNextPose();
            return;
        }
        RagdollBody body = ragdoll;
        if (body == null) return;   // a still body: nothing to simulate
        long start = System.nanoTime();
        body.tick(SurvivorRagdolls.world((net.minecraft.server.level.ServerLevel) level()));
        SurvivorRagdolls.measured(level().getGameTime(), System.nanoTime() - start);
        if (body.asleep()) {
            settle();
            return;
        }
        pose = RagdollSnapshot.capture(body, new Vector3d(poseOrigin.x, poseOrigin.y, poseOrigin.z));
        AflNetwork.survivorToTracking(this, new SurvivorPackets.CorpsePose(getId(), poseOrigin, pose, false));
    }

    /**
     * Server tick: the body is still. The record takes its place and pose, the entity moves there and the clients get the
     * final pose. No pose (it lies on its back, on top of the column) for a body that ended below the world.
     */
    private void settle() {
        RagdollBody body = stop();
        if (body == null) return;
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) level();
        Vec3 rest = SurvivorRagdolls.restingPoint(body);
        float[] still = RagdollSnapshot.capture(body, new Vector3d(rest.x, rest.y, rest.z));
        if (rest.y < level.getMinBuildHeight()) {
            rest = SurvivorCorpses.ground(level, rest);
            still = null;
        }
        pose = still;
        poseOrigin = rest;
        setPos(rest);
        SurvivorCorpseRecord record = SurvivorWorldData.get(level.getServer()).corpse(getUUID());
        if (record != null) {
            record.rest(rest.x, rest.y, rest.z, still);
            Survivors.markFlush();
        }
        AflNetwork.survivorToTracking(this, new SurvivorPackets.CorpsePose(getId(), rest, still != null ? still : new float[0], true));
    }

    /**
     * Server: unloaded, removed or the server stopping while falling. Removal runs inside the chunk manager's own update
     * (a chunk leaving entity range), where reading blocks may load chunks and break it: only the body and the record are
     * touched. A body that has touched something keeps its pose where it is; one still in the air (or below the world)
     * keeps no pose and lies down on the surface below once its entity is put back (SurvivorCorpses#tick).
     */
    void stopWhereItIs() {
        RagdollBody body = stop();
        if (body == null) return;
        Vec3 rest = SurvivorRagdolls.restingPoint(body);
        SurvivorCorpseRecord record = SurvivorWorldData.get(level().getServer()).corpse(getUUID());
        if (record == null) return;
        if (rest.y >= level().getMinBuildHeight() && body.touched() >= 0) {
            record.rest(rest.x, rest.y, rest.z, RagdollSnapshot.capture(body, new Vector3d(rest.x, rest.y, rest.z)));
        } else {
            record.stopInAir(rest.x, rest.y, rest.z);
        }
        Survivors.markFlush();
    }

    /** The falling body, now stopped (null when it was not falling). */
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
        if (!level().isClientSide) stopWhereItIs();
        super.onRemovedFromWorld();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        return player instanceof ServerPlayer serverPlayer && SurvivorCorpses.open(serverPlayer, this)
                ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public Component getName() {
        return survivorName.isEmpty() ? super.getName()
                : Component.translatable("entity.apocalypse_firstlight.survivor_corpse.named", survivorName);
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

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf b) {
        b.writeUUID(ownerPlayer);
        b.writeUtf(survivorName, 64);
        b.writeFloat(bodyYaw);
        b.writeUtf(skin, 64);
        b.writeUtf(arms.id(), 16);
        b.writeBoolean(falling);
        b.writeDouble(poseOrigin.x);
        b.writeDouble(poseOrigin.y);
        b.writeDouble(poseOrigin.z);
        SurvivorPackets.writePose(b, pose);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf b) {
        ownerPlayer = b.readUUID();
        survivorName = b.readUtf(64);
        bodyYaw = b.readFloat();
        skin = b.readUtf(64);
        arms = SurvivorSkins.Arms.byId(b.readUtf(16));
        falling = b.readBoolean();
        poseOrigin = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        float[] read = SurvivorPackets.readPose(b);
        pose = RagdollSnapshot.valid(read, RagdollDefinition.get().bodies.length) ? read : null;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
