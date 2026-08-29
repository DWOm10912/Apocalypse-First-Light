package com.antaurora.apofirstlight.radiation;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Passive status display for the sickness stage derived from absorbed dose. */
public final class RadiationSicknessEffect extends MobEffect {
    private static final int COLOR = 0xC5A52E;

    public RadiationSicknessEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return new ArrayList<>();
    }
}
