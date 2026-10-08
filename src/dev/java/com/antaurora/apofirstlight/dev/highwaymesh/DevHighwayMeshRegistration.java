package com.antaurora.apofirstlight.dev.highwaymesh;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.*;

/** Both an explicit production gate and the existing dev-package jar exclusion apply. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DevHighwayMeshRegistration {
    public static final ResourceLocation ID=new ResourceLocation("apocalypse_firstlight","dev_highway_surface");
    private static Block block;
    @SubscribeEvent public static void register(RegisterEvent event){
        if(FMLEnvironment.production)return;
        event.register(ForgeRegistries.Keys.BLOCKS,helper->{block=new DevRoadSurfaceBlock();helper.register(ID,block);});
    }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){if(!FMLEnvironment.production)DevHighwayMeshNetwork.register();}
    public static Block block(){if(block==null)throw new IllegalStateException("Development collision block is not registered");return block;}
    private DevHighwayMeshRegistration(){}
}
