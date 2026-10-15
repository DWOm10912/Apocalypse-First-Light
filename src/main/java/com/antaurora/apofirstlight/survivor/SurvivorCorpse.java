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
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * A dead survivor's body in the world (placeholder look: the owner's skin lying on its back, darkened; client
 * SurvivorCorpseRenderer). Never saved and never summoned: the corpse record in the survivor data is the truth and
 * SurvivorCorpses puts this entity back whenever its chunk is loaded. It does nothing per tick (no physics, no gravity,
 * no fire, no AI), cannot be hurt or pushed, and is walked through; a click opens the body's search.
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
        // a still body: nothing to simulate
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
    }

    @Override
    public void readSpawnData(FriendlyByteBuf b) {
        ownerPlayer = b.readUUID();
        survivorName = b.readUtf(64);
        bodyYaw = b.readFloat();
        skin = b.readUtf(64);
        arms = SurvivorSkins.Arms.byId(b.readUtf(16));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
