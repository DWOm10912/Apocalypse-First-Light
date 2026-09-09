package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.client.*;
import com.antaurora.apofirstlight.registry.*;
import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated existing development world only; drives the real Screen and real C2S transaction. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class MaintenanceAttachmentsProbe {
    private static boolean rifle(){return Boolean.getBoolean("afl.rifleMaintenanceProbe");}
    private static final BlockPos ROOT=new BlockPos(20,120,0);
    private static int ticks,step=-1;private static boolean done;
    private static int operationSounds,soundTick=-1000;
    private static ItemStack beforeSound=ItemStack.EMPTY;
    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event){
        if(!Boolean.getBoolean("afl.maintenanceAttachmentsProbe")||event.getSound()==null)return;
        if(event.getSound().getLocation().toString().equals("apocalypse_firstlight:attachment_operation")){
            operationSounds++;soundTick=ticks;beforeSound=screen().getMenu().synchronizedBench().getItem(0).copy();
        }
    }
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static net.minecraft.server.level.ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());}
    private static GunMaintenanceScreen screen(){return (GunMaintenanceScreen)mc().screen;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(done||!Boolean.getBoolean("afl.maintenanceAttachmentsProbe")||e.phase!=TickEvent.Phase.END||mc().player==null||mc().level==null)return;
        ticks++;
        if(mc().screen instanceof GunMaintenanceScreen&&ticks-soundTick>2&&ticks-soundTick<45){
            if(!ItemStack.matches(beforeSound,screen().getMenu().synchronizedBench().getItem(0))){finish("FAIL gun changed before operation SFX duration");return;}
        }
        if(ticks<100||ticks%70!=0)return;step++;
        try{switch(step){
            case 0 -> mc().getSingleplayerServer().execute(()->{
                var p=player();var l=p.serverLevel();l.setDayTime(6000);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                if(l.getBlockEntity(ROOT) instanceof GunMaintenanceBenchBlockEntity b)b.clearContent();
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)l.setBlock(ROOT.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
                for(var part:StaticWorkstationBlock.Part.values())l.setBlock(StaticWorkstationBlock.partPosition(ROOT,Direction.NORTH,part),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(Direction.NORTH,part),3);
                p.teleportTo(l,21,120,-1,java.util.Set.of(),0,20);p.getInventory().clearContent();
                p.getInventory().setItem(4,new ItemStack(rifle()?AflItems.BR51_01.get():AflItems.P9_01.get()));p.getInventory().setItem(23,new ItemStack(rifle()?AflItems.RIFLE_SUPPRESSOR_01.get():AflItems.PISTOL_SUPPRESSOR_01.get()));p.getInventory().setItem(24,new ItemStack(AflItems.PISTOL_RED_DOT.get()));
                var pos=ROOT.east().above();l.getBlockState(pos).use(l,p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
            });
            case 1 -> slot(4);
            case 2 -> hotspot(NativeAttachment.Slot.MUZZLE);
            case 3 -> {shot("muzzle_context");button(NativeAttachment.Slot.MUZZLE,false);}
            case 4 -> {shot("muzzle_candidates");check(mc().player.getInventory().getItem(23).is(rifle()?AflItems.RIFLE_SUPPRESSOR_01.get():AflItems.PISTOL_SUPPRESSOR_01.get()),"candidate not moved");slot(0);}
            case 5 -> {check(!NativeAttachments.active(screen().getMenu().synchronizedBench().getItem(0),NativeAttachment.Slot.MUZZLE).isEmpty(),"C2S suppressor install");check(mc().player.getInventory().getItem(23).isEmpty(),"source consumed");shot("installed_muzzle");if(rifle()){hotspot(NativeAttachment.Slot.MUZZLE);step=8;}else hotspot(NativeAttachment.Slot.SIGHT);}
            case 6 -> button(NativeAttachment.Slot.SIGHT,false);
            case 7 -> slot(0);
            case 8 -> {check(!NativeAttachments.activeSight(screen().getMenu().synchronizedBench().getItem(0)).isEmpty(),"C2S sight install");shot("both");hotspot(NativeAttachment.Slot.SIGHT);}
            case 9 -> button(rifle()?NativeAttachment.Slot.MUZZLE:NativeAttachment.Slot.SIGHT,true);
            case 10 -> {var gun=screen().getMenu().synchronizedBench().getItem(0);if(rifle()){check(NativeAttachments.active(gun,NativeAttachment.Slot.MUZZLE).isEmpty()&&operationSounds==2,"rifle detach and two delayed SFX");finish("PASS rifle mouse hotspot/context/candidate install/remove, early-state guard, two shared SFX");}else{check(NativeAttachments.activeSight(gun).isEmpty()&&!NativeAttachments.active(gun,NativeAttachment.Slot.MUZZLE).isEmpty(),"independent removal");hotspot(NativeAttachment.Slot.MUZZLE);}}
            case 11 -> button(NativeAttachment.Slot.MUZZLE,false);
            case 12 -> {screen().keyPressed(256,0,0);check(mc().screen instanceof GunMaintenanceScreen,"Esc only cancels selection");slot(4);}
            case 13 -> {check(mc().player.getInventory().getItem(4).is(AflItems.P9_01.get())&&!NativeAttachments.active(mc().player.getInventory().getItem(4),NativeAttachment.Slot.MUZZLE).isEmpty(),"origin return persists attachment");check(operationSounds==3,"exactly one shared SFX for each approved operation");finish("PASS MouseHandler C2S install both/remove/cancel/origin; 3 shared SFX and early-state guard; screenshots captured");}
        }}catch(Exception ex){finish("FAIL "+ex);}
    }
    private static void hotspot(NativeAttachment.Slot target){var s=screen();var p=MaintenanceHotspots.project(target,s.width,s.height);check(p!=null&&p.x()>0&&p.x()<s.width&&p.y()>0&&p.y()<s.height,"projected hotspot");s.mouseClicked(p.x(),p.y(),0);}
    private static void button(NativeAttachment.Slot target,boolean remove){var s=screen();var p=MaintenanceHotspots.project(target,s.width,s.height);int x=MaintenanceAttachmentHud.contextX(p.x(),s.width),y=Math.max(4,Math.min(s.height-106,(int)p.y()-24));s.mouseClicked(x+(remove?90:20),y+45,0);}
    private static void slot(int i){var s=screen();double x=s.hotbarX()+i*20+10,y=s.hotbarY()+10;
        try{
            var move=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(MouseHandler.class,"m_91561_",long.class,double.class,double.class);
            var press=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(MouseHandler.class,"m_91530_",long.class,int.class,int.class,int.class);
            long window=mc().getWindow().getWindow();
            move.invoke(mc().mouseHandler,window,x*mc().getWindow().getScreenWidth()/s.width,y*mc().getWindow().getScreenHeight()/s.height);
            press.invoke(mc().mouseHandler,window,0,1,0);press.invoke(mc().mouseHandler,window,0,0,0);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static void shot(String s){Screenshot.grab(mc().gameDirectory,"maintenance_attachments_"+s+".png",mc().getMainRenderTarget(),m->{});}
    private static void check(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    private static void finish(String s){done=true;com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[MAINTENANCE ATTACHMENT CLIENT] {}",s);mc().stop();}
}
