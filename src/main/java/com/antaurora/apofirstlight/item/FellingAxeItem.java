package com.antaurora.apofirstlight.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Felling axe (2026-10-20, docs/gameplay/felling_axe_v1.md): the first AFL axe, an American Michigan-pattern felling axe
 * (3.5 lb head, 32 inch hickory handle). An AxeItem, so Forge's AXE_DIG action makes it a felling tool
 * (tree/FellingServer): tier speed 6 is felling power 1, one durability per chop. A melee weapon through the
 * weapons/melee tag. No stripping, scraping or wax removal (use passes), no repair, no enchanting; no recipe yet.
 */
public final class FellingAxeItem extends AxeItem {
    /** Forged carbon-steel head: speed 6 (felling power 1), iron level, 800 uses. */
    public static final Tier TIER = new Tier() {
        @Override public int getUses() { return 800; }
        @Override public float getSpeed() { return 6.0F; }
        @Override public float getAttackDamageBonus() { return 0.0F; }
        @Override public int getLevel() { return 2; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    public FellingAxeItem() {
        // player base damage 1 + 7 = 8; player base speed 4 - 3.2 = 0.8 swings a second (a 25-tick recharge)
        super(TIER, 7.0F, -3.2F, new Properties().setNoRepair());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
        return false;
    }
}
