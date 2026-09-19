package com.antaurora.apofirstlight.client.hudlayout;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.loading.FMLEnvironment;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** In a verified source checkout, startup/F3+T must not restore a stale build/resources copy. */
public final class HudLayoutDevResources {
    private HudLayoutDevResources(){}
    public static Reader open(ResourceManager manager,ResourceLocation resource) throws IOException {
        Path source=null;
        if(!FMLEnvironment.production){
            try{
                var store=new HudLayoutSourceStore(true,Path.of(""),Minecraft.getInstance().gameDirectory.toPath());
                source=store.path(resource.getPath().substring(resource.getPath().lastIndexOf('/')+1));
            }catch(IOException ignored){ /* No verified source: ordinary resource-pack behavior. */ }
        }
        return source!=null?Files.newBufferedReader(source):manager.getResourceOrThrow(resource).openAsReader();
    }
}
