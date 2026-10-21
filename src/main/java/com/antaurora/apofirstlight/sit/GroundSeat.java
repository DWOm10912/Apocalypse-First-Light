package com.antaurora.apofirstlight.sit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Sitting on the ground (docs/gameplay/splint_animation_v2_plan.md 3): the invisible seat a player sits on with the sit key
 * ({@link GroundSit}), on the collision face under the feet (a block's or a mesh block's). A seat no one drives, so
 * PlayerHealth#resting counts it as rest. Its state is synced for the body's clips (client/GroundSitClient): sitting down
 * ({@link GroundSit#SIT_TICKS}), sat, getting up ({@link GroundSit#STAND_TICKS}, then the player is let off where it sat).
 * Gone when no one sits on it, when the ground under it goes, or in water. Not saved (EntityType noSave).
 */
public final class GroundSeat extends Entity {
    public enum State { SITTING_DOWN, SAT, GETTING_UP }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(GroundSeat.class, EntityDataSerializers.BYTE);
    /** A player rider is put at y + this - 0.35 (Player#getMyRidingOffset): its feet stay on the seat's face. */
    private static final double RIDING_OFFSET = 0.35;
    /** The head turns this far each side of the way the body sits (deg). */
    private static final float HEAD_TURN = 100F;

    private int age, risingFor = -1;
    private boolean standQueued;

    public GroundSeat(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public State state() {
        byte b = entityData.get(STATE);
        return State.values()[Mth.clamp(b, 0, 2)];
    }

    /** Server: get up once sat down (a request while sitting down waits for it). */
    void rise() {
        if (risingFor >= 0) return;
        if (state() == State.SITTING_DOWN) standQueued = true;
        else startRising();
    }

    private void startRising() {
        risingFor = 0;
        entityData.set(STATE, (byte) State.GETTING_UP.ordinal());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STATE, (byte) State.SITTING_DOWN.ordinal());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return new ClientboundAddEntityPacket(this); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Entity rider = getFirstPassenger();
        if (rider == null) {
            discard();
            return;
        }
        age++;
        if (age >= GroundSit.SIT_TICKS && state() == State.SITTING_DOWN) {
            entityData.set(STATE, (byte) State.SAT.ordinal());
            if (standQueued) startRising();
        }
        // the ground went from under it, or water came up: off at once
        if (!GroundSit.supported(level(), rider.getBoundingBox()) || isInWater() || rider.isInWater()) {
            ejectPassengers();
            discard();
            return;
        }
        if (risingFor >= 0 && ++risingFor >= GroundSit.STAND_TICKS) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        passenger.refreshDimensions();   // the seated eye and box (GroundSit#size)
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        passenger.refreshDimensions();
    }

    @Override
    public double getPassengersRidingOffset() { return RIDING_OFFSET; }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        clampRotation(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) { clampRotation(passenger); }

    /** The body keeps the way it sat down, the head within {@link #HEAD_TURN} of it (as FurnitureSeat). */
    private void clampRotation(Entity passenger) {
        if (passenger instanceof LivingEntity living) living.setYBodyRot(getYRot());
        float f = Mth.wrapDegrees(passenger.getYRot() - getYRot()), g = Mth.clamp(f, -HEAD_TURN, HEAD_TURN);
        passenger.yRotO += g - f;
        passenger.setYRot(passenger.getYRot() + g - f);
        passenger.setYHeadRot(passenger.getYRot());
    }

    /** Up where it sat (the spot it stood on), else the nearest free spot around. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 here = position();
        if (level().noCollision(passenger, passenger.getDimensions(Pose.STANDING).makeBoundingBox(here).deflate(1.0E-6))) return here;
        Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), blockPosition(), true);
        return spot != null ? spot : here;
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    /** The skeleton poses the sitter (body.sit); the vanilla model keeps its standing legs. */
    @Override
    public boolean shouldRiderSit() { return false; }
}
