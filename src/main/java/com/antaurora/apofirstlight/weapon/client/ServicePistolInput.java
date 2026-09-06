package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.weapon.ServicePistolItem;
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
public final class ServicePistolInput {
    private static final KeyMapping RELOAD = new KeyMapping("key.apocalypse_firstlight.reload",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
            "key.categories.apocalypse_firstlight");
    private static boolean attackHeld;
    private static boolean reloadHeld;

    private ServicePistolInput() {}

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(RELOAD); }
    }

    private static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null && mc.isWindowActive()
                && !mc.player.isSpectator() && mc.player.isAlive()
                && mc.player.getMainHandItem().getItem() instanceof ServicePistolItem;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || !ready(mc)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!attackHeld) AflNetwork.requestServicePistol(false, mc.player.getInventory().selected);
        attackHeld = true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean reloadClick = false;
        while (RELOAD.consumeClick()) reloadClick = true;
        if (!ready(mc)) {
            attackHeld = mc.options.keyAttack.isDown();
            reloadHeld = RELOAD.isDown();
            return;
        }
        if (!mc.options.keyAttack.isDown()) attackHeld = false;
        if (reloadClick && !reloadHeld) AflNetwork.requestServicePistol(true, mc.player.getInventory().selected);
        reloadHeld = RELOAD.isDown();
    }
}
