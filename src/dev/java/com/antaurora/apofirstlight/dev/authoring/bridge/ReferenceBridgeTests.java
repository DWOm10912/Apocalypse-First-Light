package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

/** Only invoked in WE-present tests, using a separately prepared read-only sample. */
final class ReferenceBridgeTests {
    static void run(GameTestHelper h,BridgeRouter router,ServerPlayer p)throws Exception{
        String source=System.getProperty("afl.referenceTestArtifact","");
        if(source.isBlank()||!Files.isRegularFile(Path.of(source))){com.mojang.logging.LogUtils.getLogger().warn("[AFL REFERENCE TEST] NOT_TESTED: no prepared-safe sample");return;}
        var data=JsonParser.parseString(Files.readString(Path.of(source))).getAsJsonObject();String id=data.get("reference_id").getAsString();
        var dir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("afl_reference_import/prepared");Files.createDirectories(dir);Files.writeString(dir.resolve(id+".json"),GSON.toJson(data));
        for(int x=128;x<=192;x+=16)for(int z=0;z<=64;z+=16)p.serverLevel().getChunkAt(new BlockPos(x,200,z));
        BridgeGameTests.reject(h,()->router.call("reference_paste",object("reference_id",id),p),"no reference area");
        ReferenceAreaCommands.set(p,new BridgeBounds(new BlockPos(128,200,0),new BlockPos(168,315,40)));
        router.call("reference_paste",object("reference_id",id,"dry_run",true),p);h.assertTrue(p.serverLevel().getBlockState(new BlockPos(128,200,0)).isAir(),"reference dry run");
        var schem=router.call("reference_write_schematic",object("reference_id",id),p);
        try(var stream=Files.newInputStream(Path.of(schem.get("path").getAsString()));var reader=com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(stream)){
            var clip=reader.read();h.assertTrue(clip.getDimensions().getBlockX()==39&&clip.getDimensions().getBlockY()==116,"actual WE serializer roundtrip");h.assertTrue(clip.getEntities().isEmpty(),"no entities");
        }
        var pasted=router.call("reference_paste",object("reference_id",id),p);int nonAir=0;for(var pos:BlockPos.betweenClosed(new BlockPos(128,200,0),new BlockPos(168,315,40)))if(!p.serverLevel().getBlockState(pos).isAir())nonAir++;com.mojang.logging.LogUtils.getLogger().info("[AFL REFERENCE TEST] nonAir={} reported={}",nonAir,pasted);h.assertTrue(nonAir==64025,"sample non-air count actual="+nonAir);
        router.call("reference_remove",object(),p);for(var pos:BlockPos.betweenClosed(new BlockPos(128,200,0),new BlockPos(168,315,40)))h.assertTrue(p.serverLevel().getBlockState(pos).isAir(),"reference removed");
        router.call("reference_paste",object("reference_id",id,"rotation",90),p);router.call("reference_remove",object(),p);
        com.mojang.logging.LogUtils.getLogger().info("[AFL REFERENCE TEST] PASS actual sample, WorldEdit Sponge writer/reader, 64025 blocks, scoped paste/remove, rotation90. No user-world paste.");
    }
}
