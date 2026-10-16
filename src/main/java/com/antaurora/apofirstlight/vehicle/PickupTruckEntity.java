package com.antaurora.apofirstlight.vehicle;

import com.antaurora.apofirstlight.registry.AflEntities;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/**
 * The Halstead Tamarack T15 pickup, body V1 (docs/vehicles/first_pickup_v0_design.md, V0-a first step): a placed truck that
 * shows the Pure Mesh body with its paint, wear, hood, doors and tailgate. No driving, seats, physics, collision or inventory yet.
 * <p>Picking: an entity's own box is square, so the 4.8 m truck is three boxes along its heading: this entity (the cab) and
 * two {@link Part}s (front, bed), as the ender dragon does (Forge PartEntity, ids following this entity's). Right-clicking
 * the front opens / closes the hood; right-clicking a side of the cab opens / closes that door; right-clicking the rear end
 * of the bed opens / closes the tailgate. A creative
 * player's hit removes the truck (test cleanup); other damage does nothing.
 * <p>Frame: the model's front is -Z (rendered turned by 180 - yaw), so the truck's front points along its look direction;
 * "forward" below is metres along the heading from the entity position (the cab's middle), the concept frame of
 * tools/build-pickup-t15.mjs; "lateral" is metres to the truck's right.
 */
