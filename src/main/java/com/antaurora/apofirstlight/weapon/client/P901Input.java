package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.weapon.P901Item;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class P901Input {
    private static final KeyMapping RELOAD = new KeyMapping("key.apocalypse_firstlight.reload",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
            "key.categories.apocalypse_firstlight");
    private static boolean attackHeld;
    private static final KeyMapping INSPECT = new KeyMapping("key.apocalypse_firstlight.inspect",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.apocalypse_firstlight");
    private static boolean inspectHeld;
    private static boolean reloadHeld;

    private P901Input() {}

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(RELOAD); event.register(INSPECT); }
    }

    private static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null && mc.isWindowActive()
                && !mc.player.isSpectator() && mc.player.isAlive()
                && mc.player.getMainHandItem().getItem() instanceof com.antaurora.apofirstlight.weapon.NativeGunItem;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || !ready(mc)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!attackHeld) {
            NativeGunInspect.cancel();
            NativeGunRecoil.syncAimBeforeShot();
            long shotId=NativeShotVisualSnapshot.capture();
            AflNetwork.requestP901(false, mc.player.getInventory().selected,shotId);
        }
        attackHeld = true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean reloadClick = false;
        while (RELOAD.consumeClick()) reloadClick = true;
        boolean inspectClick = false;
        while (INSPECT.consumeClick()) inspectClick = true;
        if (!ready(mc)) {
            NativeGunInspect.tick(false);
            inspectHeld = INSPECT.isDown();
            attackHeld = mc.options.keyAttack.isDown();
            reloadHeld = RELOAD.isDown();
            return;
        }
        if (!mc.options.keyAttack.isDown()) attackHeld = false;
        if (reloadClick && !reloadHeld) {
            NativeGunInspect.cancel();
            NativeGunAds.reloadRequested();
            AflNetwork.requestP901(true, mc.player.getInventory().selected);
        }
        reloadHeld = RELOAD.isDown();
        if (inspectClick && !inspectHeld && !reloadClick && !mc.options.keyAttack.isDown()) NativeGunInspect.pressed();
        inspectHeld = INSPECT.isDown();
        NativeGunInspect.tick(true);
    }
}
