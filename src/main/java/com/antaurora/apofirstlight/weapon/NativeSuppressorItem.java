package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class NativeSuppressorItem extends Item implements NativeAttachment {
    /** Used when the device has no native_attachments data file (pistol_suppressor_01). */
    private static final double DEFAULT_NOISE_MULTIPLIER=.05;
    public NativeSuppressorItem(){super(new Properties().stacksTo(1));}
    @Override public Slot slot(){return Slot.MUZZLE;}
    @Override public double noiseRadiusMultiplier(){
        return NativeAttachmentData.get(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(this)).noiseMultiplier(DEFAULT_NOISE_MULTIPLIER);
    }
    @Override public boolean suppressesFireSound(){return true;}
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,net.minecraft.world.level.Level level,
            java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag){
        String key="tooltip."+getDescriptionId().substring("item.".length());
        com.antaurora.apofirstlight.tooltip.AflEquipmentTooltip.attachment(lines,stack,key+".description");
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
