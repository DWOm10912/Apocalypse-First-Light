package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

final class BridgeRouter {
    private WorldEditAdapter worldEdit;
    private ReferenceAdapter reference;
    /** One undo/redo history for WorldEdit and controlled fixture edits. */
    final BridgeHistory history=new BridgeHistory();
    private final FixtureAdapter fixtures=new FixtureAdapter(history);
    final AuthoringCamera camera=new AuthoringCamera();
    static boolean hasWorldEdit(){return ModList.get().isLoaded("worldedit");}
    JsonObject call(String tool,JsonObject a,ServerPlayer p) throws Exception {
        if(a.has("min")!=a.has("max"))throw new IllegalArgumentException("BOUNDS_REQUIRE_BOTH_MIN_AND_MAX");
        if(tool.equals("camera_move")||tool.equals("camera_restore")||tool.equals("camera_status"))return camera.call(tool,a,p);
        if(tool.equals("export_target_registry"))return RegistrySnapshot.export();
        if(tool.equals("describe_block"))return AuthoringFixtureRegistry.describe(string(a,"block_id",""));
        if(tool.equals("list_authoring_fixtures"))return AuthoringFixtureRegistry.list(a);
        if(tool.equals("place_fixture")||tool.equals("place_multiblock")||tool.equals("reconcile_shapes")||tool.equals("relight_region"))return fixtures.call(tool,a,p);
        if(tool.equals("we_undo")||tool.equals("we_redo")){
            // History does not need WorldEdit: controlled fixture entries undo without it.
            var s=AuthoringAdapter.active(p);history.bind(s);
            if(!string(a,"target","AUTHORING_SESSION").equals("AUTHORING_SESSION"))throw new IllegalArgumentException("REFERENCE_READ_ONLY");
            return history.step(tool.equals("we_redo"),bool(a,"dry_run"),p,new BridgeBounds(s.origin,s.max()),System.nanoTime());
        }
        if(tool.equals("reference_paste")||tool.equals("reference_remove")||tool.equals("reference_write_schematic")){
            if(!hasWorldEdit())throw new IllegalArgumentException("WORLDEDIT_REQUIRED_FOR_AUTHORING_EDIT");if(reference==null)reference=new ReferenceAdapter();return reference.call(tool,a,p);
        }
        if(tool.startsWith("we_")||tool.equals("authoring_clear")){
            if(!hasWorldEdit())throw new IllegalArgumentException("WORLDEDIT_REQUIRED_FOR_AUTHORING_EDIT");
            if(worldEdit==null)worldEdit=new WorldEditAdapter(history);return worldEdit.call(tool,a,p);
        }
        if(tool.startsWith("authoring_")){
            var result=AuthoringAdapter.call(tool,a,p);
            if(tool.equals("authoring_cancel")||tool.equals("authoring_create"))camera.clear();
            return result;
        }
        if(tool.equals("get_player_state"))return object("position",new double[]{p.getX(),p.getY(),p.getZ()},"yaw",p.getYRot(),"pitch",p.getXRot(),"dimension",p.serverLevel().dimension().location().toString(),"chunk",new int[]{p.chunkPosition().x,p.chunkPosition().z},"block",xyz(p.blockPosition()));
        BridgeBounds b;
        String target=string(a,"target",tool.equals("audit_support")?"AUTHORING_SESSION":"REFERENCE_SELECTION");
        if(tool.equals("get_worldedit_selection")||target.equals("REFERENCE_SELECTION")){
            if(!hasWorldEdit())throw new IllegalArgumentException("WORLDEDIT_REQUIRED_FOR_SELECTION");b=WorldEditAdapter.selection(p);
        }else if(target.equals("AUTHORING_SESSION")){var s=AuthoringAdapter.active(p);b=new BridgeBounds(s.origin,s.max());}
        else if(target.equals("REFERENCE_AREA")){b=ReferenceAreaCommands.get(p).bounds();}
        else throw new IllegalArgumentException("INVALID_TARGET");
        if(tool.equals("get_worldedit_selection")){var result=b.json();result.addProperty("selection_type","CuboidRegion");result.addProperty("dimension",p.serverLevel().dimension().location().toString());return result;}
        if(a.has("min")&&a.has("max")){var crop=new BridgeBounds(pos(a,"min"),pos(a,"max"));crop.inside(b);b=crop;}
        return switch(tool){case "inspect_selection" -> WorldInspector.inspect(p.serverLevel(),b);case "get_horizontal_slice" -> WorldInspector.slice(p.serverLevel(),b,a,true);case "get_vertical_slice" -> WorldInspector.slice(p.serverLevel(),b,a,false);case "inspect_facade" -> WorldInspector.facade(p.serverLevel(),b,a);case "audit_support" -> FixtureAdapter.audit(p.serverLevel(),b);default -> throw new IllegalArgumentException("UNKNOWN_TOOL");};
    }
}
