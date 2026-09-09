package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.client.*;
import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.registry.*;
import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

/** Drives the real maintenance screen and delayed C2S operations. No retired V path. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class RifleRedDotProbe {
    private static final BlockPos ROOT=new BlockPos(20,120,0);
    private static int ticks,step=-1,sounds,soundTick=-1000;private static boolean done;
    private static ItemStack before=ItemStack.EMPTY;
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static GunMaintenanceScreen screen(){return (GunMaintenanceScreen)mc().screen;}
    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent e){
        if(!Boolean.getBoolean("afl.rifleRedDotProbe")||e.getSound()==null)return;
        if(e.getSound().getLocation().getPath().equals("attachment_operation")){sounds++;soundTick=ticks;before=screen().getMenu().synchronizedBench().getItem(0).copy();}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(done||!Boolean.getBoolean("afl.rifleRedDotProbe")||e.phase!=TickEvent.Phase.END||mc().player==null)return;
        mc().options.pauseOnLostFocus=false;ticks++;
        try{
            if(ticks-soundTick<49&&mc().screen instanceof GunMaintenanceScreen)check(ItemStack.matches(before,screen().getMenu().synchronizedBench().getItem(0)),"no early commit");
            if(ticks<140||ticks%70!=0)return;step++;
            switch(step){
                case 0 -> mc().getSingleplayerServer().execute(()->{
                    var p=mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());var l=p.serverLevel();
                    l.setDayTime(6000);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    if(l.getBlockEntity(ROOT) instanceof com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity b)b.clearContent();
                    for(int x=-2;x<=3;x++)for(int z=-2;z<=2;z++)l.setBlock(ROOT.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                    for(var part:StaticWorkstationBlock.Part.values())l.setBlock(StaticWorkstationBlock.partPosition(ROOT,Direction.NORTH,part),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(Direction.NORTH,part),3);
                    p.teleportTo(l,21,120,-1,java.util.Set.of(),0,10);p.getInventory().clearContent();
                    p.getInventory().setItem(4,new ItemStack(AflItems.BR51_01.get()));p.getInventory().setItem(23,new ItemStack(AflItems.RIFLE_SUPPRESSOR_01.get()));p.getInventory().setItem(24,new ItemStack(AflItems.RIFLE_RED_DOT_01.get()));
                    var pos=ROOT;l.getBlockState(pos).use(l,p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
                });
                case 1 -> slot(4);
                case 2 -> hotspot(NativeAttachment.Slot.MUZZLE);
                case 3 -> button(NativeAttachment.Slot.MUZZLE);
                case 4 -> slot(0);
                case 5 -> {check(!NativeAttachments.active(screen().getMenu().synchronizedBench().getItem(0),NativeAttachment.Slot.MUZZLE).isEmpty(),"muzzle installed");hotspot(NativeAttachment.Slot.SIGHT);}
                case 6 -> {shot("context");button(NativeAttachment.Slot.SIGHT);}
                case 7 -> {shot("candidates");slot(0);}
                case 8 -> {var gun=screen().getMenu().synchronizedBench().getItem(0);check(NativeAttachments.activeSight(gun).is(AflItems.RIFLE_RED_DOT_01.get())&&sounds==2,"dual install and two sounds");shot("bench");slot(4);}
                case 9 -> {mc().player.closeContainer();mc().player.getInventory().selected=4;mc().options.setCameraType(CameraType.FIRST_PERSON);mc().getSingleplayerServer().execute(()->{var p=mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());p.teleportTo(p.serverLevel(),21,120,-1,java.util.Set.of(),180,0);});}
                case 10 -> {check(NativeAttachments.activeSight(mc().player.getMainHandItem()).is(AflItems.RIFLE_RED_DOT_01.get()),"return persistence");shot("fp");mc().options.keyUse.setDown(true);}
                case 11 -> {check(mc().screen==null&&com.antaurora.apofirstlight.weapon.client.NativeGunAds.progress(1)>.99f,"actual ADS active, no reopened bench");shot("ads");var profile=com.antaurora.apofirstlight.weapon.client.NativeAdsProfile.forStack(mc().player.getMainHandItem());var v=profile.ads().transformPosition(new org.joml.Vector3f(profile.ax()/16,profile.ay()/16,profile.az()/16));check(Math.abs(v.x)<.00001&&Math.abs(v.y)<.00001,"ADS center math");mc().options.keyUse.setDown(false);mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
                case 12 -> {shot("tp");mc().options.setCameraType(CameraType.FIRST_PERSON);finish("PASS real maintenance install dual slots, delay guard, sound count, return, ADS math; screenshots require review");}
            }
        }catch(Exception ex){finish("FAIL step="+step+" "+ex);}
    }
    private static void hotspot(NativeAttachment.Slot target){var s=screen();var p=MaintenanceHotspots.project(target,s.width,s.height);check(p!=null,"hotspot exists");s.mouseClicked(p.x(),p.y(),0);}
    private static void button(NativeAttachment.Slot target){var s=screen();var p=MaintenanceHotspots.project(target,s.width,s.height);int x=MaintenanceAttachmentHud.contextX(p.x(),s.width),y=Math.max(4,Math.min(s.height-106,(int)p.y()-24));s.mouseClicked(x+20,y+45,0);}
    private static void slot(int i){var s=screen();double x=s.hotbarX()+i*20+10,y=s.hotbarY()+10;
        try{var move=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(MouseHandler.class,"m_91561_",long.class,double.class,double.class);var press=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(MouseHandler.class,"m_91530_",long.class,int.class,int.class,int.class);long w=mc().getWindow().getWindow();move.invoke(mc().mouseHandler,w,x*mc().getWindow().getScreenWidth()/s.width,y*mc().getWindow().getScreenHeight()/s.height);press.invoke(mc().mouseHandler,w,0,1,0);press.invoke(mc().mouseHandler,w,0,0,0);}catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private static void shot(String name){Screenshot.grab(mc().gameDirectory,"rifle_red_dot_"+name+".png",mc().getMainRenderTarget(),m->{});}
    private static void check(boolean b,String m){if(!b)throw new IllegalStateException(m);}
    private static void finish(String m){done=true;mc().options.keyUse.setDown(false);com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[RIFLE RED DOT CLIENT] {}",m);mc().stop();}
}
