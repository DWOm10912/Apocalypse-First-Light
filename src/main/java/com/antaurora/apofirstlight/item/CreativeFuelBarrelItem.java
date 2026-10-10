package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.fluid.FuelCanTransfers;
import com.antaurora.apofirstlight.fluid.FuelPourTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Creative fuel barrels (2026-10-09, user: "创造模式的专属物品，汽油桶和柴油桶，一桶可以给 5000L，玩家不能通过桶来装液体";
 * docs/models/fuel_containers_v1.md): a test source of gasoline or diesel, only in the creative inventory (no recipe, no
 * loot). Use it on anything that takes fuel there (an open fuel fill cover, the diesel generator's open fill box, a fluid
 * tank, a fuel container on the ground: fluid/FuelCanTransfers#handler): as much as fits
 * goes in at once, up to what the barrel still holds (it starts with {@link #CAPACITY} L and is used up when empty). Nothing
 * fills it: it has no fluid handler. It acts before the block's own use (onItemUseFirst), so an open lid is not shut by the
 * click; where nothing takes fuel the block's use runs as usual (a shut fill box opens).
 */
public final class CreativeFuelBarrelItem extends Item {
    public static final int CAPACITY = 5000;
    private static final String LEFT_KEY = "AflLitres";
    private final Supplier<? extends Fluid> fuel;

    public CreativeFuelBarrelItem(Supplier<? extends Fluid> fuel, Properties properties) {
        super(properties.stacksTo(1));
        this.fuel = fuel;
    }

    /** Litres left in this barrel. */
    public static int left(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(LEFT_KEY) ? Math.max(0, tag.getInt(LEFT_KEY)) : CAPACITY;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!takesFuel(level, pos, level.getBlockState(pos), context.getClickedFace())) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        IFluidHandler into = FuelCanTransfers.handler(server, pos, context.getClickedFace());
        if (into == null) return InteractionResult.PASS;
        FluidStack offer = new FluidStack(fuel.get(), left(stack));
        int accepted = into.fill(offer, IFluidHandler.FluidAction.EXECUTE);
        if (accepted <= 0) {
            FluidStack there = FuelCanTransfers.contents(into);
            String why = into.getTanks() == 0 ? "wont_take" : !into.isFluidValid(0, offer)
                    ? (level.getBlockState(pos).getBlock() instanceof FuelPourTarget t ? t.refusal() : "wont_take")
                    : !there.isEmpty() && !there.isFluidEqual(offer) ? "other_fuel" : "full";
            if (player != null) player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.fuel_can." + why), true);
            return InteractionResult.SUCCESS;
        }
        int rest = left(stack) - accepted;
        if (rest <= 0) stack.shrink(1);
        else stack.getOrCreateTag().putInt(LEFT_KEY, rest);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 0.9F);
        if (player != null) player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.creative_fuel_barrel.poured", accepted, Math.max(0, rest)), true);
        return InteractionResult.SUCCESS;
    }

    /** Both sides: something here takes fuel (an open fill opening, or a block entity with a fluid handler). */
    private static boolean takesFuel(Level level, BlockPos pos, BlockState state, net.minecraft.core.Direction face) {
        if (FuelCanItem.takesPour(level, pos)) return true;
        if (state.getBlock() instanceof FuelPourTarget) return false;   // shut: the click opens it (the block's own use)
        var entity = level.getBlockEntity(pos);
        return entity != null && (entity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, face).isPresent()
                || entity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, null).isPresent());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;   // creative only
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.creative_fuel_barrel.left", new FluidStack(fuel.get(), 1).getDisplayName(), left(stack), CAPACITY).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.creative_fuel_barrel.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }
}
