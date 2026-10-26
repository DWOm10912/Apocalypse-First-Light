package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.tooltip.AflEquipmentTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Furniture hardware box (docs/gameplay/furniture_recipes_v1.md 1.5): a taped carton of mixed cabinet hardware, as a
 * hardware store keeps it. Use opens it: the loot table {@link #TABLE} gives a random handful (6 to 10 hinges, knobs and
 * keyhole plates; a new kind of part is one more entry there), straight into the inventory (what does not fit drops), and
 * the box is gone (kept in Creative, to test the table). No opening animation yet.
 */
public final class FurnitureHardwareBoxItem extends Item {
    public static final ResourceLocation TABLE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "gameplay/furniture_hardware_box");

    public FurnitureHardwareBoxItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            LootParams params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player).withLuck(player.getLuck()).create(LootContextParamSets.GIFT);
            List<ItemStack> parts = server.getServer().getLootData().getLootTable(TABLE).getRandomItems(params);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS,
                    0.8F, 0.9F + level.random.nextFloat() * 0.2F);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            for (ItemStack part : parts) player.getInventory().placeItemBackInInventory(part);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        AflEquipmentTooltip.addDescription(lines, "tooltip.apocalypse_firstlight.furniture_hardware_box.description");
    }
}
