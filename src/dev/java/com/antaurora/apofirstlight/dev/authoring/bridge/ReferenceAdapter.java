package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.google.gson.*;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.extent.clipboard.*;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

/** Prepared reference artifacts only. WorldEdit writes and serializer, never save-file manipulation. */
final class ReferenceAdapter {
    private EditSession last;
    private ReferenceAreaCommands.Area lastArea;
    private String lastId,operationId;
    private final Map<BlockVector3,com.sk89q.worldedit.world.block.BaseBlock> afterPaste=new LinkedHashMap<>();
    JsonObject call(String tool,JsonObject a,ServerPlayer p)throws Exception{
        if(!com.antaurora.apofirstlight.authoring.BuildingAuthoringCommands.allowed(p.createCommandSourceStack()))throw new IllegalArgumentException("AUTHORING_DISABLED_OR_PERMISSION_DENIED");
        if(tool.equals("reference_remove"))return remove(p);
        String id=string(a,"reference_id","");if(!id.matches("[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("INVALID_REFERENCE_ID");
        var file=FMLPaths.GAMEDIR.get().resolve("afl_reference_import/prepared").resolve(id+".json");
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||Files.size(file)>32*1024*1024)throw new IllegalArgumentException("PREPARED_ARTIFACT_MISSING_OR_TOO_LARGE");
        var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if(!bool(data,"reference_only")||!bool(data,"prepared")||!string(data,"target_version","").equals("1.20.1")||!id.equals(string(data,"reference_id","")))throw new IllegalArgumentException("REFERENCE_NOT_PREPARED");
        var size=data.getAsJsonArray("size");var max=new BlockPos(size.get(0).getAsInt()-1,size.get(1).getAsInt()-1,size.get(2).getAsInt()-1);var local=new BridgeBounds(BlockPos.ZERO,max);if(local.volume()>BridgeBounds.LIMIT)throw new IllegalArgumentException("MAX_EDIT_LIMIT: extract smaller reference bounds");
        var clip=new BlockArrayClipboard(new CuboidRegion(BlockVector3.ZERO,ForgeAdapter.adapt(max)));clip.setOrigin(BlockVector3.ZERO);
        // Bridge enforces its own dev/creative/scope and hazardous-state policy. WE's chat-command
        // blacklist includes harmless reference vegetation (grass); never change global WE config.
        var palette=new ArrayList<com.sk89q.worldedit.world.block.BaseBlock>();var parser=new com.sk89q.worldedit.extension.input.ParserContext();parser.setWorld(ForgeAdapter.adapt(p.serverLevel()));parser.setActor(ForgeAdapter.adaptPlayer(p));parser.setRestricted(false);parser.setTryLegacy(false);
        for(var entry:data.getAsJsonArray("palette")){
            var state=entry.getAsJsonObject();String name=string(state,"Name","");var props=state.has("Properties")?state.getAsJsonObject("Properties"):new JsonObject();var parts=new ArrayList<String>();for(var property:props.entrySet())parts.add(property.getKey()+"="+property.getValue().getAsString());String input=name+(parts.isEmpty()?"":"["+String.join(",",parts)+"]");
            if(!input.matches("[a-z0-9_]+:[a-z0-9_/]+(\\[[a-z0-9_=,]+\\])?"))throw new IllegalArgumentException("INVALID_BLOCK_STATE");
            // No NBT accepted. Unsafe world-acting geometry requires an explicit offline mapping.
            if(name.matches(".*(command_block|structure_block|structure_void|jigsaw|barrier|tnt|fire$|portal|piston|sculk_sensor|sculk_shrieker|spawner).*$"))throw new IllegalArgumentException("REFERENCE_UNSAFE_BLOCK_REQUIRES_MAPPING: "+name);
            palette.add(WorldEdit.getInstance().getBlockFactory().parseFromInput(input,parser));
        }
        Set<BlockPos> seen=new HashSet<>();for(var entry:data.getAsJsonArray("blocks")){var b=entry.getAsJsonArray();var pos=new BlockPos(b.get(0).getAsInt(),b.get(1).getAsInt(),b.get(2).getAsInt());if(!local.contains(pos)||!seen.add(pos))throw new IllegalArgumentException("INVALID_REFERENCE_BLOCK_POSITION");clip.setBlock(ForgeAdapter.adapt(pos),palette.get(b.get(3).getAsInt()));}
        if(tool.equals("reference_write_schematic")){
            var out=FMLPaths.GAMEDIR.get().resolve("config/worldedit/schematics/afl_references").resolve(id+".schem");Files.createDirectories(out.getParent());
            try(var stream=Files.newOutputStream(out,StandardOpenOption.CREATE_NEW);var writer=BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(stream)){writer.write(clip);}
            return object("path",out.toAbsolutePath().toString(),"reference_only",true,"serializer","WorldEdit 7.2.15 SPONGE_SCHEMATIC");
        }
        if(!tool.equals("reference_paste"))throw new IllegalArgumentException("UNKNOWN_TOOL");
        int rotation=integer(a,"rotation",0);var rotated=ClipboardRotations.rotate(clip,rotation);var rotatedMax=ForgeAdapter.toBlockPos(rotated.getMaximumPoint());
        var area=ReferenceAreaCommands.get(p);var target=new BridgeBounds(area.bounds().min(),area.bounds().min().offset(rotatedMax));target.inside(area.bounds());target.check(p.serverLevel());
        if(last!=null)throw new IllegalArgumentException("REFERENCE_HISTORY_OCCUPIED: remove previous paste first or review in new world session");
        if(!p.serverLevel().getEntities(null,target.aabb()).isEmpty())throw new IllegalArgumentException("ENTITY_IN_REFERENCE_AREA");
        for(var pos:BlockPos.betweenClosed(target.min(),target.max()))if(!p.serverLevel().getBlockState(pos).isAir())throw new IllegalArgumentException("REFERENCE_AREA_NOT_EMPTY");
        if(bool(a,"dry_run"))return object("dry_run",true,"bounds",target.json(),"reference_only",true,"rotation",rotation);
        var edit=WorldEditAdapter.edit(p,target);try{
            Operations.complete(new ClipboardHolder(rotated).createPaste(edit).to(ForgeAdapter.adapt(target.min())).copyEntities(false).copyBiomes(false).build());edit.close();
        }catch(Exception ex){edit.close();try(var rollback=WorldEditAdapter.edit(p,target)){edit.undo(rollback);}throw ex;}
        afterPaste.clear();var changes=edit.getChangeSet().forwardIterator();var world=ForgeAdapter.adapt(p.serverLevel());while(changes.hasNext()){var change=changes.next();if(change instanceof com.sk89q.worldedit.history.change.BlockChange b)afterPaste.put(b.getPosition(),world.getFullBlock(b.getPosition()));}
        last=edit;lastArea=area;lastId=id;operationId=UUID.randomUUID().toString();var result=object("success",true,"reference_id",id,"operation_id",operationId,"changed_blocks",WorldEditAdapter.changed(edit),"bounds",target.json(),"rotation",rotation,"reference_only",true,"undo_available",true);var historyDir=FMLPaths.GAMEDIR.get().resolve("afl_reference_import/paste_history");Files.createDirectories(historyDir);Files.writeString(historyDir.resolve(operationId+".json"),GSON.toJson(result),StandardOpenOption.CREATE_NEW);return result;
    }
    private JsonObject remove(ServerPlayer p)throws Exception{
        var area=ReferenceAreaCommands.get(p);if(last==null||area!=lastArea)throw new IllegalArgumentException("NO_REFERENCE_HISTORY_FOR_AREA");
        area.bounds().check(p.serverLevel());if(!p.serverLevel().getEntities(null,area.bounds().aabb()).isEmpty())throw new IllegalArgumentException("ENTITY_IN_REFERENCE_AREA");
        var world=ForgeAdapter.adapt(p.serverLevel());for(var entry:afterPaste.entrySet())if(!world.getFullBlock(entry.getKey()).equals(entry.getValue()))throw new IllegalArgumentException("REFERENCE_HISTORY_CONFLICT: user edits preserved");
        try(var edit=WorldEditAdapter.edit(p,area.bounds())){last.undo(edit);}var result=object("removed",true,"reference_id",lastId,"operation_id",operationId,"changed_blocks",last.size());last=null;return result;
    }
}
