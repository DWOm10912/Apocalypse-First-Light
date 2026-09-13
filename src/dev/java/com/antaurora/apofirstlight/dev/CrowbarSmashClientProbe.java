package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.client.CrowbarSmashClient;
import com.antaurora.apofirstlight.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in disposable-world end-to-end visual probe, excluded from the production jar. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class CrowbarSmashClientProbe {
    private static int tick;private static boolean setup,triggered,seen,done;private static final Set<Integer> shots=new HashSet<>();
    private static final BlockPos POS=new BlockPos(20,120,0);
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("afl.crowbarSmashProbe")||done||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        mc.player.getInventory().selected=0;
        // Keep this opt-in graphical probe aimed at its fixture despite window-focus mouse deltas.
        if(setup){mc.player.setYRot(0);mc.player.setXRot(12);mc.gameRenderer.pick(1);}
        if(++tick<100)return;
        if(!setup){setup=true;mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var l=p.serverLevel();
            l.setDayTime(6000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
            for(int x=16;x<25;x++)for(int z=-5;z<4;z++){l.setBlock(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState(),3);for(int y=120;y<125;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
            var state=AflBlocks.VENDING_MACHINE.get().defaultBlockState();l.setBlock(POS,state,2);l.setBlock(POS.above(),state.setValue(VendingMachineBlock.HALF,DoubleBlockHalf.UPPER),3);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(l,20.55,120,-1.8,Set.of(),0,12);
            p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.CROWBAR.get()));p.inventoryMenu.broadcastChanges();
        });return;}
        if(!triggered&&tick>=160&&mc.screen==null&&mc.hitResult instanceof BlockHitResult hit){
            shot("idle_hint");mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);triggered=true;
        }
        if(CrowbarSmashClient.active())seen=true;
        if(triggered&&seen&&!CrowbarSmashClient.active()){
            done=true;boolean broken=mc.level.getBlockState(POS).getValue(VendingMachineBlock.BROKEN);
            ApocalypseFirstLight.LOGGER.info("[CROWBAR SMASH PROBE] {}: action completed, broken={}",broken?"PASS":"FAIL",broken);shot("completed");mc.stop();
        }
        if(tick>350){done=true;ApocalypseFirstLight.LOGGER.error("[CROWBAR SMASH PROBE] FAIL timeout hit={}",mc.hitResult);mc.stop();}
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){
        if(!Boolean.getBoolean("afl.crowbarSmashProbe")||done||e.phase!=TickEvent.Phase.END||!CrowbarSmashClient.active())return;
        int age=(int)CrowbarSmashClient.elapsed(0);
        if(Set.of(1,8,10,11,12,13,18,26,32).contains(age)&&shots.add(age)){
            shot("t"+age);var mc=Minecraft.getInstance();
            ApocalypseFirstLight.LOGGER.info("[CROWBAR SMASH PROBE] frame={} broken={}",age,mc.level.getBlockState(POS).getValue(VendingMachineBlock.BROKEN));
        }
    }
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"crowbar_smash_"+name+".png",mc.getMainRenderTarget(),m->{});}
}
