package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.registry.*;
import net.minecraft.client.*;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

/** Isolated graphical fixture. Does not run without explicit development property. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class ExtendedMagazineProbe {
    private static int ticks,step=-1;private static boolean done;
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static net.minecraft.server.level.ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());}
    private static void server(Runnable r){mc().getSingleplayerServer().execute(()->{try{r.run();}catch(Exception e){mc().execute(()->finish("FAIL "+e));}});}
    private static void shot(String name){Screenshot.grab(mc().gameDirectory,"magazine_"+name+".png",mc().getMainRenderTarget(),m->{});}
    private static void finish(String s){done=true;com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[MAGAZINE CLIENT] {}",s);mc().stop();}
    private static void gun(boolean extended,int ammo){
        var p=player();var gun=new ItemStack(AflItems.P9_01.get());
        if(extended){var tag=new net.minecraft.nbt.CompoundTag();tag.put("MAGAZINE",new ItemStack(AflItems.P9_01_EXTENDED_MAGAZINE.get()).save(new net.minecraft.nbt.CompoundTag()));gun.getOrCreateTag().put("AflAttachments",tag);}
        NativeGunAmmo.set(gun,NativeGunDefinition.P9_01,ammo);p.setItemInHand(InteractionHand.MAIN_HAND,gun);p.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(done||!Boolean.getBoolean("afl.extendedMagazineProbe")||e.phase!=TickEvent.Phase.END||mc().player==null||mc().level==null)return;
        mc().options.pauseOnLostFocus=false;
        int t=++ticks%80;
        if((step==3||step==5)&&(t==8||t==12||t==16||t==20||t==25))shot((step==3?"tactical_":"empty_")+t);
        if(ticks<120||t!=0)return;
        try{switch(++step){
            case 0->{mc().options.setCameraType(CameraType.FIRST_PERSON);server(()->{
                var p=player();p.closeContainer();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                p.serverLevel().setDayTime(6000);p.teleportTo(p.serverLevel(),20,125,-3,java.util.Set.of(),0,0);p.getAbilities().flying=true;p.onUpdateAbilities();
                p.getInventory().clearContent();p.getInventory().selected=0;gun(false,17);
            });}
            case 1->{shot("standard_fp");server(()->gun(true,17));}
            case 2->{
                if(NativeGunAmmo.capacity(mc().player.getMainHandItem(),NativeGunDefinition.P9_01)!=24)throw new IllegalStateException("client capacity");
                shot("extended_fp");com.antaurora.apofirstlight.network.AflNetwork.requestP901(false,0);
            }
            case 3->{shot("after_fire");com.antaurora.apofirstlight.network.AflNetwork.requestP901(true,0);}
            case 4->{if(NativeGunAmmo.read(mc().player.getMainHandItem(),NativeGunDefinition.P9_01)!=24)throw new IllegalStateException("reload24");server(()->{NativeGunAmmo.set(player().getMainHandItem(),NativeGunDefinition.P9_01,0);player().inventoryMenu.broadcastChanges();});}
            case 5->com.antaurora.apofirstlight.network.AflNetwork.requestP901(true,0);
            case 6->{shot("reload_complete");mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
            case 7->{shot("third_person");mc().options.setCameraType(CameraType.FIRST_PERSON);server(()->{
                var p=player();var root=new net.minecraft.core.BlockPos(20,120,0);
                for(var part:com.antaurora.apofirstlight.block.StaticWorkstationBlock.Part.values())p.serverLevel().setBlock(com.antaurora.apofirstlight.block.StaticWorkstationBlock.partPosition(root,net.minecraft.core.Direction.NORTH,part),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(net.minecraft.core.Direction.NORTH,part),3);
                var b=(com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity)p.serverLevel().getBlockEntity(root);b.clearContent();
                p.teleportTo(p.serverLevel(),21,120,-1.5,java.util.Set.of(),0,15);p.getAbilities().flying=false;p.onUpdateAbilities();
                var pos=root.east().above();p.serverLevel().getBlockState(pos).use(p.serverLevel(),p,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),net.minecraft.core.Direction.NORTH,pos,false));
            });}
            case 8->{var screen=(com.antaurora.apofirstlight.client.GunMaintenanceScreen)mc().screen;screen.mouseClicked(screen.hotbarX()+10,screen.hotbarY()+10,0);}
            case 9->{shot("maintenance");var screen=(com.antaurora.apofirstlight.client.GunMaintenanceScreen)mc().screen;
                if(com.antaurora.apofirstlight.client.MaintenanceHotspots.project(NativeAttachment.Slot.MAGAZINE,screen.width,screen.height)==null)throw new IllegalStateException("magazine hotspot");
                mc().player.closeContainer();server(()->{player().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.P9_01_EXTENDED_MAGAZINE.get()));player().inventoryMenu.broadcastChanges();});}
            case 10->{shot("standalone");mc().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc().player));}
            case 11->{shot("inventory");finish("PASS capacity packet, actual fire/reload24, maintenance hotspot; screenshots require inspection");}
        }}catch(Exception ex){finish("FAIL "+ex);}
    }
}
