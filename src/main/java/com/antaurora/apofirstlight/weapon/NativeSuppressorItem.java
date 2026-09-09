package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class NativeSuppressorItem extends Item implements NativeAttachment {
    public NativeSuppressorItem(){super(new Properties().stacksTo(1));}
    @Override public Slot slot(){return Slot.MUZZLE;}
    @Override public double noiseRadiusMultiplier(){return .05;}
    @Override public boolean suppressesFireSound(){return true;}
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,net.minecraft.world.level.Level level,
            java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag){
        String key="tooltip."+getDescriptionId().substring("item.".length());
        lines.add(net.minecraft.network.chat.Component.translatable(key+".type")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(net.minecraft.network.chat.Component.translatable(key+".description")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY,net.minecraft.ChatFormatting.ITALIC));
    }
    @Override public void initializeClient(java.util.function.Consumer<IClientItemExtensions> consumer){
        consumer.accept(new IClientItemExtensions(){
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){
                if(renderer==null)renderer=new com.antaurora.apofirstlight.weapon.client.NativeMuzzleRendering.ItemRenderer();
                return renderer;
            }
        });
    }
}
