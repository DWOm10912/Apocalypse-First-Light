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
 * gets out as before. Driving keys of their own, active only in the driver's seat ({@link #DRIVING}): shift up (R) and
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
    private static final KeyMapping[] KEYS = {SHIFT_UP, SHIFT_DOWN, TRANSFER, IGNITION};
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

    private static void sync(VehiclePackets.Sync sync) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(sync.entity()) instanceof PickupTruckEntity truck) truck.acceptSync(sync.state(), sync.correct());
    }

    /** Forge bus: the driving keys' presses, before the game and other mods read theirs this tick. */
    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {
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
            if (!driving) {
                shifts = 0;
                transfer = false;
                return;
            }
            // the same keys bound to anything else (reload, the off-hand swap, ...) do nothing while driving
            for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
                if (other == SHIFT_UP || other == SHIFT_DOWN || other == TRANSFER || other == IGNITION) continue;
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