public final class PickupTruckEntity extends Entity {
    private static final EntityDataAccessor<Integer> PAINT = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT2 = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WEAR = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HOOD = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TAILGATE = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOOR_L = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOOR_R = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BOOLEAN);
    /** Deep red, wear 0.45: the forest home's truck proposal (design doc section 2.1). */
    public static final int DEFAULT_PAINT = 0x7B1E1A;
    public static final float DEFAULT_WEAR = 0.45F;
    /** Opening angles (hood and tailgate: degrees about the model's +X; doors: about Y, outward) and swing speed (degrees per tick). */
    public static final float HOOD_OPEN = 58.0F, TAILGATE_OPEN = 90.0F, DOOR_OPEN = 68.0F;
    private static final float SWING = 7.0F;
    /** Half the truck's length (with the open tailgate) and its height with the hood open (blocks), for render culling. */
    private static final double HALF_LENGTH = 2.9, HEIGHT = 2.2;
    /** The bed part's rear end: clicks behind this (metres along the heading) work the tailgate. */
    private static final double TAILGATE_ZONE = -1.95;
    /** Clicks on the cab farther than this to a side (metres) work that side's door. */
    private static final double DOOR_ZONE = 0.45;

    private final Part front = new Part(this, "front", 1.8F, 1.3F, 1.60), bed = new Part(this, "bed", 1.8F, 1.2F, -1.45);
    private final Part[] parts = {front, bed};
    private float hood, hoodO, tailgate, tailgateO, doorL, doorLO, doorR, doorRO;

    public PickupTruckEntity(EntityType<? extends PickupTruckEntity> type, Level level) {
        super(type, level);
        // Forge (MC-158205, as the ender dragon): the parts' ids follow this entity's, so client and server agree on them
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        blocksBuilding = true;
    }

    public static PickupTruckEntity create(Level level, Vec3 pos, float yaw) {
        var truck = new PickupTruckEntity(AflEntities.PICKUP_T15.get(), level);
        truck.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        truck.placeParts();
        return truck;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    // ---- paint and moving parts ----

    public int paint() { return entityData.get(PAINT); }
    public int secondPaint() { return entityData.get(PAINT2); }
    public float wear() { return entityData.get(WEAR); }
    public boolean hoodOpen() { return entityData.get(HOOD); }
    public boolean tailgateOpen() { return entityData.get(TAILGATE); }
    public boolean doorOpen(boolean right) { return entityData.get(right ? DOOR_R : DOOR_L); }

    /** Main and second colour (0xRRGGBB; the second is the lower band under the side moulding, the same for one colour). */
    public void setPaint(int main, int second) {
        entityData.set(PAINT, main & 0xFFFFFF);
        entityData.set(PAINT2, second & 0xFFFFFF);
    }

    public void setWear(float wear) { entityData.set(WEAR, Mth.clamp(wear, 0.0F, 1.0F)); }
    public void setHoodOpen(boolean open) { entityData.set(HOOD, open); }
    public void setTailgateOpen(boolean open) { entityData.set(TAILGATE, open); }
    public void setDoorOpen(boolean right, boolean open) { entityData.set(right ? DOOR_R : DOOR_L, open); }

    /** Client: hood / tailgate angle this frame (degrees). */
    public float hoodAngle(float partialTick) { return Mth.lerp(partialTick, hoodO, hood); }
    public float tailgateAngle(float partialTick) { return Mth.lerp(partialTick, tailgateO, tailgate); }
    /** Client: a door's opening angle this frame (degrees, outward). */
    public float doorAngle(boolean right, float partialTick) { return right ? Mth.lerp(partialTick, doorRO, doorR) : Mth.lerp(partialTick, doorLO, doorL); }

    @Override
    public void tick() {
        super.tick();
        hoodO = hood;
        tailgateO = tailgate;
        doorLO = doorL;
        doorRO = doorR;
        hood = approach(hood, hoodOpen() ? HOOD_OPEN : 0.0F, HOOD_OPEN);
        tailgate = approach(tailgate, tailgateOpen() ? TAILGATE_OPEN : 0.0F, TAILGATE_OPEN);
        doorL = approach(doorL, doorOpen(false) ? DOOR_OPEN : 0.0F, DOOR_OPEN);
        doorR = approach(doorR, doorOpen(true) ? DOOR_OPEN : 0.0F, DOOR_OPEN);
        placeParts();
    }

    /** Eased swing over 0..span degrees: fast in the middle, slow at both ends. */
    private static float approach(float angle, float target, float span) {
        float d = target - angle;
        if (Math.abs(d) < 0.05F) return target;
        float step = 1.5F + (SWING - 1.5F) * Mth.sin((float)Math.PI * Mth.clamp(angle / span, 0.0F, 1.0F));
        return angle + Math.copySign(Math.min(Math.abs(d), step), d);
    }

    /** World offset of a point `forward` metres along the heading. */
    private Vec3 along(double forward) {
        double a = Math.toRadians(getYRot());
        return new Vec3(-Math.sin(a) * forward, 0.0, Math.cos(a) * forward);
    }

    private void placeParts() {
        for (Part part : parts) {
            Vec3 p = position().add(along(part.forward));
            part.xo = part.getX();
            part.yo = part.getY();
            part.zo = part.getZ();
            part.setPos(p);
        }
    }

    // ---- interaction ----

    /** A click on a part (or on this entity, the cab) at a world point. */
    InteractionResult interactPart(Part part, Player player, Vec3 at) {
        double a = Math.toRadians(getYRot());
        double forward = (at.x - getX()) * -Math.sin(a) + (at.z - getZ()) * Math.cos(a);
        double lateral = (at.x - getX()) * -Math.cos(a) + (at.z - getZ()) * -Math.sin(a);
        if (part == front) {
            if (!level().isClientSide) setHoodOpen(!hoodOpen());
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (part == null && Math.abs(lateral) > DOOR_ZONE) {
            if (!level().isClientSide) setDoorOpen(lateral > 0, !doorOpen(lateral > 0));
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (part == bed && forward < TAILGATE_ZONE) {
            if (!level().isClientSide) setTailgateOpen(!tailgateOpen());
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? interactPart(null, player, position().add(hit)) : InteractionResult.PASS;
    }

    /** A creative player's hit removes the truck; nothing else harms it in V0. */
    boolean hurtFrom(DamageSource source) {
        if (isRemoved() || !source.isCreativePlayer()) return false;
        if (!level().isClientSide) discard();
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && hurtFrom(source);
    }

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean isMultipartEntity() { return true; }

    @Override
    public PartEntity<?>[] getParts() { return parts; }

    /** The whole truck, for render culling (the entity box is only the cab). */
    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(getX() - HALF_LENGTH, getY(), getZ() - HALF_LENGTH, getX() + HALF_LENGTH, getY() + HEIGHT, getZ() + HALF_LENGTH);
    }

    // ---- data ----

    @Override
    protected void defineSynchedData() {
        entityData.define(PAINT, DEFAULT_PAINT);
        entityData.define(PAINT2, DEFAULT_PAINT);
        entityData.define(WEAR, DEFAULT_WEAR);
        entityData.define(HOOD, false);
        entityData.define(TAILGATE, false);
        entityData.define(DOOR_L, false);
        entityData.define(DOOR_R, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int main = tag.contains("Paint") ? tag.getInt("Paint") : DEFAULT_PAINT;
        setPaint(main, tag.contains("Paint2") ? tag.getInt("Paint2") : main);
        setWear(tag.contains("Wear") ? tag.getFloat("Wear") : DEFAULT_WEAR);
        setHoodOpen(tag.getBoolean("HoodOpen"));
        setTailgateOpen(tag.getBoolean("TailgateOpen"));
        setDoorOpen(false, tag.getBoolean("DoorLeftOpen"));
        setDoorOpen(true, tag.getBoolean("DoorRightOpen"));
        // a reloaded truck shows its parts where they were left, without swinging
        hood = hoodO = hoodOpen() ? HOOD_OPEN : 0.0F;
        tailgate = tailgateO = tailgateOpen() ? TAILGATE_OPEN : 0.0F;
        doorL = doorLO = doorOpen(false) ? DOOR_OPEN : 0.0F;
        doorR = doorRO = doorOpen(true) ? DOOR_OPEN : 0.0F;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Paint", paint());
        tag.putInt("Paint2", secondPaint());
        tag.putFloat("Wear", wear());
        tag.putBoolean("HoodOpen", hoodOpen());
        tag.putBoolean("TailgateOpen", tailgateOpen());
        tag.putBoolean("DoorLeftOpen", doorOpen(false));
        tag.putBoolean("DoorRightOpen", doorOpen(true));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /** One picking box of the truck, `forward` metres along its heading from the cab's middle. */
    public static final class Part extends PartEntity<PickupTruckEntity> {
        private final String name;
        private final net.minecraft.world.entity.EntityDimensions size;
        final double forward;

        Part(PickupTruckEntity parent, String name, float width, float height, double forward) {
            super(parent);
            this.name = name;
            this.size = net.minecraft.world.entity.EntityDimensions.scalable(width, height);
            this.forward = forward;
            refreshDimensions();
        }

        public String partName() { return name; }

        @Override
        public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) { return size; }

        @Override
        public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
            return hand == InteractionHand.MAIN_HAND ? getParent().interactPart(this, player, position().add(hit)) : InteractionResult.PASS;
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return !isInvulnerableTo(source) && getParent().hurtFrom(source);
        }

        @Override
        public boolean isPickable() { return true; }

        @Override
        public boolean is(Entity entity) { return this == entity || getParent() == entity; }

        @Override
        public boolean shouldBeSaved() { return false; }

        @Override
        protected void defineSynchedData() {}

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {}

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {}
    }
}
