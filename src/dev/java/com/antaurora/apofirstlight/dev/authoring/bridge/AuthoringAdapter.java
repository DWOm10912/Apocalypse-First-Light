package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.*;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

final class AuthoringAdapter {
    static BuildingAuthoringSession active(ServerPlayer p) throws Exception {
        if(!BuildingAuthoringCommands.allowed(p.createCommandSourceStack()))throw new IllegalArgumentException("AUTHORING_DISABLED_OR_PERMISSION_DENIED");
        try{return BuildingAuthoringCommands.active(p.createCommandSourceStack());}
        catch(IllegalArgumentException e){throw new IllegalArgumentException(e.getMessage().contains("dimension")?"DIMENSION_MISMATCH":"NO_ACTIVE_AUTHORING_SESSION");}
    }
    static JsonObject info(ServerPlayer p) throws Exception {
        var s=active(p);var o=new BridgeBounds(s.origin,s.max()).json();o.addProperty("id",s.metadata.id());o.addProperty("state",s.state.name());o.addProperty("dimension",s.dimension.location().toString());return o;
    }
    static JsonObject call(String tool,JsonObject a,ServerPlayer p) throws Exception {
        if(!BuildingAuthoringCommands.allowed(p.createCommandSourceStack()))throw new IllegalArgumentException("AUTHORING_DISABLED_OR_PERMISSION_DENIED");
        if(tool.equals("authoring_info"))return info(p);
        if(tool.equals("authoring_validate")){var s=active(p);new BridgeBounds(s.origin,s.max()).check(p.serverLevel());var scan=BuildingAuthoringService.validate(p.serverLevel(),s);return object("validated",true,"non_air",scan.blocks(),"exported",false);}
        String command;
        if(tool.equals("authoring_create")){
            String id=string(a,"building_id","");if(!id.matches("[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("INVALID_BUILDING_ID");
            int w=integer(a,"width",16),h=integer(a,"height",16),d=integer(a,"depth",16),offset=integer(a,"surface_offset_y",1);
            if(w<1||w>128||d<1||d>128||h<2||h>192||(long)w*h*d>BridgeBounds.LIMIT)throw new IllegalArgumentException("MAX_EDIT_LIMIT_OR_INVALID_SIZE");
            command="afl_author create "+id+" "+w+" "+d+" "+h+" "+offset;
        }else if(tool.equals("authoring_cancel")){active(p);command="afl_author cancel";}
        else throw new IllegalArgumentException("UNKNOWN_TOOL");
        // Narrow internal fallback to the existing lifecycle command. No supplied command text.
        int result=p.server.getCommands().getDispatcher().execute(command,p.createCommandSourceStack());
        if(result!=1)throw new IllegalArgumentException("AUTHORING_OPERATION_REJECTED: see game chat");
        return tool.equals("authoring_create")?info(p):object("cancelled",true,"blocks_preserved",true);
    }
}
