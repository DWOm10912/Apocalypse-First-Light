package com.antaurora.apofirstlight.equipment;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.temperature.ThermalInsulation;
import com.antaurora.apofirstlight.weight.PlayerMassSources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
    /** Curios slot type ids: the shared presets for back and wrist (so other mods' backpacks / bracelets fit), AFL's own ears. */
    public static final String BACK = "back", WRIST = "bracelet", EARS = "ears";
    /** The inventory page's column right of the player model, top to bottom (the off-hand slot is below them). */
    public static final List<String> INVENTORY_PAGE = List.of(BACK, WRIST, EARS);
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
        return stacks(entity, id).map(s -> s.getStackInSlot(0)).orElse(ItemStack.EMPTY);
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
