package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import java.util.List;

/** Independent, non-durable SIGHT accessory. */
public final class NativeSightItem extends Item implements NativeAttachment {
    private final boolean geoModel;
    public NativeSightItem(){this(false);}
    public NativeSightItem(boolean geoModel){super(new Properties().stacksTo(1));this.geoModel=geoModel;}
    public boolean usesGeoModel(){return geoModel;}
    public Slot slot(){return Slot.SIGHT;}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        String key=geoModel?"tooltip."+getDescriptionId().substring("item.".length()):"tooltip.apocalypse_firstlight.pistol_micro_red_dot";
        lines.add(Component.translatable(key+".type")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable(key+".description")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY,net.minecraft.ChatFormatting.ITALIC));
    }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
        // Forge calls this from Item's constructor, before geoModel is assigned.
        // Register unconditionally; only builtin/entity models use this renderer.
        // P9's ordinary baked item model continues through the vanilla renderer.
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions(){
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){
                if(renderer==null)renderer=new com.antaurora.apofirstlight.weapon.client.NativeMuzzleRendering.ItemRenderer();
                return renderer;
            }
        });
    }
}
