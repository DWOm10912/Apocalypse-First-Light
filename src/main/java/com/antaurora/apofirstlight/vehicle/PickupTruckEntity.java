package com.antaurora.apofirstlight.vehicle;

import com.antaurora.apofirstlight.block.FuelDispenserBlock;
import com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity;
import com.antaurora.apofirstlight.fluid.FuelFillEntity;
import com.antaurora.apofirstlight.fluid.FuelOpening;
import com.antaurora.apofirstlight.inventory.grid.GridLayout;
import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import com.antaurora.apofirstlight.item.CreativeFuelBarrelItem;
import com.antaurora.apofirstlight.item.FuelCanItem;
import com.antaurora.apofirstlight.item.FuelNozzleItem;
import com.antaurora.apofirstlight.registry.AflEntities;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout.Box;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout.Pick;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout.Seat;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Halstead Tamarack T15 pickup (docs/vehicles/first_pickup_v0_design.md): a parked truck with the Pure Mesh body
 * (paint, wear, hood, doors, tailgate, filler door), collision, six seats, a 60 L diesel tank and the bed's cargo grid
 * (V0-a second step, section 8.2). No driving or physics yet.
 * <p>Picking: an entity's own box is square, so the 4.8 m truck is three boxes along its heading: this entity (the cab) and
 * two {@link Part}s (front, bed), as the ender dragon does (Forge PartEntity, ids following this entity's). Which part a
 * click works is not the box it hit but the part the look ray meets in the truck's own frame
 * ({@link PickupTruckLayout#pick}); the world prompt (client/WorldInteractionHint) asks {@link #actionFor} the same. A
 * creative player's hit removes the truck (test cleanup; the cargo drops); other damage does nothing.
 * <p>Collision ({@link VehicleColliders}): the layout's boxes turned to the heading; who rides it goes through.
 * <p>Seats: the passengers' seats are synced (one entity id per seat). Driver and passenger get in through an open door
 * (it shuts behind them) and out the same side (the door opens; it shuts again), else the other side, the bed, the roof.
 * <p>Frame: the model's front is -Z (rendered turned by 180 - yaw), so the truck's front points along its look direction.
 */
public final class PickupTruckEntity extends Entity implements VehicleColliders.Collider, FuelFillEntity {
    private static final EntityDataAccessor<Integer> PAINT = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT2 = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WEAR = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HOOD = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TAILGATE = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOOR_L = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOOR_R = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FILLER = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> CARGO = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.COMPOUND_TAG);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Integer>[] SEAT = new EntityDataAccessor[PickupTruckLayout.SEATS.length];

    static {
        for (int i = 0; i < SEAT.length; i++) SEAT[i] = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.INT);
    }

    /** Deep red, wear 0.45: the forest home's truck proposal (design doc section 2.1). */
    public static final int DEFAULT_PAINT = 0x7B1E1A;
    public static final float DEFAULT_WEAR = 0.45F;
    /** Opening angles (hood and tailgate: degrees about the model's +X; doors and the filler door: about Y, outward) and swing speed (degrees per tick). */
    public static final float HOOD_OPEN = 58.0F, TAILGATE_OPEN = 90.0F, DOOR_OPEN = 68.0F, FILLER_OPEN = 80.0F;
    private static final float SWING = 7.0F;
    /** Fuel tank (litres = mB, diesel only; design doc section 6). */
    public static final int TANK = 60;
    /** The bed's cargo grid: 9 cells along the bed (0.2 m), 7 across (0.216 m). */
    public static final GridLayout CARGO_LAYOUT = GridLayout.of(9, 7);
    /** Half the truck's length (with the open tailgate) and its height with the hood open (blocks), for render culling. */
    private static final double HALF_LENGTH = 2.9, HEIGHT = 2.2;
    /** Ticks before a door shuts behind someone getting in / out. */
    private static final int SHUT_IN = 12, SHUT_OUT = 24;
    /** How far a click / prompt looks along the view (blocks). */
    public static final double REACH = 4.5;

    private final Part front = new Part(this, "front", 1.8F, 1.3F, 1.60), bed = new Part(this, "bed", 1.8F, 1.2F, -1.45);
    private final Part[] parts = {front, bed};
    private float hood, hoodO, tailgate, tailgateO, doorL, doorLO, doorR, doorRO, filler, fillerO;
    private final FluidTank tank = new FluidTank(TANK, f -> f.getFluid().isSame(AflFluids.DIESEL.get())) {
        @Override
        protected void onContentsChanged() {
            entityData.set(FUEL, getFluidAmount());
        }
    };
    /** Server: the bed's cargo. Client: the last synced copy, for drawing. */
    private GridStorage cargo = new GridStorage(CARGO_LAYOUT, false);
    private int cargoVersion;
    /** Server: when each door (left, right) shuts by itself (game time, -1 none); who left which seat last (for the dismount spot). */
    private final long[] shutAt = {-1, -1};
    private int leftId = -1, leftSeat = -1;
    // collision cache (VehicleColliders)
    private List<AABB> boxes = List.of();
    private AABB bounds = new AABB(0, 0, 0, 0, 0, 0);
    private double boxX = Double.NaN, boxY, boxZ;
    private float boxYaw;
    private boolean boxTailgate;
    private boolean registered;

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
    public boolean fillerOpen() { return entityData.get(FILLER); }
    /** Litres of diesel in the tank (synced). */
    public int fuel() { return entityData.get(FUEL); }

    /** Main and second colour (0xRRGGBB; the second is the lower band under the side moulding, the same for one colour). */
    public void setPaint(int main, int second) {
        entityData.set(PAINT, main & 0xFFFFFF);
        entityData.set(PAINT2, second & 0xFFFFFF);
    }

    public void setWear(float wear) { entityData.set(WEAR, Mth.clamp(wear, 0.0F, 1.0F)); }
    public void setHoodOpen(boolean open) { entityData.set(HOOD, open); }
    public void setTailgateOpen(boolean open) { entityData.set(TAILGATE, open); }
    public void setFillerOpen(boolean open) { entityData.set(FILLER, open); }

    public void setDoorOpen(boolean right, boolean open) {
        entityData.set(right ? DOOR_R : DOOR_L, open);
        shutAt[right ? 1 : 0] = -1;
    }

    /** Server: litres in the tank (diesel), 0..TANK. */
    public void setFuel(int litres) {
        tank.setFluid(litres <= 0 ? FluidStack.EMPTY : new FluidStack(AflFluids.DIESEL.get(), Math.min(TANK, litres)));
        entityData.set(FUEL, tank.getFluidAmount());
    }

    /** Client: hood / tailgate angle this frame (degrees). */
    public float hoodAngle(float partialTick) { return Mth.lerp(partialTick, hoodO, hood); }
    public float tailgateAngle(float partialTick) { return Mth.lerp(partialTick, tailgateO, tailgate); }
    public float fillerAngle(float partialTick) { return Mth.lerp(partialTick, fillerO, filler); }
    /** Client: a door's opening angle this frame (degrees, outward). */
    public float doorAngle(boolean right, float partialTick) { return right ? Mth.lerp(partialTick, doorRO, doorR) : Mth.lerp(partialTick, doorLO, doorL); }

    @Override
    public void tick() {
        super.tick();
        hoodO = hood;
        tailgateO = tailgate;
        doorLO = doorL;
        doorRO = doorR;
        fillerO = filler;
        hood = approach(hood, hoodOpen() ? HOOD_OPEN : 0.0F, HOOD_OPEN);
        tailgate = approach(tailgate, tailgateOpen() ? TAILGATE_OPEN : 0.0F, TAILGATE_OPEN);
        doorL = approach(doorL, doorOpen(false) ? DOOR_OPEN : 0.0F, DOOR_OPEN);
        doorR = approach(doorR, doorOpen(true) ? DOOR_OPEN : 0.0F, DOOR_OPEN);
        filler = approach(filler, fillerOpen() ? FILLER_OPEN : 0.0F, FILLER_OPEN);
        placeParts();
        if (!level().isClientSide) {
            long now = level().getGameTime();
            for (int side = 0; side < 2; side++) {
                if (shutAt[side] >= 0 && now >= shutAt[side]) setDoorOpen(side == 1, false);
            }
            // a seat whose rider is gone (left another way, died) frees up
            for (int i = 0; i < SEAT.length; i++) {
                int id = entityData.get(SEAT[i]);
                if (id >= 0 && getPassengers().stream().noneMatch(p -> p.getId() == id)) entityData.set(SEAT[i], -1);
            }
        }
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

    /** A point of the truck's frame (forward, right, up) in the world. */
    public Vec3 point(double f, double r, double y) {
        return PickupTruckLayout.world(position(), getYRot(), f, r, y);
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

    // ---- collision (VehicleColliders) ----

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!registered) {
            VehicleColliders.add(level(), this);
            registered = true;
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        VehicleColliders.remove(level(), this);
        registered = false;
    }

    @Override
    public List<AABB> collisionBoxes() {
        refreshBoxes();
        return boxes;
    }

    @Override
    public AABB collisionBounds() {
        refreshBoxes();
        return bounds;
    }

    @Override
    public boolean passesThrough(Entity entity) {
        return entity == this || entity instanceof Part part && part.getParent() == this || entity.getRootVehicle() == this;
    }

    private void refreshBoxes() {
        boolean tail = tailgateOpen();
        if (getX() == boxX && getY() == boxY && getZ() == boxZ && getYRot() == boxYaw && tail == boxTailgate) return;
        boxX = getX();
        boxY = getY();
        boxZ = getZ();
        boxYaw = getYRot();
        boxTailgate = tail;
        List<AABB> out = new ArrayList<>();
        for (Box b : PickupTruckLayout.BODY) strip(b, out);
        strip(tail ? PickupTruckLayout.TAILGATE_DOWN : PickupTruckLayout.TAILGATE_SHUT, out);
        AABB all = out.get(0);
        for (AABB b : out) all = all.minmax(b);
        boxes = List.copyOf(out);
        bounds = all;
    }

    private void strip(Box b, List<AABB> out) {
        VehicleColliders.strips(boxX, boxZ, boxYaw, b.f0(), b.f1(), b.r0(), b.r1(), boxY + b.y0(), boxY + b.y1(), out);
    }

    // ---- what a look at the truck works ----

    /** What a click on the truck does now; the prompt says the same (client/WorldInteractionHint). */
    public enum Action {
        OPEN_HOOD("open"), CLOSE_HOOD("close"), OPEN_DOOR("open_door"), CLOSE_DOOR("close_door"), GET_IN("get_in"),
        OPEN_TAILGATE("lower"), CLOSE_TAILGATE("close"), OPEN_FILLER("open"), CLOSE_FILLER("close"), REFUEL("refuel"),
        STOP_POUR("stop"), SIT("sit"), CARGO("open");

        public final String hint;

        Action(String hint) { this.hint = hint; }

        /** The prompt's key: hint.apocalypse_firstlight.pickup_t15.* */
        public String key() { return "hint.apocalypse_firstlight.pickup_t15." + hint; }
    }

    /** An action and where its prompt sits (world). */
    public record Offer(Action action, Vec3 anchor, Pick pick) {}

    /** The part a player looks at (their eyes and view), or null. */
    @Nullable
    public Pick pick(Player player) {
        Vec3 eye = player.getEyePosition();
        return PickupTruckLayout.pick(PickupTruckLayout.local(position(), getYRot(), eye), PickupTruckLayout.localDir(getYRot(), player.getViewVector(1.0F)),
                REACH, hoodOpen(), doorOpen(false), doorOpen(true), tailgateOpen());
    }

    /** What a click by this player does, or null (also the prompt's test). */
    @Nullable
    public Offer actionFor(Player player) {
        if (player.isSpectator() || player.getRootVehicle() == this) return null;
        Pick pick = pick(player);
        if (pick == null) return null;
        ItemStack held = player.getMainHandItem();
        Action action = switch (pick.zone()) {
            case HOOD -> hoodOpen() ? Action.CLOSE_HOOD : Action.OPEN_HOOD;
            case DOOR_L, DOOR_R -> {
                boolean right = pick.zone() == Zone.DOOR_R;
                if (!doorOpen(right)) yield Action.OPEN_DOOR;
                yield player.isShiftKeyDown() || seated(right ? PickupTruckLayout.PASSENGER : PickupTruckLayout.DRIVER) ? Action.CLOSE_DOOR : Action.GET_IN;
            }
            case FILLER -> {
                if (FuelCanItem.isPouring(held)) yield Action.STOP_POUR;
                boolean fills = held.getItem() instanceof FuelNozzleItem || held.getItem() instanceof CreativeFuelBarrelItem
                        || held.getItem() instanceof FuelCanItem can && can.pours();
                yield fillerOpen() ? (fills ? Action.REFUEL : Action.CLOSE_FILLER) : Action.OPEN_FILLER;
            }
            case TAILGATE -> tailgateOpen() ? Action.CLOSE_TAILGATE : Action.OPEN_TAILGATE;
            case SEAT -> seated(pick.seat()) ? null : Action.SIT;
            case BED -> null;   // the cargo grid opens once the inventory side has an external grid (D, requested 2026-10-16)
        };
        return action == null ? null : new Offer(action, anchor(pick), pick);
    }

    /** Where a part's prompt sits: the hood latch, a door's handle, the filler, the tailgate handle, a seat. */
    private Vec3 anchor(Pick pick) {
        return switch (pick.zone()) {
            case HOOD -> point(2.24, 0, hoodOpen() ? 1.45 : 1.05);
            case DOOR_L -> point(-0.10, -0.90, 0.98);
            case DOOR_R -> point(-0.10, 0.90, 0.98);
            case FILLER -> point(-0.62, -0.90, 0.94);
            case TAILGATE -> tailgateOpen() ? point(-2.70, 0, 0.62) : point(-2.27, 0, 1.05);
            case SEAT -> { Seat s = PickupTruckLayout.SEATS[pick.seat()]; yield point(s.f(), s.r(), 0.95); }
            case BED -> point(-1.30, 0, 0.90);
        };
    }

    /** A click on a part (or on this entity, the cab). */
    InteractionResult interactPart(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getMainHandItem();
        // a nozzle in an opening: a click on anything takes it out (as FuelNozzleItem)
        if (held.getItem() instanceof FuelNozzleItem) {
            FuelDispenserBlockEntity dispenser = FuelNozzleItem.dispenser(held, level());
            FuelDispenserBlock.Nozzle nozzle = FuelNozzleItem.nozzle(held);
            if (dispenser != null && nozzle != null && dispenser.inserted(nozzle) != null) {
                if (player instanceof ServerPlayer server) dispenser.pullOut(server, nozzle);
                return InteractionResult.CONSUME;
            }
        }
        Offer offer = actionFor(player);
        if (offer == null) return InteractionResult.PASS;
        if (level().isClientSide) return offer.action() == Action.REFUEL || offer.action() == Action.STOP_POUR ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
        switch (offer.action()) {
            case OPEN_HOOD, CLOSE_HOOD -> setHoodOpen(!hoodOpen());
            case OPEN_DOOR, CLOSE_DOOR -> setDoorOpen(offer.pick().zone() == Zone.DOOR_R, offer.action() == Action.OPEN_DOOR);
            case GET_IN -> {
                boolean right = offer.pick().zone() == Zone.DOOR_R;
                if (sit(player, right ? PickupTruckLayout.PASSENGER : PickupTruckLayout.DRIVER)) shutAt[right ? 1 : 0] = level().getGameTime() + SHUT_IN;
            }
            case OPEN_FILLER, CLOSE_FILLER -> setFillerOpen(!fillerOpen());
            case REFUEL, STOP_POUR -> refuel(player, held);
            case OPEN_TAILGATE, CLOSE_TAILGATE -> setTailgateOpen(!tailgateOpen());
            case SIT -> sit(player, offer.pick().seat());
            case CARGO -> {}
        }
        return offer.action() == Action.REFUEL || offer.action() == Action.STOP_POUR ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
    }

    /** Server: what the held item does at the open filler: a can pours (or stops), a nozzle goes in, a creative barrel fills up. */
    private void refuel(Player player, ItemStack held) {
        if (held.getItem() instanceof FuelCanItem) FuelCanItem.startPourInto(player, held, this);
        else if (held.getItem() instanceof CreativeFuelBarrelItem barrel) barrel.pourInto(held, level(), player, tank, fuelRefusal(), fuelOpening());
        else if (held.getItem() instanceof FuelNozzleItem && player instanceof ServerPlayer server) {
            FuelDispenserBlockEntity dispenser = FuelNozzleItem.dispenser(held, level());
            FuelDispenserBlock.Nozzle nozzle = FuelNozzleItem.nozzle(held);
            if (dispenser != null && nozzle != null) dispenser.insert(server, nozzle, FuelOpening.entity(this));
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return interactPart(player, hand);
    }

    // ---- fuel (FuelFillEntity) ----

    @Override
    public boolean fuelOpen() { return fillerOpen() && !isRemoved(); }

    @Override
    public Vec3 fuelOpening() {
        double[] n = PickupTruckLayout.FILL_NECK;
        return point(n[0], n[1], n[2]);
    }

    @Override
    @Nullable
    public IFluidHandler fuelHandler() { return fillerOpen() ? tank : null; }

    @Override
    public String fuelRefusal() { return "diesel_only"; }

    // ---- seats ----

    /** Whether someone sits on this seat (synced). */
    public boolean seated(int seat) { return entityData.get(SEAT[seat]) >= 0; }

    /** The seat this entity rides on, or -1. */
    public int seatOf(Entity passenger) {
        for (int i = 0; i < SEAT.length; i++) if (entityData.get(SEAT[i]) == passenger.getId()) return i;
        return -1;
    }

    /** Server: seats the player on a free seat. */
    private boolean sit(Player player, int seat) {
        if (seated(seat) || player.isPassenger() || player.isShiftKeyDown()) return false;
        entityData.set(SEAT[seat], player.getId());
        if (player.startRiding(this)) return true;
        entityData.set(SEAT[seat], -1);
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().size() < SEAT.length; }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        int seat = seatOf(passenger);
        if (seat < 0) return;   // its seat not synced yet: it stays where it is
        Seat s = PickupTruckLayout.SEATS[seat];
        Vec3 p = point(s.f(), s.r(), PickupTruckLayout.riderY(s, passenger.getEyeHeight()));
        move.accept(passenger, p.x, p.y, p.z);
        clampRotation(passenger, s);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        int seat = seatOf(passenger);
        if (seat >= 0) clampRotation(passenger, PickupTruckLayout.SEATS[seat]);
    }

    /** The body faces the seat's way, the head within its look range (as Boat#clampRotation). */
    private void clampRotation(Entity passenger, Seat seat) {
        float facing = getYRot() + seat.yaw();
        if (passenger instanceof LivingEntity living) living.setYBodyRot(facing);
        float f = Mth.wrapDegrees(passenger.getYRot() - facing), g = Mth.clamp(f, -seat.look(), seat.look());
        passenger.yRotO += g - f;
        passenger.setYRot(passenger.getYRot() + g - f);
        passenger.setYHeadRot(passenger.getYRot());
    }

    @Override
    protected void removePassenger(Entity passenger) {
        int seat = seatOf(passenger);
        super.removePassenger(passenger);
        if (level().isClientSide || seat < 0) return;
        entityData.set(SEAT[seat], -1);
        leftId = passenger.getId();
        leftSeat = seat;
    }

    /**
     * Out of the cab: this side's door opens and they stand outside it (it shuts again), else the other side's, else in
     * the bed, else on the roof. Off a bed seat: on the bed floor beside it, else over the side.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        int seat = passenger.getId() == leftId ? leftSeat : -1;
        Seat s = seat >= 0 ? PickupTruckLayout.SEATS[seat] : PickupTruckLayout.SEATS[PickupTruckLayout.DRIVER];
        double side = s.right() ? 1 : -1;
        if (s.cab()) {
            for (double sd : new double[]{side, -side}) {
                Vec3 out = ground(passenger, point(PickupTruckLayout.EXIT_F, sd * PickupTruckLayout.EXIT_R, 0));
                if (out != null) {
                    int door = sd > 0 ? 1 : 0;
                    setDoorOpen(sd > 0, true);
                    shutAt[door] = level().getGameTime() + SHUT_OUT;
                    return out;
                }
            }
            Vec3 bedFloor = onTruck(passenger, point(-1.30, 0, 0.775));
            if (bedFloor != null) return bedFloor;
            return point(0.10, 0, 1.75);   // the roof
        }
        Vec3 floor = onTruck(passenger, point(s.f(), side * 0.25, 0.775));
        if (floor != null) return floor;
        Vec3 out = ground(passenger, point(s.f() - 0.5, side * 1.40, 0));
        return out != null ? out : point(s.f(), side * 0.25, 0.775);
    }

    /** A standing spot on the ground near `at` (its block floor a little above or below the truck's), or null. */
    @Nullable
    private Vec3 ground(LivingEntity passenger, Vec3 at) {
        BlockPos base = BlockPos.containing(at.x, at.y + 0.5, at.z);
        for (BlockPos pos : new BlockPos[]{base, base.below(), base.above()}) {
            double floor = level().getBlockFloorHeight(pos);
            if (!DismountHelper.isBlockFloorValid(floor)) continue;
            Vec3 spot = new Vec3(at.x, pos.getY() + floor, at.z);
            if (free(passenger, spot)) return spot;
        }
        return null;
    }

    /** A standing spot on the truck itself (the bed floor), or null when something is in the way. */
    @Nullable
    private Vec3 onTruck(LivingEntity passenger, Vec3 spot) {
        return free(passenger, spot) ? spot : null;
    }

    private boolean free(LivingEntity passenger, Vec3 spot) {
        for (Pose pose : passenger.getDismountPoses()) {
            AABB box = passenger.getLocalBoundsForPose(pose).move(spot);
            if (DismountHelper.canDismountTo(level(), passenger, box) && !VehicleColliders.blocked(level(), box.deflate(1.0E-4), passenger)) return true;
        }
        return false;
    }

    @Override
    public boolean shouldRiderSit() { return true; }

    // ---- cargo (the bed's grid) ----

    /** The bed's cargo (server: the live grid; client: the last synced copy). */
    public GridStorage cargo() { return cargo; }

    /** Client: changes each time the synced cargo does (the renderer rebuilds its items). */
    public int cargoVersion() { return cargoVersion; }

    /** Server: after the cargo changed: synced to clients (drawn in the bed). */
    public void cargoChanged() {
        cargo.prune();
        CompoundTag tag = new CompoundTag();
        tag.put("Grid", cargo.save());
        entityData.set(CARGO, tag);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == CARGO && level().isClientSide) {
            cargo = GridStorage.load(CARGO_LAYOUT, entityData.get(CARGO).getList("Grid", Tag.TAG_COMPOUND), true);
            cargoVersion++;
        }
    }

    // ---- damage ----

    /** A creative player's hit removes the truck (its cargo drops); nothing else harms it in V0. */
    boolean hurtFrom(DamageSource source) {
        if (isRemoved() || !source.isCreativePlayer()) return false;
        if (!level().isClientSide) {
            for (ItemStack stack : cargo.stacks()) if (!stack.isEmpty()) spawnAtLocation(stack.copy(), 1.0F);
            cargo = new GridStorage(CARGO_LAYOUT, false);
            discard();
        }
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
        entityData.define(FILLER, false);
        entityData.define(FUEL, 0);
        entityData.define(CARGO, new CompoundTag());
        for (EntityDataAccessor<Integer> seat : SEAT) entityData.define(seat, -1);
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
        setFillerOpen(tag.getBoolean("FillerOpen"));
        setFuel(tag.getInt("Fuel"));
        cargo = GridStorage.load(CARGO_LAYOUT, tag.getList("Cargo", Tag.TAG_COMPOUND), false);
        cargoChanged();
        // a reloaded truck shows its parts where they were left, without swinging
        hood = hoodO = hoodOpen() ? HOOD_OPEN : 0.0F;
        tailgate = tailgateO = tailgateOpen() ? TAILGATE_OPEN : 0.0F;
        doorL = doorLO = doorOpen(false) ? DOOR_OPEN : 0.0F;
        doorR = doorRO = doorOpen(true) ? DOOR_OPEN : 0.0F;
        filler = fillerO = fillerOpen() ? FILLER_OPEN : 0.0F;
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
        tag.putBoolean("FillerOpen", fillerOpen());
        tag.putInt("Fuel", tank.getFluidAmount());
        ListTag grid = cargo.save();
        if (!grid.isEmpty()) tag.put("Cargo", grid);
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
        public InteractionResult interact(Player player, InteractionHand hand) {
            return getParent().interactPart(player, hand);
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
