package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.tooltip.AflEquipmentTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * Clinical thermometer (Temperature V1): tucked under the armpit for about 3 s (ClientThermometerHands draws the hand and
 * arm; letting go cancels), then the core body temperature at that moment shows in the temperature dial for 10 s
 * (TemperatureReadout); then a 5 s cooldown (the vanilla item cooldown). Takes no slot.
 */
public final class ClinicalThermometerItem extends Item {
    public static final int MEASURE_TICKS = 60;
    /** After a reading, 5 s before the next one (holding right-click would otherwise start again at once). */
    public static final int COOLDOWN_TICKS = 100;

    public ClinicalThermometerItem(Properties properties) { super(properties); }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public int getUseDuration(ItemStack stack) { return MEASURE_TICKS; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player) player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        if (level.isClientSide && entity instanceof Player)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TemperatureReadout.measured(entity));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        AflEquipmentTooltip.addDescription(lines, "tooltip.apocalypse_firstlight.clinical_thermometer.description");
    }
}
