package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.earthwork.BulkHolder;
import com.antaurora.apofirstlight.earthwork.EarthGrid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Round point shovel (docs/gameplay/sandbox_v1_design.md 5, docs/worldgen/earthwork_a2.md 4): 1.6 m over all, a pressed
 * steel blade 0.38 x 0.45 m on an ash handle with a D grip, 2.2 kg (item_mass). Its load is a {@link BulkHolder} in the
 * stack's tag (at most {@link #CAPACITY} quanta = 296.88 L); the work (dig, pour, scrape) is interaction/ShovelAction, started by
 * client/ShovelClient. Creative only (no recipe before the recipe rework).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ShovelItem extends Item {
    /**
     * The load a full blade carries (quanta): 1216 x 0.244 L = 296.88 L, the least one scoop brush digs (Earthwork
     * DIG_RADIUS, DIG_DEPTH: 1217 to 1230 by where it lands). User 2026-10-22: the big shovel, about 300 L a stroke, thrown
     * aside in the same stroke (it was 396, 96.68 L, poured by hand).
     */
    public static final int CAPACITY = 1216;
    /**
     * The load's mass counted towards carried weight (grams) at most: a full blade is about 386 kg of topsoil (564 kg of mud), the cap
     * keeps it a heavy tool that never overloads (user 2026-10-21, docs/worldgen/earthwork_a2.md 4.1).
     */
    public static final long LOAD_MASS_CAP = 15_000;

    public ShovelItem() {
        super(new Properties().stacksTo(1));
    }

    @SubscribeEvent
    public static void capabilities(RegisterCapabilitiesEvent event) {
        event.register(BulkHolder.class);
    }

    public static BulkHolder load(ItemStack stack) {
        return new BulkHolder.InStack(stack, CAPACITY);
    }

    /** How full the blade is, 0..1 (the drawn heap, the hint). */
    public static float fill(ItemStack stack) {
        return Math.min(1F, load(stack).stored() / (float) CAPACITY);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        LazyOptional<BulkHolder> holder = LazyOptional.of(() -> load(stack));
        return new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                return BulkHolder.CAPABILITY.orEmpty(cap, holder);
            }
        };
    }

    /** The load changing is no new item in the hand (no re-equip bob). */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BulkHolder h = load(stack);
        String m = h.material();
        if (m == null) {
            tooltip.add(Component.translatable("item.apocalypse_firstlight.round_point_shovel.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        String litres = String.format(java.util.Locale.ROOT, "%.1f", h.stored() * EarthGrid.LITRES_PER_QUANTUM);
        tooltip.add(Component.translatable("item.apocalypse_firstlight.round_point_shovel.load", litres,
                Component.translatable("bulk.apocalypse_firstlight." + m)).withStyle(ChatFormatting.GRAY));
    }
}
