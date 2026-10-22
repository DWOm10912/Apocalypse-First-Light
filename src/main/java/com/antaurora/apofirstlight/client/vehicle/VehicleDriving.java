package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import com.antaurora.apofirstlight.vehicle.CabStowage;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.VehiclePackets;
import com.antaurora.apofirstlight.vehicle.physics.VehicleInput;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.GrassColor;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Vehicle physics V0-b, the client's hooks (docs/vehicles/first_pickup_v0_design.md section 4, package 2): the driver's
 * pedals are the movement keys (forward: throttle; back: brake; left / right: steer), jump is the handbrake and sneak
 * gets out (only below PickupTruckEntity#GET_OUT_SPEED: faster, sneak does nothing and says "stop first"; any seat; from a
 * cab seat only with its own door opened first, PickupTruckEntity#mayGetOut, else "open the door first").
 * Driving keys of their own, active only in the driver's seat ({@link #DRIVING}): shift up (R) and
 * down (F), 2H / 4H (X), the ignition (G held, user 2026-10-21: one go, {@link Keys#ignitionClip}: the camera turns to the
 * keyhole, the right hand takes the key from the inventory, puts it in and turns it to ON, then START until the engine
 * catches; with the key in it only reaches and turns; with the engine running it turns it back to OFF; let go early, it
 * stops; without the key it says so; docs/vehicles/t15_hotwire_v1_design.md section 9). A click on the keyhole puts the key
 * in or pulls it out the same way, and getting out with the engine off pulls it first (Keys#startClip, user 2026-10-21).
 * Whatever else sits on those keys (reload, the off-hand swap) gets no
 * press while driving. The pedals, steering, gears and the transfer lever only with the hands on the wheel
 * (PickupTruckEntity#wheelHeld, user 2026-10-21): the driver takes hold of it with a click on it; holding it, a click lets
 * go and nothing else is used or hit; with the hands free a standing truck keeps its handbrake on. Also the local player
 * (carried on a moving truck by its own client) and the synced states (VehiclePackets.Sync).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VehicleDriving {
    /** In a truck's driver's seat with no screen open. */
    public static final IKeyConflictContext DRIVING = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.screen == null && mc.player != null && mc.player.getVehicle() instanceof PickupTruckEntity truck && truck.getControllingPassenger() == mc.player;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) { return other == this; }
    };
    private static final String CATEGORY = "key.categories.apocalypse_firstlight";
    public static final KeyMapping SHIFT_UP = new KeyMapping("key.apocalypse_firstlight.shift_up", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping SHIFT_DOWN = new KeyMapping("key.apocalypse_firstlight.shift_down", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);
    public static final KeyMapping TRANSFER = new KeyMapping("key.apocalypse_firstlight.transfer_case", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping IGNITION = new KeyMapping("key.apocalypse_firstlight.ignition", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    /**
     * The lights on one key, GTA-style (user 2026-10-16; design doc 8.10): a tap turns them on / off, a hold flips the high
     * beam (and turns the lights on with it).
     */
    public static final KeyMapping LIGHTS = new KeyMapping("key.apocalypse_firstlight.headlights", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    /** The horn, held (design doc 8.11). */
    public static final KeyMapping HORN = new KeyMapping("key.apocalypse_firstlight.horn", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);
    /** Turn signals and hazard lamps (design doc 8.12; user 2026-10-16: the arrow keys for now): a press on, again off. */
    public static final KeyMapping SIGNAL_LEFT = new KeyMapping("key.apocalypse_firstlight.signal_left", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY);
    public static final KeyMapping SIGNAL_RIGHT = new KeyMapping("key.apocalypse_firstlight.signal_right", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY);
    public static final KeyMapping HAZARD = new KeyMapping("key.apocalypse_firstlight.hazard", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, CATEGORY);
    /**
     * The wiper stalk (docs/vehicles/t15_windshield_rain_design.md; 2026-10-17, the user left the key to me: C, beside the
     * transfer case's X and free while driving): a press steps it on, off - intermittent - low - high - off.
     */
    public static final KeyMapping WIPERS = new KeyMapping("key.apocalypse_firstlight.wipers", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    /**
     * The heater panel (2026-10-18, design doc 8.19; V: free while driving, the weapon's V does nothing in the driver's
     * seat): a press steps it on, off - heat - cool - off.
     */
    public static final KeyMapping CLIMATE = new KeyMapping("key.apocalypse_firstlight.climate", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    /** The key hints at the top right on / off (user 2026-10-18: Caps Lock, to film without them; remembered in the client config). */
    /** The hints key works while driving and with a gun in the hand on foot (its key hints, weapon/client/NativeGunKeyHints). */
    private static final IKeyConflictContext HINTS_CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() { return DRIVING.isActive() || com.antaurora.apofirstlight.weapon.client.NativeGunKeyHints.active(); }

        @Override
        public boolean conflicts(IKeyConflictContext other) { return other == this || other == DRIVING || other == net.minecraftforge.client.settings.KeyConflictContext.IN_GAME; }
    };
    public static final KeyMapping HINTS = new KeyMapping("key.apocalypse_firstlight.driving_hints", HINTS_CONTEXT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_CAPS_LOCK, CATEGORY);
    private static final KeyMapping[] KEYS = {SHIFT_UP, SHIFT_DOWN, TRANSFER, IGNITION, LIGHTS, HORN, SIGNAL_LEFT, SIGNAL_RIGHT, HAZARD, WIPERS, CLIMATE, HINTS};
    /** Below this speed (m/s) a truck whose driver has let go of the wheel keeps the handbrake on. */
    private static final float FREE_HANDS_HANDBRAKE = 1.0F;
    /** The keys only the hands on the wheel work: the gear and transfer levers (free, they stay with the game: reload ...). */
    private static final KeyMapping[] WHEEL_KEYS = {SHIFT_UP, SHIFT_DOWN, TRANSFER};
    /** Ticks the light key is held before it counts as a hold (the high beam) instead of a tap. */
    private static final int HOLD_TICKS = 8;
    /** Ticks the light key has been held; -1 once this hold flipped the high beam (the release then does nothing). */
    private static int lightHeld;
    /** The horn key as last sent to the server. */
    private static boolean hornSent;
    /** Presses since the truck last asked (one gear step and one lever pull per tick at most). */
    private static int shifts;
    private static boolean transfer;
    /**
     * Each key's state last tick. Held, a key's clicks are the keyboard's auto-repeat (about every 30 ms after half a
     * second), not new presses: holding X kept turning the transfer case over with its sound each time (user 2026-10-17).
     */
    private static final boolean[] WAS_DOWN = new boolean[KEYS.length];
    /** The ignition clip's camera (Keys#ignitionClip). */
    private static final String IGNITION_CAMERA = "vehicle_ignition";
    private static final double IGNITION_IN = 0.3, IGNITION_OUT = 0.4;
    /** Ticks the clip stays after the engine caught or stopped (the key springs back, the hand lets go). */
    private static final int IGNITION_AFTER = 6;
    /** Ticks after the key should have gone in that the clip gives up (the server did not take it: not carried any more). */
    private static final int IGNITION_INSERT_WAIT = 20;
    /** Ticks the hand stays on the key once it is in (a click on the keyhole), before it goes back. */
    private static final int INSERT_HOLD = 3;
    /** The key clip: ticks since it started (-1: none), its countdown to the end (-1: running on). */
    private static int clipTicks = -1, clipEnd = -1;
    /** What the key clip does: turn the key (G held), only put it in, or pull it out (a click on the keyhole; getting out). */
    private enum KeyClip { IGNITION, INSERT, PULL }
    private static KeyClip clipMode = KeyClip.IGNITION;
    /** The pull was the first half of getting out (sneak with the engine off): the sneak goes through once it is done. */
    private static boolean exitAfterPull, exitNow;
    /** The clip brings the key along (it was not in) / switches the engine off (it was running). */
    private static boolean clipInserting, clipStop;
    /** The hand is on the key in the cylinder: the key held turns it (input()). */
    private static boolean clipReady;

    private VehicleDriving() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        PickupTruckEntity.clientInput = VehicleDriving::input;
        PickupTruckEntity.localPlayer = () -> Minecraft.getInstance().player;
        VehiclePackets.clientSync = VehicleDriving::sync;
        com.antaurora.apofirstlight.vehicle.VehicleSpawnerItem.clientOpen = (at, floor) -> Minecraft.getInstance().setScreen(new VehicleSpawnerScreen(at, floor));
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : KEYS) event.register(key);
    }

    /** Pressed plants take the grass colour of their place (block/FlattenedPlantBlock). */
    @SubscribeEvent
    public static void colours(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor(),
                AflBlocks.FLATTENED_PLANT.get());
    }

    private static VehicleInput input() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return VehicleInput.PARKED;
        Input in = player.input;
        int shift = Integer.signum(shifts);
        boolean lever = transfer;
        shifts = 0;
        transfer = false;
        PickupTruckEntity truck = player.getVehicle() instanceof PickupTruckEntity t ? t : null;
        boolean fuel = truck != null && truck.fuel() > 0, key = truck != null && truck.keyInIgnition();
        // the key turns only with the hand on it (the ignition clip, Keys#ignitionClip)
        boolean ignition = mc.screen == null && clipReady && IGNITION.isDown() && key;
        // hands off the wheel: no pedals, steering, gears or lever; standing (or nearly), the handbrake holds it
        if (truck == null || !truck.wheelHeld())
            return new VehicleInput(0, 0, 0, truck == null || truck.speed() < FREE_HANDS_HANDBRAKE, 0, false, ignition, fuel, true);
        return new VehicleInput(in.up ? 1 : 0, in.down ? 1 : 0, (in.right ? 1 : 0) - (in.left ? 1 : 0), in.jumping, shift, lever,
                ignition, fuel, true);
    }

    /** The driver's light switch, high beam, horn, turn signals, hazards or wipers to the server. */
    private static void lampSwitch(int action) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof PickupTruckEntity truck)
            com.antaurora.apofirstlight.network.AflNetwork.vehicleToServer(new VehiclePackets.Lamps(truck.getId(), action));
    }

    private static void sync(VehiclePackets.Sync sync) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(sync.entity()) instanceof PickupTruckEntity truck) truck.acceptSync(sync.state(), sync.correct());
    }

    /** Forge bus: the driving keys' presses, before the game and other mods read theirs this tick. */
    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
    public static final class Keys {
        /** The sneak key held last tick (the "stop first" message once per press). */
        private static boolean sneakHeld;
        /** The use key's press already took hold of the wheel or let go (its auto-repeat does nothing more until let up). */
        private static boolean useLatch;

        /**
         * The driver's clicks (user 2026-10-21): holding the wheel, a use click lets go (only that) and attack and pick
         * do nothing; a use click that takes hold of the wheel (PickupTruckEntity GRIP_WHEEL) latches until the key is up.
         */
        @SubscribeEvent
        public static void click(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !(mc.player.getVehicle() instanceof PickupTruckEntity truck) || truck.getControllingPassenger() != mc.player) return;
            if (truck.wheelHeld()) {
                event.setCanceled(true);
                event.setSwingHand(false);
                if (event.isUseItem() && !useLatch) {
                    useLatch = true;
                    lampSwitch(PickupTruckEntity.RELEASE_WHEEL);
                }
                return;
            }
            if (clipTicks >= 0) {
                // the hand is on the key: nothing else is used or hit meanwhile
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
            if (!event.isUseItem()) return;
            if (useLatch) {
                // the same press that just took hold or let go
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
            PickupTruckEntity.Offer offer = truck.actionFor(mc.player);
            if (offer != null && offer.action() == PickupTruckEntity.Action.GRIP_WHEEL) useLatch = true;
            // the keyhole (user 2026-10-21: not sitting still): the right hand puts the key in or pulls it out, as with G
            boolean put = offer != null && offer.action() == PickupTruckEntity.Action.PUT_KEY_IN;
            if (put || offer != null && offer.action() == PickupTruckEntity.Action.TAKE_KEY && truck.keyInIgnition()) {
                event.setCanceled(true);
                event.setSwingHand(false);
                useLatch = true;
                startClip(truck, put ? KeyClip.INSERT : KeyClip.PULL);
            }
        }

        /** Holding the wheel, nothing is held up in first person: the hands are on the wheel (the seated body draws them). */
        @SubscribeEvent
        public static void hands(net.minecraftforge.client.event.RenderHandEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && PickupTruckEntity.driving(mc.player)) event.setCanceled(true);
        }

        private Keys() {
        }

        /**
         * The key clips, one go each (user 2026-10-21): the camera turns to the keyhole (director, the look locked, no
         * first-person hands: the seated body's right hand does it, SeatPose key_r) and the hand goes for the key.
         * <ul>
         * <li>G held (IGNITION): the key from the inventory, pocket to cylinder (PickupTruckEntity#keyCarry; the server
         * takes it at the end of the path), then the key held turns it: ON, preheat, START until the engine catches
         * (vehicle/physics/Drivetrain). With the key in it only reaches and turns; with the engine running, the turn
         * switches it off. Letting go of G, the engine catching or stopping, or leaving the seat ends it; the key stays in.</li>
         * <li>A click on the keyhole with the key on you (INSERT): the same way in, a moment on the key, back.</li>
         * <li>A click on the keyhole with the key in, or sneak to get out with it in and the engine off (PULL): the hand
         * draws it out and back to the pocket (PickupTruckEntity#KEY_PULL_AT..KEY_PULL_END; the server gives it as it
         * clears the cylinder), then, getting out, the sneak goes through.</li>
         * </ul>
         */
        private static void ignitionClip(boolean press, boolean driving) {
            Minecraft mc = Minecraft.getInstance();
            PickupTruckEntity truck = driving && mc.player.getVehicle() instanceof PickupTruckEntity t ? t : null;
            if (clipTicks < 0) {
                clipReady = false;
                if (!press || truck == null) return;
                if (!truck.keyInIgnition() && !com.antaurora.apofirstlight.vehicle.VehicleKeyItem.carries(mc.player, truck.getUUID())) {
                    // no key in and none carried (the engine never cranks without it)
                    mc.gui.setOverlayMessage(Component.translatable("hint.apocalypse_firstlight.pickup_t15.no_key"), false);
                    return;
                }
                startClip(truck, KeyClip.IGNITION);
                return;
            }
            if (truck == null) {
                // out of the seat, or a screen opened: the hand goes back (still seated, the others see it too)
                endClip(mc.player != null && mc.player.getVehicle() instanceof PickupTruckEntity t ? t : null);
                exitAfterPull = false;
                return;
            }
            clipTicks++;
            if (clipMode == KeyClip.PULL) {
                if (clipTicks == PickupTruckEntity.KEY_PULL_OUT && truck.keyInIgnition()) lampSwitch(PickupTruckEntity.TAKE_KEY_OUT);
                if (clipTicks > PickupTruckEntity.KEY_PULL_END) {
                    endClip(truck);
                    exitNow = exitAfterPull;
                    exitAfterPull = false;
                }
                return;
            }
            if (clipInserting && clipTicks == PickupTruckEntity.KEY_PATH_IN && !truck.keyInIgnition()) lampSwitch(PickupTruckEntity.INSERT_KEY);
            boolean failed = clipInserting && !truck.keyInIgnition() && clipTicks > PickupTruckEntity.KEY_PATH_IN + IGNITION_INSERT_WAIT;
            if (clipMode == KeyClip.INSERT) {
                if (failed || truck.keyInIgnition() && clipTicks >= PickupTruckEntity.KEY_PATH_IN + INSERT_HOLD) endClip(truck);
                return;
            }
            clipReady = truck.keyInIgnition() && truck.keyReach(1F) >= 0.95F && (!clipInserting || clipTicks >= PickupTruckEntity.KEY_PATH_IN + 2);
            if (clipEnd < 0 && (clipStop ? !truck.engineRunning() : truck.engineRunning())) clipEnd = IGNITION_AFTER;
            if (!IGNITION.isDown() || failed || clipEnd == 0) endClip(truck);
            else if (clipEnd > 0) clipEnd--;
        }

        /** A key clip starts: the hand goes (the others see it: Lamps KEY_HAND_ON / KEY_HAND_PULL) and the camera turns. */
        private static void startClip(PickupTruckEntity truck, KeyClip mode) {
            clipTicks = 0;
            clipEnd = -1;
            clipMode = mode;
            clipReady = false;
            clipInserting = mode != KeyClip.PULL && !truck.keyInIgnition();
            clipStop = mode == KeyClip.IGNITION && truck.engineRunning();
            boolean pull = mode == KeyClip.PULL;
            truck.setLocalKeyHand(pull ? PickupTruckEntity.HAND_PULL : PickupTruckEntity.HAND_REACH);
            lampSwitch(pull ? PickupTruckEntity.KEY_HAND_PULL : PickupTruckEntity.KEY_HAND_ON);
            if (!CameraDirector.holding(IGNITION_CAMERA))
                CameraDirector.open(new CameraDirector.Request(IGNITION_CAMERA, CameraDirector.PRIORITY_MACHINE, Keys::keyholeShot,
                        CameraDirector.Clock.GAME, IGNITION_IN, IGNITION_OUT).locks(CameraDirector.Lock.LOOK).hud(CameraDirector.Hud.NO_HANDS));
        }

        private static void endClip(PickupTruckEntity truck) {
            clipTicks = -1;
            clipEnd = -1;
            clipReady = false;
            if (truck != null) {
                truck.setLocalKeyHand(PickupTruckEntity.HAND_OFF);
                lampSwitch(PickupTruckEntity.KEY_HAND_OFF);
            }
            CameraDirector.close(IGNITION_CAMERA);
        }

        /** The ignition clip's view: from where the eye is, at the keyhole (the cylinder, dash right of the wheel). */
        private static CameraPose keyholeShot(Camera camera, CameraPose current, float partialTick) {
            if (!(Minecraft.getInstance().player.getVehicle() instanceof PickupTruckEntity truck)) return null;
            float[] k = CabStowage.KEY_PIVOT;
            Vec3 d = truck.framePoint(-k[2], k[0], k[1], partialTick).subtract(current.position());
            double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) Math.toDegrees(Math.atan2(-d.y, horizontal));
            return new CameraPose(current.position(), yaw, pitch);
        }

        /**
         * A new press of a step key this tick (gears, transfer case, signals): its clicks count only if it was up last
         * tick, so holding it does one step, not one per auto-repeat. Its clicks are drained either way.
         */
        private static boolean pressed(KeyMapping key) {
            int i = java.util.Arrays.asList(KEYS).indexOf(key);
            boolean clicked = false;
            while (key.consumeClick()) clicked = true;
            boolean was = WAS_DOWN[i];
            WAS_DOWN[i] = key.isDown();
            return clicked && !was;
        }

        /** On a moving truck the sneak key does not get anyone out: it does not reach the game (nor the server). */
        @SubscribeEvent
        public static void input(MovementInputUpdateEvent event) {
            if (!(event.getEntity() instanceof LocalPlayer player) || !(player.getVehicle() instanceof PickupTruckEntity truck)) {
                sneakHeld = false;
                exitNow = false;
                return;
            }
            Input input = event.getInput();
            if (exitNow) {
                // the key is out: on out of the seat, as the sneak that pulled it asked
                exitNow = false;
                input.shiftKeyDown = true;
                sneakHeld = true;
                return;
            }
            boolean pressed = input.shiftKeyDown;
            if (pressed && clipTicks >= 0) {
                // the hand on the key: nobody gets out halfway (a pull for getting out goes on by itself)
                input.shiftKeyDown = false;
            } else if (pressed && truck.speed() > PickupTruckEntity.GET_OUT_SPEED) {
                input.shiftKeyDown = false;
                if (!sneakHeld) Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("hint.apocalypse_firstlight.pickup_t15.stop_first"), false);
            } else if (pressed && !truck.mayGetOut(player)) {
                // a cab seat with its door shut (user 2026-10-21): open it first (a stray sneak does not drop anyone out)
                input.shiftKeyDown = false;
                if (!sneakHeld) Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("hint.apocalypse_firstlight.pickup_t15.open_door_first"), false);
            } else if (pressed && !sneakHeld && truck.getControllingPassenger() == player && truck.keyInIgnition() && !truck.engineRunning()) {
                // the driver getting out with the engine off: the key comes out first, then out of the seat (user 2026-10-21)
                input.shiftKeyDown = false;
                startClip(truck, KeyClip.PULL);
                exitAfterPull = true;
            }
            sneakHeld = pressed;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            boolean driving = DRIVING.isActive();
            if (pressed(SHIFT_UP) && driving) shifts++;
            if (pressed(SHIFT_DOWN) && driving) shifts--;
            if (pressed(TRANSFER) && driving) transfer = true;
            ignitionClip(pressed(IGNITION) && driving, driving);
            boolean lightTap = false;
            while (LIGHTS.consumeClick()) lightTap = true;
            if (driving && LIGHTS.isDown()) {
                if (lightHeld >= 0 && ++lightHeld >= HOLD_TICKS) {
                    lampSwitch(PickupTruckEntity.SWITCH_HIGH);
                    lightHeld = -1;
                }
            } else {
                // released before the hold time (or pressed and let go within one tick): a tap
                if (driving && (lightHeld > 0 || lightHeld == 0 && lightTap)) lampSwitch(PickupTruckEntity.SWITCH_LIGHTS);
                lightHeld = 0;
            }
            if (pressed(SIGNAL_LEFT) && driving) lampSwitch(PickupTruckEntity.SIGNAL_LEFT);
            if (pressed(SIGNAL_RIGHT) && driving) lampSwitch(PickupTruckEntity.SIGNAL_RIGHT);
            if (pressed(HAZARD) && driving) lampSwitch(PickupTruckEntity.SWITCH_HAZARD);
            if (pressed(WIPERS) && driving) lampSwitch(PickupTruckEntity.SWITCH_WIPER);
            if (pressed(CLIMATE) && driving) lampSwitch(PickupTruckEntity.SWITCH_CLIMATE);
            if (pressed(HINTS) && (driving || com.antaurora.apofirstlight.weapon.client.NativeGunKeyHints.active()))
                com.antaurora.apofirstlight.client.FirstPersonBodyConfig.toggleKeyHints();
            while (HORN.consumeClick()) {
            }
            boolean horn = driving && HORN.isDown();
            if (horn != hornSent) {
                lampSwitch(horn ? PickupTruckEntity.HORN_ON : PickupTruckEntity.HORN_OFF);
                hornSent = horn;
            }
            if (!driving) {
                shifts = 0;
                transfer = false;
                return;
            }
            // the same keys bound to anything else (reload, the off-hand swap, ...) do nothing while driving (the levers'
            // keys only while the hands are on the wheel)
            if (!(Minecraft.getInstance().options.keyUse.isDown())) useLatch = false;
            boolean wheel = Minecraft.getInstance().player.getVehicle() instanceof PickupTruckEntity t && t.wheelHeld();
            for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
                if (java.util.Arrays.asList(KEYS).contains(other)) continue;
                for (KeyMapping mine : KEYS)
                    if (!other.isUnbound() && other.getKey().equals(mine.getKey()) && (wheel || !java.util.Arrays.asList(WHEEL_KEYS).contains(mine))) {
                        while (other.consumeClick()) {
                        }
                        break;
                    }
            }
        }
    }
}
