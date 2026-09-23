package com.antaurora.apofirstlight.tooltip;

import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static com.antaurora.apofirstlight.tooltip.AflTooltipStatType.*;

/** Read-only presentation. Never writes ammo, attachments or gameplay definitions. */
public final class AflEquipmentTooltip {
    public static final int VALUE_COLOR=0xE0E0E0;
    public record Stat(AflTooltipStatType type,Component value){}
    public static String number(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    private static Component value(String key,Object... args){return Component.translatable("tooltip.apocalypse_firstlight.value."+key,args);}
    public static void addDescription(List<Component> lines,String key){
        lines.add(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY).withStyle(s->s.withItalic(false)));
    }
    public static void addStat(List<Component> lines,Stat stat){
        var label=Component.translatable(stat.type().key()).withStyle(Style.EMPTY.withColor(stat.type().color()).withItalic(false));
        label.append(Component.translatable("tooltip.apocalypse_firstlight.stat.separator"));
        label.append(stat.value().copy().withStyle(Style.EMPTY.withColor(VALUE_COLOR).withItalic(false)));
        lines.add(label);
    }
    public static List<Stat> gunStats(ItemStack stack,NativeGunDefinition d){
        var stats = new ArrayList<Stat>();
        stats.add(new Stat(DAMAGE,Component.literal(number(d.baseDamage())
                + (d.pelletsPerShot() == 1 ? "" : " × " + d.pelletsPerShot()))));
        if (d.actionType() == NativeActionType.BREAK_ACTION)
            stats.add(new Stat(CAPACITY,Component.literal(Integer.toString(d.magazineCapacity()))));
        stats.add(new Stat(FIRE_MODE,Component.translatable("fire_mode.apocalypse_firstlight."+NativeFireModes.current(stack,d).key())));
        stats.add(new Stat(RANGE,value("blocks",number(d.effectiveRange()))));
        stats.add(new Stat(SOUND_RADIUS,value("noise_radius",number(NativeGunNoise.resolve(stack,d).radius()))));
        return List.copyOf(stats);
    }
    public static void addGunStats(List<Component> lines,ItemStack stack,NativeGunDefinition definition){for(var stat:gunStats(stack,definition))addStat(lines,stat);}
    public static void gun(List<Component> lines,ItemStack stack,NativeGunDefinition definition,String description){
        addDescription(lines,description);
        // A readable text fallback for callers that do not use Forge's component gathering hook.
        lines.add(Component.translatable(GunAmmoTooltipComponent.ROW_KEY,
                GunAmmoTooltipComponent.resolve(definition).ammoName()));
        addGunStats(lines,stack,definition);
    }
    public static void attachment(List<Component> lines,ItemStack stack,String description){
        addDescription(lines,description);
    }
    private AflEquipmentTooltip(){}
}
