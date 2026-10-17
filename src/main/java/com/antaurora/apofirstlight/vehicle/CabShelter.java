package com.antaurora.apofirstlight.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * The T15's closed cab between its riders and the outside (docs/vehicles/first_pickup_v0_design.md 8.7; user 2026-10-16:
 * a zombie hit the driver through the glass). A hand-to-hand blow from outside on someone sitting in the cab, or from
 * the cab on something outside, only knocks on the glass, unless the door on the outsider's side is open. Projectiles,
 * explosions and the rest still reach them (bullets through glass and broken windows: the truck's damage, P2); the
 * bed's seats stay open.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CabShelter {
    /** A door open this far (deg) lets a blow through on its side. */
    private static final float DOOR_GAP = 20F;

    private CabShelter() {
    }

    /** Outside in: someone hitting a cab rider with its own hands (or claws). */
    @SubscribeEvent
    public static void attacked(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        Entity hitter = event.getSource().getDirectEntity();
        if (target.level().isClientSide || !(hitter instanceof LivingEntity) || hitter != event.getSource().getEntity()) return;
        PickupTruckEntity truck = cab(target);
        if (truck != null && shut(truck, hitter)) {
            event.setCanceled(true);
            knock(truck, hitter);
        }
    }

    /** Inside out: a cab rider hitting something outside. */
    @SubscribeEvent
    public static void attacking(AttackEntityEvent event) {
        Entity target = event.getTarget();
        PickupTruckEntity truck = cab(event.getEntity());
        if (truck == null || target == truck || target.getRootVehicle() == truck || target instanceof PickupTruckEntity.Part part && part.getParent() == truck) return;
        if (shut(truck, target)) {
            event.setCanceled(true);
            if (!event.getEntity().level().isClientSide) knock(truck, event.getEntity());
        }
    }

    /** The truck whose cab this entity sits in, or null. */
    @Nullable
    private static PickupTruckEntity cab(Entity rider) {
        if (!(rider.getVehicle() instanceof PickupTruckEntity truck)) return null;
        int seat = truck.seatOf(rider);
        return seat >= 0 && PickupTruckLayout.SEATS[seat].cab() ? truck : null;
    }

    /** Whether the cab is closed toward this outsider (its side's door shut, or it is ahead of or behind the doors). */
    private static boolean shut(PickupTruckEntity truck, Entity outsider) {
        if (outsider.getRootVehicle() == truck) return false;
        double[] l = PickupTruckLayout.local(truck.position(), truck.getYRot(), outsider.position());
        boolean right = l[1] > 0;
        return truck.doorAngle(right, 1F) < DOOR_GAP;
    }

    private static void knock(PickupTruckEntity truck, Entity at) {
        truck.level().playSound(null, at.getX(), at.getEyeY(), at.getZ(), SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 0.9F, 0.9F);
    }
}
