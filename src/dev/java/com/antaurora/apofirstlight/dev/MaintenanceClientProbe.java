package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.client.GunMaintenanceScreen;
import com.antaurora.apofirstlight.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class MaintenanceClientProbe {
    private static final BlockPos ROOT=new BlockPos(20,120,0);
    private static int age,step=-1,wait;
    private static boolean done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("afl.maintenanceProbe")||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(++age<100)return;
        if(++wait<45)return;wait=0;step++;
        try {
            if(Boolean.getBoolean("afl.maintenanceVerify")){
                if(step==0){server(()->{var b=bench();if(b.isEmpty()||!b.getItem(0).is(AflItems.P9_01.get())||com.antaurora.apofirstlight.weapon.NativeAttachments.activeSight(b.getItem(0)).isEmpty())throw new IllegalStateException("saved gun/attachment missing");open();});return;}
                if(step==1){check(mc.screen instanceof GunMaintenanceScreen,"reload GUI");shot("reload_p9");finish("PASS disk reload P9 and attachment");}return;
            }
            switch(step){
                case 0 -> server(()->{
                    var l=mc.getSingleplayerServer().overworld();var p=player();p.setGameMode(GameType.CREATIVE);
                    l.setDayTime(6000);p.teleportTo(l,21,120, -2.3,java.util.Set.of(),0,20);
                    var block=AflBlocks.GUN_MAINTENANCE_BENCH.get();
                    for(var part:StaticWorkstationBlock.Part.values())l.setBlock(StaticWorkstationBlock.partPosition(ROOT,Direction.NORTH,part),Blocks.AIR.defaultBlockState(),3);
                    l.setBlock(ROOT.below(),Blocks.STONE.defaultBlockState(),3);l.setBlock(ROOT.east().below(),Blocks.STONE.defaultBlockState(),3);
                    for(var part:StaticWorkstationBlock.Part.values())l.setBlock(StaticWorkstationBlock.partPosition(ROOT,Direction.NORTH,part),block.stateFor(Direction.NORTH,part),3);
                    p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.STONE,32));
                    var gun=new ItemStack(AflItems.P9_01.get());var tag=new net.minecraft.nbt.CompoundTag();tag.put("SIGHT",new ItemStack(AflItems.PISTOL_RED_DOT.get()).save(new net.minecraft.nbt.CompoundTag()));gun.getOrCreateTag().put("AflAttachments",tag);
                    p.getInventory().setItem(1,gun);p.getInventory().setItem(2,new ItemStack(AflItems.BR51_01.get()));p.getInventory().selected=0;open();
                });
                case 1 -> {check(mc.screen instanceof GunMaintenanceScreen,"empty GUI opened");shot("empty");clickHotbar(1);}
                case 2 -> {check(!clientBench().isEmpty()&&mc.player.getInventory().getItem(1).isEmpty(),"real P9 transfer");shot("p9");mc.player.closeContainer();}
                case 3 -> {shot("world_p9");server(()->{player().getInventory().selected=2;open();});}
                case 4 -> {check(mc.screen instanceof GunMaintenanceScreen,"held BR opens GUI");clickCenter();}
                case 5 -> {check(clientBench().isEmpty()&&mc.player.getInventory().getItem(1).is(AflItems.P9_01.get()),"origin return");clickHotbar(2);}
                case 6 -> {check(clientBench().getItem(0).is(AflItems.BR51_01.get()),"BR transfer");shot("br51");mc.player.closeContainer();}
                case 7 -> {shot("world_br51");server(MaintenanceClientProbe::open);}
                case 8 -> clickCenter();
                case 9 -> clickHotbar(1);
                case 10 -> {check(clientBench().getItem(0).is(AflItems.P9_01.get()),"P9 saved on bench");mc.player.closeContainer();finish("PASS GUI transfers, reopen, world renders, saved P9");}
            }
        } catch(Exception e){finish("FAIL "+e);}
    }
    private static void server(Runnable task){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{task.run();}catch(Exception e){Minecraft.getInstance().execute(()->finish("FAIL "+e));}});}
    private static net.minecraft.server.level.ServerPlayer player(){var mc=Minecraft.getInstance();return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static GunMaintenanceBenchBlockEntity bench(){return (GunMaintenanceBenchBlockEntity)Minecraft.getInstance().getSingleplayerServer().overworld().getBlockEntity(ROOT);}
    private static GunMaintenanceBenchBlockEntity clientBench(){return (GunMaintenanceBenchBlockEntity)Minecraft.getInstance().level.getBlockEntity(ROOT);}
    private static void open(){var l=player().serverLevel();var pos=ROOT.east().above();l.getBlockState(pos).use(l,player(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));}
    private static void clickHotbar(int slot){var mc=Minecraft.getInstance();check(mc.screen instanceof GunMaintenanceScreen,"screen for click");var screen=(GunMaintenanceScreen)mc.screen;screen.mouseClicked(screen.hotbarX()+slot*20+10,screen.hotbarY()+10,0);}
    private static void clickCenter(){
        var screen=(GunMaintenanceScreen)Minecraft.getInstance().screen;
        check(screen.placeholderSlot()>=0,"owner return placeholder");clickHotbar(screen.placeholderSlot());
    }
    private static void shot(String name){var mc=Minecraft.getInstance();if(mc.screen instanceof GunMaintenanceScreen){var state=com.antaurora.apofirstlight.client.MaintenanceModeClientState.INSTANCE;check(state.active(),"active camera");check(mc.gameRenderer.getMainCamera().getPosition().distanceTo(state.cameraPosition())<.01,"actual camera position");check(Math.abs(mc.gameRenderer.getMainCamera().getXRot()-67.5)<.01,"actual camera pitch");}net.minecraft.client.Screenshot.grab(mc.gameDirectory,"maintenance_v2_"+name+".png",mc.getMainRenderTarget(),m->{});}
    private static void check(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
    private static void finish(String text){done=true;com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[MAINTENANCE CLIENT] {}",text);Minecraft.getInstance().stop();}
}
