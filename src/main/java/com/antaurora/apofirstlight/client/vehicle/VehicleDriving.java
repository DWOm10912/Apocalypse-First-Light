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
    /** The light switch and the high beam (design doc 8.10). */
    public static final KeyMapping LIGHTS = new KeyMapping("key.apocalypse_firstlight.headlights", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping HIGH_BEAM = new KeyMapping("key.apocalypse_firstlight.high_beam", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    /** The horn, held (design doc 8.11). */
    public static final KeyMapping HORN = new KeyMapping("key.apocalypse_firstlight.horn", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);
    private static final KeyMapping[] KEYS = {SHIFT_UP, SHIFT_DOWN, TRANSFER, IGNITION, LIGHTS, HIGH_BEAM, HORN};
    /** The horn key as last sent to the server. */
    private static boolean hornSent;
    /** Presses since the truck last asked (one gear step and one lever pull per tick at most). */
    private static int shifts;
    private static boolean transfer;

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

    /** The driver's light switch or high beam to the server. */
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
            while (SHIFT_UP.consumeClick()) if (driving) shifts++;
            while (SHIFT_DOWN.consumeClick()) if (driving) shifts--;
            while (TRANSFER.consumeClick()) if (driving) transfer = true;
            while (IGNITION.consumeClick()) {
            }
            while (LIGHTS.consumeClick()) if (driving) lampSwitch(PickupTruckEntity.SWITCH_LIGHTS);
            while (HIGH_BEAM.consumeClick()) if (driving) lampSwitch(PickupTruckEntity.SWITCH_HIGH);
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
