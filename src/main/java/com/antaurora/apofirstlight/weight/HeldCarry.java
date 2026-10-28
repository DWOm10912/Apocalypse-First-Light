package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * How the item in the main hand is carried (held carry V1, user 2026-10-27: "超过多少公斤，就双手抱起来…不能搬太重的东西，
 * 你参考一下现实"), by the mass of one of it with what it holds (StackMassCalculator, item_mass data):
 * <ul>
 *   <li>{@link Style#HAND}: in the hand, as before;</li>
 *   <li>{@link Style#HANDLE}: an item with a handle or a bail (tag {@code carry/handle}: the water pail, the jerry can) up to
 *   {@link #HANDLE_KG}, carried in one hand at the side;</li>
 *   <li>{@link Style#HUG}: {@link #HUG_KG} or more, or bulky (tags {@code carry/bulky}, {@code carry/oversized}: furniture,
 *   appliances, however light), carried in both arms in front of the body (client/skeleton/CarryHug): no sprinting;</li>
 *   <li>{@link Style#HUG_HEAVY}: over {@link #SLOW_KG}: the same, walking at {@link #SLOW} of the speed, no jumping;</li>
 *   <li>{@link Style#TOO_HEAVY}: over {@link #MAX_KG}: one person cannot lift it (a vending machine, a wardrobe, the box
 *   stove): it cannot be taken off the ground (inventory/grid/GroundItems), Creative excepted.</li>
 * </ul>
 * Numbers after the NIOSH lifting guide (23 kg the recommended limit for a good lift) and what one adult moves alone: a
 * bulky 40-45 kg load is about the most, carried a short way.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class HeldCarry {
    public static final double HUG_KG = 8, HANDLE_KG = 25, SLOW_KG = 25, MAX_KG = 45;
    /** The walking speed while carrying over {@link #SLOW_KG} in the arms (a multiplier). */
    public static final double SLOW = 0.7;
    public static final TagKey<Item> HANDLE = tag("carry/handle"), BULKY = tag("carry/bulky"), OVERSIZED = tag("carry/oversized");
    private static final UUID SLOW_ID = UUID.fromString("3f1c9a62-6a7e-4d0e-9b8e-5c2d0e7a4b11");

    public enum Style {
        HAND, HANDLE, HUG, HUG_HEAVY, TOO_HEAVY;

        /** Carried in both arms. */
        public boolean hug() {
            return this == HUG || this == HUG_HEAVY || this == TOO_HEAVY;
        }
    }

    private HeldCarry() {
    }

    private static TagKey<Item> tag(String path) {
        return ItemTags.create(new ResourceLocation(ApocalypseFirstLight.MOD_ID, path));
    }

    /** kg of one of this stack, with what it holds. */
    public static double kg(ItemStack stack) {
        return stack.isEmpty() ? 0 : StackMassCalculator.mass(stack.copyWithCount(1)).grams() / 1000.0;
    }

    public static Style style(ItemStack stack) {
        if (stack.isEmpty()) return Style.HAND;
        double kg = kg(stack);
        if (kg > MAX_KG) return Style.TOO_HEAVY;
        if (stack.is(HANDLE) && kg <= HANDLE_KG) return Style.HANDLE;
        if (kg > SLOW_KG) return Style.HUG_HEAVY;
        if (kg >= HUG_KG || stack.is(BULKY) || stack.is(OVERSIZED)) return Style.HUG;
        return Style.HAND;
    }

    /** One person cannot lift this (not in Creative or as a spectator). */
    public static boolean tooHeavy(Player player, ItemStack stack) {
        return !player.getAbilities().instabuild && !player.isSpectator() && style(stack) == Style.TOO_HEAVY;
    }

    /** Both sides: no sprinting with the arms full; slower under a heavy load. */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        Style style = player.isSpectator() ? Style.HAND : style(player.getMainHandItem());
        if (style.hug() && player.isSprinting()) player.setSprinting(false);
        if (event.phase != TickEvent.Phase.END) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean slow = style == Style.HUG_HEAVY || style == Style.TOO_HEAVY;
        if (slow && speed.getModifier(SLOW_ID) == null)
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, "AFL heavy load in the arms", SLOW - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!slow && speed.getModifier(SLOW_ID) != null) speed.removeModifier(SLOW_ID);
    }

    /** No jumping under a heavy load in the arms (the jump is undone as it starts, on the side that moved it). */
    @SubscribeEvent
    public static void jump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Style style = style(player.getMainHandItem());
        if (style != Style.HUG_HEAVY && style != Style.TOO_HEAVY) return;
        var v = player.getDeltaMovement();
        player.setDeltaMovement(v.x, Math.min(v.y, 0), v.z);
    }
}
