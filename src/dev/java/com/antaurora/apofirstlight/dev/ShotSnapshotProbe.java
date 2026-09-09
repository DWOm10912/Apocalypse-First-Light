package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.weapon.client.*;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class ShotSnapshotProbe {
    private static int tick,stage,index;private static long id;private static boolean done;
    private static NativeShotVisualSnapshot.Snapshot frozen;
    private static long captured;
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e)throws Exception{
        if(!Boolean.getBoolean("afl.shotSnapshotProbe")||done||e.phase!=TickEvent.Phase.END)return;
        var f=NativeShotVisualSnapshot.class.getDeclaredField("lastConfirmed");f.setAccessible(true);long shot=f.getLong(null);
        if(shot>0&&shot!=captured){captured=shot;var mc=Minecraft.getInstance();net.minecraft.client.Screenshot.grab(mc.gameDirectory,"snapshot_first_frame_"+shot+".png",mc.getMainRenderTarget(),m->{});}
    }
    @SuppressWarnings("unchecked") private static java.util.Map<Long,NativeShotVisualSnapshot.Snapshot> pending()throws Exception{
        var f=NativeShotVisualSnapshot.class.getDeclaredField("pending");f.setAccessible(true);return (java.util.Map<Long,NativeShotVisualSnapshot.Snapshot>)f.get(null);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("afl.shotSnapshotProbe")||done||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        mc.options.pauseOnLostFocus=false;
        if(++tick<100||tick%45!=0)return;
        try{
            if(stage==0){
                mc.options.framerateLimit().set(index<6?60:120);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                mc.options.keyUse.setDown(index%2==1);
                int variant=index%6;
                mc.getSingleplayerServer().execute(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    p.closeContainer();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    p.teleportTo(p.serverLevel(),20,125,-3,java.util.Set.of(),0,0);
                    p.getAbilities().flying=true;p.onUpdateAbilities();p.getInventory().selected=0;
                    var gun=new ItemStack(variant>=4?AflItems.BR51_01.get():AflItems.P9_01.get());
                    NativeGunAmmo.set(gun,((NativeGunItem)gun.getItem()).definition(),10);
                    if(variant==2||variant==3){var tag=new net.minecraft.nbt.CompoundTag();tag.put("MUZZLE",new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()).save(new net.minecraft.nbt.CompoundTag()));tag.put("SIGHT",new ItemStack(AflItems.PISTOL_RED_DOT.get()).save(new net.minecraft.nbt.CompoundTag()));gun.getOrCreateTag().put("AflAttachments",tag);}
                    software.bernie.geckolib.animatable.GeoItem.getOrAssignId(gun,p.serverLevel());
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun);p.inventoryMenu.broadcastChanges();
                });stage=1;
            }else if(stage==1){
                id=NativeShotVisualSnapshot.capture();frozen=pending().get(id);
                if(frozen==null)throw new IllegalStateException("capture missing variant="+index);
                NativeGunRecoil.syncAimBeforeShot();AflNetwork.requestP901(false,0,id);stage=2;
            }else{
                var confirmed=NativeShotVisualSnapshot.class.getDeclaredField("lastConfirmed");confirmed.setAccessible(true);
                if(confirmed.getLong(null)!=id)throw new IllegalStateException("effect not confirmed id="+id);
                com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[SHOT PROBE] PASS variant={} cap={} id={} frozenOrigin={}",index%6,index<6?60:120,id,frozen.muzzle());
                stage=0;if(++index==12){done=true;mc.options.keyUse.setDown(false);mc.stop();}
            }
        }catch(Exception ex){done=true;com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.error("[SHOT PROBE] FAIL",ex);mc.stop();}
    }
}
