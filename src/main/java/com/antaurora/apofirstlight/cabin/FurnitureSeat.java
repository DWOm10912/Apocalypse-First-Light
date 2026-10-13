package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Wood Furniture V1 (docs/models/wood_furniture_v1.md): the invisible seat of a {@link WoodFurniture.Chair}. Sitting on a
 * chair spawns one on the chair's floor and seats the player (the chair stays a block); it goes away when the sitter
 * stands up (sneak) or the chair is gone. Not saved (EntityType noSave). The body faces the chair's way, the head turns up
 * to 105 degrees each side (as in a boat).
 */
public final class FurnitureSeat extends Entity {
    /**
     * Rider offset above the chair's floor (blocks): vanilla puts a player rider at y + this - 0.35. Calibrated like the office
     * chair (OfficeChairEntity: 0.31 for its 0.547 cushion): the thighs rest on the 0.465 seat.
     */
    private static final double RIDING_OFFSET = 0.228;

    public FurnitureSeat(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Whether someone sits on the chair at pos. */
    public static boolean occupied(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(FurnitureSeat.class, new AABB(pos), FurnitureSeat::isVehicle).isEmpty();
    }

    /** Server: seats the player on the chair at pos (facing its way). */
    static boolean sit(Level level, BlockPos pos, Direction facing, Player player) {
        for (FurnitureSeat old : level.getEntitiesOfClass(FurnitureSeat.class, new AABB(pos))) if (!old.isVehicle()) old.discard();
        var seat = new FurnitureSeat(WoodFurniture.SEAT, level);
        seat.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, facing.toYRot(), 0.0F);
        if (!level.addFreshEntity(seat)) return false;
        if (player.startRiding(seat)) return true;
        seat.discard();
        return false;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return new ClientboundAddEntityPacket(this); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (!isVehicle() || !(level().getBlockState(blockPosition()).getBlock() instanceof WoodFurniture.Chair))) {
            ejectPassengers();
            discard();
        }
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

    /** The body faces the chair's way, the head within 105 degrees of it (Boat#clampRotation). */
    private void clampRotation(Entity passenger) {
        if (passenger instanceof LivingEntity living) living.setYBodyRot(getYRot());
        float f = Mth.wrapDegrees(passenger.getYRot() - getYRot()), g = Mth.clamp(f, -105.0F, 105.0F);
        passenger.yRotO += g - f;
        passenger.setYRot(passenger.getYRot() + g - f);
        passenger.setYHeadRot(passenger.getYRot());
    }

    /** Up in front of the chair, else to a side or behind it; on the chair as the last resort. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos pos = blockPosition();
        Direction ahead = Direction.fromYRot(getYRot());
        for (Direction side : new Direction[]{ahead, ahead.getClockWise(), ahead.getCounterClockWise(), ahead.getOpposite()}) {
            Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos.relative(side), true);
            if (spot != null) return spot;
        }
        return new Vec3(getX(), pos.getY() + 1.0, getZ());
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean shouldRiderSit() { return true; }
}
