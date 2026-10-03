package com.antaurora.apofirstlight.thirst;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * Stomach bug from raw water (Thirst V1). The effect only marks the sickness and its time left; PlayerThirst applies
 * the symptoms. Milk does not cure it.
 */
public final class GastroenteritisEffect extends MobEffect {
    private static final int COLOR = 0x7D8B2E;

    public GastroenteritisEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return new ArrayList<>();
    }
}
