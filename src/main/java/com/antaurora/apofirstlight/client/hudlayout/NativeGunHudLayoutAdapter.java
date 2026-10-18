package com.antaurora.apofirstlight.client.hudlayout;

import com.antaurora.apofirstlight.client.config.NativeGunHudConfig;
import com.antaurora.apofirstlight.client.config.NativeGunHudConfigManager;
import com.antaurora.apofirstlight.weapon.client.NativeGunHud;
import com.antaurora.apofirstlight.weapon.client.NativeGunHudLayout;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public final class NativeGunHudLayoutAdapter implements ClientHudLayout {
    private static final Gson JSON=new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    public String id(){return "native_gun";}
    public String fileName(){return "native_gun_hud.json";}
    public ResourceLocation overlayId(){return new ResourceLocation("apocalypse_firstlight", "native_gun");}
    public JsonObject defaults(){return JSON.toJsonTree(NativeGunHudConfig.defaults()).getAsJsonObject();}
    public JsonObject current(){return JSON.toJsonTree(NativeGunHudConfigManager.get()).getAsJsonObject();}
    public JsonObject normalize(JsonObject source){return NativeGunHudConfig.migrate(source);}
    private NativeGunHudConfig config(JsonObject json){return NativeGunHudConfig.parse(json.toString(), ignored -> {});}
    public void apply(JsonObject json){NativeGunHudConfigManager.apply(config(json));}
    public void render(GuiGraphics g,JsonObject json,int width,int height){NativeGunHud.preview(g,width,height,config(json));}
    public List<HudEditableElement> elements(JsonObject json,int width,int height){
        var c=config(json);var f=NativeGunHudLayout.frame(c.global(),width,height);
        var result=new ArrayList<HudEditableElement>();
        result.add(element("global",new HudBounds(f.x(),f.y(),f.width(),f.height()),
                c.global().offsetX(),c.global().offsetY(),1,List.of(new HudEditableElement.Scale("global.scale",c.global().scale(),.25F,2)),
                resize("global",c.global().width(),c.global().height(),40,160,24,100,false,false,true)));
        // HUD V2 B (NativeGunHud): the frame only; its rows are laid out in code, right-aligned in it
        return result;
    }
    private static List<HudEditableElement.Scale> scale(String path,float value){return List.of(new HudEditableElement.Scale(path,value,.25F,3));}
    private static HudEditableElement.Resize resize(String id,float w,float h,float minW,float maxW,float minH,float maxH,
                                                     boolean centerX,boolean centerY,boolean anchored){
        return new HudEditableElement.Resize(id+".width",id+".height",w,h,minW,maxW,minH,maxH,centerX,centerY,anchored);
    }
    private static HudEditableElement element(String id,HudBounds b,float x,float y,float units,List<HudEditableElement.Scale> scales,
                                              HudEditableElement.Resize resize){
        return new HudEditableElement(id,b,id+".offset_x",id+".offset_y",x,y,units,false,500,scales,resize);
    }
    public HudBounds occupied(JsonObject json,int width,int height){
        return elements(json,width,height).get(0).bounds();
    }
}
