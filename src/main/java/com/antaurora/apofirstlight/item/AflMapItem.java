package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.map.StartMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The map (AFL Map V1, docs/ui/afl_map_v1_plan.md 5): carried anywhere it records the ground the player walks past
 * (client/map/MapClient); used in hand it unfolds into the world map (user 2026-10-21: "必须拿着地图右键打开（像书页那样翻开）
 * 然后过度到大地图页面"). No swing: the sheet is lifted, not swung. A map of a kind ({@link StartMap#KIND}: the PRAIRIE
 * road map from the forest home's table) has that kind's name and a line about it.
 */
public class AflMapItem extends Item {
    public AflMapItem(Properties properties) {
        super(properties);
    }

    static @Nullable String kind(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(StartMap.KIND) ? stack.getTag().getString(StartMap.KIND) : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        String kind = kind(stack);
        return kind == null ? super.getName(stack) : Component.translatable(getDescriptionId() + "." + kind);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        String kind = kind(stack);
        if (kind != null) lines.add(Component.translatable(getDescriptionId() + "." + kind + ".desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.antaurora.apofirstlight.client.map.WorldMapScreen::unfold);
        return InteractionResultHolder.consume(stack);
    }
}
