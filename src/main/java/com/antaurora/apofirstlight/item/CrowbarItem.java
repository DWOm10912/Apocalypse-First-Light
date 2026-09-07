package com.antaurora.apofirstlight.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Single-target vanilla melee: no sword sweep, reach extension, or mining durability. */
public final class CrowbarItem extends Item {
    private final Multimap<Attribute, AttributeModifier> modifiers =
            ImmutableMultimap.<Attribute, AttributeModifier>builder()
                    // Player base damage is 1; the displayed/full-strength total is 6.
                    .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,
                            "Crowbar damage", 5.0, AttributeModifier.Operation.ADDITION))
                    // Player base speed is 4; 20 / 13 attacks/sec gives a 13-tick recharge.
                    .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID,
                            "Crowbar speed", 20.0 / 13.0 - 4.0, AttributeModifier.Operation.ADDITION))
                    .build();

    public CrowbarItem() {
        super(new Properties().durability(480).setNoRepair());
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Vanilla invokes this only after a successful attack, not on a miss or blocked damage.
        stack.hurtAndBreak(1, attacker, entity -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
        return false;
    }
}
