package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
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
 * gets out (only below PickupTruckEntity#GET_OUT_SPEED: faster, sneak does nothing and says "stop first"; any seat).
 * Driving keys of their own, active only in the driver's seat ({@link #DRIVING}): shift up (R) and
 * down (F), 2H / 4H (X), the ignition key held (G). Whatever else sits on those keys (reload, the off-hand swap) gets no
 * press while driving. Also the local player (carried on a moving truck by its own client) and the synced states
 * (VehiclePackets.Sync).
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
    private static final KeyMapping[] KEYS = {SHIFT_UP, SHIFT_DOWN, TRANSFER, IGNITION, LIGHTS, HORN, SIGNAL_LEFT, SIGNAL_RIGHT, HAZARD};
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

    private VehicleDriving() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        PickupTruckEntity.clientInput = VehicleDriving::input;
        PickupTruckEntity.localPlayer = () -> Minecraft.getInstance().player;
        VehiclePackets.clientSync = VehicleDriving::sync;
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
        boolean fuel = player.getVehicle() instanceof PickupTruckEntity truck && truck.fuel() > 0;
        return new VehicleInput(in.up ? 1 : 0, in.down ? 1 : 0, (in.right ? 1 : 0) - (in.left ? 1 : 0), in.jumping, shift, lever,
                mc.screen == null && IGNITION.isDown(), fuel, true);
    }

    /** The driver's light switch, high beam, horn, turn signals or hazards to the server. */
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

        private Keys() {
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
                return;
            }
            Input input = event.getInput();
            boolean pressed = input.shiftKeyDown;
            if (pressed && truck.speed() > PickupTruckEntity.GET_OUT_SPEED) {
                input.shiftKeyDown = false;
                if (!sneakHeld) Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("hint.apocalypse_firstlight.pickup_t15.stop_first"), false);
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
            while (IGNITION.consumeClick()) {
            }
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
            // the same keys bound to anything else (reload, the off-hand swap, ...) do nothing while driving
            for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
                if (java.util.Arrays.asList(KEYS).contains(other)) continue;
                for (KeyMapping mine : KEYS)
                    if (!other.isUnbound() && other.getKey().equals(mine.getKey())) {
                        while (other.consumeClick()) {
                        }
                        break;
                    }
            }
        }
    }
}
