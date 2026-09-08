package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.client.GunMaintenanceScreen;
import com.antaurora.apofirstlight.registry.*;
import com.mojang.authlib.GameProfile;
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
import java.util.UUID;

/** Isolated integrated-client V2.1 entrypoint regression, no player-world access. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class MaintenanceReturnProbe {
    private static final BlockPos ROOT=new BlockPos(20,120,0);
    private static int ticks,step=-1;private static boolean done;
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static net.minecraft.server.level.ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());}
    private static GunMaintenanceBenchBlockEntity bench(){return (GunMaintenanceBenchBlockEntity)player().serverLevel().getBlockEntity(ROOT);}
    private static GunMaintenanceScreen screen(){return (GunMaintenanceScreen)mc().screen;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(done||!Boolean.getBoolean("afl.maintenanceReturnProbe")||event.phase!=TickEvent.Phase.END||mc().player==null||mc().level==null)return;
        if(++ticks<100||ticks%40!=0)return;step++;
        try{switch(step){
            case 0 -> server(()->{
                var level=player().serverLevel();player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                if(bench()!=null)bench().clearContent();
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setBlock(ROOT.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
                for(var part:StaticWorkstationBlock.Part.values())level.setBlock(StaticWorkstationBlock.partPosition(ROOT,Direction.NORTH,part),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(Direction.NORTH,part),3);
                player().teleportTo(level,21,120,-1,java.util.Set.of(),0,20);
                player().getInventory().clearContent();player().getInventory().selected=0;
                player().getInventory().setItem(4,new ItemStack(AflItems.P9_01.get()));player().getInventory().setItem(2,new ItemStack(AflItems.BR51_01.get()));open();
            });
            case 1 -> slot(4);
            case 2 -> {
                check(mc().player.getInventory().getItem(4).isEmpty()&&screen().placeholderSlot()==4,"real empty origin and visual placeholder");
                check(!screen().takeButtonVisible(),"no take button for owner");
                check(screen().returnTooltip(screen().hotbarX()+90,screen().hotbarY()+10)!=null,"placeholder tooltip");
                shot("owner_placeholder");
                boolean found=false;var s=screen();
                outer:for(int y=s.height/4;y<s.height*3/4;y+=2)for(int x=s.width/4;x<s.width*3/4;x+=2)if(s.gunHit(x,y)){
                    check(s.returnTooltip(x,y)==null,"world gun has no return tooltip");s.mouseClicked(x,y,0);found=true;break outer;
                }check(found,"world gun located for no-action test");
            }
            case 3 -> {check(!screen().getMenu().synchronizedBench().isEmpty(),"world click cannot return");slot(2);}
            case 4 -> {check(mc().player.getInventory().getItem(2).is(AflItems.BR51_01.get()),"other slot cannot replace stored gun");slot(4);}
            case 5 -> {check(mc().player.getInventory().getItem(4).is(AflItems.P9_01.get())&&screen().placeholderSlot()==-1,"origin returned and placeholder cleared");slot(4);}
            case 6 -> {mc().player.closeContainer();}
            case 7 -> server(()->{check(!bench().isEmpty(),"close persists");open();});
            case 8 -> {check(screen().placeholderSlot()==4,"reopen metadata");server(()->{player().getInventory().setItem(4,new ItemStack(Items.DIRT,3));player().containerMenu.broadcastChanges();});}
            case 9 -> {shot("occupied_origin");slot(4);}
            case 10 -> {
                check(mc().player.getInventory().getItem(4).is(Items.DIRT)&&mc().player.getInventory().getItem(0).is(AflItems.P9_01.get()),"occupied origin fallback preserved dirt");
                server(()->{
                    var other=net.minecraftforge.common.util.FakePlayerFactory.get(player().serverLevel(),new GameProfile(UUID.randomUUID(),"return_origin"));other.setPos(21,120,-1);
                    other.getInventory().setItem(5,new ItemStack(AflItems.BR51_01.get()));check(bench().insertFrom(other,5),"other player's stored gun");
                });
            }
            case 11 -> {
                check(screen().placeholderSlot()==-1&&screen().takeButtonVisible(),"non owner button without foreign placeholder");
                check(screen().returnTooltip(screen().takeButtonX()+10,screen().hotbarY()+10)!=null,"take tooltip");shot("non_owner_take");slot(0);
            }
            case 12 -> {check(!screen().getMenu().synchronizedBench().isEmpty(),"non owner hotbar cannot replace gun");screen().mouseClicked(screen().takeButtonX()+10,screen().hotbarY()+10,0);}
            case 13 -> {check(screen().getMenu().synchronizedBench().isEmpty()&&!screen().takeButtonVisible()&&mc().player.getInventory().getItem(1).is(AflItems.BR51_01.get()),"non owner take and live UI cleared");finish("PASS placeholder, tooltip, no world return, occupied fallback, non-owner take, reopen persistence");}
        }}catch(Exception e){finish("FAIL "+e);}
    }
    private static void slot(int i){var s=screen();s.mouseClicked(s.hotbarX()+i*20+10,s.hotbarY()+10,0);}
    private static void open(){var pos=ROOT.east().above();player().serverLevel().getBlockState(pos).use(player().serverLevel(),player(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));}
    private static void server(Runnable task){mc().getSingleplayerServer().execute(()->{try{task.run();}catch(Exception e){mc().execute(()->finish("FAIL "+e));}});}
    private static void shot(String name){Screenshot.grab(mc().gameDirectory,"maintenance_v21_"+name+".png",mc().getMainRenderTarget(),m->{});}
    private static void check(boolean ok,String what){if(!ok)throw new IllegalStateException(what);}
    private static void finish(String result){done=true;com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[MAINTENANCE RETURN] {}",result);mc().stop();}
}
