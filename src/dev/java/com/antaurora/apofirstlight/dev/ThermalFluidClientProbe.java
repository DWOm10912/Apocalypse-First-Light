package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.compat.jei.AflJeiPlugin;
import com.antaurora.apofirstlight.compat.jade.*;
import com.antaurora.apofirstlight.registry.AflBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in, isolated graphical client integration probe. Excluded from release jar. */
@JeiPlugin
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class ThermalFluidClientProbe implements IModPlugin {
    private static IJeiRuntime runtime;
    private static int ticks, rendered;
    private static boolean checked, done;
    @Override public ResourceLocation getPluginUid() { return new ResourceLocation("apocalypse_firstlight","dev_thermal_fluid_probe"); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime=value; }
    @Override public void onRuntimeUnavailable() { runtime=null; }
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("afl.thermalFluidProbe") || done || event.phase!=TickEvent.Phase.END)return;
        ticks++;
        if(runtime==null || Minecraft.getInstance().level==null) {
            if(ticks>2400)finish(false,"runtime timeout");
            return;
        }
        try {
            if(checked) {
                if(++rendered==40) net.minecraft.client.Screenshot.grab(Minecraft.getInstance().gameDirectory,
                        Minecraft.getInstance().getMainRenderTarget(), message -> {});
                if(rendered>=60)finish(true,"category, catalyst, five fuel layouts, balance refresh, rendered JEI page, Jade conditional tooltip");
                return;
            }
            var manager=runtime.getRecipeManager();var type=AflJeiPlugin.THERMAL_GENERATION;
            var fuels=manager.createRecipeLookup(type).get().toList();
            if(fuels.isEmpty() && ticks<2200)return;
            require(fuels.size()==5,"five synchronized fuels, got "+fuels.size());
            var category=manager.getRecipeCategory(type);require(category!=null,"registered category");
            require(manager.createRecipeCatalystLookup(type).getItemStack().anyMatch(s->s.is(AflBlocks.THERMAL_GENERATOR.get().asItem())),"catalyst");
            require(fuels.stream().anyMatch(f->f.item().is(Items.COAL)&&f.energyFe()==500),"coal500");
            require(fuels.stream().anyMatch(f->f.item().is(Items.COAL_BLOCK)&&f.energyFe()==5000),"coalblock5000");
            require(fuels.stream().anyMatch(f->!f.fluid().isEmpty()&&f.fluid().getAmount()==1000&&f.energyFe()==20000),"lava1000/20000");
            for(var fuel:fuels)require(manager.createRecipeLayoutDrawable(category,fuel,runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).isPresent(),"layout "+fuel.id());
            var original=com.antaurora.apofirstlight.client.ClientThermalGeneratorFuelData.snapshot();
            var changed=new java.util.HashMap<>(original);changed.put(new ResourceLocation("minecraft","lava_bucket"),21001);
            com.antaurora.apofirstlight.client.ClientThermalGeneratorFuelData.replace(changed);
            var refreshed=manager.createRecipeLookup(type).get().toList();
            require(refreshed.size()==5 && refreshed.stream().anyMatch(f->!f.fluid().isEmpty()&&f.energyFe()==21001),"balance refresh without stale visible entries");
            com.antaurora.apofirstlight.client.ClientThermalGeneratorFuelData.replace(original);
            var data=new CompoundTag();data.putString(MachineJadeServerDataProvider.MACHINE_TYPE,MachineJadeServerDataProvider.THERMAL_GENERATOR);
            data.putInt(MachineJadeServerDataProvider.ENERGY_CAPACITY,100000);data.putInt(MachineJadeServerDataProvider.FLUID_CAPACITY,4000);
            var accessor=(snownee.jade.api.BlockAccessor)java.lang.reflect.Proxy.newProxyInstance(
                    ThermalFluidClientProbe.class.getClassLoader(),new Class[]{snownee.jade.api.BlockAccessor.class},
                    (proxy,method,args)->method.getName().equals("getServerData")?data:null);
            var empty=new snownee.jade.impl.Tooltip();MachineJadeComponentProvider.INSTANCE.appendTooltip(empty,accessor,null);
            data.put(MachineJadeServerDataProvider.INPUT_FLUID,new FluidStack(Fluids.LAVA,1000).writeToNBT(new CompoundTag()));
            var filled=new snownee.jade.impl.Tooltip();MachineJadeComponentProvider.INSTANCE.appendTooltip(filled,accessor,null);
            require(filled.size()==empty.size()+2,"Jade adds name/value and fluid bar only when filled");
            require(filled.getMessage().contains("1,000") && filled.getMessage().contains("4,000"),"Jade amount/capacity");
            runtime.getRecipesGui().showTypes(java.util.List.of(type));checked=true;
        } catch(Throwable error) { finish(false,error.toString()); }
    }
    private static void finish(boolean passed,String details) {
        done=true;
        com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[AFL THERMAL CLIENT PROBE] {}: {}",passed?"PASS":"FAIL",details);
        Minecraft.getInstance().stop();
    }
}
