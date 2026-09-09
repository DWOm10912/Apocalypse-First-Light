package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.client.*;
import com.antaurora.apofirstlight.registry.*;
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

/** Opt-in isolated-world graphical test; actual overlay clicks and camera assertions. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class MaintenanceFacingProbe {
    private static final Direction[] FACES={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static int ticks,step=-1,index;
    private static boolean done;
    private static Vec3 originalPosition;
    private static float originalYaw,originalPitch;
    private static CameraType originalType;
    private static BlockPos root(){return new BlockPos(50+index*6,120,0);}
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static net.minecraft.server.level.ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());}
    private static GunMaintenanceBenchBlockEntity bench(){return (GunMaintenanceBenchBlockEntity)mc().getSingleplayerServer().overworld().getBlockEntity(root());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(done||!Boolean.getBoolean("afl.maintenanceFacings")||e.phase!=TickEvent.Phase.END||mc().level==null||mc().player==null)return;
        if(++ticks<100||ticks%40!=0)return;step++;
        try{
            if(Boolean.getBoolean("afl.maintenanceVerify")){
                if(step==0)server(()->{check(bench()!=null&&bench().getItem(0).is(index==3?AflItems.BR51_01.get():AflItems.P9_01.get()),"disk gun "+FACES[index]);positionPlayer();open();});
                else {shot("disk");mc().player.closeContainer();if(++index==4)finish("PASS disk reload P9 + red dot and BR51, four facings");else step=-1;}return;
            }
            switch(step){
                case 0 -> server(()->{
                    var level=player().serverLevel();player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    if(bench()!=null)bench().clearContent();
                    for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)level.setBlock(root().offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),3);
                    var block=AflBlocks.GUN_MAINTENANCE_BENCH.get();
                    for(var part:StaticWorkstationBlock.Part.values())level.setBlock(StaticWorkstationBlock.partPosition(root(),FACES[index],part),block.stateFor(FACES[index],part),3);
                    positionPlayer();player().getInventory().clearContent();player().getInventory().setItem(0,new ItemStack(Items.STONE,32));
                    var p9=new ItemStack(AflItems.P9_01.get());var tag=new net.minecraft.nbt.CompoundTag();tag.put("SIGHT",new ItemStack(AflItems.PISTOL_RED_DOT.get()).save(new net.minecraft.nbt.CompoundTag()));p9.getOrCreateTag().put("AflAttachments",tag);
                    player().getInventory().setItem(1,p9);player().getInventory().setItem(2,new ItemStack(AflItems.BR51_01.get()));
                    player().getInventory().selected=index; // stone, P9, BR51, empty
                });
                case 1 -> {originalPosition=mc().player.position();originalYaw=mc().player.getYRot();originalPitch=mc().player.getXRot();originalType=CameraType.values()[index%3];mc().options.setCameraType(originalType);server(MaintenanceFacingProbe::open);}
                case 2 -> {shot("empty");clickSlot(1);mc().options.keyUp.setDown(true);mc().options.keyAttack.setDown(true);mc().options.keyJump.setDown(true);}
                case 3 -> {check(mc().player.position().distanceTo(originalPosition)<.02,"movement lock");check(mc().player.getInventory().getItem(1).isEmpty(),"real P9 transfer");KeyMapping.releaseAll();shot("p9");clickGun();}
                case 4 -> {check(mc().player.getInventory().getItem(1).is(AflItems.P9_01.get()),"P9 return");clickSlot(2);}
                case 5 -> {check(mc().player.getInventory().getItem(2).isEmpty(),"real BR51 transfer");shot("br51");mc().screen.keyPressed(256,0,0);}
                case 6 -> {check(!MaintenanceModeClientState.INSTANCE.active()&&mc().screen==null,"exit state");check(mc().options.getCameraType()==originalType,"camera type restored");check(Math.abs(mc().player.getYRot()-originalYaw)<.01&&Math.abs(mc().player.getXRot()-originalPitch)<.01,"player rotation preserved: "+originalYaw+","+originalPitch+" -> "+mc().player.getYRot()+","+mc().player.getXRot());check(!mc().options.keyAttack.isDown()&&!mc().options.keyUp.isDown(),"input restored");shot("outside");server(MaintenanceFacingProbe::open);}
                case 7 -> clickGun();
                case 8 -> clickSlot(index==3?2:1);
                case 9 -> {mc().player.closeContainer();if(++index==4)finish("PASS four facings, camera/input restore, picking both guns, saved both types");else step=-1;}
            }
        }catch(Exception ex){finish("FAIL "+ex);}
    }
    private static void positionPlayer(){var p=MaintenanceCameraController.world(root(),FACES[index],new Vec3(1,0,-1));player().teleportTo(player().serverLevel(),p.x,p.y,p.z,java.util.Set.of(),MaintenanceCameraController.yaw(FACES[index]),20);}
    private static void open(){var pos=StaticWorkstationBlock.partPosition(root(),FACES[index],StaticWorkstationBlock.Part.UPPER_SIDE);player().serverLevel().getBlockState(pos).use(player().serverLevel(),player(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),FACES[index],pos,false));}
    private static void clickSlot(int i){var s=(GunMaintenanceScreen)mc().screen;s.mouseClicked(s.hotbarX()+i*20+10,s.hotbarY()+10,0);}
    private static void clickGun(){var s=(GunMaintenanceScreen)mc().screen;check(s.placeholderSlot()>=0,"owner placeholder");clickSlot(s.placeholderSlot());}
    private static void shot(String suffix){if(mc().screen instanceof GunMaintenanceScreen){var s=MaintenanceModeClientState.INSTANCE;check(s.ready(),"mode ready");check(mc().gameRenderer.getMainCamera().getPosition().distanceTo(s.cameraPosition())<.01,"camera position "+FACES[index]);check(Math.abs(mc().gameRenderer.getMainCamera().getXRot()-MaintenanceCameraController.PITCH)<.01,"pitch");}Screenshot.grab(mc().gameDirectory,"maintenance_v2_"+FACES[index]+"_"+suffix+".png",mc().getMainRenderTarget(),m->{});}
    private static void server(Runnable r){mc().getSingleplayerServer().execute(()->{try{r.run();}catch(Exception ex){mc().execute(()->finish("FAIL "+ex));}});}
    private static void check(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
    private static void finish(String text){done=true;com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[MAINTENANCE FACING] {}",text);mc().stop();}
}
