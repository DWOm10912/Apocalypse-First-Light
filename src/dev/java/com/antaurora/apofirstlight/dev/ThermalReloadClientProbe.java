package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class ThermalReloadClientProbe {
    private static int age;private static boolean done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        String mode=System.getProperty("afl.thermalReloadProbe","");
        if(mode.isEmpty()||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var pos=new BlockPos(0,120,0);
        if(++age==1&&mode.equals("seed"))mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();level.setBlock(pos,AflBlocks.THERMAL_GENERATOR.get().defaultBlockState(),3);
            var g=(ThermalGeneratorBlockEntity)level.getBlockEntity(pos);var tag=new CompoundTag();
            tag.putInt("EnergyStored",100000);tag.putLong("RotorTicks",1234);g.load(tag);
            g.setItem(0,new ItemStack(Items.COAL,64));g.restoreLiquid(new FluidStack(Fluids.LAVA,2000));
        });
        if(age<40)return;done=true;
        var be=mc.level.getBlockEntity(pos);
        boolean pass=be instanceof ThermalGeneratorBlockEntity g && g.getVisualState()==ThermalGeneratorBlockEntity.VisualState.FULL
                && g.getLiquidAmount()==2000 && g.getItem(0).is(Items.COAL)&&g.getItem(0).getCount()==64
                && g.getRotorTime(.5f)==1234 && g.getBlockState().getLightEmission()==0;
        com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[THERMAL RELOAD] {} {}",mode,pass?"PASS":"FAIL");
        if(mode.equals("seed"))mc.stop();
    }
}
