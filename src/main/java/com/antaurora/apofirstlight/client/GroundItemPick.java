package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import com.antaurora.apofirstlight.inventory.grid.GridActions;
import com.antaurora.apofirstlight.inventory.grid.GridPackets;
import com.antaurora.apofirstlight.inventory.grid.GroundItems;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Ground items (docs/ui/afl_inventory_grid_v0_plan.md 12), Survival / Adventure: the item the crosshair is on (the box it
 * takes as it lies, GroundItemRenderer#footprint, nearer than the block or entity looked at, within {@link #REACH}) is
 * offered by the world prompt and taken with the use key:
 * <ul>
 * <li>use: the normal pickup order, said as what it will do: wear (its gear slot is free), equip (its weapon position is
 * free and takes it), else pick up;</li>
 * <li>sneak + use where use wears or equips: into the grids instead (stow);</li>
 * <li>with a gun in the hand, use aims: sneak + use takes the item the normal way.</li>
 * </ul>
 * The server checks and does it (GridActions GROUND_QUICK).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class GroundItemPick {
    /** How far from the eye a ground item is offered (blocks). */
    private static final double REACH = 3.0;

    /** What the use key does on a ground item: the prompt's key and the pickup mode; {@code sneak} when it takes sneak + use. */
    public record Offer(ItemEntity item, String key, int mode, boolean sneak) {
        public Component label() {
            return Component.translatable("hint.apocalypse_firstlight.ground_item." + key).append(" ").append(item.getItem().getHoverName());
        }

        public Vec3 anchor() {
            return item.position().add(0, GroundItemRenderer.footprint(item)[1] + 0.2, 0);
        }
    }

    private GroundItemPick() {}

    /** The offer for the item under the crosshair now, or null. */
    @Nullable
    public static Offer offer(Minecraft mc) {
        var player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || player.isSpectator() || PlayerStorageCapacity.unrestricted(player)) return null;
        var item = lookedAt(mc, player);
        return item == null ? null : offer(player, item);
    }

    @Nullable
    private static Offer offer(Player player, ItemEntity item) {
        var stack = item.getItem();
        boolean gunInHand = player.getMainHandItem().getItem() instanceof NativeGunItem, sneaking = player.isShiftKeyDown();
        String auto = wearable(player, stack) ? "wear" : equippable(player, stack) ? "equip" : "pick_up";
        if (gunInHand) return sneaking ? new Offer(item, auto, GroundItems.AUTO, true) : null;
        // stow is offered only where use would wear or equip, and the item may go into a grid
        if (sneaking && !auto.equals("pick_up") && PlayerGrids.allowedInGrid(stack)) return new Offer(item, "stow", GroundItems.STOW, true);
        return new Offer(item, auto, GroundItems.AUTO, false);
    }

    /** Worn on pickup: a single item one of AFL's free page slots takes (AflEquipmentSlots#wearIfFree). */
    private static boolean wearable(Player player, ItemStack stack) {
        if (stack.getCount() != 1) return false;
        for (String id : AflEquipmentSlots.INVENTORY_PAGE) {
            var stacks = AflEquipmentSlots.stacks(player, id);
            if (stacks.isPresent() && stacks.get().getStackInSlot(0).isEmpty() && AflEquipmentSlots.canHold(player, id, stack)) return true;
        }
        return false;
    }

    /** Equipped on pickup: a weapon whose empty position takes it (InventoryGridMixin). */
    private static boolean equippable(Player player, ItemStack stack) {
        var role = HotbarRoles.weaponRole(stack);
        if (role == null || stack.getCount() != 1) return false;
        var inventory = player.getInventory();
        int index = HotbarRoles.slotOf(role);
        return inventory.items.get(index).isEmpty() && !PlayerStorageCapacity.rejects(inventory, index, stack);
    }

    /**
     * The nearest pickable item whose lying box the view ray enters before anything else it hits. An entity looked at
     * hides only the items outside its box: an item lying in the pickup's bed is inside the bed's box, which the ray
     * enters first.
     */
    @Nullable
    private static ItemEntity lookedAt(Minecraft mc, Player player) {
        float partialTick = mc.getFrameTime();
        Vec3 eye = player.getEyePosition(partialTick), look = player.getViewVector(partialTick);
        double blockLimit = REACH, entityLimit = REACH;
        AABB entityBox = null;
        if (mc.hitResult instanceof EntityHitResult hit) {
            entityLimit = hit.getLocation().distanceTo(eye);
            entityBox = hit.getEntity().getBoundingBox().inflate(0.05);
        } else if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            blockLimit = Math.min(blockLimit, mc.hitResult.getLocation().distanceTo(eye));
        }
        ItemEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (var item : mc.level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(REACH + 1))) {
            if (!GroundItems.pickable(player, item)) continue;
            float yaw = GroundItemRenderer.restYaw(item);
            float[] box = GroundItemRenderer.footprint(item);
            Vec3 at = item.getPosition(partialTick);
            Vec3 from = eye.subtract(at).yRot(-yaw), dir = look.yRot(-yaw);
            double t = enter(from, dir, box[0], box[1], box[2]);
            double limit = entityBox == null || entityBox.contains(at) ? blockLimit : Math.min(blockLimit, entityLimit);
            if (t >= 0 && t < limit && t < bestT) {
                bestT = t;
                best = item;
            }
        }
        return best != null && GroundItems.inSight(player, best) ? best : null;
    }

    /** Where the ray enters the box [-hx, hx] x [0, h] x [-hz, hz] (distance along a unit direction), or -1. */
    private static double enter(Vec3 from, Vec3 dir, double hx, double h, double hz) {
        double near = 0, far = Double.MAX_VALUE;
        double[] o = {from.x, from.y, from.z}, d = {dir.x, dir.y, dir.z}, lo = {-hx, 0, -hz}, hi = {hx, h, hz};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-9) {
                if (o[a] < lo[a] || o[a] > hi[a]) return -1;
                continue;
            }
            double t1 = (lo[a] - o[a]) / d[a], t2 = (hi[a] - o[a]) / d[a];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) return -1;
        }
        return near;
    }

    /** The use key on an offered item takes it (both hands' triggers are cancelled; the main hand's sends). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (!event.isUseItem() || !mc.isWindowActive()) return;
        var offer = offer(mc);
        if (offer == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() == InteractionHand.MAIN_HAND)
            GridPackets.send(new GridPackets.Action(GridActions.Kind.GROUND_QUICK.ordinal(), 0, 0, offer.mode(), 0, false, offer.item().getId()));
    }
}
