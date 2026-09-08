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
public final class ThermalDynamicClientProbe {
    private static final BlockPos POS=new BlockPos(0,120,0);
    private static final String[] CASES={"off","coal","charcoal","coal_block","lava25","lava50","lava75","full","east","south","west","drained"};
    private static int age,index=-1,wait;
    private static boolean done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("afl.thermalDynamicProbe")||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(++age<100)return;
        if(index<0){next();return;}
        mc.options.hideGui=true;
        if(wait==30&&index==1)net.minecraft.client.Screenshot.grab(mc.gameDirectory,"thermal_dynamic_rotor_before.png",mc.getMainRenderTarget(),message->{});
        if(++wait<45)return;
        if(wait==45) {
            var be=mc.level.getBlockEntity(POS);
            if(!(be instanceof ThermalGeneratorBlockEntity)) { finish("FAIL missing client BE");return; }
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"thermal_dynamic_"+CASES[index]+".png",mc.getMainRenderTarget(),message->{});
            com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[THERMAL DYNAMIC CLIENT] {} state={} fluid={}",CASES[index],((ThermalGeneratorBlockEntity)be).getVisualState(),((ThermalGeneratorBlockEntity)be).getLiquidAmount());
        }
        if(wait>=55)next();
    }
    private static void next() {
        if(++index>=CASES.length){finish("PASS captured twelve cases");return;}wait=0;
        var mc=Minecraft.getInstance();int which=index;
        mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();
            level.setDayTime(18000);
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.setGameMode(GameType.SPECTATOR);
            Direction facing=switch(which){case 8->Direction.EAST;case 9->Direction.SOUTH;case 10->Direction.WEST;default->Direction.NORTH;};
            level.setBlock(POS,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            level.setBlock(POS,AflBlocks.THERMAL_GENERATOR.get().defaultBlockState().setValue(ThermalGeneratorBlock.FACING,facing),3);
            var g=(ThermalGeneratorBlockEntity)level.getBlockEntity(POS);var data=new CompoundTag();
            if(which==7)data.putInt("EnergyStored",100000);g.load(data);
            if(which>=1&&which<=3)g.setItem(0,new ItemStack(which==1?Items.COAL:which==2?Items.CHARCOAL:Items.COAL_BLOCK,64));
            if(which>=4&&which<=10)g.restoreLiquid(new FluidStack(Fluids.LAVA,which==4?1000:which==5?2000:which==6?3000:4000));
            player.teleportTo(level,.5+facing.getStepX()*1.8,119.4,.5+facing.getStepZ()*1.8,
                    java.util.Set.of(),facing.getOpposite().toYRot(),16);
        });
    }
    private static void finish(String message) {
        done=true;com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[THERMAL DYNAMIC CLIENT] {}",message);
        Minecraft.getInstance().stop();
    }
}
