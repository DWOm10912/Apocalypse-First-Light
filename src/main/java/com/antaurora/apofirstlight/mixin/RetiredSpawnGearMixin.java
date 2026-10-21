package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.VanillaRetirement;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla Survival Rework V1: mobs no longer spawn wearing or holding retired gear. finalizeSpawn rolls the random
 * equipment (populateDefaultEquipmentSlots, which subclasses such as Zombie extend) and then enchants it; at the head of
 * the enchanting step the rolled retired pieces are taken off again. Only that spawn roll is touched: gear given by
 * /summon NBT, picked up from the ground or saved in old chunks stays.
 */
@Mixin(Mob.class)
public abstract class RetiredSpawnGearMixin {
    @Inject(method = "populateDefaultEquipmentEnchantments", at = @At("HEAD"))
    private void afl$stripRetiredSpawnGear(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        if (!VanillaRetirement.enabled()) return;
        Mob mob = (Mob) (Object) this;
        for (EquipmentSlot slot : EquipmentSlot.values())
            if (VanillaRetirement.isUnobtainable(mob.getItemBySlot(slot))) mob.setItemSlot(slot, ItemStack.EMPTY);
    }
}
