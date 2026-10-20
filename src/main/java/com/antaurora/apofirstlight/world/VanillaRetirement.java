package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Set;

/**
 * Vanilla Survival Rework V1 (docs/gameplay/vanilla_survival_rework_v1.md): the one switch and the one list that retire
 * vanilla content. Nothing is unregistered and no stack or NBT is ever deleted; retired gear only stops working and
 * stops being obtainable. The list is data (tags under data/apocalypse_firstlight/tags/{items,blocks}/retired/ and
 * data/apocalypse_firstlight/vanilla_retirement/*.json); the switch is the COMMON config below, read before data packs
 * load, so turning it off and reloading or re-entering the world brings the vanilla rules back.
 */
public final class VanillaRetirement {
    /** Gear that no longer works anywhere: retired swords, the shield, vanilla armour (retired/gear = the three below). */
    public static final TagKey<Item> GEAR = item("retired/gear");
    public static final TagKey<Item> SWORDS = item("retired/swords");
    public static final TagKey<Item> SHIELDS = item("retired/shields");
    public static final TagKey<Item> ARMOR = item("retired/armor");
    /** Never enters survival: recipes whose output is in it are removed, loot, trades and spawn gear drop it. */
    public static final TagKey<Item> UNOBTAINABLE = item("retired/unobtainable");
    /** Hidden from the creative tabs and search (client side, cosmetic only). */
    public static final TagKey<Item> CREATIVE_HIDDEN = item("retired/creative_hidden");
    /** Vanilla blocks whose use is refused in survival / adventure (enchanting, anvils, brewing, ...). */
    public static final TagKey<Block> WORKSTATIONS = TagKey.create(Registries.BLOCK, id("retired/workstations"));

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("vanilla_retirement");
        ENABLED = b.comment("Retire vanilla gear, enchanting, brewing, experience, the Nether and the End (AFL rules).",
                        "false restores the vanilla rules for development: recipes after /reload or a restart, the creative",
                        "tabs and worn gear after re-entering the world. Nothing is deleted either way.")
                .define("enabled", true);
        b.pop();
        SPEC = b.build();
    }

    /** GameTests only: forces the switch off without touching the config file. */
    private static volatile boolean testOverrideOff;
    /** "closed_dimensions" of the vanilla_retirement files (the Nether and the End), set on every data load. */
    private static volatile Set<ResourceLocation> closedDimensions = Set.of();

    private VanillaRetirement() {
    }

    public static boolean enabled() {
        if (testOverrideOff) return false;
        return !SPEC.isLoaded() || ENABLED.get();
    }

    public static void setTestOverrideOff(boolean off) {
        testOverrideOff = off;
    }

    /** Retired gear in any slot: no attributes, no enchantment effects, no blocking, no sweep. */
    public static boolean isRetiredGear(ItemStack stack) {
        return !stack.isEmpty() && enabled() && stack.is(GEAR);
    }

    public static boolean isRetiredSword(ItemStack stack) {
        return !stack.isEmpty() && enabled() && stack.is(SWORDS);
    }

    public static boolean isRetiredShield(ItemStack stack) {
        return !stack.isEmpty() && enabled() && stack.is(SHIELDS);
    }

    public static boolean isUnobtainable(ItemStack stack) {
        return !stack.isEmpty() && enabled() && stack.is(UNOBTAINABLE);
    }

    /** Creative and spectator players may still open retired workstations to check old builds. */
    public static boolean refusesWorkstation(BlockState state, Player player) {
        return enabled() && state.is(WORKSTATIONS) && !player.isCreative() && !player.isSpectator();
    }

    /** Whether nothing may travel into the dimension (WorldAccessEvents); Nether portals stay unlit while it is closed. */
    public static boolean isClosed(ResourceKey<Level> dimension) {
        return enabled() && closedDimensions.contains(dimension.location());
    }

    static void setClosedDimensions(Set<ResourceLocation> dimensions) {
        closedDimensions = Set.copyOf(dimensions);
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, id(path));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path);
    }
}
