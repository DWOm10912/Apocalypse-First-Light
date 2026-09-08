package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.registry.*;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.client.GunMaintenanceScreen;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated graphical/network smoke; screenshots still require inspection. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class NativeSuppressorProbe {
    private static int ticks,step=-1;private static boolean done;
    private static final BlockPos ROOT=new BlockPos(20,120,0);
    private static Minecraft mc(){return Minecraft.getInstance();}
    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent e){
        if(Boolean.getBoolean("afl.suppressorProbe")&&e.getSound()!=null&&e.getSound().getLocation().getPath().startsWith("p9_01"))
            com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[SUPPRESSOR AUDIO] {}",e.getSound().getLocation());
    }
    private static net.minecraft.server.level.ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(done||!Boolean.getBoolean("afl.suppressorProbe")||e.phase!=TickEvent.Phase.END||mc().player==null||mc().level==null)return;
        mc().options.pauseOnLostFocus=false;
        if(step==5&&ticks%60==8)shot("suppressed_firing_smoke");
        if(++ticks<120||ticks%60!=0)return;step++;
        try{switch(step){
            case 0 -> server(()->{
                var p=player();var level=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
                for(int x=-4;x<5;x++)for(int z=-4;z<5;z++)level.setBlock(ROOT.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                for(var part:StaticWorkstationBlock.Part.values())level.setBlock(StaticWorkstationBlock.partPosition(ROOT,Direction.NORTH,part),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(Direction.NORTH,part),3);
                var bench=(com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity)level.getBlockEntity(ROOT);bench.clearContent();
                p.teleportTo(level,21,120,-1.5,java.util.Set.of(),0,10);
                p.getInventory().clearContent();p.getInventory().selected=0;
                var gun=new ItemStack(AflItems.P9_01.get());NativeGunAmmo.set(gun,((NativeGunItem)gun.getItem()).definition(),10);
                p.setItemInHand(InteractionHand.MAIN_HAND,gun);p.inventoryMenu.broadcastChanges();
            });
            case 1 -> {mc().options.setCameraType(CameraType.FIRST_PERSON);shot("bare_fp");server(()->P901Actions.request(player(),false,0));}
            case 2 -> server(()->{player().setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()));player().inventoryMenu.broadcastChanges();});
            case 3 -> exchange();
            case 4 -> {
                check(!NativeAttachments.active(mc().player.getMainHandItem(),NativeAttachment.Slot.MUZZLE).isEmpty(),"packet install/client sync");
                var exit=new com.mojang.blaze3d.vertex.PoseStack();
                check(com.antaurora.apofirstlight.weapon.client.NativeMuzzleRendering.applyExit(mc().player.getMainHandItem(),exit),"cached exit loaded");
                var v=exit.last().pose().transformPosition(new org.joml.Vector3f());
                check(Math.abs(v.z+9.1F/16)<.0001&&Math.abs(v.x)<.0001&&Math.abs(v.y)<.0001,"exit exact/coaxial");
                server(()->{player().setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player().inventoryMenu.broadcastChanges();});
            }
            case 5 -> {shot("suppressed_fp");server(()->P901Actions.request(player(),false,0));}
            case 6 -> server(()->{player().setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AflItems.PISTOL_RED_DOT.get()));player().inventoryMenu.broadcastChanges();});
            case 7 -> exchange();
            case 8 -> {
                check(!NativeAttachments.activeSight(mc().player.getMainHandItem()).isEmpty(),"dual client sync");
                server(()->{player().setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player().inventoryMenu.broadcastChanges();});
            }
            case 9 -> {shot("dual_fp");mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
            case 10 -> {shot("dual_tp");mc().options.setCameraType(CameraType.FIRST_PERSON);server(()->{
                var p=player();var drop=new net.minecraft.world.entity.item.ItemEntity(p.serverLevel(),20.7,120,-.5,p.getMainHandItem().copy());drop.setNoPickUpDelay();drop.setPickUpDelay(32767);p.serverLevel().addFreshEntity(drop);
                p.teleportTo(p.serverLevel(),21,120,-2,java.util.Set.of(),0,40);
            });}
            case 11 -> {shot("dual_drop");server(()->{
                var pos=ROOT.east().above();player().serverLevel().getBlockState(pos).use(player().serverLevel(),player(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
            });}
            case 12 -> {
                check(mc().screen instanceof GunMaintenanceScreen,"maintenance opened");
                var s=(GunMaintenanceScreen)mc().screen;s.mouseClicked(s.hotbarX()+10,s.hotbarY()+10,0);
            }
            case 13 -> {
                var s=(GunMaintenanceScreen)mc().screen;var gun=s.getMenu().synchronizedBench().getItem(0);
                check(!NativeAttachments.activeSight(gun).isEmpty()&&!NativeAttachments.active(gun,NativeAttachment.Slot.MUZZLE).isEmpty(),"bench both");
                shot("dual_maintenance");mc().player.closeContainer();
            }
            case 14 -> {shot("dual_bench_world");server(()->{
                player().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.PISTOL_SUPPRESSOR_01.get()));player().inventoryMenu.broadcastChanges();
            });}
            case 15 -> {shot("standalone_hand");mc().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc().player));}
            case 16 -> {shot("standalone_inventory");finish("PASS network, dual slots, independent baked model, exact exit, maintenance data; screenshots captured");}
        }}catch(Exception ex){finish("FAIL "+ex);}
    }
    private static void exchange(){AflNetwork.requestSightExchange(mc().player.getInventory().selected,mc().player.getMainHandItem(),mc().player.getOffhandItem());}
    private static void server(Runnable action){mc().getSingleplayerServer().execute(()->{try{action.run();}catch(Exception ex){mc().execute(()->finish("FAIL "+ex));}});}
    private static void shot(String name){Screenshot.grab(mc().gameDirectory,"suppressor_"+name+".png",mc().getMainRenderTarget(),m->{});}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void finish(String message){done=true;com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[SUPPRESSOR CLIENT] {}",message);mc().stop();}
}
