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
import com.antaurora.apofirstlight.skeleton.seat.RiderSeat;
import com.antaurora.apofirstlight.skeleton.seat.SeatPose;
import com.antaurora.apofirstlight.skeleton.seat.SeatedRiderHost;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout.Box;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout.Pick;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout.Seat;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout.Zone;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.vehicle.physics.LevelVehicleWorld;
import com.antaurora.apofirstlight.vehicle.physics.Drivetrain;
import com.antaurora.apofirstlight.vehicle.physics.VehicleInput;
import com.antaurora.apofirstlight.vehicle.physics.VehiclePhysics;
import com.antaurora.apofirstlight.vehicle.physics.VehicleSpec;
import com.antaurora.apofirstlight.vehicle.physics.VehicleState;
import com.antaurora.apofirstlight.vehicle.physics.VehicleWorld;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The Halstead Tamarack T15 pickup (docs/vehicles/first_pickup_v0_design.md): a parked truck with the Pure Mesh body
 * (paint, wear, hood, doors, tailgate, filler door), collision, six seats, a 60 L diesel tank and the bed's cargo grid
 * (V0-a second step, section 8.2), driven by the vehicle physics (V0-b package 1, section 3; vehicle/physics): see
 * "Driving" below.
 * <p>Picking: an entity's own box is square, so the 4.8 m truck is three boxes along its heading: this entity (the cab) and
 * two {@link Part}s (front, bed), as the ender dragon does (Forge PartEntity, ids following this entity's). Which part a
 * click works is not the box it hit but the part the look ray meets in the truck's own frame
 * ({@link PickupTruckLayout#pick}); the world prompt (client/WorldInteractionHint) asks {@link #actionFor} the same. A
 * creative player's sneaking hit removes the truck (test cleanup; the cargo drops); a punch only sounds on the metal;
 * other damage does nothing.
 * <p>Collision ({@link VehicleColliders}): the layout's boxes turned to the heading; who rides it goes through.
 * <p>Seats: the passengers' seats are synced (one entity id per seat). Driver and passenger get in through an open door
 * and get out only through their own door opened first (user 2026-10-21: a stray sneak no longer drops them out; the
 * door stays open), else the other side, the bed, the roof.
 * The seats for the player skeleton ({@link SeatedRiderHost}, docs/dev/seated_rider_v1_plan.md): the full orientation,
 * the hands on the steering wheel turning with it, the door's points swinging with the door.
 * <p>Frame: the model's front is -Z (rendered turned by 180 - yaw), so the truck's front points along its look direction.
 * <p>Driving (V0-b package 1): the driver's seat controls it ({@link #getControllingPassenger}); the driver's client runs
 * {@link VehiclePhysics} with its keys and sends the state each tick, the server checks and passes it on (VehiclePackets),
 * the other clients ease to it. Without a driver the server runs it parked (handbrake) until it rests, then lets it sleep
 * until the ground under a wheel changes or it is moved. The entity position is the model origin, its yaw the heading;
 * the full orientation (pitch and roll too) is kept beside it for drawing, seats and anchors. Collision boxes stay
 * yaw-only. Who stands on it is carried along (the server moves mobs and items, each client its own player); who is in
 * the way of a moving truck is pushed aside.
 */
public final class PickupTruckEntity extends Entity implements VehicleColliders.Collider, FuelFillEntity, SeatedRiderHost {
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
    /**
     * The lamps (design doc 8.10), the horn (8.11), the turn signals and hazard lamps (8.12): LAMP_* bits, set on the
     * server (the switches by the driver, the brake from the driver's back key, the horn while the driver holds its key).
     */
    private static final EntityDataAccessor<Byte> LAMPS = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BYTE);
    public static final int LAMP_HEAD = 1, LAMP_HIGH = 2, LAMP_BRAKE = 4, HORN = 8, TURN_L = 16, TURN_R = 32, HAZARD = 64;
    /**
     * VehiclePackets.Lamps actions: the light key (a tap: lights on / off; a hold: the high beam), the horn pressed / let go,
     * the left / right turn signal (on, or off when it already blinks) and the hazard switch.
     */
    public static final int SWITCH_LIGHTS = 0, SWITCH_HIGH = 1, HORN_ON = 2, HORN_OFF = 3, SIGNAL_LEFT = 4, SIGNAL_RIGHT = 5, SWITCH_HAZARD = 6,
            SWITCH_WIPER = 7, SWITCH_CLIMATE = 8, INSERT_KEY = 9, RELEASE_WHEEL = 10, KEY_HAND_ON = 11, KEY_HAND_OFF = 12,
            KEY_HAND_PULL = 13, TAKE_KEY_OUT = 14;
    /** The driver's key hand ({@link #KEY_HAND}): resting, reaching (and turning, or bringing the key in), pulling the key out. */
    public static final byte HAND_OFF = 0, HAND_REACH = 1, HAND_PULL = 2;
    /** The wiper switch (WiperSystem.OFF .. HIGH; the stalk right of the column), set on the server from the driver's key. */
    private static final EntityDataAccessor<Byte> WIPER = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BYTE);
    /** The heater panel's switch (CabClimate.OFF, HEAT, COOL), set on the server from the driver's key. */
    private static final EntityDataAccessor<Byte> CLIMATE = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BYTE);
    /**
     * The key's place (CabStowage.KeySpot ordinal) and the open cab compartments (CabStowage.Compartment bits: visors,
     * glovebox, ashtray), set on the server (docs/vehicles/t15_hotwire_v1_design.md section 9; 2026-10-21).
     */
    private static final EntityDataAccessor<Byte> KEY = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> STOWAGE = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BYTE);
    /**
     * The driver's hands on the steering wheel (user 2026-10-21): seated, the hands rest in the lap and the driver can use
     * what they hold; a click on the wheel takes hold of it (only then the truck drives: pedals, steering, gears), a click
     * anywhere lets go. Set on the server; not saved (a driver sits down with the hands free).
     */
    private static final EntityDataAccessor<Boolean> WHEEL = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * The driver's right hand on the ignition key (user 2026-10-21: holding G takes the key out, puts it in and turns it;
     * a click on the keyhole puts it in or pulls it out, client/vehicle/VehicleDriving): HAND_OFF, HAND_REACH or HAND_PULL,
     * set on the server from the driver's KEY_HAND_ON / KEY_HAND_PULL / KEY_HAND_OFF so others see the reach; the driver's
     * own client goes by its own state ({@link #localKeyHand}). Not saved.
     */
    private static final EntityDataAccessor<Byte> KEY_HAND = SynchedEntityData.defineId(PickupTruckEntity.class, EntityDataSerializers.BYTE);
    /** Flasher: one flash cycle (ticks; 90 a minute, inside the US 60-120) and its lit share. */
    public static final float BLINK_PERIOD = 20 * 60 / 90.0F, BLINK_ON = 0.5F;
    /**
     * Infected hearing (design doc 8.11; the sounds carry as far, client/vehicle/TruckSounds and sounds.json): the engine
     * idling and revving (every NOISE_INTERVAL ticks while it runs), the horn (every HORN_INTERVAL ticks while it sounds),
     * a light and a heavy crash, a door, the hood and the tailgate, the filler cap (blocks).
     */
    public static final double ENGINE_NOISE = 32, ENGINE_LOUD_NOISE = 48, HORN_NOISE = 64, CRASH_NOISE = 24, CRASH_HEAVY_NOISE = 48,
            DOOR_NOISE = 8, TAILGATE_NOISE = 10, CAP_NOISE = 3;
    private static final int NOISE_INTERVAL = 40, HORN_INTERVAL = 10;
    /** A loss of speed in one tick this big is a crash (m/s; braking takes at most about 0.4 m/s a tick), a heavy one past the second. */
    public static final double CRASH_DROP = 2.5, CRASH_HEAVY_DROP = 6.0;
    /** Server: the speed of the driver's last accepted state (m/s). */
    private double acceptedSpeed;
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
    /**
     * A cab rider shuts its own open door (user 2026-10-16: doors no longer shut by themselves) looking out that way: the
     * view's sideways share toward the door at least this.
     */
    private static final double CLOSE_LOOK = 0.35;
    /** Faster than this (m/s, about 2 km/h) nobody gets out (client/vehicle/VehicleDriving keeps the sneak key from it). */
    public static final double GET_OUT_SPEED = 0.55;
    /** How far a click / prompt looks along the view (blocks). */
    public static final double REACH = 4.5;

    private final Part front = new Part(this, "front", 1.8F, 1.3F, 1.60), bed = new Part(this, "bed", 1.8F, 1.2F, -1.45);
    private final Part[] parts = {front, bed};
    private float hood, hoodO, tailgate, tailgateO, doorL, doorLO, doorR, doorRO, filler, fillerO;
    /** Client: the visors, glovebox lid (deg), ashtray drawer (0..1), key turn, turn stalk and light knob (deg) as drawn. */
    private float visorL, visorLO, visorR, visorRO, glovebox, gloveboxO, ashtray, ashtrayO, keyTurn, keyTurnO, stalk, stalkO, knob, knobO;
    /** The driver's hands on their way between the lap (0) and the wheel (1). */
    private float wheelHold, wheelHoldO;
    /** Ticks the hands take from the lap to the wheel or back (and the right hand to the key and back). */
    private static final float WHEEL_REACH_TICKS = 7.0F;
    /** Client: the right hand on its way to the ignition key (0) and on it (1); ticks since it went for it (-1 none). */
    private float keyReach, keyReachO;
    private int keyHandTicks = -1;
    private boolean keyHandWas, keyInserting, keyPulling;
    /** Client: the local driver's own key hand (ahead of the synced KEY_HAND), -1 for everyone else. */
    private int localKeyHand = -1;
    /**
     * The key on its way in (model frame, the key bone's origin): from beside the right thigh (where the hand took it from
     * the pocket) to {@link #KEY_APPROACH} in front of the cylinder by {@link #KEY_PATH_OUT} ticks, then in by
     * {@link #KEY_PATH_IN}. The hand holds its bow {@link #KEY_GRIP_OUT} out along the key (the fist's end on the bow).
     */
    private static final double[] KEY_POCKET = {-0.27, 0.95, -0.24};
    private static final double KEY_APPROACH = 0.07, KEY_GRIP_OUT = 0.085;
    public static final int KEY_PATH_OUT = 8, KEY_PATH_IN = 12;
    /**
     * The key on its way out (a click on the keyhole, or getting out with the engine off; user 2026-10-21): the hand is on
     * the bow at {@link #KEY_PULL_AT}, draws it clear of the cylinder by {@link #KEY_PULL_OUT} and has it by the right
     * thigh (the pocket) at {@link #KEY_PULL_END}. The way in, backwards.
     */
    public static final int KEY_PULL_AT = 8, KEY_PULL_OUT = 12, KEY_PULL_END = 20;
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
    private int leftId = -1, leftSeat = -1;
    /**
     * Server: a player who just got out, and where (sent to its client the tick after: the server alone moves it there,
     * and in creative the client's own place, still on the seat inside the cab, would win: user 2026-10-16, out through the roof).
     */
    private int exitId = -1;
    private Vec3 exitSpot = Vec3.ZERO;
    /** Client: the driver's hands on the wheel (push-pull steering). */
    private final SteeringHands hands = new SteeringHands();
    private int handsDriver = -1;
    /** Client: the gear lever and the right hand on it (drawn for everyone; the hand with the seated driver's skeleton). */
    private final ShiftLever lever = new ShiftLever();
    /** Client: the transfer case lever and the right hand on it (as the gear lever). */
    private final TransferLever transferLever = new TransferLever();
    private double handsTime = Double.NaN;
    /** Riders moving over (MOVE_OVER): passenger id -> {the seat it came from, the tick it started}; the seat each was last on. */
    private final Map<Integer, int[]> slides = new HashMap<>();
    private final Map<Integer, Integer> lastSeat = new HashMap<>();
    /** Moving over takes this long (ticks). */
    private static final int SLIDE_TICKS = 14;
    // collision cache (VehicleColliders)
    private List<AABB> boxes = List.of();
    private AABB bounds = new AABB(0, 0, 0, 0, 0, 0);
    private double boxX = Double.NaN, boxY, boxZ;
    private float boxYaw;
    private boolean boxTailgate;
    private boolean registered;
    // ---- driving (V0-b package 1) ----
    /** Client: the local driver's keys (client/vehicle/VehicleDriving); a server never asks. */
    public static volatile Supplier<VehicleInput> clientInput = () -> VehicleInput.PARKED;
    /** Client: the local player (carried and pushed by its own client). */
    public static volatile Supplier<Player> localPlayer = () -> null;
    /** Watchers ease to a new state over this many ticks; a sleeping truck checks its ground this often (server). */
    private static final int EASE_TICKS = 3, SLEEP_CHECK = 10;
    /** A driver's state that moves the truck further than this in a tick (blocks) or faster (m/s) is refused. */
    private static final double MAX_STEP = 8.0, MAX_SPEED = 45.0;
    private VehiclePhysics physics;
    private LevelVehicleWorld world;
    /** Orientation (model frame to world) this tick and the last; until oriented, it follows the yaw. */
    private final Quaterniond orient = new Quaterniond(), orientO = new Quaterniond();
    private boolean oriented;
    /** Wheels as drawn: travel (blocks, + up), turn (rad), turn rate (rad/s); the centre steer angle (rad). */
    private final float[] travel = new float[4], travelO = new float[4], spin = new float[4], spinO = new float[4], spinRate = new float[4];
    private float steer, steerO;
    /** Where the physics last put it (anything else moving it starts the physics again from there). */
    private double placedX = Double.NaN, placedY, placedZ;
    private float placedYaw;
    /** Watchers: the state eased to, ticks left. */
    private VehicleState target;
    private int easeLeft;
    private boolean synced, wasLocal;
    /** Where it was at the end of the last tick and its boxes then (carrying); the yaw turned this tick (riders turn along). */
    private double carryX = Double.NaN, carryY, carryZ;
    private float carryYaw, turned;
    private List<AABB> carryBoxes = List.of();
    /** Where plants under it were last pressed down (server). */
    private double flatX = Double.NaN, flatZ;
    private float flatYaw;
    // ---- drivetrain (V0-b package 2) ----
    /** The drivetrain as saved, for the physics once it exists: gear, 2H / 4H, coolant (the engine is off after loading). */
    private int savedGear;
    private boolean savedFour, savedLow;
    private float savedCoolant = 20;
    /** Server: fuel burnt and not yet taken from the tank (litres; the tank counts whole litres). */
    private double fuelBurnt;
    /** Gauges as drawn: engine speed (rpm), road speed from the rear wheels (km/h), fuel (0..1), coolant (C); last tick and now. */
    private float rpm, rpmO, speedo, speedoO, fuelShown = -1, fuelShownO, coolantShown = 40, coolantShownO = 40;

    public PickupTruckEntity(EntityType<? extends PickupTruckEntity> type, Level level) {
        super(type, level);
        // Forge (MC-158205, as the ender dragon): the parts' ids follow this entity's, so client and server agree on them
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        blocksBuilding = true;
        noPhysics = true;   // the vehicle physics moves it, not Entity#move
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
    public void setHoodOpen(boolean open) {
        if (open != hoodOpen()) partNoise(point(2.0, 0, 1.0), TAILGATE_NOISE);
        entityData.set(HOOD, open);
    }

    public void setTailgateOpen(boolean open) {
        if (open != tailgateOpen()) partNoise(point(-2.3, 0, 0.8), TAILGATE_NOISE);
        entityData.set(TAILGATE, open);
    }

    public void setFillerOpen(boolean open) {
        if (open != fillerOpen()) partNoise(point(-1.6, -0.8, 0.9), CAP_NOISE);
        entityData.set(FILLER, open);
    }

    public void setDoorOpen(boolean right, boolean open) {
        if (open != doorOpen(right)) partNoise(point(0.3, right ? 0.8 : -0.8, 1.0), DOOR_NOISE);
        entityData.set(right ? DOOR_R : DOOR_L, open);
    }

    /** Server, in play (not while loading): a part of the truck heard by the infected (design doc 8.11). */
    private void partNoise(Vec3 at, double radius) {
        if (tickCount > 0 && level() instanceof net.minecraft.server.level.ServerLevel server) noise(server, at, radius);
    }

    private void noise(net.minecraft.server.level.ServerLevel server, Vec3 at, double radius) {
        com.antaurora.apofirstlight.noise.NoiseSystem.emit(new com.antaurora.apofirstlight.noise.NoiseEvent(getControllingPassenger(), at,
                com.antaurora.apofirstlight.noise.NoiseType.MACHINE, server.getGameTime(), com.antaurora.apofirstlight.registry.AflEntities.PICKUP_T15.getId(),
                radius), server);
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

    // ---- key and cab compartments (docs/vehicles/t15_hotwire_v1_design.md section 9; CabStowage) ----

    /** Where the truck's key is (synced). */
    public CabStowage.KeySpot keySpot() { return CabStowage.KeySpot.of(entityData.get(KEY)); }

    /** Server: puts the truck's key there (ELSEWHERE: not in the truck). */
    public void setKeySpot(CabStowage.KeySpot spot) { entityData.set(KEY, (byte) spot.ordinal()); }

    /** The key is in the ignition: the engine starts with the ignition key (G). */
    public boolean keyInIgnition() { return keySpot() == CabStowage.KeySpot.IGNITION; }

    /** The driver holds the steering wheel (synced): only then the truck drives. */
    public boolean wheelHeld() { return entityData.get(WHEEL); }

    /** Server: the driver takes hold of the wheel or lets go. */
    public void setWheelHeld(boolean held) { entityData.set(WHEEL, held); }

    /** Client, this frame: the driver's hands from the lap (0) to the wheel (1), eased. */
    public float wheelHold(float partialTick) {
        float t = Mth.lerp(partialTick, wheelHoldO, wheelHold);
        return t * t * (3 - 2 * t);
    }

    /** The driver's right hand is on (or going for) the ignition key: the local driver's own state, else the synced one. */
    public boolean keyHand() {
        return keyHandMode() != HAND_OFF;
    }

    /** HAND_OFF, HAND_REACH or HAND_PULL: the local driver's own state, else the synced one. */
    public int keyHandMode() {
        return localKeyHand >= 0 ? localKeyHand : entityData.get(KEY_HAND);
    }

    /** Client, the local driver (VehicleDriving): what its hand does (HAND_OFF, HAND_REACH, HAND_PULL). */
    public void setLocalKeyHand(int mode) { localKeyHand = mode; }

    /** Client, this frame: the right hand from where it was (0) to the key (1), eased. */
    public float keyReach(float partialTick) {
        float t = Mth.lerp(partialTick, keyReachO, keyReach);
        return t * t * (3 - 2 * t);
    }

    /** Client: ticks since the hand went for the key (-1: it is not on its way or there), and whether it brings the key. */
    public int keyHandTicks() { return keyHandTicks; }

    public boolean keyInserting() { return keyInserting; }

    public boolean keyPulling() { return keyPulling; }

    @Override
    public boolean handBusy(Entity rider) {
        return seatOf(rider) == PickupTruckLayout.DRIVER && (keyHand() || keyReach > 0);
    }

    /** Client, this frame: the key bone's origin (model frame) on its way in from the pocket or out to it, or the cylinder's. */
    private double[] keyAt(float partialTick) {
        float[] k = CabStowage.KEY_PIVOT;
        if (!keyInserting && !keyPulling || keyHandTicks < 0) return new double[]{k[0], k[1], k[2]};
        double t = keyHandTicks + (keyHand() ? partialTick : 0);
        double[] approach = {k[0], k[1], k[2] + KEY_APPROACH};
        if (keyPulling) {
            if (t <= KEY_PULL_AT) return new double[]{k[0], k[1], k[2]};
            if (t <= KEY_PULL_OUT) return lerp(new double[]{k[0], k[1], k[2]}, approach, SeatPose.smooth((float) ((t - KEY_PULL_AT) / (KEY_PULL_OUT - KEY_PULL_AT))));
            return lerp(approach, KEY_POCKET, SeatPose.smooth((float) Math.min(1, (t - KEY_PULL_OUT) / (KEY_PULL_END - KEY_PULL_OUT))));
        }
        if (t >= KEY_PATH_IN) return new double[]{k[0], k[1], k[2]};
        if (t >= KEY_PATH_OUT) return lerp(approach, new double[]{k[0], k[1], k[2]}, SeatPose.smooth((float) ((t - KEY_PATH_OUT) / (KEY_PATH_IN - KEY_PATH_OUT))));
        return lerp(KEY_POCKET, approach, SeatPose.smooth((float) (t / KEY_PATH_OUT)));
    }

    private static double[] lerp(double[] a, double[] b, double w) {
        return new double[]{a[0] + (b[0] - a[0]) * w, a[1] + (b[1] - a[1]) * w, a[2] + (b[2] - a[2]) * w};
    }

    /**
     * Client, this frame: the key in the driver's hand on its way in or out (the key bone's offset from the cylinder,
     * model frame), or null: not carried (in the cylinder, elsewhere, the hand not bringing it or not there yet; pulled
     * out, it is in the pocket once the hand lets go).
     */
    @Nullable
    public float[] keyCarry(float partialTick) {
        boolean carried = keyPulling ? keyHandTicks >= KEY_PULL_AT : keyInserting && !keyInIgnition() && keyReach(partialTick) >= 0.5F;
        if (!carried || !keyHand() || keyHandTicks < 0) return null;
        double[] at = keyAt(partialTick);
        float[] k = CabStowage.KEY_PIVOT;
        return new float[]{(float) (at[0] - k[0]), (float) (at[1] - k[1]), (float) (at[2] - k[2])};
    }

    public boolean compartmentOpen(CabStowage.Compartment c) { return (entityData.get(STOWAGE) & c.bit()) != 0; }

    public void setCompartmentOpen(CabStowage.Compartment c, boolean open) {
        int bits = entityData.get(STOWAGE);
        entityData.set(STOWAGE, (byte) (open ? bits | c.bit() : bits & ~c.bit()));
    }

    /** Client, this frame: a visor's turn down (deg), the glovebox lid's (deg), the ashtray drawer out (0..1). */
    public float visorAngle(boolean right, float partialTick) { return right ? Mth.lerp(partialTick, visorRO, visorR) : Mth.lerp(partialTick, visorLO, visorL); }
    public float gloveboxAngle(float partialTick) { return Mth.lerp(partialTick, gloveboxO, glovebox); }
    public float ashtrayOut(float partialTick) { return Mth.lerp(partialTick, ashtrayO, ashtray); }
    /** Client, this frame: the key's turn in the cylinder, the turn stalk's and the light knob's (deg; CabStowage). */
    public float keyTurn(float partialTick) { return Mth.lerp(partialTick, keyTurnO, keyTurn); }
    public float stalkTurn(float partialTick) { return Mth.lerp(partialTick, stalkO, stalk); }
    public float knobTurn(float partialTick) { return Mth.lerp(partialTick, knobO, knob); }

    /** Each tick: the compartments, the key in the cylinder (OFF, ON with the electrics, START cranking), the stalk and knob ease to their settings. */
    private void stowageTick() {
        visorLO = visorL;
        visorRO = visorR;
        gloveboxO = glovebox;
        ashtrayO = ashtray;
        keyTurnO = keyTurn;
        wheelHoldO = wheelHold;
        wheelHold += Mth.clamp((wheelHeld() ? 1 : 0) - wheelHold, -1 / WHEEL_REACH_TICKS, 1 / WHEEL_REACH_TICKS);
        // the right hand to the key: a new reach starts its path (bringing the key when it is not in yet)
        keyReachO = keyReach;
        boolean hand = keyHand();
        if (hand && !keyHandWas) {
            keyHandTicks = 0;
            keyPulling = keyHandMode() == HAND_PULL;
            keyInserting = !keyPulling && !keyInIgnition();
        } else if (hand) {
            keyHandTicks++;
        } else if (keyReach <= 0) {
            keyHandTicks = -1;
        }
        keyHandWas = hand;
        keyReach += Mth.clamp((hand ? 1 : 0) - keyReach, -1 / WHEEL_REACH_TICKS, 1 / WHEEL_REACH_TICKS);
        stalkO = stalk;
        knobO = knob;
        visorL = approach(visorL, compartmentOpen(CabStowage.Compartment.VISOR_L) ? CabStowage.VISOR_OPEN : 0, CabStowage.VISOR_OPEN);
        visorR = approach(visorR, compartmentOpen(CabStowage.Compartment.VISOR_R) ? CabStowage.VISOR_OPEN : 0, CabStowage.VISOR_OPEN);
        glovebox = approach(glovebox, compartmentOpen(CabStowage.Compartment.GLOVEBOX) ? CabStowage.GLOVEBOX_OPEN : 0, CabStowage.GLOVEBOX_OPEN);
        ashtray += Mth.clamp((compartmentOpen(CabStowage.Compartment.ASHTRAY) ? 1 : 0) - ashtray, -0.25F, 0.25F);
        Drivetrain d = drivetrain();
        float key = d == null || !keyInIgnition() ? 0 : d.ignition == Drivetrain.CRANK ? CabStowage.KEY_START
                : d.running || d.ignition == Drivetrain.PREHEAT ? CabStowage.KEY_ON : 0;
        keyTurn += Mth.clamp(key - keyTurn, -20, 20);
        stalk += Mth.clamp(turnSignal() * CabStowage.STALK_TURN - stalk, -4, 4);
        knob += Mth.clamp((headlights() ? CabStowage.KNOB_ON : 0) - knob, -20, 20);
    }

    /** Server: the key goes to the player (the grids, in Creative the inventory); false when there is no room. */
    private boolean giveKey(ServerPlayer player) {
        if (!VehicleKeyItem.give(player, VehicleKeyItem.forVehicle(getUUID(), "pickup_t15"))) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hint.apocalypse_firstlight.pickup_t15.key_no_room"), true);
            return false;
        }
        setKeySpot(CabStowage.KeySpot.ELSEWHERE);
        cabSound(point(0.55, -0.20, 0.98), com.antaurora.apofirstlight.registry.AflSounds.TRUCK_KEY.get(), 0.6F, 1.15F);
        return true;
    }

    /** Server: the player's key for this truck into the ignition, if they carry it and it is not in yet. */
    private void insertKey(ServerPlayer player) {
        if (!keyInIgnition() && VehicleKeyItem.take(player, getUUID())) {
            setKeySpot(CabStowage.KeySpot.IGNITION);
            cabSound(point(0.55, -0.20, 0.98), com.antaurora.apofirstlight.registry.AflSounds.TRUCK_KEY.get(), 0.6F, 1.0F);
        }
    }

    /** Server: a sound in the cab, heard by everyone near. */
    private void cabSound(Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level().playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    /** Server: a compartment opened or shut, with a placeholder click (the latch's recording; own takes to be recorded). */
    private void toggleCompartment(CabStowage.Compartment c) {
        boolean open = !compartmentOpen(c);
        setCompartmentOpen(c, open);
        boolean visor = c == CabStowage.Compartment.VISOR_L || c == CabStowage.Compartment.VISOR_R;
        Vec3 at = switch (c) {
            case VISOR_L -> point(0.30, -0.40, 1.78);
            case VISOR_R -> point(0.30, 0.40, 1.78);
            case GLOVEBOX -> point(0.58, 0.41, 0.90);
            case ASHTRAY -> point(0.58, 0, 0.81);
        };
        cabSound(at, com.antaurora.apofirstlight.registry.AflSounds.DISTRIBUTION_PANEL_LATCH.get(),
                visor ? 0.25F : 0.5F, visor ? 0.8F : open ? 1.25F : 1.1F);
    }

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
        stowageTick();
        float[] setting = HVAC[climateMode()];
        for (int i = 0; i < hvac.length; i++) {
            hvacO[i] = hvac[i];
            hvac[i] += Mth.clamp(setting[i] - hvac[i], -HVAC_STEP, HVAC_STEP);
        }
        drive();
        placeParts();
        carry();
        if (level() instanceof net.minecraft.server.level.ServerLevel server) flattenPlants(server);
        if (!level().isClientSide) {
            long now = level().getGameTime();
            // the one who got out last tick: its client to the spot outside (after the passengers packet that let it go)
            if (exitId >= 0) {
                if (level().getEntity(exitId) instanceof ServerPlayer player && !player.isPassenger() && player.distanceToSqr(exitSpot) < 64)
                    player.connection.teleport(exitSpot.x, exitSpot.y, exitSpot.z, player.getYRot(), player.getXRot());
                exitId = -1;
            }
            // a seat whose rider is gone (left another way, died) frees up
            for (int i = 0; i < SEAT.length; i++) {
                int id = entityData.get(SEAT[i]);
                if (id >= 0 && getPassengers().stream().noneMatch(p -> p.getId() == id)) entityData.set(SEAT[i], -1);
            }
            // the stop lamps: the driver's back key is the brake pedal (VehicleInput#brake; the server sees it in the
            // vanilla riding input)
            LivingEntity driver = getControllingPassenger();
            boolean braking = driver != null && driver.zza < -0.01F;
            int lamps = lamps(), next = braking ? lamps | LAMP_BRAKE : lamps & ~LAMP_BRAKE;
            if (driver == null) next &= ~HORN;   // the horn needs a hand on it
            if (next != lamps) entityData.set(LAMPS, (byte) next);
            // what the infected hear (design doc 8.11): the engine (louder revving or pulling), the horn
            net.minecraft.server.level.ServerLevel server = (net.minecraft.server.level.ServerLevel) level();
            Vec3 engine = point(1.7, 0, 0.8);
            if (engineRunning() && Math.floorMod(now + getId(), NOISE_INTERVAL) == 0)
                noise(server, engine, tachometer(1) > 1800 || throttlePedal() > 0.5F ? ENGINE_LOUD_NOISE : ENGINE_NOISE);
            if (horn() && Math.floorMod(now, HORN_INTERVAL) == 0) noise(server, engine, HORN_NOISE);
            if (Math.floorMod(now + getId(), 20) == 0 || Double.isNaN(cabAir)) cabClimate(server, 1.0);
        }
    }

    // ---- lamps (design doc 8.10) ----

    public int lamps() { return entityData.get(LAMPS); }

    /** The light switch is on: headlamps (low beam), parking lamps, tail and plate lamps, the dash lights. */
    public boolean headlights() { return (lamps() & LAMP_HEAD) != 0; }

    /** The high beam is lit (the switch on and the high beam on). */
    public boolean highBeam() { return (lamps() & (LAMP_HEAD | LAMP_HIGH)) == (LAMP_HEAD | LAMP_HIGH); }

    public boolean braking() { return (lamps() & LAMP_BRAKE) != 0; }

    /** The horn sounds (the driver holds its key; design doc 8.11). */
    public boolean horn() { return (lamps() & HORN) != 0; }

    /** In reverse, or shifting into it (the reverse lamp switch on the gearbox). */
    public boolean reversing() { return gear() < 0 || shiftTarget() < 0; }

    /** The turn signal lever: -1 left, 1 right, 0 none (design doc 8.12). */
    public int turnSignal() { int l = lamps(); return (l & TURN_L) != 0 ? -1 : (l & TURN_R) != 0 ? 1 : 0; }

    /** The hazard switch is on (both sides flash). */
    public boolean hazard() { return (lamps() & HAZARD) != 0; }

    /** The flasher's bits as last seen and the tick they changed (the first flash comes at once; client). */
    private int flasherBits = -1;
    private int flasherStart;

    /**
     * Client, this frame: one side's turn lamps lit, the flasher's lit half (the turn signal that way or the hazards); the
     * cycle starts when the switch changes, so the first flash comes at once. The lamps, the flares and the HUD all ask.
     */
    public boolean turnLit(boolean right, float partialTick) {
        int bits = lamps() & (TURN_L | TURN_R | HAZARD);
        if (bits != flasherBits) {
            flasherBits = bits;
            flasherStart = tickCount;
        }
        if ((bits & HAZARD) == 0 && (bits & (right ? TURN_R : TURN_L)) == 0) return false;
        float t = (tickCount + partialTick - flasherStart) % BLINK_PERIOD;
        return t >= 0 && t < BLINK_PERIOD * BLINK_ON;
    }

    /**
     * Server: the driver's light key (VehiclePackets.Lamps; one key, GTA-style, user 2026-10-16). SWITCH_LIGHTS (a tap)
     * turns the lights on on low beam, or off; SWITCH_HIGH (a hold) flips the high beam, turning the lights on with it.
     * The turn signal keys (user 2026-10-16: one press on, the same again off; the other side's key moves it over) and the
     * hazard switch (on / off; flashes both sides over a turn signal, which comes back when the hazards go off).
     */
    void lampSwitch(int action, ServerPlayer driver) {
        if (action == INSERT_KEY) {
            // the driver's ignition key with the key on them and not yet in: it goes in (VehicleDriving asks once per press)
            insertKey(driver);
            return;
        }
        if (action == RELEASE_WHEEL) {
            // the driver's click while holding the wheel: the hands go back to the lap
            setWheelHeld(false);
            return;
        }
        if (action == TAKE_KEY_OUT) {
            // the driver's hand drawing the key out of the cylinder (a click on the keyhole, or getting out): with the engine off
            if (keyInIgnition() && !engineRunning()) giveKey(driver);
            return;
        }
        if (action == KEY_HAND_ON || action == KEY_HAND_PULL || action == KEY_HAND_OFF) {
            // the driver's hand going for the ignition key, pulling it, or back (VehicleDriving): for everyone else to see
            entityData.set(KEY_HAND, action == KEY_HAND_ON ? HAND_REACH : action == KEY_HAND_PULL ? HAND_PULL : HAND_OFF);
            return;
        }
        int lamps = lamps();
        if (action == SWITCH_LIGHTS) lamps = (lamps & LAMP_HEAD) != 0 ? lamps & ~(LAMP_HEAD | LAMP_HIGH) : (lamps | LAMP_HEAD) & ~LAMP_HIGH;
        else if (action == SWITCH_HIGH) lamps = (lamps & LAMP_HEAD) == 0 ? lamps | LAMP_HEAD | LAMP_HIGH : lamps ^ LAMP_HIGH;
        else if (action == HORN_ON) lamps |= HORN;
        else if (action == HORN_OFF) lamps &= ~HORN;
        else if (action == SIGNAL_LEFT) lamps = (lamps & TURN_L) != 0 ? lamps & ~TURN_L : (lamps | TURN_L) & ~TURN_R;
        else if (action == SIGNAL_RIGHT) lamps = (lamps & TURN_R) != 0 ? lamps & ~TURN_R : (lamps | TURN_R) & ~TURN_L;
        else if (action == SWITCH_HAZARD) lamps ^= HAZARD;
        else if (action == SWITCH_WIPER) {
            // the wiper stalk one step on: off, intermittent, low, high, off
            entityData.set(WIPER, (byte) ((wiperMode() + 1) % WiperSystem.MODES));
            return;
        }
        else if (action == SWITCH_CLIMATE) {
            // the heater panel one step on: off, heat, cool, off
            entityData.set(CLIMATE, (byte) ((climateMode() + 1) % CabClimate.MODES));
            return;
        }
        else return;
        entityData.set(LAMPS, (byte) lamps);
    }

    // ---- wipers (docs/vehicles/t15_windshield_rain_design.md) ----

    /** The wiper switch: WiperSystem.OFF, INTERMITTENT, LOW, HIGH. */
    public int wiperMode() { return Math.floorMod(entityData.get(WIPER), WiperSystem.MODES); }

    // ---- cab air (CabClimate, design doc 8.19) ----

    /** The heater panel's switch: CabClimate.OFF, HEAT, COOL. */
    public int climateMode() { return Math.floorMod(entityData.get(CLIMATE), CabClimate.MODES); }

    /**
     * The heater panel's three sliders (mode, temperature, fan; 0 left .. 1 right) for each setting: off (fan off), heat
     * (foot, hot, fan 3), cool (face, cold, fan 3). A hand moves them in about a third of a second (HVAC_STEP a tick).
     */
    private static final float[][] HVAC = {{0.3F, 0.55F, 0.0F}, {0.75F, 0.95F, 0.67F}, {0.0F, 0.05F, 0.67F}};
    private static final float HVAC_STEP = 0.15F;
    private final float[] hvac = HVAC[CabClimate.OFF].clone(), hvacO = hvac.clone();

    /** Client: a heater slider (0 mode, 1 temperature, 2 fan) this frame, 0 left .. 1 right. */
    public float hvacSlider(int i, float partialTick) { return Mth.lerp(partialTick, hvacO[i], hvac[i]); }

    /** Server: the cab's air (C; NaN until its first update). */
    private double cabAir = Double.NaN;

    public double cabTemperature() { return cabAir; }

    /** Server, once a second: the cab's air one step on (CabClimate), from the air around the cab. */
    private void cabClimate(net.minecraft.server.level.ServerLevel server, double dt) {
        Vec3 roof = point(-0.06, 0, 1.9);
        BlockPos at = BlockPos.containing(roof);
        double[] air = com.antaurora.apofirstlight.temperature.PlayerTemperature.outdoorAndSheltered(server, at, getY());
        // the sun on the cab: its height on a clear sky, with the sky open above the roof
        double sun = server.dimensionType().hasSkyLight() && server.canSeeSky(at.above())
                ? Math.max(0, Math.cos(server.getSunAngle(1)))
                * (1 - com.antaurora.apofirstlight.weather.AflWeather.cloud(server, roof.x, roof.z)) : 0;
        boolean shut = !doorOpen(false) && !doorOpen(true);
        Drivetrain d = drivetrain();
        boolean running = d != null && d.running, power = electricsOn();
        double coolant = d != null ? d.coolant : savedCoolant;
        int mode = climateMode();
        double target = CabClimate.target(air[0], air[1], sun, shut, mode, power, running, coolant);
        cabAir = CabClimate.step(cabAir, target, dt, shut, mode != CabClimate.OFF && power);
    }

    /** The electrics are on (the engine runs or the key is at preheat): the wipers and the gauges have power. */
    public boolean electricsOn() { Drivetrain d = drivetrain(); return d != null && (d.running || d.ignition == Drivetrain.PREHEAT); }

    /** Client: the wipers' stroke and the stalk (WiperSystem). */
    private final WiperSystem wipers = new WiperSystem();

    /** Client: the wipers to this frame (once per frame time; the renderer and the windshield water both ask). */
    public WiperSystem wipers(float partialTick) {
        wipers.update((tickCount + partialTick) / 20.0, wiperMode(), electricsOn());
        return wipers;
    }

    /** Client: a point of the truck's frame (forward, right, up from the origin) in the world this frame. */
    public Vec3 framePoint(double f, double r, double y, float partialTick) {
        return model(getPosition(partialTick), orientation(partialTick, new Quaternionf()), r, y, -f);
    }

    /** Client: a direction of the truck's frame (forward, right, up) in the world this frame (unit for a unit input). */
    public Vec3 frameDirection(double f, double r, double y, float partialTick) {
        org.joml.Vector3f v = orientation(partialTick, new Quaternionf()).transform(new org.joml.Vector3f((float) r, (float) y, (float) -f));
        return new Vec3(v.x, v.y, v.z);
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

    /** A point of the truck's frame (forward, right, up) in the world, with the body's pitch and roll. */
    public Vec3 point(double f, double r, double y) {
        Vector3d p = orient().transform(new Vector3d(r, y, -f));
        return new Vec3(getX() + p.x, getY() + p.y, getZ() + p.z);
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

    /** Another vehicle's hull point inside this one's boxes (its frame, yaw only): out the nearest side or the top. */
    @Override
    public boolean pushOut(double x, double y, double z, VehicleWorld.Push out) {
        double[] l = PickupTruckLayout.local(position(), getYRot(), new Vec3(x, y, z));
        for (Box b : PickupTruckLayout.BODY) if (outOf(b, l, out)) return true;
        return outOf(tailgateOpen() ? PickupTruckLayout.TAILGATE_DOWN : PickupTruckLayout.TAILGATE_SHUT, l, out);
    }

    private boolean outOf(Box b, double[] l, VehicleWorld.Push out) {
        double f = l[0], r = l[1], u = l[2];
        if (f < b.f0() || f > b.f1() || r < b.r0() || r > b.r1() || u < b.y0() || u > b.y1()) return false;
        double a = Math.toRadians(getYRot()), s = Math.sin(a), c = Math.cos(a);
        double best = b.y1() - u;
        out.set(0, 1, 0, best);
        if (f - b.f0() < best) out.set(s, 0, -c, best = f - b.f0());       // backwards
        if (b.f1() - f < best) out.set(-s, 0, c, best = b.f1() - f);       // forwards
        if (r - b.r0() < best) out.set(c, 0, s, best = r - b.r0());        // to the left
        if (b.r1() - r < best) out.set(-c, 0, -s, b.r1() - r);             // to the right
        return true;
    }

    // ---- driving (V0-b package 1: vehicle/physics) ----

    /** The driver's seat's player steers it (vanilla then lets that player's client run it: Entity#isControlledByLocalInstance). */
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        int id = entityData.get(SEAT[PickupTruckLayout.DRIVER]);
        if (id < 0) return null;
        for (Entity p : getPassengers()) if (p.getId() == id && p instanceof Player player) return player;
        return null;
    }

    /** The orientation now (model frame to world); before the physics placed it, from the yaw. */
    private Quaterniond orient() {
        if (!oriented) orient.rotationY(Math.toRadians(180.0 - getYRot()));
        return orient;
    }

    /** Client: the orientation this frame (model frame to world). */
    public Quaternionf orientation(float partialTick, Quaternionf out) {
        Quaterniond q = new Quaterniond(orientO).slerp(orient(), partialTick);
        return out.set((float) q.x, (float) q.y, (float) q.z, (float) q.w);
    }

    /** Client: a wheel's suspension travel this frame (blocks, + up). */
    public float wheelTravel(int wheel, float partialTick) { return Mth.lerp(partialTick, travelO[wheel], travel[wheel]); }

    /** Client: a wheel's turn this frame (rad, + rolling forward). */
    public float wheelSpin(int wheel, float partialTick) { return spinO[wheel] + spinRate[wheel] * 0.05F * partialTick; }

    /** Client: the centre steer angle this frame (rad, + to the right). */
    public float steerAngle(float partialTick) { return Mth.lerp(partialTick, steerO, steer); }

    /** One tick of driving: who runs the physics, then the entity follows it. */
    private void drive() {
        orientO.set(orient());
        System.arraycopy(travel, 0, travelO, 0, 4);
        System.arraycopy(spin, 0, spinO, 0, 4);
        steerO = steer;
        if (physics == null) {
            physics = new VehiclePhysics(VehicleSpec.T15);
            world = new LevelVehicleWorld(level(), this);
            physics.reset(getX(), getY(), getZ(), orient());
            physics.drivetrain.gear = savedGear;
            physics.drivetrain.fourWheel = savedFour || savedLow;
            physics.drivetrain.low = savedLow;
            physics.drivetrain.coolant = savedCoolant;
            placed();
        } else if (!level().isClientSide && (getX() != placedX || getY() != placedY || getZ() != placedZ || getYRot() != placedYaw)) {
            // moved by something else (a command): the physics starts again from there, level
            physics.reset(getX(), getY(), getZ(), getYRot());
            place();
            VehiclePackets.toTracking(this, new VehiclePackets.Sync(getId(), physics.capture(), false));
        }
        boolean local = isControlledByLocalInstance();
        // the air conditioning's compressor on the belt (CabClimate): wherever this truck is simulated
        physics.drivetrain.accessoryTorque = climateMode() == CabClimate.COOL ? CabClimate.AC_TORQUE : 0;
        if (level().isClientSide) {
            if (local) {
                if (!wasLocal && !synced) physics.reset(getX(), getY(), getZ(), orient());
                physics.tick(clientInput.get(), world);
                place();
                AflNetwork.vehicleToServer(new VehiclePackets.Drive(getId(), physics.capture()));
            } else {
                ease();
            }
        } else if (local) {
            // no driver: the server runs it, parked, until it rests; asleep, it looks at its ground now and then. An engine
            // left running idles on (user 2026-10-21), burning fuel, and keeps it awake until it runs dry
            boolean idling = physics.drivetrain.running && fuel() > 0;
            if (physics.asleep && (idling || tickCount % SLEEP_CHECK == 0 && physics.groundChanged(world))) physics.wake();
            if (!physics.asleep) {
                physics.tick(idling ? VehicleInput.IDLING : VehicleInput.PARKED, world);
                place();
                VehicleState state = physics.capture();
                if (idling) burn(state.drive());
                VehiclePackets.toTracking(this, new VehiclePackets.Sync(getId(), state, false));
            }
        }
        wasLocal = local;
        gauges();
    }

    /**
     * Gauges, each tick (drawn between ticks): the tachometer from the engine, the speedometer from the rear wheels (the
     * cable drive on the gearbox), fuel and coolant creeping (the tank counts whole litres; a real gauge is damped). Fuel
     * and coolant read only with the key on (running or starting), else they sink back to their rests (E, 40 C).
     */
    private void gauges() {
        rpmO = rpm;
        speedoO = speedo;
        fuelShownO = fuelShown;
        coolantShownO = coolantShown;
        Drivetrain d = physics.drivetrain;
        rpm += (float) ((d.rpm() - rpm) * 0.6);
        speedo = (float) (Math.abs(spinRate[VehicleSpec.RL] + spinRate[VehicleSpec.RR]) / 2 * VehicleSpec.T15.radius * 3.6);
        boolean on = d.running || d.ignition != Drivetrain.KEY_OFF;
        float fuel = on ? fuel() / (float) TANK : 0;
        if (fuelShown < 0) fuelShown = fuelShownO = 0;
        fuelShown += Math.copySign(Math.min(Math.abs(fuel - fuelShown), 0.004F), fuel - fuelShown);
        coolantShown += (float) (((on ? d.coolant : 40) - coolantShown) * 0.1);
    }

    /** The entity to where the physics has it. */
    private void place() {
        Vector3d o = physics.origin(new Vector3d());
        setPos(o.x, o.y, o.z);
        setYRot(physics.yaw());
        orient.set(physics.q);
        oriented = true;
        steer = (float) physics.steer;
        for (int i = 0; i < 4; i++) {
            travel[i] = (float) physics.travel[i];
            spin[i] = (float) physics.spin[i];
            spinRate[i] = (float) physics.spinRate[i];
        }
        placed();
    }

    private void placed() {
        placedX = getX();
        placedY = getY();
        placedZ = getZ();
        placedYaw = getYRot();
    }

    /** Client, watching: a step towards the last synced state; the wheels keep turning at its rate. */
    private void ease() {
        if (target != null && easeLeft > 0) {
            double k = 1.0 / easeLeft--;
            setPos(Mth.lerp(k, getX(), target.x()), Mth.lerp(k, getY(), target.y()), Mth.lerp(k, getZ(), target.z()));
            orient().slerp(new Quaterniond(target.qx(), target.qy(), target.qz(), target.qw()).normalize(), k).normalize();
            oriented = true;
            Vector3d front = orient.transform(new Vector3d(0, 0, -1));
            setYRot((float) Math.toDegrees(Math.atan2(-front.x, front.z)));
            steer += (float) ((target.steer() - steer) * k);
            for (int i = 0; i < 4; i++) travel[i] += (float) ((target.travel()[i] - travel[i]) * k);
        }
        for (int i = 0; i < 4; i++) {
            if (target != null) spinRate[i] = target.asleep() ? 0 : target.spinRate()[i];
            spin[i] = (float) ((spin[i] + spinRate[i] * 0.05) % (Math.PI * 2));
        }
        placed();
    }

    /** Server: the driver's state for this tick (VehiclePackets.Drive), checked, taken and passed on. */
    void acceptDrive(VehicleState state, ServerPlayer driver) {
        if (physics == null) return;
        Vector3d o = physics.origin(new Vector3d());
        double dx = state.x() - o.x, dy = state.y() - o.y, dz = state.z() - o.z;
        if (!state.finite() || dx * dx + dy * dy + dz * dz > MAX_STEP * MAX_STEP || state.speed() > MAX_SPEED) {
            AflNetwork.vehicleTo(driver, new VehiclePackets.Sync(getId(), physics.capture(), true));
            return;
        }
        physics.apply(state);
        place();
        burn(state.drive());
        // a crash (design doc 8.11): the speed lost in this tick beyond any braking, heard by the infected
        double drop = acceptedSpeed - state.speed();
        acceptedSpeed = state.speed();
        if (drop > CRASH_DROP) noise(driver.serverLevel(), position().add(0, 0.8, 0), drop > CRASH_HEAVY_DROP ? CRASH_HEAVY_NOISE : CRASH_NOISE);
        driver.serverLevel().getChunkSource().move(driver);
        VehiclePackets.toTracking(this, new VehiclePackets.Sync(getId(), state, false));
    }

    /** Server: the fuel the driver's engine used this tick out of the tank (at least the idle's while running). */
    private void burn(Drivetrain.State drive) {
        if (!drive.running()) return;
        double idle = VehicleSpec.T15.fuelIdle / 3600 * 0.5;
        fuelBurnt += Math.max(drive.fuelFlow(), idle) * 0.05;
        if (fuelBurnt >= 1.0 && fuel() > 0) {
            int litres = (int) fuelBurnt;
            fuelBurnt -= litres;
            tank.drain(litres, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
    }

    // ---- drivetrain readings (gauges, hints, the HUD, the seated driver) ----

    private Drivetrain drivetrain() { return physics != null ? physics.drivetrain : null; }

    public boolean engineRunning() { Drivetrain d = drivetrain(); return d != null && d.running; }

    /** -1 R, 0 N, 1..5. */
    public int gear() { Drivetrain d = drivetrain(); return d != null ? d.gear : savedGear; }

    public boolean fourWheel() { Drivetrain d = drivetrain(); return d != null ? d.fourWheel : savedFour || savedLow; }

    /** The transfer case in low range (4L). */
    public boolean lowRange() { Drivetrain d = drivetrain(); return d != null ? d.low : savedLow; }

    /** The transfer case: 0 2H, 1 4H, 2 4L. */
    public int transferMode() { return lowRange() ? 2 : fourWheel() ? 1 : 0; }

    /** Drivetrain.KEY_OFF / PREHEAT / CRANK. */
    public int ignitionPhase() { Drivetrain d = drivetrain(); return d != null ? d.ignition : Drivetrain.KEY_OFF; }

    /** A gear change under way: its progress (0..1, 1 when none) and the gear it goes to (D's seated driver: the hand on the lever). */
    public float shiftProgress() { Drivetrain d = drivetrain(); return d != null ? (float) d.shiftProgress() : 1.0F; }

    public int shiftTarget() { Drivetrain d = drivetrain(); return d != null && d.shifting() ? d.shiftTarget : gear(); }

    /** The shift under way's whole time (s; 0 when none or unknown). */
    public double shiftTotal() { Drivetrain d = drivetrain(); return d != null && d.shifting() ? d.shiftTotal : 0; }

    /** Client: the gear lever's tilts this frame (deg; ShiftLever, PickupTruckLayout#leverTurn). */
    public float leverFore(float partialTick) { driverHands(partialTick); return (float) lever.fore; }

    public float leverAcross(float partialTick) { driverHands(partialTick); return (float) lever.across; }

    /** Client: the transfer case lever's tilt this frame (deg; TransferLever, PickupTruckLayout#transferTurn). */
    public float transferFore(float partialTick) { driverHands(partialTick); return (float) transferLever.fore; }

    /**
     * Client: the gear lever and the driver's hands to this frame. Once per frame time: the renderer (the lever) and the
     * seated pose (the hands) both ask.
     */
    private void driverHands(float partialTick) {
        double now = (tickCount + partialTick) / 20.0;
        if (now == handsTime) return;
        handsTime = now;
        double wheel = Math.toDegrees(steerAngle(partialTick) * PickupTruckLayout.STEERING_RATIO);
        lever.update(now, shiftTarget(), shiftTotal());
        transferLever.update(now, transferMode());
        int driver = entityData.get(SEAT[PickupTruckLayout.DRIVER]);
        if (driver != handsDriver) {
            handsDriver = driver;
            hands.reset(now, wheel);
        }
        hands.update(now, wheel, lever.handOff() || transferLever.handOff());
    }

    /** The clutch pedal as the driver holds it (0 up .. 1 down). */
    public float clutchPedal() { Drivetrain d = drivetrain(); return d != null ? (float) d.clutchPedal() : 0.0F; }

    /** The throttle pedal (0..1). */
    public float throttlePedal() { Drivetrain d = drivetrain(); return d != null ? (float) d.pedal : 0.0F; }

    /** Client, this frame: tachometer (rpm), speedometer (km/h), fuel gauge (0..1), coolant (C). */
    public float tachometer(float pt) { return Mth.lerp(pt, rpmO, rpm); }

    public float speedometer(float pt) { return Mth.lerp(pt, speedoO, speedo); }

    public float fuelGauge(float pt) { return Math.max(0, Mth.lerp(pt, fuelShownO, fuelShown)); }

    public float coolantGauge(float pt) { return Mth.lerp(pt, coolantShownO, coolantShown); }

    /** Server: the state to a client that starts tracking it. */
    void sendStateTo(ServerPlayer player) {
        if (physics != null) AflNetwork.vehicleTo(player, new VehiclePackets.Sync(getId(), physics.capture(), false));
    }

    /** Client: a synced state (VehiclePackets.Sync): watchers ease to it; the driver takes only a correction. */
    public void acceptSync(VehicleState state, boolean correct) {
        if (physics == null) {
            physics = new VehiclePhysics(VehicleSpec.T15);
            world = new LevelVehicleWorld(level(), this);
        }
        if (isControlledByLocalInstance()) {
            if (correct) {
                physics.apply(state);
                place();
            }
            return;
        }
        physics.apply(state);   // so taking the wheel starts from the latest
        target = state;
        easeLeft = synced ? EASE_TICKS : 1;
        synced = true;
    }

    /** Watchers follow the synced state (VehiclePackets), not the vanilla position packets (whole-degree yaw). */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        if (!synced) super.lerpTo(x, y, z, yaw, pitch, steps, teleport);
    }

    /**
     * After the move: who stood on it (on its boxes as they were) goes along, turned with it; who is now inside a box is
     * pushed out sideways, not against the move. The server moves mobs and items; each client its own player.
     */
    private void carry() {
        List<AABB> after = collisionBoxes();
        double dx = getX() - carryX, dy = getY() - carryY, dz = getZ() - carryZ;
        float dyaw = Double.isNaN(carryX) ? 0.0F : Mth.wrapDegrees(getYRot() - carryYaw);
        turned = dyaw;
        if (!Double.isNaN(carryX) && (dx != 0 || dy != 0 || dz != 0 || dyaw != 0)) {
            AABB area = collisionBounds();
            for (AABB b : carryBoxes) area = area.minmax(b);
            area = area.inflate(1.0);
            List<Entity> near = new ArrayList<>();
            if (level().isClientSide) {
                Player p = localPlayer.get();
                if (p != null && p.level() == level() && !p.isPassenger() && !p.isSpectator() && !p.getAbilities().flying && area.intersects(p.getBoundingBox()))
                    near.add(p);
            } else {
                near.addAll(level().getEntities(this, area, e -> !(e instanceof Player) && !(e instanceof PartEntity) && !(e instanceof PickupTruckEntity)
                        && !e.isPassenger() && !e.noPhysics && !passesThrough(e)));
            }
            Vec3 before = new Vec3(carryX, carryY, carryZ);
            for (Entity e : near) {
                if (standsOn(e, carryBoxes)) {
                    double[] l = PickupTruckLayout.local(before, carryYaw, e.position());
                    Vec3 p = PickupTruckLayout.world(position(), getYRot(), l[0], l[1], l[2]);
                    e.setPos(p.x, p.y, p.z);
                    e.setYRot(e.getYRot() + dyaw);
                    e.setYHeadRot(e.getYHeadRot() + dyaw);
                    if (e instanceof LivingEntity living) living.setYBodyRot(living.yBodyRot + dyaw);
                } else {
                    pushAside(e, after, dx, dz);
                }
            }
        }
        carryX = getX();
        carryY = getY();
        carryZ = getZ();
        carryYaw = getYRot();
        carryBoxes = after;
    }

    /**
     * Server: grass, ferns and flowers under the truck's outline (bumper to bumper, a little wider than the body) are
     * pressed down, lying the way it moves (block/FlattenedPlantBlock), each time it has moved 0.1 m or turned 2 degrees.
     * Before (2026-10-16) the plants stood up through the body and the bed.
     */
    private void flattenPlants(net.minecraft.server.level.ServerLevel server) {
        if (!Double.isNaN(flatX) && Math.abs(getX() - flatX) + Math.abs(getZ() - flatZ) < 0.1 && Math.abs(Mth.wrapDegrees(getYRot() - flatYaw)) < 2) return;
        flatX = getX();
        flatZ = getZ();
        flatYaw = getYRot();
        net.minecraft.core.Direction facing;
        if (physics != null && Math.hypot(physics.v.x, physics.v.z) > 0.3) facing = net.minecraft.core.Direction.getNearest(physics.v.x, 0, physics.v.z);
        else facing = net.minecraft.core.Direction.fromYRot(getYRot());
        AABB area = collisionBounds();
        int y0 = Mth.floor(getY()) - 1, y1 = Mth.floor(getY()) + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos pressed = null;
        for (int x = Mth.floor(area.minX); x <= Mth.floor(area.maxX); x++)
            for (int z = Mth.floor(area.minZ); z <= Mth.floor(area.maxZ); z++) {
                double[] l = PickupTruckLayout.local(position(), getYRot(), new Vec3(x + 0.5, getY(), z + 0.5));
                if (l[0] < -2.55 || l[0] > 2.55 || Math.abs(l[1]) > 1.05) continue;
                for (int y = y0; y <= y1; y++)
                    if (com.antaurora.apofirstlight.block.FlattenedPlantBlock.flatten(server, pos.set(x, y, z), facing) && pressed == null) pressed = pos.immutable();
            }
        if (pressed != null) server.playSound(null, pressed, net.minecraft.sounds.SoundEvents.GRASS_STEP, net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 0.8F);
    }

    /** Whether the entity's feet rest on one of these boxes' tops. */
    private static boolean standsOn(Entity e, List<AABB> boxes) {
        AABB b = e.getBoundingBox();
        for (AABB box : boxes)
            if (box.maxX > b.minX && box.minX < b.maxX && box.maxZ > b.minZ && box.minZ < b.maxZ && box.maxY <= b.minY + 0.05 && box.maxY >= b.minY - 0.3)
                return true;
        return false;
    }

    /** Out of the boxes it is inside: the shortest way sideways that is not against the truck's move (dx, dz). */
    private static void pushAside(Entity e, List<AABB> boxes, double dx, double dz) {
        for (int k = 0; k < 4; k++) {
            AABB eb = e.getBoundingBox(), in = eb.deflate(1.0E-3);
            AABB hit = null;
            for (AABB b : boxes) if (b.intersects(in)) { hit = b; break; }
            if (hit == null) return;
            double[][] ways = {{hit.maxX - eb.minX, 0}, {hit.minX - eb.maxX, 0}, {0, hit.maxZ - eb.minZ}, {0, hit.minZ - eb.maxZ}};
            double[] best = null, any = null;
            for (double[] w : ways) {
                double len = Math.abs(w[0]) + Math.abs(w[1]);
                if (any == null || len < Math.abs(any[0]) + Math.abs(any[1])) any = w;
                if (w[0] * dx + w[1] * dz >= 0 && (best == null || len < Math.abs(best[0]) + Math.abs(best[1]))) best = w;
            }
            double[] w = best != null ? best : any;
            e.setPos(e.getX() + w[0] + Math.signum(w[0]) * 0.01, e.getY(), e.getZ() + w[1] + Math.signum(w[1]) * 0.01);
        }
    }

    // ---- what a look at the truck works ----

    /** What a click on the truck does now; the prompt says the same (client/WorldInteractionHint). */
    public enum Action {
        OPEN_HOOD("open"), CLOSE_HOOD("close"), OPEN_DOOR("open_door"), CLOSE_DOOR("close_door"), GET_IN("get_in"),
        OPEN_TAILGATE("lower"), CLOSE_TAILGATE("close"), OPEN_FILLER("open"), CLOSE_FILLER("close"), REFUEL("refuel"),
        STOP_POUR("stop"), SIT("sit"), CARGO("open"), MOVE_OVER("move_over"),
        // the cab's compartments and the key (2026-10-21; CabStowage)
        VISOR_DOWN("visor_down"), VISOR_UP("visor_up"), OPEN_GLOVEBOX("open_glovebox"), CLOSE_GLOVEBOX("close_glovebox"),
        OPEN_ASHTRAY("open_ashtray"), CLOSE_ASHTRAY("close_ashtray"), TAKE_KEY("take_key"),
        // the keyhole without the key in: it goes in (carried) or "no key" (a prompt only); the steering wheel (2026-10-21)
        PUT_KEY_IN("insert_key"), NO_KEY("no_key"), GRIP_WHEEL("grip_wheel");

        public final String hint;

        Action(String hint) { this.hint = hint; }

        /** The prompt's key: hint.apocalypse_firstlight.pickup_t15.* */
        public String key() { return "hint.apocalypse_firstlight.pickup_t15." + hint; }
    }

    /** An action and where its prompt sits (world). */
    public record Offer(Action action, Vec3 anchor, Pick pick, @Nullable CabStowage.Compartment compartment) {
        public Offer(Action action, Vec3 anchor, Pick pick) { this(action, anchor, pick, null); }
    }

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
        if (player.isSpectator()) return null;
        if (player.getRootVehicle() == this) {
            Offer close = ownDoorFor(player, true);
            if (close != null) return close;
            Offer cab = stowageFor(player);
            if (cab == null) cab = ownDoorFor(player, false);
            return cab != null ? cab : moveOverFor(player);
        }
        Pick pick = pick(player);
        if (pick == null) return null;
        ItemStack held = player.getMainHandItem();
        Action action = switch (pick.zone()) {
            case HOOD -> hoodOpen() ? Action.CLOSE_HOOD : Action.OPEN_HOOD;
            case DOOR_L, DOOR_R -> {
                boolean right = pick.zone() == Zone.DOOR_R;
                if (!doorOpen(right)) yield Action.OPEN_DOOR;
                // the open door itself shuts it; its opening gets in (sneaking shuts it from there too; user 2026-10-17:
                // with the doors open one could only get in)
                yield pick.leaf() || player.isShiftKeyDown() || seated(right ? PickupTruckLayout.PASSENGER : PickupTruckLayout.DRIVER) ? Action.CLOSE_DOOR : Action.GET_IN;
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

    /**
     * A cab rider looking out toward its own door: shut it when open (the doors stay as left; user 2026-10-16), open it
     * when shut (user 2026-10-21: the way out, #mayGetOut). {@code open}: the open door's offer, else the shut door's.
     */
    @Nullable
    private Offer ownDoorFor(Player player, boolean open) {
        int seat = seatOf(player);
        if (seat != PickupTruckLayout.DRIVER && seat != PickupTruckLayout.PASSENGER) return null;
        boolean right = PickupTruckLayout.SEATS[seat].right();
        if (doorOpen(right) != open) return null;
        double[] d = PickupTruckLayout.localDir(getYRot(), player.getViewVector(1.0F));
        if ((right ? d[1] : -d[1]) < CLOSE_LOOK) return null;
        Pick pick = new Pick(right ? Zone.DOOR_R : Zone.DOOR_L, 0, seat);
        return new Offer(open ? Action.CLOSE_DOOR : Action.OPEN_DOOR, anchor(pick), pick);
    }

    /**
     * Whether this rider may get out now (the sneak key, VehicleDriving.Keys#input): from a cab seat only with its own door
     * open (user 2026-10-21), from the bed always.
     */
    public boolean mayGetOut(Entity rider) {
        int seat = seatOf(rider);
        if (seat != PickupTruckLayout.DRIVER && seat != PickupTruckLayout.PASSENGER) return true;
        return doorOpen(PickupTruckLayout.SEATS[seat].right());
    }

    /**
     * A cab rider looking at a compartment (a visor, the glovebox, the ashtray: open or shut it; open with the key in it:
     * take the key), at the key lying on the seat, or at the key in the ignition with the engine off (take it). The
     * nearest along the look wins.
     */
    @Nullable
    private Offer stowageFor(Player player) {
        int seat = seatOf(player);
        if (seat != PickupTruckLayout.DRIVER && seat != PickupTruckLayout.PASSENGER) return null;
        double[] o = PickupTruckLayout.local(position(), getYRot(), player.getEyePosition()), d = PickupTruckLayout.localDir(getYRot(), player.getViewVector(1.0F));
        CabStowage.KeySpot key = keySpot();
        Offer best = null;
        double near = STOWAGE_REACH;
        for (CabStowage.Compartment c : CabStowage.Compartment.values()) {
            double t = CabStowage.box(c).hit(o, d, near);
            if (t < 0) continue;
            boolean open = compartmentOpen(c);
            Action a = open && key.in == c ? Action.TAKE_KEY : switch (c) {
                case VISOR_L, VISOR_R -> open ? Action.VISOR_UP : Action.VISOR_DOWN;
                case GLOVEBOX -> open ? Action.CLOSE_GLOVEBOX : Action.OPEN_GLOVEBOX;
                case ASHTRAY -> open ? Action.CLOSE_ASHTRAY : Action.OPEN_ASHTRAY;
            };
            near = t;
            best = new Offer(a, player.getEyePosition().add(player.getViewVector(1.0F).scale(t)), new Pick(Zone.SEAT, t, seat), c);
        }
        // the driver's steering wheel while the hands are free: take hold of it
        if (seat == PickupTruckLayout.DRIVER && !wheelHeld()) {
            double t = CabStowage.wheelHit(o, d, near);
            if (t >= 0) {
                near = t;
                best = new Offer(Action.GRIP_WHEEL, player.getEyePosition().add(player.getViewVector(1.0F).scale(t)), new Pick(Zone.SEAT, t, seat), null);
            }
        }
        // a key lying in the open (on the seat)
        if (key.box != null) {
            double t = key.box.hit(o, d, near);
            if (t >= 0) {
                near = t;
                best = new Offer(Action.TAKE_KEY, player.getEyePosition().add(player.getViewVector(1.0F).scale(t)), new Pick(Zone.SEAT, t, seat), null);
            }
        }
        // the keyhole, seen through the wheel's gap (it wins over the wheel around it): take the key out with the engine off,
        // put it in when carried, else "no key"; nothing while the engine runs
        double t = CabStowage.IGNITION_BOX.hit(o, d, STOWAGE_REACH);
        if (t >= 0) {
            Action a = key == CabStowage.KeySpot.IGNITION ? (engineRunning() ? null : Action.TAKE_KEY)
                    : VehicleKeyItem.carries(player, getUUID()) ? Action.PUT_KEY_IN : Action.NO_KEY;
            if (a != null) best = new Offer(a, player.getEyePosition().add(player.getViewVector(1.0F).scale(t)), new Pick(Zone.SEAT, t, seat), null);
        }
        return best;
    }

    /** How far a cab rider reaches into the cab (blocks along the look). */
    private static final double STOWAGE_REACH = 1.6;

    /**
     * A cab rider looking at the other cab seat while it is free and the truck (nearly) stands: move over to it (across
     * the bench, without getting out; user 2026-10-16).
     */
    @Nullable
    private Offer moveOverFor(Player player) {
        if (player.getVehicle() != this || speed() > GET_OUT_SPEED) return null;
        int seat = seatOf(player);
        if (seat != PickupTruckLayout.DRIVER && seat != PickupTruckLayout.PASSENGER) return null;
        int other = seat == PickupTruckLayout.DRIVER ? PickupTruckLayout.PASSENGER : PickupTruckLayout.DRIVER;
        if (seated(other)) return null;
        double t = PickupTruckLayout.cabSeatHit(other, PickupTruckLayout.local(position(), getYRot(), player.getEyePosition()),
                PickupTruckLayout.localDir(getYRot(), player.getViewVector(1.0F)), 2.0);
        if (t < 0) return null;
        Seat s = PickupTruckLayout.SEATS[other];
        return new Offer(Action.MOVE_OVER, point(s.f(), s.r(), 0.95), new Pick(Zone.SEAT, t, other));
    }

    /** Where a part's prompt sits: the hood latch, a door's handle, the filler, the tailgate handle, a seat. */
    private Vec3 anchor(Pick pick) {
        return switch (pick.zone()) {
            case HOOD -> point(2.24, 0, hoodOpen() ? 1.45 : 1.05);
            case DOOR_L -> pick.leaf() ? point(0.52, -1.72, 0.98) : point(-0.10, -0.90, 0.98);
            case DOOR_R -> pick.leaf() ? point(0.52, 1.72, 0.98) : point(-0.10, 0.90, 0.98);
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
                sit(player, right ? PickupTruckLayout.PASSENGER : PickupTruckLayout.DRIVER);
            }
            case OPEN_FILLER, CLOSE_FILLER -> setFillerOpen(!fillerOpen());
            case REFUEL, STOP_POUR -> refuel(player, held);
            case OPEN_TAILGATE, CLOSE_TAILGATE -> setTailgateOpen(!tailgateOpen());
            case SIT -> sit(player, offer.pick().seat());
            case MOVE_OVER -> moveOver(player, offer.pick().seat());
            case VISOR_DOWN, VISOR_UP, OPEN_GLOVEBOX, CLOSE_GLOVEBOX, OPEN_ASHTRAY, CLOSE_ASHTRAY -> toggleCompartment(offer.compartment());
            case TAKE_KEY -> {
                if (player instanceof ServerPlayer server) giveKey(server);
            }
            case PUT_KEY_IN -> {
                if (player instanceof ServerPlayer server) insertKey(server);
            }
            case GRIP_WHEEL -> {
                if (getControllingPassenger() == player) setWheelHeld(true);
            }
            case NO_KEY -> {}
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

    /**
     * Whether the player drives a T15: in its driver's seat with the hands on the wheel and the gear lever, so no weapon in
     * hand (Loadout puts one away, client.inventory.GridClient and the weapon wheel mixin block switching to one; 2026-10-17).
     * Seated with the hands free (2026-10-21) the driver holds and uses things like a passenger; reaching for the ignition
     * key (G) counts as driving too (a gun goes away, nothing else is used meanwhile).
     */
    public static boolean driving(Entity player) {
        return player.getVehicle() instanceof PickupTruckEntity truck && truck.getControllingPassenger() == player
                && (truck.wheelHeld() || truck.keyHand());
    }

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

    /** Server: a rider slides over to this free seat (the other cab seat). */
    private void moveOver(Player player, int seat) {
        int from = seatOf(player);
        if (from < 0 || seated(seat)) return;
        entityData.set(SEAT[from], -1);
        entityData.set(SEAT[seat], player.getId());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().size() < SEAT.length; }

    /**
     * Who sat where when the truck was saved (by UUID; the seats themselves hold entity ids, new on every load). A player
     * who left the game in the truck rides it again on joining (PlayerList#placeNewPlayer: startRiding from the saved
     * RootVehicle) without going through sit: no seat named them, positionRider left them where they were, and they moved
     * about in the seated pose, through the body (user, 2026-10-18).
     */
    private final java.util.UUID[] savedSeats = new java.util.UUID[SEAT.length];

    /** Server: a passenger added without a seat (back from the save) gets the one it had, else the driver's, else a free one. */
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (level().isClientSide || seatOf(passenger) >= 0) return;
        int seat = -1;
        for (int i = 0; i < SEAT.length && seat < 0; i++) if (passenger.getUUID().equals(savedSeats[i]) && !seated(i)) seat = i;
        if (seat < 0 && !seated(PickupTruckLayout.DRIVER)) seat = PickupTruckLayout.DRIVER;
        for (int i = 0; i < SEAT.length && seat < 0; i++) if (!seated(i)) seat = i;
        if (seat < 0) return;
        entityData.set(SEAT[seat], passenger.getId());
        savedSeats[seat] = null;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        int seat = seatOf(passenger);
        if (seat < 0) return;   // its seat not synced yet: it stays where it is
        Seat s = PickupTruckLayout.SEATS[seat];
        Integer last = lastSeat.put(passenger.getId(), seat);
        if (last != null && last != seat && PickupTruckLayout.SEATS[last].cab() && s.cab()) slides.put(passenger.getId(), new int[]{last, tickCount});
        // the eyes (the camera) EYE_OVER_SEAT over the cushion and EYE_AHEAD ahead of its point, in the tilted truck's frame
        double yaw = Math.toRadians(s.yaw()), r = s.r() + slideOffset(passenger.getId(), seat, 0F);
        Vec3 eye = point(s.f() + Math.cos(yaw) * SeatPose.EYE_AHEAD, r + Math.sin(yaw) * SeatPose.EYE_AHEAD, s.cushion() + SeatPose.EYE_OVER_SEAT);
        move.accept(passenger, eye.x, eye.y - passenger.getEyeHeight(), eye.z);
        // riders turn with the truck (as in a boat)
        passenger.setYRot(passenger.getYRot() + turned);
        passenger.setYHeadRot(passenger.getYHeadRot() + turned);
        clampRotation(passenger, s);
    }

    @Override
    public int riderSeatIndex(Entity passenger) { return seatOf(passenger); }

    /** Client: a seat this frame in the world (the truck's full orientation), with its hands', feet's and door's points. */
    @Nullable
    @Override
    public RiderSeat riderSeat(int index, float partialTick) {
        if (index < 0 || index >= PickupTruckLayout.SEATS.length) return null;
        Seat s = PickupTruckLayout.SEATS[index];
        Vec3 origin = getPosition(partialTick);
        Quaternionf q = orientation(partialTick, new Quaternionf());
        boolean cab = s.cab();
        double floor = cab ? PickupTruckLayout.CAB_FLOOR : PickupTruckLayout.BED_FLOOR;
        int rider = entityData.get(SEAT[index]);
        // moving over: the seat (cushion, feet) still part of the way from the one it left
        Vec3 cushion = model(origin, q, s.r() + (rider >= 0 ? slideOffset(rider, index, partialTick) : 0), s.cushion(), -s.f());
        Quaternionf frame = new Quaternionf(q).rotateY((float) Math.toRadians(-s.yaw()));
        RiderSeat.Kind kind = index == PickupTruckLayout.DRIVER ? RiderSeat.Kind.DRIVER : cab ? RiderSeat.Kind.PASSENGER : RiderSeat.Kind.BENCH;
        int side = cab ? (s.right() ? 1 : -1) : 0;
        float angle = cab ? doorAngle(s.right(), partialTick) : 0F;
        Map<String, Vec3> anchors = new HashMap<>();
        boolean driver = kind == RiderSeat.Kind.DRIVER;
        RiderSeat seat = new RiderSeat(kind, cushion, frame, side, angle / DOOR_OPEN, anchors, driver ? wheelHold(partialTick) : 1F,
                driver ? keyReach(partialTick) : 0F);
        double ahead = cab ? 0.40 : 0.30;
        anchors.put("floor_l", seat.local(-0.10, floor - s.cushion(), -ahead));
        anchors.put("floor_r", seat.local(0.10, floor - s.cushion(), -ahead));
        if (kind == RiderSeat.Kind.DRIVER) {
            driverHands(partialTick);
            anchors.put("grip_l", model(origin, q, PickupTruckLayout.grip(false, Math.toRadians(hands.angle[0]), hands.lift[0])));
            // the right hand: on the rim, or on its way to / on / back from the gear lever's knob or the transfer lever's
            // (from where it let go, back to its rest); the gear lever first when both want it
            boolean onTransfer = lever.hand <= 0 && !lever.handGoing && transferLever.hand > 0;
            double reach = onTransfer ? transferLever.hand : lever.hand;
            boolean going = onTransfer ? transferLever.handGoing : lever.handGoing;
            double[] rim = reach > 0 ? PickupTruckLayout.grip(true, going ? Math.toRadians(hands.angle[1]) : 0, 0)
                    : PickupTruckLayout.grip(true, Math.toRadians(hands.angle[1]), hands.lift[1]);
            double[] knob = onTransfer ? PickupTruckLayout.transferKnob(transferLever.fore) : PickupTruckLayout.knob(lever.fore, lever.across);
            anchors.put("grip_r", model(origin, q, PickupTruckLayout.rightHand(rim, knob, reach)));
            anchors.put("wheel_hub", model(origin, q, PickupTruckLayout.HUB));
            anchors.put("wheel_axis", model(origin, q, PickupTruckLayout.AXIS));
            anchors.put("wheel_rim", model(origin, q, PickupTruckLayout.grip(true, 0, 0)));
            anchors.put("shift_knob", model(origin, q, PickupTruckLayout.knob(lever.fore, lever.across)));
            anchors.put("transfer_knob", model(origin, q, PickupTruckLayout.transferKnob(transferLever.fore)));
            // the right hand on the ignition key's bow (on its way in, or in the cylinder turning: CabStowage, about +Z)
            double[] key = keyAt(partialTick);
            double turn = Math.toRadians(keyInserting && !keyInIgnition() ? 0 : keyTurn(partialTick));
            anchors.put("key_r", model(origin, q, key[0], key[1], key[2] + KEY_GRIP_OUT));
            anchors.put("key_axis", model(origin, q, key[0], key[1], key[2] + KEY_GRIP_OUT - 0.1));
            anchors.put("key_up", model(origin, q, key[0] - 0.1 * Math.sin(turn), key[1] + 0.1 * Math.cos(turn), key[2] + KEY_GRIP_OUT));
        }
        if (side != 0) {
            anchors.put("door_pull", model(origin, q, PickupTruckLayout.door(side, PickupTruckLayout.PULL, angle)));
            anchors.put("door_outer", model(origin, q, PickupTruckLayout.door(side, PickupTruckLayout.OUTER, angle)));
            double[] entry = PickupTruckLayout.ENTRY;
            anchors.put("entry", model(origin, q, side * entry[0], entry[1], entry[2]));
        }
        return seat;
    }

    /** A model-frame point (x right, y up, z back) to the world at this frame's origin and orientation. */
    private static Vec3 model(Vec3 origin, Quaternionf q, double x, double y, double z) {
        org.joml.Vector3f v = q.transform(new org.joml.Vector3f((float) x, (float) y, (float) z));
        return origin.add(v.x, v.y, v.z);
    }

    private static Vec3 model(Vec3 origin, Quaternionf q, double[] p) {
        return model(origin, q, p[0], p[1], p[2]);
    }

    /** How fast the truck moves (m/s, from its last tick's step). */
    public double speed() {
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
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

    /**
     * A rider moving over to this seat: how far it still is from it across the cab (m, + to the right), 0 once there. The
     * cab seats differ only across (PickupTruckLayout.SEATS).
     */
    private double slideOffset(int id, int seat, float partialTick) {
        int[] slide = slides.get(id);
        if (slide == null) return 0;
        double t = (tickCount - slide[1] + partialTick) / SLIDE_TICKS;
        if (t >= 1 || t < 0) {
            if (t >= 1) slides.remove(id);
            return 0;
        }
        double e = t * t * (3 - 2 * t);
        return (PickupTruckLayout.SEATS[slide[0]].r() - PickupTruckLayout.SEATS[seat].r()) * (1 - e);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        int seat = seatOf(passenger);
        lastSeat.remove(passenger.getId());
        slides.remove(passenger.getId());
        super.removePassenger(passenger);
        if (seat == PickupTruckLayout.DRIVER) localKeyHand = -1;
        if (level().isClientSide || seat < 0) return;
        entityData.set(SEAT[seat], -1);
        // the driver takes the key out and along (a full grid leaves it in the ignition; design doc section 9); not on the
        // way out of the game: the player and the truck are already saved, the key stays in. Not with the engine running:
        // it idles on with the key at RUN (user 2026-10-21)
        if (seat == PickupTruckLayout.DRIVER && keyInIgnition() && !engineRunning() && passenger instanceof ServerPlayer player
                && player.isAlive() && !player.hasDisconnected()) giveKey(player);
        if (seat == PickupTruckLayout.DRIVER) {
            setWheelHeld(false);
            entityData.set(KEY_HAND, HAND_OFF);
        }
        leftId = passenger.getId();
        leftSeat = seat;
    }

    /**
     * Out of the cab (through the own door the rider opened, VehicleDriving.Keys#input): they stand outside it, else the other side's (it opens), else in
     * the bed, else on the roof. Off a bed seat: on the bed floor beside it, else over the side.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 spot = dismountSpot(passenger);
        if (passenger instanceof ServerPlayer) {
            exitId = passenger.getId();
            exitSpot = spot;
        }
        return spot;
    }

    private Vec3 dismountSpot(LivingEntity passenger) {
        int seat = passenger.getId() == leftId ? leftSeat : -1;
        Seat s = seat >= 0 ? PickupTruckLayout.SEATS[seat] : PickupTruckLayout.SEATS[PickupTruckLayout.DRIVER];
        double side = s.right() ? 1 : -1;
        if (s.cab()) {
            for (double sd : new double[]{side, -side}) {
                Vec3 out = ground(passenger, point(PickupTruckLayout.EXIT_F, sd * PickupTruckLayout.EXIT_R, 0));
                if (out != null) {
                    int door = sd > 0 ? 1 : 0;
                    setDoorOpen(sd > 0, true);
                    return out;
                }
            }
            Vec3 bedFloor = onTruck(passenger, point(-1.30, 0, 0.775));
            if (bedFloor != null) return bedFloor;
            return point(0.10, 0, 1.85);   // the roof
        }
        Vec3 floor = onTruck(passenger, point(s.f(), side * 0.25, 0.775));
        if (floor != null) return floor;
        Vec3 out = ground(passenger, point(s.f() - 0.5, side * 1.40, 0));
        return out != null ? out : point(s.f(), side * 0.25, 0.775);
    }

    /**
     * A standing spot on the ground near `at` (its block floor a little above or below the truck's), or null. The floors
     * under all four corners of the standing box count, lowest first: on a road's cross slope the corners stand on
     * different road_surface layer heights, and the floor under the middle alone left the box in the higher neighbour
     * (user 2026-10-16: out of the driver's seat by the right door).
     */
    @Nullable
    private Vec3 ground(LivingEntity passenger, Vec3 at) {
        double half = passenger.getBbWidth() / 2;
        java.util.TreeSet<Double> floors = new java.util.TreeSet<>();
        for (double dx : new double[]{-half, half})
            for (double dz : new double[]{-half, half}) {
                BlockPos column = BlockPos.containing(at.x + dx, at.y + 0.5, at.z + dz);
                for (BlockPos pos : new BlockPos[]{column, column.below(), column.above()}) {
                    double floor = level().getBlockFloorHeight(pos);
                    if (DismountHelper.isBlockFloorValid(floor)) floors.add(pos.getY() + floor);
                }
            }
        for (double y : floors) {
            Vec3 spot = new Vec3(at.x, y, at.z);
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

    /**
     * A creative player's sneaking hit removes the truck (its cargo drops; test cleanup); any other punch only sounds on
     * the metal; nothing else harms it in V0 (damage: P2).
     */
    boolean hurtFrom(DamageSource source) {
        if (isRemoved()) return false;
        boolean remove = source.isCreativePlayer() && source.getEntity() != null && source.getEntity().isShiftKeyDown();
        if (!remove) {
            if (!level().isClientSide && source.getDirectEntity() instanceof Player)
                level().playSound(null, getX(), getY() + 0.9, getZ(), SoundEvents.METAL_HIT, SoundSource.NEUTRAL, 1.0F, 0.7F);
            return false;
        }
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
        entityData.define(LAMPS, (byte) 0);
        entityData.define(WIPER, (byte) WiperSystem.OFF);
        entityData.define(CLIMATE, (byte) CabClimate.OFF);
        entityData.define(KEY, (byte) CabStowage.KeySpot.IGNITION.ordinal());
        entityData.define(STOWAGE, (byte) 0);
        entityData.define(WHEEL, false);
        entityData.define(KEY_HAND, HAND_OFF);
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
        entityData.set(LAMPS, (byte) ((tag.getBoolean("Headlights") ? LAMP_HEAD : 0) | (tag.getBoolean("HighBeam") ? LAMP_HIGH : 0)
                | (tag.getByte("TurnSignal") < 0 ? TURN_L : tag.getByte("TurnSignal") > 0 ? TURN_R : 0) | (tag.getBoolean("Hazard") ? HAZARD : 0)));
        entityData.set(WIPER, (byte) Math.floorMod(tag.getByte("Wipers"), WiperSystem.MODES));
        entityData.set(CLIMATE, (byte) Math.floorMod(tag.getByte("Climate"), CabClimate.MODES));
        cabAir = tag.contains("CabAir") ? tag.getDouble("CabAir") : Double.NaN;
        // a truck saved before keys (2026-10-21) keeps its key in the ignition
        CabStowage.KeySpot spot = tag.contains("Key") ? CabStowage.KeySpot.byName(tag.getString("Key")) : null;
        setKeySpot(spot != null ? spot : CabStowage.KeySpot.IGNITION);
        entityData.set(STOWAGE, tag.getByte("Stowage"));
        ListTag seats = tag.getList("Seats", Tag.TAG_COMPOUND);
        for (int i = 0; i < seats.size(); i++) {
            CompoundTag seat = seats.getCompound(i);
            int index = seat.getInt("Seat");
            if (index >= 0 && index < SEAT.length && seat.hasUUID("Rider")) savedSeats[index] = seat.getUUID("Rider");
        }
        cargo = GridStorage.load(CARGO_LAYOUT, tag.getList("Cargo", Tag.TAG_COMPOUND), false);
        cargoChanged();
        // a reloaded truck shows its parts where they were left, without swinging
        hood = hoodO = hoodOpen() ? HOOD_OPEN : 0.0F;
        tailgate = tailgateO = tailgateOpen() ? TAILGATE_OPEN : 0.0F;
        doorL = doorLO = doorOpen(false) ? DOOR_OPEN : 0.0F;
        doorR = doorRO = doorOpen(true) ? DOOR_OPEN : 0.0F;
        filler = fillerO = fillerOpen() ? FILLER_OPEN : 0.0F;
        visorL = visorLO = compartmentOpen(CabStowage.Compartment.VISOR_L) ? CabStowage.VISOR_OPEN : 0.0F;
        visorR = visorRO = compartmentOpen(CabStowage.Compartment.VISOR_R) ? CabStowage.VISOR_OPEN : 0.0F;
        glovebox = gloveboxO = compartmentOpen(CabStowage.Compartment.GLOVEBOX) ? CabStowage.GLOVEBOX_OPEN : 0.0F;
        ashtray = ashtrayO = compartmentOpen(CabStowage.Compartment.ASHTRAY) ? 1.0F : 0.0F;
        savedGear = Mth.clamp(tag.getInt("Gear"), -1, 5);
        savedFour = tag.getBoolean("FourWheel");
        savedLow = tag.getBoolean("LowRange");
        savedCoolant = tag.contains("Coolant") ? tag.getFloat("Coolant") : 20;
        fuelBurnt = tag.getDouble("FuelBurnt");
        ListTag q = tag.getList("Orientation", Tag.TAG_FLOAT);
        if (q.size() == 4) {
            orient.set(q.getFloat(0), q.getFloat(1), q.getFloat(2), q.getFloat(3));
            oriented = orient.lengthSquared() > 0.5;
            if (oriented) orient.normalize();
            orientO.set(orient);
        }
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
        tag.putBoolean("Headlights", headlights());
        tag.putBoolean("HighBeam", (lamps() & LAMP_HIGH) != 0);
        tag.putByte("TurnSignal", (byte) turnSignal());
        tag.putBoolean("Hazard", hazard());
        tag.putByte("Wipers", (byte) wiperMode());
        tag.putByte("Climate", (byte) climateMode());
        tag.putString("Key", keySpot().name());
        tag.putByte("Stowage", entityData.get(STOWAGE));
        if (!Double.isNaN(cabAir)) tag.putDouble("CabAir", cabAir);
        ListTag seats = new ListTag();
        for (Entity p : getPassengers()) {
            int index = seatOf(p);
            if (index < 0) continue;
            CompoundTag seat = new CompoundTag();
            seat.putInt("Seat", index);
            seat.putUUID("Rider", p.getUUID());
            seats.add(seat);
        }
        if (!seats.isEmpty()) tag.put("Seats", seats);
        ListTag grid = cargo.save();
        if (!grid.isEmpty()) tag.put("Cargo", grid);
        Quaterniond q = orient();
        tag.put("Orientation", newFloatList((float) q.x, (float) q.y, (float) q.z, (float) q.w));
        tag.putInt("Gear", gear());
        tag.putBoolean("FourWheel", fourWheel());
        tag.putBoolean("LowRange", lowRange());
        tag.putFloat("Coolant", physics != null ? (float) physics.drivetrain.coolant : savedCoolant);
        tag.putDouble("FuelBurnt", fuelBurnt);
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
