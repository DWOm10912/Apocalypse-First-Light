package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Character creation V1 clothes (docs/ui/character_creation_v1.md, user 2026-10-26), worn on the old body since old-body
 * clothes V1 (docs/dev/old_body_clothes_v1.md, 2026-10-27). The shirt goes in the base layer slot and the jacket in the
 * coat slot (AFL Curios slots, AflEquipmentSlots.BASE / COAT); the jeans and shoes in vanilla's legs and feet slots
 * (Equipable, so the armour slots take them; not ArmorItem, so vanilla's armour layer draws nothing). The body draws what
 * is worn (client/skeleton/OldBodyWardrobe on the old body, BlockMeshWardrobe on B5); the item itself is a folded mesh
 * (tools/build-equipment-meshes-v1.mjs). The same items for men and women. Right-click wears one, swapping out what is there.
 */
public class ClothingItem extends Item implements ICurioItem {
    private final String slot;

    /** A garment worn in one AFL Curios slot. */
    public ClothingItem(String slot) {
        super(new Item.Properties().stacksTo(1));
        this.slot = slot;
    }

    public String slot() {
        return slot;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (AflEquipmentSlots.equipFromHand(player, hand, slot))
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
    }

    /** Jeans and shoes: worn in vanilla's legs or feet slot. */
    public static final class Armour extends Item implements Equipable {
        private final EquipmentSlot slot;

        public Armour(EquipmentSlot slot) {
            super(new Item.Properties().stacksTo(1));
            this.slot = slot;
        }

        @Override
        public EquipmentSlot getEquipmentSlot() {
            return slot;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_LEATHER;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return swapWithEquipmentSlot(this, level, player, hand);
        }
    }
}
