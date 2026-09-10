package com.antaurora.apofirstlight.authoring;

import com.google.gson.GsonBuilder;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import java.nio.file.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Server-thread-only, bounded developer operations. No worldgen hook or permanent chunk load. */
public final class BuildingAuthoringService {
    private BuildingAuthoringService() {}
    public record Capture(StructureTemplate template, CompoundTag nbt, String digest, int blocks) {}
    public static void accessible(ServerLevel level, BuildingAuthoringSession s) {
        if(!level.dimension().equals(s.dimension)) throw new IllegalArgumentException("Return to session dimension: "+s.dimension.location());
        if(s.origin.getY()<level.getMinBuildHeight()||s.max().getY()>=level.getMaxBuildHeight()
                ||!level.getWorldBorder().isWithinBounds(s.origin)||!level.getWorldBorder().isWithinBounds(s.max()))
            throw new IllegalArgumentException("Plot outside world height/border");
        for(int x=s.origin.getX()>>4;x<=s.max().getX()>>4;x++)
            for(int z=s.origin.getZ()>>4;z<=s.max().getZ()>>4;z++)
                if(!level.hasChunk(x,z)) throw new IllegalArgumentException("Plot chunks must already be loaded; move closer or reduce size");
    }
    public static void vacant(ServerLevel level, BuildingAuthoringSession s) {
        accessible(level,s);
        for(var p:BlockPos.betweenClosed(s.origin,s.max()))
            if(!level.isEmptyBlock(p)) throw new IllegalArgumentException("Plot is not empty at "+p.toShortString()+"; choose an empty development area");
        noEntities(level,s);
    }
    private static void noEntities(ServerLevel level, BuildingAuthoringSession s) {
        if(!level.getEntities((net.minecraft.world.entity.Entity)null,s.bounds(),e->true).isEmpty())
            throw new IllegalArgumentException("Capture volume contains entities/players; move them outside");
    }
    private static boolean forbidden(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.COMMAND_BLOCK)||state.is(Blocks.CHAIN_COMMAND_BLOCK)||state.is(Blocks.REPEATING_COMMAND_BLOCK)
                ||state.is(Blocks.STRUCTURE_BLOCK)||state.is(Blocks.JIGSAW)||state.is(Blocks.STRUCTURE_VOID)
                ||state.is(Blocks.BARRIER)||state.is(Blocks.LIGHT)||state.is(Blocks.BEDROCK)
                ||state.is(Blocks.SPONGE)||state.is(Blocks.WET_SPONGE);
    }
    private static boolean loot(Tag tag) {
        if(tag instanceof CompoundTag c) {
            if(c.contains("Count",Tag.TAG_ANY_NUMERIC)&&c.getInt("Count")>0&&c.contains("id",Tag.TAG_STRING))return true;
            for(String key:c.getAllKeys()) {
                if(key.equalsIgnoreCase("LootTable"))return true;
                if(loot(c.get(key)))return true;
            }
        } else if(tag instanceof ListTag list) {for(Tag child:list)if(loot(child))return true;}
        return false;
    }
    public static Capture capture(ServerLevel level, BuildingAuthoringSession s, boolean validate) throws IOException {
        accessible(level,s);int blocks=0;
        if(validate) noEntities(level,s);
        for(var p:BlockPos.betweenClosed(s.origin,s.max())) {
            var state=level.getBlockState(p);if(!state.isAir())blocks++;
            if(!validate)continue;
            if(forbidden(state))throw new IllegalArgumentException("Forbidden debug/danger/placeholder block at "+p.toShortString());
            var be=level.getBlockEntity(p);
            if(be!=null) {
                if(loot(be.saveWithFullMetadata()))throw new IllegalArgumentException("Loot/items forbidden at "+p.toShortString());
                var handler=be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
                if(handler.isPresent()) for(int i=0;i<handler.get().getSlots();i++)
                    if(!handler.get().getStackInSlot(i).isEmpty())throw new IllegalArgumentException("Nonempty inventory at "+p.toShortString());
            }
        }
        if(validate&&blocks<8)throw new IllegalArgumentException("Need at least 8 non-air blocks");
        var m=s.metadata;var t=new StructureTemplate();
        t.fillFromWorld(level,s.origin,new Vec3i(m.width(),m.height(),m.depth()),false,null);
        CompoundTag nbt=t.save(new CompoundTag());
        String digest=digest(nbt)+m.json().toString();
        if(!Objects.equals(s.approvedDigest,digest))s.state=blocks==0?BuildingAuthoringSession.State.EMPTY:BuildingAuthoringSession.State.DRAFT;
        return new Capture(t,nbt,digest,blocks);
    }
    private static String digest(CompoundTag tag) throws IOException {
        try {
            var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(tag,bytes);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static Capture validate(ServerLevel level, BuildingAuthoringSession s) throws IOException {
        s.state=BuildingAuthoringSession.State.DRAFT;s.approvedDigest=null;
        Capture c=capture(level,s,true);s.approvedDigest=c.digest();s.state=BuildingAuthoringSession.State.VALIDATED;return c;
    }
    public static void clear(ServerLevel level, BuildingAuthoringSession s) {
        accessible(level,s);noEntities(level,s);
        // No drops; remove container data before replacing the block. Never writes outside the reservation.
        for(var p:BlockPos.betweenClosed(s.origin,s.max())) {
            level.removeBlockEntity(p);level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
        }
        s.changed();s.state=BuildingAuthoringSession.State.EMPTY;
    }
    public static Path export(ServerLevel level, BuildingAuthoringSession s, Path directory) throws IOException {
        Capture c=validate(level,s); // Always rescan immediately, including WorldEdit/command/BE edits.
        Path root=directory.toAbsolutePath().normalize();Files.createDirectories(root);
        Path nbt=root.resolve(s.metadata.id()+".nbt"),json=root.resolve(s.metadata.id()+".json");
        if(Files.exists(nbt,LinkOption.NOFOLLOW_LINKS)||Files.exists(json,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Export exists; archive the old pair manually or use a new building ID");
        Path temp=Files.createTempDirectory(root,".pending-");boolean wroteNbt=false;
        try {
            try(var out=Files.newOutputStream(temp.resolve("structure.nbt"))) {NbtIo.writeCompressed(c.nbt(),out);}
            Files.writeString(temp.resolve("metadata.json"),new GsonBuilder().setPrettyPrinting().create().toJson(s.metadata.json()));
            Files.move(temp.resolve("structure.nbt"),nbt);wroteNbt=true;
            Files.move(temp.resolve("metadata.json"),json);
            s.state=BuildingAuthoringSession.State.EXPORTED;return nbt;
        }catch(IOException e){if(wroteNbt)Files.deleteIfExists(nbt);throw e;}
        finally {Files.deleteIfExists(temp.resolve("structure.nbt"));Files.deleteIfExists(temp.resolve("metadata.json"));Files.deleteIfExists(temp);}
    }
}
