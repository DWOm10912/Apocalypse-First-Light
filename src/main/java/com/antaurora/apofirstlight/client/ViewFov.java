package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The fields of view this client last drew with (2026-10-05): the world's (the configured one times the dynamic factor of
 * flying, sprinting and speed, and whatever other mods make of it) and the held items' (70 degrees, unless something
 * changes it), as GameRenderer#getFov leaves them. A point on a held item is moved from the hand's projection to the
 * world's with their ratio (client/FuelDispenserRenderer: the held nozzle's spout and swivel).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ViewFov {
    private static double world = Double.NaN, hand = 70.0;

    private ViewFov() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void computed(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) world = event.getFOV();
        else hand = event.getFOV();
    }

    /** The world's field of view (degrees); the configured one until the first frame. */
    public static double world() {
        return Double.isNaN(world) ? Minecraft.getInstance().options.fov().get() : world;
    }

    /** The held items' field of view (degrees). */
    public static double hand() {
        return hand;
    }

    /**
     * A point of the hand pass (camera space, blocks: x right, y up, -z ahead) in the world, where it shows on screen at
     * the world's field of view, for {@code player}'s eyes and view at this partial tick (as FuelDispenserRenderer's held
     * nozzle).
     */
    public static net.minecraft.world.phys.Vec3 handToWorld(net.minecraft.world.entity.player.Player player, float partialTick, double x, double y, double z) {
        double k = Math.tan(Math.toRadians(world()) / 2) / Math.tan(Math.toRadians(hand()) / 2);
        var view = new org.joml.Quaternionf().rotationYXZ(-player.getViewYRot(partialTick) * net.minecraft.util.Mth.DEG_TO_RAD,
                player.getViewXRot(partialTick) * net.minecraft.util.Mth.DEG_TO_RAD, 0.0F);
        var right = new net.minecraft.world.phys.Vec3(new org.joml.Vector3f(-1, 0, 0).rotate(view));
        var up = new net.minecraft.world.phys.Vec3(new org.joml.Vector3f(0, 1, 0).rotate(view));
        var ahead = new net.minecraft.world.phys.Vec3(new org.joml.Vector3f(0, 0, 1).rotate(view));
        return player.getEyePosition(partialTick).add(right.scale(x * k)).add(up.scale(y * k)).add(ahead.scale(-z));
    }
}
