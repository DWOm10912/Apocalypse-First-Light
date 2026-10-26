package com.antaurora.apofirstlight.equipment;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.temperature.ThermalInsulation;
import com.antaurora.apofirstlight.weight.PlayerMassSources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AFL's extra equipment slots (docs/gameplay/equipment_slots_v1.md). Curios (a required dependency) stores them: sync,
 * saving, death drops and keepInventory are its; the slot types and the player's set are data
 * (data/apocalypse_firstlight/curios/). AFL shows its three on the vanilla inventory page (InventoryMenuEquipmentMixin)
 * and hides them from Curios' own panel. Every Curios slot the player has counts toward the carried mass and, by the
 * thermal_insulation data, toward the warmth (Temperature V1).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AflEquipmentSlots {
    /**
     * Curios slot type ids: the shared presets for back and wrist (so other mods' backpacks / bracelets fit), AFL's own
     * ears, chest rig (Grid Inventory V0, docs/ui/afl_inventory_grid_v0_plan.md) and pistol holster (plan 10).
     */
    public static final String BACK = "back", WRIST = "bracelet", EARS = "ears", RIG = "rig", HOLSTER = "holster";
    /**
     * The clips (user 2026-10-21): small things, a Geiger counter, a compass or a lighter (the apocalypse_firstlight:clippable
     * tag), hang on the worn chest rig's straps and do their job there (#inUse). Not a Curios slot: they are saved in the rig
     * (RigClips) and exist only while one is worn. Menu indices 51 and 52, after the page; CLIPS is the most any rig has.
     */
    public static final String CLIP = "clip";
    public static final int CLIPS = 2;
    /**
     * The AFL slots added to the inventory menu, in menu order (indices 46..50). The vanilla inventory page shows the
     * first three in the column right of the player model; the AFL inventory page places all of them itself.
     */
    public static final List<String> INVENTORY_PAGE = List.of(BACK, WRIST, EARS, RIG, HOLSTER);
    /**
     * Clothes (character creation V1, docs/ui/character_creation_v1.md, user 2026-10-26): the base layer (the shirt) and the
     * coat (the jacket), AFL's own Curios slots. Menu indices 53 and 54, after the clips; only the AFL inventory page shows
     * them (the trousers and shoes use vanilla's legs and feet slots). Not worn on pickup (#wearIfFree).
     */
    public static final String BASE = "base", COAT = "coat";
    public static final List<String> CLOTHES = List.of(BASE, COAT);
    private AflEquipmentSlots() {}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PlayerMassSources.registerExtraEquipment(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "curios"), player -> allEquipped(player));
            // Temperature V1: worn equipment warms like armour (the thermal_insulation data)
            ThermalInsulation.register(player -> allEquipped(player).stream().mapToDouble(ThermalInsulation::of).sum());
        });
    }

    /** The slot type's stacks for this entity, if it has at least one slot of it. */
    public static Optional<IDynamicStackHandler> stacks(LivingEntity entity, String id) {
        if (entity == null) return Optional.empty();
        return CuriosApi.getCuriosInventory(entity).resolve().flatMap(h -> h.getStacksHandler(id))
                .map(ICurioStacksHandler::getStacks).filter(s -> s.getSlots() > 0);
    }
    /** The first stack of the slot type, or empty. */
    public static ItemStack get(LivingEntity entity, String id) {
        return get(entity, id, 0);
    }
    /** The stack in one slot of the slot type, or empty. */
    public static ItemStack get(LivingEntity entity, String id, int index) {
        return stacks(entity, id).filter(s -> index < s.getSlots()).map(s -> s.getStackInSlot(index)).orElse(ItemStack.EMPTY);
    }

    /** What hangs on one clip of the worn chest rig, or empty (no rig, or it has fewer clips). */
    public static ItemStack clip(LivingEntity entity, int index) {
        return com.antaurora.apofirstlight.inventory.grid.RigClips.get(get(entity, RIG), index);
    }

    /** Whether one of the worn rig's clips holds the item. */
    public static boolean clipped(LivingEntity entity, Item item) {
        var rig = get(entity, RIG);
        for (int i = 0; i < com.antaurora.apofirstlight.inventory.grid.RigClips.count(rig); i++)
            if (com.antaurora.apofirstlight.inventory.grid.RigClips.get(rig, i).is(item)) return true;
        return false;
    }

    /**
     * A gadget at work: in the hand or clipped on (user 2026-10-21: the compass bar, the Geiger counter's reading and
     * clicks). Either side.
     */
    public static boolean inUse(LivingEntity entity, Item item) {
        return entity != null && (entity.getMainHandItem().is(item) || entity.getOffhandItem().is(item) || clipped(entity, item));
    }
    /** The stack of a gadget at work ({@link #inUse}): the main hand, the off hand, then the clips; empty if none. */
    public static ItemStack inUseStack(LivingEntity entity, Item item) {
        if (entity == null) return ItemStack.EMPTY;
        if (entity.getMainHandItem().is(item)) return entity.getMainHandItem();
        if (entity.getOffhandItem().is(item)) return entity.getOffhandItem();
        for (int i = 0; i < CLIPS; i++) { var stack = clip(entity, i); if (stack.is(item)) return stack; }
        return ItemStack.EMPTY;
    }
    static SlotContext context(LivingEntity entity, String id) { return new SlotContext(id, entity, 0, false, true); }

    /** Curios' own rules: the slot's validators (the item tag curios:<id>) and the item's canEquip. */
    public static boolean canHold(LivingEntity entity, String id, ItemStack stack) {
        if (stack.isEmpty() || entity == null) return false;
        var ctx = context(entity, id);
        return CuriosApi.isStackValid(ctx, stack) && CuriosApi.getCurio(stack).map(c -> c.canEquip(ctx)).orElse(true);
    }
    public static boolean canRemove(LivingEntity entity, String id, ItemStack stack) {
        return stack.isEmpty() || CuriosApi.getCurio(stack).map(c -> c.canUnequip(context(entity, id))).orElse(true);
    }

    /**
     * Right-click equip from the hand into the slot, swapping out what is there (like vanilla armour). Server side only;
     * the client sees the result through the sync. False when the slot is missing or does not take the item.
     */
    public static boolean equipFromHand(Player player, InteractionHand hand, String id) {
        var held = player.getItemInHand(hand);
        var stacks = stacks(player, id);
        if (stacks.isEmpty() || !canHold(player, id, held)) return false;
        var worn = stacks.get().getStackInSlot(0);
        if (!canRemove(player, id, worn)) return false;
        if (player.level().isClientSide) return true;
        var one = held.copyWithCount(1);
        stacks.get().setStackInSlot(0, one);
        if (held.getCount() == 1) player.setItemInHand(hand, worn);
        else { held.shrink(1); if (!worn.isEmpty() && !player.getInventory().add(worn)) player.drop(worn, false); }
        var sound = CuriosApi.getCurio(one).map(c -> c.getEquipSound(context(player, id))).orElse(null);
        if (sound != null) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound.soundEvent(),
                SoundSource.PLAYERS, sound.volume(), sound.pitch());
        return true;
    }

    /**
     * Pickup (Grid Inventory V0): puts one of the stack into the first of AFL's page slots that is empty and takes it,
     * like Tarkov wears a picked-up rig or backpack when its slot is free. Server side; false when no slot is free.
     */
    public static boolean wearIfFree(Player player, ItemStack stack) {
        if (stack.isEmpty() || player.level().isClientSide) return false;
        for (String id : INVENTORY_PAGE) {
            var stacks = stacks(player, id);
            if (stacks.isEmpty() || !stacks.get().getStackInSlot(0).isEmpty() || !canHold(player, id, stack)) continue;
            var one = stack.split(1);
            stacks.get().setStackInSlot(0, one);
            var sound = CuriosApi.getCurio(one).map(c -> c.getEquipSound(context(player, id))).orElse(null);
            if (sound != null) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound.soundEvent(),
                    SoundSource.PLAYERS, sound.volume(), sound.pitch());
            return true;
        }
        return false;
    }

    /** Every stack in the player's Curios slots. */
    public static List<ItemStack> allEquipped(LivingEntity entity) {
        List<ItemStack> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(h -> {
            var all = h.getEquippedCurios();
            for (int i = 0; i < all.getSlots(); i++) out.add(all.getStackInSlot(i));
        });
        return out;
    }
}
