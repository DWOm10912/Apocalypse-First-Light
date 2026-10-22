package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
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
public final class NativeGunInput {
    public static final KeyMapping FIELD_ATTACHMENT = new KeyMapping("key.apocalypse_firstlight.field_attachment",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z,
            "key.categories.apocalypse_firstlight");
    /** Resets the field attachment / maintenance bench view (was a hard-coded R); GUI context, so it never clashes with reload. */
    public static final KeyMapping RESET_INSPECTION = new KeyMapping("key.apocalypse_firstlight.reset_inspection",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
            "key.categories.apocalypse_firstlight");
    public static boolean firing(){return triggerSent||attackHeld;}
    /** The reload key, for the gun HUD's empty-magazine prompt and the key hints (NativeGunKeyHints). */
    public static KeyMapping reloadKey(){return RELOAD;}
    public static KeyMapping inspectKey(){return INSPECT;}
    public static KeyMapping fireModeKey(){return FIRE_MODE;}
    private static final KeyMapping RELOAD = new KeyMapping("key.apocalypse_firstlight.reload",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
            "key.categories.apocalypse_firstlight");
    private static boolean attackHeld;
    private static boolean triggerSent;
    private static int triggerSlot;
    private static long triggerGun;
    private static final KeyMapping FIRE_MODE = new KeyMapping("key.apocalypse_firstlight.fire_mode",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B,
            "key.categories.apocalypse_firstlight");
    private static final KeyMapping INSPECT = new KeyMapping("key.apocalypse_firstlight.inspect",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.apocalypse_firstlight");
    private static boolean inspectHeld;
    private static boolean reloadHeld;
    /**
     * Ticks the reload key has been down since its press on a gun, -1 when there is none to answer (user 2026-10-22,
     * docs/native_guns/magazine_system_v1.md 3): let go before {@link #RELOAD_HOLD_TICKS} it is a tap (the magazine out,
     * or one in when none is fitted), held that long the reload proper (another magazine in).
     */
    private static int reloadTicks = -1;
    private static final int RELOAD_HOLD_TICKS = 6;

    private NativeGunInput() {}

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(RELOAD); event.register(INSPECT); event.register(FIRE_MODE); event.register(FIELD_ATTACHMENT); event.register(RESET_INSPECTION); }
    }

    private static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null && mc.isWindowActive()
                && !mc.player.isSpectator() && mc.player.isAlive()
                && mc.player.getMainHandItem().getItem() instanceof com.antaurora.apofirstlight.weapon.NativeGunItem;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if(FieldAttachmentViewState.isActive()){
            event.setCanceled(true);event.setSwingHand(false);return;
        }
        if (!event.isAttack() || !ready(mc)) return;
        // orbit camera on foot: no shot until the body has turned to the camera's view (it is fired then)
        if (com.antaurora.apofirstlight.client.camera.OrbitCamera.holdClick(event)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!attackHeld) {
            NativeGunInspect.cancel();
            NativeGunRecoil.syncAimBeforeShot();
            long shotId=NativeShotVisualSnapshot.capture();
            triggerSlot=mc.player.getInventory().selected;
            triggerGun=software.bernie.geckolib.animatable.GeoItem.getId(mc.player.getMainHandItem());
            AflNetwork.nativeTrigger(1,triggerSlot,shotId,triggerGun,NativeGunAds.progress(1) >= .95F);
            triggerSent=true;
        }
        attackHeld = true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if(triggerSent && (!ready(mc)||!mc.options.keyAttack.isDown()
                ||mc.player.getInventory().selected!=triggerSlot
                ||software.bernie.geckolib.animatable.GeoItem.getId(mc.player.getMainHandItem())!=triggerGun))stopTrigger();
        boolean fieldClick=false;
        while(FIELD_ATTACHMENT.consumeClick())fieldClick=true;
        if(fieldClick&&ready(mc))FieldAttachmentViewState.open();
        boolean modeClick=false;
        while(FIRE_MODE.consumeClick())modeClick=true;
        boolean reloadClick = false;
        while (RELOAD.consumeClick()) reloadClick = true;
        boolean inspectClick = false;
        while (INSPECT.consumeClick()) inspectClick = true;
        if (!ready(mc)) {
            NativeGunInspect.tick(false);
            inspectHeld = INSPECT.isDown();
            attackHeld = mc.options.keyAttack.isDown();
            reloadHeld = RELOAD.isDown();
            reloadTicks = -1;
            return;
        }
        if (!mc.options.keyAttack.isDown()) attackHeld = false;
        if (reloadClick && !reloadHeld) reloadTicks = 0;
        if (reloadTicks >= 0) {
            if (!RELOAD.isDown()) reload(mc, com.antaurora.apofirstlight.weapon.NativeGunActions.RELOAD_TAP);
            else if (++reloadTicks >= RELOAD_HOLD_TICKS) reload(mc, 0);
        }
        reloadHeld = RELOAD.isDown();
        if(modeClick&&!attackHeld&&!reloadClick
                &&((com.antaurora.apofirstlight.weapon.NativeGunItem)mc.player.getMainHandItem().getItem()).definition().fire().modes().size()>1)
            AflNetwork.nativeTrigger(2,mc.player.getInventory().selected,0,
                    software.bernie.geckolib.animatable.GeoItem.getId(mc.player.getMainHandItem()));
        if (inspectClick && !inspectHeld && !reloadClick && !mc.options.keyAttack.isDown()) NativeGunInspect.pressed();
        inspectHeld = INSPECT.isDown();
        NativeGunInspect.tick(true);
    }
    /** The reload key's request, tapped or held (NativeGunActions#RELOAD_TAP, carried as the request's shot id). */
    private static void reload(Minecraft mc, long mode) {
        reloadTicks = -1;
        stopTrigger();
        NativeGunInspect.cancel();
        NativeGunAds.reloadRequested();
        AflNetwork.requestNativeGun(true, mc.player.getInventory().selected, mode);
    }
    private static void stopTrigger(){
        if(triggerSent){AflNetwork.nativeTrigger(0,triggerSlot,0,triggerGun);triggerSent=false;}
    }
}
