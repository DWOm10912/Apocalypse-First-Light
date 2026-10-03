package com.antaurora.apofirstlight.thirst;

import com.antaurora.apofirstlight.contamination.ItemContamination;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A bottle of water (Thirst V1): dirty (raw, may carry germs, may be radioactive), boiled (no germs, still radioactive:
 * boiling cannot remove radiation) or purified (neither). The radiation is the stack's contamination level
 * (ItemContamination), fixed when the water was bottled; boiling dirty water gives purified water when it is clean and
 * boiled water keeping the level otherwise (WaterBoilingRecipes). Drunk like a potion with the vanilla drinking sound;
 * gives the glass bottle back. Not drinkable while thirst is full (Survival). Its tooltip shows the water-drop line every
 * drink gets (ClientThirst#tooltip); the liquid colour tells the three apart (ClientThirstHud#itemColours).
 */
public final class ThirstWaterItem extends Item {
    private final boolean raw;

    /** raw: untreated water, which may give the stomach bug. */
    public ThirstWaterItem(boolean raw, Properties properties) {
        super(properties);
        this.raw = raw;
    }

    public boolean raw() { return raw; }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
    @Override public int getUseDuration(ItemStack stack) { return 32; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!PlayerThirst.canDrink(player)) return InteractionResultHolder.fail(player.getItemInHand(hand));
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Player player = entity instanceof Player p ? p : null;
        if (entity instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
            PlayerThirst.drink(serverPlayer, ThirstConfig.get().bottle, raw, ItemContamination.getLevel(stack).value(), false);
        }
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        entity.gameEvent(GameEvent.DRINK);
        if (player == null || !player.getAbilities().instabuild) {
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (player != null) player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        }
        return stack;
    }
}
