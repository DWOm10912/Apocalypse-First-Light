package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.client.FieldAttachmentScreen;
import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;

/** Client presentation lifetime only. Transaction authorization stays on the server. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class FieldAttachmentViewState {
    public enum Phase { CLOSED, ENTERING, OPEN, EXITING }
    private static Phase phase=Phase.CLOSED;
    private static final NativeAdsProgress progress=new NativeAdsProgress();
    private static Object level,player;
    private static int slot;
    private static long gunId;
    private static ItemStack snapshot=ItemStack.EMPTY;
    private static FieldAttachmentScreen screen;
    private static FieldAttachmentViewProfile profile=FieldAttachmentViewProfile.DEFAULT;
    private static String cancellationReason="";
    public static boolean isActive(){return phase!=Phase.CLOSED;}
    public static boolean isOpen(){return phase==Phase.OPEN;}
    public static Phase phase(){return phase;}
    public static float progress(float partial){float p=progress.sample(partial);return p*p*(3-2*p);}
    public static FieldAttachmentViewProfile profile(){return profile;}
    public static String cancellationReason(){return cancellationReason;}
    public static NativeAttachment.Slot selectedSlot(){return screen==null?null:screen.selectedSlot();}
    public static boolean pending(){return screen!=null&&screen.pending();}
    public static boolean matches(ItemStack stack){
        return isActive()&&stack.getItem()==snapshot.getItem()&&GeoItem.getId(stack)==gunId;
    }
    public static void open(){
        var mc=Minecraft.getInstance();var p=mc.player;
        if(isActive()||p==null||mc.level==null||mc.screen!=null||!p.isAlive()||p.isSpectator()
                ||!mc.options.getCameraType().isFirstPerson()||p.isUsingItem()||p.isSprinting()
                ||!mc.isWindowActive()||mc.options.keyAttack.isDown()||NativeGunInput.firing()
                ||!NativeGunInspect.action().isEmpty()||NativeGunInspect.blocksAds())return;
        var stack=p.getMainHandItem();
        if(stack.getItem() instanceof CatNativeGunItem){
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.apocalypse_firstlight.cat.maintenance_refused"),true);return;
        }
        if(!AttachmentModificationPolicy.hasSlots(stack)||GeoItem.getId(stack)==Long.MAX_VALUE)return;
        slot=p.getInventory().selected;gunId=GeoItem.getId(stack);snapshot=stack.copy();
        level=mc.level;player=p;profile=FieldAttachmentViewProfile.load(stack);cancellationReason="";
        NativeGunAds.leaveForField();progress.reset();phase=Phase.ENTERING;
        screen=new FieldAttachmentScreen();KeyMapping.releaseAll();mc.setScreen(screen);
    }
    public static void exit(String reason){
        if(!isActive()||phase==Phase.EXITING)return;
        cancellationReason=reason;phase=Phase.EXITING;
        if(screen!=null)screen.cancelOperation();
        KeyMapping.releaseAll();
    }
    public static void removed(FieldAttachmentScreen owner){
        if(screen!=owner)return;
        owner.cancelOperation();phase=Phase.CLOSED;progress.reset();snapshot=ItemStack.EMPTY;screen=null;
        FieldAttachmentHotspots.clear();KeyMapping.releaseAll();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!isActive())return;
        var mc=Minecraft.getInstance();var p=mc.player;
        if(mc.screen!=screen){removed(screen);return;}
        if(p==null||p!=player||mc.level!=level||!p.isAlive()||p.isSpectator()
                ||!mc.options.getCameraType().isFirstPerson()||p.getInventory().selected!=slot
                ||!matches(p.getMainHandItem())||!AttachmentModificationPolicy.hasSlots(p.getMainHandItem())){
            cancellationReason="target invalid";var owner=screen;removed(owner);
            if(mc.screen==owner)mc.setScreen(null);return;
        }
        if(!NativeGunInspect.action().isEmpty()||p.isUsingItem())exit("gun action");
        progress.tick(phase!=Phase.EXITING,profile.enterTicks(),profile.exitTicks());
        if(phase==Phase.ENTERING&&progress.sample(0)>=1)phase=Phase.OPEN;
        if(phase==Phase.EXITING&&progress.sample(0)<=0)mc.setScreen(null);
    }
    @SubscribeEvent public static void disconnect(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){
        if(screen!=null)removed(screen);
    }
    private FieldAttachmentViewState(){}
}
