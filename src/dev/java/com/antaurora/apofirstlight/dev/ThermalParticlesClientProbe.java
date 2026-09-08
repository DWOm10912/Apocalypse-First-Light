package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.ThermalGeneratorBlock;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class ThermalParticlesClientProbe {
    private static final BlockPos POS=new BlockPos(0,160,0);
    private static final String[] CASES={"coal","charcoal","coal_block","lava","full","off","east","south","west","multi16"};
    private static int age,index=-1,wait;
    private static boolean done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("afl.thermalParticlesProbe")||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(++age<100)return;
        if(index<0){next();return;}
        mc.options.hideGui=true;
        if(++wait<100)return;
        if(wait==100) {
            var be=mc.level.getBlockEntity(POS);
            if(!(be instanceof ThermalGeneratorBlockEntity g)) { finish("FAIL missing client BE");return; }
            var expected=index==4?ThermalGeneratorBlockEntity.VisualState.FULL:index==5?ThermalGeneratorBlockEntity.VisualState.OFF:ThermalGeneratorBlockEntity.VisualState.RUNNING;
            if(g.getVisualState()!=expected){finish("FAIL state "+CASES[index]);return;}
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"thermal_particles_"+CASES[index]+".png",mc.getMainRenderTarget(),message->{});
            com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[THERMAL PARTICLES CLIENT] {} state={} source={} particles={}",CASES[index],g.getVisualState(),g.getActiveFuelSource(),mc.particleEngine.countParticles());
        }
        if(wait>=120)next();
    }
    private static void next() {
        if(++index>=CASES.length){finish("PASS captured ten cases including sixteen machines");return;}wait=0;
        var mc=Minecraft.getInstance();int which=index;
        mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();level.setDayTime(18000);
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(GameType.SPECTATOR);
            Direction facing=switch(which){case 6->Direction.EAST;case 7->Direction.SOUTH;case 8->Direction.WEST;default->Direction.NORTH;};
            int count=which==9?16:1;
            // This grid belongs exclusively to the isolated probe world, including its prior saved run.
            for(int i=0;i<16;i++)level.setBlock(POS.offset((i%4)*2,0,(i/4)*2),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            for(int i=0;i<count;i++) {
                var pos=POS.offset((i%4)*2,0,(i/4)*2);
                level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                level.setBlock(pos,AflBlocks.THERMAL_GENERATOR.get().defaultBlockState().setValue(ThermalGeneratorBlock.FACING,facing),3);
                var g=(ThermalGeneratorBlockEntity)level.getBlockEntity(pos);var data=new CompoundTag();
                if(which==4)data.putInt("EnergyStored",100000);g.load(data);
                if(which==3)g.restoreLiquid(new FluidStack(Fluids.LAVA,4000));
                else if(which!=5)g.setItem(0,new ItemStack(which==1?Items.CHARCOAL:which==2?Items.COAL_BLOCK:Items.COAL,64));
            }
            player.teleportTo(level,which==9?3.5:.5+facing.getStepX()*2.2,which==9?161:159.2,
                    which==9?-5:.5+facing.getStepZ()*2.2,java.util.Set.of(),facing.getOpposite().toYRot(),which==9?20:5);
        });
    }
    private static void finish(String message) {
        done=true;com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[THERMAL PARTICLES CLIENT] {}",message);
        Minecraft.getInstance().stop();
    }
}
