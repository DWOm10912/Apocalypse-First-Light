package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * A chest rig or backpack of Grid Inventory V0 (docs/ui/afl_inventory_grid_v0_plan.md 1, 7): worn in its Curios slot,
 * it adds its grid to the right column of the inventory page; what is inside stays inside when it comes off. Right-click
 * puts it on (swapping out what is worn). A chest rig may carry clips on its shoulder straps ({@link RigClips}).
 */
public final class GridGearItem extends Item implements GridContainerItem, ICurioItem {
    private final String slot;
    private final GridLayout layout;
    private final int clips;

    public GridGearItem(String slot, GridLayout layout, Properties properties) {
        this(slot, layout, 0, properties);
    }

    public GridGearItem(String slot, GridLayout layout, int clips, Properties properties) {
        super(properties.stacksTo(1));
        this.slot = slot;
        this.layout = layout;
        this.clips = clips;
    }

    /** The clips on the rig's straps (RigClips), none on a backpack. */
    public int clips() { return clips; }

    @Override public String wornSlot() { return slot; }

    @Override public GridLayout layout(ItemStack stack) { return layout; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (AflEquipmentSlots.equipFromHand(player, hand, slot))
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
    }

    /** Capacity, the clips, then how much is inside. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        int cells = 0;
        for (var section : layout.sections()) cells += section.w() * section.h();
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.grid_gear.capacity", cells)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (clips > 0) lines.add(Component.translatable("tooltip.apocalypse_firstlight.grid_gear.clips", clips)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        var items = stack.getTag() == null ? null : stack.getTag().getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (items != null && !items.isEmpty())
            lines.add(Component.translatable("tooltip.apocalypse_firstlight.grid_gear.contents", items.size())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
