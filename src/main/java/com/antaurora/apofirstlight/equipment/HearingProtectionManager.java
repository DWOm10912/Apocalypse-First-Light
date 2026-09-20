package com.antaurora.apofirstlight.equipment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Common-side, read-only policy. Never changes sound sources, AI noise, or item NBT. */
public final class HearingProtectionManager {
    private HearingProtectionManager() {}

    public static float getWorldSoundMultiplier(@Nullable Player player) {
        ItemStack head = headStack(player);
        return head.getItem() instanceof HearingProtection protection
                ? safeMultiplier(protection.worldSoundMultiplier(head)) : 1.0F;
    }

    public static float getImpulseProtectionMultiplier(@Nullable Player player) {
        ItemStack head = headStack(player);
        return head.getItem() instanceof HearingProtection protection
                ? safeMultiplier(protection.impulseProtectionMultiplier(head)) : 1.0F;
    }

    /** Apply HEAD protection once to the final tinnitus severity, after raw exposure and trigger decisions. */
    public static float effectiveTinnitusSeverity(Player listener, float rawSeverity) {
        if (!Float.isFinite(rawSeverity)) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, rawSeverity)) * getImpulseProtectionMultiplier(listener);
    }

    private static ItemStack headStack(@Nullable Player player) {
        return player == null ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.HEAD);
    }

    private static float safeMultiplier(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 1.0F;
    }
}
