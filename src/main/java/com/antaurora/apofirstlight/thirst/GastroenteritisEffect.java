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
