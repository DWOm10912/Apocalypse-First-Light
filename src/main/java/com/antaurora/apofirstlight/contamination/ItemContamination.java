package com.antaurora.apofirstlight.contamination;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Minimal discrete contamination data stored directly on an ItemStack. */
public final class ItemContamination {
    public static final String NBT_KEY = "AflContaminationLevel";
    public static final double CLEAN_EPSILON = 1.0E-6D;
    public static final int MAX_LEVEL = 5;

    private ItemContamination() {
    }

    public static Level getLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Level.CLEAN;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_KEY, Tag.TAG_ANY_NUMERIC)) return Level.CLEAN;
        return Level.fromValue(sanitizeLevel(tag.getInt(NBT_KEY)));
    }

    public static void setLevel(ItemStack stack, int level) {
        if (stack == null || stack.isEmpty()) return;
        int sanitized = sanitizeLevel(level);
        if (sanitized == 0) {
            clear(stack);
            return;
        }
        stack.getOrCreateTag().putByte(NBT_KEY, (byte) sanitized);
    }

    public static void setLevel(ItemStack stack, Level level) {
        setLevel(stack, level == null ? 0 : level.value());
    }

    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(NBT_KEY);
        if (tag.isEmpty()) stack.setTag(null);
    }

    public static boolean isContaminated(ItemStack stack) {
        return getLevel(stack) != Level.CLEAN;
    }

    public static Level getTargetLevel(double environmentExposure) {
        if (Double.isNaN(environmentExposure) || environmentExposure <= CLEAN_EPSILON) return Level.CLEAN;
        if (environmentExposure < 1.0D) return Level.TRACE;
        if (environmentExposure < 10.0D) return Level.LOW;
        if (environmentExposure < 60.0D) return Level.MODERATE;
        if (environmentExposure < 150.0D) return Level.HIGH;
        return Level.SEVERE;
    }

    public static int sanitizeLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    public enum Level {
        CLEAN(0, "contamination_level.apocalypse_firstlight.clean"),
        TRACE(1, "contamination_level.apocalypse_firstlight.trace"),
        LOW(2, "contamination_level.apocalypse_firstlight.low"),
        MODERATE(3, "contamination_level.apocalypse_firstlight.moderate"),
        HIGH(4, "contamination_level.apocalypse_firstlight.high"),
        SEVERE(5, "contamination_level.apocalypse_firstlight.severe");

        private final int value;
        private final String translationKey;

        Level(int value, String translationKey) {
            this.value = value;
            this.translationKey = translationKey;
        }

        public int value() {
            return value;
        }

        public String translationKey() {
            return translationKey;
        }

        public static Level fromValue(int value) {
            return switch (sanitizeLevel(value)) {
                case 1 -> TRACE;
                case 2 -> LOW;
                case 3 -> MODERATE;
                case 4 -> HIGH;
                case 5 -> SEVERE;
                default -> CLEAN;
            };
        }
    }
}
