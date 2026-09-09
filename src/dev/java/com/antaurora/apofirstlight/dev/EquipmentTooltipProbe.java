package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.tooltip.*;
import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Presentation-only fixtures; no world inventory or gameplay data mutations. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class EquipmentTooltipProbe {
    private static int ticks,stage;private static boolean done,loading;private static String oldLanguage;private static int oldScale;
    private static ItemStack[] items;
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void attach(ItemStack gun,NativeAttachment.Slot slot,Item item){
        var root=gun.getOrCreateTag().getCompound("AflAttachments");root.put(slot.name(),new ItemStack(item).save(new net.minecraft.nbt.CompoundTag()));gun.getOrCreateTag().put("AflAttachments",root);
    }
    private static void fixture(boolean extended){
        items=new ItemStack[]{new ItemStack(AflItems.P9_01.get()),new ItemStack(AflItems.BR51_01.get()),new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()),new ItemStack(AflItems.RIFLE_SUPPRESSOR_01.get()),
                new ItemStack(AflItems.P9_01_EXTENDED_MAGAZINE.get()),new ItemStack(AflItems.BR51_EXTENDED_MAGAZINE_35.get()),new ItemStack(AflItems.PISTOL_RED_DOT.get()),new ItemStack(AflItems.RIFLE_RED_DOT_01.get())};
        if(extended){attach(items[0],NativeAttachment.Slot.MAGAZINE,AflItems.P9_01_EXTENDED_MAGAZINE.get());attach(items[1],NativeAttachment.Slot.MAGAZINE,AflItems.BR51_EXTENDED_MAGAZINE_35.get());
            attach(items[0],NativeAttachment.Slot.MUZZLE,AflItems.PISTOL_SUPPRESSOR_01.get());attach(items[1],NativeAttachment.Slot.MUZZLE,AflItems.RIFLE_SUPPRESSOR_01.get());}
    }
    private static void validate(boolean extended){
        int[] colors={0xD97878,0xD6B46A,0x79AFC9,0xD79A68,0x82B98A,0xC49567,0x9B8FC3,0x79AAA7};
        for(var type:AflTooltipStatType.values())check(type.color()==colors[type.ordinal()],"semantic color "+type);
        check(AflEquipmentTooltip.percentChange(.05).equals("-95%")&&AflEquipmentTooltip.percentChange(.2).equals("-80%"),"real multiplier conversion");
        for(int i=0;i<items.length;i++){
            var stack=items[i];var before=stack.copy();var lines=new ArrayList<Component>();stack.getItem().appendHoverText(stack,mc().level,lines,TooltipFlag.NORMAL);
            check(lines.get(0).getStyle().getColor().getValue()==0xAAAAAA&&!lines.get(0).getStyle().isItalic(),"description style");
            check(lines.get(1).getStyle().getColor().getValue()==0x555555&&lines.get(lines.size()-1).getString().equals(lines.get(1).getString()),"separator");
            for(var line:lines)check(!line.getString().contains("tooltip.apocalypse_firstlight.")&&!line.getString().contains("Press V"),"localized non-debug content");
            check(ItemStack.matches(stack,before),"read-only tooltip");
            if(i<2){var d=((NativeGunItem)stack.getItem()).definition();var stats=AflEquipmentTooltip.gunStats(stack,d);
                check(stats.get(0).value().getString().equals(AflEquipmentTooltip.number(d.baseDamage())),"definition damage");
                check(stats.get(2).value().getString().contains(""+(i==0?(extended?24:17):(extended?35:20))),"resolved capacity");
                check(stats.get(5).value().getString().startsWith(""+(i==0?(extended?3:64):(extended?6:112))),"resolved noise");
                check(stats.get(4).value().getString().startsWith(AflEquipmentTooltip.number(d.effectiveRange())),"effective range");
            }else{var stats=AflEquipmentTooltip.attachmentModifiers(stack);check(stats.size()==1,"current modifier count");
                if(i<4)check(stats.get(0).value().getString().equals("-95%"),"suppressor");
                if(i==4||i==5)check(stats.get(0).value().getString().equals(i==4?"17 → 24":"20 → 35"),"magazine delta");
                if(i>5)check(stats.get(0).type()==AflTooltipStatType.ADS,"sight category");}
        }
    }
    private static void language(String language){loading=true;mc().getLanguageManager().setSelected(language);mc().reloadResourcePacks().whenComplete((v,e)->mc().execute(()->{loading=false;if(e!=null)finish("FAIL resource reload "+e);}));}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(done||!Boolean.getBoolean("afl.equipmentTooltipProbe")||e.phase!=TickEvent.Phase.END||mc().player==null||loading)return;
        mc().options.pauseOnLostFocus=false;
        if(++ticks%70!=0)return;
        try{switch(stage++){
            case 0 -> {oldLanguage=mc().getLanguageManager().getSelected();oldScale=mc().options.guiScale().get();mc().options.guiScale().set(2);mc().resizeDisplay();language("zh_cn");}
            case 1 -> {fixture(false);validate(false);mc().setScreen(new Preview());}
            case 2 -> {shot("zh_standard");fixture(true);validate(true);}
            case 3 -> {shot("zh_equipped");language("en_us");}
            case 4 -> {fixture(false);validate(false);mc().setScreen(new Preview());}
            case 5 -> {shot("en_standard");fixture(true);validate(true);}
            case 6 -> {shot("en_equipped");finish("PASS zh/en styles, colors, resolved stats, modifiers and read-only fixtures; screenshots require review");}
        }}catch(Exception ex){finish("FAIL "+ex);}
    }
    private static void shot(String name){Screenshot.grab(mc().gameDirectory,"equipment_tooltip_"+name+".png",mc().getMainRenderTarget(),c->{});}
    private static void finish(String result){done=true;com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[EQUIPMENT TOOLTIP] {}",result);
        if(oldLanguage!=null)mc().getLanguageManager().setSelected(oldLanguage);mc().options.guiScale().set(oldScale);mc().stop();}
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("Equipment Tooltip V1"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF20252B);
            for(int i=0;i<items.length;i++){int x=12+(i%4)*(width/4),y=50+(i/4)*(height/2);
                g.renderItem(items[i],x,y-26);g.renderTooltip(font,items[i],x,y);}
        }
    }
}
