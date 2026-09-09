package com.antaurora.apofirstlight.tooltip;

import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import static com.antaurora.apofirstlight.tooltip.AflTooltipStatType.*;

/** Read-only presentation. Never writes ammo, attachments or gameplay definitions. */
public final class AflEquipmentTooltip {
    public static final int VALUE_COLOR=0xE0E0E0;
    public record Stat(AflTooltipStatType type,Component value){}
    public static String number(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    public static String percentChange(double multiplier){double value=(multiplier-1)*100;return (value>0?"+":"")+number(value)+"%";}
    private static Component value(String key,Object... args){return Component.translatable("tooltip.apocalypse_firstlight.value."+key,args);}
    public static void addDescription(List<Component> lines,String key){
        lines.add(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY).withStyle(s->s.withItalic(false)));
    }
    public static void addSeparator(List<Component> lines){
        lines.add(Component.literal("────────────────────").withStyle(net.minecraft.ChatFormatting.DARK_GRAY).withStyle(s->s.withItalic(false)));
    }
    public static void addStat(List<Component> lines,Stat stat){
        var label=Component.translatable(stat.type().key()).withStyle(Style.EMPTY.withColor(stat.type().color()).withItalic(false));
        label.append(Component.translatable("tooltip.apocalypse_firstlight.stat.separator"));
        label.append(stat.value().copy().withStyle(Style.EMPTY.withColor(VALUE_COLOR).withItalic(false)));
        lines.add(label);
    }
    public static List<Stat> gunStats(ItemStack stack,NativeGunDefinition d){
        var ammo=ForgeRegistries.ITEMS.getValue(d.ammoType());
        String caliberKey=ammo.getDescriptionId()+".caliber";
        Component caliber=net.minecraft.locale.Language.getInstance().has(caliberKey)?Component.translatable(caliberKey):ammo.getDescription();
        return List.of(new Stat(DAMAGE,Component.literal(number(d.baseDamage()))),
                new Stat(AMMUNITION,caliber),new Stat(MAGAZINE,value("rounds",NativeGunAmmo.capacity(stack,d))),
                new Stat(FIRE_MODE,value(d.fireMode())),new Stat(RANGE,value("blocks",number(d.effectiveRange()))),
                new Stat(NOISE,value("ai_noise",number(NativeGunNoise.resolve(stack,d).radius()))));
    }
    public static List<Stat> attachmentModifiers(ItemStack stack){
        var result=new ArrayList<Stat>();
        if(!(stack.getItem() instanceof NativeAttachment a))return result;
        if(a.noiseRadiusMultiplier()!=1)result.add(new Stat(NOISE,Component.literal(percentChange(a.noiseRadiusMultiplier()))));
        if(a instanceof NativeMagazineItem m){
            var gun=NativeGunData.get(m.compatibleGun());
            result.add(new Stat(MAGAZINE,Component.literal(gun.magazineCapacity()+" → "+m.capacity())));
        }
        if(a instanceof NativeSightItem)result.add(new Stat(ADS,value("red_dot")));
        return List.copyOf(result);
    }
    public static void addGunStats(List<Component> lines,ItemStack stack,NativeGunDefinition definition){for(var stat:gunStats(stack,definition))addStat(lines,stat);}
    public static void addAttachmentModifiers(List<Component> lines,ItemStack stack){for(var stat:attachmentModifiers(stack))addStat(lines,stat);}
    public static void gun(List<Component> lines,ItemStack stack,NativeGunDefinition definition,String description){
        addDescription(lines,description);addSeparator(lines);addGunStats(lines,stack,definition);addSeparator(lines);
    }
    public static void attachment(List<Component> lines,ItemStack stack,String description){
        addDescription(lines,description);addSeparator(lines);addAttachmentModifiers(lines,stack);addSeparator(lines);
    }
    private AflEquipmentTooltip(){}
}
