package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

final class RegistrySnapshot {
    static JsonObject export() throws Exception {
        var blocks=new JsonObject();for(var block:BuiltInRegistries.BLOCK){var props=new JsonObject();for(var property:block.getStateDefinition().getProperties())props.add(property.getName(),values(property));
            blocks.add(BuiltInRegistries.BLOCK.getKey(block).toString(),object("properties",props,"has_block_entity",block.defaultBlockState().hasBlockEntity()));}
        var file=FMLPaths.GAMEDIR.get().resolve("afl_authoring_bridge/target_registry_1_20_1.json");Files.createDirectories(file.getParent());
        Files.writeString(file,GSON.toJson(object("minecraft_version",net.minecraft.SharedConstants.getCurrentVersion().getName(),"data_version",net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion(),"blocks",blocks)));
        return object("path",file.toAbsolutePath().toString(),"block_count",blocks.size(),"source","live BuiltInRegistries.BLOCK");
    }
    private static <T extends Comparable<T>> JsonArray values(Property<T> p){var result=new JsonArray();for(T v:p.getPossibleValues())result.add(p.getName(v));return result;}
}
