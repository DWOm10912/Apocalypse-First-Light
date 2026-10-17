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

    /** HUD V2: shown as a status badge (client/StatusBadgeHud), not among vanilla's effect icons. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientMobEffectExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientMobEffectExtensions() {
            @Override public boolean isVisibleInGui(net.minecraft.world.effect.MobEffectInstance instance) { return false; }
        });
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return new ArrayList<>();
    }
}
