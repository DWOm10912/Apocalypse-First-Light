package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.client.Minecraft;
import software.bernie.geckolib.animatable.GeoItem;

/** Input intent only. Playback, duration, lock and sync remain in P901Actions/GeckoLib. */
public final class NativeGunInspect {
    private static boolean pending, requireUseRelease;
    private static int awaitingTrigger, slot;
    private static long id;
    private static long canceledId = Long.MAX_VALUE;
    private static Object player, level;

    static String action() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof NativeGunItem gun)) return "";
        var c = gun.getAnimatableInstanceCache().getManagerForId(GeoItem.getId(mc.player.getMainHandItem()))
                .getAnimationControllers().get("action");
        if (c == null || c.getTriggeredAnimation() == null) return "";
        // Include the queued trigger before the next render has selected its animation.
        var stages = c.getTriggeredAnimation().getAnimationStages();
        return stages.isEmpty() ? "" : stages.get(0).animationName();
    }

    static void pressed() {
        var mc = Minecraft.getInstance();
        if (pending || awaitingTrigger > 0 || !action().isEmpty()
                || !(mc.player.getMainHandItem().getItem() instanceof NativeGunItem gun)
                || gun.inspectClip() == null) return;
        id = GeoItem.getId(mc.player.getMainHandItem());
        if (id == Long.MAX_VALUE) return;
        slot = mc.player.getInventory().selected;
        player = mc.player; level = mc.level;
        pending = true; requireUseRelease = true;
        canceledId = Long.MAX_VALUE;
    }

    public static boolean blocksAds() { return pending || awaitingTrigger > 0 || requireUseRelease; }

    static void cancel() {
        var mc = Minecraft.getInstance();
        if ((pending || awaitingTrigger > 0 || "inspect".equals(action())) && mc.player != null) {
            long cancelId = pending || awaitingTrigger > 0 ? id : GeoItem.getId(mc.player.getMainHandItem());
            if (canceledId != cancelId) {
                AflNetwork.requestInspect(mc.player.getInventory().selected, cancelId, true);
                canceledId = cancelId;
            }
        }
        pending = false; awaitingTrigger = 0;
    }

    static void tick(boolean ready) {
        var mc = Minecraft.getInstance();
        if (!ready) {
            cancel();
            if (mc.player == null || !mc.options.keyUse.isDown()) requireUseRelease = false;
            return;
        }
        if ((pending || awaitingTrigger > 0) && (mc.player != player || mc.level != level
                || mc.player.getInventory().selected != slot || GeoItem.getId(mc.player.getMainHandItem()) != id)) cancel();
        String action = action();
        if (action.isEmpty()) canceledId = Long.MAX_VALUE;
        if (pending && !action.isEmpty()) cancel();
        if (pending && NativeGunAds.progress(0) == 0 && NativeGunAds.progress(1) == 0) {
            pending = false;
            awaitingTrigger = 40; // Network acknowledgement guard only, never animation duration.
            AflNetwork.requestInspect(slot, id, false);
        }
        if (awaitingTrigger > 0) {
            if (!action.isEmpty()) awaitingTrigger = 0;
            else if (--awaitingTrigger == 0) AflNetwork.requestInspect(slot, id, true);
        }
        if (!pending && awaitingTrigger == 0 && !"inspect".equals(action) && !mc.options.keyUse.isDown())
            requireUseRelease = false;
    }

    private NativeGunInspect() {}
}
