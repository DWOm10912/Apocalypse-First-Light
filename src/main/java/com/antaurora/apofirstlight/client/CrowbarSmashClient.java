package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.interaction.CrowbarSmashAction;
import com.antaurora.apofirstlight.interaction.CrowbarSmashTimeline;
import com.antaurora.apofirstlight.network.*;
import com.antaurora.apofirstlight.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Silent windup; the server-confirmed impact starts the fracture cue and recovery. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class CrowbarSmashClient {
    private record Playing(SimpleSoundInstance sound,int start,boolean impact){}
    private static final Map<UUID,Playing> SOUNDS=new HashMap<>();
    private static CrowbarSmashPacket current;
    private static int clock,start,slot,impactAt;
    private static net.minecraft.client.multiplayer.ClientLevel actionLevel;
    private static ItemStack tool=ItemStack.EMPTY;
    private static boolean committed;
    public static boolean active(){return current!=null;}
    public static double elapsed(float partial){
        if(!active())return 0;
        return committed?Math.min(CrowbarSmashTimeline.DURATION,CrowbarSmashTimeline.IMPACT+clock-impactAt+partial)
                :Math.min(CrowbarSmashTimeline.IMPACT-1,clock-start+partial);
    }
    public static void receive(CrowbarSmashPacket p){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(p.phase()==CrowbarSmashAction.START){
            if(SOUNDS.containsKey(p.action()))return;
            if(current!=null&&current.action().equals(p.action()))return;
            if(p.player().equals(mc.player.getUUID())){current=p;start=clock;slot=mc.player.getInventory().selected;tool=mc.player.getMainHandItem();committed=false;actionLevel=mc.level;}
        }else{
            if(p.phase()==CrowbarSmashAction.IMPACT){
                var old=SOUNDS.get(p.action());
                if(old==null||!old.impact()){
                    if(old!=null)mc.getSoundManager().stop(old.sound());
                    var sound=new SimpleSoundInstance(AflSounds.VENDING_MACHINE_BREAK.get(),SoundSource.BLOCKS,.7f,1f,mc.level.random,p.target());
                    SOUNDS.put(p.action(),new Playing(sound,clock,true));mc.getSoundManager().play(sound);
                }
                // This is a server-confirmed state, not prediction. Apply with its audio cue instead of
                // waiting for the chunk's batched block update at the end of the server tick.
                for(var pos:java.util.List.of(p.target(),p.target().above())){
                    var s=mc.level.getBlockState(pos);
                    if(s.getBlock() instanceof VendingMachineBlock)mc.level.setBlock(pos,s.setValue(VendingMachineBlock.BROKEN,true),3);
                }
            }
            if(p.phase()==CrowbarSmashAction.CANCEL){var sound=SOUNDS.remove(p.action());if(sound!=null)mc.getSoundManager().stop(sound.sound());}
            if(current!=null&&current.action().equals(p.action())){
                if(p.phase()==CrowbarSmashAction.IMPACT&&!committed){committed=true;impactAt=clock;}
                if(p.phase()==CrowbarSmashAction.CANCEL)current=null;
            }
        }
    }
    private static void cancel(){
        if(current==null)return;
        AflNetwork.cancelCrowbarSmash(current.action());
        var sound=SOUNDS.remove(current.action());if(sound!=null)Minecraft.getInstance().getSoundManager().stop(sound.sound());current=null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null){for(var s:SOUNDS.values())mc.getSoundManager().stop(s.sound());SOUNDS.clear();current=null;return;}
        if(mc.isPaused())return;clock++;
        SOUNDS.entrySet().removeIf(s->clock-s.getValue().start()>CrowbarSmashTimeline.DURATION+10);
        if(current==null)return;
        if(mc.level!=actionLevel||mc.screen!=null||!mc.player.isAlive()||mc.player.isSpectator()||mc.player.isSleeping()
                ||mc.player.getInventory().selected!=slot||!mc.player.getMainHandItem().is(AflItems.CROWBAR.get())
                ||!ItemStack.isSameItemSameTags(tool,mc.player.getMainHandItem())){cancel();return;}
        if(!committed){
            if(!(mc.hitResult instanceof BlockHitResult hit)){cancel();return;}
            var s=mc.level.getBlockState(hit.getBlockPos());
            if(!(s.getBlock() instanceof VendingMachineBlock)||!VendingMachineBlock.lower(s,hit.getBlockPos()).equals(current.target())
                    ||VendingMachineBlock.frontPoint(s,hit.getBlockPos(),mc.player.getEyePosition(),hit)==null){cancel();return;}
        }
        if(committed&&elapsed(0)>=CrowbarSmashTimeline.DURATION){current=null;return;}
        if(clock-start>CrowbarSmashTimeline.DURATION+100)cancel();
    }
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered e){
        if(!active())return;
        e.setCanceled(true);e.setSwingHand(false);
        if(e.isAttack())cancel();
    }
    private CrowbarSmashClient(){}
}
