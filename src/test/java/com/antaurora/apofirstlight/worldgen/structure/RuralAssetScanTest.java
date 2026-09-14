package com.antaurora.apofirstlight.worldgen.structure;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.antaurora.apofirstlight.worldgen.rural.RuralStructurePool;
import net.minecraft.resources.ResourceLocation;

/** Dynamic, read-only source-asset compatibility scan. Does not build definitions or change pools. */
public final class RuralAssetScanTest {
    private static String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    public static void main(String[] args) {
        Path dir=Path.of("src/main/resources/data/apocalypse_firstlight/structures");
        try(var paths=Files.list(dir)) {
            var files=paths.filter(p->p.getFileName().toString().matches("rural_.*\\.nbt")).sorted().toList();
            if(files.isEmpty()) throw new AssertionError("No Rural NBT files");
            var pool=RuralStructurePool.definitions();
            var before=new LinkedHashMap<Path,String>();
            for(var file:files) before.put(file,hash(file));
            System.out.println("WG03_SCAN|FILE|X|Y|Z|BOUNDS|BLOCKS|AIR|BE|MULTIBLOCK_CELLS|ANCHOR|FRONT|POOL|ISSUES|SHA256");
            for(var file:files) {
                String name=file.getFileName().toString();
                var inspection=StructureNbtReader.read(file,64L*1024*1024);
                var info=inspection.info().orElseThrow(()->new AssertionError(name+": "+inspection.validation()));
                var b=info.localBounds();
                if(b.isEmpty()||b.minX()!=0||b.minY()!=0||b.minZ()!=0) throw new AssertionError("Bad size bounds "+name);
                for(var rotation:net.minecraft.world.level.block.Rotation.values()) {
                    var rotated=StructureTransform.bounds(info.size(),rotation,new net.minecraft.core.BlockPos(-200,-60,-300));
                    boolean swap=rotation==net.minecraft.world.level.block.Rotation.CLOCKWISE_90
                            ||rotation==net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
                    if(rotated.width()!=(swap?b.depth():b.width()) || rotated.depth()!=(swap?b.width():b.depth())
                            ||rotated.height()!=b.height()) throw new AssertionError("Real NBT size rotation "+name);
                }
                var id=new ResourceLocation("apocalypse_firstlight",name.substring(0,name.length()-4));
                var legacy=pool.stream().filter(d->d.id().equals(id)).findFirst();
                String anchor=legacy.map(d->Integer.toString(d.groundAnchorOffsetY())).orElse("UNDEFINED_PENDING_METADATA");
                String front=legacy.map(d->d.frontDirection().name()).orElse("UNDEFINED_PENDING_METADATA");
                String status=legacy.isPresent()?"CURRENTLY_IN_LEGACY_RURAL_POOL":"NOT_IN_LEGACY_RURAL_POOL";
                if(legacy.isPresent()&&(legacy.get().groundAnchorOffsetY()<0||legacy.get().groundAnchorOffsetY()>b.height()
                        ||!legacy.get().frontDirection().getAxis().isHorizontal())) throw new AssertionError("Invalid legacy anchor/front "+name);
                if(Set.of("rural_farmhouse_02.nbt","rural_house_small_02.nbt").contains(name)&&legacy.isPresent())
                    throw new AssertionError("WG-03 variant unexpectedly in legacy pool "+name);
                System.out.println("WG03_SCAN|"+name+"|"+b.width()+"|"+b.height()+"|"+b.depth()
                        +"|[0,"+b.width()+")x[0,"+b.height()+")x[0,"+b.depth()+")|"+info.blockCount()+"|"+info.explicitAirCount()
                        +"|"+info.blockEntityCount()+"|"+info.multiblockCount()+"|"+anchor+"|"+front+"|"+status+"|"
                        +inspection.validation().issues().stream().map(i->i.code().name()).toList()+"|"+before.get(file));
            }
            for(var entry:before.entrySet()) if(!hash(entry.getKey()).equals(entry.getValue()))
                throw new AssertionError("NBT changed during read-only scan: "+entry.getKey());
            for(String variant:List.of("rural_farmhouse_02.nbt","rural_house_small_02.nbt"))
                if(Files.exists(dir.resolve(variant))&&!files.contains(dir.resolve(variant))) throw new AssertionError("Missed "+variant);
            System.out.println("PASS WG-03 rural files="+files.size()+"; hashes unchanged; no metadata/pool writes");
        } catch(Exception error) { throw new AssertionError("Read-only Rural scan failed",error); }
    }
}
