package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import java.util.List;

/** Independent, non-durable SIGHT accessory. */
public final class NativeSightItem extends Item {
    public enum Slot { SIGHT }
    public NativeSightItem(){super(new Properties().stacksTo(1));}
    public Slot slot(){return Slot.SIGHT;}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.sight.install"));
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.sight.detach"));
    }
}
