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
 * With the player skeleton off (docs/vehicles/first_pickup_v0_design.md 8.2, docs/dev/seated_rider_v1_plan.md 11): a
 * player on a T15 seat is drawn smaller around its eyes (the camera, SeatPose.EYE_OVER_SEAT over the cushion), so the
 * vanilla riding model's hips land on the cushion. The skeleton draws its seated riders itself (SeatedRiders). Every
 * pushed Pre is popped in its Post (the shadow pass fires the same pair); a Pre cancelled before this runs is skipped, so
 * no Post is waited for.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
final class VehicleRiderScale {
    private static final ThreadLocal<Deque<Player>> SCALED = ThreadLocal.withInitial(ArrayDeque::new);
    /** Eyes to hips of the vanilla riding model, scaled to the seat's eye height. */
    private static final float SCALE = (float) (com.antaurora.apofirstlight.skeleton.seat.SeatPose.EYE_OVER_SEAT / PickupTruckLayout.EYE_OVER_HIP);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof PickupTruckEntity)) return;
        // the skeleton's seated pose (client/skeleton/SeatedRiders) draws the rider at its own size
        if (com.antaurora.apofirstlight.client.skeleton.SeatedRiders.handles(player)) return;
        float s = SCALE;
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
