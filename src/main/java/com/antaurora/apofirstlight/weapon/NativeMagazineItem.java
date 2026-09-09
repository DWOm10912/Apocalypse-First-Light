package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;

/** P9-only 24-round accessory; capacity is resolved from the actual gun stack. */
public final class NativeMagazineItem extends Item implements NativeAttachment {
    public NativeMagazineItem(){super(new Properties().stacksTo(1));}
    @Override public Slot slot(){return Slot.MAGAZINE;}
    public boolean accepts(NativeGunDefinition gun){return gun.id().toString().equals("apocalypse_firstlight:p9_01");}
    public int capacity(){return 24;}
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,java.util.List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.p9_01_extended_magazine.type").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.p9_01_extended_magazine.description").withStyle(net.minecraft.ChatFormatting.DARK_GRAY,net.minecraft.ChatFormatting.ITALIC));
    }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions(){
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){
                if(renderer==null)renderer=new com.antaurora.apofirstlight.weapon.client.NativeMagazineRendering.ItemRenderer();
                return renderer;
            }
        });
    }
}
