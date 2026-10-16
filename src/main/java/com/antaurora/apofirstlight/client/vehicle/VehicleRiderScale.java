package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Interim (docs/vehicles/first_pickup_v0_design.md 8.2): a player on a T15 seat is drawn PickupTruckLayout.riderScale
 * smaller around its eyes, so the oversized vanilla model fits the real-size cab with the head at the camera. Agreed with
 * D 2026-10-16; it goes when D's seated pose (SeatedRiderHost) lands. Every pushed Pre is popped in its Post (the shadow
 * pass fires the same pair); a Pre cancelled before this runs is skipped, so no Post is waited for.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
final class VehicleRiderScale {
    private static final ThreadLocal<Deque<Player>> SCALED = ThreadLocal.withInitial(ArrayDeque::new);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof PickupTruckEntity)) return;
        float s = PickupTruckLayout.riderScale;
        if (s == 1f) return;
        float eye = player.getEyeHeight();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, eye, 0);
        pose.scale(s, s, s);
        pose.translate(0, -eye, 0);
        SCALED.get().push(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void post(RenderPlayerEvent.Post event) {
        Deque<Player> scaled = SCALED.get();
        if (!scaled.isEmpty() && scaled.peek() == event.getEntity()) {
            scaled.pop();
            event.getPoseStack().popPose();
        }
    }

    private VehicleRiderScale() {
    }
}
