package com.antaurora.apofirstlight.entity;

import com.antaurora.apofirstlight.registry.AflEntities;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * An office chair someone has sat on (Office Props V2, plan B 2026-10-03). Right-clicking a placed chair block turns it into
 * this entity at the same spot and seats the player; it then stays an entity where it was left (right-click to sit again,
 * hit to pick it up as the chair item; no tool needed, like a boat).
 * <p>Driving (the sitter's client, as a boat): W / S / A / D relative to the view, top speed ~0.6 x walking (scaled down like
 * the sitter's walking: load, cold, slowness; see pushFactor), ~0.4 s to speed up, ~0.65 s glide; no step-up (the casters cannot climb a slab), gravity, falls like a boat. The seat faces the sitter's
 * view at any angle; the five-star base keeps the orientation it was placed with; the casters turn to trail the motion
 * (client visuals only). Drawn by client/OfficeChairRenderer.
 */
public final class OfficeChairEntity extends Entity {
    private static final EntityDataAccessor<Float> BASE_YAW = SynchedEntityData.defineId(OfficeChairEntity.class, EntityDataSerializers.FLOAT);
    /**
     * Rider height above the chair's floor (blocks). Vanilla puts a rider at y + this - 0.35 (player); a seated player's hips
     * are ~0.70 above their feet and the thighs ~0.12 under the hips, so the thighs rest on the cushion (8.75 px).
     */
    private static final double RIDING_OFFSET = 0.31;
    /** Rolling, blocks per tick: top speed (also full volume for client/OfficeChairRollSound), speed kept per tick while
     *  driven / gliding, gravity. */
    public static final double TOP_SPEED = 0.13;
    private static final double DRIVE_KEEP = 0.7, GLIDE_KEEP = 0.8, DRIVE = TOP_SPEED * (1 - DRIVE_KEEP), GRAVITY = 0.08;
    public static final int CASTERS = 5;
    /** Caster turn rate (degrees per tick) and the slowest motion that still turns them (blocks per tick). */
    private static final float CASTER_TURN = 25.0F;
    private static final double CASTER_MIN_SPEED = 0.004;

    private final float[] caster = new float[CASTERS], casterO = new float[CASTERS];
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot;

    public OfficeChairEntity(EntityType<?> type, Level level) {
        super(type, level);
        blocksBuilding = true;
    }

    /** Server: the entity replacing a placed chair block (same spot, base and seat facing the block's facing). */
    public static OfficeChairEntity fromBlock(Level level, BlockPos pos, Direction facing) {
        var chair = new OfficeChairEntity(AflEntities.MODERN_OFFICE_CHAIR.get(), level);
        chair.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, facing.toYRot(), 0.0F);
        chair.setBaseYaw(facing.toYRot());
        return chair;
    }

    public float baseYaw() {
        return entityData.get(BASE_YAW);
    }

    public void setBaseYaw(float yaw) {
        entityData.set(BASE_YAW, Mth.wrapDegrees(yaw));
    }

    /** World yaw the seat faces this frame: the sitter's view, else the chair's own (last) yaw. */
    public float seatYaw(float partialTick) {
        Entity sitter = getFirstPassenger();
        return sitter != null ? Mth.rotLerp(partialTick, sitter.yRotO, sitter.getYRot()) : Mth.rotLerp(partialTick, yRotO, getYRot());
    }

    /** Client: caster k's turn about its stem (degrees, Axis.YP, base model frame) this frame. */
    public float casterYaw(int k, float partialTick) {
        return Mth.rotLerp(partialTick, casterO[k], caster[k]);
    }

    @Override
    public void tick() {
        super.tick();
        tickLerp();
        if (isControlledByLocalInstance()) {
            Vec3 v = getDeltaMovement();
            double vx = v.x, vz = v.z, vy = onGround() ? 0.0 : (v.y - GRAVITY) * 0.98;
            LivingEntity driver = getControllingPassenger();
            double ix = driver == null ? 0 : driver.xxa, iz = driver == null ? 0 : driver.zza, len = Math.sqrt(ix * ix + iz * iz);
            if (len > 1.0E-3) {
                double scale = DRIVE * pushFactor(driver) / Math.max(1.0, len), yaw = Math.toRadians(driver.getYRot()), s = Math.sin(yaw), c = Math.cos(yaw);
                ix *= scale;
                iz *= scale;
                vx = vx * DRIVE_KEEP + ix * c - iz * s;
                vz = vz * DRIVE_KEEP + iz * c + ix * s;
            } else {
                vx *= GLIDE_KEEP;
                vz *= GLIDE_KEEP;
                if (vx * vx + vz * vz < 1.0E-6) vx = vz = 0;
            }
            if (driver != null) setYRot(driver.getYRot());
            setDeltaMovement(vx, vy, vz);
            move(MoverType.SELF, getDeltaMovement());
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        if (level().isClientSide) turnCasters(getX() - xo, getZ() - zo);
    }

    /**
     * How hard the sitter can push (0..1): their movement speed against its base value, so whatever slows their walking
     * (the carried load, cold, slowness) slows the chair the same way; speed boosts do not make it faster than TOP_SPEED.
     */
    private static double pushFactor(LivingEntity driver) {
        double base = driver.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        return base > 0 ? Mth.clamp(driver.getAttributeValue(Attributes.MOVEMENT_SPEED) / base, 0.0, 1.0) : 1.0;
    }

    /** Client: each caster turns (rate-limited) so its wheels line up with this tick's motion, whichever way is nearer. */
    private void turnCasters(double dx, double dz) {
        System.arraycopy(caster, 0, casterO, 0, CASTERS);
        if (dx * dx + dz * dz < CASTER_MIN_SPEED * CASTER_MIN_SPEED) return;
        // world motion into the base model frame (the renderer turns the base by 180 - baseYaw about +Y)
        double a = Math.toRadians(-(180.0 - baseYaw())), c = Math.cos(a), s = Math.sin(a);
        double heading = Math.toDegrees(Math.atan2(dx * c + dz * s, -dx * s + dz * c));
        for (int k = 0; k < CASTERS; k++) {
            float target = Mth.wrapDegrees((float)heading - 72.0F * k), delta = Mth.wrapDegrees(target - caster[k]);
            if (delta > 90.0F) delta -= 180.0F;   // a caster rolls both ways: take the nearer alignment
            else if (delta < -90.0F) delta += 180.0F;
            caster[k] = Mth.wrapDegrees(caster[k] + Mth.clamp(delta, -CASTER_TURN, CASTER_TURN));
        }
    }

    // ---- remote clients: smooth the server's position updates (as a boat) ----

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            double x = getX() + (lerpX - getX()) / lerpSteps, y = getY() + (lerpY - getY()) / lerpSteps, z = getZ() + (lerpZ - getZ()) / lerpSteps;
            setYRot(getYRot() + (float)Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
            --lerpSteps;
            setPos(x, y, z);
            setRot(getYRot(), getXRot());
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = 10;
    }

    // ---- riding ----

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || player.isPassenger() || isVehicle()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public double getPassengersRidingOffset() {
        return RIDING_OFFSET;
    }

    /** The body faces where the sitter looks (the seat turns with the view), as a boat keeps its rider's body. */
    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (passenger instanceof LivingEntity living) living.setYBodyRot(passenger.getYRot());
    }

    /** Off the chair toward where the sitter looks, else to a side or behind; on top of the chair as the last resort. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos pos = blockPosition();
        Direction ahead = Direction.fromYRot(passenger.getYRot());
        for (Direction side : new Direction[]{ahead, ahead.getClockWise(), ahead.getCounterClockWise(), ahead.getOpposite()}) {
            Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos.relative(side), true);
            if (spot != null) return spot;
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ---- picking up ----

    /** A player's hit picks the chair up (the chair item, none in creative); other damage is ignored. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRemoved() || isInvulnerableTo(source)) return false;
        if (!(source.getEntity() instanceof Player)) return false;
        if (level().isClientSide) return true;
        ejectPassengers();
        if (!source.isCreativePlayer() && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS))
            spawnAtLocation(AflItems.MODERN_OFFICE_CHAIR.get());
        discard();
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return !isVehicle();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(AflItems.MODERN_OFFICE_CHAIR.get());
    }

    /** Rolling makes no footstep sounds. */
    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    // ---- data ----

    @Override
    protected void defineSynchedData() {
        entityData.define(BASE_YAW, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setBaseYaw(tag.getFloat("BaseYaw"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("BaseYaw", baseYaw());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
